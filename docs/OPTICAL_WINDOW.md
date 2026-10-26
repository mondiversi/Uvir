# Optical bands and protective windows

> **Project on hold: the optical assembly and calibration are unfinished.**
> App and connection workflows are operational, but current readings are not
> validated absolute measurements. The theoretical lid/apertures and any
> diffuser/filter/window need to be tested together and reference-calibrated.
> Window factors cannot restore light rejected in an angle-dependent way.

## What the channels represent

The AS7331 nominal bands are **UVC 240–280 nm, UVB 280–315 nm and UVA
315–410 nm**. Its response is not rectangular or uniform within these intervals.
The UV total in UVIR is the sum of its three channel estimates, not a measurement
of every wavelength from 100 to 400 nm. A small nonzero outdoor UVC reading is
not proof of actual UVC exposure or a validated absolute measurement.

AS7343 color names describe channel estimates, not uniform integration across
conventional color intervals. Current firmware combines channels as follows:

| UVIR value | AS7343 channel peaks (nm) |
|---|---|
| Violet | F1/F2: 405, 425 |
| Blue | FZ/F3: 450, 475 |
| Green | F4: 515 |
| Green-yellow | F5/FY: 550, 555 |
| Orange | FXL: 600 |
| Red | F6: 640 |
| Far-red | F7/F8: 690, 745 |
| NIR | 855 |

The main view shows compact nominal channel-peak references rather than
conventional colour intervals. Paired channels are averaged: for example,
green-yellow is the mean of F5/FY, not a dedicated 570–590 nm measurement. HEV
is the sum of the violet and blue estimates, not a uniform full-band measurement.
Like the group totals, HEV has no wavelength list in the UI or exported label;
the individual violet/blue channel references remain visible. Group headers omit
an aggregate spectral label instead of suggesting continuous coverage. Values
and the total/percentage layout are unchanged.

Detailed response limits and the distinction between peaks and passbands remain
in Sensor Info → Scale and sensitivity and its exported report. The live cards
do not repeat technical notes or a “Peaks” prefix. Saved acquisition/alert values,
threshold editors, optical compensation and readable exports use the same
references. Chart legends use the same compact channel names; CSV headers identify
the nominal peaks explicitly. Internal `yellow`/`YELLOW`/`giallo` identifiers and
column order remain unchanged for protocol, database and backup compatibility.

These label corrections do not rescale previous measurements. They also do not
imply continuous coverage of the entire visible or infrared spectrum.

Primary references: [AS7331 datasheet](https://look.ams-osram.com/m/1856fd2c69c35605/original/AS7331-Spectral-UVA-B-C-Sensor.pdf)
and [AS7343 datasheet](https://look.ams-osram.com/m/5f2d27fff9a874d2/original/AS7343-14-Channel-Multi-Spectral-Sensor.pdf).

## Internal LED isolation and printable enclosure parts

The [printable-parts index](../hardware/printable/README.md) provides a printed
replacement for the TEKO COFFER-A/6.5's standard aluminium panel and a separate
four-wall shield for the AS7343 visible/NIR optical path. The shield excludes
light from internal indicators; neither it nor the hollow tubes are transparent
protective windows. Keep the actual sensor inputs clear and include the final
parts in calibration. Normal visible LEDs do not make the AS7331 an internal UV
source; its [response limits and residual-interference check](../hardware/printable/uvir-visible-nir-shield-r1/README.md#why-this-shield-is-intended-for-the-as7343)
explain why a second UV shield is not specified for those indicators.

## Color dark offsets

### Independent master and channel multipliers

The optical-window editor always shows a general multiplier followed by all
eleven channel multipliers; there is no individual-mode checkbox. The applied
window factor is **general × channel**, once, after calibration and offsets.
Changing the master preserves every channel value. Both components have a 1–10
range, with a neutral value of 1; the effective product can reach 100. A uniform
90% transmission needs a final factor of approximately 1.111, not that factor in
both components. The optical-window reset sets both levels to 1 and never changes
calibration gains or dark offsets.

The independent master requires firmware 0.5.111. Earlier settings retain their
eleven factors and imply a neutral independent master, so installing the update
does not change readings or apply a correction twice. The master is stored on the
sensor, included in settings backups and per-record optical context, and applies
during autonomous operation. Existing database/SD/FRAM record layouts are preserved.
Export summaries show effective per-channel factors and never apply them again
to already-corrected readings.

### Channel dark offsets

Calibration presents **UV factor → three UV offsets → visible/NIR factor →
eight color/component offsets**, followed by the separate optical-window section.
The eleven fields are UVC/UVB/UVA, violet, blue, green, green-yellow, orange, red,
far-red and NIR. Violet/blue/green-yellow/far-red each have one field for the
paired color used by the main view. Wavelength references retain both contributing
peaks and the same 12dp inset as optical-window fields. Standard group dividers
separate the base factors from offsets and the UV section from visible/NIR.
Offsets default to zero. Firmware 0.5.108+ is required for color editing; an old sensor
cannot silently accept or ignore the extended configuration.

Offsets are saved per hardware ID in canonical **W/m²** (database v25 and
firmware 0.5.112+). The compatible v1 interface transmits explicitly labelled
**uW/cm2**, converting only at that boundary; see the [SI migration notes](SI_IRRADIANCE_MIGRATION.md).
The UI converts both entered and displayed values to the selected irradiance
unit and applies the selected decimal format. For example, 3 uW/cm2 is 0.03 W/m2
or 0.003 mW/cm2. Switching units or theme never writes a new calibration or
rounds the stored value through a display string. Readback does not interrupt
unfinished typing or trigger a command loop.

The pipeline is physical count conversion / existing raw-dark normalization →
paired-channel mean → subtract the color irradiance offset → base calibration
factor → optical-window multiplier → configured average → floor at zero → output
and storage. Instantaneous condition checks use a nonnegative one-sample result;
signed residuals are never sent or stored. Offsets are not noise thresholds or a substitute for suitable
gain/integration. No HEV or aggregate offset is applied again.

For absolute-measurement calibration, determine offsets from repeated genuinely
dark measurements with the final optical assembly. The offset measurement job
reads before all user corrections, so existing offsets or factors need not be reset.
Check temperature and all operating gains/integration times: a single stored
irradiance offset is an approximation and does not model arbitrary temperature-
or configuration-dependent drift. Do not use one nighttime observation as a
calibration. The UI calls the action **Calculate offsets** and the configuration
**Offsets**: a stable illuminated reference is also allowed for deliberate
background subtraction, but this removes real ambient light and makes subsequent
readings relative to that reference. It neither removes random fluctuations nor
supplies absolute calibration. Recalculate with covered inputs to restore
sensor dark-bias correction. The `DARK_OFFSETS` command name remains unchanged.

### Automatic UV sensitivity and mean offsets — firmware 0.5.114

Both UV and visible/NIR propose the **arithmetic mean of each channel** during
the approximately 30-second reference interval. The temporary maximum estimator
in 0.5.113 is superseded. The app requires 0.5.114+ for UV, checks `estimator:
"mean"`, and keeps VIS compatible with 0.5.110+. Existing configured offsets and
old records are preserved; values change only on explicit **Set offsets**.

AS7331 gain adapts from 1x to 2048x at fixed 64 ms integration, using the most
occupied channel ADC. Hysteresis avoids toggling at a boundary. A capture uses
at most three conversions and rejects analog/digital overload, unfinished or
stale data. The first reference sample settles the automatic gain; subsequent
samples hold that gain until completion or cancellation. Offset drift with gain
or temperature still needs physical validation; one offset is not a per-gain
calibration table.

Current color offsets are subtracted before channel gains and window factors.
Their signed residuals stay in RAM through the configured arithmetic/trimmed
average; **only the final result is clamped at zero**. A one-sample result is
clamped immediately. This avoids the positive bias of averaging readings already
clipped at zero. No deadband or new noise threshold is applied, so small residual
fluctuations can remain. The legacy fifteen-physical-channel subtraction/clamping
order is retained for compatibility. Wire/storage values remain nonnegative W/m²
(with the explicitly labelled legacy wire-unit conversion).

New sensor NVS state saves all factors and offsets in one versioned value;
legacy calibration is retained when migrating. Android database schema 23 adds
only a nullable offset configuration to per-sensor settings. Encrypted settings
backups, pending edits, runtime caches, diagnostics and Sensor Info preserve it.

The earlier version-1 fifteen-channel configuration remains losslessly stored and
readable; its physical-channel subtraction order is preserved by the firmware.
The new editor displays paired averages and warns before nonzero legacy values
are converted by an explicit color edit. Opening the page, receiving readback or
changing units does not migrate settings. Version-2 eleven-color configuration
uses the existing JSON column, so this refinement does not alter the DB schema.
Database imports upgrade private extracted v20–22 copies; existing measurements
and the 264-byte offline record format are unchanged. Old archives retain unknown
offset provenance; today's settings do not describe old records retroactively.

## Choosing an optional protective window

Use the **same material for both optical inputs: UV-grade synthetic fused silica,
polished on both faces and uncoated**. Check transmission over at least
**240–1000 nm**, including the complete sensor response curves, not just the
displayed channel peaks. This covers the AS7331 nominal UV channels and the
AS7343 visible/near-infrared response. It does not claim coverage of all UVC down
to 100 nm or of thermal infrared. Ordinary cover glass, acrylic, polycarbonate,
and an unspecified marketplace listing described only as “quartz” are not
equivalent substitutes without appropriate spectral data.

A specification-checked purchase option is
[Edmund Optics #22-692, 12.5 mm diameter, nominal 1 mm, uncoated fused silica](https://www.edmundoptics.eu/p/125mm-dia-1mm-thick-uncoated-1-fused-silica-window/48144/).
Its published 200–2200 nm range includes the required wavelengths. The usable
clear aperture is 11.25 mm; the nominal thickness tolerance is ±0.38 mm. These
are catalog specifications, not an approval of fit in the TEKO enclosure or a
measurement of the assembled instrument. The supplier's typical transmission
graph is for a **3 mm** sample, not a guaranteed curve for this exact 1 mm part.
Obtain applicable transmission data or make comparative measurements before
choosing correction factors. Supplier specifications were reviewed on 2026-10-21;
recheck the part, price and availability before ordering.

### Thickness and mounting

For a small, supported aperture, **1 mm nominal** is the recommended starting
point, balancing handling strength and optical path length. This is a mechanical
design recommendation, not an impact or sealing certification. A large shared
pane or exposed installation may need a thicker window or a stronger frame;
do not select a fragile 0.5 mm sheet solely to seek higher transmission.

Reducing thickness reduces bulk absorption, following approximately
`T_internal = exp(-alpha(lambda) * thickness)`. It does **not** remove the two
air/glass surface reflections. With the catalog visible refractive index 1.458,
the normal-incidence Fresnel estimate is about 3.5% reflection at each surface,
or about 93% direct transmission through both surfaces before bulk absorption.
That estimate is not a flat transmission guarantee across UV/VIS/NIR or at
oblique angles. See the supplier's explanation of
[surface reflections and coating wavelength limits](https://www.edmundoptics.com/knowledge-center/application-notes/lasers/anti-reflection-coatings/).
Thinner is therefore not automatically the better complete window. Do not choose
a UV-only or VIS/NIR-only antireflection coating without verified performance
over the full required range; an unsuitable coating can reduce transmission
outside its design band.

Prefer **two separate windows of the same material**, one over each optical
aperture, with opaque internal barriers isolating all status/power LEDs. One
shared sheet is possible if its dimensions, support and light isolation suit
the final layout. In either case:

- Size the clear opening for the sensor's field of view and window-to-chip
  spacing; do not infer the required diameter from breakout-board dimensions.
- Keep retaining rims, gaskets and adhesive out of the optical field. Support
  the edges without stressing the glass or touching the sensor package.
- Do not add a diffuser, lens, deep recessed opening or extra stacked window
  without rechecking spectral and angular response.
- Keep the window clean and document its exact grade, thickness and mounting.
  A window alone does not make the enclosure waterproof or dustproof.

For a directional assembly, use the [tube geometry alternatives](../hardware/README.md#directional-optical-heads-prototype)
as entrance-angle starting points: the original 6 mm / 25 mm and compact
3 mm / 12.4 mm bore/optical-height options have similar ideal cones. Include the
actual sensor-to-lid gap, window mount and diffuser in the optical review; a
matching diameter/height ratio alone does not establish the real field of view
or transfer an existing calibration to another head.

### Calibration and publication reminder

If calibration was established **without** the final window, its justified
transmission correction belongs in the optical-window multipliers below. If the
complete instrument is calibrated **with** the final window already installed,
that attenuation is normally included in the resulting calibration: leave the
separate window multipliers at **1.0** unless the calibration procedure explicitly
separates the two corrections. Never compensate the same loss twice.

The [assembly guide](../hardware/ASSEMBLY.md) and
[parts list](../hardware/BILL_OF_MATERIALS.md) include this optional-window
recommendation, mounting/LED-isolation notes and the distinction between window
compensation and final-assembly calibration. Final fit, optical tests and
calibration remain pending.

## Optional window compensation

In **Sensor → Measurement → Calibration**, the optical-window section offers an
independent multiplier for each of the eleven stored channel estimates. The
common multiplier is always shown, with **1.0 meaning no correction**; there is
no activation checkbox. This independent master requires firmware
**0.5.111 or later** and an idle, connected sensor. Existing visible
and UV calibration factors remain separate and are not reset by this feature.

A common master multiplies every channel's independent factor. Both the master
and all channel fields remain visible; there is no **Customize by channel**
checkbox. Changing the master does not overwrite individual factors, and changing
a channel leaves all other factors unchanged. Reference wavelengths appear beside
each channel. Firmware, settings backups and recorded optical provenance retain
the independent master as well as the eleven channel factors. Earlier records
without a master use a neutral master of one; opening the editor sends no command.
The protocol's enabled flag is retained for compatibility with existing firmware,
backups and historical records. A previously disabled configuration is presented
as neutral factors of one; opening the editor never enables dormant non-unit
factors or sends a command. An explicit factor edit applies the new multipliers.

Editable decimal fields, calibration descriptions and readable optical summaries
follow the numeric format selected in the app: for example **1,0** in European
format and **1.0** in International/American format. Inputs omit grouping and do
not round unfinished edits; decimal separators and localized digits are converted
only for presentation. Internal values, commands and scientific JSON retain their
canonical machine-readable representation. The same shared fields cover alert
thresholds, acquisition conditions and measurement calibration.

For a known transmission T, the approximate multiplier is **1/T**: 90% transmission
requires about **1.111**, not 1.10, as the final master × channel factor.
Each editable multiplier accepts 1–10; their product can therefore reach 100.
Only use
manufacturer transmission data applicable to the actual window and geometry,
or justified comparative measurements. A scalar multiplier cannot restore
wavelengths that a window blocks, or fully correct wavelength/angle-dependent
transmission and an unknown spectrum. Fused-silica grades and thicknesses are
not interchangeable; verify their transmission curves across the channels used.
Do not assume a generic 10% loss or change coefficients from sunlight alone.

Firmware applies enabled factors once, after physical channel conversion and
before sampling averages, automatic conditions, alerts and storage. Android and
offline synchronization receive already corrected values and never multiply
them again. Settings persist on the sensor and work without a phone connection.
A legacy two-factor calibration command explicitly disables this optional
compensation and restores its eleven factors to one; clients preserving optical
settings must use the extended command documented in the serial protocol.

New live/acquired/alert records include an `optical_window` object with schema
version, enabled state, master and eleven factors in wire order. CSV/readable exports
carry this context too. An em dash or empty context means **unknown**, not
"compensation disabled". Old records retain their original values and unknown
context. Settings backups preserve the per-sensor configuration; legacy backups
without it default to disabled factors of one. Database schema 22 adds nullable
context columns without recalculating archived measurements; imports of previous
schema 20/21 backups upgrade only the temporary extracted copy.

The 264-byte offline SD/FRAM record size is unchanged; firmware 0.5.112 uses
record version 3 for SI units. A versioned checksummed
context journal records only context boundaries, not a new
metadata write for every sample. Record IDs associate new records with the right
configuration after a restart or SD/internal-storage fallback. Old records are
not assigned today's configuration. A missing/damaged context is never invented;
an inability to persist new context prevents that queued save and follows the
existing storage-error path. Keep the journal with the offline queue when
inspecting/copying sensor storage manually.

### Comparative multiplier calculation

Development addition dated 2026-10-25, after the published 1.4.1 release. The
existing sensor firmware 0.5.112 is sufficient; no firmware update or database
migration is introduced for this observer-based procedure. Software and wizard
checks use synthetic inputs; a physical window comparison remains to be performed.

**Calculate multipliers**, before **Reset values** in the optical-window island,
opens a two-interval comparison for the selected physical sensor:

1. Keep the source, sensor position and orientation fixed; remove the window or
   filter and measure for 30 seconds under stable illumination.
2. Fit the window/filter, wait for stable illumination, then explicitly start a
   second 30-second interval without moving the sensor or source.
3. Review each proposed channel multiplier and use **Set multipliers** to apply
   usable channels. Cancel, Stop, closing the popup and restarting do not apply
   the proposal. Unusable channels retain their previous values.

For each channel the proposal is `mean_without_window / mean_with_window`.
The app observes fresh live samples, removes the already-applied optical-window
master and channel factors from both intervals, and leaves existing calibration
gains and offsets in the readings. New channel multipliers **replace**, rather
than multiply, old channel factors; the independent common master remains
unchanged. A non-neutral master remains an additional manual correction, so
leave it at one when the measured channel factors are intended as the complete
window compensation. No temporary sensor-configuration change or new firmware
is required; the existing firmware must provide optical context in live samples.

The observer uses canonical W/m2 values independently of display rounding or
hidden home bands. Each channel requires at least 20 fresh samples and a mean
of at least 0.0001 W/m2 before window correction. Relative standard deviation
and the difference between first-half and second-half means must each be at
most 5%. Missing channels, saturation, insufficient/unstable data, a higher
filtered reading or a ratio outside 1-10 produce no factor for that channel.
These are conservative software acceptance rules, not a quantified uncertainty
or a substitute for a suitable source and correctly measured offsets.

Both intervals must use the same hardware identity, connection generation,
optical/calibration configuration and sampling settings. Disconnection, handover,
sensor restart, an active recording/test or changed settings invalidates the
comparison. No fresh live samples for six seconds aborts the interval. Failures
and unusable result channels are recorded in the error log. Measurements do not
create acquisitions or alerts, and stored records are not recalculated.

The result applies to the tested source spectrum, alignment and optical geometry.
A stable change of source level between intervals cannot be distinguished from
window attenuation by this single-sensor method. In particular, a scalar factor
cannot universally compensate a tube or screen that changes the accepted field
of view, restore blocked wavelengths or establish absolute calibration.

## Validation status

The earlier optical-window implementation is included in app 1.4.0 and firmware
0.5.112. Firmware compilation
and host tests do not validate window transmission or absolute accuracy. The
sensor was unavailable during the initial implementation; the subsequent device
update and checks are recorded below. Final dark/reference
calibration is still required with the assembled PCB, enclosure, optical barriers
and actual protective windows. See [calibration guidance](../hardware/calibration/README.md).

Initial software checks on 2026-10-21:

- Android: 599 JVM tests passed; debug instrumentation sources compiled. The
  new protocol/private-database instrumentation fixtures were not run on the
  personal phone.
- Signed release APK built successfully and its signature verified. No APK was
  installed as part of this optical-window change.
- The actual three SQLite table definitions passed an in-memory migration and
  explicit-column import check, preserving original values and unknown context.
- Twenty resource locales have matching, nonempty optical-window keys.
- ESP32 firmware 0.5.105 compiled successfully for the existing huge-app
  partition profile (66% program storage, 22% static RAM). Host tests passed for
  strict parsing, disabled identity, independent factors, interrupted journal
  writes, SD/internal fallback, restart and checksum failures.
- No firmware was flashed, no physical calibration was changed, and nothing was
  committed or pushed to GitHub. Real-device verification remains pending.

### Subsequent connected-device checks — 2026-10-21

- Updated the connected ESP32 from 0.5.104 to 0.5.105 after confirming that no
  acquisition or alert activity was running and no records were queued. A private
  recovery backup was kept outside the repository; the existing partition layout
  matched the build. Only the application partition was flashed and verified.
- The pre/post settings and credential fingerprint matched, including the
  existing calibration factors and settings revision. No calibration command,
  formatting, record synchronization/acknowledgement or acquisition was issued.
- The device reports optical-window compensation disabled with all eleven
  factors equal to 1. The already-installed Android app recognized the upgraded
  firmware and enabled the previously unavailable optical-window control; the
  checkbox remained unchecked. No app reinstall was needed.
- Diagnostic checks confirmed live AS7343 and AS7331 reads, a valid DS3231 clock,
  and a successful non-destructive FRAM read/write test. The microSD was not
  detected either before or after the update. The reported storage diagnostic
  succeeded on the internal fallback, which does not validate an absent SD card.
- Firmware compilation and host tests were repeated successfully. No records
  were created, no optical transmission or absolute accuracy was validated, and
  nothing was committed or published. Final assembled-device calibration and
  physical validation with an actual protective window remain outstanding.

### Calibration rejection and connection correction — 2026-10-21

The first physical edit exposed a 0.5.105 command-dispatch bug: the calibration
command started the activity LED before its busy check, which then treated its
own visual indicator as a running operation and rejected the edit. On wireless
connections, that rejection also incorrectly changed the connection state to
ERROR; subsequent HELLO/settings replays produced repeated `calibration_busy`
messages and flashing settings fields. This was a rejection loop, not eleven
separate channel writes.

Firmware 0.5.106 excludes the command indicator and its hold from the calibration
guard while preserving all real acquisition, alert, sync and diagnostic locks.
It reports `calibration_locked` separately from visual `operation_active` in HELLO
and activity frames. Android uses that real-work lock and retains a healthy
wireless connection after calibration-command rejections. The editor keeps text
and focus stable during readback and never sends changes just from recomposition.

The application-only firmware update preserved settings and credentials. Host
tests compiled the actual busy predicates against fake state and confirmed that
every real activity still blocks edits while the visual indicator does not.
608 JVM tests passed; the isolated UI test sources compiled but were not run as
instrumentation on the personal phone. On the connected phone, common-factor
seeding, a blue-only override, and return to the common multiplier were verified
against sensor readback. Temporary test multipliers were restored to 1; the
user-selected enabled state was retained. No acquisitions or alerts were created,
and no source or release was published.

### Editable dark offsets — 2026-10-21

The calibration editor now orders the UV factor and three AS7331 offsets before
the visible/NIR factor and twelve AS7343 physical-channel offsets. Display units
and decimal separators are presentation only; settings and transport retain
unscaled `uW/cm2` in that legacy version. Firmware 0.5.112 and database v25
subsequently migrate internal storage to `W/m2` without changing the v1 wire
unit. All new offsets default to zero. Values are subtracted
before the base factor, pairing/group totals and optical-window compensation;
negative corrected channel readings are clamped to zero.

Verification completed with 622 JVM tests, including the 20-language resource
checks, and 16 distinct Android instrumentation tests on an isolated application
ID. Tests covered unit/theme changes without writes, editing and canonical
conversion, per-device storage, legacy commands, explicit offset clearing,
settings backup and private database migrations/imports through version 23.
Older acquisition values were preserved, not recalculated. Day/night editor
captures were inspected. The temporary test packages were then removed; the
personal app, its archive and its associations were not replaced or edited.

Host tests cover offset parsing and ordering, physical-channel conversion with
automatic gain, and complete rejection/recovery of oversized command lines.
Firmware 0.5.107 extends all input transports to a bounded 16384-byte envelope
so the new offset settings also fit their dated, hex-encoded synchronization
wrapper. Earlier firmware is read-only for offsets. A signed APK was prepared;
no firmware was flashed, no physical offsets were measured/applied, and nothing
was committed or pushed. Hardware readback after installation and final assembled
dark/reference calibration remain outstanding.

### Color grouping and editor refinement — 2026-10-21

The current editor uses eleven color offsets instead of fifteen physical-channel
fields. Both offset and window references now use the same trailing-text component
with 12dp horizontal padding, 11sp type and one line. Small group captions and the
existing standard dividers separate UV from visible/NIR and factors from offsets.

624 JVM tests passed, including conversion, legacy preservation, strict 27/31-token
protocol validation, firmware capability gates and 20-locale resource checks.
Host tests passed for both offset modes, invalid/partial command rejection and
post-pairing color subtraction before multiplication. Firmware 0.5.108 compiled
for the existing huge-app profile (66% program storage, 22% static RAM). The Android
instrumentation fixtures compile with additional padding/legacy-edit checks, but
were not executed for this refinement: the phone was disconnected and no emulator
was started on the memory-constrained PC. A visual phone check remains pending.
The signed APK is prepared for later installation. Neither the app nor firmware
was installed during this refinement; no calibration, records, associations or
GitHub content were changed on external devices/services.
