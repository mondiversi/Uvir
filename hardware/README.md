# Hardware

> **USB redesign:** the active branch now targets FT232H + AS7331 + AS7343.
> See the [preliminary wiring guide](../docs/usb/WIRING.md).
> The ESP32 carrier PCB, autonomous hardware and enclosure layout below belong
> to the preserved `legacy-esp32` project; they are not a validated USB-head PCB.

This directory contains the physical design and assembly documentation used
by Uvir.

> **Project on hold: optical validation and calibration remain unfinished.**
> App/firmware acquisition and communications work, but current values are
> provisional, not validated absolute irradiance measurements. The lid and
> apertures are theoretical prototypes informed by manufacturer guidance;
> their angular response and the final diffuser/filter/window arrangement
> still need development and reference calibration. See the
> [main status notice](../README.md#project-on-hold--optical-design-and-calibration-unfinished)
> and [calibration plan](calibration/README.md).

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
- [3D-printable hardware parts](printable/README.md): purchased-housing alternative,
  independent printable body, replacement optical lid and internal visible/NIR LED shield.

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

The UV and/or visible/NIR modules may require raised mounting or spacers to
reach the intended optical-window plane and clear the other components.
Internal PCB supports, lid fixing holes and any openings for LEDs, controls
or the ESP32 USB connector (including USB-C on boards that use it) are left
to the builder. Their positions and heights depend on the actual PCB,
breakouts and sockets; no universal hole/standoff pattern is implied.

The standard housing includes a **1 mm aluminium top panel**. The project's
[printed replacement lid](printable/uvir-optical-lid-r1/README.md) takes the place
of that panel and incorporates the two optical tubes. The separate
[visible/NIR light shield](printable/uvir-visible-nir-shield-r1/README.md) isolates
the AS7343 optical path from internal indicator LEDs: its clear opening is
13 x 21 mm, height 14 mm and outward wall thickness 1.2 mm. Both models are
available as portable STL files and editable sources in the
[printable-parts index](printable/README.md); final fastening and fit remain to be checked.

For a printed body instead of the purchased housing, see the
[independent enclosure body R1](printable/uvir-enclosure-body-r1/README.md).
It uses the main 125 x 70 x 35 mm COFFER-A/6 dimensions and is geometrically
compatible with the existing lid. Reconstructed guide/support details and
printing clearances are documented separately from the manufacturer's dimensions.

### Directional optical heads (prototype)

The intended directional prototype uses **two straight hollow tubes**, one
centered over each chip's optical window. **Design for the intended light-entry
angle:** tube diameter and height are ways to obtain that angle, not mandatory
dimensions in themselves. The alternatives below are prototype starting points,
not manufacturer-specified accessories or validated optical assemblies.

For a centered point detector and non-reflecting straight walls, let `D` be the
clear bore and `L` the distance from the **chip's top optical window to the tube
entrance**. The ideal full entrance cone is `2 x atan(D / (2 x L))`; the half-angle
from the tube axis is `atan(D / (2 x L))`. Keeping roughly `D / L = 0.24` preserves
approximately the same ideal cone while changing the head's size.

| Prototype option | Clear bore D | Optical height L | Ideal full entrance cone | Ideal half-angle |
|---|---:|---:|---:|---:|
| Original geometry | 6 mm | 25 mm | 13.7 degrees | 6.8 degrees |
| Intermediate geometry | 4 mm | 16.7 mm | 13.7 degrees | 6.8 degrees |
| Compact printed lid R1 | 3 mm | 12.4 mm | 13.8 degrees | 6.9 degrees |

Exact proportional halving of the original geometry would give **3 mm / 12.5 mm**.
The current compact lid uses **12.4 mm** to accommodate the agreed mounting gap
and 0.2 mm printing layers. That small rounding accounts for its slightly wider
ideal cone. Either head can use these nominal geometries, but the response of
each fitted chip and channel must still be checked.

These values are **geometrical estimates, not measured fields of view**. Real
photodiodes occupy different positions over a finite area, which does not shrink
when the tube does. Smaller heads are therefore more sensitive to centering and
can shade channels differently even at equal `D / L`. Package openings, mounting
tolerances, reflections and any diffuser also change the response and cutoff.
The important final check is the **actual angular acceptance of all channels**;
the table alone does not establish matching response or absolute accuracy.
W/m2 or W/cm2 units do not themselves require a narrow field.

Always measure `L` from the **top optical-window plane**, not from the breakout
PCB, socket, bottom of the package opening, or lid surface. If a tube starts at
the PCB or lid, include the intervening distances. Center on the optical window,
keep the two axes parallel and secure the holders without pressing on the chips.
Prevent lateral light leakage, including across any sensor-to-lid gap, and isolate
the AS7343 optical path from all indicator LEDs. A separate UV LED shield is
not specified for the normal visible indicators; see the
[UV-response qualification](printable/uvir-visible-nir-shield-r1/README.md#why-this-shield-is-intended-for-the-as7343).
Check the complete height stack and top-panel clearance:
the enclosure's nominal 35 mm height does not establish that a socketed board
plus the optical head fits internally.

Wall thickness is a mechanical and material choice. The original **2 mm wall**
example gives a **10 mm outside diameter** with a 6 mm bore. The compact R1 uses
**4 mm walls**, giving an **11 mm outside diameter** with a 3 mm bore. In general,
`outside diameter = D + 2 x wall thickness`. Neither black color nor a specified
wall thickness proves UV/NIR opacity; check the material and keep internal
surfaces low-reflectance in the measured bands.

#### Compact printed lid R1

The [STL, editable source and dimensions](printable/uvir-optical-lid-r1/README.md)
describe a **65.4 mm wide x 120.3 mm high** rectangular lid viewed in portrait
orientation from the tube side. Both 3 mm bores pass through the lid and cylinders.
The height stack is:

| Part of the height stack | Dimension |
|---|---:|
| Assumed air gap: sensor's top optical window to lid underside | 1 mm |
| Printed lid thickness | 2.4 mm |
| Tube projection above the lid | 9 mm |
| Total optical height from sensor window to entrance | 12.4 mm |
| Total printed-part height, excluding the air gap | 11.4 mm |

These are dimensions for the current proposed assembly, not a universal enclosure
or PCB fit. Re-measure the gap and actual chip centers before mounting; changing
the gap changes the entrance angle. The included STL has passed mesh closure,
connectivity, dimensions and through-bore checks, not physical fit or optical
validation.

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
