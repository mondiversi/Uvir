package me.mondiversi.uvir

import android.app.Application

/** Runs before activities AND services can read a threshold or activate a sensor. */
class UvirApplication : Application() {
    internal var migratedIrradianceAtStartup = false
        private set
    internal fun consumeLegacyUiState(): Boolean = migratedIrradianceAtStartup.also { migratedIrradianceAtStartup = false }
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        migratedIrradianceAtStartup = prefs.getString(KEY_IRRADIANCE_STORAGE_UNIT, null) != UVIR_CANONICAL_IRRADIANCE_UNIT
        backupUvirPreferencesBeforeSiMigration(this)
        migrateUvirIrradiancePreferences(prefs)
    }
}
