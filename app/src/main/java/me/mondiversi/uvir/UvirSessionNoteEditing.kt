package me.mondiversi.uvir

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Observe completion and the per-sensor manual context, including disconnected sensors. */
@Composable
internal fun rememberSessionNoteEditingEnabled(
    database: UvirDatabaseHelper,
    sessionId: Long?,
    alert: Boolean = false
): Boolean {
    val context = LocalContext.current.applicationContext
    val revision by (if (alert) database.alertRevision else database.acquisitionRevision).collectAsState()
    var preferenceRevision by remember(context) { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val credentials = context.getSharedPreferences("uvir_sensor_credentials", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.endsWith(KEY_LAST_MANUAL_SESSION_ID) ||
                key.endsWith(KEY_AUTO_ENABLED) || key.endsWith(KEY_AUTO_SESSION_ID)) {
                preferenceRevision++
            }
        }
        val selectionListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            preferenceRevision++
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        credentials.registerOnSharedPreferenceChangeListener(selectionListener)
        onDispose {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            credentials.unregisterOnSharedPreferenceChangeListener(selectionListener)
        }
    }
    // A session is locked until its state has been checked, avoiding an enabled first frame.
    val enabled by produceState(sessionId == null && !alert,
        database, sessionId, alert, revision, preferenceRevision) {
        value = withContext(Dispatchers.IO) {
            if (alert) database.canEditAlertSessionNote(sessionId)
            else database.canEditAcquisitionSessionNote(sessionId)
        }
    }
    return enabled
}
