# Validation status

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
  signed USB development build `2.0.0-dev`, without uninstalling it. Encrypted
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
