package me.mondiversi.uvir

import android.content.Context
import android.content.SharedPreferences

// =====================================================
// PREFERENZE UI
// =====================================================

internal const val PREFS_NAME = UVIR_PREFERENCES_NAME
internal const val BIOLOGICAL_MODEL_VERSION = "v1"

internal const val KEY_AUTO_ENABLED = "auto_enabled"
internal const val KEY_AUTO_INTERVAL_SECONDS = "auto_interval_seconds"
internal const val KEY_AUTO_NOTE = "auto_note"
internal const val KEY_AUTO_NEXT_SAVE_MS = "auto_next_save_ms"
internal const val KEY_AUTO_USE_START_DELAY = "auto_use_start_delay"
internal const val KEY_AUTO_START_DELAY_SECONDS = "auto_start_delay_seconds"
internal const val KEY_AUTO_USE_DURATION = "auto_use_duration"
internal const val KEY_AUTO_DURATION_SECONDS = "auto_duration_seconds"
internal const val KEY_AUTO_END_MS = "auto_end_ms"
internal const val KEY_AUTO_LIMIT_ENABLED = "auto_limit_enabled"
internal const val KEY_AUTO_MAX_COUNT = "auto_max_count"
internal const val KEY_AUTO_COMPLETED_COUNT = "auto_completed_count"
internal const val KEY_AUTO_SESSION_ID = "auto_session_id"
internal const val KEY_AUTO_SCHEDULE_KNOWN = "auto_schedule_known"
internal const val KEY_AUTO_EXTERNAL_COMMAND = "auto_external_command"
internal const val KEY_OFFLINE_REOPEN_NOTICE_PENDING =
    "offline_reopen_notice_pending"
internal const val KEY_OFFLINE_REOPEN_AUTO_ACTIVE =
    "offline_reopen_auto_active"
internal const val KEY_OFFLINE_REOPEN_ALERTS_ACTIVE =
    "offline_reopen_alerts_active"
internal const val KEY_OFFLINE_REOPEN_NEXT_SAVE_MS =
    "offline_reopen_next_save_ms"
internal const val KEY_OFFLINE_REOPEN_END_MS =
    "offline_reopen_end_ms"
internal const val KEY_OFFLINE_REOPEN_COMPLETED_COUNT =
    "offline_reopen_completed_count"

internal const val KEY_MANUAL_SAVE_MODE = "manual_save_mode"
internal const val KEY_LAST_MANUAL_SESSION_ID =
    "last_manual_session_id"
internal const val LEGACY_KEY_LAST_MANUAL_SESSION_ACTIVITY_MS =
    "last_manual_session_activity_ms"

internal enum class ManualSaveMode(
    val storedValue: String
) {
    SINGLE("single"),
    LAST_MANUAL_SESSION("last_manual_session"),
    NEW_MANUAL_SESSION("new_manual_session");

    companion object {
        fun fromStoredValue(value: String?): ManualSaveMode =
            entries.firstOrNull {
                it.storedValue == value
            } ?: SINGLE
    }
}

enum class SensorConnectionMode {
    USB,
    WIFI,
    BLUETOOTH,
    INTERNET;

    companion object {
        fun fromStoredValue(
            value: String?
        ): SensorConnectionMode =
            entries.firstOrNull {
                it.name == value
            } ?: USB
    }
}

enum class AppLanguage(
    val storedValue: String,
    val languageTag: String
) {
    SYSTEM("system", ""),
    ITALIAN("it", "it"),
    ENGLISH("en", "en"),
    SPANISH("es", "es"),
    FRENCH("fr", "fr"),
    GERMAN("de", "de"),
    GREEK("el", "el"),
    PORTUGUESE("pt", "pt"),
    // Android versions that predate the modern BCP-47 mapping expose
    // Hebrew through the legacy "iw" locale code.
    HEBREW("he", "iw"),
    HINDI("hi", "hi"),
    ARABIC("ar", "ar"),
    PERSIAN("fa", "fa"),
    RUSSIAN("ru", "ru"),
    TURKISH("tr", "tr"),
    CHINESE("zh", "zh-CN"),
    JAPANESE("ja", "ja"),
    KOREAN("ko", "ko-KR"),
    SWAHILI("sw", "sw");

    companion object {
        fun fromStoredValue(
            value: String?
        ): AppLanguage =
            entries.firstOrNull {
                it.storedValue == value
            } ?: SYSTEM
    }
}

internal data class ManualSessionChoice(
    val mode: ManualSaveMode,
    val lastSessionId: Long?
)

internal fun rememberedManualSessionChoice(
    preferences: SharedPreferences
): ManualSessionChoice {
    val lastSessionId =
        preferences.getLong(
            KEY_LAST_MANUAL_SESSION_ID,
            0L
        ).takeIf { it > 0L }
    val savedMode =
        ManualSaveMode.fromStoredValue(
            preferences.getString(
                KEY_MANUAL_SAVE_MODE,
                ManualSaveMode.SINGLE.storedValue
            )
        )

    return if (lastSessionId != null) {
        ManualSessionChoice(
            mode = savedMode,
            lastSessionId = lastSessionId
        )
    } else {
        preferences.edit()
            .putString(
                KEY_MANUAL_SAVE_MODE,
                ManualSaveMode.SINGLE.storedValue
            )
            .remove(KEY_LAST_MANUAL_SESSION_ID)
            .remove(LEGACY_KEY_LAST_MANUAL_SESSION_ACTIVITY_MS)
            .apply()
        ManualSessionChoice(
            mode = ManualSaveMode.SINGLE,
            lastSessionId = null
        )
    }
}

internal fun clearRememberedManualSession(
    preferences: SharedPreferences
) {
    preferences.edit()
        .putString(
            KEY_MANUAL_SAVE_MODE,
            ManualSaveMode.SINGLE.storedValue
        )
        .remove(KEY_LAST_MANUAL_SESSION_ID)
        .remove(LEGACY_KEY_LAST_MANUAL_SESSION_ACTIVITY_MS)
        .apply()
}

// Used only to preserve the stop deadline of a session started by an older build.
internal const val LEGACY_KEY_AUTO_USE_END = "auto_use_end"

internal const val KEY_SAMPLES_PER_MEASUREMENT = "samples_per_measurement"
internal const val KEY_SAMPLE_SPACING_MS = "sample_spacing_ms"
internal const val DEFAULT_SAMPLE_SPACING_MS = 150L

internal fun readSampleSpacingMs(preferences: SharedPreferences): Long =
    preferences.getLong(KEY_SAMPLE_SPACING_MS, DEFAULT_SAMPLE_SPACING_MS)
        .coerceIn(150L, 5_000L)

internal const val KEY_DISCARD_EXTREMES = "discard_extremes"
internal const val KEY_THRESHOLD_ALERT_ENABLED =
    "threshold_alert_enabled"
internal const val KEY_THRESHOLD_ALERT_CHANNEL =
    "threshold_alert_channel"
internal const val KEY_THRESHOLD_ALERT_DIRECTION =
    "threshold_alert_direction"
internal const val KEY_THRESHOLD_ALERT_VALUE =
    "threshold_alert_value"
internal const val KEY_THRESHOLD_ALERT_DURATION_SECONDS =
    "threshold_alert_duration_seconds"
internal const val KEY_THRESHOLD_ALERT_SOUND =
    "threshold_alert_sound"
internal const val KEY_THRESHOLD_ALERT_VOLUME =
    "threshold_alert_volume"
internal const val KEY_THRESHOLD_ALERT_REPEAT_SECONDS =
    "threshold_alert_repeat_seconds"
internal const val KEY_THRESHOLD_ALERT_SESSION_ID =
    "threshold_alert_session_id"
internal const val KEY_THRESHOLD_ALERT_NOTE =
    "threshold_alert_note"
internal const val KEY_VIEW_MODE = "view_mode"
internal const val KEY_USE_FAKE_SENSOR_DATA =
    "use_fake_sensor_data"
internal const val KEY_FAKE_SENSOR_OUT_OF_RANGE =
    "fake_sensor_out_of_range"
internal const val KEY_SENSOR_CONNECTION_MODE =
    "sensor_connection_mode"
internal const val KEY_SENSOR_AUTONOMOUS_RECORDING =
    "sensor_autonomous_recording"
internal const val KEY_SENSOR_AUTOMATIC_SHUTDOWN_ENABLED =
    "sensor_automatic_shutdown_enabled"
internal const val KEY_SENSOR_AUTOMATIC_SHUTDOWN_SECONDS =
    "sensor_automatic_shutdown_seconds"
internal const val KEY_SENSOR_STATUS_LED_ENABLED =
    "sensor_status_led_enabled"
internal const val KEY_SENSOR_STATUS_LED_BRIGHTNESS =
    "sensor_status_led_brightness"
internal const val KEY_SENSOR_STATUS_BUZZER_ENABLED =
    "sensor_status_buzzer_enabled"
internal const val KEY_SENSOR_STATUS_BUZZER_VOLUME =
    "sensor_status_buzzer_volume"
internal const val KEY_SENSOR_EXTERNAL_COMMAND_ENABLED =
    "sensor_external_command_enabled"
internal const val KEY_SENSOR_VISIBLE_CALIBRATION_FACTOR =
    "sensor_visible_calibration_factor"
internal const val KEY_SENSOR_UV_CALIBRATION_FACTOR =
    "sensor_uv_calibration_factor"
internal const val KEY_LAST_WIRELESS_SENSOR_CONNECTION_MODE =
    "last_wireless_sensor_connection_mode"
internal const val KEY_APP_LANGUAGE =
    "app_language"
internal const val KEY_PENDING_APP_LANGUAGE =
    "pending_app_language"
internal const val KEY_SETTINGS_SENSOR_CONNECTION_EXPANDED =
    "settings_sensor_connection_expanded"
internal const val KEY_SETTINGS_SENSOR_PARAMETERS_EXPANDED =
    "settings_sensor_parameters_expanded"
internal const val KEY_SETTINGS_SENSOR_CALIBRATION_EXPANDED =
    "settings_sensor_calibration_expanded"
internal const val KEY_SETTINGS_DEBUG_EXPANDED =
    "settings_debug_expanded"
internal const val KEY_SETTINGS_SAMPLING_EXPANDED =
    "settings_sampling_expanded"
internal const val KEY_SETTINGS_ALERTS_EXPANDED =
    "settings_alerts_expanded"
internal const val KEY_SETTINGS_LANGUAGE_EXPANDED =
    "settings_language_expanded"
internal const val KEY_SETTINGS_USB_EXPANDED =
    "settings_usb_expanded"
internal const val KEY_SETTINGS_BLUETOOTH_EXPANDED =
    "settings_bluetooth_expanded"
internal const val KEY_SETTINGS_WIFI_EXPANDED =
    "settings_wifi_expanded"
internal const val KEY_SETTINGS_INTERNET_EXPANDED =
    "settings_internet_expanded"
internal const val KEY_SETTINGS_COUNTERS_EXPANDED =
    "settings_counters_expanded"
internal const val KEY_SETTINGS_DATABASE_EXPORT_EXPANDED =
    "settings_database_export_expanded"
internal const val KEY_UNREAD_ACQUISITION_COUNT =
    "unread_acquisition_count"
internal const val KEY_UNREAD_ALERT_COUNT =
    "unread_alert_count"
internal const val LIVE_UI_REFRESH_MS = 500L

internal fun loadSettingsSectionExpanded(
    context: Context,
    key: String,
    defaultValue: Boolean
): Boolean =
    context
        .getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        .getBoolean(
            key,
            defaultValue
        )

internal fun saveSettingsSectionExpanded(
    context: Context,
    key: String,
    expanded: Boolean
) {
    context
        .getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        .edit()
        .putBoolean(
            key,
            expanded
        )
        .apply()
}

fun saveExpandedState(
    context: Context,
    group: SensorGroup,
    expanded: Boolean
) {
    context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean("expanded_${group.name}", expanded)
        .apply()
}

fun loadExpandedState(
    context: Context,
    group: SensorGroup
): Boolean {

    return context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(
            "expanded_${group.name}",
            true
        )
}

fun saveLiveChartMode(
    context: Context,
    showChart: Boolean
) {
    writeLiveChartMode(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        showChart
    )
}

fun loadLiveChartMode(
    context: Context
): Boolean =
    readLiveChartMode(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

internal fun readLiveChartMode(preferences: SharedPreferences): Boolean =
    preferences.getBoolean(
        "live_show_chart",
        // Use the former first island's choice until the global selector is used.
        preferences.getBoolean("sensor_group_chart_${SensorGroup.UV.name}", false)
    )

internal fun writeLiveChartMode(
    preferences: SharedPreferences,
    showChart: Boolean
) {
    preferences.edit().putBoolean("live_show_chart", showChart).apply()
}

fun saveBiologicalEffectExpanded(
    context: Context,
    group: BiologicalEffectGroup,
    expanded: Boolean
) {
    context
        .getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        .edit()
        .putBoolean(
            "biological_effect_expanded_${group.name}",
            expanded
        )
        .apply()
}

fun loadBiologicalEffectExpanded(
    context: Context,
    group: BiologicalEffectGroup
): Boolean =
    context
        .getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        .getBoolean(
            "biological_effect_expanded_${group.name}",
            true
        )

// =====================================================
// DATABASE
// =====================================================


internal data class ResolvedManualSession(
    val sessionId: Long?,
    val sequence: Int?
)

internal fun resolveManualSession(
    database: UvirDatabaseHelper,
    preferences: SharedPreferences,
    requestedMode: ManualSaveMode
): ResolvedManualSession {
    if (requestedMode == ManualSaveMode.SINGLE) {
        return ResolvedManualSession(
            sessionId = null,
            sequence = null
        )
    }

    val remembered =
        rememberedManualSessionChoice(
            preferences
        )
    val sessionId =
        if (
            requestedMode ==
            ManualSaveMode.LAST_MANUAL_SESSION
        ) {
            remembered.lastSessionId
                ?.takeIf {
                    database
                        .acquisitionCountForSession(
                            it
                        ) > 0
                }
                ?: throw IllegalStateException(
                    "No manual session is available."
                )
        } else {
            database.nextSessionId()
        }

    return ResolvedManualSession(
        sessionId = sessionId,
        sequence =
            database.nextSequenceForSession(
                sessionId
            )
    )
}

internal fun rememberManualSaveSuccess(
    preferences: SharedPreferences,
    session: ResolvedManualSession
) {
    if (session.sessionId == null) {
        clearRememberedManualSession(
            preferences
        )
    } else {
        preferences.edit()
            .putString(
                KEY_MANUAL_SAVE_MODE,
                ManualSaveMode
                    .LAST_MANUAL_SESSION
                    .storedValue
            )
            .putLong(
                KEY_LAST_MANUAL_SESSION_ID,
                session.sessionId
            )
            .remove(
                LEGACY_KEY_LAST_MANUAL_SESSION_ACTIVITY_MS
            )
            .apply()
    }
}

// =====================================================
// UTILITÀ
// =====================================================
