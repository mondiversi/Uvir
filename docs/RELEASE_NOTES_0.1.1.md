# v0.1.1

**Development is suspended: optical calibration remains unresolved.** The app,
USB connection and sensor readout work on the assembled prototype, but reliable
absolute irradiance has not been achieved or validated. Comparison trials with
a commercial UV meter showed discrepancies; aperture changes alone were
insufficient. Further optics, characterization and reference calibration exceed
the available budget. The project remains available for anyone who wants to
study or continue it. No particular reference-meter brand or model is required.

## Included in this release

- Reference lower case **R0.3**, with the USB opening corrected to **14.1 × 7 mm**,
  from **11 to 18 mm above the outside bottom**, based on fit measurements.
- Flat experimental lid **R0.6**, **56.8 × 88.8 × 2.4 mm**, with two straight
  **5 mm** apertures and no projecting tubes. Sensor centres retain the latest
  measured alignment: 13.5 mm centre distance, UV 0.5 mm right of visible/NIR.
- Printable STLs, editable sources, measured carrier/fixing/sensor coordinates
  and reproducible geometry checks. Lid optics remain experimental.
- Functional FT232H/AS7331/AS7343 wiring and removable raised plug-in modules,
  without prescribing the prototype's individual perfboard wire routing.
- Standalone USB register reference and a Python raw-count reader, with
  synthetic tests; the independent PC reader still needs physical validation.
- USB sampling, gain settling and saturation handling improvements. Temporary
  overload no longer masquerades as a USB disconnect or valid zero measurement.
- Sensor diagnostics, a newest-first on-device error log, export/held-clear
  actions, and a live stored-log size summary.
- Combined acquisition/alert archive transfer, simplified phone-owned records,
  restored automatic-acquisition controls, and consistent saved-value presentation.
- Updated release highlights in all 20 app languages and clearer project status.

## Downloads and installation

`Uvir-0.1.1.apk` uses the existing package and signing identity, version code 10.
Android 8.0 or newer and USB host/OTG support are required for the physical head.
Update over an existing compatible installation; do not uninstall or clear its
data to update. Stop sessions before installing. Display choices never change
the full-precision W/m² archive. Verify downloads against `SHA256SUMS`.

The two STLs are attached individually and included with their editable sources
in `Uvir-Printables-0.1.1.zip`. Complete source and documentation are available
from the release tag and GitHub's source ZIP/tarball.

The FT232H head has **no firmware to upload**. This release contains the
application and current FT232H hardware documentation/printables. The automatic
update channel remains disabled; publishing this prerelease does not activate it.

## Measurement limitations

This is a functioning acquisition prototype, **not a calibrated measuring
instrument**. A larger/smaller hole, correction multiplier or displayed SI unit
does not establish accuracy. The final angular/spectral response, any compatible
diffuser/window and per-channel calibration remain unfinished. Existing physical
tests and software/mesh checks do not certify other breakout variants, every
Android configuration, or radiometric performance.

See [project status](https://github.com/mondiversi/Uvir/blob/v0.1.1/docs/PROJECT_STATUS.md),
[hardware and wiring](https://github.com/mondiversi/Uvir/blob/v0.1.1/hardware/usb/README.md),
[mechanical reference](https://github.com/mondiversi/Uvir/blob/v0.1.1/hardware/printable/uvir-custom-case/README.md),
and [verification history](https://github.com/mondiversi/Uvir/blob/v0.1.1/docs/usb/VALIDATION.md).
