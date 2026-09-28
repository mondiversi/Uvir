package me.mondiversi.uvir

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UvirPersistenceRetryTest {
    @Test
    fun transientFailuresKeepTheSameInsertUntilItSucceeds() = runBlocking {
        var attempts = 0

        val rowId =
            retryUvirDatabaseInsert(attempts = 3, retryDelayMs = 0L) {
                attempts += 1
                if (attempts < 3) -1L else 42L
            }

        assertEquals(42L, rowId)
        assertEquals(3, attempts)
    }

    @Test
    fun permanentFailureStopsAtTheConfiguredLimit() = runBlocking {
        var attempts = 0

        val rowId =
            retryUvirDatabaseInsert(attempts = 3, retryDelayMs = 0L) {
                attempts += 1
                -1L
            }

        assertEquals(-1L, rowId)
        assertEquals(3, attempts)
    }

    @Test
    fun thrownWritesAreRetriedLikeFailedInsertResults() = runBlocking {
        var attempts = 0

        val rowId =
            retryUvirDatabaseInsert(attempts = 3, retryDelayMs = 0L) {
                attempts += 1
                if (attempts == 1) error("temporary database failure")
                7L
            }

        assertEquals(7L, rowId)
        assertEquals(2, attempts)
    }
}
