package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Isolated dialog geometry: no preferences, sensor commands or archive records. */
class UvirDialogPaddingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allScreenScalesKeepOriginalInsetsInsideFullWidthDialogs() {
        val width = mutableIntStateOf(400)
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        val actions = mutableStateOf(false)
        var expectedHorizontalInset = 0f
        var expectedVerticalInset = 0f
        var expectedTitleGap = 0f
        var expectedActionGap = 0f
        var expectedButtonHeight = 0f
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            val configuration = Configuration(LocalConfiguration.current).apply {
                smallestScreenWidthDp = width.intValue
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                UvirAdaptiveDisplay {
                    val density = LocalDensity.current
                    SideEffect {
                        expectedHorizontalInset = with(density) { 24.dp.toPx() }
                        expectedVerticalInset = with(density) { 24.dp.toPx() }
                        expectedTitleGap = with(density) { 16.dp.toPx() }
                        expectedActionGap = with(density) { 24.dp.toPx() }
                        expectedButtonHeight = with(density) { 48.dp.toPx() }
                    }
                    MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                        Box(Modifier.fillMaxSize().testTag("padding_host"))
                        UvirAlertDialog(
                            onDismissRequest = {},
                            modifier = Modifier.testTag("padding_dialog"),
                            title = { Box(Modifier.fillMaxWidth().height(36.dp).testTag("padding_title")) },
                            text = { Box(Modifier.fillMaxWidth().height(16.dp).testTag("padding_body")) },
                            fixedBottomContent = {
                                Box(Modifier.fillMaxWidth().height(16.dp).testTag("padding_footer"))
                            },
                            confirmButton = if (actions.value) {
                                { TextButton({}, Modifier.height(48.dp).testTag("padding_action")) { Text("OK") } }
                            } else null
                        )
                    }
                }
            }
        }
        for (screenWidth in listOf(400, 800, 1000)) for (dark in listOf(false, true))
            for (rightToLeft in listOf(false, true)) for (hasActions in listOf(false, true)) {
                compose.runOnIdle {
                    width.intValue = screenWidth; night.value = dark
                    rtl.value = rightToLeft; actions.value = hasActions
                }
                val dialog = bounds("padding_dialog")
                val title = bounds("padding_title")
                val body = bounds("padding_body")
                val footer = bounds("padding_footer")
                val case = "screen=$screenWidth, night=$dark, rtl=$rightToLeft, actions=$hasActions"
                val host = compose.onNodeWithTag("padding_host").fetchSemanticsNode().boundsInRoot
                assertEquals("No external side margins: $case", host.width, dialog.width, 1f)
                assertEquals("Top inset: $case", expectedVerticalInset, title.top - dialog.top, 1f)
                assertEquals("Left inset: $case", expectedHorizontalInset, body.left - dialog.left, 1f)
                assertEquals("Right inset: $case", expectedHorizontalInset, dialog.right - body.right, 1f)
                assertEquals("Title/body spacing unchanged: $case", expectedTitleGap, body.top - title.bottom, 1f)
                if (hasActions) {
                    val action = bounds("padding_action")
                    assertEquals("Bottom inset: $case", expectedVerticalInset, dialog.bottom - action.bottom, 1f)
                    assertEquals("Action spacing unchanged: $case", expectedActionGap, action.top - footer.bottom, 1f)
                    assertEquals("Button height unchanged: $case", expectedButtonHeight, action.height, 1f)
                    assertEquals("Actions still end-aligned: $case", expectedHorizontalInset,
                        if (rightToLeft) action.left - dialog.left else dialog.right - action.right, 1f)
                } else {
                    assertEquals("Bottom inset without actions: $case", expectedVerticalInset, dialog.bottom - footer.bottom, 1f)
                }
            }
    }

    @Test fun optionalIconKeepsTheOriginalTopInset() {
        var expectedInset = 0f
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            val density = LocalDensity.current
            SideEffect { expectedInset = with(density) { 24.dp.toPx() } }
            MaterialTheme {
                UvirAlertDialog(
                    onDismissRequest = {}, confirmButton = null,
                    modifier = Modifier.testTag("icon_dialog"),
                    icon = { Box(Modifier.size(24.dp).testTag("padding_icon")) },
                    title = { Text("Popup") }, text = { Text("Body") }
                )
            }
        }
        val dialog = bounds("icon_dialog")
        val icon = bounds("padding_icon")
        assertEquals(expectedInset, icon.top - dialog.top, 1f)
        assertEquals(dialog.center.x, icon.center.x, 1f)
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag)
        .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
}
