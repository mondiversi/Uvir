# Transferring acquisitions and alerts

In **Data management → Acquisitions and alerts**, one export/import pair transfers both record families together. Acquisitions include acquisition sessions and variants; alerts include alert sessions and their selective thresholds.

An export creates one `.uvirrecords` JSON file without a password. It contains all saved acquisitions, alerts and their sessions, including original timestamps, notes, measurement quality and optical-window provenance. Both families are read in the same database snapshot. Irradiance stays in W/m² at its stored precision. Display units, rounding, hidden bands and list filters do not affect this archive. No connection credentials, sensor configuration or pairing authorization are included.

All flagged measurement groups, including historical out-of-range records, are presented as **Saturation** in the app and readable/CSV/chart exports. The database and JSON archive store numeric `quality_flags`, not translated labels: bit 1 applies to UV and bit 2 to visible/IR. These values remain unchanged, so existing databases and older imports adopt the unified terminology without rewriting measurements, notes, IDs or session relationships.

Import adds records to the current archive. It does not replace the database. A preview reports the number of records and sessions before confirmation. Records already imported from the same archive are skipped; the result reports added and skipped records. Older separate acquisition/alert files are recognized automatically. Importing an invalid or incompatible file leaves the archive unchanged. Both families and multiple selected files are imported in one transaction; a conflict in either family rolls back the whole import.

To confirm an import, press and hold the purple **IMPORT** action for five seconds. The dialog uses the same instruction and progress feedback as deletion confirmations, with the normal day/night purple. Releasing early cancels the countdown. The same confirmation applies to settings and database imports. Legacy encrypted files still request their password before import; current phone-owned exports are not encrypted.

## Phone-owned records and local numbering

Uvir has no named sensor profiles or record-origin field. Neither hardware IDs nor sensor aliases are exported or imported. Version 1 files from the ESP32 edition remain importable: their measurements and sessions are retained while sensor/external-trigger metadata is discarded. The original ESP32 behavior is preserved on the `legacy-esp32` branch.

Acquisitions, alerts and sessions receive local IDs on import, with all session and variant relationships remapped together. Original timestamps determine chronological placement; older imports do not jump to the top simply because their local IDs are newer. Imported snapshots of ongoing sessions are closed at their last archived record and never start a recording on the receiving phone.

Each record and session also has a stable transfer UUID. These UUIDs survive re-export, allowing a file received through another phone to be recognized without exposing or reusing its local numeric IDs. Deleting a record also removes its transfer mappings so reset counters can safely reuse local numbers. Native hardware IDs are no longer used for deduplication.

## File format

The combined envelope has `format: "uvir-records-bundle"`, `version: 1`, `unit: "W/m2"` and an `archives` array containing exactly one acquisitions archive and one alerts archive. Each nested version-2 archive has `format: "uvir-records"`, `unit: "W/m2"` and `kind: "acquisitions"` or `"alerts"`. The `sessions` and `records` arrays carry transfer UUIDs and whitelisted measurement metadata. There is no `sensors` array, hardware identity or external-trigger field. Older standalone version-1/2 archives remain accepted. It is an archival exchange format, separate from readable TXT, CSV and PNG exports.

The importer validates the type, version, unit, transfer identities, numeric values and session references before writing. It accepts up to 100,000 records and 64 MiB across a selection of at most 50 files. Transfer failures are recorded in the app's error log. Database schema 27 removes retired hardware metadata without converting or rounding measured values. Older SQLite/encrypted backups migrate on a private copy before replacement; invalid files leave the current database intact.
