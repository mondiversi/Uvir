# Validation status

## Uvir 0.1.0 prerelease verification — 2026-10-28

- 774 JVM tests and 28 device tests pass. Driver/measurement tests cover MPSSE
  synchronization, FTDI status stripping, ACK/NACK, I2C release on failure,
  sensor register/channel order, readiness, saturation, gain normalization and
  the SI correction pipeline. These use simulated transports, not the new board.
- The owner-signed APK is `me.mondiversi.uvir`, version `0.1.0`, Android version
  code 9. It is a prerelease, not the legacy stable update.
- General replaces separate Appearance/Notifications menu entries and combines
  Theme, Acquisitions and Alerts. Device checks cover both themes and orientations,
  two-column island placement, preserved typography, reference controls and
  session navigation. Existing settings/data-backup round trips also pass.
- The GitHub prerelease contains the signed APK and its SHA-256 checksum. There
  is no FT232H firmware upload or new ESP32 firmware asset. The automatic USB
  update channel stays disabled and the ESP32 branch remains at 1.4.2/0.5.119.

## Development version line — 2026-10-28

The current Uvir preview is named `0.1.0`, starting its own development sequence.
Android's internal version code advances to 9 so the signed preview can replace
the already installed build without uninstalling it or clearing data. Earlier
`2.0.0-dev` entries below document historical local builds, not a stable release.

## Interface restoration — 2026-10-28

The USB home now reuses the original toolbar and expandable capture menu:
equally sized selectors, saved acquisitions/alerts in the center, USB/debug
status glyph and one floating capture control. Alert thresholds and scheduling
are in the bell's registration page; notifications retain sound/feedback options.
The Info page/menu label matches Space Compass's localized Info and credits.

774 JVM tests pass. Phone checks cover toolbar geometry, offline editing,
registration navigation, running-session controls, archive compatibility, and
the original capture menu's hold/drag/animation behavior in both themes.
Tests use the isolated verification package. Real FT232H validation is pending.

The spacing audit also restores the original 22 sp title/36 dp name row,
12 dp logo gap and header padding, with a small version label beside Uvir.
USB-only controls reuse the compact checkbox, description and numeric-field
components; existing shared settings retain their original spacing. Checks cover
day/night, both orientations, RTL and enlarged fonts, without changing archives.

## Software verification — 2026-10-27

- Signed development APK `2.0.0-dev` builds successfully; no stable USB release
  or ESP32 firmware replacement has been published.
- 769 JVM tests pass, including the new driver/pipeline tests and all existing
  unit regression tests. Resource catalogs are complete in all 20 languages.
- 16 instrumented tests pass on a Samsung Galaxy S10e / Android 12 in the
  disposable `me.mondiversi.uvir.verification` package: USB-only menu, day/night,
  portrait/landscape, mock acquisition/alerts, disconnect interruption, isolated
  USB-settings backup and legacy SI database migration. Offline configuration
  tests cover editable sampling/calibration, independent factor/offset updates,
  optical-master persistence, automatic-acquisition drafts and disabled reference
  collection without an adapter.
- At the owner's request, the installed real app was upgraded from 1.4.2 to the
  signed Uvir development build `2.0.0-dev`, without uninstalling it. Encrypted
  snapshots were verified before and after the upgrade; all existing database
  tables were unchanged. This is a local preview, not a stable release.
- Real FT232H transfers, electrical compatibility, optical response and
  screen-off USB recording remain pending. Other Android versions/tablets have
  not been bench-tested in this redesign.

## Software checks

Unit tests cover FTDI packet boundaries, MPSSE initialization, repeated starts,
ACK/NACK and release on failure, UV register/channel ordering, gain changes,
visible normalization, SI correction order, signed averaging, trimming,
temporary identities and interval behavior. Existing archive/format tests remain
part of the regression suite. These are simulated transport tests, not bench tests.

## Phone-owned backup regression (2026-10-28)

- Settings export is one plain JSON file containing durable phone preferences,
  USB sampling and calibration. Round-trip tests verify SI values and exclude
  running jobs, simulation state and retired connection secrets.
- Database export is one plain SQLite DB file. Tests compare every archive table
  after import, preserve the original backup, reject incompatible schemas without
  replacing existing records, and retain earlier encrypted archive support.
- Settings/database export dialogs have no password fields or source selector.
  Light/dark portrait and dark landscape checks cover the file count, available
  actions, equal-width Counters/App reset buttons and complete button labels.
- Reset confirmation remains explicit about deleting saved records and restarting
  IDs. UI verification only opens and cancels the confirmation in the disposable
  verification package; it does not reset the installed user's archive.

## Pending when the board arrives

- Inspect the exact FT232H board, serial EEPROM, power regulator and header.
- Verify the GY breakout's supply and I²C levels, common grounds and pull-ups.
- Verify 0x74/AS7331 and 0x39/AS7343 identity and block-read order on the real bus.
- Compare raw counts, integration times, gain transitions, darkness and overload
  against independent measurements. Confirm the bus needs no clock stretching.
- Repeated plug/unplug, denied/revoked USB permission, rapid reconnect, cable
  power loss, screen-off, lock/unlock, app recreation, force-stop and low battery.
- Verify acquisition/alert interruption, timestamps, counters, reference dialogs,
  calibration persistence with and without a USB serial, and archive exports.
- Validate both sensors with the final optics and reference sources. A passing
  software suite cannot establish irradiance accuracy or an angular response.

The previous ESP32 firmware and PCB can still be recovered from `legacy-esp32`.
They are not required to test the new USB path and must not drive the same I²C bus
at the same time as the FT232H.
