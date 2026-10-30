# Complete UVIR wiring

> **Legacy ESP32 architecture.** This guide is preserved for the
> [legacy-esp32 branch](https://github.com/mondiversi/Uvir/tree/legacy-esp32).
> The current Uvir architecture uses the [FT232H wiring guide](https://github.com/mondiversi/Uvir/blob/main/docs/usb/WIRING.md)
> and does not run this firmware, relay or autonomous storage system.


This is the wiring index for the complete instrument, not just the visible-light
sensor. The functional build includes both optical boards, RTC, FRAM, microSD,
indicators, buzzer and external input. Final optical calibration is still required.

Read the [parts list](../BILL_OF_MATERIALS.md) and [assembly guide](../ASSEMBLY.md)
first. GPIO numbers below are **logical GPIOs**, not header positions. The source
of truth is [UvirHardwareConfig.h](../../firmware/esp32/UvirSensor/UvirHardwareConfig.h).
GPIO labels do not change on the back of a PCB; the physical view is mirrored,
so verify pin 1 and socket orientation before soldering.

## All connections at a glance

| ESP32 connection | Destination | Dedicated instructions |
|---|---|---|
| ESP-WROOM-32 / USB / power | Controller board and used physical header positions | [ESP32 controller](ESP32-CONTROLLER.md) |
| GPIO 21 / SDA | SDA on AS7343, AS7331, DS3231 and FRAM, in parallel | [Shared bus and power](../ASSEMBLY.md) |
| GPIO 22 / SCL | SCL on the same four modules, in parallel | [Shared bus and power](../ASSEMBLY.md) |
| 3V3, GND | Compatible GY-AS734X / AS7343 breakout | [Visible / NIR](ESP32-AS7343.md) |
| 3V3, both GND pins | MIKROE UV 5 Click / AS7331 | [UVA / UVB / UVC](ESP32-UV5-AS7331.md) |
| 3V3, GND | DS3231 RTC breakout | [Date/time and backup cell](ESP32-DS3231.md) |
| 3V3, GND | MB85RC256V FRAM breakout | [FRAM, address and write protection](ESP32-FRAM-MB85RC256V.md) |
| GPIO 13 | microSD adapter CS | [microSD / SPI](ESP32-MICROSD-SPI.md) |
| GPIO 18 | microSD adapter SCK | [microSD / SPI](ESP32-MICROSD-SPI.md) |
| GPIO 19 | microSD adapter MISO | [microSD / SPI](ESP32-MICROSD-SPI.md) |
| GPIO 23 | microSD adapter MOSI | [microSD / SPI](ESP32-MICROSD-SPI.md) |
| 5V/VIN, GND | Tested 1117C33 + SN74HC125 microSD adapter only | [microSD power warning](ESP32-MICROSD-SPI.md) |
| GPIO 25 | RGB red anode through its own 330 ohm resistor | [Status LEDs](ESP32-STATUS-LEDS.md) |
| GPIO 26 | RGB green anode through its own 330 ohm resistor | [Status LEDs](ESP32-STATUS-LEDS.md) |
| GND | RGB common cathode; RGB blue anode left unconnected | [Status LEDs](ESP32-STATUS-LEDS.md) |
| GPIO 27 | Separate blue LED anode through its own 330 ohm resistor | [Status LEDs](ESP32-STATUS-LEDS.md) |
| GND | Separate blue LED cathode | [Status LEDs](ESP32-STATUS-LEDS.md) |
| GPIO 32, GND | Low-current passive piezo +, - respectively | [Buzzer and driver limitations](ESP32-BUZZER.md) |
| GPIO 33, GND | Normally-open external command contact | [Button / external input](ESP32-EXTERNAL-COMMAND.md) |
| USB | Power, firmware upload, initial provisioning, serial control | [Standalone operation](../../docs/STANDALONE_SENSOR.md) |

## Shared I2C addresses

| Module | 7-bit address | Notes |
|---|---|---|
| AS7343 | `0x39` | GY-AS734X board marking; driver verifies chip ID |
| AS7331 | `0x74` | UV 5 Click address jumpers in their default position |
| DS3231 | `0x68` | Optional module EEPROM at `0x57` is not used |
| MB85RC256V | `0x50` | A2=A1=A0 low, WP low for writes |

These four modules share two signal wires; they do not need four I2C buses.
Different addresses let the ESP32 select each device. Address changes require
matching firmware changes; another module at the same address is not plug-and-play.

## Verify the assembled instrument

Use **Sensor information** and **Diagnostics** in the app, or `HELLO` and
`DIAGNOSTIC <32_hex_characters>` from an independent host. Check both chip
initializations, RTC validity, FRAM read/write and microSD read/write, not merely
an I2C acknowledgement. `LED_TEST` and `BUZZER_TEST` separately exercise outputs;
a GPIO diagnostic cannot prove that an LED lights or a buzzer sounds.

The [serial protocol](../../docs/USB_SERIAL_PROTOCOL.md) covers measurements,
jobs, alerts, acknowledgements and diagnostics. The
[standalone guide](../../docs/STANDALONE_SENSOR.md) covers USB, Wi-Fi, Bluetooth,
MQTT and operation without the Android app.
