# Device testing and archive safety

Prefer a disposable emulator or a dedicated test phone. Build with
`-PuvirIsolatedVerification=true` for the separate verification package and
preferences. Do not run a complete instrumentation suite against a personal
installation or open its USB hardware in unrelated UI fixtures.

The project keeps test APKs installed after a connected run and disables
automatic uninstallation of incompatible APKs. Do not override those settings.
Never uninstall, clear data or use signature recovery on a phone containing
an archive to work around an installation problem.

Before an authorized personal-device check, retain verified settings/database
exports, compare installed/new signing certificates and update using
`adb install -r`. Stop if installation fails. Verify that archive counts and the
original installation timestamp remain unchanged. Restore temporary device
settings after the test. Cloud restore is not evidence that recent data survived.

Public reports must distinguish compilation, synthetic tests, isolated device
tests and physical hardware/optical trials. Keep private archives, incident
reports, credentials and recovered snapshots out of source and release assets.

See [current verification history](usb/VALIDATION.md) and
[project status](PROJECT_STATUS.md). Software checks do not validate optical
calibration or every USB host/breakout configuration.
