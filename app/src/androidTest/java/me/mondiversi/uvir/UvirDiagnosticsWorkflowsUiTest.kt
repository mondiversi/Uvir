package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Uses the disposable verification app only; never opens USB or records measurements. */
class UvirDiagnosticsWorkflowsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun diagnosticsOwnsTheLiveLogAndDataManagementOnlyHasOneArchivePair() {
        assertTrue(context.packageName.endsWith(".verification"))
        UvirErrorLog.clear(context)
        val runtime = UvirUsbPhoneRuntime(context)
        var night by mutableStateOf(false)
        var page by mutableStateOf(UvirSettingsPage.DEBUG)
        var state by mutableStateOf(UsbPhoneState())
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn; view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme(colorScheme = if (night) darkColorScheme() else lightColorScheme()) {
                    UvirActionTheme {
                        val colors = MaterialTheme.colorScheme
                        UsbPhoneSettings(runtime, state, page, colors.background, colors.surface,
                            colors.onSurface, colors.onSurfaceVariant,
                            UvirIrradianceUnit.W_M2, {}, UvirNumericFormat.entries.first(), {},
                            UvirDateFormat.entries.first(), {}, UvirTimeFormat.entries.first(), {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.diagnostic_title)).assertExists()
        val open = compose.onNodeWithContentDescription(context.getString(R.string.error_log_open))
        open.performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil { compose.onAllNodesWithTag("error_log_empty").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("error_log_export").assertIsNotEnabled()
        compose.onNodeWithTag("error_log_clear").assertIsNotEnabled()
        compose.runOnIdle { UvirErrorLog.record(context, "ui-fixture", "OLDER_ERROR_MARKER") }
        compose.waitUntil { compose.onAllNodesWithText("OLDER_ERROR_MARKER", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { UvirErrorLog.record(context, "ui-fixture", "NEWER_ERROR_MARKER") }
        compose.waitUntil { compose.onAllNodesWithText("NEWER_ERROR_MARKER", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val newer = compose.onNodeWithText("NEWER_ERROR_MARKER", substring = true).fetchSemanticsNode().boundsInRoot
        val older = compose.onNodeWithText("OLDER_ERROR_MARKER", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue(newer.top < older.top)
        val originalLog = UvirErrorLog.read(context)
        val exportedLog = requireNotNull(UvirErrorLog.createExport(context)).readText()
        assertTrue(exportedLog.contains("OLDER_ERROR_MARKER") && exportedLog.contains("NEWER_ERROR_MARKER"))
        assertEquals(originalLog, UvirErrorLog.read(context))
        for (theme in listOf(false, true)) {
            compose.runOnIdle { night = theme }
            compose.onNodeWithTag("error_log_export").assertIsEnabled().performClick()
            compose.onNodeWithText(context.getString(R.string.export_error_log_dialog_title)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.export_file_count, "TXT", 1)).assertIsDisplayed()
            for (label in listOf(R.string.cancel, R.string.save, R.string.share))
                compose.onNodeWithText(formatUvirActionLabel(context.getString(label), Locale.getDefault()), useUnmergedTree = true).assertIsEnabled()
            File(context.getExternalFilesDir(null), "error-log-export-${if(theme) "night" else "day"}.png").outputStream().use {
                compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithTag("phone_backup_export_cancel").performClick()
            File(context.getExternalFilesDir(null), "error-log-view-${if(theme) "night" else "day"}.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        val exportBounds = compose.onNodeWithTag("error_log_export").fetchSemanticsNode().boundsInRoot
        val deleteBounds = compose.onNodeWithTag("error_log_clear").fetchSemanticsNode().boundsInRoot
        assertTrue(exportBounds.right <= deleteBounds.left)
        compose.onNodeWithTag("error_log_clear").performClick()
        compose.onNodeWithTag("error_log_clear_cancel").performClick()
        assertEquals(originalLog, UvirErrorLog.read(context))
        compose.onNodeWithTag("error_log_clear").performClick()
        val clear = compose.onNodeWithTag("error_log_clear_confirm")
        clear.performSemanticsAction(SemanticsActions.OnClick) { it() }
        clear.performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil { compose.onAllNodesWithTag("error_log_empty").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("", UvirErrorLog.read(context))
        compose.onNodeWithTag("error_log_clear").assertIsNotEnabled()
        compose.onNodeWithTag("error_log_export").assertIsNotEnabled()
        compose.runOnIdle { UvirErrorLog.record(context, "ui-fixture", "AFTER_CLEAR_MARKER") }
        compose.waitUntil { compose.onAllNodesWithText("AFTER_CLEAR_MARKER", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("error_log_clear").assertIsEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.navigate_back)).performClick()
        open.performScrollTo().assertExists()
        compose.onNodeWithTag("usb_saturation_toggle").performScrollTo()
        compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("usb_saturation_toggle"))).assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(simulation = true) }
        compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("usb_saturation_toggle"))).assertIsEnabled()
        compose.onNodeWithTag("usb_out_of_range_toggle").assertDoesNotExist()
        compose.runOnIdle { page = UvirSettingsPage.DATA_RESTORE }
        compose.onNodeWithContentDescription(context.getString(R.string.error_log_open)).assertDoesNotExist()
        compose.onNodeWithTag("archive_export_all").assertExists()
        compose.onNodeWithTag("archive_import_all").assertExists()
        UvirErrorLog.clear(context)
    }
}
