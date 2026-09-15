package me.mondiversi.uvir

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

private const val DATABASE_LIST_REFRESH_COALESCE_MS = 50L

@Composable
internal fun rememberLiveAcquisitionRecords(
    database: UvirDatabaseHelper
): MutableState<List<SavedRecordSummary>> =
    rememberLiveDatabaseValue(
        database = database,
        revision = database.acquisitionRevision,
        load = database::readSavedRecords
    )

@Composable
internal fun rememberLiveSensorProfiles(
    database: UvirDatabaseHelper
): MutableState<Map<Long, UvirSensorProfile>> =
    rememberLiveDatabaseValue(
        database = database,
        revision = database.sensorProfileRevision,
        load = { database.readSensorProfiles().associateBy { it.id } }
    )

@Composable
internal fun rememberLiveAlertEntries(
    database: UvirDatabaseHelper
): MutableState<List<ThresholdAlertLogEntry>> =
    rememberLiveDatabaseValue(
        database = database,
        revision = database.alertRevision,
        load = { database.readThresholdAlertLog(limit = 100_000) }
    )

/** Keeps an already visible database list current without polling. Rapid
 * synchronization inserts are coalesced into one refresh. */
@Composable
private fun <T> rememberLiveDatabaseValue(
    database: UvirDatabaseHelper,
    revision: StateFlow<Long>,
    load: () -> T
): MutableState<T> {
    val result = remember(database, revision) {
        mutableStateOf(load())
    }
    val loadedRevision = remember(database, revision) {
        mutableLongStateOf(revision.value)
    }

    LaunchedEffect(database, revision) {
        revision.collectLatest { currentRevision ->
            if (currentRevision == loadedRevision.longValue) {
                return@collectLatest
            }

            delay(DATABASE_LIST_REFRESH_COALESCE_MS)
            val refreshed = withContext(Dispatchers.IO) { load() }
            result.value = refreshed
            loadedRevision.longValue = currentRevision
        }
    }

    return result
}
