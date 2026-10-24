package me.mondiversi.uvir

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@Composable
internal fun KeepUvirVerificationScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
}

internal fun SemanticsNodeInteraction.assertUvirActionTextColor(expected: Color) {
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    assertEquals("Rendered action caption color", expected, layouts.single().layoutInput.style.color)
}

/** Pure presentation callbacks: no archives, real exports, sensors or updater jobs. */
class UvirPopupActionsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resources = context.resources
    private val open = mutableStateOf(true)
    private val dark = mutableStateOf(false)
    private val page = mutableStateOf(0)
    private var exports = 0
    private var expectedCloseColor = Color.Unspecified

    private fun fixture() {
        compose.setContent {
            KeepUvirVerificationScreenOn()
            val configuration = uvirThemeConfiguration(LocalConfiguration.current,
                if (dark.value) UvirAppTheme.DARK else UvirAppTheme.LIGHT)
            CompositionLocalProvider(LocalConfiguration provides configuration) {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                val colors = MaterialTheme.colorScheme
                val closeColor = colors.primary
                SideEffect { expectedCloseColor = closeColor }
                if (open.value) when (page.value) {
                    0 -> UvirSaveOrShareDialog(colors.surface, colors.onSurface, colors.onSurfaceVariant,
                        "Export", "Preview", UvirExportFileFormat.TXT,
                        onDismiss = { open.value = false }, onExport = { exports++ })
                    1 -> MeasurementShareFormatDialog(colors.surface, colors.onSurface, colors.onSurfaceVariant,
                        onDismiss = { open.value = false }, onFormatSelected = { _, _ -> exports++ })
                    2 -> UvirEncryptedDatabaseExportDialog(colors.surface, colors.onSurface, colors.onSurfaceVariant,
                        onDismiss = { open.value = false }, onExport = { _, _ -> exports++ })
                    3 -> UvirSettingsExportDialog(colors.surface, colors.onSurface, colors.onSurfaceVariant,
                        emptyList(), onDismiss = { open.value = false },
                        onExport = { _, _, _, _ -> exports++ })
                    4 -> UvirSettingsPasswordDialog("Import", "Preview", false, "Importa",
                        colors.surface, colors.onSurface, colors.onSurfaceVariant,
                        onDismiss = { open.value = false }, onConfirm = { exports++ })
                }
            }
            }
        }
    }

    @Test fun exportPopupsHavePurpleCloseBeforeActionsInBothThemesWithoutExporting() {
        fixture()
        val tags = listOf("export_dialog_close", "export_format_close", "database_export_close", "settings_export_close")
        for (night in listOf(false, true)) for ((index, tag) in tags.withIndex()) {
            compose.runOnIdle { dark.value = night; page.value = index; open.value = true }
            compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithText(resources.getString(R.string.close), useUnmergedTree = true)
                .assertUvirActionTextColor(expectedCloseColor)
            compose.onNodeWithText(resources.getString(R.string.cancel)).assertDoesNotExist()
            val close = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            val save = compose.onNodeWithText(resources.getString(R.string.save)).getUnclippedBoundsInRoot()
            assertTrue("Close precedes save", close.left < save.left || close.top < save.top)
            if (index == 0) {
                File(context.getExternalFilesDir(null), "popup-export-${if (night) "night" else "day"}.png").outputStream().use {
                    assertTrue(compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
            compose.onNodeWithTag(tag).performClick()
            compose.runOnIdle { assertFalse(open.value); assertEquals(0, exports) }
        }
    }

    @Test fun importConfirmationIsUppercaseWithoutChangingTheTitle() {
        page.value = 4
        fixture()
        compose.onNodeWithText("IMPORTA", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Import").assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.cancel)).performClick()
        assertEquals(0, exports)
    }

    @Test fun databaseExportCannotBeDismissedWhileItsCallbackIsBusy() {
        val gate = CompletableDeferred<Unit>()
        var passwordCopy: CharArray? = null
        compose.setContent {
            KeepUvirVerificationScreenOn()
            MaterialTheme {
                val colors = MaterialTheme.colorScheme
                if (open.value) UvirEncryptedDatabaseExportDialog(colors.surface, colors.onSurface, colors.onSurfaceVariant,
                    onDismiss = { open.value = false }, onExport = { password, _ ->
                        passwordCopy = password
                        gate.await()
                    })
            }
        }
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("test-only-password")
        compose.onNodeWithText(resources.getString(R.string.save)).assertIsEnabled().performClick()
        compose.waitUntil(2_000) { passwordCopy != null }
        compose.onNodeWithTag("database_export_close").assertIsNotEnabled()
        compose.onNodeWithText(resources.getString(R.string.save)).assertIsNotEnabled()
        compose.runOnIdle { gate.complete(Unit) }
        compose.onNodeWithTag("database_export_close").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertFalse(open.value)
            assertTrue(passwordCopy!!.all { it == '\u0000' })
        }
    }

    @Test fun everyLanguageUppercasesOnlyActionsAndKeepsPopupTitlesInTheirOriginalCase() {
        val tag = mutableStateOf("it")
        compose.setContent {
            KeepUvirVerificationScreenOn()
            val configuration = Configuration(LocalConfiguration.current).apply {
                setLocales(LocaleList.forLanguageTags(tag.value))
            }
            val localized = context.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalContext provides localized, LocalResources provides localized.resources) {
                MaterialTheme {
                    UvirAlertDialog(onDismissRequest = {}, title = {
                        Text(androidx.compose.ui.res.stringResource(R.string.dark_offsets_calculate))
                    }, text = {}, confirmButton = {
                        TextButton(onClick = {}) { Text(uvirActionLabel(R.string.dark_offsets_start)) }
                        TextButton(onClick = {}) { Text(uvirActionLabel(R.string.dark_offsets_apply)) }
                    })
                }
            }
        }
        for (language in listOf("en","it","es","fr","pt","de","ar","he","fa","ru","tr","el","hi","ja","zh-CN","ko-KR","sw","pl","nl","id")) {
            compose.runOnIdle { tag.value = language }
            val configuration = Configuration(resources.configuration).apply { setLocales(LocaleList.forLanguageTags(language)) }
            val translated = context.createConfigurationContext(configuration).resources
            compose.onNodeWithText(translated.getString(R.string.dark_offsets_calculate)).assertIsDisplayed()
            for (id in listOf(R.string.dark_offsets_start, R.string.dark_offsets_apply)) {
                compose.onNodeWithText(formatUvirActionLabel(translated.getString(id), configuration.locales[0])).assertIsDisplayed()
            }
        }
    }
}
