package me.mondiversi.uvir

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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UvirSensorSimulationSettingsContentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun diagnosticSwitchesRemainUsableWithRunningActivitiesAndKeepIndependentSensorChoices() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fakeTitle = context.getString(R.string.debug_fake_data)
        val olTitle = context.getString(R.string.debug_fake_out_of_range)
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
                    var selected by remember { mutableStateOf("A") }
                    var choices by remember { mutableStateOf(mapOf(
                        "A" to UvirSensorSimulationSettings(), "B" to UvirSensorSimulationSettings())) }
                    val current = choices.getValue(selected)
                    val colors = MaterialTheme.colorScheme
                    Column {
                        Text("Acquisition and alert jobs stay active")
                        androidx.compose.material3.TextButton(onClick = {
                            selected = if (selected == "A") "B" else "A"
                        }) { Text("Switch sensor") }
                        Text("Selected: $selected")
                        UvirSensorSimulationSettingsContent(
                            enabled = current.enabled,
                            outOfRange = current.outOfRange,
                            onEnabledChange = { choices = choices + (selected to current.copy(enabled = it)) },
                            onOutOfRangeChange = { choices = choices + (selected to current.copy(outOfRange = it)) },
                            primaryText = colors.onSurface,
                            secondaryText = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
        // Labels belong to the clickable row; toggle state belongs to its checkbox.
        fun fake() = compose.onAllNodes(isToggleable())[0]
        fun ol() = compose.onAllNodes(isToggleable())[1]
        for (night in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; rtl.value = rightToLeft }
            compose.onNodeWithText(fakeTitle).assertIsDisplayed()
            compose.onNodeWithText(olTitle).assertIsDisplayed()
            fake().assertIsEnabled().assertIsOff().performClick()
            ol().assertIsEnabled().assertIsOff().performClick()
            ol().assertIsOn()
            compose.onNodeWithText("Switch sensor").performClick()
            compose.onNodeWithText("Selected: B").assertIsDisplayed()
            fake().assertIsOff()
            ol().assertIsNotEnabled()
            compose.onNodeWithText("Switch sensor").performClick()
            fake().assertIsOn()
            ol().assertIsOn().performClick()
            fake().performClick().assertIsOff()
            compose.onNodeWithText("Acquisition and alert jobs stay active").assertIsDisplayed()
        }
    }
}
