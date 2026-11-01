package me.mondiversi.uvir

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit maintenance audit. Retains a checksum inside the app; never exports records or notes. */
class UvirPhoneArchiveAuditTest {
    @Test fun retainedArchiveChecksum() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val action=InstrumentationRegistry.getArguments().getString("phoneArchiveAudit")
        assumeTrue(action=="before" || action=="after")
        val context=instrumentation.targetContext
        check(context.packageName=="me.mondiversi.uvir")
        // Standalone SQL so the test can also audit the previously installed APK.
        val columns=linkedMapOf(
            "acquisitions" to "id,timestamp,note,automatic,session_id,session_sequence,position_index,variant_index,uvc,uvb,uva,violetto,blu,verde,giallo,arancione,rosso,f8,nir,quality_flags,optical_window",
            "alerts" to "id,timestamp,details,optical_window,quality_flags,session_id,session_sequence",
            "acquisition_sessions" to "session_id,note,variants_per_position,started_at,ended_at",
            "alert_sessions" to "session_id,note,started_at,ended_at",
            "uvir_counters" to "name,value",
            "record_transfer_ids" to "uid,kind,local_id"
        )
        val summary=JSONObject()
        SQLiteDatabase.openDatabase(context.getDatabasePath("uvir.db").path,null,SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA quick_check",null).use { check(it.moveToFirst() && it.getString(0)=="ok") }
            columns.forEach { (table,fields) ->
                val digest=MessageDigest.getInstance("SHA-256")
                db.rawQuery("SELECT $fields FROM $table ORDER BY 1",null).use { c ->
                    while(c.moveToNext()) for(i in 0 until c.columnCount) {
                        val value=when(c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> "N"
                            Cursor.FIELD_TYPE_INTEGER -> "I${c.getLong(i)}"
                            Cursor.FIELD_TYPE_FLOAT -> "F${c.getDouble(i).toBits()}"
                            else -> "S${c.getString(i)}"
                        }.toByteArray(Charsets.UTF_8)
                        digest.update(java.nio.ByteBuffer.allocate(4).putInt(value.size).array());digest.update(value)
                    }
                    summary.put(table,JSONObject().put("count",c.count).put("sha256",digest.digest().joinToString("") { "%02x".format(it) }))
                }
            }
            val audit=File(context.noBackupFilesDir,"phone-archive-audit-before.json")
            if(action=="before") { check(db.version==26);audit.writeText(summary.toString()) }
            else { check(db.version==27);assertEquals(audit.readText(),summary.toString()) }
            instrumentation.sendStatus(0,Bundle().apply { putString("stream","Archive audit $action: schema ${db.version}, acquisitions ${summary.getJSONObject("acquisitions").getInt("count")}, alerts ${summary.getJSONObject("alerts").getInt("count")}; retained checksums verified.\n") })
        }
    }
}
