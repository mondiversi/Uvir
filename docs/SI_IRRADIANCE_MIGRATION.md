# Irradiance units and archive compatibility

The canonical processing, SQLite and portable archive unit is **W/m²**.
`100 µW/cm² = 1 W/m² = 0.1 mW/cm²`. Display units and selected decimal places
are presentation choices; they never round or convert values in storage.

For each displayed channel the phone uses normalized raw W/m² readings,
averaging, offset subtraction and zero-clamping, correction factor and optical
master × channel multiplier. Imported measurements are not recalibrated or
compensated a second time. See [calibration](usb/CALIBRATION.md).

Older databases and files may contain explicitly versioned µW/cm² values.
The importer validates and migrates a private copy before replacement. A
pre-upgrade backup and transactional schema upgrade preserve record/session
IDs, relationships, timestamps, notes, variants and measurement-quality flags.
Conversion divides those older numeric irradiance values by 100 exactly once;
SI-labelled files are not converted again. Invalid files leave the current
archive intact. Never downgrade a migrated database to an older app schema.

The current schema retains acquisitions, alerts, sessions, counters and transfer
UUIDs, without sensor profiles or hardware-origin fields. See
[record transfer](RECORD_TRANSFER.md) for the current format and import rules.
Original encrypted backups remain unchanged; their password is requested only
when importing such an older file. Current phone-owned settings/database exports
do not introduce encryption or a password.
