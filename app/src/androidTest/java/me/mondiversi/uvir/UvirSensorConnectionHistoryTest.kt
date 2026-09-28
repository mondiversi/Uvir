package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Isolated preference namespace: never changes real sensor history or associations. */
class UvirSensorConnectionHistoryTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "connection-history-test-${UUID.randomUUID()}-"
    private val names = mutableSetOf<String>()
    private fun isolatedContext() = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            names.add(prefix + name)
            return base.getSharedPreferences(prefix + name, mode)
        }
    }
    private val context = isolatedContext()

    @After fun cleanUp() { names.forEach { base.deleteSharedPreferences(it) } }

    @Test fun liveTrafficAndDisconnectDoNotMoveStartTimeButReconnectDoes() {
        for (mode in SensorConnectionMode.entries) {
            UvirSensorConnectionHistory.recordIfStarted(context, "A", mode, false, false, 100L)
            assertNull(UvirSensorConnectionHistory.lastConnectedAt(context, "A", mode))
            UvirSensorConnectionHistory.recordIfStarted(context, "A", mode, false, true, 200L)
            repeat(10) {
                UvirSensorConnectionHistory.recordIfStarted(context, "A", mode, true, true, 300L + it)
            }
            UvirSensorConnectionHistory.recordIfStarted(context, "A", mode, true, false, 400L)
            assertEquals(200L, UvirSensorConnectionHistory.lastConnectedAt(context, "A", mode))
            UvirSensorConnectionHistory.recordIfStarted(context, "A", mode, false, true, 500L)
            assertEquals(500L, UvirSensorConnectionHistory.lastConnectedAt(context, "A", mode))
        }
    }

    @Test fun historyPersistsSeparatelyForEveryDeviceAndTransport() {
        for ((index, mode) in SensorConnectionMode.entries.withIndex()) {
            UvirSensorConnectionHistory.recordIfStarted(context, " A ", mode, false, true, 1_000L + index)
            UvirSensorConnectionHistory.recordIfStarted(context, "B", mode, false, true, 2_000L + index)
        }
        val reopened = isolatedContext()
        for ((index, mode) in SensorConnectionMode.entries.withIndex()) {
            assertEquals(1_000L + index, UvirSensorConnectionHistory.lastConnectedAt(reopened, "a", mode))
            assertEquals(2_000L + index, UvirSensorConnectionHistory.lastConnectedAt(reopened, "B", mode))
        }
        assertEquals(1_003L, UvirSensorConnectionHistory.lastConnectedAt(reopened, "a"))
        assertEquals(2_003L, UvirSensorConnectionHistory.lastConnectedAt(reopened, "B"))
        assertNull(UvirSensorConnectionHistory.lastConnectedAt(reopened, "C"))
        assertNull(UvirSensorConnectionHistory.lastConnectedAt(reopened, "C", SensorConnectionMode.USB))
        assertTrue(UvirSensorConnectionHistory.clear(reopened))
        assertNull(UvirSensorConnectionHistory.lastConnectedAt(context, "a", SensorConnectionMode.USB))
    }

    @Test fun missingIdentityAndInvalidTimesNeverCreateHistory() {
        UvirSensorConnectionHistory.recordIfStarted(context, " ", SensorConnectionMode.USB, false, true, 100L)
        UvirSensorConnectionHistory.recordIfStarted(context, "A", SensorConnectionMode.USB, false, true, 0L)
        UvirSensorConnectionHistory.recordIfStarted(context, "A", SensorConnectionMode.USB, false, true, -100L)
        assertNull(UvirSensorConnectionHistory.lastConnectedAt(context, "", SensorConnectionMode.USB))
        assertNull(UvirSensorConnectionHistory.lastConnectedAt(context, "A", SensorConnectionMode.USB))
    }

    @Test fun lastTransportUsesOnlySuccessfulConnectionsAndKeepsDevicesIndependent() {
        assertNull(UvirSensorConnectionHistory.lastConnectedMode(context, "A"))
        UvirSensorConnectionHistory.recordIfStarted(context, " A ", SensorConnectionMode.WIFI, false, true, 100L)
        UvirSensorConnectionHistory.recordIfStarted(context, "B", SensorConnectionMode.BLUETOOTH, false, true, 300L)
        UvirSensorConnectionHistory.recordIfStarted(context, "A", SensorConnectionMode.INTERNET, false, false, 400L)
        assertEquals(SensorConnectionMode.WIFI, UvirSensorConnectionHistory.lastConnectedMode(isolatedContext(), "a"))
        assertEquals(SensorConnectionMode.BLUETOOTH, UvirSensorConnectionHistory.lastConnectedMode(context, "B"))
        UvirSensorConnectionHistory.recordIfStarted(context, "A", SensorConnectionMode.USB, false, true, 500L)
        UvirSensorConnectionHistory.recordIfStarted(context, "A", SensorConnectionMode.USB, true, false, 600L)
        assertEquals(SensorConnectionMode.USB, UvirSensorConnectionHistory.lastConnectedMode(context, "A"))
        assertNull(UvirSensorConnectionHistory.lastConnectedMode(context, ""))
    }
}
