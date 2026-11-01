package me.mondiversi.uvir

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.awaitCancellation
import me.mondiversi.uvir.usb.UsbHeadChecks
import me.mondiversi.uvir.usb.UsbOpticalCheck
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Pure diagnostic UI and fake results. Never opens USB or the user's database. */
class UvirUsbHeadDiagnosticsUiTest {
    @get:Rule val compose=createComposeRule()
    private val resources=InstrumentationRegistry.getInstrumentation().targetContext.resources
    private val state=UsbPhoneState(status=UsbPhoneStatus.ERROR,adapterName="test-head",usbPermission=true,uvAvailable=true)
    private val good=UsbOpticalCheck(true,true,true,0x21)
    private val missing=UsbOpticalCheck(false,false,null,error="I2C did not acknowledge")
    private fun report(checks:UsbHeadChecks)=UvirSensorDiagnosticReport(1000,250,"Uvir",SensorConnectionMode.USB,
        usbPhoneDiagnosticInfo(state),emptyList(),checks)

    @Test fun partialHeadsShowAllThreeComponentsAndSeparateRecognitionInitializationAndReading() {
        var checks by mutableStateOf(UsbHeadChecks(1,"test-head",true,true,true,good,missing,true))
        var dark by mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) {
                val colors=MaterialTheme.colorScheme
                UvirSensorDiagnosticsContent(usbHeadDiagnosticAvailable(state),usbPhoneDiagnosticInfo(state),SensorConnectionMode.USB,"Uvir",
                    colors.surface,colors.onSurface,colors.onSurfaceVariant,
                    onProbe={_,_->error("Partial hardware must use the component diagnostic")},
                    onRun={ progress->progress(3);report(checks) },progressSteps=3,progressTextResource=R.string.usb_diagnostic_progress)
            }
        }
        for((index,pair) in listOf(good to missing,missing to good,missing to missing).withIndex()) {
            compose.runOnIdle { dark=index!=0;checks=checks.copy(uv=pair.first,visible=pair.second) }
            compose.onNodeWithText(resources.getString(R.string.debug_diagnostic)).assertIsEnabled().performClick()
            val text=compose.onNodeWithTag("diagnostic_report").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
            for(required in listOf("FT232H","AS7331","AS7343","0x74","0x39",
                resources.getString(R.string.usb_diagnostic_recognition),resources.getString(R.string.usb_diagnostic_initialization),
                resources.getString(R.string.usb_diagnostic_reading),resources.getString(R.string.sensor_info_not_detected))) assertTrue(required,text.contains(required))
            for(retired in listOf("ESP32","RTC","FRAM","microSD","Wi-Fi","Bluetooth")) assertFalse(retired,text.contains(retired))
            assertFalse(text.contains("0/6"))
            compose.onNodeWithTag("diagnostic_cancel").performClick()
        }
    }

    @Test fun stopCancelsTheUsbComponentRunnerAndTheButtonCanBeUsedAgain() {
        val cancelled=AtomicBoolean()
        compose.setContent { MaterialTheme {
            val colors=MaterialTheme.colorScheme
            UvirSensorDiagnosticsContent(true,usbPhoneDiagnosticInfo(state),SensorConnectionMode.USB,"Uvir",
                colors.surface,colors.onSurface,colors.onSurfaceVariant,onProbe={_,_->error("Unexpected legacy path")},
                onRun={ progress->try { progress(1);awaitCancellation() } finally { cancelled.set(true) } },
                progressSteps=3,progressTextResource=R.string.usb_diagnostic_progress)
        } }
        compose.onNodeWithText(resources.getString(R.string.debug_diagnostic)).performClick()
        compose.onNodeWithTag("diagnostic_dialog").assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.usb_diagnostic_progress,1,3)).assertIsDisplayed()
        compose.onNodeWithTag("diagnostic_cancel").performClick()
        compose.waitUntil(2000) { cancelled.get() }
        compose.onNodeWithTag("diagnostic_dialog").assertDoesNotExist()
        compose.onNodeWithText(resources.getString(R.string.debug_diagnostic)).assertIsEnabled()
    }

    @Test fun busFailuresAndCompleteResultsHaveAnAccurateOverallOutcome() {
        val successful=UsbHeadChecks(1,"head",true,true,true,good,good,true)
        assertTrue(report(successful).successful)
        for(checks in listOf(successful.copy(i2cReady=false),successful.copy(mpsseReady=false),
            successful.copy(visible=missing),successful.copy(uv=good.copy(readable=null)))) {
            val diagnostic=report(checks)
            assertFalse(diagnostic.successful)
            for(technical in listOf(false,true)) {
                val text=formatUvirSensorDiagnosticReport(resources,diagnostic,technical)
                assertTrue(text.contains(resources.getString(R.string.diagnostic_warning)))
                assertTrue(text.contains(resources.getString(R.string.usb_diagnostic_wiring)))
            }
        }
    }
}
