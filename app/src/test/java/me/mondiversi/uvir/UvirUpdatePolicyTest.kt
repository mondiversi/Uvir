package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirUpdatePolicyTest {
    @Test fun onlyPinnedHttpsReleaseUrls() {
        assertTrue(UvirUpdatePolicy.allowedUrl(UvirUpdatePolicy.INDEX))
        assertFalse(UvirUpdatePolicy.allowedUrl("http://github.com/mondiversi/Uvir/releases/latest/download/x"))
        assertFalse(UvirUpdatePolicy.allowedUrl("https://github.com/other/repo/releases/download/x/y"))
        assertFalse(UvirUpdatePolicy.allowedUrl("https://github.com.evil.test/mondiversi/Uvir/releases/download/x/y"))
        assertFalse(UvirUpdatePolicy.allowedUrl("https://user@github.com/mondiversi/Uvir/releases/download/x/y"))
        assertFalse(UvirUpdatePolicy.allowedUrl("https://release-assets.githubusercontent.com/a"))
        assertTrue(UvirUpdatePolicy.allowedUrl("https://release-assets.githubusercontent.com/a", true))
    }
    @Test fun slipEscapesReservedBytes() {
        assertArrayEquals(byteArrayOf(0xC0.toByte(), 1, 0xDB.toByte(), 0xDC.toByte(), 0xDB.toByte(), 0xDD.toByte(), 0xC0.toByte()),
            UvirSlip.encode(byteArrayOf(1, 0xC0.toByte(), 0xDB.toByte())))
    }
    @Test fun rejectForeignChipAndOversizeImage() {
        val image = ByteArray(24); image[0] = 0xE9.toByte()
        assertTrue(UvirUpdatePolicy.validFirmwareImage(image))
        image[12] = 2
        assertFalse(UvirUpdatePolicy.validFirmwareImage(image))
        assertFalse(UvirUpdatePolicy.validFirmwareImage(ByteArray(UvirUpdatePolicy.APP_CAPACITY + 1)))
    }
    @Test fun digestAndVersions() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", UvirUpdatePolicy.sha256(byteArrayOf()))
        assertTrue(compareFirmwareVersions("0.5.103", "0.5.99") > 0)
        assertEquals(0, compareFirmwareVersions("0.5.103", "0.5.103"))
    }
}
