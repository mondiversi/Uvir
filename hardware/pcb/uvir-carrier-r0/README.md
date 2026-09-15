# Uvir Carrier PCB R0

Status: **engineering draft — not ready to order**.

This package fixes the electrical architecture and the intended physical
arrangement of the first Uvir carrier board. It deliberately does not contain
Gerber or drill production files yet: the exact physical ESP32 and GY-AS7343
module dimensions have not been verified. A correct logical pinout is not a
substitute for a mechanical footprint.

## Intended arrangement

From the USB edge toward the top edge:

1. ESP32 38-pin development board, USB connector accessible through the lower
   enclosure opening;
2. antenna keep-out, with no copper, ground plane, traces or components below
   the ESP32 antenna;
3. GY-AS7343 visible/NIR sensor, mounted horizontally and facing the front
   optical window;
4. SparkFun AS7331 UV sensor, mounted horizontally and facing its own front
   optical window;
5. opaque optical divider;
6. blue LED, common-cathode RGB LED and passive buzzer at the top edge. The LEDs
   use right-angle packages or light pipes so their output exits from the top
   face rather than shining toward the sensors. The buzzer may remain flat and
   use an acoustic duct to the top face.

Provisional carrier outline: **40 x 125 mm**, two copper layers, 1.6 mm FR-4.
This size is only a placement target and must be recalculated from the verified
footprints.

## Authoritative logical connections

- I2C SDA: GPIO 21;
- I2C SCL: GPIO 22;
- AS7343 address: `0x39`;
- AS7331 address: `0x74`;
- RGB red: GPIO 25 through 330 ohm;
- RGB green: GPIO 26 through 330 ohm;
- operation LED blue: GPIO 27 through 330 ohm;
- passive piezo buzzer: GPIO 32 and GND;
- sensor supply: 3.3 V after verification that each breakout contains the
  required regulator/level shifting.

## Files

- `uvir-carrier-r0-schematic.svg`: readable electrical schematic;
- `uvir-carrier-r0-layout.svg`: dimensioned placement drawing;
- `uvir-carrier-r0-pin-map.csv`: connector/net mapping;
- `uvir-carrier-r0-bom.csv`: preliminary bill of materials;
- `uvir-carrier-r0-cpl.csv`: preliminary placement list;
- `uvir-carrier-r0-board-outline.dxf`: provisional 40 x 125 mm outline;
- `fabrication-settings.md`: recommended fabrication settings and the exact
  checks required before Gerber generation.

## Measurements still required

Measure the physical parts with a caliper and record:

- ESP32 board width and length;
- center-to-center distance between its two 19-pin rows;
- location of pin 1, first/last pin and USB overhang;
- GY-AS7343 board width/length and exact order/position of its six pins;
- confirmation that the AS7331 is SEN-23517 Standard (25.4 x 25.4 mm), not the
  25.4 x 12.7 mm Mini version;
- buzzer diameter, height and lead spacing;
- LED package diameter and lead spacing;
- desired enclosure wall thickness and mounting-hole positions.

Only after these values are checked should the order bundle be generated:
Gerber X2/RS-274X, Excellon drill, BOM and component-position CSV. Those formats
are accepted by the main PCB fabrication services without tying the design to a
single provider.
