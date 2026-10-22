# Build and bring up the complete UVIR sensor

The measurement, storage, clock, external-control and communication features are
implemented. The remaining measurement-quality step is calibration of the final
optical assembly. A functional prototype is not a certified radiometer, and the
published PCB exports still require their documented electrical/mechanical review.

## 1. Gather and inspect

Use the [bill of materials](BILL_OF_MATERIALS.md),
[complete pin map](wiring/README.md) and the chosen
[schematic / PCB revision](pcb/uvir-carrier-2026-10-05/README.md).
Keep the [R1 logical reference](pcb/uvir-carrier-r1/README.md) separate from the
dated physical layouts. A label such as GY-AS7341 on an imported library symbol
does not change the installed chip: this instrument uses AS7343.

With power disconnected, verify module pin labels, polarity, sockets and USB/card
clearance. Use female carrier sockets and removable male module headers as in
the intended build. Nominal pitch is 2.54 mm, but measure both header rows and
pin 1: the reported approximately 22 mm ESP32 row spacing is not a fabrication
dimension. Do a dry fit before soldering or ordering a new board.

## 2. Power and grounding

- Use one appropriate power source at a time. Do not feed an external 5 V source
  and USB simultaneously unless the exact development board has suitable power
  isolation; clone power circuits differ. Do not route power into an ESP32 GPIO.
- Connect all grounds together. Wire the RTC, FRAM and UV 5 Click to 3.3 V.
  The GY-AS734X may use the 3.3 V rail **only after checking its onboard regulator
  and level translation**; the bare AS7343 is not a 3.3 V part.
- Power only the documented 1117C33/SN74HC125 microSD adapter from 5 V/VIN.
  Its onboard regulator supplies the card. Do not apply 5 V to a bare microSD
  card, a 3.3 V-only adapter, or any ESP32 signal.
- Size the power supply and development-board regulator for the assembled
  modules, Wi-Fi/BT activity and SD writes. Do not assume the nominal USB supply
  rating equals the available 3.3 V regulator current. Check for voltage drops,
  brownout resets or heating during radio and storage tests.
- Check for shorts between 5 V, 3.3 V and GND before first power-up. Add supply
  decoupling where required by the actual module/circuit; long breadboard wires
  are not a substitute for a reviewed PCB power layout.

## 3. Shared I2C bus

Connect the four SDA pins to GPIO 21 and the four SCL pins to GPIO 22, in parallel.
Use short wiring and a shared ground. The expected 7-bit addresses are `0x39`,
`0x74`, `0x68` and `0x50` for AS7343, AS7331, RTC and FRAM respectively.

Breakout pull-up resistors are also in parallel. Check the actual boards: do not
blindly add another pair to every module. For example, two 4.7 kohm pull-ups in
parallel are 2.35 kohm, before other modules are counted. Check both idle levels
are compatible with 3.3 V ESP32 I/O and that rise time / sink current are suitable
for the assembled bus. Never allow a module to pull SDA/SCL up to 5 V. If a line
is stuck low, power down and isolate one board at a time before changing software.

Use the [individual guides](wiring/README.md) for address jumpers, FRAM WP,
RTC battery, SD signal direction, LED polarity and the external contact.

## 4. Mechanical and optical arrangement

The selected housing for the complete circuitry is **TEKO COFFER-A/6.5**:
an ABS enclosure with a 1 mm aluminium top panel, according to the
[manufacturer](https://www.teko.it/en/products/family/TC/series/coffer-a).
Dry-fit the PCB, removable modules and sockets inside it before final fastening.
Check the required USB, microSD, button, LED, buzzer and optical openings; this
housing selection does not establish that a published PCB revision already fits.

For the directional prototype, allow one hollow tube per optical chip with a
6 mm clear bore and 25 mm clear height above its top optical window. Wall
thickness is selected for rigidity and opacity; 2 mm is an optional example,
giving a 10 mm outside diameter. Check mounts, lid clearance and LED isolation
using the [optical-head dimensions and diffuser notes](README.md#directional-optical-heads-prototype).
These are proposed prototype dimensions; verify angular response and calibrate
the final assembly before interpreting the results as absolute irradiance.

The original placement intent has ESP32 at the front bottom with USB facing
down; horizontal visible/NIR then UV boards above it; buzzer and indicators
toward the top; an accessible side button. RTC, FRAM and SD occupy the reverse,
with card access from an edge. This intent is not an assembly drawing for every
published layout: check the selected native CAD's layers and orientation.

Keep the ESP32 antenna area clear of copper, modules, battery holders and metal,
including the enclosure's aluminium panel,
as required by the exact ESP32 module design. Do not place the RTC under the
antenna merely to follow the rough arrangement. Ensure sockets on opposite
sides cannot touch through leads or spacers.

Do not put indicator LEDs in the sensors' optical field. Include the UV board's
own power LED in the isolation plan. Optical apertures/windows must suit the
bands being measured; ordinary plastic or glass is not a neutral UV window.
Changing enclosure, window, distance or LED shielding after calibration can
change the result. Maintain the normal operating LED configuration during the
final dark/reference tests, with suitable physical isolation.

If protection is wanted, use **UV-grade synthetic fused silica, polished and
uncoated**, with documented transmission across at least 240–1000 nm. Prefer
two small windows with opaque LED barriers; nominal 1 mm is a starting point for
a supported aperture, not a guaranteed enclosure fit or impact rating. A shared
sheet needs its own mechanical and light-isolation review. See
[window selection, thickness and mounting](../docs/OPTICAL_WINDOW.md#choosing-an-optional-protective-window)
for a specification-checked purchase option. Include the final window in optical
tests, and set justified window compensation only if its loss has not already
been included in the final-assembly calibration.

## 5. Firmware and first communication

Build/upload using [Getting started](../docs/GETTING_STARTED.md), ESP32 Dev Module
and the **Huge APP (3MB No OTA/1MB SPIFFS)** partition selection. The documented
October 2026 build used ESP32 core 3.3.11, SparkFun AS7343 1.0.0,
SparkFun AS7331 2.2.0, SparkFun Toolkit 1.2.0 and ArduinoMqttClient 0.1.8.
Do not erase flash as a routine update of an existing sensor.

Connect with the Android app or the [standalone USB guide](../docs/STANDALONE_SENSOR.md).
USB serial is 115200 8N1. Full USB `HELLO` responses contain private provisioning
credentials: inspect locally, never paste them into GitHub or a public log.

## 6. Acceptance checklist

| Check | How to verify | Expected result |
|---|---|---|
| Controller / firmware | `HELLO` | Correct protocol, unique hardware ID and firmware |
| Visible / NIR | Driver initialization, diagnostic, changing illumination | AS7343 present, sensible channel response; not just bus ACK |
| UV | Diagnostic and UV availability | AS7331 present at `0x74`; absence is reported, not silently called calibrated zero |
| RTC | Capture initial OSF, set `TIME`, inspect validity | Clock valid, UTC retained through a deliberate idle power-cycle test |
| FRAM | Diagnostic read/write and queue fields | FRAM available, read/write and queue available |
| microSD | Diagnostic temporary file read/write | FAT32 card mounted, correct capacity, successful round trip |
| Indicators | `LED_TEST` | Red, green, orange patterns and separate blue visibly work |
| Buzzer | `BUZZER_TEST` and volume preview | Audible patterns without distortion; no records created by output tests |
| External input | Inspect GPIO first; intentional short/triple/hold test | Contact releases high; actions follow the documented activity restrictions |
| Wireless | Provision over USB, authenticate each enabled transport | Correct device identity, stable keepalive, fallback after link loss |
| Offline recovery | Deliberately create test data, disconnect/reconnect | FRAM/SD records recovered once and only removed after durable ACK |
| Power recovery | With expendable test job and valid RTC | Original timing/counters restored; no invented blackout records |

Output tests can make sound/light. Button, job and recovery tests create real
records: use a separate test archive or clearly labelled test sessions. Do not
reset or format a device containing unsynchronized data. Diagnostic storage
tests are bounded and restore/remove only their reserved temporary test data.

If a component is absent, check power, pin labels, address and bus levels first.
Use [standalone troubleshooting](../docs/STANDALONE_SENSOR.md) for connection
failures. Keep hardware and Android error logs private until credentials and
personal data have been reviewed.

## 7. Final calibration

Perform the [calibration checklist](calibration/README.md) after mounting and
optical isolation. Covered and sunlight tests establish response and dark
behavior, not traceable absolute accuracy. Do not zero UVC solely because a
sunlight residual is small, and do not treat a nonzero residual as proof of UVC.
Record the exact assembly, source/reference, geometry, settings and uncertainty.
