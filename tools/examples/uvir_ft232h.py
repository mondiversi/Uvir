"""Read AS7331/AS7343 raw counts without Android. See docs/usb/STANDALONE.md.

Only volatile measurement registers are written. No EEPROM, phone settings,
calibration, recording, automatic gain control or irradiance conversion here.
PyFtdi is imported only by main(), so host-only tests never open USB.
"""

import argparse
from datetime import datetime, timezone
import json
import math
import sys
import time


VISIBLE_GAINS = (0.5, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024)
# Same automatic-SMUX mapping as usb/SpectralChips.kt, before color grouping.
SPECTRAL = (("F1", 12), ("F2", 6), ("FZ", 0), ("F3", 7), ("F4", 8),
            ("F5", 15), ("FY", 1), ("FXL", 2), ("F6", 9), ("F7", 13),
            ("F8", 14), ("NIR", 3))


def read(port, register, count=1):
    # exchange emits a repeated START, not a STOP between index and data.
    data = bytes(port.exchange(bytes([register]), count))
    if len(data) != count:
        raise IOError(f"Incomplete register read: {len(data)}/{count} bytes")
    return data


def write(port, register, *values):
    port.write(bytes([register, *values]))


def update(port, register, mask, value):
    write(port, register, (read(port, register)[0] & ~mask) | (value & mask))


def words(data):
    if len(data) % 2:
        raise IOError("Incomplete 16-bit value")
    return [int.from_bytes(data[i:i + 2], "little") for i in range(0, len(data), 2)]


class UvSensor:
    def __init__(self, port, gain=64, sleep=time.sleep):
        if gain not in tuple(1 << n for n in range(12)):
            raise ValueError("UV gain must be a power of two from 1 to 2048")
        self.port, self.gain, self.sleep = port, gain, sleep

    def initialize(self):
        write(self.port, 0x00, 0x42)  # Configuration state, powered down.
        self.sleep(0.002)
        identity = read(self.port, 0x02)[0]
        if identity & 0xF0 != 0x20:
            raise IOError(f"AS7331 identity mismatch: 0x{identity:02x}")
        write(self.port, 0x07, 0x00)  # No digital divider.
        write(self.port, 0x08, 0x40)  # One-shot; 1.024 MHz nominal clock.
        write(self.port, 0x0B, 0x01)  # Reset register index on STOP.
        return identity

    def capture(self):
        write(self.port, 0x00, 0x42)
        self.sleep(0.002)
        gain_code = 11 - (self.gain.bit_length() - 1)
        write(self.port, 0x06, (gain_code << 4) | 6)  # 64 ms nominal integration.
        write(self.port, 0x00, 0x03)
        write(self.port, 0x00, 0x83)
        self.sleep(0.066)
        status = words(read(self.port, 0x00, 2))[0]
        for _ in range(5):
            if not status & 0x0400:
                break
            self.sleep(0.002)
            status = words(read(self.port, 0x00, 2))[0]
        if status & 0x8400 or not status & 0x0800:
            raise IOError(f"AS7331 conversion not ready or invalid: 0x{status:04x}")
        # Output registers are word-indexed: 0x02 UVA, 0x03 UVB, 0x04 UVC.
        raw = words(read(self.port, 0x02, 6))
        saturated = bool(status & 0x6000) or max(raw) >= 65534
        return dict(status="saturation" if saturated else "ok", valid=not saturated,
                    status_word=f"0x{status:04x}", gain=self.gain, integration_ms=64,
                    counts=dict(zip(("UVA", "UVB", "UVC"), raw)))

    def stop(self):
        write(self.port, 0x00, 0x42)


class VisibleSensor:
    def __init__(self, port, gain_index=0, sleep=time.sleep):
        if gain_index not in range(len(VISIBLE_GAINS)):
            raise ValueError("VIS/IR gain index must be between 0 and 11")
        self.port, self.gain_index, self.sleep = port, gain_index, sleep

    def initialize(self):
        update(self.port, 0xBF, 0x10, 0x10)
        try:
            identity = read(self.port, 0x5A)[0]
        finally:
            update(self.port, 0xBF, 0x10, 0)
        if identity != 0x81:
            raise IOError(f"AS7343 identity mismatch: 0x{identity:02x}")
        try:
            write(self.port, 0x80, 1)
            self.sleep(0.003)
            update(self.port, 0xCD, 0x80, 0)  # Controllable illumination LED off.
            update(self.port, 0xD6, 0x60, 0x60)  # Three automatic SMUX cycles.
            write(self.port, 0x81, 9)
            write(self.port, 0xD4, 0xE7, 0x03)  # ASTEP=999; 27.8 ms per cycle.
        finally:
            self.stop()
        return identity

    def capture(self):
        write(self.port, 0x80, 1)
        self.sleep(0.003)
        update(self.port, 0xC6, 0x1F, self.gain_index)
        write(self.port, 0x80, 3)
        try:
            self.sleep(0.120)
            for poll in range(40):
                if read(self.port, 0x90)[0] & 0x40:
                    data = read(self.port, 0x94, 37)  # Latch ASTATUS + all 18 ADCs.
                    status = data[0]
                    if status & 0x0F == self.gain_index:
                        raw = words(data[1:])
                        saturated = bool(status & 0x80) or max(raw) >= 10000
                        return dict(status="saturation" if saturated else "ok", valid=not saturated,
                                    astatus=f"0x{status:02x}", gain_index=self.gain_index,
                                    gain=VISIBLE_GAINS[self.gain_index], integration_ms_per_cycle=27.8,
                                    adc_counts=raw, counts={name: raw[i] for name, i in SPECTRAL})
                if poll < 39:
                    self.sleep(0.010)
            raise IOError("AS7343 conversion did not settle at the requested gain")
        finally:
            self.stop()

    def stop(self):
        write(self.port, 0x80, 0)


def attempt(operation):
    """Preserve independent module results; missing data is never a valid zero."""
    try:
        return operation()
    except Exception as error:
        return dict(status="error", valid=False, message=str(error))


def capture(sensors, initialization_errors):
    result = dict(initialization_errors)
    for name, sensor in sensors.items():
        result[name] = attempt(sensor.capture)
    return dict(type="sample", timestamp_utc=datetime.now(timezone.utc).isoformat(),
                unit="raw_counts", **result)


def arguments(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("probe", "read"))
    parser.add_argument("--url", default="ftdi://ftdi:232h/1")
    parser.add_argument("--module", choices=("all", "uv", "vis"), default="all")
    parser.add_argument("--uv-gain", type=int, choices=tuple(1 << n for n in range(12)), default=64)
    parser.add_argument("--visible-gain-index", type=int, choices=range(12), default=0)
    parser.add_argument("--samples", type=int, default=1, help="0: read until Ctrl+C; default: 1")
    parser.add_argument("--interval", type=float, default=1, help="Minimum start-to-start spacing in seconds")
    args = parser.parse_args(argv)
    if args.samples < 0 or not math.isfinite(args.interval) or args.interval < 0.1:
        parser.error("Use samples >= 0 and a finite interval >= 0.1 seconds")
    return args


def main(argv=None):
    args = arguments(argv)
    try:
        from pyftdi.i2c import I2cController
    except ImportError:
        print("Requires PyFtdi and an OS USB backend; see docs/usb/STANDALONE.md", file=sys.stderr)
        return 2
    controller = I2cController()
    sensors, errors, probe = {}, {}, {}
    try:
        controller.configure(args.url, frequency=100_000, clockstretching=False)
        candidates = {}
        if args.module in ("all", "uv"):
            candidates["uv"] = UvSensor(controller.get_port(0x74), args.uv_gain)
        if args.module in ("all", "vis"):
            candidates["vis_ir"] = VisibleSensor(controller.get_port(0x39), args.visible_gain_index)
        for name, sensor in candidates.items():
            identity = attempt(sensor.initialize)
            if isinstance(identity, dict):
                errors[name] = probe[name] = identity
            else:
                sensors[name] = sensor
                probe[name] = dict(status="recognized", chip_id=f"0x{identity:02x}")
        emit = lambda value: print(json.dumps(value, allow_nan=False), flush=True)
        if args.command == "probe":
            emit(dict(type="probe", **probe))
            return 1 if errors else 0
        if not sensors:
            emit(dict(type="probe", **probe))
            return 1
        index, had_error = 0, bool(errors)
        while args.samples == 0 or index < args.samples:
            started = time.monotonic()
            frame = capture(sensors, errors)
            had_error |= any(part["status"] != "ok" for part in (frame[k] for k in probe))
            emit(frame)
            index += 1
            if args.samples == 0 or index < args.samples:
                time.sleep(max(0, args.interval - (time.monotonic() - started)))
        return 1 if had_error else 0
    except KeyboardInterrupt:
        return 130
    except Exception as error:
        print(f"USB read failed: {error}", file=sys.stderr)
        return 1
    finally:
        for sensor in sensors.values():
            attempt(sensor.stop)
        attempt(controller.close)


if __name__ == "__main__":
    sys.exit(main())
