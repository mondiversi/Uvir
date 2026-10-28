package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
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
    @Test fun plainPhoneSettingsRoundTripWithoutPasswordsOrSensorSelection() {
        val source = isolated(); val target = isolated()
        source.getSharedPreferences(PREFS_NAME, 0).edit()
            .putString(KEY_APP_THEME, UvirAppTheme.DARK.storedValue)
            .putString("old_wifi_password", "never-export-this")
            .putString("old_auth_token", "never-export-this-either").commit()
        source.getSharedPreferences(USB_PHONE_PREFS, 0).edit()
            .putInt("samples", 11).putLong("spacing", 400)
            .putString(USB_PHONE_CALIBRATION, JSONObject().put("factors", JSONArray(List(11) { 1.5 }))
                .put("offsets", JSONArray(List(11) { .00035 })).put("master", 1.2)
                .put("window", JSONArray(List(11) { 1.1 })).toString())
            .putLong("open_acquisition", 99).putBoolean("simulation", true).commit()
        val file = createUvirAppSettingsBackup(source, null)
        try {
            val root = JSONObject(file.readText())
            assertEquals(UVIR_SETTINGS_TRANSFER_SCHEMA, root.getString("schema"))
            assertEquals("app", root.getString("scope"))
            assertEquals(UVIR_CANONICAL_IRRADIANCE_UNIT, root.getString("irradiance_unit"))
            assertFalse(root.has("ciphertext"))
            assertFalse(file.readText().contains("never-export"))
            assertFalse(uvirSettingsFilesRequirePassword(target, listOf(Uri.fromFile(file))))
            UvirDatabaseHelper(target).use { database ->
                val result = importUvirSettingsFiles(target, database, listOf(Uri.fromFile(file)))
                assertEquals(1, result.importedAppFiles)
                assertTrue(result.importedSensors.isEmpty())
            }
            assertEquals(UvirAppTheme.DARK.storedValue, target.getSharedPreferences(PREFS_NAME, 0).getString(KEY_APP_THEME, null))
            val prefs = target.getSharedPreferences(USB_PHONE_PREFS, 0)
            assertEquals(11, prefs.getInt("samples", 0))
            assertEquals(400L, prefs.getLong("spacing", 0))
            assertEquals(.00035, decodeUsbPhoneCalibration(prefs.getString(USB_PHONE_CALIBRATION, null)!!).offsets[0], 0.0)
            assertFalse(prefs.contains("open_acquisition")); assertFalse(prefs.contains("simulation"))
        } finally { file.delete() }
    }
    @Test fun previouslyEncryptedPhoneSettingsStillImport() {
        val source = isolated(); val target = isolated()
        source.getSharedPreferences(USB_PHONE_PREFS, 0).edit().putInt("samples", 17).commit()
        val file = createUvirAppSettingsBackup(source, "legacy-password".toCharArray())
        try {
            assertTrue(uvirSettingsFilesRequirePassword(target, listOf(Uri.fromFile(file))))
            UvirDatabaseHelper(target).use { database ->
                importUvirSettingsFiles(target, database, listOf(Uri.fromFile(file)), "legacy-password".toCharArray())
            }
            assertEquals(17, target.getSharedPreferences(USB_PHONE_PREFS, 0).getInt("samples", 0))
        } finally { file.delete() }
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
