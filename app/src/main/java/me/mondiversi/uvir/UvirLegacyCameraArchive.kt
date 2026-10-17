package me.mondiversi.uvir

import android.database.sqlite.SQLiteDatabase

/**
 * Read-only compatibility schema for version-21 backups from the retired Camera
 * prototype. No active source, estimator or recording API depends on this table.
 * Keeping it avoids deleting historical rows when upgrading/importing a backup.
 */
internal object UvirLegacyCameraArchive {
    fun createTable(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS camera_estimates (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_id TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            data_origin TEXT NOT NULL DEFAULT 'estimated',
            model_version TEXT NOT NULL,
            estimate_csv TEXT NOT NULL
        )""")
    }
}
