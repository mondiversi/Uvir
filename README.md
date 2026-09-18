# Uvir

Android app for spectral irradiance acquisition and estimated biological effects.

## Status

Uvir is under active development. ESP32/AS7343 acquisition works over USB,
local Wi-Fi, Bluetooth and Internet through a standard MQTT broker over TLS. Optional AS7331
UVA/UVB/UVC acquisition is implemented in
energy-saving one-shot mode and awaits hardware validation and traceable
calibration when the UV board arrives.

## Android app

- Package: `me.mondiversi.uvir`
- Current version: `1.1.0`
- Spectral acquisitions, automatic acquisition, saved acquisitions, sharing and export, and estimated biological effects.
- Automatic acquisition can request unrestricted battery use and holds a partial wake lock while active, improving reliability when the screen is off. Force-closing the app can still stop the session.
- Firmware 0.5.52 makes the ESP32 the single authority for real-sensor
  averaging, automatic acquisitions and value alerts over USB, Wi-Fi and
  Bluetooth or Internet. While connected, completed automatic records are delivered from
  RAM and acknowledged by Android without routine flash writes. If the app
  connection drops, acquisitions, alerts and sensor errors are queued locally
  and synchronized after reconnection. Queued records are removed only after
  Android confirms that they are safely stored. Automatic-session progress is
  runtime-only RAM state: it survives connection changes but intentionally
  stops after an ESP32 restart or complete power loss.
- Before an app authenticates, Internet, Bluetooth and local Wi-Fi now take
  part in the same 20-second recovery cycle. An invalid Internet broker or
  network therefore cannot leave the sensor permanently unreachable. Internet
  authentication also expires when the app stops communicating, so closing
  Android resumes the same recovery cycle even while the broker socket itself
  remains alive.
- Averaged automatic acquisitions are collected incrementally, so the ESP32
  continues to process connection, stop and diagnostic commands between raw
  samples instead of blocking for the complete averaging interval.
- Sensor parameters now group the RGB connection LED, the separate blue
  operation LED and an optional passive piezo buzzer. The buzzer uses GPIO 32,
  defaults to 10% volume, and confirms app connection, disconnection, activity
  start or stop, and every saved acquisition or alert without blocking sampling.

## Project structure

- `app/`: Android application.
- `firmware/esp32/UvirSensor/`: ESP32 firmware with separate AS7343 and optional AS7331 drivers plus the versioned Uvir USB/Wi-Fi/Bluetooth protocol.
- `relay/`: legacy experimental Uvir relay retained for reference; current Internet mode uses standard MQTT/TLS.
- `hardware/`: breadboard wiring, pinouts, schematics, bill of materials, and hardware test notes.

Third-party computers, phones and embedded USB hosts can control the sensor
without the Android app. See [USB serial integration](docs/USB_SERIAL_PROTOCOL.md)
for connection settings, commands, JSON responses, record acknowledgements and
safe offline synchronization.

## Remote Internet connection

Internet mode uses a standard MQTT 3.1.1 broker protected by TLS. Both the app
and the ESP32 establish outgoing connections, so no port needs to be opened on
the sensor's router. The broker address, TLS port and MQTT credentials are user
configuration: Uvir does not hard-code or depend on a particular provider.
Commands from the app use QoS 1; sensor events use non-blocking QoS 0, and
retained messages are disabled. Durable offline records remain protected by
Uvir's explicit `SYNC_ACK` and replay protocol. Uvir's sensor-specific
authentication remains active inside the encrypted MQTT channel.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the current module boundaries and
the rules used for gradual, low-risk refactoring.

## First sensor test

Open `firmware/esp32/UvirSensor/UvirSensor.ino` in Arduino IDE, select
**ESP32 Dev Module**, choose **Huge APP (3MB No OTA/1MB SPIFFS)** under
**Tools → Partition Scheme**, and upload it to the ESP32. The Android app
recognizes the current CP210x USB serial bridge and asks for USB access when the
sensor is connected to the phone through a USB OTG data cable.

Disable **Settings → Debug → Mock data** before testing the real sensor. AS7343
VIS/NIR and optional AS7331 UV values remain datasheet-based estimates until
the assembled instrument is calibrated against a traceable reference.

## License

Licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).
