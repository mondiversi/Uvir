# Uvir USB serial integration

Status: development documentation for protocol `uvir-sensor-v1`.

This document describes the USB interface currently implemented by the Uvir
ESP32 firmware. It is intended for computers, mobile devices and embedded
controllers that need to power and control the sensor without running the Uvir
Android application. The command documentation was checked against firmware
source `0.5.112` on 2026-10-22. See the
[standalone transport/setup guide](STANDALONE_SENSOR.md) for concrete USB,
Wi-Fi, Bluetooth and MQTT connection procedures and a runnable live reader.

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
| Maximum command line handled by firmware | 16384 bytes (firmware 0.5.107+; earlier USB/Wi-Fi/SPP: 512) |

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

The token is 32 case-sensitive text characters, not necessarily hexadecimal.
`AUTH` uses it verbatim. A Wi-Fi client must authenticate within five seconds
of opening its TCP socket. Initial token retrieval requires trusted USB;
later configuration changes can use an authenticated existing link.

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
{"type":"hello","protocol":"uvir-sensor-v1","device_id":"…","firmware":"0.5.106"}
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
6. If this host will own the archive, send `SYNC_BEGIN` and safely import any
   queued records. A live-only reader must not blindly acknowledge them.
7. Start `STREAM <interval_ms>` or issue other commands.
8. Send `STOP` before deliberately releasing the live connection.

Throughout the active session, send `PING` at approximately four-second
intervals, including during synchronization or output tests. The owner lease
expires after 12 seconds without incoming host commands. Receiving stream
frames alone does not keep a session alive. Parse keepalive replies and other
asynchronous frames independently of the operation currently being awaited.

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

`rtc_oscillator_stopped` is the DS3231 oscillator-stop flag (OSF). It means
that the clock stopped at some point; it is not a battery voltage, charge level,
or reliable early low-battery warning. Causes include first use, a missing or
depleted backup cell, poor contacts, or other clock faults. A successful `TIME`
clears OSF after setting the clock. Hosts needing to warn users must capture the
accepted `HELLO` flag before sending `TIME`, not consult a cached snapshot later.

Android checks each associated sensor on its first accepted `HELLO` in every
app launch, including background sensors. Stops are grouped in one closable,
scrollable warning; reconnections and Activity recreation do not repeat a
dismissed warning during that launch. Missing RTC modules, missing protocol
fields, and failed reads without an OSF indication do not imply a depleted
battery. No battery model or chemistry is assumed, and no new hardware or
database fields are required.

Use capability fields such as `conditional_acquisition_supported` and
`external_command_supported` before presenting related controls.

Firmware 0.5.106 adds `calibration_locked` to HELLO and activity frames. It is
true for actual work that prevents calibration changes, but excludes the
configuration command indicator and its short LED hold. `operation_active`
continues to include that visual activity. Clients must not interpret a
`calibration_busy`, `calibration_invalid` or `calibration_save_failed` response as
a lost authenticated transport; retain the connection and report the rejected
edit. Calibration/window factors continue to travel together in one command.

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

The **v1 compatibility transport** unit is always `uW/cm2`; display-unit conversion is a
client responsibility. Firmware 0.5.112 and Android database v25 use `W/m2`
internally, converting exactly once at the v1 boundary. `HELLO` additionally
reports `canonical_unit: "W/m2"` and `storage_record_version: 3`; these describe
internal storage, not the unit of v1 sample/threshold/offset numbers. See the
[SI migration notes](SI_IRRADIANCE_MIGRATION.md). When `uv_available` is false, a client must not present
UV values as valid measurements. `saturated == true` means at least one value
was outside the usable range and should be represented as overload rather than
as a precise number.

### `STOP` / `SLEEP`

Releases live ownership/streaming for the active transport and closes an
interrupted sync reader. Rolling measurements continue at the configured
cadence, with sensors powered down between reads. It does not erase records,
stop an autonomous job, stop alerts, or enter ESP32 deep sleep. Use
`OFFLINE_STOP`, `ALERT_CONFIG OFF ...`, or `POWER_OFF` for those separate actions.

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

Channel identifiers remain stable: the protocol field/metric `yellow`/`YELLOW`
is displayed as green-yellow and represents the mean of AS7343 F5/FY (nominal
peaks 550 / 555 nm), not a dedicated yellow passband. Similarly, HEV sums the
violet/blue estimates and is displayed without its own wavelength list.
See [channel definitions](OPTICAL_WINDOW.md).

Both factors must be finite values from 0.1 to 10.0. Calibration is persistent.
The legacy form also disables optical-window compensation and resets its factors
to one. To retain/configure independent window factors, use firmware 0.5.106+:

```text
CALIBRATION_CONFIG <visible_factor> <uv_factor> <ON|OFF> <uvc> <uvb> <uva> <violet> <blue> <green> <yellow> <orange> <red> <far_red> <nir>
```

All eleven window factors are required, finite, and between 1 and 10. An unchanged
configuration is a no-op; an actual calibration/window change is rejected while
a sensor activity is running. A failed persistent write retains the prior
configuration. HELLO and calibration replies expose
`"optical_window":{"version":1,"enabled":false,"factors":[1,1,1,1,1,1,1,1,1,1,1]}`.
Samples, acquisitions, alerts and synchronized records carry their capture context
when known. The values are already corrected; clients must not apply the factors
again. An absent context on an older record remains unknown.
See [optical-window limits and band definitions](OPTICAL_WINDOW.md).

Firmware **0.5.108+** accepts one dark offset per displayed color/channel:

```text
CALIBRATION_CONFIG <visible_factor> <uv_factor> <ON|OFF> <11 window factors> COLOR_OFFSETS <11 dark offsets>
```

Color offset order is **UVC, UVB, UVA, violet, blue, green, green-yellow, orange,
red, far-red, NIR**, the same order as optical-window factors. Values are finite,
nonnegative v1 wire **uW/cm2**, between 0 and 1000000. Supply all eleven values;
zero means no subtraction. Physical channels are normalized and paired using
the usual color means, then the color offset is subtracted and clamped once at
zero, before the base and optical-window factors, conditions and saves. This is
not equivalent to clamping each constituent physical channel separately.

HELLO/calibration status returns
`"calibration_offsets":{"version":2,"unit":"uW/cm2","values":[0,0,0,0,0,0,0,0,0,0,0]}`.
Configuration is validated and persisted atomically with base/window factors;
changes remain blocked during real work. Previously saved readings are never
recalculated, and the current configuration is not historical record provenance.

For backward compatibility, firmware **0.5.107+** also accepts the earlier fifteen
physical-channel offsets:

```text
CALIBRATION_CONFIG <visible_factor> <uv_factor> <ON|OFF> <11 window factors> OFFSETS <15 dark offsets>
```

Offset wire order is **UVC, UVB, UVA, F1, F2, FZ, F3, F4, F5, FY, FXL, F6,
F7, F8, NIR**. Values are finite, nonnegative v1 wire **uW/cm2**, from 0 to
1000000. All fifteen must be supplied; zero means no subtraction. Display units
and locale never change this protocol. Old two-factor/window-only commands
preserve existing offsets; an explicit fifteen-zero suffix clears them.
The configuration is validated and persisted atomically with the base and
window factors, and cannot change during acquisitions, alerts or other work.
HELLO and calibration acknowledgements expose
`"calibration_offsets":{"version":1,"unit":"uW/cm2","values":[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]}`.
An absent object on older firmware means unknown/not supported, not measured zero.

In this legacy mode only, subtraction occurs after count-to-irradiance normalization, before each base
factor, paired-channel averaging, optical-window factors, conditions and saves.
Negative corrected channel values are floored at zero. No separate offsets
exist for HEV or group totals. Previously saved measurements are not recalculated;
current HELLO settings must not be treated as historical per-record calibration
provenance. The offline record layout is unchanged.

Firmware 0.5.108 retains this legacy calculation and its version-1 readback until
an explicit `COLOR_OFFSETS` edit replaces it. Window-only/two-factor commands
never switch modes or clear offsets. The Android color editor shows averages of
legacy paired offsets, warns when a nonzero legacy configuration would be
converted, and converts only when a color value is explicitly changed. Near zero,
the two clamp orders differ, so this is an intentional configuration change, not
a silent background migration. An all-zero `COLOR_OFFSETS` suffix clears offsets
and selects color mode. No database migration is needed for the versioned JSON.

### Per-channel calibration (firmware 0.5.109+)

Append `CHANNEL_FACTORS` and eleven finite, dimensionless values in **0.1–10.0**:

```text
CALIBRATION_CONFIG <visible_factor> <uv_factor> <ON|OFF> <11 window factors> [COLOR_OFFSETS <11 dark offsets>] CHANNEL_FACTORS <11 absolute correction factors>
```

The order is UVC, UVB, UVA, violet, blue, green, green-yellow, orange, red,
far-red, NIR. These absolute factors **replace**, rather than multiply, the two
module factors for the respective channels. The acquisition order is normalized
irradiance, the existing paired-color mean, dark-offset subtraction floored at
zero, channel correction, then optical-window compensation. Totals, conditions,
alerts and saved values use the corrected results. No independent total factor
is applied and no historical record is recalculated.

`HELLO` and calibration replies include `calibration_channel_factors`, either
`null` for legacy module-factor operation or
`{"version":1,"values":[1,1,1,1,1,1,1,1,1,1,1]}`. A missing suffix preserves the
existing channel factors. Once channel factors are configured, changing module
factors without the explicit suffix is rejected to protect calibration from
older clients. Set all eleven absolute values explicitly to restore a common
factor. Complete configuration is persisted atomically in `cal_state4`; startup
reads earlier calibration formats without writing or changing their behavior.

Android shows equal-width factor/offset fields for each channel. An unset
channel configuration displays the current module factors; opening the editor
does not save. A first explicit factor edit initializes the other channels from
those module factors. Settings exports/imports and the per-sensor cache retain
the full factor array. Database schema 24 adds a nullable sensor-settings column
only; older database imports are upgraded in a private extracted copy.

### Independent optical-window master (firmware 0.5.111+)

Append `WINDOW_MASTER <factor>` after any offset and channel-factor suffixes:

```text
CALIBRATION_CONFIG 1 1 ON 1.5 1 1 1 1 1 1 1 1 1 1 WINDOW_MASTER 1.2
```

The window correction is `master × channel`, applied once after dark subtraction
and calibration. Both components accept 1–10; their effective product may reach
100. A master of 1 is neutral and changing it never overwrites channel factors.
The master has six-decimal resolution. This is not a calibrated transmission
curve and cannot recover wavelengths blocked by a window.

Readback and per-record optical provenance use version 2 with a `master` number
and eleven individual `factors`. Version 1 remains readable and implies a master
of 1. Existing settings and readings are not rescaled. Once explicitly configured,
the master, including 1, must be included in subsequent calibration commands so
older clients cannot silently remove it.

Binary context version 2 identifies itself with signature `OPT2` and uses three
previously reserved bytes for a little-endian master in millionths. The 52-byte
optical structure, 180-byte calibration state, 64-byte optical journal entries
and acquisition record layout remain unchanged. Old `OPT1` contexts retain their
original calculation. There is no database migration or historical-record rewrite.

### Dark-offset measurement (firmware 0.5.110+)

```text
DARK_OFFSETS START <UV|VIS> <32-character hexadecimal request ID>
DARK_OFFSETS CANCEL <same request ID>
```

The sensor must be idle and the requested chip present. Android labels this
action **Calculate offsets**. Keep the reference condition stable: cover the
optical inputs completely, including near-infrared, with the final optical
assembly and normal LED operation for sensor dark-bias calibration. An illuminated
reference is allowed for deliberate background subtraction, but also subtracts
real ambient light, giving relative rather than absolute readings. The historical
`DARK_OFFSETS` command and reply names are unchanged. The transient job runs for about
30 seconds, taking roughly one sample per second (at least 20 required). It reads
normalized irradiance **before all user offsets, channel/module gains and window
compensation**, without changing or temporarily resetting any saved settings.
UV returns UVC/UVB/UVA; VIS returns violet/blue/green/green-yellow/orange/red/far-red/NIR.
The result is a mean-background proposal, not random-noise removal or absolute calibration.

Replies use `type: "dark_offsets"`, `protocol: "uvir-sensor-v1"`, `device_id`,
`request_id`, `group`, `state`, `elapsed_ms` and `samples`. State is `progress`,
`ready`, `cancelled` or `error`. A ready frame additionally contains `unit:
"uW/cm2"` and `values` (3 or 8 finite nonnegative offsets). An error contains a
machine-readable `error`; failed/saturated readings invalidate the proposal.

Keep the original authenticated transport alive with PING at intervals shorter
than 12 seconds. Link loss/lease expiry cancels measurement. Only its original
transport and request ID may cancel it; other state-changing commands are
rejected while busy. HELLO/PING and streaming controls remain serviced, but normal
live samples pause during measurement. Neither acquisitions, alerts nor settings
are saved. Applying a proposal requires a separate explicit CALIBRATION_CONFIG.

Android shows a closable, scrollable preview and writes offsets only on **Set
offsets**, retaining the other group, gains and window factors. Each calibration
group has a **Reset values** button opening the standard confirmation dialog:
hold its confirmation action for two seconds (own factors to 1, offsets to 0).
The per-channel window editor has an independent confirmation dialog resetting to all-ones,
retaining per-channel mode and leaving calibration untouched.

### `SENSOR_CONFIG`

Current syntax:

```text
SENSOR_CONFIG <ON|OFF> <brightness_1_100> <ON|OFF> <ON|OFF> <volume_1_100> <ON|OFF> <seconds_60_86400> <ON|OFF>
```

The eight fields mean LED enabled, LED brightness, autonomous recording enabled,
buzzer enabled, buzzer volume, automatic shutdown enabled, shutdown idle seconds,
and external-command input enabled, respectively. All boolean tokens are
literally `ON` or `OFF`, **not** `LED_ON`, `OFFLINE_ON` or `EXTERNAL_ON`.
Brightness and volume cannot be zero; disable the corresponding output instead.

For example, fresh defaults are represented by:

```text
SENSOR_CONFIG ON 20 ON ON 20 OFF 1800 ON
```

Do not send this default example to an already configured device unless those
are the intended new settings. Read `HELLO` and preserve unrelated user values.
These settings are persistent. The final token enables/disables GPIO 33 and
defaults to `ON`, including after a factory reset.
Firmware 0.5.83 and later also accept the previous command without the
last token and leave the saved external-command setting unchanged. Configuration commands
may evolve as hardware capabilities are added; read back `HELLO` after changing
them.

### Settings revision and `SETTINGS_APPLY`

`HELLO` includes `sensor_settings_schema` and `settings_updated_at_ms` for each
physical sensor. A newer local configuration may be submitted as:

```text
SETTINGS_APPLY <updated_at_epoch_ms> <hex_encoded_utf8_command_lines>
```

The sensor wins equal/older revisions and returns its current hello. A newer
revision is committed only after all accepted commands complete without errors.
This is not a rollback transaction: commands applied before an error or power
loss may already be persistent, while the old revision remains retryable.

Allowed inner commands are `SENSOR_CONFIG`, `SAMPLING_CONFIG`,
`CALIBRATION_CONFIG`, `WIFI_CONFIG`, `INTERNET_CONFIG`, `RADIO WIFI`,
`RADIO BLUETOOTH` and `ALERT_SETTINGS`. Separate them with LF before UTF-8 hex
encoding. Jobs, samples, resets, session controls and nested updates are not
allowed. Native accepted settings commands also advance the sensor revision;
`TIME` and keepalives do not.

The inner decoder permits up to 6000 decoded bytes. Firmware 0.5.107+ accepts an
outer command up to **16384 bytes including its hex payload** on all transports,
so a complete offset configuration fits. Oversized stream commands are discarded
through the next line terminator, rather than executing a truncated suffix;
oversized MQTT messages are rejected. Earlier USB/Wi-Fi/SPP firmware accepts only
512 bytes: keep those updates small. Read back settings and do not use the same
committed revision for later chunks (equal revisions are ignored).

### `ALERT_SETTINGS`

```text
ALERT_SETTINGS <repeat_seconds> <SAVE|NO_SAVE> <delay_seconds> <duration_seconds> <maximum_registrations> <rules_hex_or_->
```

The last field is UTF-8 hex for newline-separated `ALERT_RULE ...` commands,
or `-` for an empty rule list. It updates the saved configuration while retaining
the current enabled flag, session identity, counters, timing and cooldown.
It is not the command for starting/stopping an alert session. Use repeat 1–86400,
delay/duration 0–31536000 and maximum 0–999999; zero removes the respective limit.
Validate metrics/rules client-side and check replies; do not assume flash-level
atomicity or send an oversized hex-expanded command.

### `BUZZER_PREVIEW`

```text
BUZZER_PREVIEW <volume_1_100>
```

When `HELLO` advertises `buzzer_preview_supported: true`, the authenticated
active app connection can preview the selected buzzer volume with one 90 ms
acquisition beep. The buzzer must be enabled. This does not change any saved
settings, create a record, or stop an acquisition/alert session. Requests during
a buzzer self-test or diagnostic performance are rejected. A newer pending
preview replaces the previous pending preview instead of accumulating a queue.

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

The condition plan is a snapshot belonging to that acquisition session, separate
from value-alert rules. It is checkpointed with the active job so recovery after
power loss preserves the conditions; it is not merely a volatile RAM template.

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

Replace the complete rule set in one configuration sequence:

```text
ALERTS_CLEAR
ALERT_RULE <metric> <ABOVE|BELOW> <threshold>
ALERT_RULE <metric> <ABOVE|BELOW> <threshold>
ALERT_CONFIG ON <repeat_seconds> <session_id> <SAVE|NO_SAVE> <start_delay_seconds> <duration_seconds> <maximum_registrations>
```

Up to 24 distinct rules are accepted. Use `ALERT_CONFIG OFF ...` to stop alert
monitoring. Value alerts and automatic-acquisition conditions are different
features and do not overwrite one another.

`ALERTS_CLEAR` suspends evaluation while the rule list is being replaced;
`ALERT_CONFIG` completes the sequence. Check every reply and finish the update.
This evaluation guard is not a power-failure-atomic storage transaction.
Use only the supported metric names and finite, meaningful thresholds.

The last three parameters were added in firmware 0.5.97. They may be omitted
by older clients and then default to zero (no delay, no duration limit, no
registration limit). Duration begins after the start delay. The sensor checks
the limits autonomously, including when Android is disconnected. A completed
alert counts toward the maximum only after it has been emitted or stored;
zero means unlimited. A power interruption does not restart either deadline.

`NO_SAVE` keeps threshold checking, LED and buzzer feedback active without
creating alert records on the sensor. Connected `alert_event` frames still
report the occurrence with `"recorded":false`; clients must not add these
events to their databases. `SAVE` is the default when the recording-mode token is
omitted by an older client. The mode survives power loss with the alert
configuration. A no-save session uses session ID `0`.

Live `alert_event` frames contain timestamp, details, session ID and `recorded`,
but no record ID / `ACQUISITION_ACK` exchange. The connected host is responsible
for storing recorded alerts; this live delivery is not an ACK/replay guarantee.
Offline saved alerts instead use the acknowledgement-driven queue below.

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
start a periodic PING task (about every 4 seconds after APP_CONNECT)
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

Radio provisioning, authenticated UDP discovery, TCP ports, Bluetooth SPP,
MQTT topics/TLS, a live reader and autonomous-job examples are in
[Standalone sensor operation](STANDALONE_SENSOR.md). Hardware assembly has its
own [complete component wiring index](../hardware/wiring/README.md).
