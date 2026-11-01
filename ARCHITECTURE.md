# Uvir architecture

Uvir is an Android host for an FT232H measuring head with AS7331 UV and AS7343
visible/NIR modules on a shared I²C bus. The phone owns all measurement processing,
configuration, scheduling, alerts and storage.

- `MainActivity` opens `UvirUsbPhoneApp` and its retained `UvirUsbPhoneRuntime`.
- `usb/AndroidFt232h.kt` and `usb/Ft232hMpsse.kt` serialize USB and I²C operations.
- `usb/SpectralChips.kt` initializes both optical chips and handles conversion,
  readiness, gain adaptation and saturation independently.
- `usb/PhoneOpticalSampling.kt` implements phone-owned sampling and calibration.
- Phone acquisition/alert controllers consume new valid measurements; hidden
  bands, percentages and display rounding do not alter records or thresholds.
- `UvirDatabase` and the archive/export modules preserve full-precision W/m²
  values. Older archives are migrated on private copies before replacement.
- Shared presentation components support phone/tablet and portrait/landscape
  layouts, day/night themes and 20 translated resource catalogs.

See the [detailed runtime and storage architecture](docs/usb/ARCHITECTURE.md),
[hardware references](hardware/README.md), [archive transfer](docs/RECORD_TRANSFER.md)
and [verification status](docs/usb/VALIDATION.md).

Software functionality is separate from optical validity: the assembled prototype
supplies readings, but development is suspended at unresolved calibration.
See [project status](docs/PROJECT_STATUS.md).
