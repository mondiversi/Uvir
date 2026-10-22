package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UvirIrradianceGroupShareTest {
    @Test fun everyChannelUsesOneGrandTotalWithoutDoubleCountingHevOrHeaders() {
        val sample = SensorSample(uvc = 1.0, uvb = 1.0, uva = 2.0,
            violetto = 2.0, blu = 2.0, verde = 2.0, giallo = 2.0,
            arancione = 1.0, rosso = 1.0, f8 = 3.0, nir = 1.0)
        val totals = sample.spectralTotals()
        assertEquals(1f / 18f, totals.shareOfTotal(sample.uvc)!!, .000001f)
        assertEquals(4f / 18f, totals.shareOfTotal(sample.violetto + sample.blu)!!, .000001f)
        assertEquals(.056f, totals.displayShare(sample.uvc, 0, UvirIrradianceUnit.UW_CM2)!!, 0f)
        assertEquals(.222f, totals.displayShare(4.0, 0, UvirIrradianceUnit.UW_CM2)!!, 0f)
        val channels = listOf(sample.uvc, sample.uvb, sample.uva, sample.violetto, sample.blu,
            sample.verde, sample.giallo, sample.arancione, sample.rosso, sample.f8, sample.nir)
        assertEquals(1.0, channels.sumOf { totals.shareOfTotal(it)!!.toDouble() }, .000001)
    }

    @Test fun globalSharesAreUnknownIfAnyRequiredGroupIsOutOfRange() {
        val totals = SensorSample(uvc = 20.0, nir = 50.0,
            qualityFlags = UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE).spectralTotals()
        assertNull(totals.shareOfTotal(20.0))
        assertNull(totals.displayShare(20.0, 2, UvirIrradianceUnit.W_M2))
        assertEquals(0f, SensorSample().spectralTotals().displayShare(0.0, 2, UvirIrradianceUnit.W_M2)!!, 0f)
    }

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
