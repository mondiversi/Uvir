package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UvirWifiDiscoveryLogEpisodeTest {
    @Test
    fun repeatedMissesProduceOneEntryAndARecoverySummary() {
        val episode = UvirWifiDiscoveryLogEpisode()

        assertNotNull(episode.onMiss())
        repeat(168) { assertNull(episode.onMiss()) }
        assertEquals(
            "Wi-Fi sensor discovered after 169 unanswered search cycles.",
            episode.onDiscovered()
        )
        assertNull(episode.onDiscovered())
    }

    @Test
    fun aLaterSearchProducesItsOwnStartEntry() {
        val episode = UvirWifiDiscoveryLogEpisode()

        assertNull(episode.onDiscovered())
        assertNotNull(episode.onMiss())
        assertNotNull(episode.onDiscovered())
        assertNotNull(episode.onMiss())
    }
}
