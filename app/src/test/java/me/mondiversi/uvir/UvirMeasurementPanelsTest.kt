package me.mondiversi.uvir

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import java.io.File
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalFoundationApi::class)
class UvirMeasurementPanelsTest {
    private class RecordingScope : LazyListScope {
        val keys = mutableListOf<Any?>()
        var stickyCount = 0
        override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
            keys += key
        }
        override fun items(count: Int, key: ((Int) -> Any)?, contentType: (Int) -> Any?,
            itemContent: @Composable LazyItemScope.(Int) -> Unit) {
            repeat(count) { keys += key?.invoke(it) }
        }
        override fun stickyHeader(key: Any?, contentType: Any?, content: @Composable LazyItemScope.(Int) -> Unit) {
            stickyCount++
            keys += key
        }
    }

    private fun LazyListScope.page() {
        item("identity") {}
        item("context") {}
        stickyHeader("selector") {}
        items(24, key = { "record_$it" }) {}
        item("footer") {}
    }

    @Test fun splitOnlyAppliesToWideLandscapeWindows() {
        assertFalse(uvirMeasurementPanelsEnabled(false, 800f))
        assertFalse(uvirMeasurementPanelsEnabled(true, 479f))
        assertTrue(uvirMeasurementPanelsEnabled(true, 480f))
        assertTrue(uvirMeasurementPanelsEnabled(true, 720f))
        assertEquals(0.5f, UvirMeasurementHeaderWeight, 0f)
    }

    @Test fun reflowKeepsTopHeadersVisibleAndMapsScrolledValuesWithoutLosingTheOffset() {
        assertEquals(UvirMeasurementScrollAnchor(0, 0), uvirMeasurementReflowAnchor(1, 50, true, 3))
        assertEquals(UvirMeasurementScrollAnchor(0, 0), uvirMeasurementReflowAnchor(0, 0, false, 3))
        assertEquals(UvirMeasurementScrollAnchor(4, 25), uvirMeasurementReflowAnchor(7, 25, true, 3))
        assertEquals(UvirMeasurementScrollAnchor(7, 25), uvirMeasurementReflowAnchor(4, 25, false, 3))
        assertEquals(UvirMeasurementScrollAnchor(2, 10), uvirMeasurementReflowAnchor(4, 10, true, 2))
        assertEquals(UvirMeasurementScrollAnchor(4, 10), uvirMeasurementReflowAnchor(2, 10, false, 2))
    }

    @Test fun leftScrollingAreaOwnsMetadataOnly() {
        val recorded = RecordingScope()
        UvirMeasurementPaneScope(recorded, UvirMeasurementPane.METADATA).page()
        assertEquals(listOf("identity", "context"), recorded.keys)
        assertEquals(0, recorded.stickyCount)
    }

    @Test fun selectorIsEmittedOnceIntoTheRightHeaderAndIsNeverStickyThere() {
        val recorded = RecordingScope()
        UvirMeasurementPaneScope(recorded, UvirMeasurementPane.SELECTOR).page()
        assertEquals(listOf("selector"), recorded.keys)
        assertEquals(0, recorded.stickyCount)
    }

    @Test fun landscapeUsesEqualColumnsAndOneSharedBodyTopBelowTheTallestHeader() {
        val geometry = uvirMeasurementPanelGeometry(649, 360, 48, 46, 9, 4)
        assertEquals(320, geometry.leftWidth)
        assertEquals(320, geometry.rightWidth)
        assertEquals(329, geometry.rightLeft)
        assertEquals(48, geometry.headerHeight)
        assertEquals(52, geometry.bodyTop)
        assertEquals(308, geometry.bodyHeight)
        assertEquals(4f, UvirMeasurementHeaderGap.value, 0f)
    }

    @Test fun tallerVariantSelectorsKeepTheSameFixedGapWithoutOverlappingMetadata() {
        val geometry = uvirMeasurementPanelGeometry(649, 360, 48, 60, 9, 4)
        assertEquals(60, geometry.headerHeight)
        assertEquals(64, geometry.bodyTop)
        assertEquals(4, geometry.bodyTop - geometry.headerHeight)
        val tiny = uvirMeasurementPanelGeometry(5, 2, 48, 60, 9, 4)
        assertEquals(0, tiny.bodyHeight)
        assertEquals(2, tiny.bodyTop)
    }

    @Test fun rightPaneOwnsAllRecordsWithoutDuplicatingHeaderOrSelector() {
        val recorded = RecordingScope()
        UvirMeasurementPaneScope(recorded, UvirMeasurementPane.VALUES).page()
        assertEquals((0 until 24).map { "record_$it" } + "footer", recorded.keys)
        assertEquals(0, recorded.stickyCount)
    }

    @Test fun portraitUsesTheOriginalListAndStickySelector() {
        val recorded = RecordingScope()
        recorded.page()
        assertEquals(28, recorded.keys.size)
        assertEquals(1, recorded.stickyCount)
        assertEquals("selector", recorded.keys[2])
    }

    @Test fun aSecondBoundaryCannotSilentlyMoveValuesToTheWrongPane() {
        val scope = UvirMeasurementPaneScope(RecordingScope(), UvirMeasurementPane.VALUES)
        scope.stickyHeader("one") {}
        assertThrows(IllegalStateException::class.java) { scope.stickyHeader("two") {} }
    }

    @Test fun allFourSavedDetailsUsePanelsWhileHomeKeepsItsFullWidthChrome() {
        val directory = listOf(File("src/main/java/me/mondiversi/uvir"),
            File("app/src/main/java/me/mondiversi/uvir")).first { it.isDirectory }
        listOf("UvirRecordDetailScreen.kt", "UvirSessionChartScreen.kt",
            "UvirAlertChartScreen.kt", "UvirAlertSessionChartScreen.kt").forEach { name ->
            assertTrue(name, File(directory, name).readText().contains("UvirMeasurementScaffold("))
        }
        val live = File(directory, "UvirLiveScreen.kt").readText()
        assertFalse(live.contains("UvirMeasurementList("))
        assertTrue(live.contains("UvirLiveLandscapeIslands("))
        assertTrue(live.contains("homeMenuContent(homeMenuPinned)"))
        val islands = File(directory, "UvirLiveLandscapeIslands.kt").readText()
        assertTrue(islands.contains("order: List<SensorGroup> = UvirDefaultMainIslandOrder"))
        assertTrue(islands.contains("val column = index % 2"))
        assertTrue(live.contains("order = groups"))
        assertEquals(listOf(SensorGroup.UV, SensorGroup.NIR), UvirDefaultMainIslandOrder.filterIndexed { index, _ -> index % 2 == 0 })
        val motion = File(directory, "UvirExpansionMotion.kt").readText()
        assertTrue(motion.contains("if (LocalUvirMeasurementPanels.current) return false"))
    }

    @Test fun detailControlsNeverWrapIntoSeparateRowsInTheLandscapePane() {
        val directory = listOf(File("src/main/java/me/mondiversi/uvir"),
            File("app/src/main/java/me/mondiversi/uvir")).first { it.isDirectory }
        val controls = File(directory, "UvirMeasurementPanels.kt").readText()
            .substringAfter("internal fun UvirMeasurementControls(")
        assertFalse(controls.contains("Column("))
        assertFalse(controls.contains("LocalUvirMeasurementPanels.current"))
        assertTrue(controls.contains("leading(); middle(); trailing()"))
    }

    @Test fun landscapeAlertGlowUsesEachIslandsVisibleBoundsInsteadOfOneSharedLazyIndex() {
        val bounds = mapOf(
            SensorGroup.UV to UvirLiveIslandBounds(0f, 200f),
            SensorGroup.NIR to UvirLiveIslandBounds(215f, 400f),
            SensorGroup.VISIBLE to UvirLiveIslandBounds(0f, 800f)
        )
        val viewport = UvirLiveIslandBounds(0f, 500f)
        assertEquals(false to false, uvirLiveIslandEdgeGlow(listOf(SensorGroup.UV), bounds, viewport))
        assertEquals(false to true, uvirLiveIslandEdgeGlow(listOf(SensorGroup.VISIBLE), bounds, viewport))
        assertEquals(true to false, uvirLiveIslandEdgeGlow(listOf(SensorGroup.UV), bounds,
            UvirLiveIslandBounds(100f, 500f)))
        assertEquals(false to false, uvirLiveIslandEdgeGlow(emptyList(), bounds, viewport))
        assertEquals(false to false, uvirLiveIslandEdgeGlow(listOf(SensorGroup.UV), bounds, null))
    }
}
