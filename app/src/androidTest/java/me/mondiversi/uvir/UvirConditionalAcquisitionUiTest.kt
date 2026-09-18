package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.UUID
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import org.json.JSONObject
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated preferences and composable only: no real records or sensor commands. */
class UvirConditionalAcquisitionUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun settings() = ThresholdAlertSettings(false,listOf(
        ThresholdAlertRule(ThresholdAlertMetric.UVC,true,ThresholdAlertDirection.ABOVE,3f),
        ThresholdAlertRule(ThresholdAlertMetric.NIR,true,ThresholdAlertDirection.BELOW,5f)
    ),30,ThresholdAlertSound.SINGLE_BEEP,10)

    @Test fun savedThresholdsSurviveReopeningWithoutStartingMonitoring() {
        val name = "conditional_test_" + System.nanoTime()
        try {
            val prefs=context.getSharedPreferences(name,Context.MODE_PRIVATE)
            saveThresholdAlertSettings(prefs,settings())
            assertTrue(prefs.edit().commit())
            val restored=loadThresholdAlertSettings(context.getSharedPreferences(name,Context.MODE_PRIVATE))
            assertFalse(restored.enabled)
            assertEquals(settings().rules,restored.rules.filter { it.enabled })
            assertNotNull(conditionalPlanFromRules(restored.rules,AcquisitionConditionMatch.ALL,AcquisitionConditionAction.START))
        } finally { context.deleteSharedPreferences(name) }
    }

    @Test fun freshReadbackRestoresConditionalPhaseWithoutChangingConfiguredAlerts() {
        val plan=conditionalPlanFromRules(settings().rules,AcquisitionConditionMatch.ALL,AcquisitionConditionAction.START)!!
        val report=JSONObject().put("type","hello").put("protocol",UVIR_SENSOR_PROTOCOL)
            .put("device_id","CONDITIONAL_TEST_SENSOR").put("firmware","0.5.73")
            .put("offline_recording",true).put("offline_session_id",17)
            .put("offline_completed",0).put("offline_next_ms",1000)
            .put("offline_condition_plan",plan.encode()).put("offline_condition_waiting",true)
            .put("offline_end_ms",0).put("offline_started_ms",0)
        val waiting=UvirSensorRuntimeInfo().updatedFrom(report)
        assertEquals(plan,waiting.offlineConditionPlan)
        assertEquals(true,waiting.offlineConditionWaiting)
        val started=waiting.updatedFrom(report.put("offline_condition_waiting",false)
            .put("offline_started_ms",5000).put("offline_end_ms",35000))
        assertEquals(false,started.offlineConditionWaiting)
        assertEquals(35000L,started.offlineEndAtMs)
        val ordinary=started.updatedFrom(report.put("offline_condition_plan",""))
        assertNull(ordinary.offlineConditionPlan)
        assertTrue(settings().rules.all { it.enabled })
    }

    private inline fun isolatedDatabase(test: (UvirDatabaseHelper) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.cacheDir, "interrupted-session-test-${UUID.randomUUID()}")
        assertTrue(directory.mkdir())
        val isolatedContext = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = File(directory, name)
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
                error("This test must not access app preferences")
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
                errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory, errorHandler)
        }
        val database = UvirDatabaseHelper(isolatedContext)
        try {
            assertTrue(database.writableDatabase.path.startsWith(directory.absolutePath + File.separator))
            assertTrue(database.writableDatabase.path != base.getDatabasePath("uvir.db").absolutePath)
            test(database)
        } finally {
            database.close()
            directory.listFiles().orEmpty().forEach { assertTrue(it.delete()) }
            assertTrue(directory.delete())
        }
    }


    @Test fun triggerUpdatesOnlyItsOwnSessionAndNeverMovesPastSavedData() {
        isolatedDatabase { database ->
            database.startAcquisitionSession(17,"note",1000,"CONDITIONAL_A")
            assertFalse(database.confirmConditionalSessionStart(17,"CONDITIONAL_B",5000))
            assertTrue(database.confirmConditionalSessionStart(17,"CONDITIONAL_A",5000))
            assertFalse(database.confirmConditionalSessionStart(17,"CONDITIONAL_A",4000))
            assertTrue(database.saveRecoveredAcquisition(6000,SensorSample(),"",17,1,"CONDITIONAL_A",1))
            assertFalse(database.confirmConditionalSessionStart(17,"CONDITIONAL_A",7000))
            database.readableDatabase.rawQuery("SELECT started_at,note FROM acquisition_sessions WHERE session_id=17",null).use {
                assertTrue(it.moveToFirst()); assertEquals(5000L,it.getLong(0)); assertEquals("note",it.getString(1))
            }
        }
    }

    @Test fun choicesRemainUsableAndLockedSnapshotShowsWaitingInBothThemes() {
        val dark=mutableStateOf(false)
        val enabled=mutableStateOf(false)
        val match=mutableStateOf(AcquisitionConditionMatch.ANY)
        val action=mutableStateOf(AcquisitionConditionAction.ACQUIRE)
        val locked=mutableStateOf(false)
        compose.setContent {
            val view=LocalView.current
            DisposableEffect(view) { val old=view.keepScreenOn; view.keepScreenOn=true
                onDispose { view.keepScreenOn=old } }
            MaterialTheme(colorScheme=if(dark.value) darkColorScheme() else lightColorScheme()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    UvirConditionalAcquisitionCard(
                        enabled = enabled.value,
                        match = match.value,
                        action = action.value,
                        rules = settings().rules,
                        waiting = locked.value,
                        locked = locked.value,
                        cardColor = MaterialTheme.colorScheme.surface,
                        primaryText = MaterialTheme.colorScheme.onSurface,
                        secondaryText = MaterialTheme.colorScheme.onSurfaceVariant,
                        numericFormat = UvirNumericFormat.SYSTEM,
                        onEnabled = { enabled.value = it },
                        onMatch = { match.value = it },
                        onAction = { action.value = it }
                    )
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.conditional_acquisition)).performClick()
        compose.onNodeWithText(context.getString(R.string.conditional_all)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.conditional_start)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(enabled.value); assertEquals(AcquisitionConditionMatch.ALL,match.value)
            assertEquals(AcquisitionConditionAction.START,action.value); locked.value=true }
        for(night in listOf(false,true)) {
            compose.runOnIdle { dark.value=night }
            compose.onNodeWithText(context.getString(R.string.conditional_waiting)).performScrollTo().assertIsDisplayed()
        }
    }
}
