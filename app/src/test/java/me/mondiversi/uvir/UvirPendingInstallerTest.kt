package me.mondiversi.uvir

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class UvirPendingInstallerTest {
    @Test fun pendingApkMustStillMatchSignedSizeAndHash() {
        val file = File.createTempFile("uvir-installer-", ".apk")
        try {
            val bytes = byteArrayOf(1, 2, 3, 4)
            file.writeBytes(bytes)
            val asset = UvirUpdateAsset("https://github.com/mondiversi/Uvir/releases/download/v1.4.0/app.apk",
                bytes.size.toLong(), UvirUpdatePolicy.sha256(bytes))
            assertTrue(UvirUpdatePolicy.matches(file, asset))
            file.writeBytes(byteArrayOf(1, 2, 3, 5))
            assertFalse(UvirUpdatePolicy.matches(file, asset))
            file.writeBytes(byteArrayOf(1))
            assertFalse(UvirUpdatePolicy.matches(file, asset))
            assertFalse(UvirUpdatePolicy.matches(File(file.parentFile, "missing-${file.name}"), asset))
        } finally { file.delete() }
    }

    @Test fun permissionReturnReloadsAndRevalidatesThePersistedInstaller() {
        val directory = listOf(File("src/main/java/me/mondiversi/uvir"),
            File("app/src/main/java/me/mondiversi/uvir")).first { it.isDirectory }
        val host = File(directory, "UvirUpdateHost.kt").readText()
        val client = File(directory, "UvirUpdateClient.kt").readText()
        val updates = File(directory, "UvirUpdates.kt").readText()
        assertTrue(host.contains("UvirUpdates.continueInstall(context)"))
        assertFalse(host.contains("downloadedInstaller()"))
        assertTrue(client.contains("getString(\"pending_app_catalog\", null)"))
        assertTrue(client.contains("val release = verifyCatalog(encoded).app"))
        assertTrue(client.contains("release.code > BuildConfig.VERSION_CODE"))
        assertTrue(client.contains("UvirUpdates.verifyApk(context, source, release)"))
        assertTrue(client.contains("UvirUpdates.verifyApk(context, shared, release)"))
        assertTrue(updates.contains("client.savePendingInstaller(encodedCatalog)"))
        assertTrue(updates.contains("application/vnd.android.package-archive"))
        assertTrue(updates.contains("ClipData.newRawUri"))
    }
}
