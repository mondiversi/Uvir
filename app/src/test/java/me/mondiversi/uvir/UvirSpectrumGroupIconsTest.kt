package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirSpectrumGroupIconsTest {
    private val groups = listOf(
        SensorGroup.UV, SensorGroup.VISIBLE, SensorGroup.NIR, SensorGroup.BIOLOGICAL
    )

    @Test fun sessionGroupsPreserveTheirSpectrumIdentity() {
        assertEquals(groups, SessionChartGroup.entries.map { it.spectrumIconGroup() })
    }

    @Test fun acquisitionChartSectionsMatchTheirDataCardGroups() {
        assertEquals(groups, AcquisitionChartSection.entries.map { it.spectrumIconGroup() })
    }
}
