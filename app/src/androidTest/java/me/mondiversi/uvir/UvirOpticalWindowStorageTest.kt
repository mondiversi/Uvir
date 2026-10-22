package me.mondiversi.uvir

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Pure protocol/private in-memory fixtures: never opens the user's archive. */
class UvirOpticalWindowStorageTest {
    @Test fun opticalJsonAndAlreadyCorrectedReadingsRoundTrip() {
        for (enabled in listOf(false, true)) for (master in listOf(null, 1f, 1.25f, 10f)) {
            val window = OpticalWindowSettings(enabled, List(11) { 1f + it / 10f }, master)
            assertEquals(window, OpticalWindowSettings.fromJsonOrNull(window.toJson()))
            val frame = JSONObject().put("unit", UVIR_SENSOR_UNIT)
                .put("bands", JSONObject().put("uvc", 42))
                .put("optical_window", window.toJson())
            val sample = requireNotNull(frame.toUvirSensorSampleOrNull())
            assertEquals(.42, sample.uvc, 0.0)
            assertEquals(window, sample.opticalWindow)
            assertEquals(sample, sensorSampleToJson(sample).toSensorSample())
            assertEquals(window, OpticalWindowSettings.fromStoredOrNull(window.toJson().toString()))
        }
        assertNull(OpticalWindowSettings.fromJsonOrNull(JSONObject(
            "{\"version\":1,\"enabled\":true,\"factors\":[1]}")))
        assertNull(OpticalWindowSettings.fromJsonOrNull(
            OpticalWindowSettings().toJson().put("version", 2)))
    }

    @Test fun downgradeClearsStaleOpticalContextButPartialStatusPreservesIt() {
        val window = OpticalWindowSettings(true, List(11) { 1.111f })
        val runtime = UvirSensorRuntimeInfo(firmwareVersion = "0.5.105", opticalWindow = window)
        assertEquals(window, runtime.updatedFrom(JSONObject().put("type", "status")).opticalWindow)
        assertNull(runtime.updatedFrom(JSONObject().put("firmware", "0.5.104")).opticalWindow)
    }

    @Test fun versionTwentyOneUpgradeOnlyAddsNullableContextAndPreservesValues() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        UvirDatabaseHelper(context).use { helper ->
            SQLiteDatabase.create(null).use { fixture ->
                fixture.execSQL("CREATE TABLE acquisitions(id INTEGER PRIMARY KEY, uvc REAL)")
                fixture.execSQL("INSERT INTO acquisitions VALUES(7, 42.125)")
                fixture.execSQL("CREATE TABLE alerts(id INTEGER PRIMARY KEY, details TEXT)")
                fixture.execSQL("INSERT INTO alerts VALUES(8, 'original alert')")
                fixture.execSQL("CREATE TABLE sensor_settings(sensor_id INTEGER PRIMARY KEY)")
                fixture.execSQL("INSERT INTO sensor_settings VALUES(9)")
                helper.onUpgrade(fixture, 21, 22)
                fixture.rawQuery("SELECT uvc, optical_window FROM acquisitions", null).use {
                    assertTrue(it.moveToFirst())
                    assertEquals(42.125, it.getDouble(0), 0.0)
                    assertTrue(it.isNull(1))
                }
                fixture.rawQuery("SELECT details, optical_window FROM alerts", null).use {
                    assertTrue(it.moveToFirst())
                    assertEquals("original alert", it.getString(0))
                    assertTrue(it.isNull(1))
                }
                fixture.rawQuery("SELECT sensor_id, optical_window FROM sensor_settings", null).use {
                    assertTrue(it.moveToFirst())
                    assertEquals(9L, it.getLong(0))
                    assertTrue(it.isNull(1))
                }
            }
        }
    }
}
