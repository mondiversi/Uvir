# USB measuring-head wiring — preliminary

This is the logical connection plan for the ordered CJMCU FT232H board, not a
verified photograph of its header. Confirm the actual board markings and its
power circuit when it arrives. Leave the old ESP32 disconnected from the bus.

| FT232H signal | UV 5 Click | GY-AS734X / AS7343 breakout |
|---|---|---|
| AD0 / D0 (SCL) | SCL | SCL |
| AD1 / D1 **joined to AD2 / D2** (SDA) | SDA | SDA |
| Verified regulated 3.3 V rail | 3V3 | 3V3/VIN **only after confirming breakout input rating** |
| GND | GND | GND |

AD1 is the FT232H data output and AD2 its input: both must reach the same SDA
wire. There is one shared bus, with four common nets branching to both modules.
The UV board's SYN, RDY and Click-ID connection are not used. Do not wire a 5 V
pin to either sensor's logic. Do not join another controller's active I²C pins.

Connect the phone with a **USB-C male to USB-A female OTG data adapter** if the
chosen FT232H board has a USB-A plug. A charging-only cable will not work.

## Checks before applying power

1. Verify that it is FT232H (normally USB VID 0403, PID 6014), not FT232R/FT232RL.
2. Check the board's 3.3 V regulator and current budget; specifications for the
   Adafruit FT232H board do not certify an unrelated CJMCU board.
3. Confirm the GY-AS734X breakout accepts 3.3 V supply and has appropriate I/O
   level translation. The **bare AS7343 requires 1.8 V**, unlike this assumed
   breakout-level connection. If that circuitry is absent, use proper regulation
   and bidirectional I²C level translation instead of this direct hookup.
4. Check SDA/SCL idle levels, pull-ups and all common grounds. UV 5 Click already
   includes 2.2 kΩ pull-ups to 3.3 V; account for any parallel pull-ups on the GY
   board. Keep wires short. Initial bus clock is 100 kHz.
5. FT232H powers up in its EEPROM/default mode before Android switches it to
   MPSSE. Check that its startup pin states cannot contend with the sensor bus.

The driver uses FT232H drive-only-zero mode; SDA/SCL are released high through
pull-ups, not driven high by push-pull outputs. Clock stretching is not supported
by this initial driver; verify both modules work with the chosen timing.

## Identity

FT232H USB serial numbers depend on its optional programmed EEPROM. An adapter
without a usable serial number does not acquire a globally unique identity from
its model or I²C addresses. The app uses a temporary attachment identity and
does not reuse another attachment's calibration. A valid serial permits a
stable calibration profile. No ESP32 hardware ID is fabricated.

## Primary references

- [FTDI FT232H datasheet](https://ftdichip.com/wp-content/uploads/2024/09/DS_FT232H.pdf)
- [FTDI MPSSE commands, AN_108](https://ftdichip.com/Support/Documents/AppNotes/AN_108_Command_Processor_for_MPSSE_and_MCU_Host_Bus_Emulation_Modes.pdf)
- [FTDI I²C example, AN_255](https://ftdichip.com/wp-content/uploads/2020/08/AN_255_USB-to-I2C-Example-using-the-FT232H-and-FT201X-devices-1.pdf)
- [MIKROE UV 5 Click schematic](https://download.mikroe.com/documents/add-on-boards/click/uv-5-click/UV_5_click_v100_Schematic.pdf)
- [ams OSRAM AS7343 datasheet](https://look.ams-osram.com/m/5f2d27fff9a874d2/original/AS7343-14-Channel-Multi-Spectral-Sensor.pdf)
