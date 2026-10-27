# Uvir updates

> The text below describes the preserved ESP32 stable channel. The USB development
> edition reserves a separate signed `usb-channel` feed and does not query or
> replace `releases/latest`. Its channel is disabled until a USB release is
> published and validated. See [the release boundary](usb/ARCHITECTURE.md#release-boundary).

Uvir checks the latest stable GitHub release once per process launch. In Info,
**Check for updates** runs the same check manually. A successful empty result is
silent automatically and shows a short message manually. Network, signature,
download, installer and USB failures go to the existing error log; they are not
reported as “no updates”. No update is installed without user action.

The APK installer uses Android's standard confirmation screen, as in Space
Compass. If Android requests permission to install applications and recreates
the Uvir process, returning from Settings reloads the locally persisted signed
catalog and APK. Signature, package/version, file length and SHA-256 are checked
again before handing the file to Android. Recovery does not rely on an in-memory
file reference and does not require another download. Sensor updates remain a
separate, USB-only operation after updating the app.

The availability dialog lists the app first, followed by associated sensors in
display-name order, with current and available versions. Sensor versions are the
last authenticated versions received from each sensor, not guesses. Unassociated
or never-identified hardware is not silently adopted by the updater.

Update actions are uppercase, purple text actions with no resting outline or
fill, in both themes. App verification/failure messages never mention sensor
recovery or keeping USB connected; that warning belongs only to firmware work.

## Authenticity and download

The public index is `releases/latest/download/uvir-update.json`. It is an envelope
containing a Base64 payload and an RSA SHA-256 signature. The app pins the public
release certificate; no private key or password is included in the repository.
Signed metadata includes versions, application ID, minimum app/Android versions,
firmware layout, download URLs, lengths and SHA-256 hashes. Files are downloaded
to private storage, bounded by the signed length, and verified before use. Only
this repository's HTTPS release URLs and GitHub's asset redirect hosts are allowed.
Equal or older versions are never offered as upgrades.

Android APKs must also have the expected package, version and signing certificate.
Android asks for installation permission/confirmation; this is not a silent
installer. Updating does not uninstall the app, clear its database or forget
sensor associations. A signed pending plan in non-backed-up storage survives
replacement of the app so firmware can be offered afterwards, including offline.

## Sensor USB update

Update the app first. Select the target sensor, connect it using USB OTG, finish
every session and synchronize all stored records. The updater checks the fresh
USB identity and idle/sync state, supported firmware (0.5.102 or newer), classic
ESP32 board and the 3 MiB application partition. Exclusive serial ownership
prevents the normal reader or heartbeat from interfering with the ROM protocol.
The ROM MAC identity, security configuration and actual partition-table digest
are checked again against signed release metadata before erase.
Secure boot, encrypted flash, custom flash pin wiring and other ESP32 families
are not supported by this updater.

The firmware action remains disabled until the matching USB device is attached
and authenticated, the sensor is idle, and a successful record synchronization
has been processed with no queued acquisitions, alerts or errors remaining.
App-version, firmware-version and hardware-layout prerequisites are checked by
the same policy. Live USB presence is checked before the download, again after
it, and before transferring ownership of the port for writing. A Wi-Fi,
Bluetooth or Internet connection alone never enables firmware installation.

Only the application at `0x10000`, up to `0x300000` bytes, is written. Bootloader,
partition table, NVS settings, SPIFFS, FRAM and microSD are not rewritten. An
independent foreground service and CPU wake lock protect the operation when the
display turns off or the app is backgrounded. Progress and actions are shared;
duplicate requests are disabled. Closing the progress popup is ignored while
busy. A flash MD5 verifies the transferred data; a new HELLO with the expected
identity/version is required before reporting success.

**Single-partition limitation:** there is no automatic firmware rollback. Never
remove USB power during a write. If interrupted, use the application-only recovery
command from the USB release archive on a computer. BOOT may need to be held to
enter the ROM loader. Do not erase the entire flash.

## Publishing

Write all public repository documentation, release titles, release notes and
future project-owned GitHub posts in English. This does not change the app's
localized interface strings. Reuse the English release-notes document when
publishing so the repository and the GitHub release stay consistent.

1. Increment the app version/code. Increment the sensor firmware version when
   its implementation changes; an app-only release may reuse verified firmware.
2. Run all tests, build the signed release APK and compile the ESP32 huge_app image.
3. Run `tools/ReleaseTool.java` with Java 17 or newer:

   `java tools/ReleaseTool.java ROOT OUTPUT FIRMWARE_BUILD 1.4.2 7 0.5.119`

4. Create a draft GitHub release tagged `v1.4.2`, titled `v1.4.2`. Upload every generated asset:
   APK, application BIN, USB archive, signed index and SHA256SUMS.
5. Publish only after all uploads and validation succeed. The updater ignores
   drafts and prereleases through GitHub's latest-stable endpoint.
6. Download the public assets again and verify their sizes, signatures and hashes.

Keep `keystore.properties`, the keystore and all credentials outside source
control. Future key rotation needs an explicitly trusted migration, not simply
replacing the index certificate.

The AS7331 and AS7343 modules have been detected and exercised on the prototype.
Development is currently on hold pending optical design validation and reference
calibration with the final PCB, enclosure, lid/apertures and any diffuser,
filter or protective window. The aperture dimensions are theoretical, and
current readings do not establish accurate absolute irradiance. A signed firmware
build is not evidence of measurement accuracy or PCB fabrication approval.
