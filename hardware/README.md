# Uvir hardware

The current measuring head consists of an **FT232H USB-to-I²C interface**, a
**UV 5 Click / AS7331** UV module and a **GY-AS734X / AS7343** visible/NIR module.
The phone runs the drivers, sampling, calibration, acquisition and alert logic,
and stores the records. The head communicates over USB and has no local archive.

> **Development is suspended because optical calibration remains unresolved.**
> The assembled electrical prototype and app operate; reliable absolute
> irradiance has not been achieved or validated. See [project status](../docs/PROJECT_STATUS.md).

## Build references

- [Components, shared-bus wiring and removable plug-in modules](usb/README.md).
- [Power checks and first connection](../docs/usb/WIRING.md).
- [Reference lower case and experimental 5 mm lid](printable/README.md).
- [Carrier dimensions, fixing positions, USB opening and sensor centres](printable/uvir-custom-case/README.md).
- [Calibration tools and unresolved optical work](../docs/usb/CALIBRATION.md).
- [Read the head from another USB host](../docs/usb/STANDALONE.md).

The reference assembly uses a hand-built perfboard carrier with raised removable
sensor sockets. Wiring is documented by signal, without prescribing individual
wire routes on the carrier. Different breakout power circuits, socket heights or
module positions require checking the electrical and mechanical references.
