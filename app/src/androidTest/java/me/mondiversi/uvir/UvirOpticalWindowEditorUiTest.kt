package me.mondiversi.uvir

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CopyOnWriteArrayList
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated Compose host: empty editor ID, no real sensor, DB or preference writes. */
class UvirOpticalWindowEditorUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val value = mutableStateOf(OpticalWindowSettings(enabled = true))
    private val applied = mutableStateOf(value.value)
    private val saves = CopyOnWriteArrayList<OpticalWindowSettings>()
    private val dark = mutableStateOf(false)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val tick = mutableIntStateOf(0)
    private val numericFormat = mutableStateOf(UvirNumericFormat.INTERNATIONAL)

    private fun fixture() {
        compose.setContent {
            KeepUvirVerificationScreenOn()
            val configuration = uvirThemeConfiguration(LocalConfiguration.current,
                if (dark.value) UvirAppTheme.DARK else UvirAppTheme.LIGHT)
            val scope = rememberCoroutineScope()
            val queue = remember { UvirSettingsSaveQueue(scope, {}) }
            queue.prepare = {
                val committed = value.value
                suspend {
                    if (committed != applied.value) {
                        saves.add(committed)
                        applied.value = committed
                    }
                }
            }
            CompositionLocalProvider(LocalSettingsCommit provides
                UvirSettingsCommitScope(queue, SettingsSaveGroup.CALIBRATION),
                LocalLayoutDirection provides direction.value,
                LocalConfiguration provides configuration,
                LocalUvirNumericFormat provides numericFormat.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    Box(Modifier.width(340.dp).height(620.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text("${tick.intValue}")
                            UvirOpticalWindowSettings(value.value, { value.value = it }, true, true,
                                colors.surface, colors.onSurface, colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    @Test fun neutralEditorAlwaysShowsMasterAndAllChannelsWithoutSaving() {
        value.value = OpticalWindowSettings(false, List(11) { 1.8f })
        applied.value = value.value
        numericFormat.value = UvirNumericFormat.EUROPEAN
        fixture()
        compose.onNodeWithText(context.getString(R.string.optical_window_enabled)).assertDoesNotExist()
        compose.onNodeWithTag("optical_window_factor_limits").assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.optical_window_factor_limits, "1,0", "10,0"))
        assertTrue(compose.onNodeWithTag("optical_window_factor_limits").getUnclippedBoundsInRoot().bottom <=
            compose.onNodeWithTag("optical_common_factor").getUnclippedBoundsInRoot().top)
        compose.onNodeWithTag("optical_individual_factors_firmware").assertDoesNotExist()
        compose.onNodeWithTag("optical_common_factor").performScrollTo().assertIsEnabled().assertTextContains("1,0")
        for (index in 0..10) compose.onNodeWithTag("optical_channel_$index").performScrollTo()
            .assertIsEnabled().assertTextContains("1,0")
        assertTrue(saves.isEmpty())
        compose.onNodeWithTag("optical_common_factor").performScrollTo().performTextReplacement("1,25")
        compose.onNodeWithTag("optical_channel_0").performScrollTo().performClick()
        compose.waitUntil(5_000) { saves.size == 1 }
        assertEquals(List(11) { 1f }, value.value.factors)
        assertEquals(1.25f, value.value.masterMultiplier, 0f)
        assertEquals(List(11) { 1.25f }, value.value.effectiveFactors)
        compose.onNodeWithTag("optical_common_factor").performScrollTo().assertTextContains("1,25")
        compose.runOnIdle { numericFormat.value = UvirNumericFormat.INTERNATIONAL }
        compose.onNodeWithTag("optical_common_factor").assertTextContains("1.25")
        assertEquals(1, saves.size)
    }

    @Test fun masterAndChannelEditsRemainIndependentWithoutReadbackWriteLoops() {
        val original = List(11) { 1f + it / 100f }
        value.value = OpticalWindowSettings(true, original)
        applied.value = value.value
        fixture()
        compose.onNodeWithTag("optical_common_factor").performScrollTo().performTextReplacement("1.25")
        assertTrue(saves.isEmpty())
        compose.onNodeWithTag("optical_channel_0").performScrollTo().performClick()
        compose.waitUntil(5_000) { saves.size == 1 }
        assertEquals(original, value.value.factors)
        repeat(10) { compose.runOnIdle { tick.intValue++ } }
        assertEquals(1, saves.size)
        compose.onNodeWithTag("optical_channel_4").performScrollTo().performTextReplacement("1.5")
        compose.onNodeWithTag("optical_common_factor").performScrollTo().performClick()
        compose.waitUntil(5_000) { saves.size == 2 }
        assertEquals(1.25f, value.value.masterMultiplier, 0f)
        assertEquals(1.5f, value.value.factors[4], 0f)
        assertEquals(1.875f, value.value.effectiveFactors[4], 0f)
        compose.onNodeWithTag("optical_common_factor").performTextReplacement("2")
        compose.onNodeWithTag("optical_channel_0").performScrollTo().performClick()
        compose.waitUntil(5_000) { saves.size == 3 }
        assertEquals(2f, value.value.masterMultiplier, 0f)
        assertEquals(1.5f, value.value.factors[4], 0f)
        assertEquals(3f, value.value.effectiveFactors[4], 0f)
        for (index in original.indices.filter { it != 4 }) assertEquals(original[index], value.value.factors[index])
    }

    @Test fun readbackAndThemeChangesDoNotSaveOrRecreateEditors() {
        value.value = OpticalWindowSettings(true, List(11) { 1f + it / 100f }).withMasterFactor(1.2f)
        applied.value = value.value
        fixture()
        for (night in listOf(false, true)) for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { dark.value = night; direction.value = layout; tick.intValue++ }
            compose.onNodeWithTag("optical_common_factor").performScrollTo().assertIsEnabled().assertTextContains("1.2")
            if (layout == LayoutDirection.Ltr) {
                compose.onNodeWithText(tick.intValue.toString()).performScrollTo()
                File(context.getExternalFilesDir(null), "optical-master-${if (night) "night" else "day"}.png").outputStream().use {
                    assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
            compose.onNodeWithTag("optical_channel_4").performScrollTo().assertTextContains("1.04")
            compose.onNodeWithText("450 / 475 nm").assertIsDisplayed()
            assertTrue(saves.isEmpty())
        }
    }
}
