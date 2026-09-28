package me.mondiversi.uvir

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val UVIR_PERSISTENCE_ATTEMPTS = 3
private const val UVIR_PERSISTENCE_RETRY_DELAY_MS = 75L

/**
 * Retries a single SQLite insert without blocking the UI thread.
 *
 * A successful insert returns a non-negative row id. The short incremental
 * delay is only paid after a real failure, so normal acquisitions retain their
 * current responsiveness.
 */
internal suspend fun retryUvirDatabaseInsert(
    attempts: Int = UVIR_PERSISTENCE_ATTEMPTS,
    retryDelayMs: Long = UVIR_PERSISTENCE_RETRY_DELAY_MS,
    operation: () -> Long
): Long {
    val boundedAttempts = attempts.coerceAtLeast(1)
    repeat(boundedAttempts) { attempt ->
        val rowId =
            withContext(Dispatchers.IO) {
                runCatching(operation).getOrDefault(-1L)
            }
        if (rowId != -1L) return rowId
        if (attempt + 1 < boundedAttempts && retryDelayMs > 0L) {
            delay(retryDelayMs * (attempt + 1L))
        }
    }
    return -1L
}
