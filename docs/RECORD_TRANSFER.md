# Transferring acquisitions and alerts

In **Data management → Acquisitions and alerts**, each section has separate export and import actions. Acquisitions include acquisition sessions and variants; alerts include alert sessions and their selective thresholds.

An export creates a `.uvirrecords` JSON file without a password. It contains all saved records of the chosen type, including original timestamps, notes, measurement quality and optical-window provenance. Irradiance stays in W/m² at its stored precision. Display units, rounding, hidden bands and list filters do not affect this archive. No connection credentials, sensor configuration or pairing authorization are included.

Import adds records to the current archive. It does not replace the database. A preview reports the number of records and sessions before confirmation. Records already imported from the same archive are skipped; the result reports added and skipped records. Importing an invalid or incompatible file leaves the archive unchanged. Multiple selected files are imported in one transaction.

To confirm an import, press and hold the purple **IMPORT** action for five seconds. The dialog uses the same instruction and progress feedback as deletion confirmations, with the normal day/night purple. Releasing early cancels the countdown. The same confirmation applies to encrypted settings and database imports after file selection and password entry.

## Sensor identity and local numbering

The portable source identity is the ESP32 hardware identifier, never a phone's numeric sensor ID. The receiving phone reuses its sensor row for that identifier, or creates an archive-only source with a new local ID. A new archive source is displayed by hardware identifier and is not added to paired sensors. Its exported alias does not rename a sensor on the receiving phone. After normal pairing, the user can name that same hardware and the archived records resolve the name automatically. Synthetic records retain no sensor source and display an em dash.

Acquisitions, alerts and sessions receive local IDs on import, with all session and variant relationships remapped together. Original timestamps determine chronological placement; older imports do not jump to the top simply because their local IDs are newer. Imported snapshots of ongoing sessions are closed at their last archived record and never start a recording on the receiving phone.

Each record and session also has a stable transfer UUID. These UUIDs survive re-export, allowing a file received through another phone to be recognized without exposing or reusing its local numeric IDs. Where available, native sensor record IDs additionally prevent duplication with measurements already received directly from the same hardware. Deleting a record also removes its transfer mappings so reset counters can safely reuse local numbers.

## File format

Version 1 has `format: "uvir-records"`, `version: 1`, `unit: "W/m2"` and `kind: "acquisitions"` or `"alerts"`. The `sensors`, `sessions` and `records` arrays carry hardware identities, transfer UUIDs and whitelisted measurement metadata. It is an archival exchange format, separate from readable TXT, CSV and PNG exports.

The importer validates the type, version, unit, identities, numeric values and session references before writing. It accepts up to 100,000 records and 64 MiB across a selection of at most 50 files. Transfer failures are recorded in the app's error log. Database schema 26 adds the transfer-identity table without converting or rounding existing measurements; encrypted database backups retain compatibility through an additive migration.
