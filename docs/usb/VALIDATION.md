# Validation status

## Release 0.1.1 verification — 2026-11-01

The signed release APK builds successfully as `me.mondiversi.uvir`, version
0.1.1, version code 10. All 832 JVM tests pass, including release identity,
archive/migration behavior and the three new release highlights in all 20
languages. The Android instrumentation test package compiles successfully;
there is no new on-device run for this release. Earlier dated device results
below remain historical evidence for their respective changes.

The standalone-reader/example suite passes 23 synthetic Python tests; it does
not establish physical validation of the independent PC reader. Documentation
link checks pass. Both portable CAD generators reproduce the selected reference
STLs byte-for-byte, and independent mesh checks pass for closed connected
geometry, USB height, supports and lid clearance. These checks do not establish
fresh print fit or optical calibration. The public APK and printable downloads
are supplied with SHA-256 checksums.

## Current hardware and suspension status — 2026-11-01

The assembled FT232H/AS7331/AS7343 prototype has supplied live readings on the
owner's phone, including strong-light/saturation trials and recovery. Earlier
dated entries below saying the board had not yet arrived describe those earlier
software-only checks; they are not the current project status.

Comparison trials with a commercial UV meter exposed unresolved measurement
discrepancies. Aperture changes alone did not establish valid absolute irradiance.
Development is suspended because further optics/reference-calibration work is
beyond the available budget. No reference-meter brand or model is prescribed.
See [project status](../PROJECT_STATUS.md) and [current calibration notes](CALIBRATION.md).

The published mechanical references are case R0.3 and experimental 5 mm lid R0.6.
The case USB opening follows the user's fit correction, Z=11–18 mm from outside
bottom, 7 mm high. Mesh checks verify the exported geometry, support positions
and lid clearance, not a fresh physical fit or optical validation.

## Original automatic-acquisition form restored — 2026-10-31

The phone-owned automatic page now uses the original form, matching alert
registration: separate interval, start-delay, duration, maximum, conditional and
note islands, standard checkbox headers and hours/minutes/seconds fields. The
external-command island is omitted. Existing seconds-based preferences are
retained; disabled options preserve their editable draft without becoming active.
Running-session schedule/note fields are read-only, with the original stop dialog.

831 JVM checks and nine isolated Android checks pass. Coverage includes existing
preference conversion, start-request values, disable/re-enable/reopen persistence,
running-state locks, conditional waiting, stop confirmation, navigation, full-width
header interaction, first-expansion scrolling and all 20 translated headers with
large text. Day/night captures were visually reviewed. Test fixtures use a separate
package and preferences; real acquisitions, alerts and calibration are untouched.

## Unified saturation terminology and existing archives — 2026-10-31

All live and saved spectrum/biological rows, charts, readable tables and CSV
exports now use one localized Saturation label. Optical-window measurement uses
the same label for a saturated signal; a multiplier outside its permitted limits
remains a distinct validation result. Diagnostics has one saturation simulation,
enabled only with simulated data. The orange status dot is unchanged.

Existing quality flags are interpreted as saturation without rewriting measured
values or rebuilding database schema 27. A device regression seeds the historical
bit values 1, 2 and 3 in acquisition/alert sessions, closes and reopens the database,
checks unchanged records, then transfers both families and checks exact stored
measurements and flags. Readable and CSV exports are checked in all 20 languages.
CSV headers now preserve Unicode combining marks, including Hindi vowel signs.

831 JVM tests pass. Twenty targeted Android checks cover archive compatibility,
transactional transfer, status colors/dot bounds, saturated live readings and the
single diagnostic option. All device fixtures use the isolated verification
package; no real archive, calibration or log is changed by the checks.

## Simulated-data status dot — 2026-10-31

Simulated data now uses the same 9 dp filled status dot as physical connection
states, in orange, replacing the debug glyph. Two isolated Android UI checks pass:
all nine status labels, colors and circular bounds in both themes, plus distinct
live saturation/out-of-range wording and preservation of valid UV during VIS
saturation. Tests do not open USB or access the real archive. The shared
out-of-range text no longer includes the OL prefix in any of the 20 catalogs.

## Confirmed error-log clearing — 2026-10-31

The log remains newest-first. A red trash action follows the export icon, is
disabled for an empty log, and opens the standard five-second destructive
confirmation. Delete all appears first in red and Cancel second in purple, with
the shared red hold-progress feedback. Cancel and early release preserve entries.
Successful clearing refreshes the view and both toolbar actions; later errors
create new entries normally. Completion feedback is delivered on Android's main
thread. All 20 catalogs include the new wording.

19 resource/confirmation checks and two isolated device tests pass. Physical
holds at 4.8 seconds do not clear, release restarts the timer, and a full five-second
hold confirms once. Day/night and LTR/RTL checks cover colors, order and progress.
The end-to-end test clears only a disposable verification log, checks cancellation,
empty-state controls and subsequent logging. Real user logs are not cleared by
these tests or installation.

## Combined archives and diagnostic log viewer — 2026-10-31

Data management now has one acquisition/alert export/import pair. Its combined
file preserves both families and their sessions, full-precision W/m² values,
notes, optical provenance and transfer UUIDs. Older standalone archives are
recognized automatically. Import remains a five-second confirmation and a single
transaction; conflicts in the second family roll back the first family as well.

The Error log island moves below Sensor diagnostics. Its viewer reads the bounded
file without clearing it, refreshes on new entries, shows newest entries first,
and exports TXT through the title-bar action and standard Cancel/Save/Share popup.
A separate saturation simulation follows the out-of-range checkbox; enabling
either mode disables the other without changing real-head calibration.

831 JVM checks and 20 isolated Android checks pass. Real-device checks include
both archive families/session remapping, repeat-import deduplication, legacy file
recognition, invalid-envelope rejection, all-or-nothing conflict handling, one
action pair, import hold behavior, live log refresh and export popup, diagnostics
navigation/ordering, simulation availability and day/night status rendering.
Follow-up targeted checks cover the log export contents, cross-family UUID
collisions and simulated saturation wording. Screen captures are retained with
local verification evidence; no real records or calibration values were changed.

## Activity, saturation wording and status dot — 2026-10-31

The header now exposes ongoing acquisition/alert activity, without mistaking a
completed manual-session ID for active work. Connection problems and physical
saturation retain precedence; simulated data retains its orange debug indicator.
The physical USB glyph is replaced by a small filled status dot with the existing
red/yellow/green colors and opacity pulses. Physical overload uses "Saturation"
throughout live islands, including biological values, while synthetic/legacy
out-of-range values keep their distinct OL label. All 20 catalogs are complete.

23 targeted JVM checks and four isolated Android UI tests pass. Device checks
cover status text/color in both themes, circular-dot bounds, valid UV during VIS
saturation, per-band saturation labels, simulated OL remaining distinct, neutral
disabled capture and unavailable-state layout. No reference measurement, saved
record or calibration value was changed. The phone's Wi-Fi debugging connection
was restored after its address changed; the signed APK was updated in place.

## Home status wording and colors — 2026-10-30

The USB header now says "Initializing…" and "Waiting for data…", translated in
all 20 catalogs. Its existing color mapping was verified on screen for all eight
messages in both themes: red detached/unavailable, yellow permission/initializing,
green connected/waiting/OL, orange simulated data with the debug glyph. Twenty-six
targeted JVM checks and four isolated Android UI tests pass, including rendered
glyph-color checks through the existing opacity pulses. The signed development
APK was installed in place without changing records or calibration.

## USB batching, range selection and strong-light presentation — 2026-10-30

The real-head trace exposed gain hunting: narrow-band peaks around 220 counts
raised gain from 32× to 64×, where a clear ADC saturated, then fell to 32× on
every capture. Range selection now considers all 18 ADCs and uses bounded fast
steps with hysteresis. Data-phase MPSSE commands are batched without changing
the repeated-start/header ACK sequence or final-byte NACK. A 37-byte VIS frame
uses four host reads instead of 40. Initial same-gain trace samples improved
from a median 682 ms (8 logged samples) to 221 ms (23 logged samples); these are
sparse real-device traces, not a controlled optical benchmark. The owner
confirmed faster updates, but a subsequent close-to-lamp test still exposed
screen disappearance during persistent saturation at the lowest gain.

That remaining case now has a separate display-only path: OL for the saturated
module, a fresh configured average for the other module, and no false total
percentage. It does not become a recordable full pair. Recovering averages keep
the islands visible, while actual timeouts/transport faults still expire data.
Minimum/locked-gain saturation stops redundant same-frame retries and is retried
on the next normal cycle. Saturated values are never silently accepted just to
keep the screen visible; the legacy firmware checked only spectral-channel
counts, whereas the current driver also honors latched whole-chip saturation.

828 JVM tests pass, including wire-level batch decoding/FIFO bounds, all-ADC
gain control, strong-light recovery, independent valid-group averaging with
calibration, nonrecordable OL state, percentage handling and watchdog expiry.
Device/UI verification and the signed APK fingerprint are recorded with the
local verification evidence. No optical calibration or archive data is changed.
Three isolated Android UI checks also pass, covering OL/valid-UV islands,
unavailable-state centering and neutral disabled capture in both themes. The
signed APK was installed over the existing app. A subsequent real-head lamp
transition produced persistent VIS saturation, still returned fresh UV captures
at approximately 0.3 s per cycle, and recovered full pairs on the same USB
attachment. The owner confirmed stable islands, explicit OL under the strong
lamp, and normal return to values. The observed visible sum near 2,500 µW/cm²
is specific to that illumination, not a hard-coded or calibrated full-scale
limit for the summed visible bands. Absence of a timer-expiry log alone had not
been used to infer that confirmation.

## Continuous lamp/gain-transition availability — 2026-10-30

The earlier short diagnostic did not expose intermittent expiry during gain
adaptation. A longer trace reproduced it with the real head: valid full pairs
took 2.818–3.603 seconds while the display's age budget had decayed to
0.699–1.467 seconds. USB stayed attached and both modules returned valid data,
but the independent timer cleared the sample and forced the window to refill.

The runtime now uses a bounded in-flight lease, granted only when a capture
starts on a still-fresh/error-free reading. It cannot extend an already expired
or failed result; failed captures release it immediately, and the watchdog
enforces its 10-second limit independently of blocking I/O. The rolling window
recognizes this successful adaptation interval instead of discarding valid
samples. Only newly completed valid pairs can trigger recording.

813 JVM tests pass, including replay of 160 cycles with the actual 3.247-second
lamp transition, failed-gap/refill cases, nonrenewable expiry and the watchdog
deadline. The signed `0.1.0` APK was installed without changing calibration,
archives or the database schema. Extended real-head results are recorded in the
local verification evidence; a short 6/6 diagnostic alone does not establish
continuous stability through lighting changes.

With this correction, the real-head trace reached 540 capture cycles over about
4 minutes 45 seconds with zero display-expiry events. Lamp transitions changed
VIS gain between 4× and 1024×, including valid captures lasting 3.294 seconds.
Two saturated attempts were discarded and recovered without reviving invalid
data. The owner confirmed that the value screen no longer disappeared during
the lamp test. At that stage, update latency remained a separate optimization: the
37-byte VIS snapshot performs 40 individual host reads (three ACKs plus one per
byte), before counting USB writes and gain retries. Batching the read-data
commands was a candidate for reducing this overhead; it was not applied by
that continuity fix (see the subsequent batching validation above).

## AS7343 settling regression and disabled capture — 2026-10-30

- Real Android logs exposed a regression in the stricter gain check: CFG1 was
  correct while ASTATUS was cleared or still held the previous conversion's
  gain. Restarting immediately prevented reliable settling. The driver now
  waits within the same conversion for a coherent snapshot at the requested
  gain, with bounded polling and an initial autozero margin. It does not accept
  mismatched gain or reinterpret missing values as zero.
- FT232H reopening now resets bit mode before purging and disables event/error
  characters. The packet-status error mask retains overrun/parity/framing/FIFO
  rejection without treating UART BREAK as a failed MPSSE transaction.
- 809 JVM tests pass, including delayed/previous gain, persistent invalid gain,
  truncated snapshots and USB status regressions. Two isolated Android UI tests
  pass, including pixel checks proving the disabled capture glyph/container use
  neutral colors in both themes instead of retaining active purple.
- The corrected signed `0.1.0` was installed and tested with the actual FT232H
  head connected to the Galaxy S10e. Both modules initialized successfully;
  the app's real diagnostic passed USB, MPSSE, I²C, UV and VIS/IR checks and
  received six fresh reading pairs out of six. No renewed optical errors were
  logged during approximately two minutes of continuous operation before the
  diagnostic. No calibration was applied and no acquisitions/alerts were saved.
  Wider lighting/gain-transition and automatic-reference bench tests remain.

## Phone-owned measurement engine — 2026-10-30

- 807 JVM tests pass. New regressions cover coherent AS7343 ASTATUS/data reads,
  latched saturation versus live status, actual gain validation, incomplete data,
  rolling-window recovery, expiry/configuration resets, independent module
  retries, overlapping UV integration, reference cancellation and per-channel
  optical stability/signal/saturation checks.
- Five targeted Android checks pass in the isolated verification app. The two
  acquisition/settings checks were repeated after the final freshness change;
  diagnostics were unchanged. Recording uses fresh values and never backfills
  a disconnected interval. Phone-owned settings remain editable offline.
- The signed `0.1.0` APK was installed over the existing Uvir on the Galaxy S10e
  (Android 12), without uninstalling it, resetting calibration or changing the
  database schema. The disposable verification packages were removed afterwards.
- Earlier real Android logs showed nine temporary AS7343 saturation episodes on
  the same attachment generation; each recovered without a physical detach.
  A simulated transport demonstrates why reading DATA0 without ASTATUS can mix
  frames. This establishes the software defect, not that every observed warning
  came from a torn frame. Continuous real-head validation of the corrected
  reader, gain transitions and automatic references remains pending with the
  head connected to the phone. No optical calibration was applied in these tests.
- No GitHub publication or ESP32 firmware update was performed for this change.

## Sensor diagnostics restoration — 2026-10-28

The USB diagnostic page restores the existing Sensor diagnostics and Simulation
wording, the standard island descriptions and the note below Simulate events.
Raw connection enum values and preparation/debug lines are no longer displayed
in the island. Run diagnostics is disabled without an initialized real head.
The report uses fresh optical-reading pairs from the current attachment; it has
no ESP32 firmware requirement or absent peripheral fields. Its timeout allows
for the configured sampling spacing without changing the running reader.

782 JVM tests and 14 isolated device tests pass. New cases cover cached/stale/
invalid frames, zero readings, timeout, cancellation, reconnecting the same serial,
preservation of running-session state, USB reports, legacy firmware checks,
descriptions and disabled actions in both themes and portrait/landscape. Real
FT232H hardware validation remains pending.

## Uvir 0.1.0 prerelease verification — 2026-10-28

- 774 JVM tests and 28 device tests pass. Driver/measurement tests cover MPSSE
  synchronization, FTDI status stripping, ACK/NACK, I2C release on failure,
  sensor register/channel order, readiness, saturation, gain normalization and
  the SI correction pipeline. These use simulated transports, not the new board.
- The owner-signed APK is `me.mondiversi.uvir`, version `0.1.0`, Android version
  code 9. It is a prerelease, not the legacy stable update.
- General replaces separate Appearance/Notifications menu entries and combines
  Theme, Acquisitions and Alerts. Device checks cover both themes and orientations,
  two-column island placement, preserved typography, reference controls and
  session navigation. Existing settings/data-backup round trips also pass.
- The GitHub prerelease contains the signed APK and its SHA-256 checksum. There
  is no FT232H firmware upload or new ESP32 firmware asset. The automatic USB
  update channel stays disabled and the ESP32 branch remains at 1.4.2/0.5.119.

## Development version line — 2026-10-28

The current Uvir preview is named `0.1.0`, starting its own development sequence.
Android's internal version code advances to 9 so the signed preview can replace
the already installed build without uninstalling it or clearing data. Earlier
`2.0.0-dev` entries below document historical local builds, not a stable release.

## Interface restoration — 2026-10-28

The USB home now reuses the original toolbar and expandable capture menu:
equally sized selectors, saved acquisitions/alerts in the center, USB/debug
status glyph and one floating capture control. Alert thresholds and scheduling
are in the bell's registration page; notifications retain sound/feedback options.
The Info page/menu label matches Space Compass's localized Info and credits.

774 JVM tests pass. Phone checks cover toolbar geometry, offline editing,
registration navigation, running-session controls, archive compatibility, and
the original capture menu's hold/drag/animation behavior in both themes.
Tests use the isolated verification package. Real FT232H validation is pending.

The spacing audit also restores the original 22 sp title/36 dp name row,
12 dp logo gap and header padding, with a small version label beside Uvir.
USB-only controls reuse the compact checkbox, description and numeric-field
components; existing shared settings retain their original spacing. Checks cover
day/night, both orientations, RTL and enlarged fonts, without changing archives.

## Software verification — 2026-10-27

- Signed development APK `2.0.0-dev` builds successfully; no stable USB release
  or ESP32 firmware replacement has been published.
- 769 JVM tests pass, including the new driver/pipeline tests and all existing
  unit regression tests. Resource catalogs are complete in all 20 languages.
- 16 instrumented tests pass on a Samsung Galaxy S10e / Android 12 in the
  disposable `me.mondiversi.uvir.verification` package: USB-only menu, day/night,
  portrait/landscape, mock acquisition/alerts, disconnect interruption, isolated
  USB-settings backup and legacy SI database migration. Offline configuration
  tests cover editable sampling/calibration, independent factor/offset updates,
  optical-master persistence, automatic-acquisition drafts and disabled reference
  collection without an adapter.
- At the owner's request, the installed real app was upgraded from 1.4.2 to the
  signed Uvir development build `2.0.0-dev`, without uninstalling it. Encrypted
  snapshots were verified before and after the upgrade; all existing database
  tables were unchanged. This is a local preview, not a stable release.
- Real FT232H transfers, electrical compatibility, optical response and
  screen-off USB recording remain pending. Other Android versions/tablets have
  not been bench-tested in this redesign.

## Software checks

Unit tests cover FTDI packet boundaries, MPSSE initialization, repeated starts,
ACK/NACK and release on failure, UV register/channel ordering, gain changes,
visible normalization, SI correction order, signed averaging, trimming,
temporary identities and interval behavior. Existing archive/format tests remain
part of the regression suite. These are simulated transport tests, not bench tests.

## Phone-owned backup regression (2026-10-28)

- Settings export is one plain JSON file containing durable phone preferences,
  USB sampling and calibration. Round-trip tests verify SI values and exclude
  running jobs, simulation state and retired connection secrets.
- Database export is one plain SQLite DB file. Tests compare every archive table
  after import, preserve the original backup, reject incompatible schemas without
  replacing existing records, and retain earlier encrypted archive support.
- Settings/database export dialogs have no password fields or source selector.
  Light/dark portrait and dark landscape checks cover the file count, available
  actions, equal-width Counters/App reset buttons and complete button labels.
- Reset confirmation remains explicit about deleting saved records and restarting
  IDs. UI verification only opens and cancels the confirmation in the disposable
  verification package; it does not reset the installed user's archive.

## Phone archive simplification — 2026-10-28

- Schema 27 creates only measurement, session, counter and transfer-UUID tables.
  Sensor profiles/settings/native IDs, external flags and camera estimates are removed.
- Synthetic version-26 upgrade checks compare every retained field and transfer UUID,
  preserve AUTOINCREMENT high watermarks and the shared session counter, reopen the
  result and verify the private pre-upgrade backup still contains the original data.
- Legacy database imports migrate a private copy, preserve the input file, reject a
  missing spectral column without replacing the target, and convert pre-SI values once.
- Portable version-2 archives contain no hardware provenance. Version-1 imports normalize
  to version 2; repeated imports/re-exports retain duplicate detection and session mapping.
- Saved context cards and list filters omit sensor/external fields. Alerts omit mode;
  acquisitions retain Manual/Automatic. Scientific bands/models and full SI precision remain.

## Component diagnostics — 2026-10-29

- The diagnostic can run after USB permission is granted even if either optical
  module failed to initialize. The report separates USB/MPSSE/I²C, AS7331 identity,
  initialization and reading, and AS7343 identity, initialization and reading.
  Missing modules, wrong IDs, configuration failures and read failures remain
  distinguishable. A failed module does not suppress the other module's result.
- Failed idle connections receive one fresh initialization attempt. Healthy
  connections are observed for six new reading pairs through the existing USB
  owner, without changing calibration or interrupting acquisition/alert sessions.
  Cached readings and readings from a previous attachment cannot pass the test.
- 788 JVM tests pass, including fake-bus tests using the real optical drivers for
  both modules, each missing module, both missing, incorrect identity, read failure,
  invalid frames, valid zero readings and changed attachments. All 20 resource
  catalogs pass the translation/completeness checks.
- Ten instrumented checks pass on Samsung Galaxy S10e / Android 12 in the isolated
  verification package: partial-component reports, light/dark UI, cancellation,
  report/export consistency, shared diagnostic regressions and the disconnected
  real diagnostic page. These checks do not modify the installed user's archive
  or calibration. Real FT232H/sensor recognition remains pending the arriving board.

## USB home-state presentation — 2026-10-29

The USB home uses one centered disconnected island for every state without a
valid measurement, including permission, initialization and read failures.
The temporary technical/reconnect card at the top of the page is removed.
The status line and diagnostic report retain connection/error information.
An isolated phone UI check covers every USB status in both themes, return to
valid readings and the absence of the reconnect card. Toolbar checks also pass.

Android log tag `UvirUsb` distinguishes system attach/detach events, optical
read retries/recovery and fatal USB/I²C failures. It does not log archived data,
calibration values or USB serial numbers. A temporary optical read failure is
not proof that the USB cable physically disconnected.

## Pending when the board arrives

- Inspect the exact FT232H board, serial EEPROM, power regulator and header.
- Verify the GY breakout's supply and I²C levels, common grounds and pull-ups.
- Verify 0x74/AS7331 and 0x39/AS7343 identity and block-read order on the real bus.
- Compare raw counts, integration times, gain transitions, darkness and overload
  against independent measurements. Confirm the bus needs no clock stretching.
- Repeated plug/unplug, denied/revoked USB permission, rapid reconnect, cable
  power loss, screen-off, lock/unlock, app recreation, force-stop and low battery.
- Verify acquisition/alert interruption, timestamps, counters, reference dialogs,
  calibration persistence with and without a USB serial, and archive exports.
- Validate both sensors with the final optics and reference sources. A passing
  software suite cannot establish irradiance accuracy or an angular response.

The previous ESP32 firmware and PCB can still be recovered from `legacy-esp32`.
They are not required to test the new USB path and must not drive the same I²C bus
at the same time as the FT232H.
