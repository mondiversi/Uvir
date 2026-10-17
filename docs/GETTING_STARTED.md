# Getting started with UVIR

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
