package me.mondiversi.uvir

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Isolated menu: callbacks only count clicks; no app database or sensor commands. */
class UvirHomeRecordActionGroupTest {
    @get:Rule val compose = createComposeRule()

    private fun verifyButtons(withCountersAndAnimations: Boolean) {
        val dark = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val historyClicks = AtomicInteger()
        val alertClicks = AtomicInteger()
        val selectorClicks = AtomicInteger()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val historyLabel = context.getString(R.string.saved_measurements)
        val alertLabel = context.getString(R.string.threshold_alert_log_title)
        val irradianceLabel = context.getString(R.string.irradiance_view)

        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    UvirHomeMenuBar(
                        pinned = false,
                        viewMode = ViewMode.IRRADIANCE,
                        showChart = false,
                        monitoringAlertMetrics = emptyList(),
                        backgroundColor = colors.background,
                        cardColor = colors.surfaceContainer,
                        primaryText = colors.onSurface,
                        secondaryText = colors.onSurfaceVariant,
                        unreadAcquisitionCount = if (withCountersAndAnimations) 3 else 0,
                        unreadAlertCount = if (withCountersAndAnimations) 7 else 0,
                        acquisitionActivityInProgress = withCountersAndAnimations,
                        alertActivityInProgress = withCountersAndAnimations,
                        acquisitionSyncInProgress = withCountersAndAnimations,
                        alertSyncInProgress = withCountersAndAnimations,
                        onViewModeChanged = { selectorClicks.incrementAndGet() },
                        onShowChartChanged = { selectorClicks.incrementAndGet() },
                        onOpenHistory = { historyClicks.incrementAndGet() },
                        onOpenAlertLog = { alertClicks.incrementAndGet() }
                    )
                }
            }
        }
        var combination = 0
        for (darkTheme in listOf(false, true)) {
            for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                compose.runOnIdle { dark.value = darkTheme; direction.value = layoutDirection }
                compose.mainClock.advanceTimeBy(300L)
                val history = compose.onNodeWithContentDescription(historyLabel)
                val alerts = compose.onNodeWithContentDescription(alertLabel)
                val notSelected = SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected)
                history.assertIsDisplayed().assertHasClickAction().assert(notSelected)
                alerts.assertIsDisplayed().assertHasClickAction().assert(notSelected)
                val center = compose.onNodeWithContentDescription(irradianceLabel)
                    .fetchSemanticsNode().boundsInRoot.center.y
                assertEquals(center, history.fetchSemanticsNode().boundsInRoot.center.y, 1f)
                assertEquals(center, alerts.fetchSemanticsNode().boundsInRoot.center.y, 1f)
                if (withCountersAndAnimations) {
                    compose.onNodeWithText("3").assertIsDisplayed()
                    compose.onNodeWithText("7").assertIsDisplayed()
                }
                history.performClick()
                history.performClick()
                alerts.performClick()
                combination++
                assertEquals(combination * 2, historyClicks.get())
                assertEquals(combination, alertClicks.get())
                assertEquals(0, selectorClicks.get())
            }
        }
    }

    @Test fun groupedActionsRemainIndependentInBothThemesAndDirections() {
        verifyButtons(withCountersAndAnimations = false)
    }

    @Test fun countersAndSynchronizationDoNotChangeButtonBehavior() {
        verifyButtons(withCountersAndAnimations = true)
    }
}
