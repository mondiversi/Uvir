# Hardware

This directory is reserved for the physical design and assembly documentation
used by Uvir.

Planned contents:

- breadboard wiring diagrams;
- pin assignments and I2C addresses;
- power requirements and level-shifting notes;
- schematics and PCB revisions;
- bill of materials;
- assembly photos and sensor test notes.

The first carrier-board engineering draft is in
`pcb/uvir-carrier-r0`. It contains the verified logical schematic, preliminary
placement, BOM and fabrication profile. Gerbers remain intentionally blocked
until the exact physical modules have been measured.

Firmware source code belongs in `firmware/esp32/UvirSensor`. Keeping hardware
documents separate from firmware makes the repository easier to navigate as
the prototype evolves beyond a breadboard.
