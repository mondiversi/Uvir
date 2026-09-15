# Uvir architecture

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
kept per hardware identity. The app still has only one active sensor connection;
the sensor-source selector switches between known profiles sequentially. A new
hardware identity requires explicit association; historical profiles and records
remain intact after dissociation.
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
- `UvirOfflineStore.h` owns the acknowledged LittleFS offline queue.
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
state remains in RAM; record size and permanent storage are unchanged.
Conditional waiting/gating shows exact remaining capacity but no invented time
estimate, since future threshold crossings cannot be predicted. A triggered
START returns to the ordinary schedule estimate. Reboot never resumes a RAM job.
