# Uvir 1.4.1 — sensor firmware 0.5.112

Portable acquisition and alert archives, clearer spectral displays and consistent import confirmations. This release includes the signed Android APK and a fresh build of the existing ESP32 firmware 0.5.112.

## Record transfer

- **Data management → Acquisitions and alerts** exports and imports password-free `.uvirrecords` files, separately for acquisitions and alerts, including their sessions. Original timestamps, notes, variants, selective alert thresholds, optical-window provenance and full stored W/m² precision are retained.
- Import adds to the current archive and allocates local record/session IDs together. The portable sensor identity is its hardware ID. Existing hardware is matched to its local profile; unknown sources display their hardware ID and are not paired automatically. Pairing that hardware later lets its archived records resolve the local display name.
- Stable transfer identities prevent duplicate imports, including files re-exported through another phone. Native sensor record identities additionally reconcile directly synchronized data. Database schema 26 adds this identity bookkeeping without changing existing measured values. Invalid imports roll back the whole operation. Chronological placement uses original measurement timestamps.
- All import confirmations use the normal day/night purple and require five continuous seconds, with the same instruction and progress feedback as deletion confirmations. Releasing early cancels the countdown. Import and red Cancel have a consistent order. Encrypted settings/database and legacy plaintext settings follow the same confirmation rule.

## Display and exports

- The Main View remembers island order and included bands. Reordering is animated in portrait and landscape. Muted groups retain neutral placeholders and empty tracks. Every original reading continues to be recorded and exported.
- Saved acquisitions and acquisition sessions offer temporary inclusion controls and total/group percentage bases. These display choices reset when the relevant view is recreated; Main View preferences remain persistent. Selective alerts keep their existing threshold-oriented presentation.
- Spectral colors share a single palette across live views, saved records, thresholds, charts and exports. Saved band names use normal day/night text with a colored marker on the left. Session record rows use a consistent full-row action.
- Acquisition TXT/CSV tables include both total and group percentage columns. These extra columns do not apply to selective alerts or PNG charts. Sensor model and spectral-reference metadata remain available in exports. Scientific exports retain W/m² and full stored precision; custom exports follow selected units and decimal places.
- Refined dialog actions, disabled states, mock-data notifications and USB-update readiness/recovery checks. All six in-app release highlights are translated into the 20 supported languages.

## Updated 1.4.1 publication

The existing 1.4.1 release now includes the final data-management dialog refinements, without changing the app version, version code or firmware version:

- Database, error-log, settings, acquisition and alert exports use a red **Cancel** action and purple **Save/Share** actions, with day/night colors. Reset confirmations retain the red reset action first and purple **Cancel** second.
- Acquisition and alert exports have dedicated titles, a concise single-file description and a **JSON files: 1** summary. Settings exports include an introductory description above the selections.
- Export, import, reset, deletion, session and simulation confirmations use present-tense action wording. The 20 language catalogs and applicable plural forms are checked; operation behavior and hold durations are unchanged.

The APK, signed update catalog and checksums have been regenerated from the updated source. The sensor firmware remains 0.5.112.

## Release files

- `Uvir-1.4.1.apk`: signed Android app, version code 6, Android 8 or newer.
- `UvirSensor-0.5.112-esp32.bin`: freshly compiled classic ESP32 application image.
- `UvirSensor-0.5.112-usb.zip`: application, bootloader and partition table, with separate preserving-update and first-install instructions.
- `uvir-update.json`: signed catalog with asset identities, lengths and SHA-256 hashes.
- `SHA256SUMS.txt`: published asset checksums.

The firmware implementation and its independent version number are unchanged from release 1.4.0. Users already running 0.5.112 do not need to flash it again. Older sensors can use the firmware included in this release after updating the app, synchronizing all records and stopping every session. Application-only USB updates preserve NVS, FRAM and microSD; keep USB power connected during flashing.

## Verification and hardware status

Release verification covers JVM tests, isolated Android archive/identity/migration tests, display and import-confirmation fixtures, localized release highlights, firmware host checks, a fresh huge_app ESP32 build, public documentation links, signing and asset checksums. Public downloads are checked again after publication. Device fixtures use a disposable package and do not add readings or change calibration in the installed app.

Final PCB/enclosure/tube/diffuser testing and traceable optical calibration remain pending. Prototype checks do not establish absolute irradiance accuracy. CAD/Gerber files remain development references. The single firmware application partition has no automatic rollback; the USB archive documents application-only recovery.

See [record transfer](RECORD_TRANSFER.md), [updates and recovery](UPDATES.md), [hardware construction](../hardware/README.md), and the [previous 1.4.0 release notes](RELEASE_NOTES_1.4.0.md).
