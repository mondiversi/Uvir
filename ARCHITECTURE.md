# Uvir architecture

## Signed updates

`UvirUpdateClient` owns signed-catalog verification and bounded HTTPS downloads.
`UvirUpdates` owns the process-scoped workflow, pending app-first plan and error
reporting. `UvirUpdateHost` presents ordered versions, progress and Android's
installation confirmation. `UvirEsp32RomUpdater` implements the classic ESP32 ROM
protocol behind a serial interface with mockable tests. `UvirUsbSensorManager`
leases a fresh exclusive USB connection only for an authenticated idle sensor
with synchronized records. `UvirFirmwareUpdateService` protects a flash from
screen sleep and activity navigation. `tools/ReleaseTool.java` creates the signed
public index offline; its signing key remains outside the repository.

See [update/recovery policy](docs/UPDATES.md) for compatibility and single-partition
limitations. The updater never needs a database migration or an app uninstall.

## Android

- `UvirAppRoot.kt` owns application lifecycle and cross-screen state.
- `UvirLiveScreen.kt` coordinates the live screen and its overlays.
- Dedicated screen and settings files contain the visual sections used by the
  two coordinators above.
- `UvirUsbSensorManager.kt` and `UvirWirelessSensorManager.kt` own transport
  lifecycles only.
- `UvirSensorProtocol.kt` is the single mapping from sensor JSON bands to the
  Android `SensorSample`, shared by USB, Wi-Fi, Bluetooth and offline records.
- `UvirOfflineSensorSync.kt` owns versioned commands and synchronized records.
- `UvirDatabase.kt` and the export files own persistence and interchange.

The two large coordinator files are intentionally reduced in small verified
steps. New protocol, database, export or reusable UI behavior should not be
added directly to them when it belongs to one of the dedicated modules.

Sensor profiles, connection credentials, settings and historical records are
kept per hardware identity. UvirMultiSensorRuntime retains an independent
authenticated Wi-Fi/Bluetooth/MQTT worker for each associated UID, alongside the
single physical USB-host transport. Selecting a sensor changes only the visible
live/settings context and command destination; other devices stay connected and
continue their autonomous sessions. The explicit no-sensor entry pauses all app
connections, not the jobs running on the hardware. Multiple USB serial devices
through a hub are not implemented.

UvirSensorEventHub tags every event with its authenticated transport's UID. One
retained UvirMultiSensorPersistence receiver stores all devices' records before
acknowledging them to their originating transport. UI collectors receive only
processed events for the selected UID. The shared database publishes foreground
and background revisions. Retransmitted records are deduplicated by UID + sensor
record ID; a recovered copy of a live alert attaches its hardware ID to the
original row rather than duplicating it. Sensor-originated session IDs are
mapped independently per UID. Credential and diagnostic readbacks cannot
activate another UI profile. Per-device status dots and named connection
messages continue to update while another sensor is selected. Global counter
reset is blocked while any known device has an active session.

The quiet connection foreground notification aggregates ephemeral states from
all retained transports. One sensor shows its saved name and state; multiple
sensors show connected/connecting/active/simulation counts, with named states
and the selected context in the expanded text. Simulation never counts as a
real connection or activity. An offline job is labelled as last known activity,
not counted as verified live activity. Changing UI context does not discard
another device's state. The payload is value-compared before updating the
service, so ordinary live sample changes do not repost an identical notification.
Automatic acquisition and alert notifications also identify their sensor by
app display name, falling back to hardware UID.

Session notes cannot be edited while their automatic/external/alert job is open,
or while their manual group is the remembered continuation target for its UID.
The four detail screens observe completion and per-sensor context changes; the
database also checks the lock inside each note-write transaction. Switching or
disconnecting a sensor does not finish its job or unlock its note. Finished
session notes, including deliberately empty notes, are authoritative over late
sensor records. This uses the existing session metadata without a schema change.

A new hardware identity requires explicit USB association or a valid encrypted
association backup; historical profiles and records remain after dissociation.
Every settings export is encrypted with a user-supplied password. App and sensor
settings selected together use the same password but remain separate files. A
sensor-settings backup also contains that sensor's hardware identity and
authentication token. Importing it restores the sensor as an offline selectable
profile without replacing the currently active sensor. Legacy unencrypted
backups remain import-compatible.
Factory reset clears the sensor-side token, so an older association backup can
no longer authenticate until a new physical USB association is completed.
`UvirSensorOperationalState.kt` clears only that active app context on
dissociation. Reopening the app retains it. Dissociation never finishes a
database session or stops an autonomous sensor job. On reassociation, sensor
readback restores an actually running job and visible counts follow persisted
Android records. If its schedule is no longer known, memory capacity is shown
without an invented time estimate based on another session's controls.

Sampling spacing defaults to 150 ms in Android preferences and all transports,
matching firmware defaults. A stored interval or sensor settings snapshot takes
precedence; changing the default does not migrate existing configurations.
Live UI refresh throttling and automatic/alert session timers are independent.

Chart axes share `UvirChartScale.kt`: linear scales use the largest valid visible
value plus 10% headroom, with no minimum of one in the selected display unit.
Live scales use only the rolling minute, animate in canonical units over 300 ms,
and keep new peaks visible throughout the transition. Saved/exported charts use
their represented records; grouped variant exports retain a common scale for
comparison. Axis labels adapt decimal precision or use compact scientific
notation. OL samples do not set the scale; percentage alert charts retain their
threshold-centered logarithmic reference. Neither stored data nor schema changes.

Automatic-acquisition draft cards reuse `SettingsSection` through the small
`UvirAutomaticAcquisitionSection` adapter. Checkbox and header taps share the
full-width clickable header, focus normalization and post-layout bring-into-view.
Automatic cards retain their neutral header/text colors when enabled; the checkbox
indicates the active option. Settings and Info keep their existing accent headers.
Expansion never starts or modifies a sensor job; a running conditional snapshot
remains immutable. Disabled choices and editors use the existing shared action,
radio and text-field styles rather than a second set of theme-specific controls.

## ESP32

- `UvirSensor.ino` coordinates protocol, connectivity, sessions, alerts and
  the main loop.
- `UvirVisibleSensor.h` owns AS7343 power, gain, integration and conversion.
- `UvirUvSensor.h` owns optional AS7331 one-shot acquisition and power-down.
- `Calibration.h` contains replaceable irradiance calibration coefficients.
- `UvirFram.h` owns bounded I²C access, the double activity-state journal and
  the non-destructive FRAM read/write diagnostic.
- `UvirOfflineStore.h` owns the acknowledged FRAM queue, microSD overflow queue,
  runtime-state migration and the bounded LittleFS emergency fallback.
- `UvirRtcClock.h` owns the DS3231 UTC clock, oscillator-validity check and
  phone-to-RTC synchronization without a third-party library.
- `UvirStatusLed.h` and `UvirStatusBuzzer.h` own non-blocking status feedback.

Sensor drivers return one complete band sample and do not know whether it will
be streamed, averaged, used by an alert or placed in the offline queue. This
keeps measurement behavior identical across USB, Wi-Fi and Bluetooth.

## Conditional automatic acquisition

Android stores the chosen mode and a per-sensor active snapshot in preferences.
Acquisition conditions have their own per-sensor phone-only rules, separate from
configured alerts. Starting a conditional acquisition copies enabled conditions;
subsequent edits never alter this copy. No database migration is required.

`UvirConditionalAcquisition.kt` and `UvirConditionalAcquisition.h` own matching,
validation and action decisions. The new atomic `OFFLINE_CONDITIONAL_JOB` command
requires firmware 0.5.75 for sample-level monitoring; ordinary `OFFLINE_JOB` is unchanged.
Readback of existing 0.5.73/0.5.74 snapshots remains supported. The ESP32 snapshot
lives only in RAM. Fresh HELLO/status frames report the waiting phase and actual
end deadline. Invalid/unavailable readings are unknown, never a satisfied NONE.

START monitors physical samples after the initial delay, then latches its first
matching sample and starts the duration clock and recording cadence. STOP monitors
between recordings and during averaging, without storing the triggering check.
ACQUIRE monitors until a match, preserves that first sample in the configured
averaging window, saves the measurement, then suspends its condition checks for
the full interval measured from completion. False/invalid checks never advance
the recording deadline, allocate an ID or count as a record. Live and alert reads
also feed the observer, avoiding separate blocking loops and missed crossings
while another measurement is collected. Alert rules and averaging stay separate.
Duration, maximum count and initial-delay guards always take priority. All monitor
transient samples remain in RAM; the durable job checkpoint is updated only at
meaningful state transitions and completed records, not on every sensor sample.
Conditional waiting/gating shows exact remaining capacity but no invented time
estimate, since future threshold crossings cannot be predicted. A triggered
START returns to the ordinary schedule estimate. After reboot the battery-backed
DS3231 restores the absolute clock before the durable checkpoint is reconciled.
Elapsed sessions close, missed intervals are skipped rather than fabricated,
and still-valid jobs resume with the same ID, counters, conditions and cadence.
If the RTC is absent or invalid, the existing no-date guard keeps the job paused.
