# v0.1.0

First public **development preview of Uvir**. This is a new 0.x
development line, not an ESP32 firmware update or a calibrated instrument release.

- USB-only measuring head: FT232H MPSSE with UV 5 Click / AS7331 and
  GY-AS734X / AS7343 on one I2C bus. Android handles both chip drivers directly.
- Phone-owned sampling, per-channel correction factors/offsets, optical-window
  master/channel multipliers, acquisitions, sessions, alerts, time and storage.
- General settings combine Theme with acquisition and alert notification options.
  The original home controls and the compact settings menu are retained.
- Existing records remain readable in full-precision W/m². Display visibility,
  percentages and rounding do not alter recorded measurements.
- One phone-settings JSON export and one SQLite database export, without new
  password prompts. Earlier encrypted backups remain importable.
- Updated wiring diagram, first-connection guide and explicit hardware-validation
  checklist. Simulated readings remain available and start disabled.

## Installation

Download `Uvir-0.1.0.apk` and verify its SHA-256 against `SHA256SUMS`.
Android 8.0 or newer and USB host/OTG support are required for the physical head.
The APK uses the existing Uvir package/signing identity; updating does not require
uninstalling or clearing existing data. Stop sessions before replacing the app.

## Hardware status

FT232H hardware validation is not complete. Software tests use simulated transports and an
isolated Android verification package. Real I2C transfers, exact breakout voltage
compatibility, screen-off/OTG operation, optical response and traceable calibration
remain pending. The AS7343 conversion is a provisional datasheet estimate.
This preview does not establish measurement accuracy.

There is no ESP32, sensor firmware upload, wireless association or autonomous
sensor archive in this edition. Unplugging USB stops phone-owned sessions;
reconnection does not silently restart them.

## Preserved ESP32 edition

ESP32 users should keep **v1.4.2 / firmware 0.5.119** from `legacy-esp32`.
Its branch, complete source history and Git tags remain unchanged. The old
GitHub release pages and downloadable installers have been retired, leaving
only this Uvir prerelease. The legacy stable update feed no longer has a published
release; the Uvir APK is not delivered to ESP32 users through that feed.
Automatic USB updates remain disabled until a separate signed channel is validated.
