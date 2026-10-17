# Earlier R1 electrical references supplied with the layouts

These two JSON files were supplied together with the 2026-10-05 board exports:

- `uvir-carrier-r1-easyeda-standard-schematic.json`: a connected electrical
  schematic for EasyEDA Standard, with 13 symbols, 52 pins, 17 nets and
  69 wire segments.
- `uvir-carrier-r1-pin-netlist.json`: the logical pin map and provisional
  front/back placement; this is not a native schematic or PCB document.

They are retained unchanged as historical references. They do not contain a
physical board layout and must not be used as proof that the newer PCB has
passed electrical or mechanical review.

The accompanying intake README described the R1 electrical design, not the
new physical layouts. This English index replaces that older explanatory
text in the publication collection; it does not declare its old fabrication
checklist complete.

For the maintained R1 documentation and corrected visible-board labels, see
[the existing carrier R1 directory](../../uvir-carrier-r1/README.md).
Do not overwrite those maintained files with these historical copies.

The provisional arrangement was ESP32, visible and UV modules, LEDs and
buzzer on the front; RTC, FRAM and microSD on the back, with antenna
clearance. Actual socket dimensions, orientation, power integrity, optical
isolation and ERC/DRC remain release-review items.
