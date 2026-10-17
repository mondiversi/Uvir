# ESP32 external command / pushbutton

Connect a normally-open dry contact between **GPIO 33 and GND**. Firmware uses
the internal pull-up: released is high, pressed is low. There is no external
5 V signal in this connection. See [complete wiring](README.md).

On a four-leg tactile button, two leads are usually internally common on each
contact group. Verify continuity and select leads from **opposite groups**;
using two already-connected legs makes the input appear permanently pressed.
A two-pin connector can extend the same contact to an enclosure button.

Never apply 5 V directly. External equipment must use an appropriate dry contact,
3.3 V-compatible open-drain interface with shared reference, or suitable isolation
and protection. This is not an industrial-voltage input. Keep long/noisy wiring
out of the build until filtering/protection have been reviewed.

## Implemented actions

| Gesture / state | Result |
|---|---|
| Short press, no activity | Standalone external acquisition |
| Three quick short presses, idle | Start an external session, without recording the three gesture presses |
| Short press, external session | Add one externally triggered acquisition |
| Short press, manual app session | Add an acquisition to that session, marked externally triggered |
| Short press, normal automatic session | Reject; do not add a record |
| Short press, alerts active | Reject; do not add a record |
| Hold at least 2 seconds, automatic/external session or alerts | Stop that sensor activity; not a power-off command |
| No valid date/time | Reject recording; five orange flashes and prolonged beep |

Debounce is 20 ms; consecutive presses for the triple gesture use a 400 ms
window. The input is enabled by default and can be disabled in sensor settings.
External sessions may originate from the app or the physical button; both
follow the same activity rules. A repeated triple gesture does not append three
records to an already running external session.

Inspect the input in diagnostics before testing gestures. Button tests **create
real records or stop real activity**: use an idle device and a test archive.
The [protocol guide](../../docs/USB_SERIAL_PROTOCOL.md) explains remote session
IDs, durable acknowledgements and later recovery without the app present.
