# Uvir 1.3.0 — sensor firmware 0.5.103

Consolidation of the Android/ESP32 platform and the first GitHub update system
with a signed index and verified downloads.

## Highlights

- Multi-sensor management: independent connections and recordings, with one
  sensor selected for display and commands.
- Acquisitions, alerts, autonomous sessions, external commands and session variants.
- Offline recording in FRAM/microSD and state recovery with the DS3231 RTC.
- CSV/TXT/PNG exports, encrypted backups and extended diagnostics.
- Refined interface, localization, accessibility and sensor selection.
- Automatic and manual update checks, an ordered version list, verified downloads,
  app updates before sensor updates, and failure logging.
- USB firmware updates with identity, idle-state and memory-layout checks.
  Settings and data are preserved.

## Files

- `Uvir-1.3.0.apk`: signed Android app (Android 8 or later).
- `UvirSensor-0.5.103-esp32.bin`: ESP32 application image only.
- `UvirSensor-0.5.103-usb.zip`: firmware and USB instructions for updates,
  recovery and first installation.
- `uvir-update.json`: signed index for in-app update checks.
- `SHA256SUMS.txt`: checksums of the published files.

## Validation and limitations

Android and firmware builds completed; JVM tests, firmware-logic tests and phone
checks performed. The physical sensor was updated from a computer over USB,
with its identity, settings, credentials and record counts checked before and
after the update, without creating artificial acquisitions.

The AS7331 UV sensor is not yet available: physical validation and calibration
remain pending. Firmware uploads directly from the app over USB OTG still
require a physical test of that connection; the protocol, safety checks and
release files are tested separately.

**Do not disconnect power during a firmware upload.** The ESP32 uses a single
application partition: automatic rollback is not available. For recovery, use
the USB procedure included in the archive without erasing the entire flash.

Documentation: [updates and recovery](UPDATES.md),
[serial interface](USB_SERIAL_PROTOCOL.md).
