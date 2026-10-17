# ESP32 status LEDs

Use one **common-cathode RGB LED** (red/green only) and one separate blue LED.
See [complete wiring](README.md).

| Signal | Connection |
|---|---|
| RGB red anode | GPIO 25 -> 330 ohm resistor -> red anode |
| RGB green anode | GPIO 26 -> separate 330 ohm resistor -> green anode |
| RGB common cathode | GND |
| RGB blue anode | Unconnected |
| Separate blue anode | GPIO 27 -> separate 330 ohm resistor -> anode |
| Separate blue cathode | GND |

The resistor may be placed on either side of its **individual** LED channel;
do not replace the three resistors with one on the common return. Lead order
is not universal. Identify polarity from the actual part/data sheet or diode
test, not just the longest lead of an already trimmed LED. A common-anode LED
is not a drop-in substitute for this wiring and firmware.

## Feedback

| Pattern | Meaning |
|---|---|
| Steady red | Powered, no active app/controller connection |
| Flashing red | Disconnected with activity in progress |
| Flashing yellow | Connection in progress |
| Steady green | Active app/controller connection, idle |
| Flashing green | Connected with activity in progress |
| Three rapid red flashes | Acquisition/alert indication while disconnected |
| Three rapid green flashes | Acquisition/alert indication while connected |
| Five rapid orange flashes | Explicit acquisition cannot be saved, including invalid time / incompatible active activity |
| Separate steady blue | Acquisitions, alerts or errors remain pending synchronization |

RGB and blue are independent: flashing green and steady blue can appear together.
The blue LED is not the activity indicator; activity uses RGB. Debug performances
and the LED self-test temporarily exercise the outputs outside the normal legend.
Rejection also has a prolonged buzzer indication when the buzzer is enabled.

Use `LED_TEST` to inspect the patterns, not only `DIAGNOSTIC`, which checks
configuration/control paths without optical feedback. Brightness defaults to
20% only for new settings; an update preserves the user's saved value.

Keep every LED, including breakout power indicators, out of the optical sensor
field. Folded leads may position indicators at the enclosure edge, but insulate
them and maintain polarity. Calibrate after installing the final opaque barriers.
