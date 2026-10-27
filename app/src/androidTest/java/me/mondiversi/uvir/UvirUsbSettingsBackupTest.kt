package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class UvirUsbSettingsBackupTest {
    private fun isolated(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        check(base.packageName.endsWith(".verification"))
        val prefix = "usb_backup_test_${UUID.randomUUID()}_"
        return object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences(prefix + name, mode)
        }
    }
    @Test fun backupKeepsSiCalibrationButNeverRestoresRunningJobsOrTemporaryHardware() {
        val source = isolated(); val target = isolated()
        val calibration = JSONObject().put("factors", JSONArray(List(11) { 2.0 }))
            .put("offsets", JSONArray(List(11) { .00025 })).put("master", 1.5).put("window", JSONArray(List(11) { 1.2 })).toString()
        source.getSharedPreferences(USB_PHONE_PREFS, 0).edit().putInt("samples", 7).putLong("spacing", 250).putBoolean("trim", true)
            .putString(USB_PHONE_CALIBRATION, calibration)
            .putString("calibration:FT232H:FT12345", calibration).putString("calibration:FT232H-TEMP:unknown", calibration)
            .putLong("open_acquisition", 77).putLong("open_alert", 78).putBoolean("simulation", true).commit()
        val payload = JSONObject()
        addUsbPhoneSettingsBackup(source, payload)
        restoreUsbPhoneSettingsBackup(target, payload)
        val prefs = target.getSharedPreferences(USB_PHONE_PREFS, 0)
        assertEquals(7, prefs.getInt("samples", 0))
        assertEquals(250, prefs.getLong("spacing", 0))
        assertTrue(prefs.getBoolean("trim", false))
        assertFalse(prefs.contains("open_acquisition")); assertFalse(prefs.contains("open_alert")); assertFalse(prefs.contains("simulation"))
        assertFalse(prefs.contains("calibration:FT232H-TEMP:unknown"))
        val restored = decodeUsbPhoneCalibration(prefs.getString("calibration:FT232H:FT12345", null)!!)
        assertEquals(.00025, restored.offsets[0], 0.0)
        assertEquals(1.5, restored.windowMaster, 0.0)
        assertEquals(restored, decodeUsbPhoneCalibration(prefs.getString(USB_PHONE_CALIBRATION, null)!!))
    }
    @Test fun oldAppBackupCannotOverwriteUsbCalibration() {
        val target = isolated()
        val prefs = target.getSharedPreferences(USB_PHONE_PREFS, 0)
        prefs.edit().putInt("samples", 9).commit()
        restoreUsbPhoneSettingsBackup(target, JSONObject().put("calibration_offsets", "old ESP32 data"))
        assertEquals(9, prefs.getInt("samples", 0))
    }
    @Test fun offlineCalibrationSurvivesDisconnectAndANewRuntimeWithoutAnAdapter() {
        val context = isolated()
        val first = UvirUsbPhoneRuntime(context)
        try {
            assertEquals(UsbPhoneStatus.DETACHED, first.state.value.status)
            first.updateCalibration { it.copy(factors = List(11) { 1.75 }) }
            first.updateCalibration { it.copy(offsets = List(11) { .00025 }, windowMaster = 1.25) }
            first.setSampling(9, 300, true)
            first.disconnect()
            assertEquals(1.75, first.state.value.calibration.factors[0], 0.0)
            val second = UvirUsbPhoneRuntime(context)
            try {
                assertEquals(first.state.value.calibration, second.state.value.calibration)
                assertEquals(9, second.state.value.samples)
                assertEquals(300, second.state.value.spacingMs)
                assertTrue(second.state.value.trim)
                assertEquals(UsbPhoneStatus.DETACHED, second.state.value.status)
            } finally { second.close() }
        } finally { first.close() }
    }
}
