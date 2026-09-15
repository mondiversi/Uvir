# Calibration

The first AS7343 firmware produces provisional irradiance estimates so the
complete acquisition path can be tested before laboratory calibration.

Before values are considered absolute measurements:

1. record dark readings for every channel with the sensor optically covered;
2. expose the assembled sensor to traceable reference sources across the
   supported spectrum;
3. determine per-channel scale factors for the final enclosure and diffuser;
4. update `firmware/esp32/UvirSensor/Calibration.h`;
5. document the reference instrument, geometry, temperature and uncertainty.

UVA, UVB and UVC calibration will be added when the AS7331 sensor is available.
