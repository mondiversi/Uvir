package me.mondiversi.uvir

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real screens and navigation, using only a disposable private archive. */
class UvirSavedPresentationNavigationTest : SessionNoteEditingFixture() {
    @get:Rule val compose = createComposeRule()

    @Test fun childNavigationAndReopeningPreserveChoicesButANewAppSessionResetsThem() {
        database.startAcquisitionSession(12L, "original", 900L, a)
        val records = List(2) { database.readRecord(acquisition())!! }
        database.finishAcquisitionSession(12L, 2_000L)
        assertTrue(database.updateAcquisitionSessionVariantsPerPosition(12L, 2))
        val page = mutableStateOf("session")
        val store = mutableStateOf(UvirSavedPresentationStore())
        var navigation: SessionChartNavigationState? = null
        var selected = records.first()
        compose.setContent {
            KeepUvirVerificationScreenOn()
            CompositionLocalProvider(LocalContext provides context,
                LocalUvirSavedPresentationStore provides store.value) {
                MaterialTheme {
                    UvirActionTheme {
                        val colors = MaterialTheme.colorScheme
                        when (page.value) {
                            "session" -> SessionChartScreen(12L, records, database,
                                colors.background, colors.surface, colors.onSurface, colors.onSurfaceVariant,
                                initialNavigationState = navigation, onDeleteSession = {},
                                onOpenRecord = { record, state ->
                                    selected = record; navigation = state; page.value = "record"
                                }, onBack = { page.value = "list" })
                            "record" -> RecordDetailScreen(selected, database, rememberLazyListState(),
                                colors.background, colors.surface, colors.onSurface, colors.onSurfaceVariant,
                                colors.outline, onBack = { page.value = "session" }, onDeleted = {})
                        }
                    }
                }
            }
        }
        val id = records.first().id
        val uvb = "saved_row_acquisition_${id}_UVB"
        val uva = "saved_row_acquisition_${id}_UVA"
        scrollTo(uvb).performClick().assertIsOff()
        scrollTo("saved_percentage_base_UV").performClick()
        scrollTo("session_record_header_acquisition_UV_$id").performClick()
        scrollTo(uvb).assertIsOn() // A child has its own presentation state.
        scrollTo(uva).performClick().assertIsOff()
        compose.onNodeWithContentDescription(context.getString(R.string.navigate_back)).performClick()
        scrollTo(uvb).assertIsOff()
        scrollTo(uva).assertIsOn()
        scrollTo("saved_percentage_base_UV").assertTextEquals(context.getString(R.string.live_percent_group))

        // Return to the same child, and then to the session list, without closing the app.
        scrollTo("session_record_header_acquisition_UV_$id").performClick()
        scrollTo(uva).assertIsOff()
        scrollTo("chart_group_title_UV").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.navigate_back)).performClick()
        scrollTo("chart_group_title_UV").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.navigate_back)).performClick()
        compose.runOnIdle { page.value = "session"; navigation = null }
        scrollTo("chart_group_title_UV").performClick() // It was still compacted.
        scrollTo(uvb).assertIsOff()
        scrollTo("saved_percentage_base_UV").assertTextEquals(context.getString(R.string.live_percent_group))
        scrollTo("session_record_header_acquisition_UV_$id").performClick()
        compose.runOnIdle {
            assertEquals(false, store.value.screen("acquisition:$id").expandedGroups[SessionChartGroup.UV])
        }
        scrollTo("chart_group_title_UV").performClick()
        scrollTo(uva).assertIsOff()

        // A new activity session starts with defaults; the durable variants and raw data survive.
        compose.runOnIdle { page.value = "list" }
        compose.runOnIdle { store.value = UvirSavedPresentationStore(); page.value = "session"; navigation = null }
        scrollTo(uvb).assertIsOn()
        scrollTo("saved_percentage_base_UV").assertTextEquals(context.getString(R.string.live_percent_total))
        scrollTo("session_record_header_acquisition_UV_$id").performClick()
        scrollTo(uva).assertIsOn()
        assertEquals(2, database.readAcquisitionSessionVariantsPerPosition(12L))
        records.forEach { assertEquals(it.sample, database.readRecord(it.id)!!.sample) }
    }

    private fun scrollTo(tag: String): SemanticsNodeInteraction {
        // Single-record headers deliberately merge their decorative title for accessibility.
        val matcher = if (tag == "chart_group_title_UV")
            hasContentDescription(context.getString(R.string.uv_radiation)) and hasClickAction()
        else hasTestTag(tag)
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(matcher)
        return compose.onNode(matcher, useUnmergedTree = true).performScrollTo()
    }
}
