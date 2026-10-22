package me.mondiversi.uvir

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Private in-memory fixtures only; never opens or edits the user's archive. */
class UvirCalibrationOffsetsStorageTest {
    @Test fun channelGainsRoundTripAndPartialStatusDoesNotEraseThem() {
        val gains = CalibrationChannelFactors(List(11) { .5f + it * .1f })
        assertEquals(gains, CalibrationChannelFactors.fromJsonOrNull(gains.toJson()))
        assertEquals(gains, CalibrationChannelFactors.fromStoredOrNull(gains.toJson().toString()))
        val runtime = UvirSensorRuntimeInfo().updatedFrom(JSONObject().put("firmware", "0.5.109")
            .put("calibration_channel_factors", gains.toJson()))
        assertEquals(gains, runtime.calibrationChannelFactors)
        assertEquals(gains, runtime.updatedFrom(JSONObject().put("type", "status")).calibrationChannelFactors)
        assertNull(runtime.updatedFrom(JSONObject().put("firmware", "0.5.108")).calibrationChannelFactors)
        assertNull(runtime.updatedFrom(JSONObject().put("calibration_channel_factors", JSONObject.NULL)).calibrationChannelFactors)
    }

    @Test fun version24AddsOnlyNullableChannelGainsAndPreservesRecords() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        UvirDatabaseHelper(context).use { helper ->
            SQLiteDatabase.create(null).use { fixture ->
                fixture.execSQL("CREATE TABLE sensor_settings(sensor_id INTEGER PRIMARY KEY, calibration_offsets TEXT)")
                fixture.execSQL("INSERT INTO sensor_settings VALUES(9, 'preserved')")
                fixture.execSQL("CREATE TABLE acquisitions(id INTEGER PRIMARY KEY, uva REAL)")
                fixture.execSQL("INSERT INTO acquisitions VALUES(7, 42.125)")
                helper.onUpgrade(fixture, 23, 24)
                fixture.rawQuery("SELECT calibration_offsets, calibration_channel_factors FROM sensor_settings", null).use {
                    assertTrue(it.moveToFirst()); assertEquals("preserved", it.getString(0)); assertTrue(it.isNull(1))
                }
                fixture.rawQuery("SELECT uva FROM acquisitions", null).use {
                    assertTrue(it.moveToFirst()); assertEquals(42.125, it.getDouble(0), 0.0)
                }
            }
        }
    }

    @Test fun jsonReadbackAndRuntimeCachePreserveCanonicalOffsets() {
        val value = CalibrationOffsets(List(15) { it * .125f })
        assertEquals(value, CalibrationOffsets.fromJsonOrNull(value.toJson()))
        val grouped = value.forColorEditor()
        assertEquals(grouped, CalibrationOffsets.fromJsonOrNull(grouped.toJson()))
        assertNull(CalibrationOffsets.fromJsonOrNull(grouped.toJson().put("version", 1)))
        assertNull(CalibrationOffsets.fromJsonOrNull(value.toJson().put("unit", "unknown")))
        assertNull(CalibrationOffsets.fromJsonOrNull(value.toJson().put("version", 2)))
        val runtime = UvirSensorRuntimeInfo().updatedFrom(JSONObject()
            .put("firmware", "0.5.107").put("calibration_offsets", value.toJson()))
        assertEquals(value, runtime.calibrationOffsets)
        assertEquals(value, runtime.updatedFrom(JSONObject().put("type", "status")).calibrationOffsets)
        assertNull(runtime.updatedFrom(JSONObject().put("firmware", "0.5.106")).calibrationOffsets)
    }

    @Test fun schemaUpgradeDoesNotInventOffsetsOrRecalculateMeasurements() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        UvirDatabaseHelper(context).use { helper ->
            SQLiteDatabase.create(null).use { fixture ->
                fixture.execSQL("CREATE TABLE sensor_settings(sensor_id INTEGER PRIMARY KEY)")
                fixture.execSQL("INSERT INTO sensor_settings VALUES(9)")
                fixture.execSQL("CREATE TABLE acquisitions(id INTEGER PRIMARY KEY, uva REAL)")
                fixture.execSQL("INSERT INTO acquisitions VALUES(7, 42.125)")
                helper.onUpgrade(fixture, 22, 23)
                fixture.rawQuery("SELECT sensor_id, calibration_offsets FROM sensor_settings", null).use {
                    assertTrue(it.moveToFirst()); assertEquals(9L, it.getLong(0)); assertTrue(it.isNull(1))
                }
                fixture.rawQuery("SELECT uva FROM acquisitions", null).use {
                    assertTrue(it.moveToFirst()); assertEquals(42.125, it.getDouble(0), 0.0)
                }
            }
        }
    }
}
