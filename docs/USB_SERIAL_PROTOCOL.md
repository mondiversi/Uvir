# Uvir USB serial integration

Status: development documentation for protocol `uvir-sensor-v1`.

This document describes the USB interface currently implemented by the Uvir
ESP32 firmware. It is intended for computers, mobile devices and embedded
controllers that need to power and control the sensor without running the Uvir
Android application. It was verified against firmware `0.5.80`.

The protocol is usable now, but Uvir is still under active development. An
integrator should validate the `protocol` field and advertised capabilities
instead of depending only on a firmware version string.

## What an external controller can do

An external USB host can:

- read the sensor identity and capabilities;
- set the sensor clock;
- request individual samples or a continuous stream;
- configure sampling and calibration;
- start, monitor and stop automatic, conditional or external-command sessions;
- configure value-alert rules;
- receive live acquisition events;
- recover acquisitions, alerts and errors stored while no app was connected.

Only one host/transport owns an active app session at a time. USB, local Wi-Fi,
Bluetooth and Internet expose the same logical command protocol, but this guide
focuses on the trusted physical USB connection.

## Electrical and serial connection

The current development board exposes the ESP32 UART through a CP210x USB
bridge.

| Setting | Value |
|---|---|
| USB bridge currently recognized by Uvir | CP210x, VID `0x10C4`, PID `0xEA60` |
| Baud rate | `115200` |
| Data bits | `8` |
| Parity | none |
| Stop bits | `1` |
| Flow control | none |
| Command terminator | `LF`, `CR`, or `CRLF` |
| Response framing | one UTF-8 JSON object per line |
| Maximum command line handled by firmware | 512 characters |

The controlling device must act as a USB host. A USB power-only connection is
not sufficient. Some serial libraries toggle DTR/RTS when opening a port and
can reset an ESP32; after opening the port, wait for the startup `hello` frame
or send a fresh `HELLO` before continuing.

## Trust and authentication

USB is treated as a trusted physical transport and does not require `AUTH`.
Wireless transports additionally require the private token learned during the
trusted USB provisioning step.

The USB `hello` response can contain provisioning data that is deliberately
withheld from wireless responses. Treat it as sensitive: do not publish it,
write it to ordinary logs, or include it in diagnostic reports.

Physical USB access therefore grants control of the sensor. Products exposing
the port should apply their own physical-access policy.

### Switching the active wireless transport

An authenticated controller can send `WIRELESS WIFI`, `WIRELESS BLUETOOTH` or
`WIRELESS INTERNET` over its existing link. The sensor replies with a `status`
frame containing the requested `wireless_mode` before closing the old radio,
then enables the requested mode first. If the new link does not authenticate,
the normal 20-second fallback cycle through configured modes remains available.
The Android app checks this acknowledgement before saving its new selection;
a rejection, timeout or link loss does not count as confirmation. Without a
live link, selecting a source locally still uses normal reconnection. Selecting
USB does not send `WIRELESS OFF`, so wireless recovery is preserved.

## Framing and compatibility rules

Commands are ASCII text followed by a newline. Responses are newline-delimited
JSON. For example:

```text
PING\n
```

```json
{"type":"hello","protocol":"uvir-sensor-v1","device_id":"…","firmware":"0.5.80"}
```

An integration should:

- require `protocol == "uvir-sensor-v1"` for this version of the parser;
- use `type` to dispatch each incoming JSON object;
- ignore unknown JSON fields and unknown asynchronous frame types;
- never depend on JSON field order;
- preserve 64-bit IDs and Unix timestamps without converting them to
  floating-point numbers;
- wait for the appropriate status/error response instead of assuming that a
  write succeeded;
- reconnect and repeat `HELLO` after a serial reset or transport loss.

Errors have this common form:

```json
{"type":"error","protocol":"uvir-sensor-v1","code":"…","message":"…"}
```

`code` is the machine-readable value. `message` is intended for diagnostics
and may become more descriptive over time.

## Recommended connection sequence

1. Open the serial port at 115200 8N1.
2. Read the startup frames or send `HELLO`.
3. Verify `protocol`, `device_id`, sensor availability and the required
   capability fields.
4. Send `APP_CONNECT` if this host is going to act as the current live app
   session.
5. Send `TIME <unix_epoch_ms>` before recording anything.
6. Send `SYNC_BEGIN` and safely import any queued records.
7. Start `STREAM <interval_ms>` or issue other commands.
8. Send `STOP` before deliberately releasing the live connection.

Example:

```text
HELLO
APP_CONNECT
TIME 1789660800000
SYNC_BEGIN
STREAM 150
```

`TIME` accepts Unix epoch milliseconds from 2020 onward and synchronizes both
the powered runtime and the DS3231 battery-backed RTC. Android sends it on every
authenticated connection; a third-party host should do the same. After a power
loss the RTC supplies UTC autonomously. If it is absent, uninitialized or has
lost backup power, external-button and autonomous acquisitions remain rejected;
Uvir never creates intentionally undated records.

## Identity and status

### `HELLO` / `PING`

Returns a `hello` object containing identity, firmware, capabilities, current
sampling settings, time state, memory information, configured radios, offline
queue usage and current automatic/alert state.

Firmware 0.5.90 adds these storage fields:

- `storage_backend`: `micro_sd`, `internal_emergency`, or `none`;
- `storage_record_size_bytes`;
- `sd_available`, `sd_foreign`, and `sd_type`;
- `sd_total_bytes`, `sd_used_bytes`, and `sd_free_bytes`;
- `sd_record_capacity_total` and `sd_record_capacity_free`;
- `sd_invalid_records`, `sd_mount_errors`, and `sd_write_errors`.

Firmware 0.5.95 adds FRAM fields to `hello`: `fram_available`,
`fram_model`, `fram_i2c_address`, `fram_capacity_bytes`,
`fram_queue_available`, `fram_record_capacity`, and `fram_records_used`.
The `offline_*` counts include pending records in both FRAM and the
filesystem. A full FRAM queue spills new final records to microSD.

Record capacity reserves 5% of the card, with a minimum reserve of 16 MiB, and
therefore intentionally differs from raw free bytes divided by record size.

Time-related fields are `time_synced`, `current_time_ms`, `rtc_available`,
`rtc_valid`, `rtc_oscillator_stopped`, `rtc_read_ok`, `rtc_current_time_ms`,
`time_source`, `rtc_read_errors`, and `rtc_write_errors`.

Use capability fields such as `conditional_acquisition_supported` and
`external_command_supported` before presenting related controls.

### `APP_CONNECT`

Marks this transport as the active live app session and returns a `status`
frame. Taking ownership can stop live streaming on a previously active
transport; integrations must not try to control one sensor concurrently from
multiple hosts.

### `TIME <unix_epoch_ms>`

Sets the runtime clock and, when detected, the DS3231. The returned status
contains `"time_synced":true` and `rtc_updated`. Time remains valid through a
complete ESP32 power loss when the RTC backup cell and oscillator are valid.

### `DIAGNOSTIC <request_id>`

`request_id` must contain exactly 32 hexadecimal characters. Firmware 0.5.92+
returns a correlated `diagnostic` frame with identity, uptime, processor,
memory, storage, connection and session state. It also identifies every wired
component and reports the I²C acknowledgement and driver state for AS7343,
optional AS7331 and DS3231; microSD adapter/bus/pins and read/write result;
FRAM bus, queue and non-destructive read/write result (`fram_bus_ok`,
`fram_queue_available`, `fram_read_write_ok`); LED and buzzer control
paths/configuration; and the current GPIO33 external
input state. It never includes credentials or changes settings, sessions or
measurement records. It tests microSD via one bounded temporary
write/read/delete round trip (`sd_read_write_ok`) and FRAM by restoring the
original contents of a reserved diagnostic address. LEDs and buzzer are not
actuated by this command because
their final optical or acoustic result cannot be verified electrically.

## Reading measurements

### `STREAM <interval_ms>`

Starts transmission of the rolling measurements and makes the current
transport the active app session. Physical sampling already continues in the
background at the configured cadence, including while no app is connected.
The requested output interval is constrained to 150–5000 ms. The sensor
returns a status acknowledgement and then `sample` frames.

### `SAMPLE`

Returns the newest completed rolling average. Directly after startup or after
changing sampling/calibration settings, the command can produce no frame until
the configured RAM window has been filled. It does not start a second physical
sampling sequence.

### Sample frame

```json
{
  "type": "sample",
  "protocol": "uvir-sensor-v1",
  "seq": 42,
  "uptime_ms": 123456,
  "unit": "uW/cm2",
  "measurement_kind": "band_equivalent_irradiance",
  "calibration": "datasheet_estimate",
  "uv_available": true,
  "saturated": false,
  "gain": 256.0,
  "gain_ratio": 1.0,
  "integration_ms": 27.8,
  "samples_per_result": 5,
  "sample_spacing_ms": 150,
  "extremes_discarded": true,
  "bands": {
    "uvc": 0.0,
    "uvb": 0.0,
    "uva": 0.0,
    "violet": 0.0,
    "blue": 0.0,
    "green": 0.0,
    "yellow": 0.0,
    "orange": 0.0,
    "red": 0.0,
    "far_red": 0.0,
    "nir": 0.0
  }
}
```

The canonical transport unit is always `uW/cm2`; display-unit conversion is a
client responsibility. When `uv_available` is false, a client must not present
UV values as valid measurements. `saturated == true` means at least one value
was outside the usable range and should be represented as overload rather than
as a precise number.

### `STOP` / `SLEEP`

Stops live streaming for the active transport and powers down the spectral
sensors between operations. It does not erase stored records.

## Sampling and calibration configuration

### `SAMPLING_CONFIG <count> <spacing_ms> <discard>`

Example:

```text
SAMPLING_CONFIG 5 150 1
```

- `count`: 1–21 raw readings per final result;
- `spacing_ms`: 150–5000 ms;
- `discard`: `1` removes the per-band minimum and maximum when at least three
  readings are available, `0` keeps all readings.

### `CALIBRATION_CONFIG <visible_factor> <uv_factor>`

Both factors must be finite values from 0.1 to 10.0. Calibration is persistent.

### `SENSOR_CONFIG`

Current syntax:

```text
SENSOR_CONFIG <LED_ON|OFF> <brightness_0_100> <OFFLINE_ON|OFF> <BUZZER_ON|OFF> <volume_0_100> <AUTO_OFF_ON|OFF> <seconds> <EXTERNAL_ON|OFF>
```

These are persistent sensor settings. `EXTERNAL_ON|OFF` enables or disables the
GPIO 33 external-command input and defaults to `ON`, including after a factory
reset. Firmware 0.5.83 and later also accept the previous command without the
last token and leave the saved external-command setting unchanged. Configuration commands
may evolve as hardware capabilities are added; read back `HELLO` after changing
them.

## Automatic acquisition jobs

From firmware 0.5.98, automatic acquisition and value-alert monitoring are
mutually exclusive. `OFFLINE_JOB`, `OFFLINE_CONDITIONAL_JOB` and
`OFFLINE_EXTERNAL_JOB` return
`session_busy` while value alerts are active; stop them first with
`ALERT_CONFIG OFF ...`. Likewise, `ALERT_CONFIG ON ...` returns `session_busy`
while an automatic job is active; stop it first with `OFFLINE_STOP`.

All IDs and timestamps are unsigned decimal integers. A session note is stored
by the Uvir app rather than duplicated in every sensor record; `-` is the
reserved note token in the current wire format.

### Timed job

```text
OFFLINE_JOB <session_id> <next_at_ms> <interval_seconds> <end_at_ms> <maximum_count> <completed_count> <samples_per_acquisition> <sample_spacing_ms> <discard_0_or_1> -
```

Example:

```text
OFFLINE_JOB 1 1789660800000 5 1789660860000 12 0 5 150 1 -
```

`end_at_ms == 0` means no time deadline. `maximum_count == 0` means no count
limit. The sensor reports the accepted state through `automatic_status`.

### External-command job

```text
OFFLINE_EXTERNAL_JOB <session_id> <completed_count> <samples_per_acquisition> <sample_spacing_ms> <discard_0_or_1> -
```

In this mode a GPIO 33 active-low command is the only acquisition trigger.
Timing, maximum-count and conditional settings are intentionally ignored.
The trigger snapshots the newest compatible rolling average already held in
RAM. If that value is unavailable or stale, the sensor safely falls back to a
fresh configured acquisition.

GPIO 33 also supports autonomous control when no job was created by the app:

- one short closure stores a standalone external acquisition;
- three consecutive short closures start a sensor-originated external session,
  without storing the three gesture presses as acquisitions;
- one short closure during that external session stores its next sequenced
  acquisition;
- one short closure during a normal automatic session is rejected and cannot
  add a record to it;
- one short closure during a manual app session is delivered as an external
  acquisition within that manual session, marked as externally triggered;
- a short closure while value-alert monitoring is active is rejected with five
  orange RGB flashes and one prolonged buzzer tone;
- a closure held for at least two seconds stops the active automatic or
  value-alert session;
- a triple closure while an external session is already active is ignored;
  during a normal automatic or value-alert session it is rejected.

Sensor-originated sessions use a positive 64-bit remote token with bit 62 set.
Hosts must map the tuple `(device_id, remote session token)` to their own local
session identifier instead of displaying or adopting the token as a global ID.
The sensor reuses that token for live events and later offline synchronization.
All external gestures require a valid clock (from `TIME` or the RTC); without
it, no job or record is created and the sensor emits the same five-flash/long-beep
rejection signal.

### Conditional job

```text
OFFLINE_CONDITIONAL_JOB <base job fields> <START|STOP|ACQUIRE> <ANY|ALL|NONE> <duration_seconds> <rule_count> <metric> <ABOVE|BELOW> <threshold> [...]
```

Example:

```text
OFFLINE_CONDITIONAL_JOB 1 1789660800000 5 0 3 0 5 150 1 - START ANY 30 2 UV_TOTAL ABOVE 6.0 NIR_TOTAL BELOW 4.0
```

Supported metrics are:

```text
UV_TOTAL UVC UVB UVA HEV HEB VISIBLE_TOTAL VIOLET BLUE GREEN YELLOW
ORANGE RED NIR_TOTAL FAR_RED NIR BIO_DNA_UV BIO_UVA_PHOTOAGING
BIO_HEV_OXIDATIVE
```

The condition plan is a session-only snapshot held in RAM. It is independent
from the separately configured value-alert rules.

### `OFFLINE_STOP`

Stops the automatic acquisition job. Completion is confirmed by an
`automatic_status` frame where `job_active` is false.

## Live acquisition delivery

While a host owns an active app session, a completed automatic or external
acquisition is sent as an `acquisition_event`. Important fields include:

- `record_id`;
- `timestamp_ms`;
- `session_id` (`0` for a standalone external acquisition);
- `sequence`;
- `external_command` (`true` only when this specific record was triggered by
  GPIO 33, including records in external-command sessions);
- `completed_count` and `job_active`;
- the `bands` object.

After durably storing the record, reply with:

```text
ACQUISITION_ACK <record_id>
```

The sensor retries an unacknowledged live event. Never acknowledge a record
before it has been committed to durable storage. If the transport disappears
while an event is pending, the sensor moves that event to its offline queue.
Before creating that event, each required physical sample permits up to three
read attempts. These are recovery attempts for transient sensor/I2C failures,
not additional samples in the configured average.

## Value alerts

Replace the complete rule set atomically:

```text
ALERTS_CLEAR
ALERT_RULE <metric> <ABOVE|BELOW> <threshold>
ALERT_RULE <metric> <ABOVE|BELOW> <threshold>
ALERT_CONFIG ON <repeat_seconds> <session_id> <SAVE|NO_SAVE> <start_delay_seconds> <duration_seconds> <maximum_registrations>
```

Up to 24 distinct rules are accepted. Use `ALERT_CONFIG OFF ...` to stop alert
monitoring. Value alerts and automatic-acquisition conditions are different
features and do not overwrite one another.

The last three parameters were added in firmware 0.5.97. They may be omitted
by older clients and then default to zero (no delay, no duration limit, no
registration limit). Duration begins after the start delay. The sensor checks
the limits autonomously, including when Android is disconnected. A completed
alert counts toward the maximum only after it has been emitted or stored;
zero means unlimited. A power interruption does not restart either deadline.

`NO_SAVE` keeps threshold checking, LED and buzzer feedback active without
creating alert records on the sensor. Connected `alert_event` frames still
report the occurrence with `"recorded":false`; clients must not add these
events to their databases. `SAVE` is the default when the final token is
omitted by an older client. The mode survives power loss with the alert
configuration. A no-save session uses session ID `0`.

## Offline synchronization

Offline synchronization is an acknowledgement-driven sequence. The queue can
contain acquisitions, alerts and sensor errors.

1. Host sends `SYNC_BEGIN`.
2. Sensor sends `sync_start` with counts and `storage_was_full`.
3. Sensor sends one `offline_record`.
4. Host validates and durably stores or deduplicates it.
5. Host sends `SYNC_ACK <record_id>`.
6. Sensor removes that acknowledged record and sends the next one.
7. The sequence ends with `sync_complete`.

If a record cannot be stored, do not acknowledge it. Disconnect or retry later;
the sensor preserves unacknowledged data. The Android database deduplicates by
sensor identity plus sensor record ID.

An acquisition `offline_record` contains `session_id`, `sequence` and `bands`.
An alert contains `session_id` and encoded `details`. An error contains `code`
and `message`.

## Using another controller before Uvir synchronizes

Two operating models are valid, but they must not be mixed accidentally.

### Controller owns the live records

Send `APP_CONNECT`, receive `acquisition_event` frames, store each record and
send `ACQUISITION_ACK`. Those acknowledged records are owned by the controller
and will not later appear in the sensor's offline queue.

### Sensor keeps records for later Uvir recovery

Set `TIME`, configure the job, then release the live app session with `STOP`.
Leave autonomous recording enabled. The ESP32 records final acquisitions in
its own offline queue, and Uvir can later import them through `SYNC_BEGIN`.

With firmware 0.5.95, FRAM is the first queue for up to 92 pending final
records; microSD is the overflow and fallback queue. Current automatic-job and
alert state is stored in alternating checksummed FRAM checkpoints after meaningful
changes and completed records (on microSD when FRAM is unavailable at startup).
Following a complete power loss, a valid DS3231
clock lets the firmware reconcile the restored job autonomously: expired jobs
close, missed intervals are skipped without synthetic catch-up measurements,
and a still-valid job keeps its session ID, counters, schedule and conditions.
Without a valid RTC the job remains paused until a host sends `TIME`.

This second model is appropriate when another apparatus merely powers,
configures or triggers the sensor and the Android app remains the long-term
record owner.

Plain `SAMPLE`/`STREAM` results are live measurements, not database records.
They are not automatically imported into Uvir later.

## Destructive and power commands

- `POWER_OFF` enters deep sleep when no acquisition or alert activity is
  running. Physical reset or a power cycle is required to wake the sensor.
- `FACTORY_RESET` removes sensor settings, credentials, Bluetooth bonds and
  every unsynchronized record, then powers the sensor down.

Do not expose either command through an unattended integration without an
explicit confirmation and suitable authorization.

## Minimal host pseudocode

```text
open serial 115200 8N1
send "HELLO\n"
hello = wait for JSON type "hello"
assert hello.protocol == "uvir-sensor-v1"

send "APP_CONNECT\n"
wait for status.app_connected == true
send "TIME <current Unix epoch milliseconds>\n"

send "SYNC_BEGIN\n"
for each offline_record:
    commit record using (device_id, record_id) as the durable identity
    send "SYNC_ACK <record_id>\n"
wait for sync_complete

send "STREAM 150\n"
consume sample frames
...
send "STOP\n"
close serial
```

## Source of truth

Until the public protocol is frozen, implementation remains authoritative:

- firmware command parser: `firmware/esp32/UvirSensor/UvirSensor.ino`;
- conditional grammar: `firmware/esp32/UvirSensor/UvirConditionalAcquisition.h`;
- Android USB client: `app/src/main/java/me/mondiversi/uvir/UvirUsbSensorManager.kt`;
- Android command builders: `app/src/main/java/me/mondiversi/uvir/UvirOfflineSensorSync.kt`.
