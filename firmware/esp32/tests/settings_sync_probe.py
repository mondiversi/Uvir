"""Verify dated settings without creating records or changing the user's values.

Reapplies exactly the current general/sampling/calibration configuration once;
only settings_updated_at_ms advances. Never prints HELLO credentials.
"""
import argparse
import json
import time
import serial


def read_until(port, predicate, timeout=6):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            frame = json.loads(port.readline())
        except (ValueError, UnicodeError):
            continue
        if predicate(frame):
            return frame
    raise TimeoutError("Expected sensor reply was not received")


def write(port, command):
    port.write((command + "\n").encode("ascii"))


def hello(port):
    write(port, "HELLO")
    return read_until(port, lambda f: f.get("type") == "hello")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", default="COM6")
    args = parser.parse_args()
    port = serial.Serial()
    port.port, port.baudrate, port.timeout = args.port, 115200, 0.25
    port.dtr = port.rts = False
    port.open()
    try:
        before = hello(port)
        assert before.get("firmware") == "0.5.101"
        assert not before.get("offline_recording") and not before.get("alert_monitoring_enabled"), "Wait for an idle sensor"
        boolean = lambda key: "ON" if before[key] else "OFF"
        commands = [
            f"SENSOR_CONFIG {boolean('status_led_enabled')} {before['status_led_brightness']} "
            f"{boolean('autonomous_recording_enabled')} {boolean('status_buzzer_enabled')} {before['status_buzzer_volume']} "
            f"{boolean('automatic_shutdown_enabled')} {before['automatic_shutdown_seconds']} {boolean('external_command_enabled')}",
            f"SAMPLING_CONFIG {before['samples_per_result']} {before['sample_spacing_ms']} {int(before['extremes_discarded'])}",
            f"CALIBRATION_CONFIG {before['visible_calibration_factor']} {before['uv_calibration_factor']}",
        ]
        stamp = max(int(time.time() * 1000), before['settings_updated_at_ms'] + 1)
        payload = "\n".join(commands).encode().hex()
        write(port, f"SETTINGS_APPLY {stamp} {payload}")
        confirmed = read_until(port, lambda f: f.get("type") == "hello" and f.get("settings_updated_at_ms") == stamp)
        fields = ["samples_per_result", "sample_spacing_ms", "extremes_discarded", "status_led_enabled", "status_led_brightness",
                  "status_buzzer_enabled", "status_buzzer_volume", "external_command_enabled", "autonomous_recording_enabled",
                  "automatic_shutdown_enabled", "automatic_shutdown_seconds", "visible_calibration_factor", "uv_calibration_factor",
                  "offline_recording", "alert_monitoring_enabled", "offline_used", "auth_token", "wifi_ssid"]
        assert all(confirmed.get(k) == before.get(k) for k in fields), "A configuration value changed unexpectedly"
        for old in (stamp - 1, stamp):
            write(port, f"SETTINGS_APPLY {old} {'SAMPLING_CONFIG 1 5000 0'.encode().hex()}")
            stale = read_until(port, lambda f: f.get("type") == "hello")
            assert stale['settings_updated_at_ms'] == stamp
            assert stale['samples_per_result'] == before['samples_per_result'] and stale['sample_spacing_ms'] == before['sample_spacing_ms']
        for invalid in ("SAMPLE", "SAMPLING_CONFIG 0 0 9"):
            write(port, f"SETTINGS_APPLY {stamp + 1} {invalid.encode().hex()}")
            read_until(port, lambda f: f.get("type") == "error")
            after_error = hello(port)
            assert after_error['settings_updated_at_ms'] == stamp
            assert all(after_error.get(k) == before.get(k) for k in fields)
        print(json.dumps({"firmware": confirmed['firmware'], "dated_commit": True, "older_and_equal_ignored": True,
                          "operational_commands_rejected": True, "invalid_update_not_committed": True,
                          "values_records_and_credentials_preserved": True}))
    finally:
        port.close()


if __name__ == "__main__":
    main()
