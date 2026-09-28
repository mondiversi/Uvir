package me.mondiversi.uvir

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

/** Renders the real four detail screens against isolated fixture data, without sensor commands. */
class UvirSessionNoteEditingUiTest : SessionNoteEditingFixture() {
    @get:Rule val compose = createComposeRule()

    private fun verifyScreen(screen: Int) {
        val alerts = screen >= 2
        if (alerts) database.startAlertSession(13L, "original", 900L, a)
        else database.startAcquisitionSession(12L, "original", 900L, a)
        val record = if (!alerts) database.readRecord(acquisition())!! else null
        val entry = if (alerts) alert() else null
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            CompositionLocalProvider(LocalContext provides context) {
                MaterialTheme {
                    when (screen) {
                        0 -> RecordDetailScreen(record!!, database, rememberLazyListState(),
                            Color.White, Color.White, Color.Black, Color.Gray, Color.LightGray, {}, {})
                        1 -> SessionChartScreen(12L, listOf(record!!), database,
                            Color.White, Color.White, Color.Black, Color.Gray,
                            onDeleteSession = {}, onOpenRecord = { _, _ -> }, onBack = {})
                        2 -> AlertChartScreen(entry!!, database, Color.White, Color.White,
                            Color.Black, Color.Gray, {}, {})
                        3 -> AlertSessionChartScreen(13L, listOf(entry!!), database,
                            Color.White, Color.White, Color.Black, Color.Gray, {}, {}, {})
                    }
                }
            }
        }
        val edit = compose.onNodeWithContentDescription(context.getString(R.string.edit_note))
        edit.performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithText(context.getString(R.string.edit_note)).assertDoesNotExist()
        compose.runOnIdle {
            if (alerts) database.finishAlertSession(13L) else database.finishAcquisitionSession(12L)
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(context.getString(R.string.edit_note))
                .filter(isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        edit.assertIsEnabled().performClick()
        compose.onNodeWithText(context.getString(R.string.edit_note)).assertIsDisplayed()
        // A session can restart after the editor opened. The dialog must close automatically.
        compose.runOnIdle {
            if (alerts) database.startAlertSession(13L, "original", 900L, a)
            else database.startAcquisitionSession(12L, "original", 900L, a)
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(context.getString(R.string.edit_note))
                .fetchSemanticsNodes().isEmpty()
        }
        edit.assertIsNotEnabled()
    }

    @Test fun acquisitionDetailDisablesPencilAndRechecksAnOpenEditor() = verifyScreen(0)
    @Test fun acquisitionSessionDisablesPencilAndRechecksAnOpenEditor() = verifyScreen(1)
    @Test fun alertDetailDisablesPencilAndRechecksAnOpenEditor() = verifyScreen(2)
    @Test fun alertSessionDisablesPencilAndRechecksAnOpenEditor() = verifyScreen(3)
}
