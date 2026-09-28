package me.mondiversi.uvir

import android.app.Notification
import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

/** Builds notifications in memory: never starts a transport or posts a test notification. */
class UvirConnectionNotificationTest {
    @Test fun searchingNeverUsesTheYellowHandshakeIndicator() {
        for (busy in listOf(false, true)) {
            val searching = uvirConnectionSensorState("a", false, false, false, true, busy)
            assertEquals(UvirConnectionState.SEARCHING, searching.state)
            assertEquals(UvirStatusIndicator(UvirStatusDot.RED, busy), searching.indicator)
            val handshake = uvirConnectionSensorState("a", false, false, true, false, busy)
            assertEquals(UvirConnectionState.CONNECTING, handshake.state)
            assertEquals(UvirStatusIndicator(UvirStatusDot.YELLOW, true), handshake.indicator)
            val connected = uvirConnectionSensorState("a", false, true, false, false, busy)
            assertEquals(UvirStatusIndicator(UvirStatusDot.GREEN, busy), connected.indicator)
            val disconnected = uvirConnectionSensorState("a", false, false, false, false, busy)
            assertEquals(UvirStatusIndicator(UvirStatusDot.RED, busy), disconnected.indicator)
        }
    }
    private fun context(language: String = "it") = InstrumentationRegistry.getInstrumentation().targetContext
        .let { base -> base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }) }
    private val profiles = listOf(
        UvirSensorProfile(1, "A", "Portatile", 0, 0),
        UvirSensorProfile(2, "B", "Fisso", 0, 0),
        UvirSensorProfile(3, "C", "Terzo", 0, 0)
    )

    @Test fun oneSensorShowsItsNameAndActualStateIncludingSimulation() {
        val res = context().resources
        val connected = buildUvirConnectionNotification(res,
            listOf(UvirConnectionSensorState("a", UvirConnectionState.CONNECTED)), profiles, "a")
        assertEquals("🟢 Portatile · Connesso", connected.text)
        assertEquals(UvirStatusDot.GREEN.colorArgb, connected.colorArgb)
        assertFalse(connected.busy)
        val fake = buildUvirConnectionNotification(res,
            listOf(UvirConnectionSensorState("a", UvirConnectionState.SIMULATED, true)), profiles, "a")
        assertEquals("🟠 Portatile · Simulazione", fake.text)
        assertFalse(fake.busy)
    }

    @Test fun multipleSensorsShowStableCountsAndExpandedNamedRows() {
        val res = context().resources
        val states = listOf(UvirConnectionSensorState("a", UvirConnectionState.CONNECTED, true),
            UvirConnectionSensorState("b", UvirConnectionState.CONNECTED),
            UvirConnectionSensorState("c", UvirConnectionState.SIMULATED))
        val first = buildUvirConnectionNotification(res, states, profiles, "a")
        val second = buildUvirConnectionNotification(res, states, profiles, "b")
        assertEquals("2 sensori connessi · 1 attività in corso · 1 simulazione", first.text)
        assertEquals(first.text, second.text)
        assertNull(first.colorArgb)
        assertTrue(first.busy)
        assertTrue(first.lines.first().contains("Portatile · Connesso · Attività in corso · selezionato"))
        assertTrue(second.lines.first().contains("Fisso · Connesso · selezionato"))
        assertEquals(3, second.lines.size)
        assertTrue(second.lines.any { it.contains("Terzo · Simulazione") })
    }

    @Test fun offlineActivityIsExplicitlyLastKnownAndNotCountedAsLive() {
        val res = context().resources
        val notification = buildUvirConnectionNotification(res,
            listOf(UvirConnectionSensorState("a", UvirConnectionState.DISCONNECTED, true),
                UvirConnectionSensorState("b", UvirConnectionState.SEARCHING)), profiles, "b")
        assertEquals("0 sensori connessi · 1 sensore in connessione", notification.text)
        assertTrue(notification.lines.any { it.contains("Portatile · Scollegato · Attività in corso (ultimo stato noto)") })
        assertFalse(notification.busy)
    }

    @Test fun quietNotificationContainsAllExpandedRowsAndUsesOneNeutralSummary() {
        val ctx = context()
        val payload = buildUvirConnectionNotification(ctx.resources,
            listOf(UvirConnectionSensorState("a", UvirConnectionState.CONNECTED),
                UvirConnectionSensorState("b", UvirConnectionState.CONNECTING)), profiles, "a")
        val notification = uvirConnectionNotificationBuilder(ctx, "test_unposted", payload).build()
        assertEquals(payload.text, notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(payload.lines.joinToString("\n"), notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
        assertEquals(Notification.CATEGORY_SERVICE, notification.category)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(Notification.COLOR_DEFAULT, notification.color)
        assertNull(notification.sound)
        assertNull(notification.vibrate)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
    }

    @Test fun allSupportedLocalesFormatCountsAndNamedRows() {
        for (tag in listOf("en", "it", "ar", "de", "el", "es", "fa", "fr", "hi", "he", "ja", "ko", "pt", "ru", "sw", "tr", "zh-CN")) {
            val res = context(tag).resources
            for (count in listOf(0, 1, 2, 5, 21, 1_000_000)) for (plural in listOf(
                R.plurals.connection_notification_connected_count, R.plurals.connection_notification_connecting_count,
                R.plurals.connection_notification_activity_count, R.plurals.connection_notification_simulation_count)) {
                val text = res.getQuantityString(plural, count, count)
                assertFalse("$tag $count", text.contains("%1\$d"))
                assertTrue(text.isNotBlank())
            }
            val payload = buildUvirConnectionNotification(res,
                listOf(UvirConnectionSensorState("a", UvirConnectionState.CONNECTED),
                    UvirConnectionSensorState("b", UvirConnectionState.SIMULATED)), profiles, "a")
            assertEquals(2, payload.lines.size)
            assertTrue(payload.lines.first().contains("Portatile"))
        }
    }

    @Test fun sensorIdIsFallbackAndEmptyListNeverClaimsAConnection() {
        val res = context().resources
        val known = buildUvirConnectionNotification(res,
            listOf(UvirConnectionSensorState("unknown", UvirConnectionState.DISCONNECTED)), emptyList(), "unknown")
        assertTrue(known.text.contains("unknown · Scollegato"))
        val empty = buildUvirConnectionNotification(res, emptyList(), profiles, "")
        assertEquals(res.getString(R.string.sensor_status_no_sensor), empty.text)
        assertFalse(empty.busy)
    }
}
