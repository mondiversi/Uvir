# Phone-owned USB architecture

## Active path

`MainActivity` opens `UvirUsbPhoneApp` and `UvirUsbPhoneRuntime`. Only the
FT232H USB attachment filter is registered. The phone owns processing,
configuration, scheduling and storage. One attached head contains both optical
modules; there is no selectable wireless hardware backend.

The bus has one serialized I/O owner. `AndroidFt232h` handles USB requests and
strips FTDI status headers from every USB packet. `Ft232hMpsse` implements I²C
start/repeated-start, ACK/NACK, open-drain GPIO and 100 kHz three-phase clocking.
Independent `As7331` and `As7343` drivers validate identity, conversion readiness
and overload, then normalize raw counts with the gain used for that conversion.
Register headers still validate each address/register ACK before data is read.
Data bytes are queued in MPSSE blocks of at most 64, with one host read per block;
the 37-byte VIS snapshot needs four host reads including its three header ACKs,
instead of 40. Each block is bounded below the FT232H FIFO capacities. Only the
last byte of the entire read receives NACK, including across block boundaries.
AS7343 reads ASTATUS and all 36 spectral bytes in one consecutive transaction
(0x94–0xB8), latching a coherent frame and its actual gain/saturation status.
Unexpected gain, incomplete frames and saturated conversions cannot become valid
measurements. AVALID may still describe the preceding conversion: the driver
keeps the same conversion running and polls coherent snapshots for the requested
gain, rather than immediately aborting/restarting on cleared or old ASTATUS.
The initial wait includes the three integration cycles and an autozero margin;
settling is bounded to 40 checks. UV integration starts before VIS/IR is read, overlapping conversion
time while keeping every bus transaction serialized.
VIS auto-ranging uses the maximum of all 18 ADCs, including clear channels:
narrow-band values alone can cause repeated gain increases into clear-channel
saturation. Saturated frames lower gain quickly; valid frames target a safe
range with hysteresis. Neither clear channels nor the range-selection logic add
any contribution to the reported spectral values. At minimum/locked gain an
overload is reported once per cycle rather than repeatedly taking the same
clipped conversion; the next cycle still retries and can recover normally.

The configured sample count is a rolling window: after its initial fill, every
new valid pair can update the result. Each failing module has up to three logical
attempts, 50 ms apart, in addition to its bounded gain adaptation. A successful
reading from the other module is preserved. Recoverable optical failures skip
recording without reopening USB or replacing invalid values with zero. An
independent monotonic age guard expires the previous display after three times
the configured spacing or observed successful capture time (at least 500 ms;
the capture-time component is capped at 10 seconds). A long gap or changed
configuration refills the averaging window. Failed captures do not extend its
freshness budget. A transport failure closes the link. Session-duration timers remain independent
of measurement readiness. Denying USB permission never immediately reopens the
permission dialog; an explicit retry or a new attachment permits another request.
When a capture starts while the last reading is still fresh, an in-flight lease
allows that bounded gain adaptation/retry cycle to finish (maximum 10 seconds).
The independent watchdog still expires a stalled operation. A failed attempt
clears the lease and cannot renew an expired/error reading. A successfully
completed, on-time-started capture also preserves the averaging window across
that adaptation time; a true stale/failed gap or settings change still refills it.
Recording remains driven only by newly completed valid pairs, never by the
temporarily retained display sample.
Saturation is distinct from a lost measurement head. A separate presentation-only
path shows OL for the overloaded module and a fresh, calibrated rolling average
for the other module, if valid. VIS overload applies to both visible and NIR.
Unknown totals yield no percentage rather than a false 100% UV share. This path
does not enter chart history, raw full-pair observers, recording, or threshold
evaluation; capture is unavailable until a full valid average recovers. During
refill the explicit OL display remains, with the valid group's values updated.
The same finite age/watchdog rules apply: a stall, missing/non-saturation data,
changed settings or detachment cannot keep an old overload display alive.
On reopening FT232H, reset bit mode before purging and disable event/error
character substitution. Status headers are still removed from every packet;
overrun, parity, framing and receive-FIFO errors are rejected. UART BREAK alone
is not treated as a failed MPSSE transfer.

The home status dot is red for detached/unavailable, yellow for permission or
initialization, and green for connected, waiting for data, activity or saturation.
Waiting for a valid average and optical overload do not imply a lost USB connection.
Recording/pending manual capture shows "Activity in progress…"; saturation takes
precedence so an active job cannot hide an optical problem. A completed manual
session is not ongoing work. Affected live, archived and exported values all use
the same localized "Saturation" label, including historical out-of-range flags.
Persisted `quality_flags` keep their existing bit values (1 for UV, 2 for VIS/NIR),
so existing and imported records adopt this terminology without rewriting numeric
measurements, IDs, notes or sessions. The database and transfer schemas stay compatible.
Simulated data overrides physical connection states with an orange filled dot of the same size. These colors
apply in both themes; existing activity/initialization pulses vary opacity.

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
Reference collection first allows each relevant module to auto-range, then locks
its gain for the interval. Offset collection uses only the selected UV or VIS/IR
group, so an invalid reading of the other module cannot erase valid references.
Window references preserve independent per-channel availability and reject
saturation, fewer than 20 valid samples, insufficient signal, relative standard
deviation above 5%, or first-/second-half drift above 5%. Both reference intervals
use the same statistics checks as the existing wizard. Already-applied optical
multipliers are excluded when deriving new multipliers; the master is preserved.
Results require explicit application; merely opening a dialog changes nothing.

At connection, diagnostics record separate USB/MPSSE/I²C results and test AS7331
and AS7343 recognition, chip ID, initialization and one conversion independently.
Failure of one optical module cannot suppress the other module's result.
The diagnostic button is usable with an authorized FT232H even if either module
failed. A failed idle connection gets one explicit fresh initialization attempt,
using the existing serialized USB owner. Healthy connections are only observed:
six new raw pairs must arrive without interrupting acquisition/alert jobs or
changing phone calibration. Cached display values and frames from a previous
attachment cannot complete a probe. Timeout, cancellation and attachment changes
are handled explicitly. Reports can be saved/shared and failures enter the error
log. Reports describe the adapter, bus and both optical modules.

## External USB hosts

The [standalone USB reference](STANDALONE.md) describes the current MPSSE/I²C
transactions and includes a small independent PyFtdi raw-count reader. Its host
program owns initialization, fixed-gain conversions and status checks; it does
not inherit phone settings or the calibrated/averaged Android pipeline. The
reader is covered by synthetic register tests, not a physical PC/driver test.

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

Simulated readings start disabled and remain foreground-only. Simulated archive
events use the same phone-owned archive as measurements. Neither adds a source
field or a synthetic sensor profile. Both remain useful for development without hardware.
Simulation offers one periodic saturation mode, available only with simulated data.
It uses the same quality bits and "Saturation" label as saved values, while the
orange simulated-data header retains precedence. Historical out-of-range flags
are interpreted as saturation on read and export, as requested for this archive.
Real saturated partial readings remain display-only and cannot become valid records.

Diagnostics contains an Error log island immediately after the sensor
test. Its screen observes the bounded log file, shows newest entries first and
supports text selection, scrolling and TXT export from the title-bar action.
The export keeps the existing technical report format and standard Cancel/Save/
Share dialog. A red trash action follows export and opens a standard five-second
held confirmation: Delete all is red and Cancel is purple. Releasing early cancels
the countdown. Clearing removes only the bounded log file, refreshes the empty
view and allows later errors to be recorded normally. Merely opening the screen
never clears or exports errors. Data
management no longer contains the error log; its acquisition/alert action pair
transfers one combined archive, with legacy separate files still importable.
See [record transfer](../RECORD_TRANSFER.md) for transaction and identity rules.

## Persistence and compatibility

Database schema 27 retains acquisitions, alerts, their sessions, counters and
portable transfer UUIDs. It removes sensor profiles/settings/rules, hardware
identifiers, sensor-native record/session identifiers, external-command flags
and the retired camera-estimate table. Records previously marked external retain
their stored manual/automatic classification. There is no replacement Origin field.
Record/session IDs, timestamps, notes, variants, quality flags, optical metadata
and full-precision W/m² values are preserved. The app checkpoints the database and
keeps a private pre-upgrade copy before the transactional migration. New databases
are created directly with the reduced schema; old schema builders are isolated
in `UvirLegacyDatabaseMigration` for read compatibility.

Saved acquisitions expose Manual/Automatic under “Mode and details”; alerts use
“Details” without a redundant mode field. Lists no longer filter by sensor.
Acquisitions can be filtered by Manual/Automatic mode, next to Variants;
Note is the final full-width field. Alert filters omit mode and variants.
The restored mode choice is persisted with other filters. Charts, readable tables
and CSV omit sensor aliases while retaining optical component/band references.
Portable archives use version 2 without sensor arrays or hardware provenance;
version 1 imports discard those retired fields. Transfer UUIDs still prevent
duplicate imports and keep sessions together across phones and re-exports.

USB sampling and calibration are phone-owned settings, editable and persistent
even without an attached adapter. All configuration belongs to the phone. The menu has no Sensor/App separators;
General combines Theme with acquisition/alert notification islands. Only automatic reference
measurements require real live readings. FT232H identity is used only in memory
to validate attachment/reference/diagnostic generations, never as record provenance
or the owner of settings. Archived hardware settings never silently replace
the phone configuration or become FT232H commands.
Automatic acquisition and alert registration both reuse their original forms.
The automatic page retains separate interval, delayed-start, duration, maximum,
conditional and note islands, with hours/minutes/seconds editors. External
triggering is omitted. Existing `usb_auto_*` totals remain compatible; separate
draft values preserve durations/counts when their optional section is disabled.
Running jobs lock schedule and note editing. Conditional-start waiting reflects
the phone scheduler state, and the original stop confirmation remains in use.
Both phone-owned job forms share the same saved-state duration editor.
The app-settings JSON backup includes durable USB sampling/calibration
settings, excluding open jobs and simulation state. Earlier preview per-adapter
profiles remain archived without replacing the phone configuration.
Uvir settings and SQLite database exports no longer require encryption
or a password. Settings export is a single phone-owned document; there is no
App/Sensor export choice. Imports still recognize earlier encrypted settings
and database archives and request a password only for those legacy files.
Database replacement retains integrity/schema validation and a single transaction.

## Release boundary

`0.1.0` started Uvir's new development version line; `0.1.1` publishes the current
prototype and documents suspended development at optical calibration.
A separate signed application channel must be validated before enabling
automatic updates; signature/hash/installer checks remain in use. The head
does not require a firmware upload. See [distribution and updates](../UPDATES.md).
