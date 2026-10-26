# Uvir ESP32 sensor firmware

This sketch is the first hardware bridge between Uvir for Android and an
ESP32 Dev Module fitted with a GY-AS734X breakout containing an AS7343 and,
optionally, an AS7331 UV sensor. It streams visible, far-red, NIR, UVA, UVB and UVC bands over
USB serial, the local Wi-Fi network, Bluetooth Classic SPP, or an encrypted
Internet relay. Firmware keeps
working with UV values unavailable when the optional AS7331 is not installed.

The current prototype has both the GY-AS734X / AS7343 and MIKROE UV 5 Click /
AS7331 boards installed and detected. The 2026-10-12 replacement visible-board
check confirmed the same AS7343 chip through the driver's device-ID validation;
no driver, GPIO assignment or calibration change is needed for the board label.
Development is currently on hold pending final optical design validation and
reference calibration. The app and sensor communication/acquisition work, but
the theoretical lid and apertures are not validated measuring optics and
current readings do not establish accurate absolute irradiance; see
[`hardware/calibration`](../../../hardware/calibration/README.md).

The current release is firmware **0.5.119**, published with app **1.4.2**.
It adds fresh-sample/boot fencing and Wi-Fi recovery refinements to the
0.5.114 sampling changes below. These protect live state across interrupted
connections; they do not calibrate the optical assembly.

Firmware **0.5.114** adds automatic AS7331 gain (1x–2048x at fixed 64 ms)
and returns **Calculate offsets** to arithmetic means for UV, like visible/NIR.
The gain is chosen from the most occupied ADC, with hysteresis and at most three
conversions per capture; analog/digital overload or stale data are never published.
The first reference reading settles the gain, which is then held for that run.
Ready replies identify `estimator: "mean"`; the app requires 0.5.114+ for UV
calculation and rejects maximum-based previews from 0.5.113. Signed corrected
color readings are averaged before the final zero clamp, including trimmed
averages. Legacy physical-channel offset semantics are preserved. Existing
offsets are not recalculated or changed until the user applies a new proposal.
No database or stored-record layout changes are needed. See the
[offset measurement protocol](../../../docs/USB_SERIAL_PROTOCOL.md#dark-offset-measurement-firmware-05110).

Firmware **0.5.112** uses SI `W/m²` internally and for new offline records.
Legacy calibration/runtime states and queued v1/v2 records are read using their
original units; new calibration uses an atomic `CAL5` block and record version 3
retains the existing queue/FRAM layout. The explicitly labelled v1 interface
continues to exchange `uW/cm2` so older serial clients do not get a silent ×100
change. See the [SI migration notes](../../../docs/SI_IRRADIANCE_MIGRATION.md).

Firmware **0.5.111** adds an independent optical-window master applied as
general × channel without overwriting the eleven individual factors. It is saved
atomically on the sensor and retained in offline optical context. Existing
configuration uses a neutral master without changing readings or record layouts.
See [the serial protocol](../../../docs/USB_SERIAL_PROTOCOL.md#independent-optical-window-master-firmware-05111).

Firmware **0.5.110** adds read-only, approximately 30-second dark-offset
measurement for the UV or visible/NIR group. `DARK_OFFSETS START UV|VIS <request_id>`
collects normalized readings before user corrections; `DARK_OFFSETS CANCEL <request_id>`
aborts the same request. A correlated `dark_offsets` preview never changes NVS
or writes acquisition/alert records. See the [serial protocol](../../../docs/USB_SERIAL_PROTOCOL.md#dark-offset-measurement-firmware-05110)
for the lease, reply states, sample order and explicit application step.

## Settings authority and interrupted transfers

Firmware 0.5.102 reports `settings_updated_at_ms` in `HELLO`: UTC Unix
milliseconds of the last committed configuration change, persisted in NVS.
Receiving `TIME`, connecting, reading samples and running sessions do not refresh
this revision. Native serial configuration edits also advance it monotonically,
even if the runtime clock is unavailable or moves backwards.

Android keeps a separate revision/outbox for each hardware identity. Hardware
wins when its revision is equal or newer; otherwise Android resends its pending
configuration after authentication. Writes are queued durably before transfer
and are not acknowledged merely because a socket write succeeded. Pending
commands containing network credentials are excluded from Android cloud/device
backups; explicit encrypted settings exports include the desired settings.

`SETTINGS_APPLY <updated_at_ms> <hex_utf8_commands>` applies a bounded batch of
configuration commands separated by newlines. Its revision is committed only
after all commands succeed. Older/equal batches are ignored and answered with
`HELLO`. Interrupted/partially applied batches keep the previous revision and
can be retried. Radio reconnection is deferred until after commit/readback.
This is a recoverable configuration batch, not an atomic flash transaction.
The allowlist excludes acquisition, reset, session start/stop and other
operational commands. `ALERT_SETTINGS` synchronizes alert rules/timing without
changing an active session's identity, counters, cooldown or enabled state.

The outbox is invalidated when the association's access token changes (factory
reset), when that sensor is disassociated, or when the app's associations are
reset. Older firmware retains its previous sensor-authoritative readback behavior.

## Current wiring

For construction, start with the [complete component wiring index](../../../hardware/wiring/README.md),
[bill of materials](../../../hardware/BILL_OF_MATERIALS.md) and
[assembly / acceptance guide](../../../hardware/ASSEMBLY.md). The tables below
are the firmware-oriented summary, not the only wiring documentation.

| GY-AS734X / AS7343 | ESP32 Dev Module |
|---|---|
| SDA | GPIO 21 |
| SCL | GPIO 22 |
| GND | GND |
| VIN/VCC | See the voltage warning below |
| I / INT | Not connected; optional active-low interrupt output |
| G / GPIO | Not connected; optional trigger/synchronization GPIO |

MIKROE UV 5 Click / AS7331 on the same I2C bus (current prototype):

| AS7331 | ESP32 Dev Module |
|---|---|
| SDA | GPIO 21 (shared with AS7343) |
| SCL | GPIO 22 (shared with AS7343) |
| GND (both mikroBUS ground pins) | GND |
| 3V3 | 3.3 V |
| RDY | Not connected |
| SYN | Not connected |
| 5V / other unused mikroBUS pins | Not connected |

The sensors do not conflict: AS7343 uses address `0x39` and AS7331 uses
`0x74`. The AS7331 runs in command/one-shot mode and therefore does not need
its `RDY` or `SYN` pins for Uvir. SparkFun AS7331 boards remain supported by
the driver, but their connectors and footprints are not interchangeable with
the UV 5 Click board; use the current carrier R1 pin map for that module.

HW-084 / DS3231 battery-backed real-time clock on the same I2C bus:

| RTC module | ESP32 Dev Module |
|---|---|
| SDA | GPIO 21 (shared I2C bus) |
| SCL | GPIO 22 (shared I2C bus) |
| VCC | 3.3 V |
| GND | GND |
| SQW / 32K | Not connected |

The DS3231 uses address `0x68`; the module's optional AT24C32 EEPROM commonly
uses `0x57` and is not used by Uvir. The backup cell powers only the clock when
main power is absent, so the module power LED going out is normal. If a CR2032
is fitted, the module's charging path must remain disabled. Uvir stores UTC in
the RTC and converts it for display in Android.

`HELLO` exposes the RTC oscillator-stop flag. Android captures it before `TIME`
clears it and checks each sensor anew on every app launch. A grouped warning
lists affected sensors and suggests checking or replacing their backup cells,
without assuming a battery model. OSF can also indicate first use or other
clock faults; the DS3231 does not measure battery voltage or remaining charge.
The startup warning needs no firmware upgrade, extra wiring, or database migration.

32 KiB MB85RC256V I²C FRAM on the shared bus:

| FRAM module | ESP32 Dev Module |
|---|---|
| SDA | GPIO 21 |
| SCL | GPIO 22 |
| VCC | 3.3 V |
| GND | GND |
| WP / A0 / A1 / A2 | Leave at the module defaults; do not drive high |

The tested board answers at `0x50`, independently of AS7343 (`0x39`), DS3231
(`0x68`) and optional AS7331 (`0x74`). FRAM keeps two checksummed copies of
the current activity state and up to 92 pending final records. Each record is
committed after a readback, then removed only after Android acknowledges it.
The remaining 4 KiB are reserved for future diagnostics. If FRAM is absent or
belongs to a different sensor, the microSD/LittleFS path remains available;
foreign FRAM contents are never silently erased.

Generic 6-pin microSD adapter with onboard level conversion (durable overflow
storage):

| microSD adapter | ESP32 Dev Module |
|---|---|
| CS | GPIO 13 |
| SCK | GPIO 18 |
| MOSI | GPIO 23 |
| MISO | GPIO 19 |
| VCC | 5 V / VIN |
| GND | GND |

The tested generic board uses an SN74HC125 level-conversion buffer and a 3.3 V
regulator, so its `VCC` input is powered from 5 V while all SPI signals remain
safe for the ESP32. Use a FAT32 card. The firmware binds a newly initialized
card to the ESP32 hardware identity, stores overflow acquisitions, alerts and
sensor errors under `/uvir`, and keeps two checksummed activity checkpoints as
a fallback when FRAM is unavailable. A card
belonging to another sensor is never silently adopted. If the card is missing,
the internal LittleFS partition remains a deliberately small emergency fallback.
Existing valid LittleFS records are migrated to the card without changing their
record IDs.

Capacity is calculated from the actual mounted card and the current binary
record size, while reserving 5% of the card (at least 16 MiB) for filesystem
headroom. The app reports total bytes, free bytes, total record capacity and
records still available in **Sensor information**. The diagnostic test performs
a temporary write/read/delete round trip without creating a measurement record.

Optional common-cathode RGB connection LED:

| RGB LED | ESP32 Dev Module |
|---|---|
| Common cathode | GND |
| Red anode | GPIO 25 through its own 330 Ω resistor |
| Green anode | GPIO 26 through its own 330 Ω resistor |

The RGB blue anode is not connected. GPIO 27 instead drives a separate blue
synchronization LED:

| Blue synchronization LED | ESP32 Dev Module |
|---|---|
| Anode | GPIO 27 through its own 330 Ω resistor |
| Cathode | GND |

Use one resistor per connected LED channel. A 470–1000 Ω value is also valid
when a lower physical brightness is preferred. The current firmware expects a
common-cathode RGB LED; a common-anode LED requires inverted output logic.

Optional low-current passive piezo buzzer:

| Passive buzzer | ESP32 Dev Module |
|---|---|
| Positive (`+`) | GPIO 32 |
| Negative (`-`) | GND |

The direct connection above is intended for a small passive piezo buzzer. An
active buzzer, magnetic transducer, or three-pin module may require a series
resistor or a transistor driver according to its electrical specifications.
The firmware generates the tones by PWM and defaults to 20% volume. Status LED
brightness also defaults to 20%. Both defaults apply only when no stored setting
exists; updating the firmware never replaces an existing user-selected percentage.
Acquisitions use one 1400 Hz beep; each of the three alert beeps rises from
1800 to 2400 Hz, with unchanged timing. Volume uses 12-bit PWM capped at
17.1875% duty: on the assembled prototype, the former 30-40% slider range was
the useful peak and higher settings sounded weaker/distorted. The new 1-100%
scale spans that conservative usable range; it is not a calibrated SPL scale.
Revalidate this limit for a different buzzer. Stored volume percentages are
preserved, but their electrical mapping changes. Previews, self-tests and
diagnostic melodies use the same range without changing stored settings.

External acquisition command (active low):

| Command contact | ESP32 Dev Module |
|---|---|
| Input | GPIO 33 (`INPUT_PULLUP`) |
| Return | GND |

A dry pushbutton or open-collector/open-drain output closes GPIO 33 to GND.
The input is enabled by default and its state is stored persistently by the
sensor; it can be disabled from **Settings > Management** in the Uvir app.
One short press records a standalone acquisition, or the next acquisition in
an external session. Three consecutive short presses start an external session;
the gesture itself does not create three records. A triple press is ignored
while any session is already active. Holding for two seconds stops the active
automatic or value-alert session. All gestures require a valid sensor clock;
otherwise the RGB LED flashes orange rapidly five times and the buzzer emits one
prolonged beep. Never apply 5 V directly to GPIO 33: use an optocoupler or a
properly designed level-shifting/protection stage for an industrial 5 V command.

The bare AS7343 operates at 1.8 V. Only connect the module to the ESP32 3.3 V
pin if the exact GY-AS734X board includes a regulator and I2C level shifting.
Check the markings/schematic of the purchased module first. Never feed a bare
AS7343 directly from 3.3 V.

The `I` and `G` pads are not required by the current polling-based firmware and
should remain unconnected. A normal power bank exposes regulated USB power but
does not report its remaining charge to the ESP32. Battery percentage requires
a separate battery/fuel-gauge circuit (for example a dedicated I2C fuel-gauge
module); measuring the regulated 5 V USB output is not a useful state-of-charge
indicator because it stays nearly constant until the power bank shuts down.

## Upload with Arduino IDE

1. Open `UvirSensor.ino` from this folder.
2. Select the board **ESP32 Dev Module**.
3. Under **Tools → Partition Scheme**, select **Huge APP (3MB No OTA/1MB
   SPIFFS)**. The included `partitions.csv` mirrors this 3 MB application
   layout and gives the combined Wi-Fi/Bluetooth firmware enough space.
4. Select the ESP32 serial port.
5. Install **SparkFun AS7343 Arduino Library**, **SparkFun AS7331 Arduino
   Library**, **SparkFun Toolkit**, and **ArduinoMqttClient** from the Library Manager if Arduino
   reports a missing library.
6. Upload the sketch.

The equivalent command-line build is:

```text
arduino-cli compile --fqbn esp32:esp32:esp32:PartitionScheme=huge_app firmware/esp32/UvirSensor
```

The repository copy is the working source: open and save this file directly
from this folder, so later changes stay under version control.

## USB protocol

For independent PC/controller use, see [Standalone sensor operation](../../../docs/STANDALONE_SENSOR.md)
for USB, local Wi-Fi, Bluetooth and MQTT setup plus runnable live examples.
The [protocol reference](../../../docs/USB_SERIAL_PROTOCOL.md) defines measurement
schemas, automatic jobs and acknowledgement-safe offline recovery.

The serial port uses 115200 baud, 8 data bits, no parity and one stop bit. Each
message is one JSON object followed by a newline. Protocol name:
`uvir-sensor-v1`.

Firmware image size is calculated once for the startup identity report and
cached in RAM until reboot. Periodic identity checks keep reporting current
settings and runtime information without re-verifying the flash image.
Wi-Fi JSON output uses a fixed 512-byte buffer to combine small formatting
writes. Every newline is sent immediately; reconnects discard any unfinished
output, and a failed send closes the socket rather than continuing a truncated
JSON frame. USB, Bluetooth and MQTT output are unchanged.

Commands accepted by the ESP32:

- `HELLO` or `PING`: report identity and capabilities;
- `DIAGNOSTIC <32-character hexadecimal request ID>` (full component report in
  firmware 0.5.92+): return one correlated `diagnostic` frame containing
  identity, firmware, processor, RAM/flash, connection state and the hardware
  map. Each probe checks the AS7343, optional AS7331 and DS3231 on I²C, performs
  a temporary microSD read/write round trip, reads the external input and
  reports the LED and buzzer control paths, pins and current configuration.
  The temporary microSD file is deleted immediately; settings, sessions and
  measurement records are never changed. LED and buzzer outputs are not
  actuated automatically because their final optical/acoustic result requires
  physical confirmation. Credentials are never included. Android sends six
  spaced probes over the selected transport, measures round-trip latency at
  receipt, and offers the localized report in a scrollable dialog and UTF-8 TXT
  export.
- `APP_CONNECT`: confirm that the authenticated transport is the source
  currently selected by Uvir, before synchronization and live streaming;
- `STREAM 150`: request one sample about every 150 ms;
- `SAMPLE`: return the newest completed rolling average; it does not trigger a
  second physical sampling sequence;
- `SAMPLING_CONFIG 5 150 1`: keep five raw samples in ESP32 RAM, one every
  150 ms, discard the per-band minimum and maximum, and emit only the final
  averaged result;
- `LED_EVENT ACQUISITION`: briefly keeps the RGB activity indication visible
  after Android confirms a manual acquisition was saved. Automatic
  acquisitions are timed and signalled directly by the ESP32;
- `LED_EVENT ALERT`: compatibility/diagnostic command that shows the same RGB
  activity indication. During normal live use the ESP32 evaluates the
  configured rules on the same final averaged result sent to Android and
  signals the activity locally, without waiting for a return command from the
  phone;
- `LED_TEST`: when connected and idle, reproduces every status signal in legend
  order, then automatically restores the current real sensor state;
- `BUZZER_TEST`: when connected and idle, reproduces the connection,
  disconnection, activity-start, activity-stop and saved-record sounds, then
  returns to silence;
- `BUZZER_PREVIEW 30`: previews the buzzer at 30% with one short acquisition
  beep, without changing persistent settings or creating records. Requires an
  authenticated active connection and an enabled buzzer; can run during a
  recording session, but not during a buzzer test or diagnostic performance;
- `DEBUG_PERFORMANCE HAPPY_BIRTHDAY`, `DEBUG_PERFORMANCE INDIANA_JONES`,
  `DEBUG_PERFORMANCE JURASSIC_PARK`, or `DEBUG_PERFORMANCE STAR_WARS`: while
  connected and idle, starts the selected
  diagnostic theme locally on the ESP32. Notes and LED timing are stored in
  firmware, while the saved buzzer/LED switches, volume and brightness remain
  authoritative;
- `DEBUG_PERFORMANCE_STOP`: immediately cancels the current diagnostic theme
  and restores the real connection/activity and synchronization LED state;
- `DEBUG_FRAME 784 220 255 150 0`: legacy diagnostic command that plays one
  audiovisual frame without overriding saved buzzer or LED settings;
- `POWER_OFF`: when connected and idle, enters deep sleep. The sensor must then
  be restarted with EN/RESET or by cycling its power;
- `FACTORY_RESET`: restores sensor configuration and credentials to defaults,
  deletes every unsynchronized acquisition, alert and sensor error, and removes
  every Bluetooth Classic pairing stored by the ESP32. The Wi-Fi driver's own
  persistent network configuration is also restored and SSID/password readback
  is verified empty, even when resetting over USB or Bluetooth with Wi-Fi off.
  Reset cleanup starts STA without connecting to the old saved network and
  disables automatic reconnection. It acknowledges that reset has started before
  disconnecting the current peer, verifies both driver stores, and then enters
  deep sleep. A shared durable retry marker survives configuration erasure:
  interrupted/failed Bluetooth or Wi-Fi credential cleanup is retried
  at startup before any normal radio is enabled. Android may still retain its
  own old pairing entry, which must be forgotten/repaired on the phone. This
  does not change Uvir's app-only disassociation or revoke broker/router secrets;
- `STOP` or `SLEEP`: close the active app stream. The rolling measurement
  window continues in RAM so physical commands remain immediately usable;
  both spectral sensors still return to power-down between individual reads.
- `WIRELESS OFF`, `WIRELESS WIFI`, `WIRELESS BLUETOOTH`, or `WIRELESS INTERNET`: select and persist
  the only radio that should remain available after USB is disconnected.
- `RADIO WIFI ON/OFF` and `RADIO BLUETOOTH ON/OFF`: enable or disable each
  wireless transport persistently. An authenticated active radio may control
  the other one. USB is required only after both wireless transports have been
  disabled, because no wireless recovery channel then remains.
- `WIFI_CONFIG <SSID_HEX> <PASSWORD_HEX>`: store the existing WPA2/WPA3 Wi-Fi
  network credentials. It is accepted over USB or an already authenticated
  Wi-Fi/Bluetooth connection. USB remains the recovery path when no wireless
  connection is available.
- `INTERNET_CONFIG <ON|OFF> <PRIMARY|SECONDARY> <SSID_HEX_OR_-> <PASSWORD_HEX_OR_-> <BROKER_HOST_HEX_OR_-> <PORT> <MQTT_USERNAME_HEX_OR_-> <MQTT_PASSWORD_HEX_OR_->`:
  configure the TLS MQTT broker. `PRIMARY` reuses the normal Wi-Fi
  credentials; `SECONDARY` stores a separate network, useful with a portable
  router. Both the sensor and app connect outward, so no sensor-router port is
  exposed.
- `WIFI_DISCONNECT`: accepted only through an authenticated Wi-Fi connection;
  sets the persisted wireless mode to off and stops fallback/radios. This is
  distinct from changing the `wifi_enabled` preference using `RADIO WIFI OFF`.
  Use another available trusted connection (normally USB) to select a radio again.

The ESP32 stops sending frames automatically if it does not hear from the
active host for 12 seconds. Send `PING` about every four seconds; receiving
samples alone does not renew the lease. Measurement continues at the configured cadence
even without an active stream, keeping the latest averaged result ready in
RAM for autonomous and external commands. The optical devices return to
power-down between reads. The default/recommended
interval is 150 ms; the safe range is 150–5000 ms. A capture that takes longer
still finishes before another sample is taken. Existing saved intervals are
preserved; the default applies to an unconfigured or factory-reset sensor.

Firmware 0.5.115 adds a per-boot `boot_id` to `HELLO` and live sample frames.
Live samples include `sample_seq` (advanced only for a new averaged result),
`sample_age_ms`, `sample_max_age_ms` and `sample_valid`. A cached result expires
after the greater of 500 ms and three configured sample intervals. If a fresh
result is unavailable, `sample_status` reports `sample_valid:false` and a reason
instead of retransmitting stale numbers. These fields do not change stored
records, calibration or irradiance units. Android clears unavailable readings,
reopens the app session after a new boot and detects silent Wi-Fi links after
six seconds; a closed TCP socket is handled immediately. A replacement Wi-Fi
client gets the existing bounded authentication timeout before radio fallback
can change the connection method.

Firmware 0.5.116 also reports `wifi_status_code`, `wifi_disconnect_reason` (the
ESP-IDF reason code) and `wifi_disconnect_count` in `HELLO`. Network events only
update these counters; the main loop serializes them without interleaving
protocol frames. These diagnostics contain no Wi-Fi passwords or tokens.

Firmware 0.5.117 distinguishes Wi-Fi association from IPv4 readiness
(`wifi_associated` versus `wifi_connected`). An associated station receives up
to 45 seconds to finish DHCP before reconnection or radio fallback interrupts
it. Discovery/authentication then gets a fresh radio window after IPv4 is
ready. This recovery policy applies to local Wi-Fi and Internet/MQTT.

Firmware 0.5.118 checks association plus the actual nonzero IPv4 address for
network readiness. While waiting for an address it disables modem power
saving, renews DHCP once after 15 seconds without dropping association, and
retains the bounded 45-second recovery window. Normal modem power saving is
restored when IPv4 is ready. `HELLO` includes DHCP state/restart results, modem
power saving, associated access-point BSSID and RSSI for diagnosis.
After a wireless app connection closes, the sensor keeps the same radio for a
full 20-second reconnect window before falling back. This lets Android app
updates/restarts reconnect without chasing a radio that switched immediately.
An explicit source-switch command still applies immediately.

Firmware 0.5.119 scans all channels and ranks matching access points by signal
strength before connecting, for both local Wi-Fi and Internet/MQTT. It avoids
fast scan stopping at the first, weaker repeater when several access points
share the configured SSID. Credentials and SSID remain unchanged.

## Wi-Fi and Bluetooth provisioning

Connect the ESP32 to Uvir by USB after flashing firmware 0.5.46 or later. The app learns
the device's unique ID, private authentication token, Bluetooth name and
pairing PIN. The identity is generated once and kept in ESP32 NVS and Android
private storage, so two otherwise identical sensors cannot be confused.

In Uvir, enter the name and password of the Wi-Fi network already used by the
phone. The first configuration may use USB; later changes may also be sent over
an already authenticated Wi-Fi or Bluetooth connection. The password is kept
in ESP32 NVS and Android private storage and is never returned over a wireless
connection.

Select Wi-Fi, Bluetooth or Internet from the sensor-selection connection controls
while USB is still connected. After provisioning, Uvir can switch radio modes
through whichever authenticated wireless connection is currently active.
The app waits for a `status` acknowledgement of the requested `wireless_mode`
before committing a connected handover. The new mode gets a fresh 20-second
window before normal fallback; repeated requests for an already exposed but
unauthenticated radio preserve and refresh that window too. A rejected request
leaves the previous source selected. Automatic jobs and alert sessions are not
stopped by a transport handover.
Selecting USB as the measurement source does not turn off the last wireless
radio, so disconnecting the data cable never strands the sensor offline. The
spectral devices remain powered down between individual conversions; use the
explicit Wi-Fi disconnect control when the radio itself must be disabled.
The ESP32 persists the chosen radio and keeps only that radio enabled. At
startup it tries the last successfully authenticated mode first. If Uvir does
not authenticate within 20 seconds, the sensor tries the next configured enabled
mode among local Wi-Fi, Bluetooth and Internet. Once Uvir authenticates, that
mode is saved and fallback stops; modes are not left active concurrently.
A disabled/unconfigured mode is excluded. Disabling all wireless modes leaves
the sensor in USB-only mode:

USB identity checks (`HELLO`/`PING`) do not interrupt fallback, which is useful
when the ESP32 is powered from a computer while the phone uses a wireless
source. Actual USB sampling (`SAMPLE`/`STREAM`) or an explicit radio command
stops the startup alternation.

- Wi-Fi joins the same existing router/access point as the phone, proves its
  identity through nonce-based HMAC discovery on UDP port `8732`, and serves
  the authenticated sensor protocol over TCP port `8733`;
- Bluetooth exposes the same unique name over Classic SPP and requires the
  generated six-digit pairing PIN;
- both wireless transports additionally require the private protocol token.

The periodic identity response also reports the current firmware, sensor and
board identity, uptime, effective sampling settings, available memory and radio
state. Wi-Fi signal is reported as RSSI in dBm. The current Bluetooth Classic
stack exposes a relative RSSI delta, which Uvir presents as link quality rather
than incorrectly labelling it as an absolute dBm value.

## Sampling and autonomous operation

The ESP32 is the single owner of real-sensor sampling. The configured raw
samples are kept only in RAM, the optional highest and lowest values are
removed independently for each band, and only the averaged result is sent to
Android. Android therefore does not average real frames a second time. The
same processing is used through USB, Wi-Fi and Bluetooth and while the phone
is absent. A transport-independent rolling window keeps the newest completed
average ready even when no app is connected. A physical external command
snapshots that value; only immediately after startup or a sampling/calibration
change does it wait for the replacement window to become valid. Each raw
sampling step performs one matching AS7331 one-shot
conversion; the UV sensor immediately returns to power-down before the next
step. Mock data remains generated and averaged by Android because no physical
sensor exists in that mode.

A logical acquisition tolerates up to three physical-read attempts for each
required sample. Retries are scheduled on later loop passes with a short delay,
so a transient I2C error does not discard an external command and does not block
connection or stop handling. This retry count is independent from the configured
number of raw samples used to calculate an averaged result. Once a final record
exists, live delivery remains pending until Android acknowledges durable storage;
offline writes are retried without replacing the record with a later reading.

When automatic acquisition or value alerts are active and the authenticated
phone connection disappears, the sensor continues from the last time and job
configuration received from Uvir. Completed acquisitions, alert events and
sensor errors are queued in FRAM first, with microSD (or emergency LittleFS)
as fallback or overflow. Raw intermediate samples are never persisted. At
reconnection the queue is transferred to Android. Each FRAM record is removed
after its individual acknowledgment; the filesystem queue is cleared after
its complete acknowledged transfer. The Android database deduplicates
repeated transfers by sensor ID and sensor record ID.

Autonomous offline recording can be disabled from **Sensor parameters**. This
pauses automatic acquisitions and alert evaluation only while the app is
disconnected; connected operation is unchanged and no final record is written
to flash unnecessarily. The same section offers an optional automatic shutdown,
disabled by default. Its idle timer lives in RAM and starts only when the app is
disconnected and no acquisition, alert monitoring, pending result or
synchronization is active. Waking from this low-power shutdown requires a
physical reset or a power cycle.

The active automatic-session and alert state is journaled in FRAM at meaningful
transitions and completed acquisitions, not on every raw sample. The RTC gives
the absolute time after power loss; recovery resumes valid work, closes expired
work and skips missed intervals without inventing measurements. If FRAM is
absent, the existing redundant microSD checkpoints remain the fallback.
Firmware 0.5.97 also journals alert-session start delay, end deadline and
completed-registration count; these limits remain effective without an app
connection and after a power cycle.

Firmware 0.5.98 makes acquisition and value-alert sessions mutually exclusive:
`OFFLINE_JOB` and `OFFLINE_EXTERNAL_JOB` are rejected while value alerts run,
and `ALERT_CONFIG ON` is rejected while automatic acquisition runs. `LED_TEST`
now includes the rapid orange, red and green event patterns as well as the
steady and slow-blinking states. If a journal written by older firmware
contains both activities, recovery keeps the automatic job and closes alert
monitoring without deleting its already stored records.

Firmware 0.5.100 rejects a short GPIO 33 acquisition during a normal automatic
or value-alert session with five orange flashes and one long tone. A short
press is accepted in an external-command session, or when no sensor job is
active. In the latter case, the app attaches the external record to an active
manual app session when one exists. `external_command` identifies the
individual record in live and synchronized messages. A long hold can still
stop the active sensor session.

The included custom partition reserves about 960 KiB for this queue. The
compact version-2 format supports up to 3200 final records while preserving
filesystem headroom. A legacy queue keeps its proven 1800-record ceiling until
it has been synchronized and cleared. The limit is shared by acquisitions,
alerts, and sensor errors. Existing records
are never overwritten: when the queue becomes full, autonomous recording is
stopped and the condition is reported to the app. After reconnection Android imports and
acknowledges every record before the ESP32 clears the queue; the recovery
summary also reports whether recording stopped because the memory was full.
The app uses the capacity and remaining-record values reported by the firmware
to show an offline-autonomy estimate if the connection is lost while automatic
acquisition or value alerts are active.

The RGB LED reports connection and activity: steady red means powered but not
connected to the app, flashing red means disconnected with an activity in
progress, flashing yellow means that an app connection is in progress, steady
green means connected, and flashing green means connected with an activity in
progress. The separate blue LED has one status meaning: steady blue indicates
that acquisitions, alerts, or errors remain in sensor memory waiting to be
synchronized. Three rapid red flashes indicate an acquisition or value alert
while disconnected; three rapid green flashes indicate the same while
connected. Five rapid orange flashes report that an explicit acquisition
cannot be saved, for example because date and time are unavailable or
value-alert monitoring is active. The RGB and blue indicators
work independently, so synchronization can show flashing green together with
steady blue.
LED use and brightness can be changed in Uvir under **Sensor parameters**.
The same section controls the optional status buzzer. It distinguishes app
connection, disconnection, activity start, activity stop and a saved record.
One prolonged tone reports the same missing-date rejection as the five rapid
orange flashes.
All sounds run in a dedicated task so they do not delay sampling or connectivity.

For Wi-Fi, leave the phone connected to the same local network. Uvir finds only
the previously associated sensor by matching its unique ID and secret token.
For Bluetooth, pair the displayed Uvir device from Android Bluetooth Settings.

## Irradiance values

Firmware **0.5.109** adds eleven absolute per-color calibration gains using the
`CHANNEL_FACTORS` suffix of `CALIBRATION_CONFIG`. They replace module gains,
after dark subtraction/clamping and before optical-window compensation.
Settings are saved atomically in `cal_state4`; earlier calibration remains
unchanged until an explicit edit. See the [protocol](../../../docs/USB_SERIAL_PROTOCOL.md#per-channel-calibration-firmware-05109).

Firmware **0.5.108** refines dark calibration to one offset per displayed color:
three UV and eight visible/NIR components, using `COLOR_OFFSETS` and version-2
readback. The subtraction/clamp happens after the physical channels are combined,
before calibration/window factors. Existing version-1 physical-channel offsets
are preserved with their original calculation until explicitly replaced. The
64-byte offset NVS layout is retained with distinct signatures for the two modes;
there is no boot-time calibration rewrite or automatic dark measurement.

Firmware **0.5.107** adds configurable physical-channel dark offsets: three UV
and twelve visible/NIR values, initially zero, in the then-canonical uW/cm2 unit
(internal storage is W/m2 from 0.5.112; v1 transport remains uW/cm2). They are
subtracted before base factors, paired-color averaging, window compensation,
conditions and saves. Complete calibration is stored atomically in versioned
NVS state while preserving legacy settings. HELLO/status readback and standalone
USB/Wi-Fi/Bluetooth/MQTT commands expose the same configuration. See the
[offset protocol](../../../docs/USB_SERIAL_PROTOCOL.md#calibration_config-visible_factor-uv_factor)
and [final-assembly calibration](../../../hardware/calibration/README.md).
This source update does not measure or automatically set a dark calibration.

Firmware 0.5.106 corrects the calibration busy guard: a configuration command
must not reject itself because it started the activity LED. Actual acquisitions,
alerts, synchronization and diagnostic tests still prevent calibration changes.
The separate `calibration_locked` field lets clients avoid disabling every
calibration field for the command indicator alone. This correction was flashed
and checked on the connected device; common and individual window factors were
verified without changing base calibration or creating records.

Firmware 0.5.105 adds optional eleven-channel optical-window compensation, off
by default. It persists the whole calibration/window configuration and applies
correction once before conditions, averaging, alerts and storage. The existing
SD/FRAM record layout is retained with a checksummed context-boundary journal.
See [the protocol](../../../docs/USB_SERIAL_PROTOCOL.md) and
[optical-window limits](../../../docs/OPTICAL_WINDOW.md). The connected ESP32 was
updated to 0.5.105 on 2026-10-21 with its existing calibration/settings preserved
and compensation disabled. AS7343, AS7331, RTC and FRAM checks passed; microSD
was not detected before or after the update. This does not validate a protective
window or replace final assembled-device calibration; see the linked validation
record for scope and remaining checks.

Firmware 0.5.104 extends the AS7343 automatic gain range down to 0.5x, after
the assembled device saturated at the previous 16x minimum in direct sunlight.
Startup gain, integration time, sampling settings and calibration factors are
unchanged. Conversion always uses the gain of the completed read, including
when the bounded automatic-gain retry budget is exhausted. This is a dynamic
range correction, not an absolute calibration or an empirical UV correction.

Values are emitted in `uW/cm2` and explicitly marked `datasheet_estimate`. The
formula normalizes raw counts for actual gain and integration time, then uses
the AS7343 typical response measured at 155 mW/m² (15.5 µW/cm²), 1024× and
27.8 ms. The resulting values are channel-equivalent irradiance estimates,
not a traceable calibration of this physical module and not a full spectral
integration over each displayed color band.

They are suitable for end-to-end and relative-light tests. Absolute
measurements require per-device dark/scale calibration of the assembled optical
system against a calibrated source or spectroradiometer.

AS7331 UVA, UVB and UVC values use the SparkFun driver's datasheet conversion,
then the Uvir UV calibration factor. They also require comparison with a
traceable reference before being treated as absolute measurements.

Sensor drivers are isolated in `UvirVisibleSensor.h` and `UvirUvSensor.h`;
calibration coefficients remain in `Calibration.h`. This keeps acquisition
hardware separate from transport, session, alert and offline-storage logic.
