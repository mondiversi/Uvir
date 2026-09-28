# Uvir updates

Uvir checks the latest stable GitHub release once per process launch. In Info,
**Check for updates** runs the same check manually. A successful empty result is
silent automatically and shows a short message manually. Network, signature,
download, installer and USB failures go to the existing error log; they are not
reported as “no updates”. No update is installed without user action.

The availability dialog lists the app first, followed by associated sensors in
display-name order, with current and available versions. Sensor versions are the
last authenticated versions received from each sensor, not guesses. Unassociated
or never-identified hardware is not silently adopted by the updater.

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

1. Increment the app version/code and sensor firmware version.
2. Run all tests, build the signed release APK and compile the ESP32 huge_app image.
3. Run `tools/ReleaseTool.java` with Java 17 or newer:

   `java tools/ReleaseTool.java ROOT OUTPUT FIRMWARE_BUILD 1.3.0 4 0.5.103`

4. Create a draft GitHub release tagged `v1.3.0`. Upload every generated asset:
   APK, application BIN, USB archive, signed index and SHA256SUMS.
5. Publish only after all uploads and validation succeed. The updater ignores
   drafts and prereleases through GitHub's latest-stable endpoint.
6. Download the public assets again and verify their sizes, signatures and hashes.

Keep `keystore.properties`, the keystore and all credentials outside source
control. Future key rotation needs an explicitly trusted migration, not simply
replacing the index certificate.

Physical testing of the AS7331 UV component remains pending until installation.
