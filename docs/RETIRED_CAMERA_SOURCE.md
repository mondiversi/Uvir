# Retired camera source

The experimental phone-camera irradiance source has been removed because its
spectral estimates were not sufficiently reliable. The sensor picker retains
**Add sensor**, which opens Connection settings; only associated physical sensors
are selectable. No associated hardware is represented by an em dash.

Upgrade cleanup restores the last physical preference context, preserving any
active diagnostic simulation and all hardware credentials, jobs and records.
The camera permission, image/location/weather pipeline and live model are removed.

The version-21 `camera_estimates` table remains an opaque compatibility archive
for old database backups. Existing rows are preserved by upgrades/imports and
included in full database backups; there is no active camera source or recording
API. The usual explicitly confirmed counter reset still clears this archive.

The removed prototype source was locally snapshotted before removal. No user
records are deleted by retiring the feature.
