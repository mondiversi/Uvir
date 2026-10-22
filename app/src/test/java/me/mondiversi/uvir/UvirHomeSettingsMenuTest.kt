package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirHomeSettingsMenuTest {
    @Test fun generalUsesTheGearWithoutChangingOtherMenuEntries() {
        val general = uvirHomeSettingsMenuGroups.flatMap { it.entries }
            .single { it.page == UvirSettingsPage.SENSOR_PARAMETERS }
        assertEquals(ConnectivityIconType.GEAR, general.icon)
        assertEquals(R.string.settings_section_sensor_parameters, general.titleRes)
    }

    @Test fun everyActiveSettingsPageAppearsExactlyOnce() {
        val pages = uvirHomeSettingsMenuGroups.flatMap { it.entries }.mapNotNull { it.page }
        assertEquals(setOf(UvirSettingsPage.SENSOR_PARAMETERS, UvirSettingsPage.SENSOR_CONNECTION,
            UvirSettingsPage.SENSOR_MEASUREMENT, UvirSettingsPage.DEBUG, UvirSettingsPage.APPEARANCE,
            UvirSettingsPage.ALERTS, UvirSettingsPage.LANGUAGE, UvirSettingsPage.LANGUAGE_AND_FORMATS,
            UvirSettingsPage.DATA_RESTORE), pages.toSet())
        assertEquals(pages.size, pages.distinct().size)
    }

    @Test fun sensorAndAppGroupsKeepTheExistingOrderAndInformationIsLast() {
        assertEquals(R.string.settings_category_sensor, uvirHomeSettingsMenuGroups[0].titleRes)
        assertEquals(R.string.data_management_app_button, uvirHomeSettingsMenuGroups[1].titleRes)
        assertEquals(listOf(UvirSettingsPage.SENSOR_PARAMETERS, UvirSettingsPage.SENSOR_CONNECTION,
            UvirSettingsPage.SENSOR_MEASUREMENT, UvirSettingsPage.DEBUG),
            uvirHomeSettingsMenuGroups[0].entries.map { it.page })
        val last = uvirHomeSettingsMenuGroups.last().entries
        assertEquals(UvirSettingsPage.DATA_RESTORE, last.first().page)
        assertEquals(R.string.version_info, last.last().titleRes)
        assertEquals(null, last.last().page)
    }

    @Test fun unfinishedAndLegacyRoutesAreNotExposed() {
        val pages = uvirHomeSettingsMenuGroups.flatMap { it.entries }.map { it.page }
        assertFalse(UvirSettingsPage.DISPLAY in pages)
        assertFalse(UvirSettingsPage.SHARING_AND_EXPORT in pages)
        assertTrue(uvirHomeSettingsMenuGroups.flatMap { it.entries }.all { it.titleRes != 0 })
    }

    @Test fun dropdownBackReturnsHomeButDisablingTheTrialRestoresTheSettingsIndex() {
        UvirSettingsPage.entries.forEach { page ->
            assertTrue(uvirSettingsBackReturnsHome(page, dropdownEnabled = true))
            assertFalse(uvirSettingsBackReturnsHome(page, dropdownEnabled = false))
        }
        assertTrue(uvirSettingsBackReturnsHome(null, dropdownEnabled = true))
        assertTrue(uvirSettingsBackReturnsHome(null, dropdownEnabled = false))
    }
}
