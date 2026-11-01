package me.mondiversi.uvir

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Physical holds with inert callbacks: no real log is deleted. */
class UvirErrorLogClearHoldTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun clearingNeedsFiveSecondsAndUsesStandardDestructiveOrderInBothThemesAndDirections() {
        var dark by mutableStateOf(false)
        var rtl by mutableStateOf(false)
        var confirmations = 0
        var primary = Color.Unspecified
        var destructive = Color.Unspecified
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KeepUvirVerificationScreenOn()
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    UvirActionTheme {
                        val colors = MaterialTheme.colorScheme
                        primary = colors.primary
                        destructive = UvirDestructiveActionColor
                        key(dark, rtl) { UvirErrorLogClearDialog(colors.surface, colors.onSurface,
                            colors.onSurfaceVariant, false, {}, { confirmations++ }) }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val label = formatUvirActionLabel(context.getString(R.string.delete_all), Locale.getDefault())
        for (theme in listOf(false, true)) for (direction in listOf(false, true)) {
            compose.runOnIdle { dark = theme; rtl = direction }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText(context.getString(R.string.hold_action_seconds_confirmation, label, 5)).assertExists()
            compose.onNodeWithText(label, useUnmergedTree = true).assertUvirActionTextColor(destructive)
            compose.onNodeWithText(formatUvirActionLabel(context.getString(R.string.cancel), Locale.getDefault()),
                useUnmergedTree = true).assertUvirActionTextColor(primary)
            val action = compose.onNodeWithTag("error_log_clear_confirm")
            val bounds = action.fetchSemanticsNode().boundsInRoot
            val cancel = compose.onNodeWithTag("error_log_clear_cancel").fetchSemanticsNode().boundsInRoot
            assertTrue(if (direction) bounds.left >= cancel.right else bounds.right <= cancel.left)
            val baseline = confirmations
            action.performTouchInput { down(center) }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(4_800)
            compose.runOnIdle { assertEquals(baseline, confirmations) }
            action.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(5_500)
            compose.runOnIdle { assertEquals(baseline, confirmations) }
            action.performTouchInput { down(center) }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(2_500)
            if (!direction) File(context.getExternalFilesDir(null), "error-log-clear-${if(theme) "night" else "day"}.png")
                .outputStream().use { compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            compose.mainClock.advanceTimeBy(2_700)
            compose.runOnIdle { assertEquals(baseline + 1, confirmations) }
            compose.mainClock.advanceTimeBy(5_500)
            compose.runOnIdle { assertEquals(baseline + 1, confirmations) }
            action.performTouchInput { up() }
        }
    }
}
