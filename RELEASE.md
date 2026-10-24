# Uvir distribution

## Application identity

- Display name: `Uvir`
- Application ID: `me.mondiversi.uvir`
- Namespace: `me.mondiversi.uvir`
- Current version: `1.4.1` (`versionCode` 6)
- Sensor firmware: `0.5.112`

## Building the APK

Double-click `CREA_APK_RELEASE.bat`.

When the build finishes, the signed APK ready for distribution is available at:

```text
dist\Uvir-1.4.1-release.apk
```

The same directory also contains `Uvir-1.4.1-release.apk.sha256`, which can be
used to verify that a download has not been modified or corrupted.

The APK can be attached to a GitHub release or distributed from a website.
Make the corresponding source available in accordance with the project's
[GPL-3.0 license](LICENSE).

For a complete release (APK, USB firmware, signed index and checksums), follow
[docs/UPDATES.md](docs/UPDATES.md). Publish the release only after all files have
been uploaded. Both optical modules are installed and detected; final-assembly
tests and traceable calibration remain pending until the PCB/optical assembly
is ready. A development-source push is not a new signed APK/firmware release.

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
