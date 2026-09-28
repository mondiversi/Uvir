package me.mondiversi.uvir

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Information-only fixtures: no real sensor commands, profile changes or exports. */
class UvirSensorInformationOrganizationTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val info = UvirSensorRuntimeInfo(
        deviceId = "UVIR-INFO-TEST", firmwareVersion = "0.5.101",
        boardName = "ESP32 DevKit", chipModel = "ESP32", chipCores = 2,
        cpuFrequencyMhz = 240, sensorName = "AS7343", sensorAvailable = true,
        uvAvailable = false, rtcAvailable = true, rtcValid = true, timeSource = "rtc",
        rtcCurrentTimeMs = 1_700_000_000_000L, framAvailable = true, sdAvailable = true
    )
    private fun resources(language: String): Resources =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }).resources

    @Test fun identityHardwareComponentsAndClockRemainSeparateInEveryLanguage() {
        for (language in listOf("en", "it", "ar", "de", "el", "es", "fa", "fr", "hi",
            "he", "ja", "ko-KR", "pt", "ru", "sw", "tr", "zh-CN")) {
            val res = resources(language)
            val clock = listOf(res.getString(R.string.sensor_info_clock_status) to
                res.getString(R.string.sensor_info_valid))
            val groups = uvirSensorDeviceInfoGroups(res, info, clock)
            assertEquals(listOf(R.string.sensor_info_general_information, R.string.sensor_info_hardware,
                R.string.sensor_info_components, R.string.sensor_info_date_time).map(res::getString),
                groups.map { it.title })
            assertEquals(listOf(res.getString(R.string.sensor_info_identifier),
                res.getString(R.string.sensor_info_firmware)), groups[0].rows.map { it.first })
            assertFalse(groups[0].rows.any { it.second.contains("AS7343") })
            assertTrue(groups[1].rows.any { it.second == "ESP32" })
            assertEquals(8, groups[2].rows.size)
            assertTrue(groups[2].paragraphLayout)
            assertTrue(groups[2].rows.first().second.contains("AS7343"))
            assertTrue(groups[2].rows[1].second.endsWith(res.getString(R.string.sensor_info_not_detected)))
            assertEquals(clock, groups[3].rows)
        }
    }

    @Test fun exportedReportUsesTheSameCategoriesWithoutDuplicateModels() {
        val res = resources("en")
        val report = formatUvirSensorInformation(res, SensorConnectionMode.USB, info,
            UvirNumericFormat.INTERNATIONAL, UvirIrradianceUnit.W_M2,
            lastConnectionAtMs = 1_700_000_060_000L)
        val device = report.substringAfter(res.getString(R.string.sensor_info_device).uppercase(Locale.ENGLISH))
            .substringBefore(res.getString(R.string.sensor_info_connection).uppercase(Locale.ENGLISH))
        val titles = listOf(R.string.sensor_info_general_information, R.string.sensor_info_hardware,
            R.string.sensor_info_components, R.string.sensor_info_date_time).map(res::getString)
        assertTrue(titles.all { device.contains(it) })
        assertTrue(titles.zipWithNext().all { (a, b) -> device.indexOf(a) < device.indexOf(b) })
        val general = device.substringAfter(titles[0]).substringBefore(titles[1])
        assertTrue(general.contains(info.deviceId))
        assertTrue(general.contains(info.firmwareVersion))
        assertFalse(general.contains("AS7343"))
        assertEquals(1, Regex("AS7343").findAll(report).count())
        assertTrue(report.contains("2023-11-14 22:13:20 UTC"))
        assertTrue(report.contains(res.getString(R.string.sensor_info_last_connection)))
        assertTrue(report.contains("2023-11-14 22:14:20 UTC"))
        assertTrue(report.contains("MB85RC256V"))
        assertTrue(report.contains("SN74HC125"))
    }

    @Test fun missingInformationStillKeepsAllComponentEntries() {
        val res = resources("it")
        val groups = uvirSensorDeviceInfoGroups(res, UvirSensorRuntimeInfo(), emptyList())
        val unavailable = res.getString(R.string.sensor_info_unavailable)
        assertTrue(groups[0].rows.all { it.second == unavailable })
        assertEquals(8, groups[2].rows.size)
        assertTrue(groups[2].rows.first().second.endsWith(unavailable))
    }

    @Test fun screenShowsModelsUnderComponentsAndScrollsInBothThemes() {
        val res = resources("it")
        val night = mutableStateOf(false)
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            CompositionLocalProvider(LocalResources provides res) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    UvirSensorInfoScreen(SensorConnectionMode.USB, info, colors.background,
                        colors.onSurface, colors.onSurfaceVariant, colors.surface, {})
                }
            }
        }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            compose.onNodeWithText(res.getString(R.string.sensor_info_general_information))
                .performScrollTo().assertIsDisplayed()
            // The old generic row contained just AS7343 in general information.
            compose.onNodeWithText("AS7343").assertDoesNotExist()
            compose.onNodeWithText(res.getString(R.string.sensor_info_components))
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("AS7343 · I²C 0x39 · ${res.getString(R.string.sensor_info_detected)}")
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(res.getString(R.string.sensor_info_date_time))
                .performScrollTo().assertIsDisplayed()
        }
    }
}
