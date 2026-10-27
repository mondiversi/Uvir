package me.mondiversi.uvir

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class UvirUsbArchitectureTest {
    private val root = listOf(File("."), File("..")).first { File(it, "app/build.gradle.kts").isFile }
    private fun source(name: String) = File(root, "app/src/main/java/me/mondiversi/uvir/$name.kt").readText()
    @Test fun activityNeverStartsAnEsp32Manager() {
        val create = source("MainActivity").substringAfter("override fun onCreate(savedInstanceState: Bundle?) {")
            .substringBefore("override fun onDestroy()")
        assertTrue(create.contains("UvirUsbPhoneApp(phoneRuntime)"))
        for (forbidden in listOf("UvirApp(", "retainedRuntime.startIfNeeded", "UvirRetainedRuntimeStore.acquire"))
            assertFalse(forbidden, create.contains(forbidden))
    }
    @Test fun manifestDoesNotExposeWirelessOrFirmwareServices() {
        val xml = File(root, "app/src/main/AndroidManifest.xml").readText()
        for (forbidden in listOf("BLUETOOTH", "LOCATION", "WIFI_STATE", "UvirFirmwareUpdateService", "UvirConnectionForegroundService"))
            assertFalse(forbidden, xml.contains(forbidden))
        assertTrue(xml.contains(".UsbPhoneService"))
        val filter = File(root, "app/src/main/res/xml/usb_device_filter.xml").readText()
        assertTrue(filter.contains("vendor-id=\"1027\"")); assertTrue(filter.contains("product-id=\"24596\""))
    }
    @Test fun incompatibleEditionsHaveSeparateUpdateChannels() {
        assertNotEquals(UvirUpdatePolicy.INDEX, UvirUpdatePolicy.USB_INDEX)
        assertTrue(UvirUpdatePolicy.allowedUrl(UvirUpdatePolicy.USB_INDEX))
        assertFalse(BuildConfig.USB_UPDATE_CHANNEL_READY)
        assertTrue(source("UvirUpdates").contains("if (BuildConfig.USB_ONLY) emptySet()"))
    }
    @Test fun legacyAndPendingHardwareAreExplicitlyDocumented() {
        val readme = File(root, "README.md").readText()
        assertTrue(readme.contains("legacy-esp32"))
        assertTrue(readme.contains("7161a31711f249de2bc346199c269da7aaea88c5"))
        assertTrue(readme.contains("Hardware validation is pending"))
        assertTrue(File(root, "docs/usb/WIRING.md").readText().contains("AD2"))
    }
}
