# Printable hardware parts

This directory groups the project's printable body, optical lid and internal
light shield. Each component has its own model, editable source, preview,
dimensions and mounting instructions.

> **Prototype parts: optical validation and calibration are unfinished.**
> The project is currently on hold on this work. The theoretical tube/lid
> geometry does not establish reliable irradiance measurements. See the
> [project status](../../README.md#project-on-hold--optical-design-and-calibration-unfinished)
> before using or printing the optical parts.

The selected enclosure is **TEKO COFFER-A/6.5**, nominally 125 x 70 x 35 mm.
The [manufacturer's product page](https://www.teko.it/en/products/family/TC/series/coffer-a)
provides the purchase reference and drawings. Its standard top panel is
**1 mm aluminium**. Nominal external dimensions are not internal clearances.

The project provides three separate printable parts for the current prototype:

| Part | Purpose | Model and instructions |
|---|---|---|
| Printable enclosure body R1 | Independent open-top print version based on the main COFFER-A/6 dimensions; no lid | [Body files, dimensions and reconstruction details](uvir-enclosure-body-r1/README.md) |
| Replacement optical lid R1 | Replaces the purchased enclosure's standard aluminium top panel and incorporates two optical tubes | [Lid files, dimensions and mounting](uvir-optical-lid-r1/README.md) |
| Visible/NIR light shield R1 | Four internal walls isolate the AS7343 optical path from the instrument's indicator LEDs | [Shield files, dimensions and mounting](uvir-visible-nir-shield-r1/README.md) |

The body, lid and shield are independent objects. The shield is open at both ends;
it is not a second lid or an optical window. The UV tube in the replacement lid
sets directional acceptance; it has a different purpose from the AS7343's
internal LED shield.

## Buy or print the enclosure

- **Use the purchased TEKO body:** print the replacement optical lid and the
  visible/NIR light shield. The printed lid replaces the stock aluminium panel.
- **Print the complete enclosure:** print the enclosure body, the same optical
  lid and the light shield. Buying the TEKO housing is then unnecessary; check
  the documented print clearances, fastening and connector openings.

The printable body is an independent reconstruction based on the main TEKO
dimensions. Its documented print details are not a claim of an exact OEM replica.

## Portable print files

Each part includes a **binary STL** and an **editable OpenSCAD source**, with
English filenames. STL is a widely supported model interchange format: import
it into the slicer for the printer being used, at **1:1 scale in millimetres**.
An STL does not contain reliable unit or printer/material settings; check the
documented dimensions after import. Generate the printer's own toolpath in that
slicer rather than sharing machine-specific G-code as a universal print file.

Each part also includes a CAD preview and a geometry-verification report.
The previews are renders, not photographs of fitted or tested parts.

## Assembly status

The lid and shield preserve the measured prototype dimensions. The body follows
the published housing dimensions and documents its reconstructed print details;
it is not official TEKO CAD. The existing lid and shield do not include
unmeasured screw holes, snap tabs, or added assembly clearance. Verify the lid's
retention, the shield's attachment, chip alignment, board/socket clearance and
the complete height stack on the actual enclosure before final assembly.

UV and/or visible/NIR modules may need raised mounting to fit and align with
the lid. Internal board standoffs, lid fixing holes and openings for LEDs,
controls or the ESP32 USB connector (USB-C where fitted) are user-defined.
Adjust the editable models to the actual assembly; supplied files do not
define these features for every board or module variant.

Select a material and print settings that block the relevant light, including
near infrared for the AS7343 shield; black appearance alone does not establish
opacity. Prevent light leaking under or over the walls without shading the
intended optical input. Perform the [final calibration checks](../calibration/README.md)
with these exact parts, windows and normal LED settings installed.

See [assembly](../ASSEMBLY.md#4-mechanical-and-optical-arrangement),
[directional-head variants](../README.md#directional-optical-heads-prototype)
and [optical-window guidance](../../docs/OPTICAL_WINDOW.md).
