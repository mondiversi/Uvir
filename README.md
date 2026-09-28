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
- Current version: `1.3.0` (sensor firmware `0.5.103`)
- Signed GitHub update checks at startup and from Info, verified APK downloads,
  and identity-checked USB sensor updates. The app is updated before the sensor.
  See [update and recovery documentation](docs/UPDATES.md).
- Multiple associated sensors can stay connected over Wi-Fi, Bluetooth or MQTT
  while one is selected for live display, settings and commands. Switching the
  selection keeps other sensors connected and continues saving their records.
  A single USB serial device can coexist with these wireless connections; USB
  hubs with multiple serial sensors are not yet supported. The no-sensor entry
  deliberately pauses all app connections without stopping hardware sessions.
- Spectral acquisitions, automatic acquisition, saved acquisitions, sharing and export, and estimated biological effects.
- Automatic acquisition can request unrestricted battery use and holds a partial wake lock while active, improving reliability when the screen is off. Force-closing the app can still stop the session.
- Firmware 0.5.95 makes the ESP32 the single authority for real-sensor
  averaging, automatic acquisitions and value alerts over USB, Wi-Fi and
  Bluetooth or Internet. While connected, completed automatic records are delivered from
  RAM and acknowledged by Android without routine flash writes. If the app
  connection drops, acquisitions, alerts and sensor errors are queued first in
  the sensor's 32 KiB FRAM, then on microSD when that queue fills,
  and synchronized after reconnection. Queued records are removed only after
  Android confirms that they are safely stored. Automatic-session and alert
  runtime state is checkpointed redundantly in FRAM, with the card as fallback.
  A DS3231 RTC on the
  shared I2C bus keeps UTC through a power loss, so valid sessions can resume
  autonomously without inventing records for the period in which the sensor
  was unpowered. The app corrects the RTC from the phone on every connection;
  invalid or uninitialized time still blocks every durable record.
- The in-app diagnostic performs six correlated probes and reports the complete
  hardware chain: processor and memory, AS7343/AS7331/DS3231/FRAM I²C state,
  FRAM and microSD read/write health, connection transports, LED and buzzer control
  paths, and the external-input GPIO. It does not create records or change
  settings and excludes every credential.
- Settings can be backed up from **Settings → Data and restore**: the app and
  each selected associated sensor produce separate password-encrypted
  `.uvirsettings` files. Multiple sensor backups can be imported together.
  A full database export is instead a password-protected AES-256 ZIP containing
  the SQLite database; it can be opened on a computer with a compatible ZIP
  program. The app can import that encrypted ZIP after checking the password,
  database integrity and schema version. Import replaces the local records and
  sensor profiles atomically; it does not change the physical sensor.
- Before an app authenticates, Internet, Bluetooth and local Wi-Fi now take
  part in the same 20-second recovery cycle. An invalid Internet broker or
  network therefore cannot leave the sensor permanently unreachable. Internet
  authentication also expires when the app stops communicating, so closing
  Android resumes the same recovery cycle even while the broker socket itself
  remains alive.
- Averaged automatic acquisitions are collected incrementally, so the ESP32
  continues to process connection, stop and diagnostic commands between raw
  samples instead of blocking for the complete averaging interval.
- Sensor parameters now group the RGB connection/activity LED, the separate
  blue pending-synchronization LED and an optional passive piezo buzzer. The
  buzzer uses GPIO 32,
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
