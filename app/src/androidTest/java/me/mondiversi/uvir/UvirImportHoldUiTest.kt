package me.mondiversi.uvir

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** Actual import dialogs, with inert callbacks and no files, settings or real records. */
class UvirImportHoldUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val page = mutableIntStateOf(0)
    private val night = mutableStateOf(false)
    private val rtl = mutableStateOf(false)
    private val busy = mutableStateOf(false)
    private val confirmations = AtomicInteger()
    private val label get() = formatUvirActionLabel(context.getString(R.string.data_management_import_button), context.resources.configuration.locales[0])
    private var purple = Color.Unspecified
    private var clearFocus: () -> Unit = {}
    private fun button() = compose.onNodeWithTag(if (page.intValue < 2 || page.intValue == 4) "encrypted_import_confirm" else "archive_import_confirm")

    private fun fixture() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KeepUvirVerificationScreenOn()
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                UvirActionTheme {
                    val colors = MaterialTheme.colorScheme
                    val focus = LocalFocusManager.current
                    val keyboard = LocalSoftwareKeyboardController.current
                    SideEffect { purple = colors.primary; clearFocus = { focus.clearFocus(); keyboard?.hide() } }
                    key(page.intValue, night.value, rtl.value) {
                        if (page.intValue < 2 || page.intValue == 4) UvirSettingsPasswordDialog(
                            title = context.getString(if (page.intValue == 0) R.string.settings_decryption_password_title else R.string.import_database_dialog_title),
                            description = context.getString(if (page.intValue == 0) R.string.settings_decryption_password_description else R.string.import_database_dialog_description),
                            confirmationRequired = false, confirmLabel = label,
                            cardColor = colors.surface, primaryText = colors.onSurface, secondaryText = colors.onSurfaceVariant,
                            passwordRequired = page.intValue != 4, busy = busy.value,
                            onDismiss = {}, onConfirm = { confirmations.incrementAndGet(); it.fill('\u0000') })
                        else UvirRecordArchiveImportDialog(12, 2, busy.value, colors.surface, colors.onSurface, colors.onSurfaceVariant,
                            onDismiss = {}, onImport = { confirmations.incrementAndGet() })
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun enterPassword() {
        if (page.intValue < 2) {
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("fixture-password")
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { clearFocus() }
            compose.mainClock.advanceTimeBy(300)
        }
    }

    @Test fun allFourImportsRequireFiveSecondsAndReleaseRestartsTheCountdownInBothThemesAndDirections() {
        fixture()
        for (dark in listOf(false, true)) for (direction in listOf(false, true)) for (current in 0..4) {
            compose.runOnIdle { night.value = dark; rtl.value = direction; page.intValue = current; busy.value = false }
            compose.mainClock.advanceTimeByFrame()
            enterPassword()
            if (current == 4) compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
            compose.onNodeWithText(context.getString(R.string.hold_action_seconds_confirmation, label, 5))
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(label, useUnmergedTree = true).assertUvirActionTextColor(purple)
            val baseline = confirmations.get()
            val action = button()
            action.performTouchInput { down(center) }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(2_200)
            compose.runOnIdle { assertEquals(baseline, confirmations.get()) }
            action.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(5_500)
            compose.runOnIdle { assertEquals(baseline, confirmations.get()) }
            action.performTouchInput { down(center) }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(4_800)
            compose.runOnIdle { assertEquals(baseline, confirmations.get()) }
            compose.mainClock.advanceTimeBy(400)
            compose.runOnIdle { assertEquals(baseline + 1, confirmations.get()) }
            compose.mainClock.advanceTimeBy(5_500)
            compose.runOnIdle { assertEquals(baseline + 1, confirmations.get()) }
            action.performTouchInput { up() }
        }
    }

    @Test fun progressUsesTheSamePurpleAsTheActionAndDisablingMidPressCancelsImport() {
        page.intValue = 2
        fixture()
        for (dark in listOf(false, true)) for (direction in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; rtl.value = direction; busy.value = false }
            compose.mainClock.advanceTimeByFrame()
            val action = button()
            val before = action.captureToImage().toPixelMap()
            action.performTouchInput { down(center) }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(2_500)
            val during = action.captureToImage().toPixelMap()
            var purplePixels = 0
            for (y in during.height / 5 until during.height * 4 / 5) {
                val xs = if (direction) during.width * 2 / 3 until during.width * 3 / 4 else during.width / 4 until during.width / 3
                for (x in xs) {
                    val expected = purple.copy(alpha = .12f).compositeOver(before[x, y])
                    val actual = during[x, y]
                    if (abs(expected.red - actual.red) < .015f && abs(expected.green - actual.green) < .015f &&
                        abs(expected.blue - actual.blue) < .015f && actual != before[x, y]) purplePixels++
                }
            }
            assertTrue("Purple hold progress", purplePixels > 30)
            if (!direction) File(context.getExternalFilesDir(null), "import-hold-${if (dark) "night" else "day"}.png")
                .outputStream().use { assertTrue(compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)) }
            compose.runOnIdle { busy.value = true }
            compose.mainClock.advanceTimeBy(6_000)
            action.assertIsNotEnabled()
            compose.runOnIdle { assertEquals(0, confirmations.get()) }
            action.performTouchInput { up() }
        }
    }

    @Test fun invalidPasswordCannotImportEvenAfterAFullHold() {
        fixture()
        button().performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(5_300)
        compose.runOnIdle { assertEquals(0, confirmations.get()) }
        button().performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(context.getString(R.string.settings_encryption_password_too_short,
            UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH)).assertExists()
    }
}
