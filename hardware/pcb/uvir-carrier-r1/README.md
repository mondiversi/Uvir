# Uvir Carrier R1 — EasyEDA Standard

## File to open in EasyEDA

`uvir-carrier-r1-easyeda-standard-schematic.json` is a **native EasyEDA Standard
electrical schematic**. The first version loaded the components but used only
short wire stubs with net names, so they appeared disconnected. This version
uses continuous wires and explicit junctions. It contains 13 symbols, 52 pins,
17 nets and 69 segments. The generator checks that every net is electrically
continuous in the drawing, rather than merely sharing a label.
The new version has not yet been reimported and visually checked in the EasyEDA
editor.

In EasyEDA **Standard** for PC:

1. Open `File > Open > EasyEDA...`.
2. Select `uvir-carrier-r1-easyeda-standard-schematic.json`.
3. Leave **Import File** selected and confirm.

Do not open the file by double-clicking it in Windows or choose Altium, Eagle
or KiCad. `uvir-carrier-r1-pin-netlist.json` is the reference logical pin map,
**not** a native EasyEDA document.

## Pin mapping and placement

The ESP32 symbol shows the `J2` (left row) and `J3` (right row) positions when
viewing the front of the board with USB at the bottom. These follow the
user-supplied `esp32_pin_ref.jpg` reference photo and agree with the 38-pin
ESP32-DevKitC pinout. The actual row spacing and pin pitch must still be measured
on the board before designing the sockets.

The **electrical schematic** arranges blocks to make the wiring readable; it
does not represent the physical PCB layout. Intended placement is recorded in
the logical pin map's `layout` section:

- **Front, bottom to top:** ESP32 with USB facing the bottom edge; horizontal
  AS7343 visible/NIR board above it; horizontal UV 5 Click board further up;
  buzzer, RGB LED and blue LED at the top. Side button on the left, slightly
  above the midpoint.
- **Back, bottom to top:** transverse RTC; clear ESP32 antenna area; FRAM above;
  microSD adapter at the top with the card accessible from the edge.
- **Approximate dimensions:** ESP32 25×52 mm; AS7343 22×13 mm;
  UV 5 Click 28.6×25.4 mm; RTC 37×22 mm; FRAM 20×15 mm;
  microSD 24×43 mm. These are not sufficient for fabrication.

The four I²C peripherals share GPIO21/SDA, GPIO22/SCL, 3.3 V and GND.
The microSD module uses GPIO13/18/19/23 for SPI, and only its VCC uses 5 V,
following the previously tested adapter with a regulator and level shifting.
Each LED has its own 330 Ω series resistor. Pressing the external button pulls
GPIO33 to GND: the chosen terminals are on opposite sides of the contact.

## Requirements before ordering the PCB

This document defines the connections; it is **not** a PCB layout. The module
symbols do not yet have physical footprints assigned. Before generating Gerbers:

- Measure pin 1, pin order, pitch and socket row spacing for every module,
  as well as LED/buzzer polarity and microSD orientation.
- Create/assign the footprints and place them on the actual front/back layout.
- Check the 3.3 V regulator budget, existing module I²C pull-ups, optical
  isolation and the antenna copper keepout.
- Check that RTC charging is disabled when using a CR2032.
- Run ERC, DRC and a human review before ordering.

`generate_easyeda_standard.py` regenerates the file from the logical pinout and
stops if a pin is missing or duplicated, a net is discontinuous, or the GPIO map
does not match `UvirHardwareConfig.h`.
The actual generator is `generate_connected_easyeda.py`; the original name
remains available for compatibility.
