package me.mondiversi.uvir

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirSensorOriginatedSessionTest {
    @Test
    fun markerRecognizesOnlyPositiveBit62SessionTokens() {
        assertTrue(isSensorOriginatedSessionId((1L shl 62) or 1_789_000_000_000L))
        assertFalse(isSensorOriginatedSessionId(0L))
        assertFalse(isSensorOriginatedSessionId(42L))
        assertFalse(isSensorOriginatedSessionId(-1L))
    }
}
