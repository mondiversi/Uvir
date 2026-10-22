# UVIR bill of materials

One complete sensor uses the following parts. Generic boards are identified by
their fitted chip and electrical design rather than an invented manufacturer.
Dimensions are approximate build measurements, **not footprint specifications**.

| Qty | Part | Identification / requirement | Approximate board size |
|---:|---|---|---|
| 1 | ESP32 development board | Classic ESP-WROOM-32, 38-pin reference; prototype ESP32-D0WD-V3, CP210x USB bridge | 52 x 25 mm |
| 1 | Visible / NIR breakout | GY-AS734X marking, **AS7343** chip; verify regulator and I/O translation | 22 x 13 mm |
| 1 | UV breakout | **MIKROE UV 5 Click, MIKROE-5535, AS7331** | 28.6 x 25.4 mm |
| 1 | RTC breakout | DS3231; generic module described as HW-084 in the prototype | 37 x 22 mm |
| 1 | RTC backup cell | Chemistry and voltage compatible with the specific module, including any charging circuit | Match holder |
| 1 | FRAM breakout | MB85RC256V, 256 Kbit = 32 KiB, default I2C `0x50` | 20 x 15 mm |
| 1 | SPI microSD adapter | Tested generic 6-pin board with **1117C33 regulator + SN74HC125** | 43 x 24 mm |
| 1 | microSD card | FAT32; a 4 GB card is sufficient for the prototype | Standard microSD |
| 1 | RGB LED | Common cathode; only red and green are connected | Verify actual lead order |
| 1 | Blue LED | Separate pending-synchronization indicator | Verify polarity |
| 3 | LED resistors | 330 ohm, one for each used LED anode | Through-hole or matching PCB footprint |
| 1 | Buzzer | Low-current passive piezo compatible with GPIO drive; prototype symbol BUZZER-12X9 | Roughly 12 x 9 mm body; measure leads |
| 1 | Normally-open pushbutton | Four-leg tactile type; use opposite contact groups | Measure chosen part |
| As needed | Female sockets / matching male headers | Removable breakout modules; nominal 2.54 mm pitch, verify row spacing | Match physical modules |
| As needed | Carrier PCB or prototype board, insulated wire, spacers | See published schematics and physical-layout review | Match selected revision |
| 1 | USB data cable / suitable power source | Must support ESP32, radios and card-write current peaks | Match USB connector |
| Optional | USB OTG data adapter | Initial Android association, if required by the phone | Match phone |
| Optional | Two-pin external button connector | For example JST-XH; carries contact and ground only | Match PCB |
| 1 | Enclosure | **TEKO COFFER-A/6.5**, intended housing for the complete circuitry; [manufacturer](https://www.teko.it/en/products/family/TC/series/coffer-a) | Nominal 125 x 70 x 35 mm (L x W x H), not usable internal dimensions |
| As needed | Opaque optical barriers | Separate sensor apertures from all indicator LEDs inside the selected enclosure | Final optical assembly |
| 2, for the directional prototype | Hollow optical tubes and holders | One per AS7331/AS7343 chip; opaque in the measured bands with low-reflectance internal surfaces; [design and mounting](README.md#directional-optical-heads-prototype) | Clear bore 6 mm; clear height 25 mm above the chip; optional 2 mm wall gives 10 mm outside diameter |
| To be selected | AS7343 optical diffuser | Achromatic diffuser suited to the final optical head; characterize placement and transmission before calibration | Not supplied by a hollow tube; material, thickness and mount remain to be validated |

The RGB LED's unused blue anode needs no resistor because it is not connected.
The separate blue LED **does** need its own resistor. If a different buzzer needs
a transistor/driver, include its supporting parts; dimensions alone do not
establish electrical compatibility.

No MIKROE mainboard or mikroBUS controller is required: wire the UV board's
3V3/GND/SDA/SCL directly as shown in its [guide](wiring/ESP32-UV5-AS7331.md).
The ESP32 provides USB, Wi-Fi and Bluetooth; MQTT needs a network and TLS broker,
not an additional radio board.

See [complete wiring](wiring/README.md), [assembly](ASSEMBLY.md) and
[PCB exports](pcb/uvir-carrier-2026-10-05/README.md). Where a generic board's
schematic, pin order or exact footprint is unknown, verify the physical part
before substituting it. An AS7341 breakout is not the documented AS7343 module.
