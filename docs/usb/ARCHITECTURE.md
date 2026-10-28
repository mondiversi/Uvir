# Phone-owned USB architecture

The full ESP32 implementation is retained on `legacy-esp32`, based on commit
`7161a31711f249de2bc346199c269da7aaea88c5`. Local Git-bundle, source-ZIP and
release-package backups were verified before development started.

## Active path

`MainActivity` opens `UvirUsbPhoneApp` and `UvirUsbPhoneRuntime`. It does not start
the legacy multi-sensor runtime, remote server, serial ESP32 manager or wireless
manager. Only the FT232H USB attachment filter is registered. Wireless/location
permissions and the ESP32 firmware service are absent from the active manifest.
Some legacy implementation files remain as compatibility/reference code while
shared archive, presentation, import/export and settings components are reused.
They are not a second selectable hardware backend in this edition.

The bus has one serialized I/O owner. `AndroidFt232h` handles USB requests and
strips FTDI status headers from every USB packet. `Ft232hMpsse` implements I²C
start/repeated-start, ACK/NACK, open-drain GPIO and 100 kHz three-phase clocking.
Independent `As7331` and `As7343` drivers validate identity, conversion readiness
and overload, then normalize raw counts with the gain used for that conversion.
Recoverable optical overload/not-ready results clear live availability and skip
recording; the same open bus keeps the reduced gain for the next attempt. A
transport failure closes the link. Session-duration timers remain independent
of measurement readiness. Denying USB permission never immediately reopens the
permission dialog; an explicit retry or a new attachment permits another request.

The AS7343 conversion is the provisional datasheet estimate used by the previous
project, not a laboratory calibration. Physical channels are combined into the
same displayed colors before their color offset is subtracted.

For each displayed channel the phone uses:

```text
max(0, mean(raw W/m²) - offset W/m²)
    × channel correction factor × optical master × optical channel multiplier
```

Optional extreme trimming is per channel. Signed residuals are averaged before
clamping. Source data, full-precision SI storage and alert evaluation never use
UI-only hidden bands, percentage mode or display rounding. Reference collection
uses fresh raw frames, cancellation and fixed identity/configuration checks.
Results require explicit application; merely opening a dialog changes nothing.

## Lifecycle and recording

An adapter must have USB permission and both supported chips must initialize
before a real measurement is available. Failure clears live availability rather
than reusing a previous result. Disconnect invalidates the attachment generation,
closes the transport and ends phone-owned jobs. Reconnection starts an idle head,
not a restored acquisition session.

Intervals use monotonic elapsed time; timestamps use phone wall-clock time.
Missed intervals are skipped, not backfilled with invented measurements.
A small journal lets a new process close interrupted sessions at their last
recorded time. USB sessions use a connected-device foreground service and partial
wake lock. Screen-off/OTG behavior still needs real-phone validation, and Android
force-stop/phone shutdown necessarily ends acquisition.

Simulated readings start disabled. They do not use a real sensor identity and
remain foreground-only. Simulated archive events also retain their established
em-dash sensor behavior. Both remain useful for development without hardware.

## Persistence and compatibility

The existing database and schema are retained. Old sensor profiles, hardware
UIDs, acquisition/session IDs, external-mode records, quality flags and optical
metadata stay readable. Existing scientific/custom exports and portable imports
continue using their original SI and source-identity rules. No bulk conversion
or destructive database reset is part of this redesign.

USB sampling and calibration are phone-owned settings, editable and persistent
even without an attached adapter. All configuration belongs to the phone. The menu has no Sensor/App separators;
General combines Theme with acquisition/alert notification islands. Only automatic reference
measurements require real live readings. FT232H identity remains record provenance,
not the owner of these settings. Old ESP32 settings are not silently promoted.
Imported legacy sensor settings stay
archived and are never sent as ESP32 commands to an FT232H.
The app-settings JSON backup includes durable USB sampling/calibration
settings, excluding open jobs and simulation state. Earlier preview per-adapter
profiles remain archived without replacing the phone configuration.
USB-edition settings and SQLite database exports no longer require encryption
or a password. Settings export is a single phone-owned document; there is no
App/Sensor export choice. Imports still recognize earlier encrypted settings
and database archives and request a password only for those legacy files.
Database replacement retains integrity/schema validation and a single transaction.

## Release boundary

`0.1.0` starts the USB edition's development version line and is the only remaining
GitHub release. The former ESP32 binary releases have been retired; `legacy-esp32`,
its source history and Git tags are unchanged. The old stable update feed no longer
has a published release and must not deliver the incompatible USB edition.
A separate signed USB channel is required
before enabling automatic USB updates; signature/hash/installer checks remain in
use. There is no firmware to upload to the FT232H head through this app.
