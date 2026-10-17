# ESP32 controller, power and header reference

The build uses a classic **ESP-WROOM-32 development board**, with the 38-pin
reference supplied for this project. The prototype reports ESP32-D0WD-V3 revision
3.1 and a CP210x USB bridge (VID `10C4`, PID `EA60`). An ESP32-S2/S3/C3 or a
different 30-pin development board is not a mechanically or electrically
identical replacement; Classic Bluetooth SPP also requires compatible silicon.

## Used header positions

Viewed from the **front/component side, USB connector at the bottom**, count
each row from the top. These positions follow the project's reference board,
not every product sold as "ESP32 Dev Module". Verify the printed labels before
powering a replacement. Reverse-side mounting mirrors the physical view.

| Signal label | Header side / position | Used for |
|---|---|---|
| 3V3 | Left 1 | Compatible I2C breakout supplies |
| GPIO 32 | Left 7 | Passive piezo buzzer |
| GPIO 33 | Left 8 | External command contact |
| GPIO 25 | Left 9 | RGB red, own resistor |
| GPIO 26 | Left 10 | RGB green, own resistor |
| GPIO 27 | Left 11 | Separate blue, own resistor |
| GND | Left 14 | Common ground |
| GPIO 13 | Left 15 | microSD CS |
| 5V | Left 19 | Tested regulated microSD adapter VCC |
| GND | Right 1 or 7 | Common ground |
| GPIO 23 | Right 2 | microSD MOSI |
| GPIO 22 | Right 3 | Shared I2C SCL |
| GPIO 21 | Right 6 | Shared I2C SDA |
| GPIO 19 | Right 8 | microSD MISO |
| GPIO 18 | Right 9 | microSD SCK |

Other exposed pins are not needed by the carrier wiring. Do not move signals
to arbitrary unused-looking pins; UART/flash/boot-strapping pins have additional
constraints. The authoritative logical assignment is
[UvirHardwareConfig.h](../../firmware/esp32/UvirSensor/UvirHardwareConfig.h).

## Power and programming

Use a USB data cable for programming/serial and initial provisioning. Serial
control uses **115200 baud, 8N1**. Select
**ESP32 Dev Module** and **Huge APP (3MB No OTA/1MB SPIFFS)**. Build instructions,
library versions and update precautions are in [Getting started](../../docs/GETTING_STARTED.md).

The carrier does not add a verified battery charger or power-source switchover
circuit. Use one suitable source, check the actual board's regulator/current
budget, and do not back-feed USB from an external supply. The GPIOs are 3.3 V,
not 5 V inputs. See [power, shared bus and first-start checks](../ASSEMBLY.md).

Nominal module dimensions are about 52 x 25 mm. Measure header pitch, row
spacing, USB overhang and antenna clearance before committing a footprint;
the rough approximately 22 mm row separation is not an exact socket dimension.
Keep the antenna area clear on both carrier faces. All component-specific
connections are in the [wiring index](README.md).
