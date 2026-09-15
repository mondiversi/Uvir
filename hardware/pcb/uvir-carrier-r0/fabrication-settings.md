# Fabrication settings — Uvir Carrier R0

These settings are suitable for a low-cost first prototype after all footprints
have been verified.

- Layers: 2
- Material: FR-4
- Finished thickness: 1.6 mm
- Copper: 1 oz outer layers
- Solder mask: green or black
- Silkscreen: white
- Surface finish: lead-free HASL; ENIG is optional, not required for the carrier
- Minimum signal track: 0.25 mm
- 3.3 V and GND tracks: 0.50 mm or wider
- Via: 0.60 mm pad / 0.30 mm drill or the fabricator's standard
- Copper-to-edge clearance: at least 0.30 mm
- Ground pour: both layers, except the ESP32 antenna keep-out
- Controlled impedance: not required
- Castellations, blind/buried vias and edge plating: not required
- Panelization: fabricator standard

## Required production bundle

The final ZIP should contain:

- top and bottom copper Gerbers;
- top and bottom solder-mask Gerbers;
- top and bottom silkscreen Gerbers;
- board-outline Gerber;
- plated and non-plated Excellon drill files;
- Gerber job file when supported;
- BOM CSV and component-position CSV only if assembly is requested.

## Do not order R0 yet

The current DXF, placement CSV and drawings express the intended architecture,
but U1/U2 header positions and several mechanical component footprints remain
unverified. Generating Gerbers before measuring them would create a board whose
electrical netlist could be correct while the real modules do not physically fit.
