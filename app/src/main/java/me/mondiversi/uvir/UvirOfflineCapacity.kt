package me.mondiversi.uvir

import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

internal data class UvirOfflineAutonomyEstimate(
    val capacity: Int,
    val remaining: Int,
    val automaticAcquisitions: Int?,
    val durationSeconds: Long?,
    val includesAlerts: Boolean,
    val storageFull: Boolean,
    val durationKnown: Boolean = true
)

internal data class UvirOfflineDisconnectionNotice(
    val estimate: UvirOfflineAutonomyEstimate?,
    val autonomousRecordingEnabled: Boolean,
    val showNotification: Boolean = true
)

internal data class UvirOfflineReopenState(
    val automaticActive: Boolean,
    val alertsActive: Boolean,
    val nextAutomaticSaveMs: Long,
    val automaticEndMs: Long,
    val automaticCompletedCount: Int
)

private const val KEY_PERSISTED_OFFLINE_NOTICE_PRESENT =
    "persisted_offline_notice_present"
private const val KEY_PERSISTED_OFFLINE_NOTICE_AUTONOMOUS =
    "persisted_offline_notice_autonomous"
private const val KEY_PERSISTED_OFFLINE_NOTICE_HAS_ESTIMATE =
    "persisted_offline_notice_has_estimate"
private const val KEY_PERSISTED_OFFLINE_NOTICE_CAPACITY =
    "persisted_offline_notice_capacity"
private const val KEY_PERSISTED_OFFLINE_NOTICE_REMAINING =
    "persisted_offline_notice_remaining"
private const val KEY_PERSISTED_OFFLINE_NOTICE_HAS_AUTOMATIC_COUNT =
    "persisted_offline_notice_has_automatic_count"
private const val KEY_PERSISTED_OFFLINE_NOTICE_AUTOMATIC_COUNT =
    "persisted_offline_notice_automatic_count"
private const val KEY_PERSISTED_OFFLINE_NOTICE_HAS_DURATION =
    "persisted_offline_notice_has_duration"
private const val KEY_PERSISTED_OFFLINE_NOTICE_DURATION =
    "persisted_offline_notice_duration"
private const val KEY_PERSISTED_OFFLINE_NOTICE_INCLUDES_ALERTS =
    "persisted_offline_notice_includes_alerts"
private const val KEY_PERSISTED_OFFLINE_NOTICE_STORAGE_FULL =
    "persisted_offline_notice_storage_full"
private const val KEY_PERSISTED_OFFLINE_NOTICE_DURATION_KNOWN =
    "persisted_offline_notice_duration_known"

internal val persistedOfflineNoticeKeys = listOf(
    KEY_PERSISTED_OFFLINE_NOTICE_PRESENT,
    KEY_PERSISTED_OFFLINE_NOTICE_AUTONOMOUS,
    KEY_PERSISTED_OFFLINE_NOTICE_HAS_ESTIMATE,
    KEY_PERSISTED_OFFLINE_NOTICE_CAPACITY,
    KEY_PERSISTED_OFFLINE_NOTICE_REMAINING,
    KEY_PERSISTED_OFFLINE_NOTICE_HAS_AUTOMATIC_COUNT,
    KEY_PERSISTED_OFFLINE_NOTICE_AUTOMATIC_COUNT,
    KEY_PERSISTED_OFFLINE_NOTICE_HAS_DURATION,
    KEY_PERSISTED_OFFLINE_NOTICE_DURATION,
    KEY_PERSISTED_OFFLINE_NOTICE_INCLUDES_ALERTS,
    KEY_PERSISTED_OFFLINE_NOTICE_STORAGE_FULL,
    KEY_PERSISTED_OFFLINE_NOTICE_DURATION_KNOWN
)

// HELLO can recover an autonomous session's identity without providing its
// interval, end deadline or acquisition limit. Do not estimate a duration from
// another session's saved controls in that case.
internal fun UvirOfflineAutonomyEstimate.withKnownAutomaticSchedule(
    automaticActive: Boolean,
    scheduleKnown: Boolean
): UvirOfflineAutonomyEstimate =
    if (automaticActive && !scheduleKnown) {
        copy(automaticAcquisitions = null, durationSeconds = null, durationKnown = false)
    } else {
        this
    }

/** Conditional gating/waiting has no predictable future storage cadence. Capacity remains exact. */
internal fun UvirOfflineAutonomyEstimate.withConditionalSchedule(
    plan: ConditionalAcquisitionPlan?,
    waiting: Boolean
): UvirOfflineAutonomyEstimate =
    if (plan != null && (plan.action != AcquisitionConditionAction.START || waiting)) {
        copy(durationSeconds = null, durationKnown = false)
    } else this

internal fun loadPersistedOfflineDisconnectionNotice(
    preferences: SharedPreferences
): UvirOfflineDisconnectionNotice? {
    if (
        !preferences.getBoolean(
            KEY_PERSISTED_OFFLINE_NOTICE_PRESENT,
            false
        )
    ) {
        return null
    }

    val estimate =
        if (
            preferences.getBoolean(
                KEY_PERSISTED_OFFLINE_NOTICE_HAS_ESTIMATE,
                false
            )
        ) {
            val capacity =
                preferences.getInt(
                    KEY_PERSISTED_OFFLINE_NOTICE_CAPACITY,
                    0
                )
            if (capacity <= 0) {
                null
            } else {
                UvirOfflineAutonomyEstimate(
                    capacity = capacity,
                    remaining =
                        preferences.getInt(
                            KEY_PERSISTED_OFFLINE_NOTICE_REMAINING,
                            capacity
                        ).coerceIn(0, capacity),
                    automaticAcquisitions =
                        if (
                            preferences.getBoolean(
                                KEY_PERSISTED_OFFLINE_NOTICE_HAS_AUTOMATIC_COUNT,
                                false
                            )
                        ) {
                            preferences.getInt(
                                KEY_PERSISTED_OFFLINE_NOTICE_AUTOMATIC_COUNT,
                                0
                            ).coerceAtLeast(0)
                        } else {
                            null
                        },
                    durationSeconds =
                        if (
                            preferences.getBoolean(
                                KEY_PERSISTED_OFFLINE_NOTICE_HAS_DURATION,
                                false
                            )
                        ) {
                            preferences.getLong(
                                KEY_PERSISTED_OFFLINE_NOTICE_DURATION,
                                0L
                            ).coerceAtLeast(0L)
                        } else {
                            null
                        },
                    includesAlerts =
                        preferences.getBoolean(
                            KEY_PERSISTED_OFFLINE_NOTICE_INCLUDES_ALERTS,
                            false
                        ),
                    storageFull =
                        preferences.getBoolean(
                            KEY_PERSISTED_OFFLINE_NOTICE_STORAGE_FULL,
                            false
                        ),
                    durationKnown = preferences.getBoolean(
                        KEY_PERSISTED_OFFLINE_NOTICE_DURATION_KNOWN,
                        true
                    )
                )
            }
        } else {
            null
        }

    return UvirOfflineDisconnectionNotice(
        estimate = estimate,
        autonomousRecordingEnabled =
            preferences.getBoolean(
                KEY_PERSISTED_OFFLINE_NOTICE_AUTONOMOUS,
                false
            ),
        // A restored notice must not issue a duplicate Android notification.
        showNotification = false
    )
}

internal fun persistOfflineDisconnectionNotice(
    preferences: SharedPreferences,
    notice: UvirOfflineDisconnectionNotice?
) {
    val editor = preferences.edit()

    if (notice == null) {
        persistedOfflineNoticeKeys.forEach(editor::remove)
        editor.apply()
        return
    }

    val estimate = notice.estimate
    editor
        .putBoolean(KEY_PERSISTED_OFFLINE_NOTICE_PRESENT, true)
        .putBoolean(
            KEY_PERSISTED_OFFLINE_NOTICE_AUTONOMOUS,
            notice.autonomousRecordingEnabled
        )
        .putBoolean(
            KEY_PERSISTED_OFFLINE_NOTICE_HAS_ESTIMATE,
            estimate != null
        )

    if (estimate != null) {
        editor
            .putInt(
                KEY_PERSISTED_OFFLINE_NOTICE_CAPACITY,
                estimate.capacity
            )
            .putInt(
                KEY_PERSISTED_OFFLINE_NOTICE_REMAINING,
                estimate.remaining
            )
            .putBoolean(
                KEY_PERSISTED_OFFLINE_NOTICE_HAS_AUTOMATIC_COUNT,
                estimate.automaticAcquisitions != null
            )
            .putInt(
                KEY_PERSISTED_OFFLINE_NOTICE_AUTOMATIC_COUNT,
                estimate.automaticAcquisitions ?: 0
            )
            .putBoolean(
                KEY_PERSISTED_OFFLINE_NOTICE_HAS_DURATION,
                estimate.durationSeconds != null
            )
            .putLong(
                KEY_PERSISTED_OFFLINE_NOTICE_DURATION,
                estimate.durationSeconds ?: 0L
            )
            .putBoolean(
                KEY_PERSISTED_OFFLINE_NOTICE_INCLUDES_ALERTS,
                estimate.includesAlerts
            )
            .putBoolean(
                KEY_PERSISTED_OFFLINE_NOTICE_STORAGE_FULL,
                estimate.storageFull
            )
            .putBoolean(
                KEY_PERSISTED_OFFLINE_NOTICE_DURATION_KNOWN,
                estimate.durationKnown
            )
    }

    editor.apply()
}

internal fun offlineCapacityPercentage(
    remaining: Int,
    capacity: Int
): Int {
    if (capacity <= 0) return 0
    return (
        (remaining.coerceIn(0, capacity).toLong() * 100L + capacity / 2L) /
            capacity.toLong()
        ).toInt().coerceIn(0, 100)
}

internal fun consumeOfflineReopenState(
    preferences: SharedPreferences
): UvirOfflineReopenState? {
    if (
        !preferences.getBoolean(
            KEY_OFFLINE_REOPEN_NOTICE_PENDING,
            false
        )
    ) {
        return null
    }

    val state = UvirOfflineReopenState(
        automaticActive = preferences.getBoolean(
            KEY_OFFLINE_REOPEN_AUTO_ACTIVE,
            false
        ),
        alertsActive = preferences.getBoolean(
            KEY_OFFLINE_REOPEN_ALERTS_ACTIVE,
            false
        ),
        nextAutomaticSaveMs = preferences.getLong(
            KEY_OFFLINE_REOPEN_NEXT_SAVE_MS,
            0L
        ),
        automaticEndMs = preferences.getLong(
            KEY_OFFLINE_REOPEN_END_MS,
            0L
        ),
        automaticCompletedCount = preferences.getInt(
            KEY_OFFLINE_REOPEN_COMPLETED_COUNT,
            0
        ).coerceAtLeast(0)
    )

    preferences.edit()
        .remove(KEY_OFFLINE_REOPEN_NOTICE_PENDING)
        .remove(KEY_OFFLINE_REOPEN_AUTO_ACTIVE)
        .remove(KEY_OFFLINE_REOPEN_ALERTS_ACTIVE)
        .remove(KEY_OFFLINE_REOPEN_NEXT_SAVE_MS)
        .remove(KEY_OFFLINE_REOPEN_END_MS)
        .remove(KEY_OFFLINE_REOPEN_COMPLETED_COUNT)
        .apply()

    return state.takeIf {
        it.automaticActive || it.alertsActive
    }
}

internal fun estimateOfflineAutonomy(
    runtimeInfo: UvirSensorRuntimeInfo,
    automaticIntervalSeconds: Long?,
    alertsEnabled: Boolean,
    alertRepeatSeconds: Int,
    startDelaySeconds: Long = 0L,
    automaticDurationSeconds: Long? = null,
    maximumAutomaticAcquisitions: Int? = null,
    samplesPerMeasurement: Int = 1,
    sampleSpacingMs: Long = 0L
): UvirOfflineAutonomyEstimate? {
    val capacity = runtimeInfo.offlineCapacity ?: return null
    if (capacity <= 0) return null
    val remaining =
        (runtimeInfo.offlineRemaining
            ?: (capacity - (runtimeInfo.offlineUsed ?: 0)))
            .coerceIn(0, capacity)
    val automaticInterval =
        automaticIntervalSeconds?.takeIf { it > 0L }
    val sampleCount = samplesPerMeasurement.coerceIn(1, 21)
    val integrationMs =
        runtimeInfo.integrationMs
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: 0.0
    val measurementDurationSeconds =
        (
            (sampleCount - 1).toDouble() * sampleSpacingMs.coerceAtLeast(0L) +
                sampleCount.toDouble() * integrationMs
        ) / 1_000.0
    val automaticCycleSeconds =
        automaticInterval?.let { interval ->
            maxOf(interval.toDouble(), measurementDurationSeconds)
        }
    // Offline alert cooldown starts after a measurement has been completed.
    // Its worst-case storage cadence therefore includes both operations.
    val alertCycleSeconds =
        alertRepeatSeconds.coerceAtLeast(1).toDouble() +
            measurementDurationSeconds
    val alertRate = if (alertsEnabled) 1.0 / alertCycleSeconds else 0.0

    val (automaticAcquisitions, durationSeconds) =
        if (automaticInterval != null && remaining > 0) {
            val automaticCycle =
                automaticCycleSeconds ?: automaticInterval.toDouble()
            val startDelay = startDelaySeconds.coerceAtLeast(0L)
            val possibleAlertsBeforeStart =
                if (alertsEnabled && startDelay > 0L) {
                    ceil(startDelay.toDouble() / alertCycleSeconds)
                        .toInt()
                        .coerceAtMost(remaining)
                } else {
                    0
                }

            if (possibleAlertsBeforeStart >= remaining) {
                0 to floor(remaining / alertRate).toLong()
            } else {
                val remainingAtStart = remaining - possibleAlertsBeforeStart
                val recordsPerSecond =
                    (1.0 / automaticCycle) + alertRate
                val capacityRunSeconds =
                    floor(remainingAtStart / recordsPerSecond)
                        .toLong()
                        .coerceAtLeast(0L)
                val configuredDuration =
                    automaticDurationSeconds
                        ?.coerceAtLeast(0L)
                val maximumCount =
                    maximumAutomaticAcquisitions
                        ?.coerceAtLeast(0)
                val maximumCountDuration =
                    maximumCount?.let { count ->
                        floor(count.toDouble() * automaticCycle)
                            .toLong()
                    }
                val runSeconds =
                    listOfNotNull(
                        capacityRunSeconds,
                        configuredDuration,
                        maximumCountDuration
                    ).minOrNull() ?: capacityRunSeconds
                val acquisitions =
                    ceil(runSeconds.toDouble() / automaticCycle)
                        .toInt()
                        .coerceAtMost(remainingAtStart)
                        .let { count ->
                            maximumCount?.let { min(count, it) } ?: count
                        }

                acquisitions to (startDelay + runSeconds)
            }
        } else {
            val recordsPerSecond = alertRate
            null to
                if (recordsPerSecond > 0.0 && remaining > 0) {
                    floor(remaining / recordsPerSecond).toLong()
                } else {
                    null
                }
        }
    return UvirOfflineAutonomyEstimate(
        capacity = capacity,
        remaining = remaining,
        automaticAcquisitions = automaticAcquisitions,
        durationSeconds = durationSeconds,
        includesAlerts = alertsEnabled,
        storageFull =
            runtimeInfo.offlineStorageFull == true || remaining == 0
    )
}

@Composable
internal fun UvirOfflineAutonomyNotice(
    notice: UvirOfflineDisconnectionNotice,
    primaryText: Color,
    secondaryText: Color
) {
    val estimate = notice.estimate
    val warningColor =
        if (estimate?.storageFull == true) {
            MaterialTheme.colorScheme.error
        } else {
            Color(0xFFF57C00)
        }

    val warningTextColor = if (estimate?.storageFull == true && isSystemInDarkTheme())
        UvirDestructiveActionColor else warningColor

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = warningColor.copy(alpha = 0.09f),
        border = BorderStroke(
            1.dp,
            warningColor.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.offline_capacity_title),
                color = warningTextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            if (!notice.autonomousRecordingEnabled) {
                Text(
                    text = stringResource(
                        R.string.offline_disconnection_recording_disabled
                    ),
                    color = primaryText,
                    fontSize = 12.sp
                )
            } else if (estimate == null) {
                Text(
                    text = stringResource(
                        R.string.offline_disconnection_estimate_unavailable
                    ),
                    color = primaryText,
                    fontSize = 12.sp
                )
            } else {
                OfflineAutonomyDetails(
                    estimate = estimate,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    warningColor = warningTextColor
                )
            }
        }
    }
}

@Composable
internal fun UvirCompactOfflineAutonomyNotice(
    notice: UvirOfflineDisconnectionNotice,
    primaryText: Color,
    modifier: Modifier = Modifier
) {
    val estimate = notice.estimate
    val warningColor =
        if (estimate?.storageFull == true) {
            MaterialTheme.colorScheme.error
        } else {
            Color(0xFFF57C00)
        }

    val warningTextColor = if (estimate?.storageFull == true && isSystemInDarkTheme())
        UvirDestructiveActionColor else warningColor

    Surface(
        modifier =
            modifier
                .height(56.dp)
                .widthIn(max = 210.dp),
        shape = RoundedCornerShape(14.dp),
        color = warningColor.copy(alpha = 0.09f),
        border = BorderStroke(
            1.dp,
            warningColor.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.offline_capacity_title),
                color = warningTextColor,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            if (notice.autonomousRecordingEnabled && estimate != null) {
                val availablePercentage =
                    offlineCapacityPercentage(
                        remaining = estimate.remaining,
                        capacity = estimate.capacity
                    )
                Text(
                    text =
                        "• " + stringResource(
                            R.string.offline_capacity_available,
                            estimate.remaining,
                            estimate.capacity,
                            availablePercentage
                        ),
                    color = primaryText,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    maxLines = 1
                )
                Text(
                    text =
                        "• " + (
                            estimate.durationSeconds?.let { seconds ->
                                stringResource(
                                    R.string.offline_capacity_estimated_duration,
                                    compactOfflineDuration(seconds)
                                )
                            } ?: offlineAutonomyDurationFallback(estimate)
                        ),
                    color = primaryText,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    maxLines = 1
                )
            } else {
                Text(
                    text = stringResource(R.string.sensor_info_unavailable),
                    color = primaryText,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun OfflineAutonomyDetails(
    estimate: UvirOfflineAutonomyEstimate,
    primaryText: Color,
    secondaryText: Color,
    warningColor: Color
) {
    val availablePercentage =
        offlineCapacityPercentage(
            remaining = estimate.remaining,
            capacity = estimate.capacity
        )

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OfflineAutonomyBullet(
            text = stringResource(
                R.string.offline_capacity_available,
                estimate.remaining,
                estimate.capacity,
                availablePercentage
            ),
            color = primaryText
        )
        if (estimate.storageFull) {
            Text(
                text = stringResource(R.string.offline_capacity_full),
                color = warningColor,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
        } else {
            estimate.durationSeconds?.let { seconds ->
                val duration = compactOfflineDuration(seconds)
                OfflineAutonomyBullet(
                    text = stringResource(
                        R.string.offline_capacity_estimated_duration,
                        duration
                    ),
                    color = primaryText
                )
            } ?: OfflineAutonomyBullet(
                text = offlineAutonomyDurationFallback(estimate),
                color = primaryText
            )
            if (estimate.includesAlerts) {
                Text(
                    text = stringResource(
                        R.string.offline_capacity_conservative_hint
                    ),
                    color = secondaryText,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun offlineAutonomyDurationFallback(
    estimate: UvirOfflineAutonomyEstimate
): String =
    if (estimate.durationKnown) {
        stringResource(R.string.offline_capacity_estimated_duration_unlimited)
    } else {
        stringResource(
            R.string.offline_capacity_estimated_duration,
            stringResource(R.string.sensor_info_unavailable)
        )
    }

@Composable
private fun OfflineAutonomyBullet(
    text: String,
    color: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "•",
            color = color,
            fontSize = 12.sp
        )
        Text(
            modifier = Modifier.weight(1f),
            text = text,
            color = color,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun compactOfflineDuration(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val days = safeSeconds / 86_400L
    val hours = (safeSeconds % 86_400L) / 3_600L
    val minutes = (safeSeconds % 3_600L) / 60L
    return when {
        days > 0L ->
            stringResource(
                R.string.offline_duration_days_hours,
                days,
                hours
            )
        hours > 0L ->
            stringResource(
                R.string.offline_duration_hours_minutes,
                hours,
                minutes
            )
        safeSeconds < 60L ->
            stringResource(
                R.string.offline_duration_seconds,
                safeSeconds
            )
        else ->
            stringResource(
                R.string.offline_duration_minutes,
                minutes
            )
    }
}
