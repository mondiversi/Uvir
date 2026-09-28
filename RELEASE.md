# Uvir distribution

## Application identity

- Display name: `Uvir`
- Application ID: `me.mondiversi.uvir`
- Namespace: `me.mondiversi.uvir`
- Current version: `1.3.0` (`versionCode` 4)
- Sensor firmware: `0.5.103`

## Building the APK

Double-click `CREA_APK_RELEASE.bat`.

When the build finishes, the signed APK ready for distribution is available at:

```text
dist\Uvir-1.3.0-release.apk
```

The same directory also contains `Uvir-1.3.0-release.apk.sha256`, which can be
used to verify that a download has not been modified or corrupted.

The APK can be attached to a GitHub release or distributed from a website.
Make the corresponding source available in accordance with the project's
[GPL-3.0 license](LICENSE).

For a complete release (APK, USB firmware, signed index and checksums), follow
[docs/UPDATES.md](docs/UPDATES.md). Publish the release only after all files have
been uploaded. Physical validation of the AS7331 UV sensor remains pending until
the component is available.

## Signing and updates

The private signing key is stored outside the project at:

```text
C:\Users\otta8\Documents\UvirSigning\uvir-release.jks
```

The local configuration containing the signing credentials is in
`keystore.properties`. Keep both files in a secure, private backup. Never upload
them to GitHub, share them or include them in public archives.

The exported public certificate is available at:

```text
C:\Users\otta8\Documents\UvirSigning\uvir-release-certificate.pem
```

The public certificate may be shared; the private `.jks` key must not be shared.

To publish an update:

1. Increment `versionCode` in `app/build.gradle.kts`.
2. Update `versionName`.
3. Always use the same release signing key.
4. Rebuild the APK with `CREA_APK_RELEASE.bat`.

On the development computer, the `debug` variant launched from Android Studio
also uses this key when `keystore.properties` is present. This lets **Run** and
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
