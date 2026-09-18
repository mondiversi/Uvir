package me.mondiversi.uvir

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun updateAutomaticAcquisitionNotification(
    context: Context,
    completedCount: Int
) {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val notificationManager =
        context.getSystemService(
            NotificationManager::class.java
        )

    notificationManager.createNotificationChannel(
        NotificationChannel(
            AUTOMATIC_NOTIFICATION_CHANNEL_ID,
            context.getString(
                R.string.automatic_notification_channel_name
            ),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description =
                context.getString(
                    R.string.automatic_notification_channel_description
                )
            setSound(null, null)
            enableVibration(false)
        }
    )

    val savedText =
        context.resources.getQuantityString(
            R.plurals.automatic_notification_saved,
            completedCount,
            completedCount
        )

    val notification =
        NotificationCompat.Builder(
            context,
            AUTOMATIC_NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification_uvir)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.uvir_logo
                )
            )
            .setContentTitle(
                context.getString(
                    R.string.automatic_notification_title
                )
            )
            .setContentText(
                savedText
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(savedText)
            )
            .setContentIntent(
                createOpenHomePendingIntent(
                    context = context,
                    requestCode = 0
                )
            )
            .setColor(0xFF43A047.toInt())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

    postNotificationIfAllowed(
        context = context,
        notificationId = AUTOMATIC_NOTIFICATION_ID,
        notification = notification
    )
}

internal fun cancelAutomaticAcquisitionNotification(
    context: Context
) {
    NotificationManagerCompat
        .from(context)
        .cancel(AUTOMATIC_NOTIFICATION_ID)
}

internal fun updateThresholdAlertNotification(
    context: Context,
    alert: ThresholdNotificationAlert
) {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val notificationManager =
        context.getSystemService(
            NotificationManager::class.java
        )

    notificationManager.createNotificationChannel(
        NotificationChannel(
            THRESHOLD_ALERT_NOTIFICATION_CHANNEL_ID,
            context.getString(
                R.string.threshold_notification_channel_name
            ),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description =
                context.getString(
                    R.string.threshold_notification_channel_description
                )
            setSound(null, null)
            enableVibration(false)
        }
    )

    val metricLabel =
        context.getString(
            thresholdAlertMetricLabelResource(
                alert.metric
            )
        )
    val alertDate =
        formatUvirDateTime(
            timestamp = alert.timestamp,
            format = loadUvirDateFormat(context),
            separator = " ",
            timeFormat =
                resolveUvirTimeFormat(
                    context,
                    loadUvirTimeFormat(context)
                )
        )
    val irradianceUnit = loadUvirIrradianceUnit(context)
    val unit =
        if (alert.metric.isBiologicalEffect()) {
            irradianceUnit.symbol + " eq."
        } else {
            irradianceUnit.symbol
        }
    val alertValueText =
        context.getString(
            R.string.threshold_notification_value,
            formatUvirIrradianceNumber(
                canonicalUwCm2 = alert.value,
                fractionDigits = 3,
                numericFormat = loadUvirNumericFormat(context),
                unit = irradianceUnit
            ),
            unit
        )
    val alertDateText =
        context.getString(
            R.string.threshold_notification_last_measurement,
            alertDate
        )
    val expandedStyle =
        NotificationCompat.InboxStyle()
            .setBigContentTitle(
                context.getString(
                    R.string.threshold_notification_title
                )
            )
            .addLine(metricLabel)
            .addLine(alertValueText)
            .addLine(alertDateText)

    val notification =
        NotificationCompat.Builder(
            context,
            THRESHOLD_ALERT_NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification_uvir)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.uvir_logo
                )
            )
            .setContentTitle(
                context.getString(
                    R.string.threshold_notification_title
                )
            )
            .setContentText(metricLabel)
            .setStyle(expandedStyle)
            .setContentIntent(
                createOpenHomePendingIntent(
                    context = context,
                    requestCode = 1
                )
            )
            .setColor(0xFFF57C00.toInt())
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

    postNotificationIfAllowed(
        context = context,
        notificationId = THRESHOLD_ALERT_NOTIFICATION_ID,
        notification = notification
    )
}

internal fun cancelThresholdAlertNotification(
    context: Context
) {
    NotificationManagerCompat
        .from(context)
        .cancel(THRESHOLD_ALERT_NOTIFICATION_ID)
}

internal fun showOfflineDisconnectionNotification(
    context: Context,
    notice: UvirOfflineDisconnectionNotice
) {
    if (!canPostNotifications(context)) return

    createSensorStorageNotificationChannel(context)

    val lines = mutableListOf(
        context.getString(
            R.string.sensor_disconnected_notification_summary
        )
    )
    val estimate = notice.estimate
    if (!notice.autonomousRecordingEnabled) {
        lines += context.getString(
            R.string.offline_disconnection_recording_disabled
        )
    } else if (estimate == null) {
        lines += context.getString(
            R.string.offline_disconnection_estimate_unavailable
        )
    } else {
        lines += context.getString(
            R.string.offline_capacity_available,
            estimate.remaining,
            estimate.capacity,
            offlineCapacityPercentage(
                remaining = estimate.remaining,
                capacity = estimate.capacity
            )
        )
        if (estimate.storageFull) {
            lines += context.getString(R.string.offline_capacity_full)
        } else {
            estimate.durationSeconds?.let { seconds ->
                val duration = compactOfflineDuration(context, seconds)
                lines += context.getString(
                    R.string.offline_capacity_estimated_duration,
                    duration
                )
            } ?: run {
                lines += context.getString(
                    if (estimate.durationKnown) {
                        R.string.offline_capacity_estimated_duration_unlimited
                    } else {
                        R.string.offline_capacity_estimated_duration
                    },
                    context.getString(R.string.sensor_info_unavailable)
                )
            }
        }
    }

    val style = NotificationCompat.InboxStyle()
        .setBigContentTitle(
            context.getString(R.string.offline_capacity_title)
        )
    lines.forEach(style::addLine)

    val contentText =
        lines.getOrNull(1) ?: lines.first()
    val notification =
        NotificationCompat.Builder(
            context,
            SENSOR_STORAGE_NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification_uvir)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.uvir_logo
                )
            )
            .setContentTitle(
                context.getString(R.string.offline_capacity_title)
            )
            .setContentText(contentText)
            .setStyle(style)
            .setContentIntent(
                createOpenHomePendingIntent(
                    context = context,
                    requestCode = 2
                )
            )
            .setColor(0xFFF9A825.toInt())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .build()

    postNotificationIfAllowed(
        context = context,
        notificationId = OFFLINE_AUTONOMY_NOTIFICATION_ID,
        notification = notification
    )
}

internal fun cancelOfflineDisconnectionNotification(
    context: Context
) {
    NotificationManagerCompat
        .from(context)
        .cancel(OFFLINE_AUTONOMY_NOTIFICATION_ID)
}

internal fun showSensorSyncCompleteNotification(
    context: Context,
    summary: SensorSyncSummary
) {
    if (!canPostNotifications(context)) return

    createSensorStorageNotificationChannel(context)

    val lines = buildList {
        add(
            context.getString(
                R.string.sensor_sync_acquisitions_recovered
            ) + ": ${summary.acquisitions}"
        )
        add(
            context.getString(
                R.string.sensor_sync_alerts_recovered
            ) + ": ${summary.alerts}"
        )
        if (summary.errors > 0) {
            add(
                context.getString(
                    R.string.sensor_sync_errors_recovered
                ) + ": ${summary.errors}"
            )
        }
        if (summary.storageWasFull) {
            add(
                context.getString(
                    R.string.sensor_sync_storage_full_warning
                )
            )
        }
    }
    val style = NotificationCompat.InboxStyle()
        .setBigContentTitle(
            context.getString(R.string.sensor_sync_complete)
        )
    lines.forEach(style::addLine)

    val notification =
        NotificationCompat.Builder(
            context,
            SENSOR_STORAGE_NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification_uvir)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.uvir_logo
                )
            )
            .setContentTitle(
                context.getString(R.string.sensor_sync_complete)
            )
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(
                createOpenHomePendingIntent(
                    context = context,
                    requestCode = 3
                )
            )
            .setColor(0xFF6750A4.toInt())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .build()

    postNotificationIfAllowed(
        context = context,
        notificationId = SENSOR_SYNC_NOTIFICATION_ID,
        notification = notification
    )
}

private fun createSensorStorageNotificationChannel(
    context: Context
) {
    context.getSystemService(NotificationManager::class.java)
        .createNotificationChannel(
            NotificationChannel(
                SENSOR_STORAGE_NOTIFICATION_CHANNEL_ID,
                context.getString(
                    R.string.sensor_storage_notification_channel_name
                ),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(
                    R.string.sensor_storage_notification_channel_description
                )
                setSound(null, null)
                enableVibration(false)
            }
        )
}

private fun canPostNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

/**
 * Single permission boundary for every notification emitted by the app.
 * The explicit check handles Android 13+, while runCatching also protects
 * older/vendor implementations that may still reject a notification.
 */
@SuppressLint("MissingPermission")
private fun postNotificationIfAllowed(
    context: Context,
    notificationId: Int,
    notification: android.app.Notification
) {
    if (!canPostNotifications(context)) return
    runCatching {
        NotificationManagerCompat
            .from(context)
            .notify(notificationId, notification)
    }
}

private fun compactOfflineDuration(
    context: Context,
    totalSeconds: Long
): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val days = safeSeconds / 86_400L
    val hours = (safeSeconds % 86_400L) / 3_600L
    val minutes = (safeSeconds % 3_600L) / 60L
    return when {
        days > 0L ->
            context.getString(
                R.string.offline_duration_days_hours,
                days,
                hours
            )
        hours > 0L ->
            context.getString(
                R.string.offline_duration_hours_minutes,
                hours,
                minutes
            )
        safeSeconds < 60L ->
            context.getString(
                R.string.offline_duration_seconds,
                safeSeconds
            )
        else ->
            context.getString(
                R.string.offline_duration_minutes,
                minutes
            )
    }
}

private const val SENSOR_STORAGE_NOTIFICATION_CHANNEL_ID =
    "uvir_sensor_storage"
private const val OFFLINE_AUTONOMY_NOTIFICATION_ID = 1103
private const val SENSOR_SYNC_NOTIFICATION_ID = 1104

private fun createOpenHomePendingIntent(
    context: Context,
    requestCode: Int
): PendingIntent {
    val openHomeIntent =
        Intent(
            context,
            MainActivity::class.java
        ).apply {
            putExtra(EXTRA_OPEN_HOME, true)
            flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    return PendingIntent.getActivity(
        context,
        requestCode,
        openHomeIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
    )
}
