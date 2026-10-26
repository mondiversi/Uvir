package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirLiveConnectionHealthTest {
    @Test fun rebootRetiresEarlierFramesWithoutConfusingThePermanentDeviceIdentity() {
        val session = UvirBootSession()
        assertEquals(UvirBootObservation.INITIAL, session.observe("boot-A"))
        assertEquals(UvirBootObservation.SAME, session.observe("boot-A"))
        assertEquals(UvirBootObservation.RESTARTED, session.observe("boot-B"))
        assertFalse(session.accepts("boot-A"))
        assertEquals(UvirBootObservation.RETIRED, session.observe("boot-A"))
        assertTrue(session.accepts("boot-B"))
    }

    @Test fun legacyFirmwareAndACompletelyNewTransportRemainSupported() {
        val session = UvirBootSession()
        assertEquals(UvirBootObservation.SAME, session.observe(""))
        assertTrue(session.accepts(""))
        session.observe("A"); session.observe("B"); session.reset()
        assertEquals(UvirBootObservation.INITIAL, session.observe("A"))
    }

    @Test fun invalidAndExpiredSamplesAreRejectedIncludingMalformedAgeMetadata() {
        assertTrue(uvirLiveFrameFresh(true, null, null)) // Legacy firmware.
        assertFalse(uvirLiveFrameFresh(false, null, null))
        assertTrue(uvirLiveFrameFresh(true, 500, 500))
        assertFalse(uvirLiveFrameFresh(true, 501, 500))
        assertFalse(uvirLiveFrameFresh(true, -1, 500))
        assertFalse(uvirLiveFrameFresh(true, 1, null))
        assertFalse(uvirLiveFrameFresh(true, 1, Long.MAX_VALUE))
        assertTrue(uvirLiveFrameFresh(true, 15_000, 15_000))
    }

    @Test fun wifiSilenceExpiresAfterSixSecondsAndOnlyAValidReceiptRestartsTheClock() {
        val health = UvirFrameLiveness(true, 1_000)
        assertEquals(2_000L, health.pingIntervalMs)
        assertFalse(health.expired(6_999))
        assertTrue(health.expired(7_000))
        health.received(7_000)
        assertFalse(health.expired(12_999))
        assertTrue(health.expired(13_000))
    }

    @Test fun bluetoothAndInternetKeepTheirExistingTimeoutAndDiscoveryAllowance() {
        val health = UvirFrameLiveness(false, 0)
        assertEquals(4_000L, health.pingIntervalMs)
        assertFalse(health.expired(9_999)); assertTrue(health.expired(10_000))
        assertFalse(health.expired(29_999, discovery = true))
        assertTrue(health.expired(30_000, discovery = true))
    }
}
