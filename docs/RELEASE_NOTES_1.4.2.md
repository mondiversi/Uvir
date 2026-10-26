# Uvir 1.4.2 — sensor firmware 0.5.119

## Project status: on hold pending optical validation and calibration

**The app, sensor connections, acquisition and storage workflows are operational.
The current software implementation is complete for this prototype. Development
is currently on hold because the optical assembly and its calibration are not
finished. Current readings are provisional and do not establish accurate absolute
irradiance or calibrated exposure/safety assessments.**

The lid and light-entry apertures were calculated geometrically with reference
to the manufacturers' optical guidance. They are theoretical prototypes, not a
validated measuring head. Narrow tubes reject oblique and diffuse light; their
ideal entrance angle is not a measured angular response. A fixed multiplier
cannot restore light lost differently with incidence angle. The final apertures,
lid and any diffuser/filter/protective window must be developed, characterized
and calibrated together with both sensors and suitable reference sources.

The main README now makes this limitation prominent. Hardware, assembly,
printable-part and calibration guides carry the same status. Depending on the
actual PCB and sockets, UV and/or visible/NIR modules may need raised mounting
or spacers. Internal PCB standoffs, lid fixing holes, LED openings and access
to the ESP32 USB connector (USB-C where fitted) are left to the builder.

## App and firmware changes since 1.4.1

- Guided optical-window multiplier comparison uses two stable-light measurement
  stages. It proposes per-channel factors for explicit application, without
  changing calibration or creating acquisitions/alerts while sampling. This is
  a relative transmission tool, not absolute or angular-response calibration.
- UV sampling uses adaptive AS7331 gain with bounded retries and rejects
  saturated or stale conversions. Offset previews use arithmetic means for UV
  and visible/NIR; signed residuals are averaged before the final zero clamp.
  Existing offsets/factors are preserved until explicitly changed by the user.
- Fresh-reading and boot checks prevent old connection state or samples from
  being reused after sensor restarts. Firmware 0.5.119 includes Wi-Fi recovery
  and access-point selection refinements. Network availability remains dependent
  on the local network.
- Main View island bars, gaps and contents expand/collapse together. Reordering
  remains animated, and muted groups compact without changing recorded values.
- Saved acquisition/session presentation choices survive navigation and child
  record visits for the current app session. They reset when the app session
  ends; saved variants and Main View preferences remain persistent. Saved island
  headings retain their normal theme color when bands are excluded.
- The Info release highlights are updated in all 20 supported languages.

## Verification

Release checks passed: 742 JVM tests, 20 isolated Android UI tests (including
release highlights in all 20 languages), 12 firmware host-test suites plus the
calibration guard regression, a fresh ESP32 huge_app build, documentation links,
hardware bundle integrity and the private-file audit. The APK uses the existing
release certificate. These checks validate software/build behavior, not optical
accuracy or the physical fit of the printable parts. UI tests use a disposable
package and do not modify the installed app's records or sensor calibration.

## Release files

- `Uvir-1.4.2.apk`: signed Android app, version code 7, Android 8 or newer.
- `UvirSensor-0.5.119-esp32.bin`: classic ESP32 application image, huge_app layout.
- `UvirSensor-0.5.119-usb.zip`: application, bootloader and partition table, with
  separate preserving-update and first-install instructions.
- `uvir-update.json`: signed app/firmware catalog with identities, lengths and hashes.
- `SHA256SUMS.txt`: release asset checksums.

Update the app first. Synchronize queued records and stop active jobs before
updating firmware. Application-only USB updates preserve NVS, FRAM and microSD.
The single firmware application partition has no automatic rollback; keep USB
power connected and use the documented recovery procedure if interrupted.

See [project status](../README.md#project-on-hold--optical-design-and-calibration-unfinished),
[calibration](../hardware/calibration/README.md), [printable hardware](../hardware/printable/README.md),
[optical windows](OPTICAL_WINDOW.md) and [updates/recovery](UPDATES.md).
