package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirAdaptiveDisplayTest {
    @Test fun phonesKeepTheirCurrentScale() {
        assertEquals(1f, uvirAutomaticScreenScale(320), 0f)
        assertEquals(1f, uvirAutomaticScreenScale(400), 0f)
    }

    @Test fun tabletsEnlargeProportionallyWithoutExcessiveGrowth() {
        assertEquals(1.5f, uvirAutomaticScreenScale(600), 0f)
        assertEquals(2f, uvirAutomaticScreenScale(800), 0f)
        assertEquals(2.5f, uvirAutomaticScreenScale(1200), 0f)
    }
}
