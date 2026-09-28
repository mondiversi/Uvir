package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

/** No manual scroll state, height limit or scrollbar configuration at the popup call site. */
class UvirDefaultDialogScrollTest {
    @get:Rule val compose = createComposeRule()

    @Test fun defaultPopupAutomaticallyScrollsOnOverflowAndKeepsTitleAndActionsFixed() {
        val long = mutableStateOf(false)
        val compact = mutableStateOf(false)
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        val largeText = mutableStateOf(false)
        val epoch = mutableIntStateOf(0)
        var density = 1f
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            val configuration = Configuration(LocalConfiguration.current)
            if (compact.value) configuration.screenHeightDp = 320
            val deviceDensity = LocalDensity.current
            density = deviceDensity.density
            MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(
                    LocalConfiguration provides configuration,
                    LocalDensity provides Density(density, if (largeText.value) 1.6f else 1f),
                    LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    key(epoch.intValue) {
                        UvirAlertDialog(
                            onDismissRequest = {},
                            modifier = Modifier.testTag("default_dialog"),
                            title = { Text("Popup") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(UvirSettingsControlGap)) {
                                    repeat(if (long.value) 40 else 1) { Text("Body ${it + 1}") }
                                }
                            },
                            confirmButton = { TextButton({}) { Text("Action") } },
                            textContentColor = Color.Magenta
                        )
                    }
                }
            }
        }
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true))
            for (short in listOf(false, true)) for (large in listOf(false, true)) {
                compose.runOnIdle {
                    night.value = dark; rtl.value = rightToLeft; compact.value = short
                    largeText.value = large; long.value = false; epoch.intValue++
                }
                val scroll = compose.onNodeWithTag("uvir_dialog_scroll")
                assertEquals(0f, scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue())
                assertFalse(hasThumb(density))
                compose.runOnIdle { long.value = true }
                assertTrue(scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 2f * density)
                assertTrue(hasThumb(density))
                val title = compose.onNodeWithText("Popup").getUnclippedBoundsInRoot()
                val action = compose.onNodeWithText("Action").getUnclippedBoundsInRoot()
                scroll.performTouchInput { swipeUp() }
                assertTrue(scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
                compose.onNodeWithText("Body 40").performScrollTo().assertIsDisplayed()
                assertEquals(title, compose.onNodeWithText("Popup").getUnclippedBoundsInRoot())
                assertEquals(action, compose.onNodeWithText("Action").getUnclippedBoundsInRoot())
                compose.onNodeWithText("Action").assertIsDisplayed()
                compose.runOnIdle { long.value = false }
                assertFalse(hasThumb(density))
            }
    }

    private fun hasThumb(density: Float): Boolean {
        val dialog = compose.onNodeWithTag("default_dialog")
        val pixels = dialog.captureToImage().toPixelMap()
        val x = (pixels.width - 3.5f * density).roundToInt().coerceIn(0, pixels.width - 1)
        return (0 until pixels.height).any { y ->
            val color = pixels[x, y]
            color.red > color.green + 0.15f && color.blue > color.green + 0.15f
        }
    }
}
