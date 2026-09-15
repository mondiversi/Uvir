package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirChartExportTextTest {
    @Test
    fun acquisitionIdentifierIncludesRecordAndSession() {
        assertEquals(
            "Acquisition / session ID: 17 / 4",
            chartExportIdentifierLine(
                recordLabel = "Acquisition",
                recordId = 17L,
                sessionId = 4L
            )
        )
    }

    @Test
    fun acquisitionIdentifierUsesDashWithoutSession() {
        assertEquals(
            "Acquisition / session ID: 17 / —",
            chartExportIdentifierLine(
                recordLabel = "Acquisition",
                recordId = 17L,
                sessionId = null
            )
        )
    }

    @Test
    fun sessionChartUsesDashWithoutSingleRecord() {
        assertEquals(
            "Alert / session ID: — / 9",
            chartExportIdentifierLine(
                recordLabel = "Alert",
                recordId = null,
                sessionId = 9L
            )
        )
    }
}
