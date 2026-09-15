package me.mondiversi.uvir

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirSensorOperationalStateTest {
    @Test
    fun samplingDefaultsTo150MsWithoutWritingPreferences() {
        val values = mutableMapOf<String, Any?>()
        assertEquals(150L, readSampleSpacingMs(memoryPreferences(values)))
        assertTrue(values.isEmpty())
    }

    @Test
    fun samplingPreservesAnAlreadySavedInterval() {
        val values = mutableMapOf<String, Any?>(KEY_SAMPLE_SPACING_MS to 500L)
        assertEquals(500L, readSampleSpacingMs(memoryPreferences(values)))
        assertEquals(500L, values[KEY_SAMPLE_SPACING_MS])
    }

    @Test
    fun samplingStillEnforcesTheExistingAllowedRange() {
        assertEquals(150L, readSampleSpacingMs(memoryPreferences(mutableMapOf(KEY_SAMPLE_SPACING_MS to 149L))))
        assertEquals(5_000L, readSampleSpacingMs(memoryPreferences(mutableMapOf(KEY_SAMPLE_SPACING_MS to 5_001L))))
    }

    @Test
    fun disassociationDurablyRemovesOnlyTheActiveSensorContext() {
        val values = mutableMapOf<String, Any?>(
            KEY_AUTO_ENABLED to true,
            KEY_AUTO_SESSION_ID to 12L,
            KEY_AUTO_COMPLETED_COUNT to 3,
            KEY_AUTO_NEXT_SAVE_MS to 120_000L,
            KEY_AUTO_END_MS to 240_000L,
            KEY_AUTO_SCHEDULE_KNOWN to false,
            KEY_THRESHOLD_ALERT_ENABLED to true,
            KEY_THRESHOLD_ALERT_SESSION_ID to 13L,
            KEY_LAST_MANUAL_SESSION_ID to 11L,
            KEY_MANUAL_SAVE_MODE to ManualSaveMode.LAST_MANUAL_SESSION.storedValue,
            KEY_AUTO_INTERVAL_SECONDS to 30L,
            KEY_AUTO_NOTE to "Acquisition note",
            KEY_THRESHOLD_ALERT_NOTE to "Alert note",
            KEY_APP_LANGUAGE to "it",
            KEY_UNREAD_ACQUISITION_COUNT to 4,
            "acquisition_counter" to 100L,
            "sensor_profile.UVIR-001" to "Historical profile"
        )
        ThresholdAlertMetric.entries.forEach { metric ->
            values[thresholdRulePreferenceKey(metric, "enabled")] = true
            values[thresholdRulePreferenceKey(metric, "value")] = 2.5f
            values[thresholdRulePreferenceKey(metric, "direction")] = "BELOW"
        }
        val preferences = memoryPreferences(values)
        var committed = false
        val durablePreferences = memoryPreferences(values) { committed = true }

        assertTrue(clearSensorOperationalState(durablePreferences))
        assertTrue(committed)
        sensorOperationalPreferenceKeys.forEach { assertFalse(values.containsKey(it)) }
        assertEquals(ManualSaveMode.SINGLE.storedValue, values[KEY_MANUAL_SAVE_MODE])
        assertEquals(30L, values[KEY_AUTO_INTERVAL_SECONDS])
        assertEquals("Acquisition note", values[KEY_AUTO_NOTE])
        assertEquals("Alert note", values[KEY_THRESHOLD_ALERT_NOTE])
        assertEquals("it", values[KEY_APP_LANGUAGE])
        assertEquals(4, values[KEY_UNREAD_ACQUISITION_COUNT])
        assertEquals(100L, values["acquisition_counter"])
        assertEquals("Historical profile", values["sensor_profile.UVIR-001"])
        ThresholdAlertMetric.entries.forEach { metric ->
            assertEquals(2.5f, values[thresholdRulePreferenceKey(metric, "value")])
            assertEquals("BELOW", values[thresholdRulePreferenceKey(metric, "direction")])
        }
        assertNull(rememberedManualSessionChoice(preferences).lastSessionId)
    }

    @Test
    fun offlineNoticeSurvivesReopeningButNotDisassociation() {
        val values = mutableMapOf<String, Any?>(KEY_AUTO_ENABLED to true)
        val preferences = memoryPreferences(values)
        persistOfflineDisconnectionNotice(
            preferences,
            UvirOfflineDisconnectionNotice(
                estimate = UvirOfflineAutonomyEstimate(1_800, 1_790, 10, 300L, true, false),
                autonomousRecordingEnabled = true
            )
        )

        val reopened = loadPersistedOfflineDisconnectionNotice(memoryPreferences(values))!!
        assertEquals(1_790, reopened.estimate!!.remaining)
        assertFalse(reopened.showNotification)
        assertTrue(preferences.getBoolean(KEY_AUTO_ENABLED, false))

        assertTrue(clearSensorOperationalState(preferences))
        assertNull(loadPersistedOfflineDisconnectionNotice(memoryPreferences(values)))
        assertFalse(preferences.getBoolean(KEY_AUTO_ENABLED, false))
    }

    @Test
    fun reopeningRecoversBothSessionsWithoutClearingTheirRunningFlags() {
        val values = mutableMapOf<String, Any?>(
            KEY_AUTO_ENABLED to true,
            KEY_AUTO_SESSION_ID to 12L,
            KEY_THRESHOLD_ALERT_ENABLED to true,
            KEY_THRESHOLD_ALERT_SESSION_ID to 13L,
            KEY_OFFLINE_REOPEN_NOTICE_PENDING to true,
            KEY_OFFLINE_REOPEN_AUTO_ACTIVE to true,
            KEY_OFFLINE_REOPEN_ALERTS_ACTIVE to true,
            KEY_OFFLINE_REOPEN_NEXT_SAVE_MS to 120_000L,
            KEY_OFFLINE_REOPEN_END_MS to 240_000L,
            KEY_OFFLINE_REOPEN_COMPLETED_COUNT to 3
        )
        val preferences = memoryPreferences(values)
        val pending = consumeOfflineReopenState(preferences)!!
        assertTrue(pending.automaticActive)
        assertTrue(pending.alertsActive)
        assertEquals(3, pending.automaticCompletedCount)
        assertTrue(preferences.getBoolean(KEY_AUTO_ENABLED, false))
        assertTrue(preferences.getBoolean(KEY_THRESHOLD_ALERT_ENABLED, false))
        assertEquals(12L, preferences.getLong(KEY_AUTO_SESSION_ID, 0L))
        assertEquals(13L, preferences.getLong(KEY_THRESHOLD_ALERT_SESSION_ID, 0L))
    }

    @Test
    fun disassociationCancelsAnUnconsumedReopenNotice() {
        val preferences = memoryPreferences(mutableMapOf(
            KEY_OFFLINE_REOPEN_NOTICE_PENDING to true,
            KEY_OFFLINE_REOPEN_AUTO_ACTIVE to true,
            KEY_OFFLINE_REOPEN_ALERTS_ACTIVE to true
        ))
        assertTrue(clearSensorOperationalState(preferences))
        assertNull(consumeOfflineReopenState(preferences))
    }

    @Test
    fun forgettingUiMonitoringPreservesTheConfiguredThresholdValues() {
        val settings = ThresholdAlertSettings(
            enabled = true,
            rules = listOf(ThresholdAlertRule(ThresholdAlertMetric.UVA, true, ThresholdAlertDirection.BELOW, 2.5f)),
            repeatSeconds = 30,
            sound = ThresholdAlertSound.SINGLE_BEEP,
            volume = 20
        )
        val forgotten = settings.withoutAssociatedSensor()
        assertFalse(forgotten.enabled)
        assertFalse(forgotten.rules.single().enabled)
        assertEquals(settings.rules.single().threshold, forgotten.rules.single().threshold)
        assertEquals(settings.rules.single().direction, forgotten.rules.single().direction)
        assertEquals(settings.repeatSeconds, forgotten.repeatSeconds)
        assertEquals(settings.sound, forgotten.sound)
        assertEquals(settings.volume, forgotten.volume)
    }

    @Test
    fun sensorReadbackRecoversOnlyAnActuallyActiveValidSession() {
        assertEquals(12L, UvirSensorRuntimeInfo(offlineRecording = true, offlineSessionId = 12L).activeAutomaticSessionIdOrNull())
        assertNull(UvirSensorRuntimeInfo(offlineRecording = false, offlineSessionId = 12L).activeAutomaticSessionIdOrNull())
        assertNull(UvirSensorRuntimeInfo(offlineSessionId = 12L).activeAutomaticSessionIdOrNull())
        assertNull(UvirSensorRuntimeInfo(offlineRecording = true, offlineSessionId = 0L).activeAutomaticSessionIdOrNull())
        assertNull(UvirSensorRuntimeInfo(offlineRecording = true).activeAutomaticSessionIdOrNull())
    }

    @Test
    fun recoveredUnknownScheduleDoesNotBorrowAnotherSensorsAutonomy() {
        val estimate = UvirOfflineAutonomyEstimate(1_800, 1_790, 10, 300L, true, false)
        val unknown = estimate.withKnownAutomaticSchedule(true, false)
        assertEquals(estimate.capacity, unknown.capacity)
        assertEquals(estimate.remaining, unknown.remaining)
        assertTrue(unknown.includesAlerts)
        assertNull(unknown.durationSeconds)
        assertNull(unknown.automaticAcquisitions)
        assertFalse(unknown.durationKnown)
        assertEquals(estimate, estimate.withKnownAutomaticSchedule(true, true))
        assertEquals(estimate, estimate.withKnownAutomaticSchedule(false, false))
    }

    @Test
    fun unknownAutonomyRemainsUnknownAfterReopening() {
        val preferences = memoryPreferences(mutableMapOf())
        val estimate = UvirOfflineAutonomyEstimate(1_800, 1_790, 10, 300L, true, false)
            .withKnownAutomaticSchedule(true, false)
        persistOfflineDisconnectionNotice(
            preferences,
            UvirOfflineDisconnectionNotice(estimate, true)
        )
        val restored = loadPersistedOfflineDisconnectionNotice(preferences)!!.estimate!!
        assertFalse(restored.durationKnown)
        assertNull(restored.durationSeconds)
        assertEquals(1_790, restored.remaining)
    }
}

/** Tests the real preference helpers without Android storage or new libraries. */
internal fun memoryPreferences(
    values: MutableMap<String, Any?>,
    onCommit: () -> Unit = {}
): SharedPreferences {
    val removed = mutableSetOf<String>()
    val changed = mutableMapOf<String, Any?>()
    val editor = Proxy.newProxyInstance(
        SharedPreferences.Editor::class.java.classLoader,
        arrayOf(SharedPreferences.Editor::class.java)
    ) { proxy, method, arguments ->
        when (method.name) {
            "remove" -> { removed += arguments!![0] as String; proxy }
            "commit", "apply" -> {
                removed.forEach(values::remove)
                values.putAll(changed)
                removed.clear()
                changed.clear()
                if (method.name == "commit") { onCommit(); true } else null
            }
            else -> if (method.name.startsWith("put")) {
                changed[arguments!![0] as String] = arguments[1]
                proxy
            } else error("Unexpected editor call: ${method.name}")
        }
    } as SharedPreferences.Editor
    return Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java)
    ) { _, method, arguments ->
        when (method.name) {
            "edit" -> editor
            "contains" -> values.containsKey(arguments!![0] as String)
            "getAll" -> values.toMap()
            else -> if (method.name.startsWith("get")) {
                values[arguments!![0] as String] ?: arguments[1]
            } else error("Unexpected preference call: ${method.name}")
        }
    } as SharedPreferences
}
