# SI irradiance storage

## Units and precision

The canonical application, SQLite and firmware arithmetic unit is **W/m²**.
`100 µW/cm² = 1 W/m² = 0.1 mW/cm²`. Legacy irradiance numbers are divided by
100; dimensionless calibration gains, optical-window factors, the independent
window master, percentages, times, identifiers and counts are not scaled.
No UI decimal-place limit is applied to stored measurements or settings.
The conversion preserves the precision available in SQLite doubles and sensor
floats; changing units does not create additional instrument precision.

The processing order remains: normalized sensor response, subtraction and
zero-clamping of the appropriate offset, calibration gain, optical-window
master × channel factor, aggregation/conditions/recording. Archived readings
are converted in units only, never recalibrated or compensated a second time.

## Android and imports

- SQLite **version 25** converts the eleven acquisition columns, both numeric
  fields of each serialized alert violation, cached sensor alert thresholds
  and explicitly unit-labelled offset JSON. Sessions, notes, sensor identities,
  quality flags, optical provenance and settings-authority dates are preserved.
- SQLiteOpenHelper performs the upgrade in one transaction. A malformed offset
  aborts the migration rather than erasing a calibration. The schema version is
  advanced only after success. Reopening v25 does not convert again.
- A checkpointed pre-upgrade database and the old preference XML are retained
  in the application's private, non-cloud-backed `before-si` backup directory.
  Interrupted backup copies are not accepted as completed backups.
- Irradiance preferences, including inactive sensor contexts and conditional
  plans, are converted in one synchronous commit with an explicit
  `irradiance_storage_unit: W/m2` marker. New UI caches are not restored from
  unversioned pre-SI activity state. App reset retains the unit marker.
- Settings export **version 4** and sensor snapshot JSON explicitly declare
  `irradiance_unit: W/m2`; offset JSON has its own `unit` field. Older settings
  and database archives are converted on private import copies. Original
  encrypted archives remain unchanged. Reimporting a legacy archive replaces
  values from a freshly converted copy, never scales the current archive again.
- Retired camera CSV archive rows are opaque historical documents and are not
  rewritten or relabelled. No active estimator depends on them.

## Sensor compatibility

Firmware **0.5.112** converts SparkFun's AS7331 µW/cm² results to W/m² before
offset subtraction. The AS7343 conversion reference is 0.155 W/m², physically
equivalent to the former 15.5 µW/cm² reference. Limits on offsets remain
physically equivalent: 10,000 W/m² internally, 1,000,000 µW/cm² on v1 wire.

New persisted calibration uses `cal_state5` / `CAL5`, and alert rules have an
atomic SI snapshot. Older NVS calibration blocks remain available and are
decoded with their original units. Runtime journal version 3 contains SI
thresholds; versions 1/2 are converted while restoring, without changing the
configuration's authority timestamp.

New offline records are **version 3**, in W/m², using the same 264-byte layout
as v2. Old v1/v2 records retain µW/cm² and their checksums. Mixed v2/v3 queues
and FRAM records are validated by version; a v1-to-v2 layout conversion does
not mistakenly relabel its unit. Queues are not rewritten in place merely to
change units. Record IDs and acknowledgements are unchanged.

The **`uvir-sensor-v1` compatibility boundary remains explicitly µW/cm²**:
live/acquisition/offline/alert frames, alert and conditional commands, offset
configuration/readback and offset previews. Both old and new Android versions
can use this interface without silently reading values 100 times too small.
Android converts incoming physical values once and serializes outgoing v1
commands in the corresponding wire units; the durable pending-command outbox
therefore remains a v1 wire document, not an unlabelled SI settings cache.
`HELLO.canonical_unit` and `HELLO.storage_record_version` describe internal
storage only. They do not change the `unit` of a v1 response.

The Android remote sample JSON now carries its own `unit`. Third-party clients
must honor it rather than assume the former µW/cm² representation. The retired
desktop prototype is not part of this distribution; use the documented sensor
protocol or a client that explicitly supports these units.

Do not downgrade a migrated Android database to an old application. Keep the
pre-migration backup and sync/export sensor records before any firmware
downgrade; old firmware cannot interpret new v3 storage records.

## Verification and maintenance backup

Regression coverage includes physical unit equivalence, legacy/new frames,
mixed sensor record versions/checksums, zero-clamping, unchanged gains/window
factors, legacy 15-channel offsets, preferences for inactive sensors, rollback,
database reopens, encrypted imports, and live/saved day/night formatting.

The opt-in `UvirMaintenanceArchiveBackupTest` can take a checkpointed encrypted
snapshot through a same-signed instrumentation APK without replacing the
installed release or making it debuggable. It runs only with explicit backup
arguments, writes AES-256-GCM ciphertext with an RSA-OAEP-wrapped random key,
and never prints records, credentials or key material. The private decryption
key and decrypted verification copies belong outside the repository. The
local helper `tools/android_archive_backup.py` verifies encryption, ZIP integrity
and SQLite integrity. Disposable-package dry runs never modify the real app.
