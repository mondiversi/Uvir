# Read Uvir without the Android app

This guide covers the current **FT232H + AS7331 + AS7343** measuring head.
It is the standalone USB reference for `main`, checked against the current
[Android chip drivers](../../app/src/main/java/me/mondiversi/uvir/usb/SpectralChips.kt).
The [ESP32 serial commands](../USB_SERIAL_PROTOCOL.md) belong to the preserved
legacy hardware and do not work with this head.

## What USB exposes

The FT232H is a USB-to-I²C bridge, not a computer running Uvir firmware. It accepts
binary MPSSE operations; it does not accept `HELLO`, `SAMPLE`, `STREAM`, JSON,
or text typed into a COM-port terminal. The optical chips return register bytes.
An external host initializes them, starts conversions, checks status and reads
the results. There is no unsolicited stream: the host requests each reading.

| Operation | Where it happens |
|---|---|
| Detect FT232H; read chip IDs and raw spectral counts | USB/I²C, usable without Android |
| Set integration time and gain; start/stop conversion | Volatile chip registers |
| Check conversion readiness, saturation and missing modules | Host reads and interprets chip status |
| Auto-ranging, averaging, offsets, factors, optical-window correction | Host program; Uvir implements these on the phone |
| Irradiance/color sums, biological estimates, thresholds, sessions and files | Host program; not commands stored in the head |
| Read the phone's archive or its saved calibration | Not available through the FT232H |

Use one USB host/controller at a time. Connect the head directly to the computer
after disconnecting it from the phone. Keep the same
[four-net wiring and AD1–AD2 bridge](WIRING.md); leave other I²C masters disconnected.
No firmware upload is needed. The example below changes only volatile conversion
registers, not the FT232H EEPROM or the phone's settings, calibration or archive.

## Terminal example

[uvir_ft232h.py](../../tools/examples/uvir_ft232h.py) is a small independent
Python 3 reader using PyFtdi. Run the following commands from the repository root.
Install a supported libusb backend and device permissions first, following the
[PyFtdi installation instructions](https://eblot.github.io/pyftdi/installation.html).
Linux/macOS are the documented starting point. On Windows, PyFtdi needs a suitable
USB backend and is not officially tested by its maintainer; the ordinary FTDI
COM-port driver alone is insufficient. This script never installs/replaces drivers.
An implementation using FTDI's D2XX/MPSSE API is another option; it must implement
the same transactions below rather than send serial text.

```sh
python -m pip install pyftdi
python tools/examples/uvir_ft232h.py --help
python tools/examples/uvir_ft232h.py probe
python tools/examples/uvir_ft232h.py read
python tools/examples/uvir_ft232h.py read --samples 10 --interval 1
python tools/examples/uvir_ft232h.py read --samples 0 --interval 1 > readings.jsonl
```

`probe` initializes the selected components and reports each ID independently;
it is not a passive USB-descriptor query. `read` returns one reading by default;
`--samples 0` runs until Ctrl+C. The interval is a minimum start-to-start spacing:
conversions and USB transfers can take longer. Missed intervals are not backfilled.
Stop with Ctrl+C; the reader attempts to stop conversions and releases USB.

| Option | Meaning |
|---|---|
| `--module all`, `uv`, `vis` | Both modules (default), UV only, or visible/NIR only |
| `--uv-gain 64` | Fixed UV gain; powers of two from 1 to 2048; default 64 |
| `--visible-gain-index 0` | Fixed VIS/NIR gain index 0–11; default 0 = 0.5× |
| `--url ftdi://ftdi:232h/1` | Default FT232H interface; select an explicit device if more than one is connected |

The example deliberately uses **fixed gain and raw counts**, not Uvir's complete
auto-ranging/calibration pipeline. Reduce gain for saturation; increase it for
weak signals. A low fixed gain can produce zero counts without proving absence
of radiation. The VIS/NIR gain-index values are:
`0.5, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024`.
For example, read only UV at minimum gain:

```sh
python tools/examples/uvir_ft232h.py read --module uv --uv-gain 1
```

PyFtdi supports serial-number and USB bus/address selectors in its
[device URL syntax](https://eblot.github.io/pyftdi/urlscheme.html). A serial number
is not guaranteed on every FT232H board. The default URL must not be treated as a
unique hardware ID.

## Returned data

The script writes one JSON object per line to standard output, errors opening
USB to standard error, and never emits raw-count values as W/m². This JSON is
**created by the Python program**, not a protocol emitted by the FT232H.
A synthetic UV-only example is:

```json
{"type":"sample","timestamp_utc":"2026-10-31T12:00:00+00:00","unit":"raw_counts","uv":{"status":"ok","valid":true,"status_word":"0x0803","gain":64,"integration_ms":64,"counts":{"UVA":123,"UVB":456,"UVC":789}}}
```

- UV returns separate UVA, UVB and UVC counts.
- VIS/NIR returns the 18 `adc_counts`, the coherent `astatus`, actual gain, and
  named physical channels `F1, F2, FZ, F3, F4, F5, FY, FXL, F6, F7, F8, NIR`.
  These are individual filter responses, not continuous color-band integrals.
- `status: saturation`, `valid: false` retains clipped counts for inspection.
  It never treats them as a usable irradiance measurement. An error/timeout
  returns `status: error`, `valid: false`, not invented zero measurements.
- One failed module does not remove the other's result. Exit status is 0 for a
  successful probe or all-valid finite run, 1 for a USB/module failure or any
  saturation, 2 for a missing Python dependency/invalid arguments, and 130 for Ctrl+C.

The example has host-only tests for byte order, coherent snapshots, old-gain
rejection, saturation, timeouts and independent module failures. **The Python
reader has not been validated on the physical head.** It does not inherit Android
hardware validation simply because it follows the same register configuration.

## I²C send/receive reference

All addresses below are **7-bit**; use `0x74` and `0x39` in I²C APIs. `W(r, …)`
means START + address/write + register + data + STOP. `R(r, n)` writes the register
index, issues a **repeated START**, then reads `n` bytes and ends with NACK + STOP.
The two-byte numeric results are little-endian. PyFtdi's
[`exchange()`](https://eblot.github.io/pyftdi/api/i2c.html) performs this combined
transaction. Do not split index and read into separate STOP-terminated calls.

### AS7331, address `0x74`

This is the sequence implemented by Uvir, with 64 ms nominal integration:

| Request | Purpose / received bytes |
|---|---|
| `W(0x00, 0x42)`, wait 2 ms | Enter configuration/power-down state |
| `R(0x02, 1)` **in configuration state** | ID; Uvir accepts `(ID & 0xF0) == 0x20` |
| `W(0x07, 0x00)`; `W(0x08, 0x40)`; `W(0x0B, 0x01)` | Divider off, one-shot clock configuration, index reset on STOP |
| `W(0x06, (gain_code << 4) \| 6)` | `gain_code = 11 - log2(gain)`; at gain 64, write `0x56` |
| `W(0x00, 0x03)`; `W(0x00, 0x83)` | Enter measurement state and start conversion |
| Wait 66 ms, then `R(0x00, 2)` | OSR byte followed by status byte; poll NOTREADY up to five further times, 2 ms apart |
| `R(0x02, 6)` **in measurement state** | UVA low/high, UVB low/high, UVC low/high |
| `W(0x00, 0x42)` | Return to configuration/power-down; also done before the next shot |

On the combined 16-bit OSR/status word, the reader rejects NOTREADY (`0x0400`),
missing new data (`0x0800` clear), or conversion overflow (`0x8000`). It marks
`0x6000` or any count ≥65534 as saturation. The ID and measurement banks reuse
addresses: reading `0x02` in the wrong state does not identify the chip.

### AS7343, address `0x39`

This sequence uses the same three-cycle snapshot as Uvir:

| Request | Purpose / received bytes |
|---|---|
| Set bit `0x10` in `0xBF`, `R(0x5A, 1)`, clear that bit | ID bank; expected ID `0x81`; restore normal bank |
| `W(0x80, 1)`, wait 3 ms | Power on |
| Clear bit `0x80` in `0xCD`; set bits `0x60` in `0xD6` | Illumination off; automatic 18-channel readout |
| `W(0x81, 9)`; `W(0xD4, 0xE7, 0x03)` | ATIME 9, ASTEP 999; 27.8 ms per cycle |
| Set `0xC6` low five bits to gain index; `W(0x80, 3)` | Configure gain, enable spectral conversion |
| Wait 120 ms, poll `R(0x90, 1)` | Wait for AVALID bit `0x40`; at most 40 checks, 10 ms apart |
| `R(0x94, 37)` | ASTATUS followed by all 18 low/high ADC pairs |
| `W(0x80, 0)` | Stop conversion after the snapshot or a timeout |

Set up each subsequent shot with power-on + 3 ms wait before gain/enable. Read
ASTATUS and all data in **one consecutive transaction**, and accept only a
snapshot whose low four ASTATUS bits match the requested gain index. AVALID can
refer to an earlier conversion. ASTATUS bit `0x80` or any of the 18 ADCs ≥10000
marks saturation with this integration setting, including overload of clear
channels. The threshold is not a universal irradiance ceiling. The script uses
the same conservative checks as the Android driver.

## If implementing the USB layer directly

Prefer a maintained MPSSE library. The source-controlled low-level references are
[AndroidFt232h.kt](../../app/src/main/java/me/mondiversi/uvir/usb/AndroidFt232h.kt)
and [Ft232hMpsse.kt](../../app/src/main/java/me/mondiversi/uvir/usb/Ft232hMpsse.kt).
USB VID/PID is normally `0403:6014`; FT232R/FT232RL cannot replace FT232H.
The app resets bit mode, purges RX/TX, disables event/error-character substitution,
sets 2 ms latency and enables MPSSE with AD0/AD1 outputs, AD2 input.

MPSSE synchronization sends hex `AA 87` and expects `FA AA`. The app's subsequent
setup bytes are `8A 97 8C 85 86 C7 00 9E 03 00 80 03 03`: three-phase 100 kHz,
drive-only-zero SDA/SCL, external pull-ups. `81 87` reads pins; bits `0x05` must
both be high while idle. These are **binary bytes**, not ASCII hex to a terminal.
START/STOP, ACK and read blocks are generated by the bus layer. Clock stretching
is disabled for the documented wiring. If handling USB bulk data yourself,
remove the two FTDI status bytes from **every USB packet**, not only once per
transfer, and reject line errors and incomplete/late responses.

## Reproducing Uvir's displayed values

Counts depend on gain and integration time. Do not sum raw counts from different
filters or relabel them W/m². Start from the current
[chip conversion code](../../app/src/main/java/me/mondiversi/uvir/usb/SpectralChips.kt),
including its physical-channel mapping and provisional VIS/NIR response factors.
Then use the [phone processing rules](ARCHITECTURE.md):

```text
max(0, mean(raw W/m²) - channel offset W/m²)
    × channel correction factor × optical-window master × optical channel multiplier
```

The reader intentionally does not import phone calibration, average multiple
frames, auto-range, compute display percentages/biological effects, run alerts,
or create Uvir database records. Those can be implemented by another host, but
are not additional commands available inside the FT232H. Absolute irradiance
and the final optical assembly still require calibration.

## References

- [FTDI MPSSE command processor, AN_108](https://ftdichip.com/Support/Documents/AppNotes/AN_108_Command_Processor_for_MPSSE_and_MCU_Host_Bus_Emulation_Modes.pdf)
- [AS7331 manufacturer datasheet hosted by MIKROE](https://download.mikroe.com/documents/datasheets/AS7331_datasheet.pdf)
- [AS7343 manufacturer datasheet](https://look.ams-osram.com/m/5f2d27fff9a874d2/original/AS7343-14-Channel-Multi-Spectral-Sensor.pdf)
- [PyFtdi I²C API](https://eblot.github.io/pyftdi/api/i2c.html)

For the preserved ESP32 hardware, use the
[legacy standalone guide](../STANDALONE_SENSOR.md) instead.
