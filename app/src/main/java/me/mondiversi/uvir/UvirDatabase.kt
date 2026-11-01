package me.mondiversi.uvir

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class UvirDatabaseHelper(
    context: Context
) : SQLiteOpenHelper(
    context.applicationContext,
    "uvir.db",
    null,
    UVIR_DATABASE_VERSION
) {

    private val appContext =
        context.applicationContext

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        if (db.version in 1 until UVIR_DATABASE_VERSION) {
            // Before SQLiteOpenHelper starts its upgrade transaction. The checkpoint
            // must succeed; never upgrade a live archive without a consistent backup.
            db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { c ->
                check(c.moveToFirst() && c.getInt(0) == 0) { "Unable to checkpoint database before SI migration" }
            }
            val source = java.io.File(db.path)
            val target = java.io.File(appContext.noBackupFilesDir, if (db.version < 25) "before-si/uvir-v${db.version}.db" else "before-phone-archive/uvir-v${db.version}.db")
            check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
            atomicUvirPrivateBackup(source, target)
        }
    }

    private val mutableAcquisitionRevision = MutableStateFlow(0L)
    private val mutableAlertRevision = MutableStateFlow(0L)
    private val mutableSensorProfileRevision = MutableStateFlow(0L)

    internal val acquisitionRevision: StateFlow<Long> =
        mutableAcquisitionRevision.asStateFlow()
    internal val alertRevision: StateFlow<Long> =
        mutableAlertRevision.asStateFlow()
    internal val sensorProfileRevision: StateFlow<Long> =
        mutableSensorProfileRevision.asStateFlow()

    private fun notifyAcquisitionsChanged() {
        mutableAcquisitionRevision.update { it + 1L }
    }

    private fun notifyAlertsChanged() {
        mutableAlertRevision.update { it + 1L }
    }

    private fun notifySensorProfilesChanged() {
        mutableSensorProfileRevision.update { it + 1L }
    }

    internal fun notifyDatabaseImported() {
        notifyAcquisitionsChanged()
        notifyAlertsChanged()
        notifySensorProfilesChanged()
    }

    private fun createCountersTable(
        db: SQLiteDatabase
    ) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS uvir_counters (
                name TEXT PRIMARY KEY,
                value INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO uvir_counters(name, value)
            VALUES ('session_id', 0)
            """.trimIndent()
        )
    }

    private fun createSessionTables(
        db: SQLiteDatabase
    ) {
        createUvirPhoneSessionTables(db)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createUvirPhoneArchiveSchema(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        UvirLegacyDatabaseMigration(appContext).upgrade(db, oldVersion, newVersion)
    }

    // Compatibility surface for dormant legacy import/control code. USB archives have no profiles.
    fun ensureSensorProfile(
        hardwareUid: String,
        seenAt: Long = System.currentTimeMillis()
    ): UvirSensorProfile? {
        return null
    }

    fun renameSensor(
        hardwareUid: String,
        displayName: String
    ): UvirSensorProfile? {
        return null
    }

    fun readSensorProfiles(): List<UvirSensorProfile> {
        return emptyList()
    }

    fun repairKnownAlertSensorAssociations(): Int {
        return 0
    }

    fun findSensorProfile(sensorId: Long): UvirSensorProfile? {
        return null
    }

    fun findSensorProfile(hardwareUid: String): UvirSensorProfile? {
        return null
    }

    fun upsertSensorSettings(
        hardwareUid: String,
        settings: UvirSensorSettingsSnapshot,
        syncedAt: Long = System.currentTimeMillis()
    ): Boolean {
        return false
    }

    fun readSensorSettings(
        hardwareUid: String
    ): UvirSensorSettingsSnapshot? {
        return null
    }

    private fun nextSessionId(database: SQLiteDatabase): Long {
        var nextId = 0L
        createCountersTable(database)
        database.execSQL(
            """
            UPDATE uvir_counters
            SET value = value + 1
            WHERE name = 'session_id'
            """.trimIndent()
        )
        database.rawQuery(
            """
            SELECT value
            FROM uvir_counters
            WHERE name = 'session_id'
            """.trimIndent(),
            null
        ).use {
            if (it.moveToFirst()) {
                nextId = it.getLong(0)
            }
        }
        return nextId
    }

    fun nextSessionId(): Long {
        val database = writableDatabase
        var nextId = 0L
        database.beginTransaction()
        try {
            nextId = nextSessionId(database)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        return nextId
    }

    /**
     * Translates a per-sensor autonomous session token into Uvir's global,
     * progressive session ID. The mapping lives beside the session itself so
     * live delivery, later offline recovery and app restarts all converge on
     * the same row without exposing the large remote token in the UI.
     */
    fun resolveSensorOriginatedAcquisitionSession(
        sensorDeviceId: String,
        sensorSessionId: Long,
        startedAt: Long
    ): Long {
        error("Autonomous sensor sessions are not supported by Uvir")
    }

    private fun upsertSession(
        database: SQLiteDatabase,
        table: String,
        sessionId: Long,
        note: String,
        startedAt: Long,
        reopen: Boolean,
        sensorDeviceId: String = "",
        externalCommand: Boolean = false
    ) {
        require(
            table == "acquisition_sessions" || table == "alert_sessions"
        )
        if (sessionId <= 0L) return

        createUvirPhoneSessionTables(database)
        var existingSessionOpen = false
        val existingNote =
            database.query(
                table,
                arrayOf("note", "ended_at"),
                "session_id = ?",
                arrayOf(sessionId.toString()),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    existingSessionOpen = cursor.isNull(1)
                    cursor.getString(0)
                } else null
            }
        val normalizedNote = limitUvirNote(note).trim()
        if (existingNote == null) {
            database.insertOrThrow(
                table,
                null,
                ContentValues().apply {
                    put("session_id", sessionId)
                    put("note", normalizedNote)
                    put("started_at", startedAt.coerceAtLeast(0L))
                    putNull("ended_at")
                }
            )
        } else {
            val values = ContentValues()
            // Once finished, even an empty note is authoritative: late sensor records must
            // not restore a note that the user deliberately cleared after the session.
            if (existingSessionOpen && existingNote.isBlank() && normalizedNote.isNotBlank()) {
                values.put("note", normalizedNote)
            }
            if (reopen) {
                values.putNull("ended_at")
            }
            if (values.size() > 0) {
                database.update(
                    table,
                    values,
                    "session_id = ?",
                    arrayOf(sessionId.toString())
                )
            }
        }
    }

    fun startAcquisitionSession(
        sessionId: Long,
        note: String,
        startedAt: Long = System.currentTimeMillis(),
        sensorDeviceId: String = "",
        externalCommand: Boolean = false
    ) {
        upsertSession(
            database = writableDatabase,
            table = "acquisition_sessions",
            sessionId = sessionId,
            note = note,
            startedAt = startedAt,
            reopen = true,
            sensorDeviceId = sensorDeviceId,
            externalCommand = externalCommand
        )
        notifyAcquisitionsChanged()
    }

    fun ensureAcquisitionSession(
        sessionId: Long,
        note: String,
        startedAt: Long,
        sensorDeviceId: String = "",
        externalCommand: Boolean = false
    ) {
        upsertSession(
            database = writableDatabase,
            table = "acquisition_sessions",
            sessionId = sessionId,
            note = note,
            startedAt = startedAt,
            reopen = false,
            sensorDeviceId = sensorDeviceId,
            externalCommand = externalCommand
        )
    }

    fun finishAcquisitionSession(
        sessionId: Long,
        endedAt: Long = System.currentTimeMillis()
    ) {
        if (sessionId <= 0L) return
        val changed = writableDatabase.update(
            "acquisition_sessions",
            ContentValues().apply {
                put("ended_at", endedAt.coerceAtLeast(0L))
            },
            "session_id = ? AND ended_at IS NULL",
            arrayOf(sessionId.toString())
        )
        if (changed > 0) notifyAcquisitionsChanged()
    }

    /** A lost RAM job has no shutdown timestamp: use only the last recorded event. */
    fun finishInterruptedAcquisitionSession(sessionId: Long, sensorDeviceId: String): Boolean {
        if (sessionId <= 0L) return false
        val closed = writableDatabase.compileStatement("""
            UPDATE acquisition_sessions SET ended_at = COALESCE(
                (SELECT MAX(timestamp) FROM acquisitions WHERE session_id = acquisition_sessions.session_id), started_at)
            WHERE session_id = ? AND ended_at IS NULL
        """.trimIndent()).use { it.bindLong(1, sessionId); it.executeUpdateDelete() > 0 }
        if (closed) notifyAcquisitionsChanged()
        return closed
    }

    fun startAlertSession(
        sessionId: Long,
        note: String,
        startedAt: Long = System.currentTimeMillis(),
        sensorDeviceId: String = ""
    ) {
        upsertSession(
            database = writableDatabase,
            table = "alert_sessions",
            sessionId = sessionId,
            note = note,
            startedAt = startedAt,
            reopen = true,
            sensorDeviceId = sensorDeviceId
        )
        notifyAlertsChanged()
    }

    fun readAcquisitionSessionNote(sessionId: Long): String =
        readSessionNote("acquisition_sessions", sessionId)

    /** Notes are locked only while their phone-owned recording is running. */
    fun canEditAcquisitionSessionNote(sessionId: Long?): Boolean =
        sessionId == null || canEditAcquisitionSessionNote(readableDatabase, sessionId)

    private fun canEditAcquisitionSessionNote(database: SQLiteDatabase, sessionId: Long): Boolean {
        if (sessionId <= 0L) return false
        val recordContext = database.rawQuery(
            "SELECT COUNT(*), COALESCE(MAX(automatic),0) FROM acquisitions WHERE session_id=?",
            arrayOf(sessionId.toString())
        ).use { it.moveToFirst(); it.getInt(0) to (it.getInt(1) != 0) }
        val open = database.rawQuery("SELECT ended_at FROM acquisition_sessions WHERE session_id=?",
            arrayOf(sessionId.toString())).use { it.moveToFirst() && it.isNull(0) }
        if (open && (recordContext.first == 0 || recordContext.second)) return false
        val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (preferences.getBoolean(KEY_AUTO_ENABLED, false) &&
            preferences.getLong(KEY_AUTO_SESSION_ID,0L) == sessionId) return false
        return preferences.getLong(KEY_LAST_MANUAL_SESSION_ID,0L) != sessionId
    }

    fun canEditAlertSessionNote(sessionId: Long?): Boolean =
        sessionId != null && canEditAlertSessionNote(readableDatabase, sessionId)

    private fun canEditAlertSessionNote(database: SQLiteDatabase, sessionId: Long): Boolean {
        if (sessionId <= 0L) return false
        return database.query(
            "alert_sessions", arrayOf("ended_at"), "session_id = ?",
            arrayOf(sessionId.toString()), null, null, null, "1"
        ).use { cursor -> cursor.moveToFirst() && !cursor.isNull(0) }
    }

    fun updateAcquisitionNote(recordId: Long, note: String): Boolean {
        if (recordId <= 0L) return false
        val database = writableDatabase
        val recordContext =
            database.query(
                "acquisitions",
                arrayOf("automatic", "session_id"),
                "id = ?",
                arrayOf(recordId.toString()),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    null
                } else {
                    val automatic = cursor.getInt(0) != 0
                    val sessionId = if (cursor.isNull(1)) null else cursor.getLong(1)
                    automatic to sessionId
                }
            } ?: return false

        val updated =
            if (recordContext.first && recordContext.second != null) {
                updateAcquisitionSessionNote(requireNotNull(recordContext.second), note)
            } else {
                database.beginTransaction()
                try {
                    if (recordContext.second != null &&
                        !canEditAcquisitionSessionNote(database, requireNotNull(recordContext.second))) {
                        return false
                    }
                    val changed = database.update(
                        "acquisitions",
                        ContentValues().apply { put("note", limitUvirNote(note).trim()) },
                        "id = ?", arrayOf(recordId.toString())
                    ) > 0
                    database.setTransactionSuccessful()
                    changed
                } finally {
                    database.endTransaction()
                }
            }
        if (updated && !(recordContext.first && recordContext.second != null)) {
            notifyAcquisitionsChanged()
        }
        return updated
    }

    fun updateAcquisitionSessionNote(sessionId: Long, note: String): Boolean {
        if (sessionId <= 0L) return false
        val database = writableDatabase
        val normalizedNote = limitUvirNote(note).trim()
        val automaticSession =
            database.rawQuery(
                "SELECT EXISTS(SELECT 1 FROM acquisitions WHERE session_id = ? AND automatic = 1)",
                arrayOf(sessionId.toString())
            ).use { cursor ->
                cursor.moveToFirst() && cursor.getInt(0) != 0
            }
        var updated = false
        database.beginTransaction()
        try {
            if (!canEditAcquisitionSessionNote(database, sessionId)) return false
            updated =
                if (automaticSession) {
                    createSessionTables(database)
                    database.update(
                        "acquisition_sessions",
                        ContentValues().apply { put("note", normalizedNote) },
                        "session_id = ?",
                        arrayOf(sessionId.toString())
                    ) > 0
                } else {
                    database.update(
                        "acquisitions",
                        ContentValues().apply { put("note", normalizedNote) },
                        "session_id = ?",
                        arrayOf(sessionId.toString())
                    ) > 0
                }
            if (updated && automaticSession) {
                database.update(
                    "acquisitions",
                    ContentValues().apply { put("note", "") },
                    "session_id = ?",
                    arrayOf(sessionId.toString())
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        if (updated) notifyAcquisitionsChanged()
        return updated
    }

    fun readAcquisitionSessionVariantsPerPosition(sessionId: Long): Int {
        if (sessionId <= 0L) return 1
        createSessionTables(readableDatabase)
        return readableDatabase.query(
            "acquisition_sessions",
            arrayOf("variants_per_position"),
            "session_id = ?",
            arrayOf(sessionId.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0).coerceAtLeast(1) else 1
        }
    }

    fun updateAcquisitionSessionVariantsPerPosition(
        sessionId: Long,
        variantsPerPosition: Int
    ): Boolean {
        if (sessionId <= 0L) return false
        val database = writableDatabase
        createSessionTables(database)
        val normalizedVariants = variantsPerPosition.coerceAtLeast(1)
        val acquisitionCount =
            database.rawQuery(
                "SELECT COUNT(*) FROM acquisitions WHERE session_id = ?",
                arrayOf(sessionId.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        if (
            normalizedVariants > 1 &&
            (acquisitionCount <= 0 || acquisitionCount % normalizedVariants != 0)
        ) {
            return false
        }

        var updated = false
        database.beginTransaction()
        try {
            updated = database.update(
                "acquisition_sessions",
                ContentValues().apply {
                    put("variants_per_position", normalizedVariants)
                },
                "session_id = ?",
                arrayOf(sessionId.toString())
            ) > 0
            if (updated) {
                if (normalizedVariants > 1) {
                    database.execSQL(
                        """
                        UPDATE acquisitions
                        SET
                            variant_index = ((session_sequence - 1) % ?) + 1,
                            position_index = CAST((session_sequence - 1) / ? AS INTEGER) + 1
                        WHERE session_id = ? AND session_sequence IS NOT NULL
                        """.trimIndent(),
                        arrayOf<Any>(
                            normalizedVariants,
                            normalizedVariants,
                            sessionId
                        )
                    )
                } else {
                    database.execSQL(
                        """
                        UPDATE acquisitions
                        SET variant_index = NULL, position_index = NULL
                        WHERE session_id = ?
                        """.trimIndent(),
                        arrayOf(sessionId)
                    )
                }
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        if (updated) notifyAcquisitionsChanged()
        return updated
    }

    private fun acquisitionVariantMetadata(
        database: SQLiteDatabase,
        sessionId: Long?,
        sessionSequence: Int?
    ): Pair<Int?, Int?> {
        if (sessionId == null || sessionSequence == null || sessionSequence <= 0) {
            return null to null
        }
        val variantsPerPosition =
            database.query(
                "acquisition_sessions",
                arrayOf("variants_per_position"),
                "session_id = ?",
                arrayOf(sessionId.toString()),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0).coerceAtLeast(1) else 1
            }
        if (variantsPerPosition <= 1) return null to null

        val variantIndex = ((sessionSequence - 1) % variantsPerPosition) + 1
        val positionIndex = ((sessionSequence - 1) / variantsPerPosition) + 1
        return positionIndex to variantIndex
    }

    fun readAlertSessionNote(sessionId: Long): String =
        readSessionNote("alert_sessions", sessionId)

    fun updateAlertNote(alertId: Long, note: String): Boolean {
        if (alertId <= 0L) return false
        val sessionId =
            readableDatabase.query(
                "alerts",
                arrayOf("session_id"),
                "id = ?",
                arrayOf(alertId.toString()),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            } ?: return false
        return updateAlertSessionNote(sessionId, note)
    }

    fun updateAlertSessionNote(sessionId: Long, note: String): Boolean {
        if (sessionId <= 0L) return false
        val database = writableDatabase
        var updated = false
        database.beginTransaction()
        try {
            if (!canEditAlertSessionNote(database, sessionId)) return false
            updated = database.update(
                "alert_sessions",
                ContentValues().apply { put("note", limitUvirNote(note).trim()) },
                "session_id = ?",
                arrayOf(sessionId.toString())
            ) > 0
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        if (updated) notifyAlertsChanged()
        return updated
    }

    private fun readSessionNote(table: String, sessionId: Long): String {
        if (sessionId <= 0L) return ""
        return readableDatabase.query(
            table,
            arrayOf("note"),
            "session_id = ?",
            arrayOf(sessionId.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }
    }

    fun ensureAlertSession(
        sessionId: Long,
        note: String = "",
        startedAt: Long,
        sensorDeviceId: String = ""
    ) {
        upsertSession(
            database = writableDatabase,
            table = "alert_sessions",
            sessionId = sessionId,
            note = note,
            startedAt = startedAt,
            reopen = false,
            sensorDeviceId = sensorDeviceId
        )
    }

    fun finishAlertSession(
        sessionId: Long,
        endedAt: Long = System.currentTimeMillis()
    ) {
        if (sessionId <= 0L) return
        val changed = writableDatabase.update(
            "alert_sessions",
            ContentValues().apply {
                put("ended_at", endedAt.coerceAtLeast(0L))
            },
            "session_id = ? AND ended_at IS NULL",
            arrayOf(sessionId.toString())
        )
        if (changed > 0) notifyAlertsChanged()
    }

    fun currentSessionCounter(): Long {
        val database = readableDatabase
        createCountersTable(database)
        return database.rawQuery(
            """
            SELECT value
            FROM uvir_counters
            WHERE name = 'session_id'
            """.trimIndent(),
            null
        ).use {
            if (it.moveToFirst()) {
                it.getLong(0)
            } else {
                0L
            }
        }
    }

    private fun updateSessionCounterFromStoredData(
        database: SQLiteDatabase
    ) {
        createCountersTable(database)
        val largestStoredSession =
            database.rawQuery(
                """
                SELECT MAX(session_id)
                FROM (
                    SELECT session_id FROM acquisitions
                    UNION ALL
                    SELECT session_id FROM alerts
                )
                """.trimIndent(),
                null
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    cursor.getLong(0)
                } else {
                    0L
                }
            }
        database.execSQL(
            """
            UPDATE uvir_counters
            SET value = ?
            WHERE name = 'session_id'
            """.trimIndent(),
            arrayOf(largestStoredSession)
        )
    }

    private fun acquisitionCounter(
        database: SQLiteDatabase
    ): Long {
        return database.rawQuery(
            """
            SELECT seq
            FROM sqlite_sequence
            WHERE name = 'acquisitions'
            """.trimIndent(),
            null
        ).use {
            if (it.moveToFirst()) {
                it.getLong(0)
            } else {
                0L
            }
        }
    }

    fun currentAcquisitionCounter(): Long =
        acquisitionCounter(readableDatabase)

    fun nextSequenceForSession(
        sessionId: Long
    ): Int =
        readableDatabase.rawQuery(
            """
            SELECT COALESCE(MAX(session_sequence), 0) + 1
            FROM acquisitions
            WHERE session_id = ?
            """.trimIndent(),
            arrayOf(sessionId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getInt(0).coerceAtLeast(1)
            } else {
                1
            }
        }

    fun acquisitionCountForSession(
        sessionId: Long
    ): Int =
        readableDatabase.rawQuery(
            """
            SELECT COUNT(*)
            FROM acquisitions
            WHERE session_id = ?
            """.trimIndent(),
            arrayOf(sessionId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getInt(0).coerceAtLeast(0)
            } else {
                0
            }
        }

    fun manualSessionBelongsToSensor(
        sessionId: Long,
        sensorDeviceId: String,
        recordTimestamp: Long = Long.MAX_VALUE
    ): Boolean = false

    fun manualSessionNote(sessionId: Long): String = readableDatabase.rawQuery(
        """
        SELECT note FROM acquisitions
        WHERE session_id = ? AND automatic = 0 AND TRIM(note) <> ''
        ORDER BY timestamp, id LIMIT 1
        """.trimIndent(),
        arrayOf(sessionId.toString())
    ).use { if (it.moveToFirst()) it.getString(0) else "" }

    fun acquisitionSessionUsesExternalCommand(sessionId: Long): Boolean = false

    fun saveAcquisition(
        sample: SensorSample,
        note: String,
        automatic: Boolean = false,
        sessionId: Long? = null,
        sessionSequence: Int? = null,
        sensorDeviceId: String = "",
        externalCommand: Boolean = false,
        timestamp: Long = System.currentTimeMillis()
    ): Long {

        val normalizedNote = limitUvirNote(note).trim()
        val database = writableDatabase

        val (positionIndex, variantIndex) =
            acquisitionVariantMetadata(database, sessionId, sessionSequence)
        val values = ContentValues().apply {
            put("timestamp", timestamp.coerceAtLeast(0L))
            put("note", normalizedNote)
            put("automatic", if (automatic) 1 else 0)

            if (sessionId != null) {
                put(
                    "session_id",
                    sessionId
                )
            }

            if (sessionSequence != null) {
                put(
                    "session_sequence",
                    sessionSequence
                )
            }
            positionIndex?.let { put("position_index", it) }
            variantIndex?.let { put("variant_index", it) }

            put("uvc", sample.uvc)
            put("uvb", sample.uvb)
            put("uva", sample.uva)

            put("violetto", sample.violetto)
            put("blu", sample.blu)
            put("verde", sample.verde)
            put("giallo", sample.giallo)
            put("arancione", sample.arancione)
            put("rosso", sample.rosso)

            put("f8", sample.f8)
            put("nir", sample.nir)
            put("quality_flags", sample.qualityFlags)
                    put("optical_window", sample.opticalWindow?.toJson()?.toString())
        }

        val result = database.insert(
            "acquisitions",
            null,
            values
        )
        if (result != -1L) {
            notifyAcquisitionsChanged()
        }
        return result
    }

    fun saveRecoveredAcquisition(
        timestamp: Long,
        sample: SensorSample,
        note: String,
        sessionId: Long,
        sequence: Int,
        sensorDeviceId: String,
        sensorRecordId: Long,
        externalCommand: Boolean? = null,
        externalSession: Boolean = false,
        manualSession: Boolean = false
    ): Boolean {
        return false
    }

    fun hasSensorAcquisition(
        sensorDeviceId: String,
        sensorRecordId: Long
    ): Boolean = false

    fun readSavedRecords(
        sensorId: Long? = null
    ): List<SavedRecordSummary> {
        return readableDatabase.rawQuery("""
            SELECT a.*, CASE WHEN a.automatic=1 AND s.session_id IS NOT NULL
                THEN s.note ELSE a.note END AS resolved_note
            FROM acquisitions a LEFT JOIN acquisition_sessions s ON s.session_id=a.session_id
            ORDER BY a.timestamp DESC,a.id DESC
        """.trimIndent(), null).use { c -> buildList {
            fun long(name: String) = c.getLong(c.getColumnIndexOrThrow(name))
            fun optional(name: String): Int? = c.getColumnIndexOrThrow(name).let { if (c.isNull(it)) null else c.getInt(it) }
            while(c.moveToNext()) add(SavedRecordSummary(
                id=long("id"), timestamp=long("timestamp"), note=c.getString(c.getColumnIndexOrThrow("resolved_note")),
                automatic=long("automatic") != 0L,
                sessionId=c.getColumnIndexOrThrow("session_id").let { if(c.isNull(it)) null else c.getLong(it) },
                sessionSequence=optional("session_sequence"),positionIndex=optional("position_index"),variantIndex=optional("variant_index")
            ))
        } }
    }

    fun readRecord(id: Long): SavedRecordDetail? {
        return readableDatabase.rawQuery("""
            SELECT a.*, CASE WHEN a.automatic=1 AND s.session_id IS NOT NULL
                THEN s.note ELSE a.note END AS resolved_note
            FROM acquisitions a LEFT JOIN acquisition_sessions s ON s.session_id=a.session_id WHERE a.id=?
        """.trimIndent(), arrayOf(id.toString())).use { c ->
            if (!c.moveToFirst()) return null
            fun d(name:String)=c.getDouble(c.getColumnIndexOrThrow(name))
            fun optional(name:String):Int?=c.getColumnIndexOrThrow(name).let { if(c.isNull(it)) null else c.getInt(it) }
            SavedRecordDetail(id=id,timestamp=c.getLong(c.getColumnIndexOrThrow("timestamp")),
                note=c.getString(c.getColumnIndexOrThrow("resolved_note")),automatic=d("automatic") != 0.0,
                sample=SensorSample(uvc=d("uvc"),uvb=d("uvb"),uva=d("uva"),violetto=d("violetto"),blu=d("blu"),
                    verde=d("verde"),giallo=d("giallo"),arancione=d("arancione"),rosso=d("rosso"),f8=d("f8"),nir=d("nir"),
                    qualityFlags=d("quality_flags").toInt(),
                    opticalWindow=OpticalWindowSettings.fromStoredOrNull(c.getString(c.getColumnIndexOrThrow("optical_window")))),
                sessionId=c.getColumnIndexOrThrow("session_id").let { if(c.isNull(it)) null else c.getLong(it) },
                sessionSequence=optional("session_sequence"),positionIndex=optional("position_index"),variantIndex=optional("variant_index"))
        }
    }

    fun deleteRecord(id: Long): Int {
        return deleteRecords(listOf(id))
    }

    fun deleteRecords(ids: Collection<Long>): Int {
        val normalizedIds = ids.filter { it > 0L }.distinct()
        if (normalizedIds.isEmpty()) return 0

        val database = writableDatabase
        val affectedSessionIds = linkedSetOf<Long>()
        var deletedRows = 0
        database.beginTransaction()
        try {
            createSessionTables(database)
            // Stay below SQLite's host-parameter limit even for large selections.
            normalizedIds.chunked(900).forEach { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                val arguments = chunk.map(Long::toString).toTypedArray()
                database.rawQuery(
                    "SELECT DISTINCT session_id FROM acquisitions " +
                        "WHERE id IN ($placeholders) AND session_id IS NOT NULL",
                    arguments
                ).use { cursor ->
                    while (cursor.moveToNext()) affectedSessionIds += cursor.getLong(0)
                }
                deletedRows += database.delete(
                    "acquisitions",
                    "id IN ($placeholders)",
                    arguments
                )
            }

            affectedSessionIds.forEach { sessionId ->
                val remainingRecords = database.rawQuery(
                    "SELECT COUNT(*) FROM acquisitions WHERE session_id = ?",
                    arrayOf(sessionId.toString())
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
                if (remainingRecords == 0) {
                    // A deleted session must not leave note or variant metadata behind.
                    database.delete(
                        "acquisition_sessions",
                        "session_id = ?",
                        arrayOf(sessionId.toString())
                    )
                } else {
                    val variantsPerPosition = database.query(
                        "acquisition_sessions",
                        arrayOf("variants_per_position"),
                        "session_id = ?",
                        arrayOf(sessionId.toString()),
                        null,
                        null,
                        null,
                        "1"
                    ).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getInt(0).coerceAtLeast(1) else 1
                    }
                    if (variantsPerPosition > 1) {
                        database.update(
                            "acquisition_sessions",
                            ContentValues().apply { put("variants_per_position", 1) },
                            "session_id = ?",
                            arrayOf(sessionId.toString())
                        )
                        database.execSQL(
                            "UPDATE acquisitions SET position_index = NULL, " +
                                "variant_index = NULL WHERE session_id = ?",
                            arrayOf(sessionId)
                        )
                    }
                }
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        if (deletedRows > 0) notifyAcquisitionsChanged()
        return deletedRows
    }

    fun deleteAllAcquisitions(): Int {
        val database = writableDatabase
        var deletedRows = 0

        database.beginTransaction()
        try {
            deletedRows = database.delete(
                "acquisitions",
                null,
                null
            )
            database.delete(
                "acquisition_sessions",
                null,
                null
            )

            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        clearRememberedManualSession(
            appContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
        )

        return deletedRows
    }

    fun resetAllCounters(): Int {
        clearCachedSensorOperationalContexts(
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        )
        val database = writableDatabase
        var deletedRows = 0

        database.beginTransaction()
        try {
            deletedRows += database.delete(
                "acquisitions",
                null,
                null
            )
            database.delete("acquisition_sessions", null, null)
            database.execSQL(
                "DELETE FROM sqlite_sequence " +
                    "WHERE name IN ('acquisitions', 'alerts')"
            )
            deletedRows += database.delete(
                "alerts",
                null,
                null
            )
            database.delete("alert_sessions", null, null)
            createCountersTable(database)
            database.execSQL(
                """
                UPDATE uvir_counters
                SET value = 0
                WHERE name = 'session_id'
                """.trimIndent()
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        clearRememberedManualSession(
            appContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
        )

        return deletedRows
    }

    fun deleteAllSensorProfiles(): Int {
        return 0
    }

    fun resetAcquisitionCounters(): Int {
        val database = writableDatabase
        var deletedRows = 0

        database.beginTransaction()
        try {
            deletedRows = database.delete(
                "acquisitions",
                null,
                null
            )
            database.delete("acquisition_sessions", null, null)
            database.execSQL(
                "DELETE FROM sqlite_sequence " +
                    "WHERE name = 'acquisitions'"
            )
            updateSessionCounterFromStoredData(database)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        clearRememberedManualSession(
            appContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
        )

        return deletedRows
    }

    fun resetAlertCounters(): Int {
        val database = writableDatabase
        var deletedRows = 0

        database.beginTransaction()
        try {
            deletedRows = database.delete(
                "alerts",
                null,
                null
            )
            database.delete("alert_sessions", null, null)
            database.execSQL(
                "DELETE FROM sqlite_sequence " +
                    "WHERE name = 'alerts'"
            )
            updateSessionCounterFromStoredData(database)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        return deletedRows
    }

    fun readAllRecords():
            List<SavedRecordDetail> {
        return readSavedRecords()
            .mapNotNull {
                readRecord(it.id)
            }
    }

    fun readSessionRecords(
        sessionId: Long
    ): List<SavedRecordDetail> {
        return readSavedRecords()
            .asSequence()
            .filter {
                it.sessionId == sessionId
            }
            .mapNotNull {
                readRecord(it.id)
            }
            .sortedBy {
                it.timestamp
            }
            .toList()
    }

    fun insertThresholdAlertLog(
        violations: List<ThresholdAlertViolation>,
        timestamp: Long = System.currentTimeMillis(),
        sessionId: Long? = null,
        sensorDeviceId: String = "",
        qualityFlags: Int = 0,
        opticalWindow: OpticalWindowSettings? = null
    ): Long {
        if (violations.isEmpty()) {
            return -1L
        }

        val details =
            violations.joinToString(";") { violation ->
                listOf(
                    violation.rule.metric.name,
                    violation.value.toString(),
                    violation.rule.direction.name,
                    violation.rule.threshold.toString()
                ).joinToString("|")
            }

        val database = writableDatabase

        val normalizedSessionId = sessionId?.takeIf { it > 0L }
        var result = -1L
        database.beginTransaction()
        try {
            normalizedSessionId?.let {
                upsertSession(
                    database = database,
                    table = "alert_sessions",
                    sessionId = it,
                    note = "",
                    startedAt = timestamp,
                    reopen = false,
                    sensorDeviceId = sensorDeviceId
                )
            }
            result = database.insert(
                "alerts",
                null,
                ContentValues().apply {
                    put("timestamp", timestamp)
                    put("details", details)
                    put("quality_flags", qualityFlags)
                    put("optical_window", opticalWindow?.toJson()?.toString())
                    normalizedSessionId?.let {
                        put("session_id", it)
                        put(
                            "session_sequence",
                            nextAlertSequenceForSession(database, it)
                        )
                    }
                }
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        if (result != -1L) {
            notifyAlertsChanged()
        }
        return result
    }

    internal fun hasMatchingSensorAlert(deviceId: String, timestamp: Long, details: String): Boolean = false

    fun insertRecoveredThresholdAlert(
        timestamp: Long,
        details: String,
        sensorDeviceId: String,
        sensorRecordId: Long,
        sessionId: Long? = null,
        qualityFlags: Int = 0,
        opticalWindow: OpticalWindowSettings? = null
    ): Boolean {
        return false
    }

    private fun nextAlertSequenceForSession(
        database: SQLiteDatabase,
        sessionId: Long
    ): Int =
        database.rawQuery(
            """
            SELECT COALESCE(MAX(session_sequence), 0) + 1
            FROM alerts
            WHERE session_id = ?
            """.trimIndent(),
            arrayOf(sessionId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getInt(0).coerceAtLeast(1)
            } else {
                1
            }
        }

    private fun sensorRecordExists(
        database: SQLiteDatabase,
        table: String,
        sensorDeviceId: String,
        sensorRecordId: Long
    ): Boolean {
        return false
    }

    fun readThresholdAlertLog(
        limit: Int = 10_000,
        sensorId: Long? = null
    ): List<ThresholdAlertLogEntry> {
        return readableDatabase.rawQuery("""
            SELECT a.id,a.timestamp,a.details,a.session_id,a.session_sequence,COALESCE(s.note,''),a.quality_flags,a.optical_window
            FROM alerts a LEFT JOIN alert_sessions s ON s.session_id=a.session_id
            ORDER BY a.timestamp DESC,a.id DESC LIMIT ?
        """.trimIndent(),arrayOf(limit.coerceIn(1,100_000).toString())).use { c -> buildList {
            while(c.moveToNext()) add(ThresholdAlertLogEntry(id=c.getLong(0),timestamp=c.getLong(1),details=c.getString(2),
                sessionId=if(c.isNull(3)) null else c.getLong(3),sessionSequence=if(c.isNull(4)) null else c.getInt(4),
                note=c.getString(5),qualityFlags=c.getInt(6),opticalWindow=OpticalWindowSettings.fromStoredOrNull(c.getString(7))))
        } }
    }

    fun deleteAllThresholdAlertLogs(): Int {
        val database = writableDatabase
        var deleted = 0
        database.beginTransaction()
        try {
            deleted = database.delete("alerts", null, null)
            database.delete("alert_sessions", null, null)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        if (deleted > 0) notifyAlertsChanged()
        return deleted
    }

    fun deleteThresholdAlertLogs(
        ids: Collection<Long>
    ): Int {
        if (ids.isEmpty()) {
            return 0
        }

        val placeholders =
            ids.joinToString(",") { "?" }

        return writableDatabase.delete(
            "alerts",
            "id IN ($placeholders)",
            ids.map { it.toString() }
                .toTypedArray()
        )
    }

    fun updateAcquisition(
        record: SavedRecordDetail
    ): Int {
        val values =
            ContentValues().apply {
                put("timestamp", record.timestamp)
                put("note", record.note)
                put(
                    "automatic",
                    if (record.automatic) 1 else 0
                )

                record.sessionId?.let {
                    put("session_id", it)
                } ?: putNull("session_id")

                record.sessionSequence?.let {
                    put("session_sequence", it)
                } ?: putNull("session_sequence")

                put("uvc", record.sample.uvc)
                put("uvb", record.sample.uvb)
                put("uva", record.sample.uva)
                put("violetto", record.sample.violetto)
                put("blu", record.sample.blu)
                put("verde", record.sample.verde)
                put("giallo", record.sample.giallo)
                put("arancione", record.sample.arancione)
                put("rosso", record.sample.rosso)
                put("f8", record.sample.f8)
                put("nir", record.sample.nir)
                put("quality_flags", record.sample.qualityFlags)
                put("optical_window", record.sample.opticalWindow?.toJson()?.toString())
            }

        return writableDatabase.update(
            "acquisitions",
            values,
            "id = ?",
            arrayOf(record.id.toString())
        )
    }

    fun replaceAllAcquisitions(
        records: List<SavedRecordDetail>,
        acquisitionCounter: Long? = null,
        sessionCounter: Long? = null
    ): Int {
        val database = writableDatabase

        database.beginTransaction()
        try {
            database.delete(
                "acquisitions",
                null,
                null
            )
            database.delete(
                "acquisition_sessions",
                null,
                null
            )

            records.forEach { record ->
                val values =
                    ContentValues().apply {
                        put("id", record.id)
                        put("timestamp", record.timestamp)
                        put("note", record.note)
                        put(
                            "automatic",
                            if (record.automatic) 1 else 0
                        )

                        record.sessionId?.let {
                            put("session_id", it)
                        } ?: putNull("session_id")

                        record.sessionSequence?.let {
                            put("session_sequence", it)
                        } ?: putNull("session_sequence")

                        put("uvc", record.sample.uvc)
                        put("uvb", record.sample.uvb)
                        put("uva", record.sample.uva)
                        put("violetto", record.sample.violetto)
                        put("blu", record.sample.blu)
                        put("verde", record.sample.verde)
                        put("giallo", record.sample.giallo)
                        put("arancione", record.sample.arancione)
                        put("rosso", record.sample.rosso)
                        put("f8", record.sample.f8)
                        put("nir", record.sample.nir)
                        put("quality_flags", record.sample.qualityFlags)
                put("optical_window", record.sample.opticalWindow?.toJson()?.toString())
                    }

                database.insertOrThrow(
                    "acquisitions",
                    null,
                    values
                )
            }

            records
                .filter { it.automatic && it.sessionId != null }
                .groupBy { requireNotNull(it.sessionId) }
                .forEach { (sessionId, sessionRecords) ->
                    val ordered = sessionRecords.sortedBy { it.timestamp }
                    upsertSession(
                        database = database,
                        table = "acquisition_sessions",
                        sessionId = sessionId,
                        note =
                            ordered.firstOrNull { it.note.isNotBlank() }
                                ?.note
                                .orEmpty(),
                        startedAt = ordered.first().timestamp,
                        reopen = false,
                        // A mixed timed session may contain individual GPIO-triggered
                        // records without becoming an external-only session.
                        externalCommand = ordered.all { it.externalCommand }
                    )
                    database.update(
                        "acquisition_sessions",
                        ContentValues().apply {
                            put("ended_at", ordered.last().timestamp)
                        },
                        "session_id = ?",
                        arrayOf(sessionId.toString())
                    )
                }

            val importedSessionCounter =
                maxOf(
                    sessionCounter ?: 0L,
                    records
                        .mapNotNull {
                            it.sessionId
                        }
                        .maxOrNull()
                        ?: 0L
                )
            createCountersTable(database)
            database.execSQL(
                """
                UPDATE uvir_counters
                SET value = MAX(value, ?)
                WHERE name = 'session_id'
                """.trimIndent(),
                arrayOf(importedSessionCounter)
            )

            val importedAcquisitionCounter =
                maxOf(
                    acquisitionCounter ?: 0L,
                    records.maxOfOrNull {
                        it.id
                    } ?: 0L
                )
            if (
                importedAcquisitionCounter >
                acquisitionCounter(database)
            ) {
                val sequenceValues =
                    ContentValues().apply {
                        put(
                            "seq",
                            importedAcquisitionCounter
                        )
                    }
                val updated =
                    database.update(
                        "sqlite_sequence",
                        sequenceValues,
                        "name = ?",
                        arrayOf("acquisitions")
                    )
                if (updated == 0) {
                    sequenceValues.put(
                        "name",
                        "acquisitions"
                    )
                    database.insertOrThrow(
                        "sqlite_sequence",
                        null,
                        sequenceValues
                    )
                }
            }

            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        clearRememberedManualSession(
            appContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
        )

        return records.size
    }
}
