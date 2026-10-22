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

## Planned enclosure

The complete sensor circuitry is intended to be installed inside the
**TEKO COFFER-A/6.5** enclosure, nominally **125 x 70 x 35 mm (L x W x H)**.
See the [manufacturer's COFFER A page](https://www.teko.it/en/products/family/TC/series/coffer-a).
This identifies the selected housing, not a verified internal clearance or PCB
fit. Check mounting, module/socket height, antenna clearance and openings during
[final assembly](ASSEMBLY.md); calibrate with the final enclosure and optics.

### Directional optical heads (prototype)

The intended directional prototype uses **two straight hollow tubes**, one
centered over each chip's optical window. The common geometry below is a design
starting point, **not a manufacturer-specified accessory, a tested assembly, or
a guarantee of matching angular response or absolute measurement accuracy**.

| Dimension | AS7331 UV head | AS7343 visible/NIR head |
|---|---|---|
| Clear internal diameter | 6 mm | 6 mm |
| Clear height above the chip's top optical window | 25 mm | 25 mm |
| Wall thickness | Chosen for material and rigidity; optional 2 mm example | Chosen for material and rigidity; optional 2 mm example |
| Outside diameter with the 2 mm wall example | 10 mm | 10 mm |

The **25 mm is measured from the top optical window of the chip to the tube's
entrance**, not from the breakout PCB, socket, or enclosure lid. If the tube
stands on the PCB, add the measured height of the chip's top window above that
PCB to obtain the overall tube length. Center on the optical window, not the
center of the breakout board; keep both axes parallel and secure the holders
without pressing on the chips. Prevent lateral light leakage and isolate all
indicator LEDs. Check the complete height stack and top-panel clearance: the
enclosure's nominal 35 mm height does not establish that a socketed board plus
the optical head fits internally. The heads may need to protrude through the lid.

The **2 mm wall is optional as a dimension**, mainly to provide rigidity in a
printed prototype. Another thickness can be used if the mount is stable and
opaque over the actual UV/VIS/NIR bands while retaining the 6 mm clear bore and
25 mm clear height. Outside diameter is `6 mm + 2 x wall thickness`. Neither
black color nor a specified wall thickness proves UV/NIR opacity; check the
material. Internal surfaces must have low reflectance in the measured bands.

The tubes select incoming directions; W/m2 or W/cm2 units do not themselves
require a narrow field. In an ideal straight-ray, point-detector model, the
6 mm bore and 25 mm distance give a full cone of about
`2 x atan(3 / 25) = 13.7 degrees`. This is **not a measured field of view**:
finite photodiode positions, the package apertures, alignment, reflections and
any diffuser change the response and its cutoff. Equal tube dimensions do not
prove that every UV and visible/NIR channel sees exactly the same field.

#### Diffuser and final optical validation

A tube selects the incoming field; a diffuser redistributes the admitted light
spatially and angularly across the detector array. Shortening a tube does not
reproduce diffusion. The [AS7343 datasheet, section 11.3](https://look.ams-osram.com/m/5f2d27fff9a874d2/original/AS7343-14-Channel-Multi-Spectral-Sensor.pdf)
recommends an achromatic bulk diffuser for optimal performance. The
[AS7343 evaluation-kit manual, section 4](https://look.ams-osram.com/m/41cfc630bb4e80ba/original/AS7343_UG001009_2-00.pdf)
explains its use for direct/non-diffuse sources. A suitable diffuser and its
placement in the directional head remain to be selected and tested; the hollow
tube alone does not fulfill this optical requirement. Do not add a diffuser and
assume that the original cone estimate or calibration remains valid.

For the UV head, the [AS7331 design guide, sections 4.1-4.2](https://look.ams-osram.com/m/45d5dfb4a22ecb04/original/AS7331_Design_Guidelines_PD001048_2-00.pdf)
recommends limiting incidence at the filters to within +/-10 degrees of normal
and describes aperture geometry. A diffuser, if used on this head, must be
compatible with the measured UV bands; its material and placement also require
characterization. The angle at a chip's filters and the complete head's external
field of view are distinct design properties.

Verify the final heads' angular and spectral response, relative pointing,
stray-light rejection, dark readings and saturation, then perform the
[final-assembly calibration](calibration/README.md) with the actual enclosure,
tubes and selected diffuser installed. The software's provisional irradiance
conversion does not become an absolute calibration merely by adding tubes.

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
