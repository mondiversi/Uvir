# Calibration

> **Development is on hold until the optical assembly and calibration can be
> completed.** App and sensor communication/acquisition are operational, but
> current readings are provisional and do not establish accurate absolute
> irradiance. The theoretical lid/aperture dimensions are not a completed
> optical design or a calibration certificate.

Resolve the intended measurement geometry first: a narrow directional tube
can reject oblique and diffuse light rather than measure total ambient
irradiance. Validate the final lid, apertures and any diffuser/filter/window
for each channel before setting reference scale factors. Raising a module,
changing its sensor-to-lid gap or modifying an opening changes this assembly
and requires another optical check. A single multiplier cannot correct
angle-dependent light rejection.

The AS7343 visible/NIR and AS7331 UV drivers produce provisional irradiance
estimates so the complete acquisition path can be tested before traceable
calibration. Both chips are now installed and detected. The visible/NIR board
is marked GY-AS734X; its replacement still contains an AS7343, so no driver
change is required.

Definitive tests and calibration are deferred until the PCB and final optical
assembly are ready. Covered-sensor and sunlight checks on the breadboard are
preliminary checks, not a traceable absolute calibration. The 2026-10-12 board
identification update does not change existing coefficients or user settings.

The planned final housing is **TEKO COFFER-A/6.5**; see the
[enclosure notes](../README.md#planned-enclosure). Perform final dark/reference
checks with the circuitry mounted in that housing, including the final sensor
openings/windows, optical barriers and normal LED configuration.

Include the [replacement lid and visible/NIR light shield](../printable/README.md)
in this final configuration. The shield is intended to exclude internal LED
light from the AS7343. A separate shield around the AS7331 is not specified for
the normal visible indicators; still compare readings with those LEDs off and
on before dismissing possible residual interference.

Include the [proposed directional tubes and diffuser](../README.md#directional-optical-heads-prototype)
in the final optical configuration. The design reference is the **light-entry
angle**, not a mandatory tube size: the 6 / 25 mm, 4 / 16.7 mm and compact
3 / 12.4 mm bore/optical-height options have similar ideal cones. Check angular
response and calibration when changing between variants. Record the bore,
optical height from the chip's top window, sensor-to-lid gap, wall material, diffuser identity and
placement. For the compact lid, verify the actual `1 + 2.4 + 9 = 12.4 mm` height
stack rather than using the 9 mm projection as the optical height. Check angular
response for all channels, relative alignment of the two heads, lateral leakage
and saturation. Recheck calibration after changing these components; equal
dimensions or ideal angles alone do not establish a common field of view or
absolute irradiance accuracy.

Before values are considered absolute measurements:

1. test the assembled hardware and check for optical leakage from indicator
   LEDs, including the LEDs fitted on the breakout boards; use the final
   opaque barriers, enclosure and optical windows with normal LED operation;
2. record dark readings for every visible/NIR and UV channel with the optical
   inputs fully covered, at the gain and integration settings being tested;
3. expose the assembled sensor to traceable reference sources across the
   supported spectrum;
4. determine per-channel dark offsets and scale factors for the final optics,
   and verify their behavior across the supported gain/integration settings;
5. update `firmware/esp32/UvirSensor/Calibration.h` and the AS7331 calibration
   path as appropriate, keeping visible/NIR and UV corrections separate;
6. document the reference instrument, geometry, temperature and uncertainty.

Do not derive absolute visible/NIR or UV scale factors from sunlight alone.
The AS7331 UVC channel also needs a suitable UVC reference; an outdoor residual
by itself neither establishes real UVC exposure nor validates that channel.

## Editable offsets

Firmware 0.5.108+ supports eleven color/channel irradiance offsets, initially
zero. Firmware 0.5.109+ also supports eleven absolute multiplicative factors.
The Android calibration editor places UV first, then visible/NIR, with equal
factor/offset columns and one row per displayed channel. Legacy module factors
initialize the rows without changing measurements or saving on open. Offsets
follow the selected display unit. Since firmware 0.5.112 and database v25 they
are stored internally in W/m2; only the explicitly labelled v1 compatibility
interface exchanges uW/cm2. See the [SI migration notes](../../docs/SI_IRRADIANCE_MIGRATION.md).
Firmware 0.5.110 adds **Calculate offsets**, independently for UV and
visible/NIR. It samples for about 30 seconds before user corrections, previews
the proposed offsets and changes nothing until **Set offsets** is confirmed. It is
not necessary to reset the existing gains, offsets or window factors first.
Since **0.5.114**, UV and visible/NIR both use **per-channel arithmetic means**.
This supersedes the maximum-based UV proposal in 0.5.113. The updated app requires
0.5.114+ for UV calculation and identifies the method in the preview. AS7331 gain
adapts automatically from 1x to 2048x at fixed 64 ms integration, then stays fixed
after settling the first reference sample. Existing offsets, gains, window
factors and records are unchanged until a new proposal is applied.
For sensor dark-bias calibration, use the final opaque barriers, normal LED operation and completely covered optical inputs. Collect
a distribution rather than one sample, and verify at the operating temperature,
gain and integration settings. A mean offset corrects the average background;
signed corrected color readings are averaged before the final zero clamp to
avoid positive clipping bias. No new deadband is applied: small fluctuations can
remain. A fixed offset does not adapt to later changes in noise, temperature or
gain, so validate the correction over the intended operating range.

A stable illuminated reference can instead be used for deliberate background
subtraction. That also subtracts real light present during the reference interval,
so subsequent readings are relative to that condition, not absolute irradiance.
It is not an automatic rejection of bad measurements or random-noise filtering.
Recalculate with genuinely dark, covered inputs to return to dark-bias correction.
The UI uses the generic name **Offsets**, including Sensor Info and its report;
the mean-based calculation and existing settings/protocol formats are unchanged.

Offsets are subtracted once per displayed color, after physical-channel
normalization and the usual paired-channel means, before the channel factor
(or the original module factor if no channel factors have been configured).
Since 0.5.114, negative residuals remain signed through the configured average;
only the final result is floored at zero. Instantaneous one-sample outputs and
condition checks remain nonnegative.
Violet, blue, green-yellow and far-red each have one offset, not one per peak.
HEV and group totals receive no additional offset. Older physical-channel
configurations retain their original semantics until explicitly edited.
Do not also encode the same dark correction in `Calibration.h` and runtime
offsets. See [implementation and limitations](../../docs/OPTICAL_WINDOW.md#channel-dark-offsets).

## Protective windows

Window compensation is separate from calibration. The current editor always
presents a common master multiplier and all per-channel multipliers, with
**1.0 meaning no correction**. Each channel's final multiplier is
**master × channel**; there is no activation/customization checkbox. This master
behavior requires firmware 0.5.111+ and an idle, connected sensor; 0.5.112 is
included with app 1.4.0.
Justified factors apply before sampling, conditions, alerts and offline saves.
They do not correct historical records or recover blocked wavelengths. See
[window selection, compensation and validation status](../../docs/OPTICAL_WINDOW.md).

Use UV-grade synthetic fused silica with documented transmission over the
required UV/VIS/NIR response; nominal 1 mm is the starting recommendation for
small supported windows, subject to the final mount. Keep that exact window,
optical geometry and normal LED configuration in the final reference tests.
If the complete instrument is calibrated with the window installed, its loss
is normally already included: leave separate window factors at 1.0 unless the
procedure explicitly separates them. Do not apply the same correction twice.
If calibration was performed without the window, derive its additional factors
from applicable transmission data or controlled comparisons, not an assumed
uniform 10% loss.

## Reproducible dark and response checks

Keep a private test log with firmware version, component models, enclosure/window
configuration, current calibration factors, sampling count/spacing, per-result
gain/integration settings, saturation state, timestamps and every spectral band.
The [standalone guide](../../docs/STANDALONE_SENSOR.md) explains how to read live
samples without adding records to an Android archive. Keep provisioning replies
out of the measurement log.

1. Finish the mechanical build and isolate all LEDs optically; do not unplug
   normal indicators merely to obtain a better-looking dark reading.
2. Cover both optical inputs with material opaque over the relevant bands,
   including NIR. Allow startup/averaging and any automatic gain change to settle,
   then collect a repeatable dark interval rather than one sample.
3. Uncover and compare against controlled/reference illumination in the same
   geometry. Record changes of gain, saturation and environmental conditions.
4. Repeat the dark check to detect drift/leakage. Preserve the raw observations
   before proposing offsets or factors; do not overwrite user coefficients just
   because a covered value is not exactly zero.
5. Only apply justified corrections, separately for visible/NIR and UV, then
   repeat at more than one illumination level and relevant gain/integration setting.

These checks can establish repeatability and reveal LED leakage now. They do
not supply an absolute reference or a validated uncertainty budget by themselves.
The instrument's functional completion and the remaining optical calibration are
separate from the [PCB release review](../pcb/uvir-carrier-2026-10-05/REVIEW.md).
