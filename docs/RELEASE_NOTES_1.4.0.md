# Uvir 1.4.0 — sensor firmware 0.5.112

Calibration controls, explicit spectral references and consistent SI storage
and exports, with updated hardware construction documentation.

## Highlights

- Per-channel correction factors and offsets for UV and visible/NIR, arranged
  in equal factor/offset columns. Guided **Calculate offsets** measures the
  mean background over approximately 30 seconds and previews it before applying.
  Reset actions require confirmation. Offsets subtract background, not random
  noise, and illuminated references produce relative readings.
- Optical-window compensation uses an independent common master multiplied
  by each channel factor. All channel controls remain visible. Neutral factors
  are 1.0; firmware compatibility and idle-state guards protect edits.
- AS7331 nominal channel ranges and AS7343 discrete sensitivity peaks are
  distinguished from continuous wavelength coverage. The home headings show
  sensor models without overstating the optical response.
- Canonical irradiance storage is now **W/m²** in Android database v25 and
  firmware 0.5.112. Automatic versioned migration preserves the physical values
  of existing measurements, thresholds and offsets. Display rounding never
  changes stored measurements. The explicitly labelled `uvir-sensor-v1`
  compatibility interface continues to use µW/cm².
- Decimal places are configurable per display unit for live and saved values.
  Trailing zeros remain visible. Irradiance percentages and bars use the total
  across UV, visible and infrared readings; rounding is consistent with display
  settings. These sums describe the instrument channels, not a measurement of
  every wavelength in the entire spectrum.
- Custom CSV, readable TXT and PNG exports follow the selected unit, numeric
  format and decimal count. Scientific exports use W/m² and the full available
  stored numeric precision. Exports include sensor-model metadata and compact
  spectral references; chart legends wrap without overlapping values.
- Refined settings menu, dialog actions, numeric-input formatting and layouts.
  In-app release highlights are translated into all 20 supported languages.
- Updated complete wiring, standalone USB/Wi-Fi/Bluetooth/MQTT instructions,
  calibration and optical-window guides. Directional-head instructions describe
  two 6 mm clear-bore tubes, a 25 mm clear height above the chip windows,
  optional wall thickness, diffuser requirements and final-assembly limitations.

## Files

- `Uvir-1.4.0.apk`: signed Android application, version code 5, Android 8+.
- `UvirSensor-0.5.112-esp32.bin`: classic ESP32 application image only.
- `UvirSensor-0.5.112-usb.zip`: application, bootloader, partition table and
  separate instructions for preserving updates, recovery and first installation.
- `uvir-update.json`: signed version/length/hash index for in-app updates.
- `SHA256SUMS.txt`: checksums of the published assets.

Update the app before the sensor. Synchronize sensor records and stop all
sessions before a firmware update. Do not disconnect USB power during a write.
The single application partition has no automatic rollback; recovery uses the
application-only USB command without erasing settings or recorded data.

## Validation and limitations

All **670 JVM tests** pass, including translations and format placeholders,
calibration, optical compensation, SI migration, archive imports and export
precision. **Eight release-page/update-dialog device tests** pass, including
scrolling through all six highlights in every one of the 20 languages with
enlarged text, day/night themes and right-to-left layouts. Firmware host checks,
the ESP32 build, signing checks and local documentation/bundle checks also pass.
Prior
device checks verified migration on a private copy of an existing archive,
isolated display/export fixtures and an application-only ESP32 firmware update.
Signed release builds and firmware logic checks are repeated for this release.

The AS7331 and AS7343 modules are installed and detected on the prototype.
Final PCB/enclosure/tube/diffuser testing and traceable optical calibration
remain pending. Breadboard checks and sunlight comparisons do not establish
absolute irradiance accuracy. Published CAD and Gerbers are development
references, not fabrication approval. In-app USB-OTG flashing still needs a
dedicated physical acceptance test; computer USB flashing is verified separately.

Do not downgrade an Android archive after its SI migration. Retain pre-upgrade
backups; older firmware cannot interpret new version-3 offline records.

See [SI migration](SI_IRRADIANCE_MIGRATION.md),
[optical compensation](OPTICAL_WINDOW.md), [updates and recovery](UPDATES.md),
and [hardware construction](../hardware/README.md).
