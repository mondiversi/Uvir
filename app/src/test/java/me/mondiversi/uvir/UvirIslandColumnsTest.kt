package me.mondiversi.uvir

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class UvirIslandColumnsTest {
    @Test fun onlyLandscapeWithEnoughLogicalSpaceUsesTwoColumns() {
        assertEquals(1, uvirIslandColumnCount(false, 800f))
        assertEquals(1, uvirIslandColumnCount(true, 479f))
        assertEquals(2, uvirIslandColumnCount(true, 480f))
        assertEquals(2, uvirIslandColumnCount(true, 720f))
    }

    @Test fun portraitPreservesOrderAndTheExistingGap() {
        val placement = uvirPlaceIslands(listOf(100, 50, 80), listOf(false, false, false), 1, 15)
        assertEquals(listOf(UvirIslandPosition(0, 0), UvirIslandPosition(0, 115),
            UvirIslandPosition(0, 180)), placement.positions)
        assertEquals(260, placement.height)
    }

    @Test fun landscapePacksUnequalHeightsWithoutOverlappingOrBlankRows() {
        val placement = uvirPlaceIslands(listOf(200, 50, 80), listOf(false, false, false), 2, 15)
        assertEquals(listOf(UvirIslandPosition(0, 0), UvirIslandPosition(1, 0),
            UvirIslandPosition(1, 65)), placement.positions)
        assertEquals(200, placement.height)
    }

    @Test fun sectionLabelsWaitForBothColumnsAndResetTheNextGroup() {
        val placement = uvirPlaceIslands(listOf(200, 50, 20, 60, 90),
            listOf(false, false, true, false, false), 2, 15)
        assertEquals(UvirIslandPosition(0, 215), placement.positions[2])
        assertEquals(UvirIslandPosition(0, 250), placement.positions[3])
        assertEquals(UvirIslandPosition(1, 250), placement.positions[4])
        assertEquals(340, placement.height)
    }

    @Test fun emptyPagesHaveNoPhantomGap() {
        assertEquals(UvirIslandPlacement(emptyList(), 0), uvirPlaceIslands(emptyList(), emptyList(), 2, 15))
    }

    @Test fun acquisitionAlertAndSessionPagesDoNotOptIntoThisLayout() {
        val directory = listOf(File("src/main/java/me/mondiversi/uvir"),
            File("app/src/main/java/me/mondiversi/uvir")).first { it.isDirectory }
        listOf("UvirRecordDetailScreen.kt", "UvirHistoryScreen.kt", "UvirAlertLogScreen.kt",
            "UvirSessionChartScreen.kt", "UvirAlertSessionChartScreen.kt").forEach { name ->
            assertFalse(name, File(directory, name).readText().contains("UvirIslandColumns("))
        }
        listOf("UvirVersionInfoScreen.kt", "UvirSensorInfoDialog.kt", "UvirSettingsComponents.kt").forEach { name ->
            assertTrue(name, File(directory, name).readText().contains("UvirIslandColumns("))
        }
    }
}
