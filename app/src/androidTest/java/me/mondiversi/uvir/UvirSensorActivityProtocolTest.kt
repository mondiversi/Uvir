package me.mondiversi.uvir

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parser-only checks: no app database, saved preferences or sensor commands. */
class UvirSensorActivityProtocolTest {
    private val uid = "689FACC8E720"
    private val previous = UvirSensorRuntimeInfo(deviceId = uid, visibleCalibrationFactor = 1.25)

    private fun frame(active: Boolean) = JSONObject()
        .put("type", "activity").put("protocol", UVIR_SENSOR_PROTOCOL)
        .put("device_id", uid).put("operation_active", active)

    @Test fun onlyAuthenticatedMatchingSensorsCanReportActivity() {
        assertTrue(previous.updatedFromActivityFrame(frame(true), true)!!.operationActive == true)
        assertFalse(previous.updatedFromActivityFrame(frame(false), true)!!.operationActive == true)
        assertNull(previous.updatedFromActivityFrame(frame(true), false))
        assertNull(previous.updatedFromActivityFrame(frame(true).put("device_id", "112233445566"), true))
        assertNull(UvirSensorRuntimeInfo().updatedFromActivityFrame(frame(true), true))
        assertNull(previous.updatedFromActivityFrame(frame(true).put("operation_active", "true"), true))
        assertNull(previous.updatedFromActivityFrame(frame(true).put("protocol", "other"), true))
        assertNull(previous.updatedFromActivityFrame(frame(true).put("type", "sample"), true))
    }

    @Test fun activityCannotOverwriteSamplingOrCalibrationSettings() {
        val result = previous.updatedFromActivityFrame(
            frame(true).put("visible_calibration_factor", 7.0).put("samples_per_result", 21), true
        )!!
        assertEquals(previous.copy(operationActive = true), result)
        assertEquals(result, result.updatedFromActivityFrame(frame(true), true))
    }

    @Test fun snapshotsAndPartialRepliesKeepActivityUntilItChanges() {
        val active = previous.updatedFrom(JSONObject().put("operation_active", true))
        assertTrue(active.operationActive == true)
        assertEquals(true, active.updatedFrom(JSONObject().put("uptime_ms", 1000)).operationActive)
        assertEquals(false, active.updatedFrom(JSONObject().put("operation_active", false)).operationActive)
        assertNull(previous.operationActive)
    }
}
