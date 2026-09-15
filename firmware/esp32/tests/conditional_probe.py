"""Read-only HELLO probe. Never emits pairing tokens, network names or passwords."""
import json
import time
import serial

port = serial.Serial()
port.port = "COM6"
port.baudrate = 115200
port.timeout = 0.15
port.dtr = False
port.rts = False
port.open()
port.write(b"HELLO\n")
deadline = time.monotonic() + 3
last = None
while time.monotonic() < deadline:
    try:
        frame = json.loads(port.readline())
    except (ValueError, UnicodeDecodeError):
        continue
    if frame.get("type") == "hello":
        keys = ("firmware", "device_id", "offline_recording", "offline_session_id",
                "alert_monitoring_enabled", "offline_condition_plan", "operation_active",
                "conditional_acquisition_supported", "status_buzzer_enabled", "status_buzzer_volume",
                "status_led_enabled", "status_led_brightness")
        last = {key: frame.get(key) for key in keys}
port.close()
print(json.dumps(last) if last else "No HELLO received")
