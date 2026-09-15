package me.mondiversi.uvir

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Isolated cards: no settings, database, connection or sensor commands are used. */
class UvirLiveIslandHeaderTest {
    @get:Rule val compose = createComposeRule()
    private val toggles = AtomicInteger()
    private val bells = AtomicInteger()
    private val expanded = mutableStateOf(true)
    private lateinit var headerText: String
    private lateinit var bellDescription: String

    private fun showCard(biological: Boolean, dark: Boolean = false) {
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                headerText = if (biological) stringResource(R.string.biological_effects_view) else "Header test"
                bellDescription = stringResource(R.string.threshold_bell_configure)
                Box(Modifier.width(280.dp).testTag("card")) {
                    val primaryText = MaterialTheme.colorScheme.onSurface
                    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
                    if (biological) {
                        BiologicalEffectsContent(
                            sample = SensorSample(),
                            expanded = expanded.value,
                            onToggle = { toggles.incrementAndGet(); expanded.value = !expanded.value },
                            onConfigureAlerts = { bells.incrementAndGet() },
                            cardColor = MaterialTheme.colorScheme.surface,
                            primaryText = primaryText,
                            secondaryText = secondaryText
                        )
                    } else {
                        SpectrumCard(
                            group = SensorGroup.UV,
                            title = headerText,
                            total = 1.0,
                            unit = "µW/cm²",
                            expanded = expanded.value,
                            onToggle = { toggles.incrementAndGet(); expanded.value = !expanded.value },
                            showChart = false,
                            liveHistory = emptyList(),
                            liveChartSeries = emptyList(),
                            cardColor = MaterialTheme.colorScheme.surface,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            onAlertClick = { bells.incrementAndGet() }
                        ) { Text("Body test", color = Color.Black) }
                    }
                }
            }
        }
    }

    private fun checkFullHeader(biological: Boolean, dark: Boolean = false) {
        showCard(biological, dark)
        val card = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithText(headerText)
        val bounds = header.fetchSemanticsNode().boundsInRoot
        assertEquals(card.left, bounds.left, 0.5f)
        assertEquals(card.right, bounds.right, 0.5f)
        assertEquals(card.top, bounds.top, 0.5f)
        // The side padding must toggle the card too, not just its inner rectangle.
        header.performTouchInput { click(Offset(1f, bounds.height / 2f)) }
        compose.runOnIdle { assertEquals(1, toggles.get()); assertEquals(0, bells.get()) }
        // Reopen by pressing the bottom padding of the collapsed header.
        val collapsedBounds = header.fetchSemanticsNode().boundsInRoot
        header.performTouchInput { click(Offset(collapsedBounds.width / 2f, collapsedBounds.height - 2f)) }
        compose.runOnIdle { assertEquals(2, toggles.get()); assertEquals(0, bells.get()) }
    }

    private fun checkIndependentBell(biological: Boolean) {
        showCard(biological)
        compose.onNodeWithContentDescription(bellDescription).performClick()
        compose.runOnIdle {
            assertEquals(1, bells.get())
            assertEquals(0, toggles.get())
            assertEquals(true, expanded.value)
        }
    }

    @Test fun irradianceHeaderIncludesItsPadding() = checkFullHeader(false)
    @Test fun biologicalHeaderIncludesItsPadding() = checkFullHeader(true)
    @Test fun irradianceHeaderIncludesItsPaddingInDarkTheme() = checkFullHeader(false, true)
    @Test fun biologicalHeaderIncludesItsPaddingInDarkTheme() = checkFullHeader(true, true)
    @Test fun irradianceBellDoesNotCollapseTheCard() = checkIndependentBell(false)
    @Test fun biologicalBellDoesNotCollapseTheCard() = checkIndependentBell(true)
}
