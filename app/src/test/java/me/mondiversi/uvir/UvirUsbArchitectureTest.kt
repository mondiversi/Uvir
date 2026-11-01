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
    @Test fun currentHardwareAndUnresolvedCalibrationAreExplicitlyDocumented() {
        val readme = File(root, "README.md").readText()
        for (component in listOf("FT232H", "AS7331", "AS7343")) assertTrue(readme.contains(component))
        assertTrue(readme.contains("PROJECT SUSPENDED"))
        assertTrue(readme.contains("OPTICAL CALIBRATION IS UNRESOLVED"))
        assertTrue(readme.contains("Reliable absolute irradiance measurements have not been achieved or validated"))
        assertTrue(File(root, "docs/usb/WIRING.md").readText().contains("AD2"))
    }
}
