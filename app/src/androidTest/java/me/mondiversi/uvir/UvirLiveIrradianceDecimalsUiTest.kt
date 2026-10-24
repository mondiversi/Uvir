package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic cards and isolated preferences only: no sensor or user archive writes. */
class UvirLiveIrradianceDecimalsUiTest {
    @get:Rule val compose = createComposeRule()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "live-decimals-test-${UUID.randomUUID()}-"
    private val files = mutableSetOf<String>()
    private val isolated = object : ContextWrapper(base) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val file = prefix + name
            files.add(file)
            return base.getSharedPreferences(file, mode)
        }
    }
    private val dark = mutableStateOf(false)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val unit = mutableStateOf(UvirIrradianceUnit.W_M2)
    private val liveSample = mutableStateOf(SensorSample(uva = .123456))
    private val group = mutableStateOf(SensorGroup.UV)
    private val digits = mutableStateMapOf(*UvirIrradianceUnit.entries.map {
        it to defaultUvirLiveIrradianceDecimals(it)
    }.toTypedArray())

    @After fun cleanup() { files.forEach(base::deleteSharedPreferences) }

    private fun fixture(chart: Boolean = false, provideDecimals: Boolean = true) {
        compose.setContent {
            KeepUvirVerificationScreenOn()
            val config = uvirThemeConfiguration(LocalConfiguration.current,
                if (dark.value) UvirAppTheme.DARK else UvirAppTheme.LIGHT).apply {
                setLocales(LocaleList(Locale.ITALY))
            }
            CompositionLocalProvider(LocalConfiguration provides config,
                LocalLayoutDirection provides direction.value,
                LocalUvirNumericFormat provides UvirNumericFormat.EUROPEAN,
                LocalUvirIrradianceUnit provides unit.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    Surface {
                        Column(Modifier.width(340.dp).height(620.dp).padding(12.dp)
                            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsIsland(containerColor = colors.surface, contentColor = colors.onSurface) {
                                SettingsIslandHeader("Irradianza", colors.onSurface, ConnectivityIconType.IRRADIANCE)
                                UvirLiveIrradianceDecimalsSetting(digits.getValue(unit.value), {
                                    digits[unit.value] = it
                                    saveUvirLiveIrradianceDecimals(isolated, unit.value, it)
                                }, colors.onSurface, colors.onSurfaceVariant)
                            }
                            CompositionLocalProvider(LocalUvirLiveIrradianceDecimals provides
                                if (provideDecimals) digits.getValue(unit.value) else null) {
                                Box(Modifier.testTag("decimal_live_card")) {
                                    val sample = liveSample.value
                                    val totals = sample.spectralTotals()
                                    if (group.value == SensorGroup.BIOLOGICAL) {
                                        BiologicalEffectsContent(sample, true, {}, cardColor = colors.surfaceVariant,
                                            primaryText = colors.onSurface, secondaryText = colors.onSurfaceVariant)
                                    } else {
                                        SensorGroupContent(group.value, true, {}, showChart = chart,
                                            liveHistory = listOf(LiveSamplePoint(1000, sample)),
                                            sample = sample, uvTotal = totals.ultraviolet,
                                            visibleTotal = totals.visible, nirTotal = totals.infrared, hev = sample.violetto + sample.blu,
                                            cardColor = colors.surfaceVariant, primaryText = colors.onSurface,
                                            secondaryText = colors.onSurfaceVariant, trackColor = colors.outlineVariant)
                                    }
                                }
                                Text(formatUvirIrradianceNumber(.123456, 3, UvirNumericFormat.EUROPEAN, unit.value),
                                    Modifier.testTag("decimal_saved_unchanged"))
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun preferencesAreIndependentPerUnitPersistedAndBounded() {
        for (current in UvirIrradianceUnit.entries) {
            assertEquals(defaultUvirLiveIrradianceDecimals(current), loadUvirLiveIrradianceDecimals(isolated, current))
            assertFalse(isolated.getSharedPreferences(UVIR_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .contains(uvirLiveIrradianceDecimalsKey(current)))
        }
        saveUvirLiveIrradianceDecimals(isolated, UvirIrradianceUnit.W_M2, 5)
        saveUvirLiveIrradianceDecimals(isolated, UvirIrradianceUnit.MW_CM2, 1)
        assertEquals(5, loadUvirLiveIrradianceDecimals(isolated, UvirIrradianceUnit.W_M2))
        assertEquals(1, loadUvirLiveIrradianceDecimals(isolated, UvirIrradianceUnit.MW_CM2))
        assertEquals(1, loadUvirLiveIrradianceDecimals(isolated, UvirIrradianceUnit.UW_CM2))
        saveUvirLiveIrradianceDecimals(isolated, UvirIrradianceUnit.UW_CM2, 0)
        assertEquals(0, loadUvirLiveIrradianceDecimals(isolated, UvirIrradianceUnit.UW_CM2))
        saveUvirLiveIrradianceDecimals(isolated, UvirIrradianceUnit.W_M2, 50)
        assertEquals(6, loadUvirLiveIrradianceDecimals(isolated, UvirIrradianceUnit.W_M2))
        saveUvirLiveIrradianceDecimals(isolated, UvirIrradianceUnit.W_M2, -50)
        assertEquals(0, loadUvirLiveIrradianceDecimals(isolated, UvirIrradianceUnit.W_M2))
    }

    @Test fun sharedDecimalPreferencesUpdateAcrossScreensAndReturnToDefaultsAfterReset() {
        compose.setContent {
            val values = rememberUvirIrradianceDisplayDecimals(isolated)
            Text(values.getValue(unit.value).toString(), Modifier.testTag("shared_decimal_choice"))
        }
        compose.onNodeWithTag("shared_decimal_choice").assertTextEquals("2")
        compose.runOnIdle { saveUvirLiveIrradianceDecimals(isolated, UvirIrradianceUnit.W_M2, 5) }
        compose.onNodeWithTag("shared_decimal_choice").assertTextEquals("5")
        compose.runOnIdle { unit.value = UvirIrradianceUnit.MW_CM2 }
        compose.onNodeWithTag("shared_decimal_choice").assertTextEquals("3")
        compose.runOnIdle {
            unit.value = UvirIrradianceUnit.W_M2
            isolated.getSharedPreferences(UVIR_PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        }
        compose.onNodeWithTag("shared_decimal_choice").assertTextEquals("2")
    }

    @Test fun pickerKeepsZerosAndChangesOnlyLiveValuesInBothThemesAndDirections() {
        fixture()
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { dark.value = night; direction.value = layout }
            compose.onNodeWithTag("live_irradiance_decimals").performScrollTo().performClick()
            compose.onNodeWithTag("live_irradiance_decimals_0").performScrollTo().performClick()
            compose.onAllNodesWithText("0 W/m²").onFirst().performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("live_irradiance_decimals").performScrollTo().performClick()
            compose.onNodeWithTag("live_irradiance_decimals_2").performScrollTo().performClick()
            compose.onAllNodesWithText("0,12 W/m²").onFirst().performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithText("0,00 W/m²").onFirst().performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("decimal_saved_unchanged").performScrollTo().assertTextEquals("0,12346")
            if (layout == LayoutDirection.Ltr) {
                compose.onNodeWithTag("live_irradiance_decimals").performScrollTo()
                screenshot("main-decimals-${if (night) "night" else "day"}.png")
            }
        }
    }

    @Test fun switchingUnitsRestoresTheirOwnDigitsWithoutTouchingSavedFormatting() {
        fixture()
        compose.onNodeWithTag("live_irradiance_decimals").performScrollTo().performClick()
        compose.onNodeWithTag("live_irradiance_decimals_4").performScrollTo().performClick()
        compose.onAllNodesWithText("0,1235 W/m²").onFirst().performScrollTo().assertIsDisplayed()
        compose.runOnIdle { unit.value = UvirIrradianceUnit.MW_CM2 }
        compose.onAllNodesWithText("0,012 mW/cm²").onFirst().performScrollTo().assertIsDisplayed()
        compose.runOnIdle { unit.value = UvirIrradianceUnit.UW_CM2 }
        compose.onAllNodesWithText("12,3 µW/cm²").onFirst().performScrollTo().assertIsDisplayed()
        compose.runOnIdle { unit.value = UvirIrradianceUnit.W_M2 }
        compose.onAllNodesWithText("0,1235 W/m²").onFirst().performScrollTo().assertIsDisplayed()
    }

    @Test fun liveChartUsesFixedLabelsWithoutRoundingItsUnderlyingSamples() {
        fixture(chart = true)
        compose.onNodeWithTag("live_irradiance_decimals").performScrollTo().performClick()
        compose.onNodeWithTag("live_irradiance_decimals_3").performScrollTo().performClick()
        compose.onNodeWithText("0,136").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0,000").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("decimal_saved_unchanged").performScrollTo().assertTextEquals("0,12346")
    }

    @Test fun cardsWithoutAnExplicitChoiceNeverFallBackToTheOldFixedPrecision() {
        fixture(provideDecimals = false)
        for ((current, expected) in listOf(
            UvirIrradianceUnit.W_M2 to "0,12 W/m²",
            UvirIrradianceUnit.MW_CM2 to "0,012 mW/cm²",
            UvirIrradianceUnit.UW_CM2 to "12,3 µW/cm²"
        )) {
            compose.runOnIdle { unit.value = current }
            compose.onAllNodesWithText(expected).onFirst().performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun chartsWithoutAnExplicitChoiceAlsoUseTheNewUnitDefault() {
        fixture(chart = true, provideDecimals = false)
        compose.onNodeWithText("0,14").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0,00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("decimal_saved_unchanged").performScrollTo().assertTextEquals("0,12346")
    }

    @Test fun decimalSelectorHasBreathingRoomAboveItsBorder() {
        fixture()
        compose.onNodeWithTag("live_irradiance_decimals").performScrollTo()
        val section = compose.onNodeWithTag("live_irradiance_decimals_section").fetchSemanticsNode().boundsInRoot
        val field = compose.onNodeWithTag("live_irradiance_decimals").fetchSemanticsNode().boundsInRoot
        assertTrue(field.top - section.top >= with(compose.density) { 8.dp.toPx() } - .5f)
    }

    @Test fun decimalMenuFollowsTheArrowEdgeInBothThemesAndDirections() {
        fixture()
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { dark.value = night; direction.value = layout }
            compose.onNodeWithTag("live_irradiance_decimals").performScrollTo().performClick()
            val field = compose.onNodeWithTag("live_irradiance_decimals").fetchSemanticsNode()
            val menu = compose.onNodeWithTag("live_irradiance_decimals_menu").assertIsDisplayed().fetchSemanticsNode()
            // The popup owns a separate Android window, so root-relative bounds cannot be compared.
            val fieldEdge = field.positionOnScreen.x + if (layout == LayoutDirection.Ltr) field.size.width else 0
            val menuEdge = menu.positionOnScreen.x + if (layout == LayoutDirection.Ltr) menu.size.width else 0
            assertEquals("Dropdown follows the chevron", fieldEdge, menuEdge, 1f)
            assertTrue("Dropdown keeps its content width instead of filling the field", menu.size.width < field.size.width)
            assertTrue("Value-only dropdown stays compact", menu.size.width <= with(compose.density) { 80.dp.toPx() })
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(0)
            compose.onNodeWithTag("live_irradiance_decimals_2").assertIsSelected()
            if (layout == LayoutDirection.Ltr) {
                val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                File(base.getExternalFilesDir(null), "decimal-menu-${if (night) "night" else "day"}.png")
                    .outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                bitmap.recycle()
            }
            compose.onNodeWithTag("live_irradiance_decimals_2").performClick()
            compose.onNodeWithTag("live_irradiance_decimals_menu").assertDoesNotExist()
        }
    }

    @Test fun allMainViewBarsStayEmptyWhenReadingsDisplayZeroAndRecoverWithMoreDecimals() {
        fixture()
        val expectedBars = mapOf(SensorGroup.UV to 4, SensorGroup.VISIBLE to 8,
            SensorGroup.NIR to 3, SensorGroup.BIOLOGICAL to 3)
        for (night in listOf(false, true)) for ((current, count) in expectedBars) {
            compose.runOnIdle { dark.value = night; group.value = current }
            for (noise in listOf(.01, .02, .03)) {
                compose.runOnIdle {
                    liveSample.value = SensorSample(uvc = noise, uvb = noise, uva = noise,
                        violetto = noise, blu = noise, verde = noise, giallo = noise,
                        arancione = noise, rosso = noise, f8 = noise, nir = noise).scaledIrradiance(.01)
                    digits[unit.value] = 2
                }
                val bars = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
                    useUnmergedTree = true).fetchSemanticsNodes()
                assertEquals("$current bars", count, bars.size)
                assertTrue("$current noise=$noise", bars.all { it.config[SemanticsProperties.ProgressBarRangeInfo].current == 0f })
            }
            compose.runOnIdle { digits[unit.value] = 6 }
            val visibleBars = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
                useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("$current with more decimals", visibleBars.any { it.config[SemanticsProperties.ProgressBarRangeInfo].current > 0f })
        }
    }

    private fun screenshot(name: String) {
        File(base.getExternalFilesDir(null), name).outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
