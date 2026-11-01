# Project status — suspended development

Uvir 0.1.1 preserves a functioning acquisition prototype whose optical design
and absolute calibration remain unfinished. Development is suspended because
the required further investigation, equipment and optics exceed the available
budget. The project remains available for study, reuse and continuation under
its existing license.

## Functional prototype

The assembled head connects an FT232H bridge, AS7331 UV sensor and AS7343
visible/NIR sensor to Android. The app recognizes the components, reads live
values and provides phone-owned sampling, gain adaptation, offsets, correction
factors, optical-window multipliers, acquisitions, sessions, alerts, archives,
diagnostics and error logs. Successful operation of these functions does not
establish the accuracy of the displayed irradiance or biological estimates.

The current reference case has the USB opening corrected from the owner's
fit measurements. A flat lid with two 5 mm apertures is included as the latest
published test variant. The mechanical files and dimensions are reproducible
references, not a claim that every replica fits without adjustment.

## Why work stopped

Comparison trials against a commercial UV meter showed discrepancies. Aperture
changes alone did not yield satisfactory agreement. Narrow tubes reject oblique
and diffuse light; a larger opening does not by itself establish correct spectral
or angular response. A constant multiplier cannot generally recover light rejected
in a direction-dependent or wavelength-dependent way.

Further work may involve compatible diffusers or other optics, suitable reference
sources/instruments, and characterization of the complete assembled head.
Lenses are not asserted to be mandatory. No specific comparison-meter brand or
model is required or endorsed. A UV comparison also does not calibrate visible,
NIR, or any UV band outside the reference instrument's documented response.

The present W/m² conversions are provisional. Do not interpret the availability
of an SI display unit, adjustable correction fields, or an automatic offset/window
wizard as proof of traceable calibration. In particular, the AS7343 color values
use discrete spectral responses and provisional conversion/grouping factors;
their sums are not validated continuous-spectrum radiometer measurements.

## What a continuation should establish

1. Define the quantity being measured and the receiving plane: directional
   illumination versus irradiance from a wider angular field.
2. Specify the full optical path, including aperture, sensor gap, any diffuser
   or protective window, optical isolation and material transmission.
3. Check each channel's response versus angle, spectrum, intensity, gain,
   temperature and saturation using appropriate references.
4. Determine dark offsets and scale factors for that final assembly, then
   verify repeatability, remaining error and usable operating conditions.
5. Record methods, reference coverage, uncertainty and limitations before
   treating results as reliable absolute irradiance measurements.

See [wiring and removable modules](../hardware/usb/README.md),
[mechanical reference](../hardware/printable/uvir-custom-case/README.md),
[calibration notes](usb/CALIBRATION.md), and
[software verification history](usb/VALIDATION.md). The complete former ESP32
implementation remains preserved on `legacy-esp32`; it is not the active head.
