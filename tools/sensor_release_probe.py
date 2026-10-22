"""Read-only pre/post release check. Never prints or persists credentials.

Only --flash explicitly authorizes an application-only flash on the verified,
idle sensor. No sampling commands or settings changes are sent.
"""
import argparse
import hashlib
import json
import math
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


SETTINGS_KEYS = ("device_id", "auth_token", "wifi_ssid", "wifi_password", "bluetooth_pin",
            "internet_mqtt_username", "internet_mqtt_password", "settings_updated_at_ms",
            "visible_calibration_factor", "uv_calibration_factor", "calibration_channel_factors",
            "calibration_offsets", "optical_window", "samples_per_result",
            "sample_spacing_ms", "extremes_discarded", "status_led_enabled",
            "status_led_brightness", "status_buzzer_enabled", "status_buzzer_volume",
            "external_command_enabled", "autonomous_recording_enabled",
            "automatic_shutdown_enabled", "automatic_shutdown_seconds")


def fingerprint(frame):
    return hashlib.sha256(json.dumps({key: frame.get(key) for key in SETTINGS_KEYS}, sort_keys=True).encode()).hexdigest()


def settings_preserved(before, after):
    # SI conversion and the v1 readback each round to Float. All other settings,
    # including authority dates and credentials, must still match exactly.
    for key in SETTINGS_KEYS:
        left, right = before.get(key), after.get(key)
        if key != "calibration_offsets" or not isinstance(left, dict) or not isinstance(right, dict):
            if left != right:
                return False
            continue
        if {k: v for k, v in left.items() if k != "values"} != {k: v for k, v in right.items() if k != "values"}:
            return False
        a, b = left.get("values", []), right.get("values", [])
        if len(a) != len(b) or not all(math.isclose(x, y, rel_tol=2e-7, abs_tol=0) for x, y in zip(a, b)):
            return False
    return True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", default="COM6")
    parser.add_argument("--uid", required=True)
    parser.add_argument("--flash", type=Path)
    parser.add_argument("--esptool")
    parser.add_argument("--baud", type=int, default=230400)
    args = parser.parse_args()
    before = snapshot(args.port)
    assert before.get("device_id", "").upper() == args.uid.upper(), "Wrong sensor"
    assert not before.get("offline_recording") and not before.get("alert_monitoring_enabled") and not before.get("operation_active"), "Sensor must be idle"
    if args.flash:
        assert args.esptool and args.flash.is_file()
        data = args.flash.read_bytes()
        assert 24 <= len(data) <= 0x300000 and data[0] == 0xE9 and data[12:14] == b"\0\0"
        assert args.baud in (115200, 230400, 460800), "Unsupported flashing baud"
        subprocess.run([args.esptool, "--chip", "esp32", "--port", args.port, "--baud", str(args.baud),
                        "write-flash", "0x10000", str(args.flash)], check=True)
        time.sleep(3)
        after = snapshot(args.port)
        assert settings_preserved(before, after), "Settings or credentials changed"
        assert before.get("offline_acquisitions") == after.get("offline_acquisitions")
        assert before.get("offline_alerts") == after.get("offline_alerts")
        if after.get("canonical_unit") == "W/m2":
            assert after.get("si_configuration_valid") is True, "SI configuration migration did not validate"
    else:
        after = before
    print(json.dumps({"firmware": after.get("firmware"), "board": after.get("board"),
                      "device_id": after.get("device_id"), "idle": True,
                      "settings_fingerprint": fingerprint(after),
                      "settings_and_credentials_preserved": True if args.flash else None,
                      "offline_acquisitions": after.get("offline_acquisitions"),
                      "offline_alerts": after.get("offline_alerts"),
                      "uv_available": after.get("uv_available")}))


if __name__ == "__main__":
    main()
