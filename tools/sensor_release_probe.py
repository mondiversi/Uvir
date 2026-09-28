"""Read-only pre/post release check. Never prints or persists credentials.

Only --flash explicitly authorizes an application-only flash on the verified,
idle sensor. No sampling commands or settings changes are sent.
"""
import argparse
import hashlib
import json
import subprocess
import time
from pathlib import Path
import serial


def snapshot(port_name):
    port = serial.Serial()
    port.port, port.baudrate, port.timeout = port_name, 115200, 0.25
    port.dtr = port.rts = False
    port.open()
    try:
        for _ in range(4):
            port.write(b"HELLO\n")
            deadline = time.monotonic() + 3
            while time.monotonic() < deadline:
                try:
                    frame = json.loads(port.readline())
                except (ValueError, UnicodeError):
                    continue
                if frame.get("type") == "hello":
                    return frame
        raise RuntimeError("Sensor HELLO not received")
    finally:
        port.close()


def fingerprint(frame):
    keys = ("device_id", "auth_token", "wifi_ssid", "wifi_password", "bluetooth_pin",
            "internet_mqtt_username", "internet_mqtt_password", "settings_updated_at_ms",
            "visible_calibration_factor", "uv_calibration_factor", "samples_per_result",
            "sample_spacing_ms", "extremes_discarded", "status_led_enabled",
            "status_led_brightness", "status_buzzer_enabled", "status_buzzer_volume",
            "external_command_enabled", "autonomous_recording_enabled",
            "automatic_shutdown_enabled", "automatic_shutdown_seconds")
    return hashlib.sha256(json.dumps({key: frame.get(key) for key in keys}, sort_keys=True).encode()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", default="COM6")
    parser.add_argument("--uid", required=True)
    parser.add_argument("--flash", type=Path)
    parser.add_argument("--esptool")
    args = parser.parse_args()
    before = snapshot(args.port)
    assert before.get("device_id", "").upper() == args.uid.upper(), "Wrong sensor"
    assert not before.get("offline_recording") and not before.get("alert_monitoring_enabled") and not before.get("operation_active"), "Sensor must be idle"
    if args.flash:
        assert args.esptool and args.flash.is_file()
        data = args.flash.read_bytes()
        assert 24 <= len(data) <= 0x300000 and data[0] == 0xE9 and data[12:14] == b"\0\0"
        subprocess.run([args.esptool, "--chip", "esp32", "--port", args.port,
                        "write-flash", "0x10000", str(args.flash)], check=True)
        time.sleep(3)
        after = snapshot(args.port)
        assert fingerprint(before) == fingerprint(after), "Settings or credentials changed"
        assert before.get("offline_acquisitions") == after.get("offline_acquisitions")
        assert before.get("offline_alerts") == after.get("offline_alerts")
    else:
        after = before
    print(json.dumps({"firmware": after.get("firmware"), "board": after.get("board"),
                      "device_id": after.get("device_id"), "idle": True,
                      "settings_fingerprint": fingerprint(after),
                      "settings_and_credentials_preserved": bool(args.flash),
                      "offline_acquisitions": after.get("offline_acquisitions"),
                      "offline_alerts": after.get("offline_alerts"),
                      "uv_available": after.get("uv_available")}))


if __name__ == "__main__":
    main()
