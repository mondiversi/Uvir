package me.mondiversi.uvir

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File

internal fun createEncryptedUvirDatabaseArchive(
    source: File,
    destination: File,
    password: CharArray
): File {
    require(source.isFile) { "Database file not found" }
    require(password.size >= UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH)
    require(!destination.exists()) { "Database archive already exists" }
    val parameters = ZipParameters().apply {
        isEncryptFiles = true
        encryptionMethod = EncryptionMethod.AES
        aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
    }
    try {
        ZipFile(destination, password).addFile(source, parameters)
        check(ZipFile(destination, password).isValidZipFile) {
            "Encrypted database archive is invalid"
        }
        return destination
    } catch (error: Exception) {
        destination.delete()
        throw error
    }
}

private val UVIR_DATABASE_TABLES = listOf(
    "sensors",
    "uvir_counters",
    "acquisition_sessions",
    "alert_sessions",
    "sensor_settings",
    "sensor_alert_rules",
    "acquisitions",
    "alerts"
)

internal fun extractEncryptedUvirDatabaseArchive(
    archive: File,
    destination: File,
    password: CharArray
): File {
    require(archive.isFile) { "Database archive not found" }
    require(!destination.exists()) { "Database extraction destination already exists" }
    val zip = ZipFile(archive, password)
    check(zip.isValidZipFile) { "Invalid database archive" }
    val header = zip.fileHeaders.singleOrNull()
        ?: error("The database archive must contain exactly one file")
    check(
        header.fileName == "uvir.db" && !header.isDirectory &&
            header.isEncrypted && header.encryptionMethod == EncryptionMethod.AES &&
            header.aesExtraDataRecord?.aesKeyStrength == AesKeyStrength.KEY_STRENGTH_256
    ) { "Unsupported database archive format" }
    try {
        zip.getInputStream(header).use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        return destination
    } catch (error: Exception) {
        destination.delete()
        throw error
    }
}

private fun databaseTableColumns(database: SQLiteDatabase, table: String): List<String> =
    database.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    listOf(1, 2, 3, 5).joinToString(":") { column ->
                        cursor.getString(column) ?: ""
                    }
                )
            }
        }
    }

private fun validateImportedDatabase(source: SQLiteDatabase, target: SQLiteDatabase) {
    check(source.version == target.version) { "Unsupported database schema version" }
    source.rawQuery("PRAGMA quick_check", null).use { cursor ->
        check(cursor.moveToFirst() && cursor.getString(0) == "ok") {
            "The imported database is damaged"
        }
    }
    UVIR_DATABASE_TABLES.forEach { table ->
        val expected = databaseTableColumns(target, table)
        check(expected.isNotEmpty() && databaseTableColumns(source, table) == expected) {
            "The imported database has an incompatible $table table"
        }
    }
}

/** Replaces only SQLite data; app preferences and connection credentials are not part of this backup. */
internal suspend fun importEncryptedUvirDatabaseArchive(
    context: Context,
    database: UvirDatabaseHelper,
    archiveUri: Uri,
    password: CharArray
) = withContext(Dispatchers.IO) {
    require(password.size >= UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH)
    val temporaryDirectory = File(context.cacheDir, "uvir-db-import-${System.nanoTime()}")
    check(temporaryDirectory.mkdir()) { "Unable to create database import directory" }
    try {
        val archive = File(temporaryDirectory, "backup.zip")
        checkNotNull(context.contentResolver.openInputStream(archiveUri)) {
            "Unable to open database archive"
        }.use { input -> archive.outputStream().use { output -> input.copyTo(output) } }
        val importedFile = extractEncryptedUvirDatabaseArchive(
            archive,
            File(temporaryDirectory, "uvir.db"),
            password
        )
        val target = database.writableDatabase
        SQLiteDatabase.openDatabase(
            importedFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { source -> validateImportedDatabase(source, target) }

        // A single transaction makes the replacement all-or-nothing. The active database
        // connection and its WAL stay in place, avoiding a partially replaced database file.
        target.execSQL("ATTACH DATABASE ? AS uvir_import", arrayOf(importedFile.absolutePath))
        try {
            target.beginTransaction()
            try {
                UVIR_DATABASE_TABLES.asReversed().forEach { table ->
                    target.execSQL("DELETE FROM main.$table")
                }
                UVIR_DATABASE_TABLES.forEach { table ->
                    target.execSQL("INSERT INTO main.$table SELECT * FROM uvir_import.$table")
                }
                target.execSQL(
                    "DELETE FROM main.sqlite_sequence " +
                        "WHERE name IN ('sensors', 'acquisitions', 'alerts')"
                )
                target.execSQL(
                    "INSERT INTO main.sqlite_sequence(name, seq) " +
                        "SELECT name, seq FROM uvir_import.sqlite_sequence " +
                        "WHERE name IN ('sensors', 'acquisitions', 'alerts')"
                )
                target.setTransactionSuccessful()
            } finally {
                target.endTransaction()
            }
        } finally {
            target.execSQL("DETACH DATABASE uvir_import")
        }
        database.notifyDatabaseImported()
    } finally {
        temporaryDirectory.deleteRecursively()
    }
}

internal suspend fun shareDatabase(
    context: Context,
    database: UvirDatabaseHelper,
    password: CharArray,
    destination: UvirExportDestination = UvirExportDestination.SHARE
) {
    val exportedFile = withContext(Dispatchers.IO) {
        val sqliteDatabase = database.writableDatabase
        sqliteDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use {
            check(it.moveToFirst()) { "Database checkpoint did not return a result" }
            val busy = it.getInt(0)
            val logFrames = it.getInt(1)
            val checkpointedFrames = it.getInt(2)
            check(busy == 0 && (logFrames < 0 || checkpointedFrames == logFrames)) {
                "Database checkpoint was incomplete"
            }
        }
        val archive = File(
            uvirSharedExportDirectory(context),
            UvirExportFileFormat.ZIP.fileName(
                "${uvirExportBaseName(UvirExportContent.DATABASE)}_${System.nanoTime().toString(36)}"
            )
        )
        createEncryptedUvirDatabaseArchive(
            source = File(sqliteDatabase.path),
            destination = archive,
            password = password
        )
    }

    deliverUvirExportFiles(
        context = context,
        files = listOf(exportedFile),
        destination = destination,
        chooserTitle = context.getString(R.string.export_database),
        subject = context.getString(R.string.export_database_subject)
    )
}
