# Preparation and release review

## Completed during local preparation

- Neutral filenames and project title; no personal author credited in the
  drawing frame or PDF metadata.
- Personal contributor display fields and personal 3D-model display names
  cleared or normalized. Manufacturer names, library IDs, supplier identifiers
  and component model numbers preserved.
- Personal signature removed from the V4 native silkscreen, its vector PDF
  representation and the matching top-silkscreen Gerber strokes. This is a
  real removal, not a white rectangle placed over recoverable text.
- Native non-display primitives compared before and after cleanup: pads,
  nets, wires, tracks, vias, holes, copper, coordinates and board rules
  unchanged.
- Gerber archive inspected internally, rebuilt without archive comments,
  and checked for corruption. Only the top-silkscreen member differs;
  copper, drills, masks, outline and all other members are byte-identical
  to the supplied archive.
- PDF text, metadata, decoded streams and embedded attachments checked;
  unrelated vector drawings compared before and after removal. Raster
  previews are generated from the cleaned PDFs.
- Historical R1 JSON references kept byte-identical rather than replacing
  the newer corrected R1 documents elsewhere in the repository.

These checks cover the supplied collection, not all historical hardware
files or the repository's Git history.

## Still required before a validated hardware release

- Open each cleaned native JSON in EasyEDA Standard and confirm rendering,
  library resolution and pad numbering. A JSON parser check is not a CAD
  re-import test.
- Confirm redistribution permissions and any required attribution for the
  embedded community symbols, footprints and 3D models. Library identifiers
  are retained for that review; clearing a display field does not change a
  third-party license. Replace/rebuild assets if anonymous redistribution
  is not permitted. No third-party license is granted by this collection.
- Compare the electrical schematic, the chosen PCB revision and the current
  `firmware/esp32/UvirSensor/UvirHardwareConfig.h` pin by pin. The older
  logical R1 map is not evidence that the physical layout has passed this.
- Check the visible-light footprint against the actual GY-AS734X board
  containing an AS7343. Some source library identifiers still mention
  AS7341; these have intentionally not been renamed as if that validated
  their geometry. Check the UV socket against the actual UV 5 Click.
- Measure pin 1, pin order, socket pitch/row spacing, polarity, board outline,
  mounting holes and microSD/USB access on the physical parts.
- Review outline closure and the separate internal contour in the supplied
  board-outline Gerber. Confirm whether the internal contour is an intended
  cutout, not an antenna/courtyard drawing accidentally on the outline layer.
  Review repeated hole coordinates in the supplied non-plated drill file.
- Check the ESP32 antenna copper keepout on every copper layer, the 3.3 V
  power budget, microSD 5 V supply versus 3.3 V GPIO, combined I2C pull-ups,
  and the RTC battery charging configuration.
- Run ERC and DRC; review the final Gerber/drill set in a CAM viewer and
  confirm that it corresponds to the chosen native board revision. The
  archive cleanup does not establish that correspondence.
- After assembly, repeat hardware diagnostics and optical tests/calibration
  with the final enclosure, windows, optical isolation and normal LED states.

## Scope of the privacy intake

No app, firmware, sensor settings, signing material, releases or remote Git
state was changed by the privacy intake. The later development-snapshot commit
also contains the project's app/firmware changes; it is not an installable
release or a hardware certification. The original export date and the frame's
historical design date have not been represented as a new hardware revision.
