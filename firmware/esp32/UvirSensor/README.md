# Uvir ESP32 sensor firmware

This sketch is the first hardware bridge between Uvir for Android and an
ESP32 Dev Module fitted with a GY-AS7343 spectral sensor and, optionally, an
AS7331 UV sensor. It streams visible, far-red, NIR, UVA, UVB and UVC bands over
USB serial, the local Wi-Fi network, Bluetooth Classic SPP, or an encrypted
Internet relay. Firmware keeps
working with UV values unavailable when the optional AS7331 is not installed.

## Current wiring

| GY-AS7343 | ESP32 Dev Module |
|---|---|
| SDA | GPIO 21 |
| SCL | GPIO 22 |
| GND | GND |
| VIN/VCC | See the voltage warning below |
| I / INT | Not connected; optional active-low interrupt output |
| G / GPIO | Not connected; optional trigger/synchronization GPIO |

Optional SparkFun AS7331 / SEN-23517 on the same I2C bus:

| AS7331 | ESP32 Dev Module |
|---|---|
| SDA | GPIO 21 (shared with AS7343) |
| SCL | GPIO 22 (shared with AS7343) |
| GND | GND |
| 3V3 | 3.3 V |
| INT | Not connected |
| SYN | Not connected |

The sensors do not conflict: AS7343 uses address `0x39` and AS7331 uses
`0x74`. The AS7331 runs in command/one-shot mode and therefore does not need
its `INT` or `SYN` pins for Uvir.

Optional common-cathode RGB connection LED:

| RGB LED | ESP32 Dev Module |
|---|---|
| Common cathode | GND |
| Red anode | GPIO 25 through its own 330 Ω resistor |
| Green anode | GPIO 26 through its own 330 Ω resistor |

The RGB blue anode is not connected. GPIO 27 instead drives a separate blue
operation LED:

| Blue operation LED | ESP32 Dev Module |
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
The firmware generates the tones by PWM and starts at 10% volume.

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
otherwise the blue LED flashes rapidly five times and the buzzer emits one
prolonged beep. Never apply 5 V directly to GPIO 33: use an optocoupler or a
properly designed level-shifting/protection stage for an industrial 5 V command.

The bare AS7343 operates at 1.8 V. Only connect the module to the ESP32 3.3 V
pin if the exact GY-AS7343 board includes a regulator and I2C level shifting.
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
   Library**, and **SparkFun Toolkit** from the Library Manager if Arduino
   reports a missing library.
6. Upload the sketch.

The equivalent command-line build is:

```text
arduino-cli compile --fqbn esp32:esp32:esp32:PartitionScheme=huge_app firmware/esp32/UvirSensor
```

The repository copy is the working source: open and save this file directly
from this folder, so later changes stay under version control.

## USB protocol

For the standalone integration guide, including the recommended handshake,
measurement schema, automatic jobs and acknowledgement-safe offline recovery,
see [USB serial integration](../../../docs/USB_SERIAL_PROTOCOL.md).

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
- `DIAGNOSTIC <32-character hexadecimal request ID>` (firmware 0.5.69+):
  return one correlated, read-only `diagnostic` frame containing identity,
  firmware, RAM/flash/offline capacity, sensor availability, and session state.
  No sampling, session, LED/buzzer, configuration, or synchronization command
  is executed. Credentials are never included. Android sends six spaced probes
  over the selected transport, measures round-trip latency at receipt, and
  offers the same localized report in a scrollable dialog and UTF-8 TXT export.
- `APP_CONNECT`: confirm that the authenticated transport is the source
  currently selected by Uvir, before synchronization and live streaming;
- `STREAM 150`: request one sample about every 150 ms;
- `SAMPLE`: acquire one sample;
- `SAMPLING_CONFIG 5 150 1`: keep five raw samples in ESP32 RAM, one every
  150 ms, discard the per-band minimum and maximum, and emit only the final
  averaged result;
- `LED_EVENT ACQUISITION`: shows three flashes on the separate blue operation
  LED after Android confirms a manual acquisition was saved. Automatic
  acquisitions are timed and signalled directly by the ESP32;
- `LED_EVENT ALERT`: compatibility/diagnostic command that shows the same
  three blue flashes used for a saved manual acquisition. During normal live
  use the ESP32 evaluates the configured rules on the same final averaged
  result sent to Android and starts the green signal locally, without waiting
  for a return command from the phone;
- `LED_TEST`: when connected and idle, reproduces every status signal in legend
  order, then automatically restores the current real sensor state;
- `BUZZER_TEST`: when connected and idle, reproduces the connection,
  disconnection, activity-start, activity-stop and saved-record sounds, then
  returns to silence;
- `DEBUG_PERFORMANCE HAPPY_BIRTHDAY`, `DEBUG_PERFORMANCE INDIANA_JONES`,
  `DEBUG_PERFORMANCE JURASSIC_PARK`, or `DEBUG_PERFORMANCE STAR_WARS`: while
  connected and idle, starts the selected
  diagnostic theme locally on the ESP32. Notes and LED timing are stored in
  firmware, while the saved buzzer/LED switches, volume and brightness remain
  authoritative;
- `DEBUG_PERFORMANCE_STOP`: immediately cancels the current diagnostic theme
  and restores the real connection/operation LED state;
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
- `INTERNET_CONFIG ON|OFF PRIMARY|SECONDARY <SSID_HEX_OR_-> <PASSWORD_HEX_OR_-> <RELAY_HOST_HEX_OR_-> <PORT>`:
  configure the encrypted remote relay. `PRIMARY` reuses the normal Wi-Fi
  credentials; `SECONDARY` stores a separate network, useful with a portable
  router. Both the sensor and app connect outward, so no sensor-router port is
  exposed.
- `WIFI_DISCONNECT`: disable and persistently turn off the Wi-Fi radio. This
  command is accepted only through an already authenticated Wi-Fi connection;
  Wi-Fi must then be re-enabled through USB.

The ESP32 stops sending frames automatically if it does not hear from the
Android host for 12 seconds. Measurement continues at the configured cadence
even without an active stream, keeping the latest averaged result ready in
RAM for autonomous and external commands. The sensor LED stays off and both
spectral devices return to power-down between reads. The default/recommended
interval is 150 ms; the safe range is 150–5000 ms. A capture that takes longer
still finishes before another sample is taken. Existing saved intervals are
preserved; the default applies to an unconfigured or factory-reset sensor.

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

Select Wi-Fi or Bluetooth from the source icon while USB is still connected.
After this first secure provisioning, Uvir can switch between the two radios
through whichever authenticated wireless connection is currently active.
Selecting USB as the measurement source does not turn off the last wireless
radio, so disconnecting the data cable never strands the sensor offline. The
spectral devices remain powered down between individual conversions; use the
explicit Wi-Fi disconnect control when the radio itself must be disabled.
The ESP32 persists the chosen radio and keeps only that radio enabled. At
startup it tries the last successfully authenticated mode first. If Uvir does
not authenticate within 20 seconds, the sensor switches to the other configured
wireless transport and continues alternating until the app is found. Once Uvir
authenticates, that mode is saved and the fallback stops, so both radios are
never left active together. A transport disabled in Uvir settings is excluded
from this fallback; disabling both leaves the sensor in USB-only mode:

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

When automatic acquisition or value alerts are active and the authenticated
phone connection disappears, the sensor continues from the last time and job
configuration received from Uvir. Completed acquisitions, alert events and
sensor errors are queued in LittleFS. Raw intermediate samples are never
written to flash. At reconnection the queue is transferred to Android, every
record is acknowledged individually, and the ESP32 clears the queue only
after the complete transfer succeeds. The Android database deduplicates
repeated transfers by sensor ID and sensor record ID.

Autonomous offline recording can be disabled from **Sensor parameters**. This
pauses automatic acquisitions and alert evaluation only while the app is
disconnected; connected operation is unchanged and no final record is written
to flash unnecessarily. The same section offers an optional automatic shutdown,
disabled by default. Its idle timer lives in RAM and starts only when the app is
disconnected and no acquisition, alert monitoring, pending result or
synchronization is active. Waking from this low-power shutdown requires a
physical reset or a power cycle.

The active automatic-session state and its progress remain exclusively in RAM.
They survive USB/Wi-Fi/Bluetooth connection changes but are deliberately not
resumed after an ESP32 restart or power loss. Persistent writes are reserved
for user-confirmed settings and final records that really need the offline
queue; no periodic automatic-session checkpoint is written.

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

The RGB LED reports connection and pending synchronization: steady red means
powered but not connected to the app, flashing red means disconnected with
acquisitions, alerts, or errors waiting in sensor memory, flashing yellow means
that an app connection is in progress, steady green means connected, and
flashing green means connected while stored records are being synchronized.
The separate blue LED is reserved for operations: steady blue means an
automatic session or value-alert monitor is active, while three blue flashes
report a newly recorded acquisition or alert. Five much faster blue flashes
report that an explicit acquisition was rejected because date and time were not
available. The connection, synchronization, and operation indicators work
independently.
LED use and brightness can be changed in Uvir under **Sensor parameters**.
The same section controls the optional status buzzer. It distinguishes app
connection, disconnection, activity start, activity stop and a saved record.
One prolonged tone reports the same missing-date rejection as the five rapid
blue flashes.
All sounds run in a dedicated task so they do not delay sampling or connectivity.

For Wi-Fi, leave the phone connected to the same local network. Uvir finds only
the previously associated sensor by matching its unique ID and secret token.
For Bluetooth, pair the displayed Uvir device from Android Bluetooth Settings.

## Irradiance values

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
