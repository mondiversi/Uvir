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
