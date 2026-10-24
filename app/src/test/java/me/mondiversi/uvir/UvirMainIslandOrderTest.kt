package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirMainIslandOrderTest {
    @Test fun firstInstallAndInvalidPreferencesKeepTheExistingDefaultOrder() {
        for (stored in listOf(null, "", "unknown,BIOLOGICAL,uv")) {
            assertEquals(listOf(SensorGroup.UV, SensorGroup.VISIBLE, SensorGroup.NIR), decodeUvirMainIslandOrder(stored))
        }
    }

    @Test fun restoredOrderIgnoresDuplicatesAndAppendsMissingIslands() {
        assertEquals(listOf(SensorGroup.NIR, SensorGroup.UV, SensorGroup.VISIBLE),
            decodeUvirMainIslandOrder(" NIR,UV,NIR,unknown,BIOLOGICAL"))
        assertEquals(listOf(SensorGroup.VISIBLE, SensorGroup.UV, SensorGroup.NIR), decodeUvirMainIslandOrder("VISIBLE"))
    }

    @Test fun movingUvToTheBottomAndBackPreservesAllThreeIslandsExactlyOnce() {
        val first = moveUvirMainIsland(UvirDefaultMainIslandOrder, SensorGroup.UV, 1)
        assertEquals(listOf(SensorGroup.VISIBLE, SensorGroup.UV, SensorGroup.NIR), first)
        val bottom = moveUvirMainIsland(first, SensorGroup.UV, 1)
        assertEquals(listOf(SensorGroup.VISIBLE, SensorGroup.NIR, SensorGroup.UV), bottom)
        assertEquals(bottom, decodeUvirMainIslandOrder(bottom.joinToString(",") { it.name }))
        assertEquals(UvirDefaultMainIslandOrder,
            moveUvirMainIsland(moveUvirMainIsland(bottom, SensorGroup.UV, -1), SensorGroup.UV, -1))
    }

    @Test fun boundaryMovesNeverWrapAndInvalidActionsCannotChangeTheOrder() {
        for ((group, step) in listOf(SensorGroup.UV to -1, SensorGroup.NIR to 1,
            SensorGroup.VISIBLE to 0, SensorGroup.VISIBLE to 2, SensorGroup.BIOLOGICAL to 1)) {
            assertEquals(UvirDefaultMainIslandOrder, moveUvirMainIsland(UvirDefaultMainIslandOrder, group, step))
        }
    }

    @Test fun reorderingIsOnlyAnAppPreferenceAndNeverSentToTheSensor() {
        assertTrue(isTransferableAppPreference(KEY_MAIN_ISLAND_ORDER))
        assertFalse(KEY_MAIN_ISLAND_ORDER in sensorSelectionPreferenceKeys)
        assertFalse(KEY_MAIN_ISLAND_ORDER in sensorConfigurationPreferenceKeys)
        assertFalse(KEY_MAIN_ISLAND_ORDER in sensorOperationalPreferenceKeys)
    }
}
