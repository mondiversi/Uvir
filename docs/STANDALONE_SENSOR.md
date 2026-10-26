# Use the complete UVIR sensor without the Android app

This guide is for a PC, another controller or a custom application. It describes
the implemented firmware `0.5.119`, protocol `uvir-sensor-v1`. It does not require
the official Android interface. Build the hardware using the
[assembly guide](../hardware/ASSEMBLY.md) and [all wiring guides](../hardware/wiring/README.md).

The same commands work over USB serial, local Wi-Fi TCP, Bluetooth Classic SPP
and Internet MQTT/TLS. For command grammar, record formats and capabilities, see
the [protocol reference](USB_SERIAL_PROTOCOL.md). Read that reference before
writing a client that owns records; this guide's live example is deliberately
not an archive importer.

## 1. Safety, identity and ownership

- One host/transport owns the live app session of a physical sensor at a time.
  Disconnect the Android client before independent control. Several sensors can
  be controlled separately, with separate credentials, state and record-ID scope.
- USB is trusted physical access. Its `HELLO`/`PING` replies contain the private
  `auth_token`, Bluetooth PIN and other provisioning fields. Never publish a full
  reply, store it in ordinary logs, or commit it to source control.
- Wireless `AUTH <auth_token>` uses the exact, case-sensitive **32-character
  text token** learned over USB. Hex-encoding that token again is wrong. A factory
  reset replaces the credentials; an old association backup does not grant access.
- Treat local Wi-Fi TCP as a **trusted-LAN interface**, not encrypted HTTPS.
  Do not expose port 8733 to the Internet. MQTT uses a separate TLS connection.
- After `APP_CONNECT`/`STREAM`, send `PING` about every **4 seconds**. The active
  host lease expires after 12 seconds without commands. Receiving sample frames
  alone does not renew it. Reauthenticate/reconnect after loss; check device ID.
- `SAMPLE` and `STREAM` are live readings, not automatically saved acquisitions.
  Do not expect them to appear in Android's archive later.

## 2. USB serial quick start

Use a USB **data** cable and a terminal supporting 115200 baud, 8 data bits,
no parity, 1 stop bit, no flow control, and LF/CRLF line termination. Close other
serial monitors and the app's USB connection first. Port names differ by host
(for example `COM6` or `/dev/ttyUSB0`); select your actual port.

Opening a serial port may reset some boards because of DTR/RTS. Wait for boot,
then send:

```text
HELLO
APP_CONNECT
STREAM 500
```

Validate `protocol`, device identity and component availability. Send `PING`
periodically while reading JSON lines; send `STOP` before releasing the session.
You may also send `SAMPLE` to request the newest completed rolling result.
Immediately after startup the averaging window may not yet be ready.

Before any saved recording, set **current** UTC epoch milliseconds:

```python
import time
print("TIME", time.time_ns() // 1_000_000)
```

Send that generated command, not a historical timestamp from a document. Inspect
the RTC oscillator-stop flag before `TIME`, because successful clock setting
clears it. Neither time zones nor daylight-saving offsets are added to epoch time.

### Runnable live example

The repository includes [uvir_live.py](../tools/examples/uvir_live.py). Python 3.10+
is sufficient for TCP; USB additionally needs `python -m pip install pyserial`.
Run from the repository root:

```text
python tools/examples/uvir_live.py --serial COM6 --duration 30
python tools/examples/uvir_live.py --tcp 192.0.2.10 --device-id YOUR_DEVICE_ID --duration 30
```

Replace the documentation-only IP and ID with the sensor's actual values.
The Wi-Fi token is requested through a hidden prompt, not a command-line option.
The example validates identity/protocol, maintains the lease and prints only
selected live measurement fields. It refuses a busy sensor, never changes clock
or persistent settings, and never sends `SYNC_BEGIN`, `SYNC_ACK` or
`ACQUISITION_ACK`. It is a connectivity demonstration, not a production recorder
or provisioning tool. An OS/driver may still reset an ESP32 when opening USB.

## 3. Configure local Wi-Fi without Android

1. Connect over trusted USB and read `HELLO` privately. Save `device_id` and
   `auth_token` in protected local storage outside the repository. Check the
   current settings before modifying them.
2. Encode your existing 2.4 GHz network's SSID/password as **UTF-8 bytes in hex**,
   not Base64. SSID is 1–32 bytes; the supported WPA password is 8–63 bytes.
   Open networks are not accepted by this command.
3. Send the following, substituting the encoded values; wait for successful
   responses instead of assuming a port write means success:

```text
WIFI_CONFIG <SSID_UTF8_HEX> <PASSWORD_UTF8_HEX>
RADIO WIFI ON
WIRELESS WIFI
```

For example, generate a command locally without putting a password in shell
history; the generated line is itself a secret, so do not log or share it:

```python
from getpass import getpass
ssid = input("SSID: ").encode("utf-8")
password = getpass("Wi-Fi passphrase: ").encode("utf-8")
assert 1 <= len(ssid) <= 32 and 8 <= len(password) <= 63
print("WIFI_CONFIG", ssid.hex(), password.hex())
```

`wifi_configuration_saved:true` confirms persistence. `WIRELESS WIFI` returns
`wireless_mode:"wifi"` before changing radio mode. Inspect `wifi_ip` in a fresh
USB `HELLO` once the network joins, or use authenticated discovery below. Do not
start a competing USB stream while testing Wi-Fi ownership.

### Connect to TCP

Open a normal TCP socket to the sensor IP, port **8733**, and send `AUTH <token>`
immediately: an unauthenticated TCP client is disconnected after **5 seconds**.
The response is a `hello` without provisioning secrets. Validate protocol and
expected device ID, then send `APP_CONNECT`, optional current `TIME`, and
`STREAM 500`. Keep sending `PING` every 4 seconds and parse asynchronous JSON
frames independently of command replies. End with `STOP` and close the socket.

Wi-Fi here means both devices join an existing router/access point. It is not
Wi-Fi Direct or an ESP32-hosted access point. Guest-network isolation and a host
firewall can prevent communication even when both devices show network access.

### Find a sensor whose IP changed

Send UDP port **8732** the ASCII datagram:

```text
UVIR_DISCOVER <device_id> <nonce>
```

`nonce` must be 16 cryptographically random bytes encoded as 32 hex characters.
The JSON reply contains `type:"discovery"`, `protocol`, `device_id`, `nonce`,
`proof` and `port`. Require the expected identity/protocol and your exact nonce.
Verify `proof` against:

```python
import hashlib, hmac
message = f"DISCOVERY:{device_id}:{nonce}".encode("utf-8")
expected = hmac.new(auth_token.encode("utf-8"), message, hashlib.sha256).hexdigest()
assert hmac.compare_digest(expected, response["proof"])
```

Use the verified packet's sender IP and advertised TCP port. The token's key is
its text bytes, **not** `bytes.fromhex(auth_token)`. Discovery proves identity;
it does not replace TCP `AUTH` or encrypt subsequent traffic. A subnet broadcast
may not cross routers; a known-IP unicast or network configuration may be needed.

## 4. Bluetooth without Android

Provision/read `bluetooth_name`, six-digit `bluetooth_pin` and `auth_token` over
trusted USB. Send `RADIO BLUETOOTH ON`, then `WIRELESS BLUETOOTH`. Pair using the
OS and that PIN, then open its **Classic SPP / RFCOMM serial** service. This is
not BLE GATT; a BLE-only terminal will not work. SPP support is host-dependent.

Send `AUTH <token>`, validate the `hello` and ID, then use the same
`APP_CONNECT`, `TIME`, `STREAM`/`SAMPLE`, keepalive and ACK rules. The pairing
PIN alone does not authorize the UVIR protocol. Retain the exact token case.
If the OS exposes an SPP serial port, the live example's `--serial` USB mode
is **not** its authentication flow; use an SPP client that sends `AUTH` first.

## 5. Internet MQTT/TLS without Android

Use a broker reachable by both controller and sensor, with a trusted TLS
certificate chain and dedicated username/password/ACLs. Do not disable certificate
validation to get a test working. Set valid device time for TLS as well as records.
Both peers connect outward; no home-router port forwarding is required.

Configure over USB (all string fields below are UTF-8 hex, `-` means empty):

```text
INTERNET_CONFIG ON PRIMARY - - <BROKER_HOST_HEX> 8883 <MQTT_USER_HEX> <MQTT_PASSWORD_HEX>
WIRELESS INTERNET
```

`PRIMARY` reuses the local Wi-Fi credentials. For a separate network:

```text
INTERNET_CONFIG ON SECONDARY <SSID_HEX> <WIFI_PASSWORD_HEX> <BROKER_HOST_HEX> 8883 <MQTT_USER_HEX> <MQTT_PASSWORD_HEX>
```

Wait for `internet_configuration_saved:true`. Limits are SSID 32 bytes,
Wi-Fi password 8–63, broker host 253, MQTT username 1–128 and password 1–256.
The combined **hex-expanded command must still fit the 512-character USB line**;
these independent field limits do not guarantee that their maxima fit together.
Port must match your TLS broker (8883 is the example, not a universal requirement).
There is no `RADIO INTERNET` command; enable it through `INTERNET_CONFIG`.

For the lower-case device ID, use:

| Topic | Direction / framing |
|---|---|
| `uvir/<device_id_lowercase>/commands` | Controller publishes one plain command per MQTT message; never retained |
| `uvir/<device_id_lowercase>/events` | Controller subscribes for individual JSON response/event messages |

Subscribe to events **before** publishing `AUTH <token>`; then validate the hello,
claim `APP_CONNECT`, and send keepalives / commands as for the other transports.
Use a unique controller MQTT client ID; the sensor uses `uvir-sensor-<device_id>`.
Apply broker ACLs per device topic, not a shared unrestricted wildcard account.
Retained commands are ignored to prevent replaying old actions on reconnect.
The sensor subscribes to commands at QoS 1 but publishes events at QoS 0; broker
delivery acknowledgement is **not** `SYNC_ACK` or `ACQUISITION_ACK`.

## 6. Record autonomously and recover safely

Do not run these examples on a valuable active session. They intentionally
create activity/records and can change settings. Confirm the device ID and valid
time, adequate storage and that no other acquisition/alert activity is running.
Read back existing settings instead of blindly replacing user configuration.

### Timed acquisition, app not required

1. Send current `TIME` and confirm `time_synced:true`.
2. Ensure `autonomous_recording_enabled:true` in `HELLO`. If changing it with
   `SENSOR_CONFIG`, preserve the other fields; see the full protocol syntax.
3. Choose a positive session ID unique within this device's host-managed jobs.
4. Generate a job that starts 10 seconds from now, records every 5 minutes, and
   ends after at most 12 acquisitions:

```python
import time
start_ms = time.time_ns() // 1_000_000 + 10_000
print(f"OFFLINE_JOB 1001 {start_ms} 300 0 12 0 5 150 1 -")
```

5. Send it; verify `automatic_status`, then send `STOP` and close the host link.
   This releases live delivery, **not** the autonomous job. Keep the sensor powered.
6. Reconnect to monitor or stop with `OFFLINE_STOP`. After power failure a valid
   RTC and checkpoint preserve original deadlines/counters; missed intervals
   are skipped, not synthesized.

### External button session

With valid time, use the [button gestures](../hardware/wiring/ESP32-EXTERNAL-COMMAND.md)
or explicitly start `OFFLINE_EXTERNAL_JOB <session_id> 0 5 150 1 -`. A short press
adds the next external acquisition; a 2-second hold stops the activity. A normal
automatic or alert session blocks extra short-button acquisitions.

### Alerts

Configure only the metrics you need. Example **for testing, not an exposure limit**:

```text
ALERTS_CLEAR
ALERT_RULE UVA ABOVE 10
ALERT_CONFIG ON 60 2001 SAVE 0 3600 0
STOP
```

This checks the configured rule with a 60-second repeat interval, for one hour
after start. It uses canonical protocol units, not the app's chosen display
unit. Use `NO_SAVE` and session ID `0` for indication-only monitoring. Finish a
rule-list update with `ALERT_CONFIG` and check every response; it is not a
power-failure-atomic configuration transaction. Stop monitoring with
`ALERT_CONFIG OFF 60 0 SAVE 0 0 0`, not just `STOP`.

### Recover stored data

FRAM holds the first pending records; the microSD filesystem holds overflow.
`SYNC_BEGIN` covers **both**, not only the card. Consume each `offline_record`,
commit/deduplicate using `(device_id, record_id)`, then send `SYNC_ACK <record_id>`.
Repeat until `sync_complete`. Handle `sync_failed` without deleting or ACKing
unreadable data. An acknowledged record is removed and will not be offered later
to Android: decide who owns the archive before importing.

For live automatic/external acquisitions, durable commit precedes
`ACQUISITION_ACK <record_id>`. Live alerts use `alert_event` without that ACK:
store on the controller if `recorded:true`; discard as archive data if false.
Do not claim live alert delivery is replay-guaranteed like the ACK-driven queue.
If Android should own later recovery, release live ownership and let the sensor
record offline. The example live reader never acknowledges or imports records.

## 7. Diagnostics and troubleshooting

| Symptom | Check first |
|---|---|
| No serial data | Data cable, correct port, CP210x driver, 115200 8N1, another process holding the port |
| Wi-Fi cannot join | Correct UTF-8 hex credentials, supported network, signal/power, `wifi_connected` and IP |
| IP reachable, TCP fails | TCP 8733, guest/AP isolation, firewall, active configured radio |
| Connection disappears after seconds | AUTH within 5 s; PING every 4 s after ownership; another host takeover |
| Radio changes unexpectedly | 20 s fallback among configured enabled modes until a host authenticates |
| Authentication fails | Exact token case, correct device ID, reset invalidated old token; reprovision over USB |
| Bluetooth terminal cannot see service | Classic SPP support, pairing PIN, not a BLE-only client |
| MQTT connects but no data | Matching device topics, subscribe first, non-retained commands, protocol AUTH, broker ACLs |
| MQTT TLS fails | Device time, hostname/certificate chain, port, Wi-Fi and credentials; never bypass TLS verification |
| No durable records | Valid time, autonomous recording enabled, activity accepted, FRAM/SD available, no overload/full queue |
| UV absent / bad values | Correct AS7331 wiring/address and driver state; check saturation and calibration |

`DIAGNOSTIC <32_hex_characters>` returns a correlated hardware/storage report.
For example, generate an ID with `uuid.uuid4().hex` in Python. `LED_TEST` and
`BUZZER_TEST` require an active host, idle activity state and enabled outputs.
`DEBUG_PERFORMANCE HAPPY_BIRTHDAY` (also `INDIANA_JONES`, `JURASSIC_PARK`,
`STAR_WARS`) plays a diagnostic theme while connected and idle;
`DEBUG_PERFORMANCE_STOP` cancels it. Keep the lease alive during long tests.

`STOP` releases live ownership; `OFFLINE_STOP` stops an acquisition job;
`ALERT_CONFIG OFF ...` stops alerts; `POWER_OFF` deep-sleeps an idle device.
`WIRELESS OFF` disables the selected radio/fallback, not just one stream.
`FACTORY_RESET` is destructive: credentials, settings, bonds and pending records
are lost. None of these are interchangeable troubleshooting shortcuts.

## Verification scope

The guides and example were checked against the current source and automated
host tests. The documentation update does not flash a device, alter calibration,
format cards or perform new physical radio/optical measurements. Check the
[recorded device/build validation](DEVICE_TESTING.md) and complete the final
[optical calibration](../hardware/calibration/README.md) on the assembled instrument.
