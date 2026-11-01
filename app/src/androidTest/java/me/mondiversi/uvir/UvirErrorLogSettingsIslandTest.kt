package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UvirErrorLogSettingsIslandTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sizeSummaryTracksUtf8BytesAndClearingInBothThemesAndNumberFormats() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".verification"))
        assertTrue(UvirErrorLog.clear(context))
        var night by mutableStateOf(false)
        var format by mutableStateOf(UvirNumericFormat.EUROPEAN)
        var secondary = Color.Unspecified
        compose.setContent {
            KeepUvirVerificationScreenOn()
            CompositionLocalProvider(LocalUvirNumericFormat provides format) {
                MaterialTheme(colorScheme = if (night) darkColorScheme() else lightColorScheme()) {
                    secondary = MaterialTheme.colorScheme.onSurfaceVariant
                    UvirErrorLogSettingsIsland(MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.onSurface, secondary, onOpen = {})
                }
            }
        }
        fun expected(bytes: Long) = context.getString(R.string.error_log_size,
            context.getString(R.string.sensor_info_kilobytes,
                formatUvirNumber(bytes / 1024.0, 1, format, grouping = false, minimumDigits = 0)))
        fun awaitSize(bytes: Long) {
            val label = expected(bytes)
            compose.waitUntil { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
        }
        try {
            for (dark in listOf(false, true)) for (numberFormat in listOf(UvirNumericFormat.EUROPEAN, UvirNumericFormat.INTERNATIONAL)) {
                compose.runOnIdle { night = dark; format = numberFormat }
                awaitSize(0)
                compose.runOnIdle { UvirErrorLog.record(context, "size-fixture", "é漢☀".repeat(800)) }
                val bytes = File(context.filesDir, "uvir_error_log.txt").length()
                assertTrue("Measure UTF-8 bytes, not character count", bytes > UvirErrorLog.read(context).length)
                awaitSize(bytes)
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithTag("error_log_size").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(11.sp, layouts.single().layoutInput.style.fontSize)
                assertEquals(14.sp, layouts.single().layoutInput.style.lineHeight)
                assertEquals(secondary, layouts.single().layoutInput.style.color)
                val button = compose.onNodeWithContentDescription(context.getString(R.string.error_log_open)).fetchSemanticsNode().boundsInRoot
                val summary = compose.onNodeWithTag("error_log_size").fetchSemanticsNode().boundsInRoot
                assertTrue(button.bottom <= summary.top)
                assertTrue(UvirErrorLog.clear(context))
                awaitSize(0)
            }
        } finally { UvirErrorLog.clear(context) }
    }

    @Test
    fun islandKeepsSaveAndShareFlowInBothThemesAndLayoutDirections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val export = context.getString(R.string.error_log_open)
        val title = context.getString(R.string.export_error_log_dialog_title)
        val count = context.getString(R.string.export_file_count, "TXT", 1)
        val destinations = mutableListOf<UvirExportDestination>()
        val dark = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalLayoutDirection provides
                    if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    val view = LocalView.current
                    DisposableEffect(view) {
                        val previous = view.keepScreenOn
                        view.keepScreenOn = true
                        onDispose { view.keepScreenOn = previous }
                    }
                    var dialog by remember { mutableStateOf(false) }
                    val colors = MaterialTheme.colorScheme
                    Column {
                        Text("Database marker")
                        UvirErrorLogSettingsIsland(
                            colors.surface, colors.onSurface, colors.onSurfaceVariant,
                            onOpen = { dialog = true }
                        )
                        Text("Reset marker")
                    }
                    if (dialog) UvirSaveOrShareDialog(
                        cardColor = colors.surface,
                        primaryText = colors.onSurface,
                        secondaryText = colors.onSurfaceVariant,
                        title = stringResource(R.string.export_error_log_dialog_title),
                        description = stringResource(R.string.export_error_log_dialog_description),
                        fileFormat = UvirExportFileFormat.TXT,
                        onDismiss = { dialog = false },
                        onExport = { destinations += it; dialog = false }
                    )
                }
            }
        }
        for (night in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; rtl.value = rightToLeft }
            val island = compose.onNodeWithContentDescription(export).fetchSemanticsNode().boundsInRoot
            val before = compose.onNodeWithText("Database marker").fetchSemanticsNode().boundsInRoot
            val after = compose.onNodeWithText("Reset marker").fetchSemanticsNode().boundsInRoot
            assertTrue(before.bottom <= island.top && island.bottom <= after.top)
            for ((label, destination) in listOf(
                R.string.save to UvirExportDestination.SAVE,
                R.string.share to UvirExportDestination.SHARE
            )) {
                compose.onNodeWithContentDescription(export).performClick()
                compose.onNodeWithText(title).assertIsDisplayed()
                compose.onNodeWithText(count).assertIsDisplayed()
                compose.onNodeWithText(context.getString(R.string.save)).assertIsEnabled()
                compose.onNodeWithText(context.getString(R.string.share)).assertIsEnabled()
                compose.onNodeWithText(context.getString(label)).performClick()
                compose.waitForIdle()
                compose.onNodeWithText(title).assertDoesNotExist()
                compose.runOnIdle { assertEquals(destination, destinations.last()) }
            }
        }
        assertEquals(8, destinations.size)
    }

    @Test
    fun errorLogIslandHasTranslationsForEverySupportedLanguage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }).getString(R.string.data_management_error_log_group)
        for (language in listOf("en", "it", "ar", "de", "el", "es", "fa", "fr", "hi",
            "he", "ja", "ko", "pt", "ru", "sw", "pl", "nl", "id", "tr", "zh")) {
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            })
            val group = localized.getString(R.string.data_management_error_log_group)
            assertTrue(group.isNotBlank())
            assertTrue(localized.getString(R.string.data_management_error_log_description).isNotBlank())
            if (language != "en") assertNotEquals("Missing translation: $language", english, group)
        }
    }
}
