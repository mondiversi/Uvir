"""Minimal live-only UVIR reader. No provisioning, persistent edits or record ACKs.

USB needs pyserial; local Wi-Fi TCP uses the Python standard library.
Use an idle sensor, disconnected from Android. See docs/STANDALONE_SENSOR.md.
"""

import argparse
from collections import deque
from getpass import getpass
import json
import math
import re
import socket
import sys
import time

PROTOCOL = "uvir-sensor-v1"
MAX_FRAME_BYTES = 65536
BANDS = ("uvc", "uvb", "uva", "violet", "blue", "green", "yellow", "orange",
         "red", "far_red", "nir")


class TcpChannel:
    def __init__(self, host, port):
        self.connection = socket.create_connection((host, port), timeout=5)
        self.connection.settimeout(0.2)

    def read(self):
        try:
            data = self.connection.recv(4096)
        except socket.timeout:
            return b""
        if not data:
            raise ConnectionError("Sensor disconnected")
        return data

    def write(self, data):
        self.connection.sendall(data)

    def close(self):
        self.connection.close()


class SerialChannel:
    def __init__(self, port):
        try:
            import serial
        except ImportError:
            raise RuntimeError("USB requires: python -m pip install pyserial") from None
        self.connection = serial.Serial()
        self.connection.port = port
        self.connection.baudrate = 115200
        self.connection.timeout = 0.2
        self.connection.write_timeout = 2
        self.connection.dtr = False
        self.connection.rts = False
        self.connection.open()

    def read(self):
        # Read at least one byte; do not wait to accumulate a full 4096 bytes.
        return self.connection.read(min(4096, self.connection.in_waiting or 1))

    def write(self, data):
        if self.connection.write(data) != len(data):
            raise ConnectionError("Incomplete serial write")

    def close(self):
        self.connection.close()


class Reader:
    def __init__(self, channel, clock=time.monotonic):
        self.channel = channel
        self.clock = clock
        self.buffer = bytearray()
        self.frames = deque()
        self.owned = False
        self.next_ping = 0.0

    def send(self, command):
        encoded = command.encode("ascii")
        if not encoded or len(encoded) > 512 or b"\n" in encoded or b"\r" in encoded:
            raise ValueError("Command does not fit one firmware line")
        self.channel.write(encoded + b"\n")
        if self.owned:
            self.next_ping = self.clock() + 4

    def receive(self, deadline):
        while self.clock() < deadline:
            if self.owned and self.clock() >= self.next_ping:
                self.send("PING")
            if self.frames:
                frame = self.frames.popleft()
                if frame.get("protocol") != PROTOCOL:
                    raise RuntimeError("Unexpected sensor protocol")
                if frame.get("type") == "error":
                    code = frame.get("code", "")
                    safe_code = code if isinstance(code, str) and re.fullmatch(r"[a-z0-9_]{1,80}", code) else "protocol_error"
                    # Never echo arbitrary messages or raw frames: they may be sensitive.
                    raise RuntimeError("Sensor rejected command: " + safe_code)
                return frame
            self.buffer.extend(self.channel.read())
            while b"\n" in self.buffer:
                line, _, remaining = self.buffer.partition(b"\n")
                self.buffer = bytearray(remaining)
                if len(line) > MAX_FRAME_BYTES:
                    raise RuntimeError("Oversized sensor response")
                try:
                    frame = json.loads(line)
                except (ValueError, UnicodeError):
                    continue  # ESP32 startup messages are not protocol JSON.
                if isinstance(frame, dict):
                    self.frames.append(frame)
            if len(self.buffer) > MAX_FRAME_BYTES:
                raise RuntimeError("Unterminated sensor response")
        return None

    def wait_for(self, predicate, seconds=10):
        deadline = self.clock() + seconds
        while self.clock() < deadline:
            frame = self.receive(deadline)
            if frame is not None and predicate(frame):
                return frame
        raise TimeoutError("Expected sensor reply not received")


def sample_output(frame):
    """Allowlist output fields; never print arbitrary hello/status/provisioning."""
    result = {"type": "sample", "unit": "uW/cm2"}
    for key in ("seq", "uptime_ms"):
        value = frame.get(key)
        if type(value) is int and value >= 0:
            result[key] = value
    for key in ("uv_available", "saturated"):
        if type(frame.get(key)) is bool:
            result[key] = frame[key]
    bands = frame.get("bands")
    if isinstance(bands, dict):
        result["bands"] = {key: value for key in BANDS
                           if type(value := bands.get(key)) in (int, float)
                           and math.isfinite(value)}
    return result


def read_live(reader, expected_device_id, token, duration, interval, emit):
    if token is not None:
        if not re.fullmatch(r"[A-Za-z0-9]{32}", token):
            raise ValueError("Expected the original 32-character authentication token")
        reader.send("AUTH " + token)
    else:
        reader.send("HELLO")
    hello = reader.wait_for(lambda f: f.get("type") == "hello")
    identity = hello.get("device_id")
    if not isinstance(identity, str) or not identity:
        raise RuntimeError("Missing sensor identity")
    if expected_device_id and identity.casefold() != expected_device_id.casefold():
        raise RuntimeError("Unexpected sensor identity; refusing control")
    if any(hello.get(key) for key in ("app_connected", "offline_recording",
                                    "alert_monitoring_enabled", "operation_active")):
        raise RuntimeError("Sensor is busy; this live example requires an idle sensor")
    reader.send("APP_CONNECT")
    reader.owned = True  # Ensure cleanup even if the acknowledgement is interrupted.
    reader.next_ping = reader.clock() + 4
    try:
        reader.wait_for(lambda f: f.get("type") == "status" and f.get("app_connected") is True)
        reader.send(f"STREAM {interval}")
        reader.wait_for(lambda f: f.get("type") == "status" and f.get("streaming") is True)
        deadline = reader.clock() + duration
        while reader.clock() < deadline:
            frame = reader.receive(deadline)
            if frame is not None and frame.get("type") == "sample":
                emit(sample_output(frame))
    finally:
        try:
            reader.send("STOP")
        except (OSError, ValueError):
            pass  # A lost link is also released by the firmware's host lease.
        reader.owned = False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    transport = parser.add_mutually_exclusive_group(required=True)
    transport.add_argument("--serial", metavar="PORT", help="Trusted USB serial port, not Bluetooth SPP")
    transport.add_argument("--tcp", metavar="HOST", help="Sensor IP on a trusted LAN (plain TCP, not TLS)")
    parser.add_argument("--port", type=int, default=8733)
    parser.add_argument("--device-id", help="Expected identity (required for TCP)")
    parser.add_argument("--duration", type=int, default=30, help="Live-reading duration in seconds, 1..3600")
    parser.add_argument("--interval", type=int, default=500, help="Output interval in ms, 150..5000")
    args = parser.parse_args()
    if not (1 <= args.port <= 65535 and 1 <= args.duration <= 3600 and 150 <= args.interval <= 5000):
        parser.error("Port, duration or interval outside the supported range")
    if args.tcp and not args.device_id:
        parser.error("--tcp requires --device-id")
    channel = None
    try:
        # Prompt BEFORE opening TCP: firmware requires authentication within 5 s.
        token = getpass("Private sensor auth token: ") if args.tcp else None
        channel = TcpChannel(args.tcp, args.port) if args.tcp else SerialChannel(args.serial)
        reader = Reader(channel)
        read_live(reader, args.device_id, token, args.duration, args.interval,
                  lambda frame: print(json.dumps(frame, allow_nan=False), flush=True))
    except KeyboardInterrupt:
        return 130
    except (OSError, RuntimeError, ValueError) as error:
        # Only our controlled errors are printed; transport-library exceptions
        # are intentionally not echoed with arbitrary device/connection details.
        message = str(error) if isinstance(error, (RuntimeError, ValueError)) else "Connection failed; check port, network and device state"
        print(message, file=sys.stderr)
        return 1
    finally:
        if channel is not None:
            channel.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
