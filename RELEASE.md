# Uvir USB distribution

`main` starts the USB edition at **0.1.0** (`versionCode` 9).
This is a public **prerelease**, with real FT232H hardware validation pending.
The preserved ESP32 edition is **1.4.2 / firmware 0.5.119** on `legacy-esp32`.
Do not replace its installers, firmware or stable update feed with this APK.

## Application identity

- Display name: `Uvir`
- Application ID / namespace: `me.mondiversi.uvir`
- Version: `0.1.0` (`versionCode` 9)
- Minimum Android: 8.0 / API 26
- Measuring head: FT232H + AS7331 + AS7343; no sensor firmware asset

## Build and publish the USB preview

1. Run the unit and isolated device tests. Read the
   [validation status](docs/usb/VALIDATION.md); software tests do not certify
   physical transfers or calibrated irradiance.
2. Build the signed APK with `CREA_APK_RELEASE.bat`, or use
   `gradlew.bat :app:assembleRelease` with the private signing configuration below.
3. The script copies the APK to `dist/Uvir-0.1.0.apk` and creates
   `dist/Uvir-0.1.0.apk.sha256`. `dist/` is ignored; binaries are release assets,
   not source commits.
4. Create a draft release tagged and titled `v0.1.0` at the reviewed `main` commit.
   Use [the English release notes](docs/RELEASE_NOTES_0.1.0.md), attach
   `Uvir-0.1.0.apk` and `SHA256SUMS`, and mark it **prerelease**.
5. Validate the package, version, existing signing certificate and downloaded
   checksums before publishing. Keep `make_latest` false so ESP32 users continue
   to receive their stable release.

GitHub provides the tagged source ZIP/tarball automatically. Make corresponding
source available under the [GPL-3.0 license](LICENSE). Do not attach an ESP32 BIN,
USB firmware uploader or the legacy `uvir-update.json` to this USB-only preview.
The new USB automatic update channel remains disabled until separately validated.
The historical `tools/ReleaseTool.java` procedure belongs to `legacy-esp32`.

## Signing and updates

The private signing key is stored outside the project, in the owner's private
Dropbox folder. The default location follows the current account's home folder:

```text
<user home>/Dropbox/Mondiversi/UvirSigning/uvir-release.jks
```

The complete private configuration is `signing.properties` beside the key. It
contains `storePassword`, `keyAlias`, `keyPassword` and an optional `storeFile`.
Leave `storeFile` blank to resolve `uvir-release.jks` in this directory.
Gradle and `tools/ReleaseTool.java` read this shared configuration automatically;
an ignored project-local `keystore.properties` can optionally override its values.
If Dropbox is installed elsewhere, set `MONDIVERSI_SIGNING_DIR` to the absolute
private `UvirSigning` directory. An explicit absolute `storeFile` takes precedence.
The username is not embedded in the project.

On a new trusted computer, install the Android/JDK tools, use the updated project
sources, and sync the whole private folder with its files available offline.
No project-local password file or manually entered password environment variables
are required. The release build and update-index signer then use Dropbox directly.
Do not generate a replacement key. Keep a separate protected backup and restrict
folder access: anyone with both files can sign the applications. Never publish
the private folder, key or credentials to GitHub or public archives.

The existing UVIR and Space Compass distribution identities use this same key;
the applications and repositories remain independent while the private signing
configuration is intentionally shared.
To print only the resolved key path without signing or creating release assets:

```text
java tools/ReleaseTool.java --keystore-path .
```

Standalone resolver and local credential checks (no release assets are signed):

```text
javac -d build/signing-path-test tools/ReleaseTool.java tools/UvirSigningPathTest.java
java -cp build/signing-path-test UvirSigningPathTest --live
```

The exported public certificate is available at:

```text
<user home>/Dropbox/Mondiversi/UvirSigning/uvir-release-certificate.pem
```

The public certificate may be shared; access to the private `.jks` must remain
restricted to the owner's trusted signing computers.

To publish an update:

1. Increment `versionCode` in `app/build.gradle.kts`.
2. Update `versionName`.
3. Always use the same release signing key.
4. Rebuild the APK with `CREA_APK_RELEASE.bat`.

On the development computer, the `debug` variant launched from Android Studio
also uses this key when a private shared or local configuration is present. This lets **Run** and
**Debug** update an existing installation without certificate conflicts. On other
computers without this private configuration, Gradle automatically falls back to
the normal local debug key.

If the release key or its password is lost, future APKs will not be able to
update an existing installation.

## Public content language

Use English for repository documentation, GitHub release titles and release
notes, build-tool messages and future project-owned GitHub posts. The Android
app remains multilingual; its localized interface strings are not subject to
this documentation rule.
