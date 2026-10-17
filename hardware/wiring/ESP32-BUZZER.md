# ESP32 status buzzer

The reference design uses a **low-current passive piezo** controlled by GPIO 32.
The prototype's BUZZER-12X9 label describes a body/symbol, not enough electrical
information to approve any buzzer sold under that name. See [complete wiring](README.md).

| Compatible passive piezo lead | Connection |
|---|---|
| + | GPIO 32 |
| - | GND |

Direct drive is only appropriate after checking the part's current/load and
drive requirements against ESP32 GPIO limits. An active buzzer, magnetic
transducer, higher-current sounder or three-pin module may require a transistor
driver and other protection appropriate to that load; do not connect one
directly simply because its dimensions match. Such a substitution also affects
whether variable-frequency tones can be reproduced.

The firmware generates tones and controls perceived volume with a bounded
drive curve, not by raising the supply voltage. Acquisition confirmation keeps
its short 1400 Hz tone; alerts use a distinct rising 1800–2400 Hz pattern.
Volume defaults to 20% for a new sensor; saved user volume is retained on update.

Use `BUZZER_TEST` for the output patterns. With an active authenticated host,
`BUZZER_PREVIEW 20` previews a short beep without changing settings or saving a
record, if the advertised capability is supported and the buzzer is enabled.
Avoid previews during self-tests/diagnostic performances. Diagnose distortion
by checking the actual transducer and power rather than applying more voltage.
