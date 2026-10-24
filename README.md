# Uvir

Android app for spectral irradiance acquisition and estimated biological effects.

Planet Compass has been extracted into an independent Android project. Celestial
screens and the hidden logo-entry gesture are no longer included in UVIR.

## Status

Uvir is under active development. ESP32/AS7343 acquisition works over USB,
local Wi-Fi, Bluetooth and Internet through a standard MQTT broker over TLS.
The current visible/NIR breakout is marked **GY-AS734X** and contains an
**AS7343** chip. The **MIKROE UV 5 Click / AS7331** board is also installed and
detected; its UVA/UVB/UVC acquisition uses energy-saving one-shot mode.
Final-assembly testing and traceable calibration of both optical sensors will
follow PCB assembly. See [hardware identification](hardware/README.md) and the
[calibration plan](hardware/calibration/README.md).

The circuitry is intended to be housed in a **TEKO COFFER-A/6.5** enclosure.
See the [enclosure and assembly notes](hardware/README.md#planned-enclosure).

For the intended directional prototype, plan two hollow optical tubes: one
above the AS7331 UV chip and one above the AS7343 visible/NIR chip, each with a
**6 mm clear bore and 25 mm clear height above the chip's top optical window**.
Wall thickness is a mechanical choice; **2 mm is an optional rigidity example**.
See [tube mounting, diffuser requirements and validation limits](hardware/README.md#directional-optical-heads-prototype)
before making the enclosure openings or treating readings as calibrated irradiance.

## Start here

- [Setup and assembly tutorial](docs/GETTING_STARTED.md): components, GPIO map,
  app/firmware builds, first association, diagnostics and backups.
- [Complete hardware wiring](hardware/wiring/README.md),
  [parts list](hardware/BILL_OF_MATERIALS.md) and
  [assembly / acceptance checks](hardware/ASSEMBLY.md): all fitted components,
  not only the visible-light board.
- [Schematics, PCB versions and Gerbers](hardware/pcb/uvir-carrier-2026-10-05/README.md):
  anonymized design files and previews, shared as a development snapshot for
  review, **not a production-approved board**.
- [Use the sensor without the app](docs/STANDALONE_SENSOR.md): USB serial,
  Wi-Fi TCP, Bluetooth SPP, MQTT/TLS, live examples and autonomous operation.
- [Command / record protocol](docs/USB_SERIAL_PROTOCOL.md) and
  [signed app/sensor updates](docs/UPDATES.md).

Download the signed app and sensor firmware from the
[latest release](https://github.com/mondiversi/Uvir/releases/latest). The release
includes a signed update index for verified in-app downloads.

## Android app

- Package: `me.mondiversi.uvir`
- Current app version: **1.4.1**; sensor firmware: **0.5.112**.
- [Version 1.4.1 release notes](docs/RELEASE_NOTES_1.4.1.md): portable record
  transfers, persistent home-display choices, consistent spectral colors and
  five-second import confirmations. The release includes both APK and firmware.
- [Record transfer](docs/RECORD_TRANSFER.md): password-free acquisition and alert
  archives, hardware identity matching, session remapping and duplicate handling.
- [Version 1.4.0 release notes](docs/RELEASE_NOTES_1.4.0.md) document the earlier
  calibration, SI-storage and construction-guide changes.
- [Optical bands and optional window compensation](docs/OPTICAL_WINDOW.md): nominal channel coverage, correction limits and deferred hardware validation.
- Signed GitHub update checks at startup and from Info, verified APK downloads,
  and identity-checked USB sensor updates. The app is updated before the sensor.
  See [update and recovery documentation](docs/UPDATES.md).
- Multiple associated sensors can stay connected over Wi-Fi, Bluetooth or MQTT
  while one is selected for live display, settings and commands. Switching the
  selection keeps other sensors connected and continues saving their records.
  A single USB serial device can coexist with these wireless connections; USB
  hubs with multiple serial sensors are not yet supported. With no associated
  hardware, the home name is an em dash and **Add sensor** opens initial setup.
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
  On every app launch, fresh RTC oscillator-stop reports are captured before
  time synchronization and grouped in one dismissible warning listing the
  affected sensors. This suggests checking the backup cells; it does not claim
  to measure their charge or predict a nearly depleted battery.
  Phone settings include a Notifications page with Acquisition, Alerts and
  Miscellaneous sections, in that order. The clock-battery warning is enabled by
  default and can be disabled there or by unchecking the warning's "Show on
  every launch" checkbox. Both controls share one preference, included in
  encrypted app-settings backups. The Small screen/Large screen mode selector
  remains implemented but is hidden from the Settings title until both layouts
  are ready; this does not disable automatic display scaling.
  On wider displays, text, icons, spacing and touch targets scale together
  automatically while retaining the phone layout and system font scale. The
  future tablet layout will allow side-by-side sensor views when ready.
- The in-app diagnostic performs six correlated probes and reports the complete
  hardware chain: processor and memory, AS7343/AS7331/DS3231/FRAM I²C state,
  FRAM and microSD read/write health, connection transports, LED and buzzer control
  paths, and the external-input GPIO. It does not create records or change
  settings and excludes every credential.
  Live-data simulation is disabled by default, including the first launch
  without an associated sensor. When enabled, it uses a separate phone-only
  context: the home name is `—`, the status is an orange bug with "Mock data
  active", and new acquisitions/alerts have no hardware association. Their
  manual/automatic/external modes are unchanged. The sensor-selection dialog
  shows a plain explanation and an **Open settings**
  shortcut directly to the mock-data controls in Diagnostics until mock data
  is turned off; the hardware list and connection choices are hidden. The shortcut
  does not change any settings. **Simulate events** is always available, whether
  live-data simulation is on or off and whether real hardware is connected or not.
  Its generated acquisitions, alerts and sessions always have no sensor association
  and display `—`, even after returning to a real sensor. Changing either simulation
  switch keeps the current settings page and scroll position. Real connections
  and autonomous jobs continue in the background.
  Disabling simulation restores the previous real sensor's own settings and
  jobs. Mock and real session state never share a preference namespace, and
  unattributed mock records are not reassigned when a sensor is later paired.
  With no associated hardware, the home name is `—`; the selection dialog shows
  **Add sensor** rather than a selectable empty sensor profile, and the connection
  choices remain disabled until a real sensor can be configured.
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
  defaults to 20% volume, and confirms app connection, disconnection, activity
  start or stop, and every saved acquisition or alert without blocking sampling.
  Status LED brightness also defaults to 20%. These defaults apply only when no
  stored setting exists; updates preserve all existing user values, including 10%.
- Acquisition/alert notification volume sliders preview the configured sound
  after each adjustment, using Android's alarm audio stream. The sensor buzzer
  volume slider previews one short beep on the selected connected sensor when
  its firmware advertises `buzzer_preview_supported`; previews never create records.
- Android acquisitions keep their original confirmation sound; only alert
  beeps use the new rising 1800-to-2400 Hz timbre. Alerts also offer a single
  beep, distinct from acquisition feedback. Android retains the selected beep cadence, silence/vibration options
  and alarm-stream volume. The passive sensor buzzer retains one acquisition
  1400 Hz beep and three short rising alert beeps. Its 12-bit PWM volume range is capped at
  the prototype's listening-tested usable peak, not calibrated sound-pressure level.

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

Disable **Mock data** in the sensor **Diagnostics** settings before testing
the real sensor. AS7343 VIS/NIR and optional AS7331 UV values remain
datasheet-based estimates until
the assembled instrument is calibrated against a traceable reference.

## License

Licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).
