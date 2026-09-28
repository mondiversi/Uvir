package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirAlertDefaultViewModeTest {
    @Test
    fun `irradiance remains the default when it is available`() {
        assertEquals(
            ViewMode.IRRADIANCE,
            defaultAlertViewMode(
                listOf(
                    ThresholdAlertMetric.UVA,
                    ThresholdAlertMetric.BIO_DNA_UV
                )
            )
        )
    }

    @Test
    fun `biological effects become the default when they are the only data`() {
        assertEquals(
            ViewMode.BIOLOGICAL_EFFECTS,
            defaultAlertViewMode(
                listOf(
                    ThresholdAlertMetric.BIO_DNA_UV,
                    ThresholdAlertMetric.BIO_HEV_OXIDATIVE
                )
            )
        )
    }

    @Test
    fun `empty alerts preserve the safe irradiance default`() {
        assertEquals(
            ViewMode.IRRADIANCE,
            defaultAlertViewMode(emptyList())
        )
    }
}
