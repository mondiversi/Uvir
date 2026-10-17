# Hardware

This directory contains the physical design and assembly documentation used
by Uvir.

## Build the complete instrument

- [Bill of materials](BILL_OF_MATERIALS.md): every fitted module, indicators,
  button, resistors, connectors and approximate dimensions.
- [Assembly and acceptance guide](ASSEMBLY.md): power, shared I2C bus, removable
  modules, optical isolation, first startup and full functional checks.
- [Complete wiring index](wiring/README.md): controller, visible/NIR, UV, RTC,
  FRAM, microSD, LEDs, buzzer and external input, with a guide for each.
- [Standalone operation](../docs/STANDALONE_SENSOR.md): use USB serial, Wi-Fi,
  Bluetooth or MQTT without the official app, including autonomous recording.
- [Calibration](calibration/README.md): the remaining final optical work.

The system's acquisition, storage, RTC and communication logic is implemented;
final calibration does not require inventing the missing wiring or protocol.
The guides distinguish this functional state from PCB fabrication approval and
calibrated measurement accuracy. CAD previews are provided with the exports;
they are not photographs or proof of an assembled board.

## Current optical modules

| Area | Breakout board | Sensor chip |
|---|---|---|
| Visible / far-red / NIR | Generic GY-AS734X | AS7343 |
| UVA / UVB / UVC | MIKROE UV 5 Click | AS7331 |

On 2026-10-12, the replacement visible-light board was identified from its
GY-AS734X silkscreen and successful AS7343 driver initialization over USB.
The driver checks the AS7343 device ID (`0x81`) at I2C address `0x39`;
this is not an identification based only on the firmware's model label.
Firmware `0.5.104` also reports the AS7331 as available. This confirms detection,
not measurement accuracy or a completed optical calibration.

The replacement board has the same dimensions as the previous board according
to the owner: approximately **22 × 13 mm**, mounted horizontally. Retain this
placement estimate; the exact socket geometry and electrical design still
require verification on the physical board. GY-AS734X is the board marking,
not a substitute for the chip model or an identified manufacturer. Driver,
logical GPIO assignments, I2C address and calibration settings are unchanged.

## Carrier board and validation

The current connected EasyEDA Standard schematic, logical pin map and
provisional front/back placement are in [carrier R1](pcb/uvir-carrier-r1/README.md).
The earlier [R0 draft](pcb/uvir-carrier-r0/README.md) is retained for reference;
its SparkFun UV-board footprint is not the current UV 5 Click footprint.
The [design exports dated 2026-10-05](pcb/uvir-carrier-2026-10-05/README.md)
add a supplied schematic, two physical-layout versions, PDF/PNG references
and a Gerber/drill archive. Publication copies have been anonymized locally;
the originals are not included. This is a separate intake collection, not a
replacement for the maintained R1 logical references. The presence of a
manufacturing archive does not mean fabrication is approved: footprint,
outline, firmware/net compatibility, library licensing and ERC/DRC review
remain required. See its [review checklist](pcb/uvir-carrier-2026-10-05/REVIEW.md).

Repeat the functional tests, dark readings and optical calibration after PCB
assembly, with the final optical isolation, enclosure/windows and normal LED
operation. Breadboard tests are provisional. See the
[calibration checklist](calibration/README.md).

Firmware source code belongs in `firmware/esp32/UvirSensor`. Keeping hardware
documents separate from firmware makes the repository easier to navigate as
the prototype evolves beyond a breadboard.
