package me.mondiversi.uvir

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic firmware/connectivity only: never opens real ports, archives or update jobs. */
class UvirCalibrationFirmwareGateUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val connected = mutableStateOf(true)
    private val blocked = mutableStateOf(false)
    private val uvAvailable = mutableStateOf(true)
    private val version = mutableStateOf("0.5.105")
    private val night = mutableStateOf(false)
    private val showUpdate = mutableStateOf(false)
    private val window = mutableStateOf(OpticalWindowSettings(true))
    private var requests = 0
    private var searches = 0
    private var writes = 0
    private var measurements = 0

    private fun fixture() {
        compose.setContent {
            KeepUvirVerificationScreenOn()
            val configuration = uvirThemeConfiguration(LocalConfiguration.current,
                if (night.value) UvirAppTheme.DARK else UvirAppTheme.LIGHT)
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    val onUpdate: () -> Unit = { requests++; showUpdate.value = true }
                    Column(Modifier.width(340.dp).height(620.dp).verticalScroll(rememberScrollState())) {
                        UvirSensorCalibrationSettings(LocalContext.current, connected.value && !blocked.value,
                            uvAvailable.value, "1.0", { writes++ }, "1.0", { writes++ },
                            colors.surface, colors.onSurface, colors.onSurfaceVariant,
                            offsets = CalibrationOffsets(),
                            offsetsSupported = firmwareSupportsCalibrationOffsets(version.value),
                            onOffsetsChange = { writes++ }, channelFactors = CalibrationChannelFactors(),
                            channelFactorsSupported = firmwareSupportsCalibrationChannels(version.value),
                            onChannelFactorsChange = { writes++ },
                            darkMeasurementSupported = firmwareSupportsDarkOffsetMeasurement(version.value, CalibrationGroup.VISIBLE),
                            uvDarkMeasurementSupported = firmwareSupportsDarkOffsetMeasurement(version.value, CalibrationGroup.UV),
                            onMeasureDarkOffsets = { group, _ ->
                                measurements++
                                DarkOffsetReading(group, List(group.indices.count()) { 0f }, 30)
                            }, onFirmwareUpdateRequired = onUpdate)
                        UvirOpticalWindowSettings(window.value, { writes++; window.value = it },
                            connected.value && !blocked.value, firmwareSupportsOpticalWindow(version.value),
                            colors.surface, colors.onSurface, colors.onSurfaceVariant,
                            onFirmwareUpdateRequired = onUpdate,
                            masterSupported = firmwareSupportsOpticalWindowMaster(version.value))
                    }
                    if (showUpdate.value) UvirFirmwareUpdateRequiredDialog(
                        colors.surface, colors.onSurface, colors.onSurfaceVariant,
                        onCheckUpdates = { searches++ }, onDismissRequest = { showUpdate.value = false })
                }
            }
        }
    }

    private fun assertNoFixedFirmwareMessages() {
        for (id in listOf(R.string.calibration_channels_firmware_required,
            R.string.calibration_offsets_firmware_required, R.string.sensor_calibration_firmware_required,
            R.string.dark_offsets_firmware_required)) {
            compose.onNodeWithText(context.getString(id)).assertDoesNotExist()
        }
    }

    private fun requestFrom(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("${tag}_firmware").performTouchInput { click() }
        compose.onNodeWithTag("firmware_update_required_dialog").assertIsDisplayed()
        assertEquals(0, writes)
        assertEquals(0, measurements)
        assertEquals(0, searches)
    }

    private fun closeUpdate() {
        compose.onNodeWithText(context.getString(R.string.close)).performClick()
        compose.onNodeWithTag("firmware_update_required_dialog").assertDoesNotExist()
    }

    @Test fun disconnectedSensorHasOnlyDisabledControlsAndNoFirmwareNoticesInBothThemes() {
        connected.value = false
        version.value = ""
        fixture()
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            assertNoFixedFirmwareMessages()
            for (tag in listOf("calibration_channel_factor_0", "calibration_offset_4", "dark_offsets_uv",
                "calibration_reset_vis", "optical_common_factor")) {
                compose.onNodeWithTag(tag).performScrollTo().assertIsNotEnabled().performTouchInput { click() }
                compose.onNodeWithTag("${tag}_firmware").assertDoesNotExist()
            }
            compose.onNodeWithTag("optical_individual_factors_firmware").assertDoesNotExist()
            compose.onNodeWithTag("firmware_update_required_dialog").assertDoesNotExist()
        }
        assertEquals(0, requests)
        assertEquals(0, writes)
        assertEquals(0, measurements)
    }

    @Test fun oldFirmwareOffersAnUpdateWithoutEditingFactorsOffsetsButtonsOrCommonWindowInBothThemes() {
        fixture()
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            assertNoFixedFirmwareMessages()
            for (index in 0..10) {
                compose.onNodeWithTag("calibration_channel_factor_$index").performScrollTo().assertIsNotEnabled()
                compose.onNodeWithTag("calibration_channel_factor_${index}_firmware").assertExists()
                compose.onNodeWithTag("calibration_offset_$index").assertIsNotEnabled()
                compose.onNodeWithTag("calibration_offset_${index}_firmware").assertExists()
            }
            for (tag in listOf("calibration_channel_factor_0", "calibration_offset_4", "calibration_channel_factor_10",
                "dark_offsets_uv", "calibration_reset_vis", "optical_common_factor")) {
                requestFrom(tag)
                if (tag == "optical_common_factor") {
                    File(context.getExternalFilesDir(null), "firmware-required-${if (dark) "night" else "day"}.png")
                        .outputStream().use {
                            assertTrue(compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
                                .compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                }
                closeUpdate()
            }
        }
        assertEquals(12, requests)
    }

    @Test fun eachFeatureUsesItsOwnMinimumFirmwareAndReadbackNeverTriggersAPopup() {
        version.value = "0.5.108"
        fixture()
        compose.onNodeWithTag("calibration_offset_4").performScrollTo().assertIsEnabled()
        requestFrom("calibration_channel_factor_4")
        closeUpdate()
        compose.onNodeWithTag("optical_channel_4").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("optical_channel_4_firmware").assertDoesNotExist()
        compose.onNodeWithTag("optical_common_factor").assertIsNotEnabled()
        compose.runOnIdle { version.value = "0.5.109" }
        compose.onNodeWithTag("calibration_channel_factor_4").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("calibration_reset_vis").performScrollTo().assertIsEnabled()
        requestFrom("dark_offsets_vis")
        closeUpdate()
        compose.runOnIdle { version.value = "0.5.110" }
        compose.onNodeWithTag("dark_offsets_vis").assertIsEnabled()
        compose.onNodeWithTag("dark_offsets_vis_firmware").assertDoesNotExist()
        compose.onNodeWithTag("firmware_update_required_dialog").assertDoesNotExist()
        assertEquals(2, requests)
        assertEquals(0, writes)
        assertEquals(0, measurements)
        compose.runOnIdle { version.value = "0.5.111" }
        compose.onNodeWithTag("optical_common_factor").performScrollTo().assertIsEnabled()
        requestFrom("dark_offsets_uv")
        closeUpdate()
        compose.runOnIdle { version.value = "0.5.114" }
        compose.onNodeWithTag("dark_offsets_uv").assertIsEnabled()
        compose.onNodeWithTag("dark_offsets_uv_firmware").assertDoesNotExist()
    }

    @Test fun individualWindowChannelsAndResetOnlyOfferTheUpdate() {
        window.value = OpticalWindowSettings(true, List(11) { 1f + it / 100f })
        fixture()
        for (tag in listOf("optical_common_factor", "optical_channel_0", "optical_channel_4", "optical_channel_10", "optical_window_reset")) {
            requestFrom(tag)
            closeUpdate()
        }
        assertEquals(5, requests)
        assertEquals(List(11) { 1f + it / 100f }, window.value.factors)
        assertEquals(0, writes)
    }

    @Test fun activeSessionAndMissingUvHardwareDoNotMasqueradeAsFirmwareProblems() {
        blocked.value = true
        fixture()
        for (tag in listOf("calibration_channel_factor_4", "dark_offsets_vis", "optical_common_factor")) {
            compose.onNodeWithTag(tag).performScrollTo().assertIsNotEnabled().performTouchInput { click() }
            compose.onNodeWithTag("${tag}_firmware").assertDoesNotExist()
        }
        compose.runOnIdle { blocked.value = false; uvAvailable.value = false }
        compose.onNodeWithTag("calibration_channel_factor_0").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("calibration_channel_factor_0_firmware").assertDoesNotExist()
        compose.onNodeWithTag("dark_offsets_uv_firmware").assertDoesNotExist()
        assertEquals(0, requests)
        assertEquals(0, writes)
    }

    @Test fun firmwareDialogStartsOnlyAnExplicitSearchAndUsesUppercaseActions() {
        fixture()
        requestFrom("calibration_channel_factor_4")
        compose.onNodeWithText(context.getString(R.string.check_for_updates)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.close)).assertIsDisplayed()
        compose.onNodeWithTag("firmware_update_check").performClick()
        compose.onNodeWithTag("firmware_update_required_dialog").assertDoesNotExist()
        assertEquals(1, searches)
        assertEquals(0, writes)
        assertEquals(0, measurements)
    }
}
