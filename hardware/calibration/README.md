# Calibration

The AS7343 visible/NIR and AS7331 UV drivers produce provisional irradiance
estimates so the complete acquisition path can be tested before traceable
calibration. Both chips are now installed and detected. The visible/NIR board
is marked GY-AS734X; its replacement still contains an AS7343, so no driver
change is required.

Definitive tests and calibration are deferred until the PCB and final optical
assembly are ready. Covered-sensor and sunlight checks on the breadboard are
preliminary checks, not a traceable absolute calibration. The 2026-10-12 board
identification update does not change existing coefficients or user settings.

Include the [proposed directional tubes and diffuser](../README.md#directional-optical-heads-prototype)
in the final optical configuration. Record the bore, the height reference, wall
material, diffuser identity and placement. Check angular response for all channels,
relative alignment of the two heads, lateral leakage and saturation. Recheck
calibration after changing these components; equal tube dimensions alone do not
establish a common field of view or absolute irradiance accuracy.

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
