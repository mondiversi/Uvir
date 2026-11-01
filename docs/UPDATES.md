# Uvir updates

Uvir is distributed from the
[v0.1.1 prerelease](https://github.com/mondiversi/Uvir/releases/tag/v0.1.1).
Install the signed APK over an existing compatible installation without
uninstalling or clearing its data. Stop active sessions first. The FT232H head
does not require a firmware upload.

The reserved `usb-channel` application feed remains disabled until its signed
metadata and installation flow are validated. Publishing a prerelease does
not activate it. For now, obtain the APK from the linked release and verify
it against the supplied SHA-256 checksums.

An enabled app updater must verify the expected package, signing identity,
version, length and hash before requesting Android's installation confirmation.
It must retain a prepared APK across process recreation and the request for
permission to install from this source. A failed update must be reported in
the error log and leave the current installation and archive usable.

See [release building and signing](../RELEASE.md) and the
[runtime architecture](usb/ARCHITECTURE.md#release-boundary).
