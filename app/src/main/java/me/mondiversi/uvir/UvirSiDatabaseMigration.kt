package me.mondiversi.uvir

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/** Called inside SQLiteOpenHelper's upgrade transaction (also on private import copies). */
internal fun migrateUvirDatabaseToSi(db: SQLiteDatabase) {
    fun columns(table: String): Set<String> = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
        buildSet { while (c.moveToNext()) add(c.getString(1)) }
    }
    val bands = listOf("uvc", "uvb", "uva", "violetto", "blu", "verde", "giallo", "arancione", "rosso", "f8", "nir")
        .filter { it in columns("acquisitions") }
    if (bands.isNotEmpty()) db.execSQL("UPDATE acquisitions SET " + bands.joinToString(",") { "$it=$it/100.0" })
    if ("threshold" in columns("sensor_alert_rules"))
        db.execSQL("UPDATE sensor_alert_rules SET threshold=threshold/100.0")
    if ("details" in columns("alerts")) {
        db.rawQuery("SELECT id,details FROM alerts", null).use { cursor ->
            while (cursor.moveToNext()) {
                db.update("alerts", ContentValues().apply {
                    put("details", scaleUvirAlertDetails(cursor.getString(1), .01))
                }, "id=?", arrayOf(cursor.getLong(0).toString()))
            }
        }
    }
    // Offsets carry their own explicit unit; parsing never averages legacy physical channels.
    if ("calibration_offsets" in columns("sensor_settings")) {
        db.rawQuery("SELECT rowid,calibration_offsets FROM sensor_settings WHERE calibration_offsets IS NOT NULL", null).use { c ->
            while (c.moveToNext()) {
                val offsets = requireNotNull(CalibrationOffsets.fromStoredOrNull(c.getString(1))) {
                    "Invalid stored calibration offsets; migration rolled back"
                }
                db.update("sensor_settings", ContentValues().apply { put("calibration_offsets", offsets.toJson().toString()) },
                    "rowid=?", arrayOf(c.getLong(0).toString()))
            }
        }
    }
}
