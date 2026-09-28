package me.mondiversi.uvir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Collections
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UvirDiagnosticsIslandsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun threeIslandsKeepTheirOrderAndOnlyConcertTargetReceivesStartAndStop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testTitle = context.getString(R.string.diagnostics_test_group)
        val simulationTitle = context.getString(R.string.diagnostics_simulation_group)
        val concertTitle = context.getString(R.string.debug_performance_title)
        val play = context.getString(R.string.debug_performance_play)
        val stop = context.getString(R.string.debug_performance_stop)
        val dark = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        val connected = mutableStateOf(false)
        val fake = mutableStateOf(false)
        val saturated = mutableStateOf(false)
        val uid = mutableStateOf("A")
        val commands = Collections.synchronizedList(mutableListOf<String>())
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalUvirSettingsNeutralIcons provides true,
                    LocalLayoutDirection provides
                    if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    val view = LocalView.current
                    DisposableEffect(view) {
                        val previous = view.keepScreenOn
                        view.keepScreenOn = true
                        onDispose { view.keepScreenOn = previous }
                    }
                    val colors = MaterialTheme.colorScheme
                    val target = uid.value
                    Column(Modifier.fillMaxWidth().background(colors.background)
                        .verticalScroll(rememberScrollState()).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(UvirIslandSpacing)) {
                        UvirDiagnosticsIslands(colors.surface, colors.onSurface,
                            diagnosticTest = {
                                UvirSensorDiagnosticsContent(connected.value,
                                    UvirSensorRuntimeInfo(deviceId = target, firmwareVersion = "0.5.69"),
                                    SensorConnectionMode.WIFI, target,
                                    colors.surface, colors.onSurface, colors.onSurfaceVariant,
                                    onProbe = { _, _ -> error("No physical diagnostic probe in this fixture") },
                                    showDescription = false)
                            },
                            simulation = {
                                UvirSensorSimulationSettingsContent(fake.value, saturated.value,
                                    { fake.value = it }, { saturated.value = it },
                                    colors.onSurface, colors.onSurfaceVariant)
                            },
                            concert = {
                                UvirDebugPerformanceContent(context, connected.value, true, true, 0L,
                                    colors.onSurface, colors.onSurfaceVariant,
                                    onStart = { commands += "$target:START"; true },
                                    onStop = { commands += "$target:STOP"; true })
                            })
                    }
                }
            }
        }
        var completed = 0
        for (night in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            val target = if (rightToLeft) "B" else "A"
            compose.runOnIdle { dark.value = night; rtl.value = rightToLeft; uid.value = target; connected.value = false }
            val first = compose.onNodeWithText(testTitle).getUnclippedBoundsInRoot()
            val second = compose.onNodeWithText(simulationTitle).getUnclippedBoundsInRoot()
            val third = compose.onNodeWithText(concertTitle).getUnclippedBoundsInRoot()
            assertTrue(first.top < second.top && second.top < third.top)
            for (description in listOf(R.string.diagnostic_description,
                R.string.diagnostics_simulation_description, R.string.debug_performance_description)) {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(context.getString(description)).performScrollTo()
                    .assertIsDisplayed()
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(13.sp, layouts.single().layoutInput.style.fontSize)
                assertEquals(18.sp, layouts.single().layoutInput.style.lineHeight)
            }
            compose.onNodeWithText(context.getString(R.string.debug_diagnostic)).assertIsNotEnabled()
            compose.onNodeWithText(play).performScrollTo().assertIsNotEnabled()
            compose.runOnIdle { connected.value = true }
            compose.onNodeWithText(play).assertIsEnabled().performClick()
            compose.waitUntil(3_000L) { commands.size > completed }
            compose.onNodeWithText(stop).performScrollTo().performClick()
            compose.waitUntil(3_000L) { commands.size >= completed + 2 }
            assertEquals(listOf("$target:START", "$target:STOP"), commands.subList(completed, completed + 2).toList())
            completed += 2
            compose.onNodeWithText(play).assertIsDisplayed()
        }
        assertEquals(8, commands.size)
    }
}
