package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolated summaries only: no real records, sensor commands or notifications. */
class UvirSensorSyncSummaryTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun titleIncludesTheOriginInEveryLanguageAndUsesItsIdWhenUnnamed() {
        for (language in listOf("en", "it", "ar", "de", "el", "es", "fa", "fr",
            "hi", "he", "ja", "ko-KR", "pt", "ru", "sw", "tr", "zh-CN")) {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val resources = context.createConfigurationContext(configuration).resources
            val template = resources.getString(R.string.sensor_sync_complete_from)
            assertEquals("Exactly one origin placeholder: $language", 1,
                Regex("%1\\\$s").findAll(template).count())
            val title = sensorSyncCompleteTitle(resources, SensorSyncSummary(12, 3, 0,
                sensorDeviceId = "UVIR-A", sensorName = "Portatile"))
            assertTrue("Uses the origin's name: $language", title.contains("Portatile"))
            assertTrue(!title.contains("%1\$s"))
            assertTrue(sensorSyncCompleteTitle(resources, SensorSyncSummary(1, 0, 0,
                sensorDeviceId = "UVIR-C")).contains("UVIR-C"))
            if (language == "it") assertEquals("Dati sincronizzati da Portatile", title)
            assertEquals(resources.getString(R.string.sensor_sync_complete),
                sensorSyncCompleteTitle(resources, SensorSyncSummary(1, 0, 0)))
        }
    }

    @Test fun dialogShowsTheSensorAndOnlyNonzeroCountsInBothThemesAndDirections() {
        val dark = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val summary = mutableStateOf(SensorSyncSummary(0, 3, 0, true,
            sensorDeviceId = "UVIR-A", sensorName = "Portatile"))
        val dismissals = AtomicInteger()
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    SensorSyncCompleteDialog(summary.value,
                        cardColor = if (dark.value) Color(0xFF1C242B) else Color.White,
                        primaryText = MaterialTheme.colorScheme.onSurface,
                        secondaryText = MaterialTheme.colorScheme.onSurfaceVariant,
                        onDismiss = { dismissals.incrementAndGet() })
                }
            }
        }
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { dark.value = night; direction.value = layout }
            compose.onNodeWithText(sensorSyncCompleteTitle(context.resources, summary.value)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.sensor_sync_acquisitions_recovered)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.sensor_sync_alerts_recovered)).assertIsDisplayed()
            compose.onNodeWithText("3").assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.sensor_sync_errors_recovered)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.sensor_sync_storage_full_warning)).assertIsDisplayed()
        }
        compose.runOnIdle {
            summary.value = SensorSyncSummary(12, 0, 2, sensorDeviceId = "UVIR-C")
        }
        compose.onNodeWithText(sensorSyncCompleteTitle(context.resources, summary.value)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.sensor_sync_acquisitions_recovered)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.sensor_sync_alerts_recovered)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.sensor_sync_errors_recovered)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.sensor_sync_storage_full_warning)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        assertEquals(1, dismissals.get())
    }
}
