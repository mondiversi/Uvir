# Current calibration status

**Development is suspended because optical calibration is unresolved and the
cost of further work exceeds the available budget.** The acquisition software
and assembled electrical prototype operate, but this does not make the current
readings reliable absolute irradiance measurements.

Comparison trials with a commercial UV meter showed discrepancies. Adjusting
aperture diameter or geometry alone was insufficient. No meter brand or model
is required; the reference must cover the actual bands, geometry and range being
calibrated. A UV comparison does not calibrate the AS7343 visible/NIR response.

## Existing software tools

Android reads and normalizes the physical channels, groups the appropriate
visible channels, averages readings, subtracts channel offsets and applies
per-channel correction factors and optical-window master/channel multipliers.
Values and offsets use W/m² internally. The effective sequence is:

```text
max(0, mean(raw W/m²) - channel offset W/m²)
    × channel correction factor × optical master × optical channel multiplier
```

The reference dialogs collect measurements and require explicit application.
Offsets remove a measured background; they do not validate spectral response or
automatically remove every noise fluctuation. Window ratios cannot recover
blocked wavelengths or generally correct direction-dependent light loss.
These tools being implemented is distinct from having determined valid coefficients.

## Remaining optical work

The reference case and 5 mm lid are mechanical prototypes. A narrow tube limits
which directions reach a chip; simply widening it is not a calibration. The
manufacturer's AS7331 design guidance distinguishes incidence on the interference
filters from the external field of view and discusses a compatible diffuser.
The AS7343 documentation recommends an achromatic diffuser. Material transmission,
placement and the complete angular/spectral response still need investigation.
No particular lens or diffuser has been selected and validated for this assembly.

For a continuation, define the intended measurement, fix the optical assembly,
check stray light and final dark offsets, compare each relevant channel with
suitable references, and determine repeatability and uncertainty across expected
gain, intensity and operating conditions. If filters/windows/diffusers or module
height change, repeat the relevant validation. The AS7343 conversion/grouped sums
remain provisional estimates rather than validated continuous-spectrum irradiance.

- [Project status and continuation checklist](../PROJECT_STATUS.md)
- [Reference mechanical files and dimensions](../../hardware/printable/uvir-custom-case/README.md)
- [AS7331 optical design guidance, §§4.1–4.2](https://look.ams-osram.com/m/45d5dfb4a22ecb04/original/AS7331_Design_Guidelines_PD001048_2-00.pdf)
- [AS7343 datasheet, §11.3](https://look.ams-osram.com/m/5f2d27fff9a874d2/original/AS7343-14-Channel-Multi-Spectral-Sensor.pdf)
