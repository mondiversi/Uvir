# Printable hardware parts

## Current reference: custom case and flat 5 mm lid

Use [the Uvir custom-case package](uvir-custom-case/README.md):

| Part | Current published file |
|---|---|
| Lower case R0.3, USB opening 14.1 × 7 mm at Z=11–18 mm | [Case STL](uvir-custom-case/uvir-custom-case-base-r03.stl) |
| Flat lid R0.6, two straight 5 mm apertures, no tubes | [Lid STL](uvir-custom-case/uvir-custom-case-lid-r06-5mm.stl) |

Measured carrier/sensor positions, editable generators, lid OpenSCAD, dependency
versions and geometry checks are included. The base is the user-selected reference;
the lid remains experimental. Aperture size alone does not validate irradiance.
See [project status](../../docs/PROJECT_STATUS.md).


## Portable files

STL coordinates are in millimetres. Import at 100% scale, check the dimensions
in the slicer and generate toolpaths for the actual printer and material.
The package includes editable generators, input measurements, a lid OpenSCAD
source, previews and independent geometry checks. No printer-specific G-code
is supplied. Software mesh checks do not establish physical fit or calibration.
