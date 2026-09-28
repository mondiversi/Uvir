package me.mondiversi.uvir

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import java.util.Base64
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class UvirUpdateVerificationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun fixture() = instrumentation.context.assets.open("uvir-update-fixture.json").bufferedReader().use { it.readText() }
    @Test fun signedCatalogUsesExpectedIdentityAndVersions() {
        val catalog = UvirUpdateClient(context).verifyCatalog(fixture())
        assertEquals(4, catalog.app.code)
        assertEquals("1.3.0", catalog.app.version)
        assertEquals("0.5.103", catalog.sensor.version)
        assertEquals(4, catalog.sensor.minAppCode)
    }
    @Test fun alteredPayloadIsRejected() {
        val envelope = JSONObject(fixture())
        val bytes = Base64.getDecoder().decode(envelope.getString("payload"))
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 1).toByte()
        envelope.put("payload", Base64.getEncoder().encodeToString(bytes))
        assertThrows(IllegalArgumentException::class.java) { UvirUpdateClient(context).verifyCatalog(envelope.toString()) }
    }
    @Test fun alteredSignatureIsRejected() {
        val envelope = JSONObject(fixture())
        envelope.put("signature", Base64.getEncoder().encodeToString(ByteArray(512)))
        assertThrows(IllegalArgumentException::class.java) { UvirUpdateClient(context).verifyCatalog(envelope.toString()) }
    }
    @Test fun unknownSignatureAlgorithmIsRejected() {
        val envelope = JSONObject(fixture()).put("algorithm", "none")
        assertThrows(IllegalArgumentException::class.java) { UvirUpdateClient(context).verifyCatalog(envelope.toString()) }
    }
    @Test fun publishedAssetsDownloadAndVerifyOnAndroid() {
        // Explicit opt-in: offline unit/CI runs do not depend on GitHub availability.
        assumeTrue(InstrumentationRegistry.getArguments().getString("uvirNetworkTest") == "true")
        val client = UvirUpdateClient(context)
        val catalog = client.catalog().first
        val firmware = client.download(catalog.sensor.asset, "bin") {}
        assertEquals(catalog.sensor.asset.bytes, firmware.length())
        assertTrue(UvirUpdatePolicy.validFirmwareImage(firmware.readBytes()))
        val apk = client.download(catalog.app.asset, "apk") {}
        UvirUpdates.verifyApk(context, apk, catalog.app)
        // Cached files are revalidated, not accepted merely because a file exists.
        assertEquals(apk, client.download(catalog.app.asset, "apk") {})
    }
    @Test fun corruptionCannotBeUsedAsCachedFirmware() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("uvirNetworkTest") == "true")
        val client = UvirUpdateClient(context)
        val asset = client.catalog().first.sensor.asset
        val file = client.download(asset, "bin") {}
        file.outputStream().use { it.write(0) }
        val restored = client.download(asset, "bin") {}
        assertEquals(asset.bytes, restored.length())
        assertEquals(asset.sha256, UvirUpdatePolicy.sha256(restored.readBytes()))
    }
}
