package me.mondiversi.uvir

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UvirAccessibilitySemanticsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun customActionKeepsLabelAndClickOnOneNode() {
        val clicks = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                Box(
                    androidx.compose.ui.Modifier.uvirAccessibleAction(
                        label = "Accessible action",
                        onClick = { clicks.incrementAndGet() }
                    )
                )
            }
        }

        compose
            .onAllNodesWithContentDescription("Accessible action", useUnmergedTree = true)
            .assertCountEquals(1)
        compose
            .onNodeWithContentDescription("Accessible action", useUnmergedTree = true)
            .assertHasClickAction()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(1, clicks.get()) }
    }

    @Test
    fun segmentedControlAnnouncesSelection() {
        compose.setContent {
            MaterialTheme {
                ViewModeButton(
                    selected = true,
                    alerted = false,
                    onClick = {},
                    contentDescription = "Irradiance",
                    primaryText = Color.Black,
                    secondaryText = Color.Gray
                ) { Box(androidx.compose.ui.Modifier) }
            }
        }

        compose.onNodeWithContentDescription("Irradiance").assertIsSelected().assertHasClickAction()
    }

    @Test
    fun collapsibleCardAnnouncesCurrentState() {
        val expanded = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                UvirCollapsibleChartCard(
                    title = "Ultraviolet",
                    expanded = expanded.value,
                    onToggle = { expanded.value = !expanded.value },
                    cardColor = Color.White,
                    primaryText = Color.Black,
                    secondaryText = Color.Gray
                ) {}
            }
        }

        val collapsed = context.getString(R.string.accessibility_collapsed)
        val expandedText = context.getString(R.string.accessibility_expanded)
        compose.onNodeWithContentDescription("Ultraviolet")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, collapsed))
            .performClick()
        compose.onNodeWithContentDescription("Ultraviolet")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expandedText))
    }

    @Test
    fun holdActionOffersTwoActivationAccessibilityAlternative() {
        val confirmations = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                HoldToConfirmActionButton(
                    label = "Delete",
                    onConfirmed = { confirmations.incrementAndGet() },
                    holdDurationMillis = 2_000L
                )
            }
        }

        compose.onNodeWithContentDescription("Delete").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(0, confirmations.get()) }

        compose.onNodeWithContentDescription("Delete")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(0, confirmations.get()) }
        compose.onNodeWithContentDescription("Delete")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(R.string.accessibility_activate_again_to_confirm)
                )
            )
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(1, confirmations.get()) }
    }

    @Test
    fun compactHomeBadgeRemainsDecorativeAndDoesNotBlockItsAction() {
        val clicks = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                UvirHomeActionButton(
                    type = MenuIconType.SAVED_MEASUREMENTS,
                    contentDescription = "Open acquisitions",
                    cardColor = Color.White,
                    primaryText = Color.Black,
                    badgeCount = 123,
                    badgeColor = Color.Red,
                    onClick = { clicks.incrementAndGet() }
                )
            }
        }

        val action =
            compose.onNodeWithContentDescription(
                "Open acquisitions",
                useUnmergedTree = true
            )
        compose
            .onAllNodesWithContentDescription(
                "Open acquisitions",
                useUnmergedTree = true
            )
            .assertCountEquals(1)
        action.assertHasClickAction()

        // The compact count bubble occupies this corner visually, but must not intercept it.
        action.performTouchInput {
            click(Offset(width - 5f, 5f))
        }
        compose.runOnIdle { assertEquals(1, clicks.get()) }
    }
}
