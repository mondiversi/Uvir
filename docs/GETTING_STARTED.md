# Getting started with UVIR

> **Preserved ESP32 hardware documentation.** For the current Uvir architecture, use the
> [FT232H wiring and first-connection guide](usb/WIRING.md).

> **Project currently on hold pending optical validation and calibration.**
> The app and sensor links work; the theoretical lid/openings and final optics
> are unfinished. Current readings do not establish reliable absolute
> irradiance. Read the [prominent project status](../README.md#project-on-hold--optical-design-and-calibration-unfinished)
> before building or interpreting measurements.

UVIR combines an Android app with an ESP32-based optical instrument. This guide
covers the current development prototype, not a certified measurement device or
a production-ready PCB. Absolute irradiance and derived biological-effect values
must not be treated as calibrated safety measurements.

## 1. Choose what to try

- **Explore the interface:** build/install the app and enable mock data in
  **Settings > Sensor > Diagnostics**. Simulation is off on a fresh installation;
  its records have no real sensor association. Turn it off before testing hardware.
- **Connect an existing UVIR sensor:** install the app, use a USB OTG data cable
  for the initial association, then configure the desired wireless connection.
- **Build the instrument:** follow the component and wiring sections below.
  Review the physical modules before ordering or soldering a carrier board.

The repository's `main` branch is a development snapshot. Installable published
versions, when available, are under [GitHub Releases](https://github.com/mondiversi/Uvir/releases).
A source push does not replace an existing APK or publish a signed update.

### Units, decimal places and spectral percentages

Measurements are stored in the SI base unit `W/m²`, at the precision
received from the sensor. The unit and decimal-place preferences affect live
and saved-value displays, including chart labels, without rewriting records.
Trailing zeros are retained. **Scientific/international exports** use W/m²,
English/international formatting and the full round-trippable stored numeric
precision. **Selected/custom exports** use the selected unit, number format and
per-unit decimal count, including trailing zeros, in CSV, readable text and
chart values. Chart axes remain compact in scientific mode. Neither mode
rewrites or rounds stored readings.

Default displayed decimal places are **1 for µW/cm², 2 for W/m² and 3 for
mW/cm²**. Each unit retains its own saved choice; changing a default does not
overwrite a user's explicit choice, including zero decimal places.

CSV exports append UV and visible/infrared component-model columns; readable
tables and charts include compact model captions. Models are resolved from
each record's source profile, not the selected sensor. Missing identities use
an em dash. Earlier v1 profiles use the documented AS7331/AS7343 channel-schema
defaults when explicit component names were not cached. UV channel labels use
nominal ranges; visible/infrared labels give discrete sensitivity peaks, not
continuous colour passbands. Totals and HEV do not claim an individual passband.

Database version 25 converts older irradiances, alert thresholds and calibration
offsets once, without applying calibration or optical compensation again.
The explicitly labelled v1 sensor interface remains `uW/cm2` for compatibility;
both Android and firmware convert at that boundary. See the
[SI migration notes](SI_IRRADIANCE_MIGRATION.md) for backup and compatibility details.

By default, spectral channel percentages use the total **UV + visible + infrared** of the
same measurement, not the channel's own spectral group. Group totals and the
overlapping HEV subset are not counted again in that denominator. UI shares
use displayed, rounded irradiances so a displayed zero has an empty progress
bar; exported shares use the original values. If part of the required spectrum
is out of range, the global percentage is unavailable instead of inferred.
Alert percentages remain relative to their configured thresholds, and the
biological indices retain their separate spectral-weighting meaning.

In the **Main View only**, the underlined **% total / % group** control changes
the percentage/bar denominator of the **internal channels** independently for each
irradiance island. Each island header always keeps its percentage and bar relative
to the complete displayed total, regardless of that internal choice. Tapping a
channel row hides its reading and bar and removes its contribution from the
live displayed totals. Tap the grey row again to restore the reading. HEV is an overlapping
violet/blue aggregate: its row controls those two contributing channels, never an
extra amount added to or subtracted from the total. Live charts follow the same
inclusion choices without rewriting their raw sample history. These presentation
controls do **not** filter sensor measurements, recordings, alerts, biological
estimates, saved-value views or exports: those retain all original readings.

## 2. Hardware and wiring

The authoritative logical pin map is
[`UvirHardwareConfig.h`](../firmware/esp32/UvirSensor/UvirHardwareConfig.h).
GPIO numbers below are ESP32 GPIOs, not physical header positions.

| Part / signal | Connection | I2C address |
|---|---|---|
| ESP32 Dev Module | Classic ESP-WROOM-32 / 38-pin reference | - |
| GY-AS734X board with AS7343 | SDA 21, SCL 22, shared GND; verify breakout supply/I/O circuitry | `0x39` |
| MIKROE UV 5 Click with AS7331 | SDA 21, SCL 22, 3V3 and both GND pins; unused mikroBUS pins disconnected | `0x74` |
| DS3231 RTC breakout | SDA 21, SCL 22, 3.3 V, GND | `0x68` |
| MB85RC256V, 32 KiB FRAM breakout | SDA 21, SCL 22, 3.3 V, GND; module address defaults | `0x50` |
| Tested level-shifted microSD adapter | CS 13, SCK 18, MOSI 23, MISO 19, 5 V VCC, GND | SPI |
| Common-cathode RGB LED | Red 25, green 26, each with its own 330 ohm resistor; cathode to GND | - |
| Separate blue LED | Anode 27 through 330 ohm; cathode to GND | - |
| Low-current passive piezo buzzer | Positive 32, negative GND; other types may need a driver | - |
| External command contact | GPIO 33 to GND, active low | - |

Disconnect power before wiring. Share GND. Never apply 5 V directly to an ESP32
GPIO. The microSD supply recommendation applies only to the tested adapter with
its regulator and level-conversion circuitry, not to a bare card or every board
sold under the same generic name. Check the AS7343 breakout's regulator and
level shifting; the chip itself is not a 3.3 V part. Check combined I2C pull-ups.

Use a FAT32 microSD card. Check the RTC module's charging circuit against the
installed backup cell; a non-rechargeable cell must not be charged. A physical
module's pin order and polarity must be checked, even when its chip model matches.

For complete construction instructions, use the
[bill of materials](../hardware/BILL_OF_MATERIALS.md),
[assembly guide](../hardware/ASSEMBLY.md) and
[wiring index for every component](../hardware/wiring/README.md).
The [firmware guide](../firmware/esp32/UvirSensor/README.md) explains the implementation.

The complete circuitry is intended to be housed in a **TEKO COFFER-A/6.5**.
See the [enclosure specification and assembly notes](../hardware/README.md#planned-enclosure)
before final mounting and optical calibration.

## 3. Schematics and carrier PCB

- [Hardware overview](../hardware/README.md)
- [Connected R1 logical schematic and pin map](../hardware/pcb/uvir-carrier-r1/README.md)
- [Dated physical layouts, schematic, PDFs and Gerber archive](../hardware/pcb/uvir-carrier-2026-10-05/README.md)
- [Physical-layout review checklist](../hardware/pcb/uvir-carrier-2026-10-05/REVIEW.md)

The newer layout exports and older logical references are separate artifacts.
Presence of a Gerber ZIP does not prove correct routing, exact footprint fit,
antenna clearance, board-outline intent or electrical compatibility. The current
collection is shared for review; do not order it as a validated design. Final
assembly tests and optical calibration are still pending. Third-party library
rights are not relicensed by the project's software license.

After cloning, `python tools/check_hardware_bundle.py` verifies the bundle's
file hashes, JSON readability and ZIP integrity. It does not replace ERC/DRC
or prove that the physical circuit is correct.

## 4. Build the Android app

Clone the repository, open its root in Android Studio, and install the SDK
components requested by Gradle. The project currently uses compile/target SDK 37,
minimum SDK 26, Gradle 9.5.0 and Android Gradle Plugin 9.3.1. Use Android Studio's
compatible bundled JDK; do not assume an older system Java installation is enough.

On Windows:

```text
gradlew.bat :app:assembleDebug
gradlew.bat :app:testDebugUnitTest
```

On other supported development hosts, use `./gradlew` instead. The debug APK is
under `app/build/outputs/apk/debug/`. A new contributor does not need the owner's
private signing key: without private signing configuration, debug builds use
the normal local debug key. Such a build cannot update an already installed APK
signed by a different key. Do not uninstall an app containing a personal archive
just to work around that conflict; export/backup first or use a test device.

For owner-signed distribution, see [RELEASE.md](../RELEASE.md) and
[signed updates](UPDATES.md). Never commit signing keys, passwords, local SDK
configuration, databases or diagnostic captures. See
[device-test safety](DEVICE_TESTING.md) before any connected Android tests.

## 5. Build and install the ESP32 firmware

Use Arduino IDE with the Espressif ESP32 board package, or Arduino CLI. Select
**ESP32 Dev Module** and **Huge APP (3MB No OTA/1MB SPIFFS)**. Keep the sketch's
`partitions.csv` with its source files.

Install the libraries used by the sketch: **SparkFun AS7343 Arduino Library**,
**SparkFun AS7331 Arduino Library**, **SparkFun Toolkit**, and
**ArduinoMqttClient**, including their dependencies. The Wi-Fi, Bluetooth and
ESP32 storage APIs are supplied by the ESP32 board package.

```text
arduino-cli compile --fqbn esp32:esp32:esp32:PartitionScheme=huge_app firmware/esp32/UvirSensor
```

Review wiring and power before a first upload. For an existing sensor, synchronize
its pending records, stop active jobs and follow the preserving-update procedure
in [UPDATES.md](UPDATES.md). Do not erase flash or rewrite the partition table as
a routine update. Raw identification replies may contain association credentials:
do not paste full `HELLO` responses into public issues or logs.

## 6. Associate and verify

1. Power the assembled sensor and connect it to the Android device through a
   USB OTG data cable. Grant USB access when Android asks.
2. Use **Add sensor** to open the connection settings and complete association.
   Rename the profile if desired; the underlying hardware ID remains unchanged.
3. Confirm mock data is off. Check detected components, valid clock and storage
   in **Sensor information**, then run **Diagnostics > Diagnostic test**.
4. Check live changes under controlled light before starting a saved acquisition.
   A successful diagnostic/detection result is not an optical calibration.
5. Configure Wi-Fi, Bluetooth or MQTT/TLS only after USB association. Local Wi-Fi
   uses the same network as the phone; Internet mode needs a compatible TLS MQTT
   broker. Keep all credentials private. See the firmware/protocol guides for
   ports, authentication, connection fallback and offline acknowledgements.

Each selected profile controls its own sensor settings. Other associated sensors
may remain connected in the background. Before disconnecting or updating a sensor,
check its active job and pending synchronization state.

## 7. Records, backups and external integrations

- **Data management → Acquisitions and alerts** transfers saved measurements and
  sessions between phones using password-free `.uvirrecords` files. Imports add
  records, preserve timestamps and match sensors by hardware identity; they do
  not pair sensors or replace the existing archive. See [Record transfer](RECORD_TRANSFER.md).

- App/sensor settings exports are password-encrypted. Store both the files and
  their password securely; Android cloud backup is not a substitute.
- Database import replaces the local archive/profile database: export a backup
  first. Use a compatible AES ZIP tool for database inspection on a PC.
- [Standalone sensor operation](STANDALONE_SENSOR.md) gives concrete USB,
  Wi-Fi, Bluetooth and MQTT setup without the official app, a runnable live
  reader, and autonomous recording/recovery examples. The
  [protocol reference](USB_SERIAL_PROTOCOL.md) defines the command grammar.
  Acknowledge durable record storage correctly to avoid deleting unsaved data.
- [Calibration plan](../hardware/calibration/README.md) describes the final
  assembled optical tests. LEDs, enclosure/windows and shielding affect them.

The former celestial feature is not part of this app anymore; Planet Compass /
Space Compass is developed separately. It is not a sensor dependency.
