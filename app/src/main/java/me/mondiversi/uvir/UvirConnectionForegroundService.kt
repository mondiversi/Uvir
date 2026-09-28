package me.mondiversi.uvir

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Keeps all active sensor transports responsive while Android turns the
 * display off. The service owns no sensor state: it only keeps the existing
 * transport worker scheduled and, for Wi-Fi based transports, the radio awake.
 */
class UvirConnectionForegroundService : Service() {
    private lateinit var cpuWakeLock: PowerManager.WakeLock
    private var wifiWakeLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        cpuWakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Uvir:SensorConnection"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        updateWifiLock(
            required = intent?.getBooleanExtra(EXTRA_WIFI_LOCK, false) == true
        )
        val notification = UvirConnectionNotification(
            text = intent?.getStringExtra(EXTRA_STATUS_TEXT).orEmpty()
                .ifBlank { getString(R.string.sensor_status_no_sensor) },
            lines = intent?.getStringArrayListExtra(EXTRA_STATUS_LINES)?.toList().orEmpty(),
            colorArgb = intent?.takeIf { it.hasExtra(EXTRA_STATUS_COLOR) }?.getIntExtra(EXTRA_STATUS_COLOR, 0),
            busy = intent?.getBooleanExtra(EXTRA_STATUS_PULSES, false) == true
        )
        startForeground(
            NOTIFICATION_ID,
            buildNotification(notification)
        )
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseWifiLock()
        if (::cpuWakeLock.isInitialized && cpuWakeLock.isHeld) {
            cpuWakeLock.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    private fun updateWifiLock(required: Boolean) {
        if (!required) {
            releaseWifiLock()
            return
        }
        if (wifiWakeLock?.isHeld == true) return

        val manager =
            applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiWakeLock =
            manager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "Uvir:SensorWifiConnection"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseWifiLock() {
        wifiWakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wifiWakeLock = null
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.settings_section_sensor_connection),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(
        notification: UvirConnectionNotification
    ) =
        uvirConnectionNotificationBuilder(this, CHANNEL_ID, notification)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    NOTIFICATION_ID,
                    Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(EXTRA_OPEN_HOME, true)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    companion object {
        private const val CHANNEL_ID = "uvir_sensor_connection"
        private const val NOTIFICATION_ID = 4106
        private const val ACTION_START =
            "me.mondiversi.uvir.action.START_SENSOR_CONNECTION_SERVICE"
        private const val ACTION_STOP =
            "me.mondiversi.uvir.action.STOP_SENSOR_CONNECTION_SERVICE"
        private const val EXTRA_STATUS_TEXT = "status_text"
        private const val EXTRA_STATUS_LINES = "status_lines"
        private const val EXTRA_STATUS_COLOR = "status_color"
        private const val EXTRA_STATUS_PULSES = "status_pulses"
        private const val EXTRA_WIFI_LOCK = "wifi_lock"

        internal fun update(
            context: Context,
            active: Boolean,
            notification: UvirConnectionNotification = UvirConnectionNotification(),
            wifiLockRequired: Boolean = false
        ) {
            val applicationContext = context.applicationContext
            if (!active) {
                applicationContext.stopService(
                    Intent(applicationContext, UvirConnectionForegroundService::class.java)
                )
                return
            }

            val intent =
                Intent(applicationContext, UvirConnectionForegroundService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_STATUS_TEXT, notification.text)
                    .putStringArrayListExtra(EXTRA_STATUS_LINES, ArrayList(notification.lines))
                    .putExtra(EXTRA_STATUS_PULSES, notification.busy)
                    .putExtra(EXTRA_WIFI_LOCK, wifiLockRequired)
            notification.colorArgb?.let { intent.putExtra(EXTRA_STATUS_COLOR, it) }
            ContextCompat.startForegroundService(applicationContext, intent)
        }
    }
}
