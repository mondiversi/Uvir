package me.mondiversi.uvir

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class UvirPhoneArchiveContextUiTest {
    @get:Rule val compose=createComposeRule()
    private fun text(id:Int)=InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun acquisitionsShowOnlyNoteAndManualOrAutomaticMode() {
        compose.setContent { MaterialTheme {
            UvirDetailContextCard(automatic=false,note="Archive note",cardColor=Color.White,
                primaryText=Color.Black,secondaryText=Color.Gray)
        } }
        compose.onNodeWithText("Archive note").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.manual_measurement)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.record_context_mode_details)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.sensor_selector_label)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.note_and_sensor)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.external_measurement)).assertDoesNotExist()
    }

    @Test fun alertsHaveANoteWithoutSensorOrRedundantMode() {
        compose.setContent { MaterialTheme {
            UvirDetailContextCard(automatic=null,note="Alert note",cardColor=Color.White,
                primaryText=Color.Black,secondaryText=Color.Gray)
        } }
        compose.onNodeWithText("Alert note").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.record_context_details)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.acquisition_mode_label)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.sensor_selector_label)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.automatic_measurement)).assertDoesNotExist()
    }
}
