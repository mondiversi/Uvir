package me.mondiversi.uvir

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Callback-only picker tests: no real connections, profile changes or records. */
class UvirSensorSelectionInfoTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dark = mutableStateOf(false)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val connectedId = mutableStateOf("A")
    private val enabled = mutableStateOf(true)
    private val selected = mutableStateOf("A")
    private val infoRequests = mutableListOf<String>()
    private val selectionRequests = mutableListOf<String>()

    private fun showPicker() {
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    UvirSensorSelectionDialog(
                        sensorProfiles = listOf(
                            UvirSensorProfile(1, "A", "Picker A", 0, 1_700_000_000_000L),
                            UvirSensorProfile(2, "B", "Picker B", 0, 1_700_000_100_000L)),
                        selectedSensorDeviceId = selected.value, enabled = enabled.value,
                        primaryText = colors.onSurface, secondaryText = colors.onSurfaceVariant,
                        cardColor = colors.surface,
                        onSensorSelected = { selectionRequests.add(it); selected.value = it },
                        onSensorInfoRequested = { infoRequests.add(it) },
                        connectedSensorDeviceId = connectedId.value,
                        onDismissRequest = {}
                    )
                }
            }
        }
    }

    @Test fun infoFollowsEachDateAndOnlyConnectedSensorIsEnabled() {
        showPicker()
        var requests = 0
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { dark.value = night; direction.value = layout }
            compose.onNodeWithText(context.getString(R.string.sensor_selection_title)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.sensor_associate_action)).assertDoesNotExist()
            for (id in listOf("A", "B")) {
                val info = compose.onNodeWithTag("sensor_profile_info_$id").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val date = compose.onNodeWithTag("sensor_profile_info_${id}_activity", useUnmergedTree = true)
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertEquals(date.center.y, info.center.y, 1f)
                if (layout == LayoutDirection.Ltr) assertTrue(info.left > date.right)
                else assertTrue(info.right < date.left)
            }
            compose.onNodeWithTag("sensor_profile_info_B").assertIsNotEnabled().performTouchInput { click() }
            assertEquals(0, selectionRequests.size)
            assertEquals(requests, infoRequests.size)
            compose.onNodeWithTag("sensor_profile_info_A").assertIsEnabled().performClick()
            assertEquals(++requests, infoRequests.size)
            assertEquals("A", infoRequests.last())
            assertEquals(0, selectionRequests.size)
        }
    }

    @Test fun disconnectionAndBusyPickerDisableInfoWithoutSelectingRows() {
        showPicker()
        compose.runOnIdle { connectedId.value = "" }
        for (id in listOf("A", "B")) {
            compose.onNodeWithTag("sensor_profile_info_$id").assertIsNotEnabled().performTouchInput { click() }
        }
        assertEquals(0, infoRequests.size)
        assertEquals(0, selectionRequests.size)
        // Selecting a remembered profile does not make it connected.
        compose.onNodeWithText("Picker B").performClick()
        assertEquals(listOf("B"), selectionRequests)
        compose.onNodeWithTag("sensor_profile_info_B").assertIsNotEnabled()
        compose.runOnIdle { connectedId.value = "b" }
        compose.onNodeWithTag("sensor_profile_info_B").assertIsEnabled().performClick()
        assertEquals(listOf("B"), infoRequests)
        compose.onNodeWithTag("sensor_profile_info_A").assertIsNotEnabled()
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithTag("sensor_profile_info_B").assertIsNotEnabled().performTouchInput { click() }
        assertEquals(listOf("B"), infoRequests)
        assertEquals(listOf("B"), selectionRequests)
    }
}
