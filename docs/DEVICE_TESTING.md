# Device testing and archive safety

Prefer a disposable emulator or dedicated test phone. Isolated UI fixtures do not
insert production acquisitions or alerts, but that alone does not make a test runner
safe for a personal installation.

Android Gradle Plugin 9.3.1 defaults to uninstalling the target and test APKs when
connected instrumentation tests finish. This also deletes the target app's data.
The project therefore explicitly sets:

- `android.injected.androidTest.leaveApksInstalledAfterRun=true`
- `android.experimental.testOptions.uninstallIncompatibleApks=false`

Do not override these settings. Do not use uninstall, clear-data or an automatic
signature-recovery operation on a phone containing a user archive. Android cloud
restore is neither an up-to-date archive guarantee nor a credential backup.

Before any instrumentation run on a personal device:

1. Obtain explicit authorization for the selected test classes and keep a verified
   database/settings export, including an encrypted sensor profile export.
2. Verify that both installed and new APKs have matching signing certificates.
3. Build APKs without running a connected test task, then update with `adb install -r`.
   Stop if installation fails; do not uninstall to work around the failure.
4. Run only reviewed, isolated classes through `adb shell am instrument`.
   Do not run the complete suite against a personal installation.
5. Verify that the package's original `firstInstallTime`, archive counts and sensor
   association remain unchanged; restore temporarily changed screen settings.

Verification reports must distinguish an archive-preserving update from a fresh
installation followed by automatic cloud restore. Restored counts alone do not prove
that recent data were included. Keep any incident report or recovered user snapshot
local; neither is a public release artifact.

## Local filter result-count update — 2026-10-05, unpublished

Acquisition and alert list filter fields now append the final intersected row
count in parentheses. Each real list passes the size of its existing filtered
collection; no second database query or filter pass is added. Enabled ID, session,
date, mode, sensor, note and acquisition-variant fields share the same count.
The alert page's disabled variant field omits the suffix. Number preferences,
accessible descriptions and the selected-label ellipsis preserve the count.
Choices remain derived from unfiltered data and filter storage stays unchanged.

The debug build and all **565 JVM tests** passed. Android test sources compiled;
existing private-database integration fixtures now assert the count in each
enabled field, but no instrumentation was executed for this update. The Samsung
phone check covered actual list fields and a temporary additional variant filter,
then restored the exact original filters, including the existing ID criterion.
Archive data were not edited and no acquisitions were started by the check.
The locally signed APK matched the installed signing identity, was installed with
`-r`, and its installed bytes and original first-install timestamp were verified.
Tablet, RTL and dark-mode checks were not repeated. No GitHub publication occurred.

## Development snapshot validation - 2026-10-17

The source snapshot consolidates the current Android/firmware work and adds the
anonymized hardware collection and setup tutorial. It is not a new installable
release and does not replace existing APKs, firmware assets or signed update indexes.

| Check | Result |
|---|---|
| Android JVM suite | 586 tests, zero failures/errors/skips; rerun locally |
| Android builds | Debug and release builds passed; release vital lint passed |
| Android instrumentation sources | Compiled; no connected-device suite executed for this push |
| Portable signing resolver | Standalone Java tests passed without exposing key/password contents |
| Firmware host checks | 10 executable suites passed |
| ESP32 firmware build | `huge_app`, successful; 2,099,425 bytes flash, 72,312 bytes static RAM |
| Hardware collection | 15 manifest entries verified; JSON and ZIP checks passed |
| Documentation links | Local links in the setup guide and primary indexes resolved |
| Publication hygiene | Private keys/configuration, APKs, binaries, local databases and build outputs excluded |

The firmware host suites cover bounded acquisition retries, acquisition/alert
beep patterns, condition parsing and runtime transitions, external-command
policy, visible automatic gain, volume previews, LED status and Wi-Fi/Bluetooth
reset helpers. They use host stubs, not physical hardware. A test-only hardware
identity was replaced with a synthetic fixture; behavior and rejection tests
remain covered.

Firmware toolchain used for this build: ESP32 board package 3.3.11,
ArduinoMqttClient 0.1.8, SparkFun AS7343 1.0.0, SparkFun AS7331 2.2.0 and
SparkFun Toolkit 1.2.0. Static RAM usage is compiler output, not a runtime
heap/stack safety proof. The firmware was compiled only, not flashed.

No phone was installed, cleared, uninstalled or exercised by this publication
check; no acquisitions, alerts or associations were created or changed. PCB
ERC/DRC, footprint/outline review, community-library permissions and final
assembled optical calibration remain separate review items, documented with
the hardware bundle.
