package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UvirIrradianceGroupShareTest {
    @Test fun eachGroupUsesTheSumOfAllIrradianceGroups() {
        assertEquals(0.1f, uvirIrradianceGroupShare(10.0, 10.0, 30.0, 60.0), 0.0001f)
        assertEquals(0.3f, uvirIrradianceGroupShare(30.0, 10.0, 30.0, 60.0), 0.0001f)
        assertEquals(0.6f, uvirIrradianceGroupShare(60.0, 10.0, 30.0, 60.0), 0.0001f)
    }

    @Test fun zeroIrradianceDoesNotCreateAnInvalidPercentage() {
        assertEquals(0f, uvirIrradianceGroupShare(0.0, 0.0, 0.0, 0.0), 0f)
    }

    @Test fun savedAcquisitionUsesItsCompleteSample() {
        val totals = SensorSample(uvc = 20.0, blu = 30.0, nir = 50.0).spectralTotals()
        assertEquals(0.2f, totals.share(SensorGroup.UV)!!, 0.0001f)
        assertEquals(0.3f, totals.share(SensorGroup.VISIBLE)!!, 0.0001f)
        assertEquals(0.5f, totals.share(SensorGroup.NIR)!!, 0.0001f)
    }

    @Test fun outOfRangeSampleDoesNotInventShares() {
        val totals = SensorSample(uvc = 20.0, qualityFlags = UVIR_QUALITY_UV_OUT_OF_RANGE).spectralTotals()
        assertNull(totals.share(SensorGroup.UV))
    }
}
