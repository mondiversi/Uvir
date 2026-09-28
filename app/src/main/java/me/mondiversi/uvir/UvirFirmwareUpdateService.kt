package me.mondiversi.uvir

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Separate from connection notifications: removing the task must not abort a flash. */
class UvirFirmwareUpdateService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("uvir_updates", getString(R.string.update_available), NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) })
        startForeground(203, NotificationCompat.Builder(this, "uvir_updates")
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle(getString(R.string.update_flash))
            .setContentText(getString(R.string.update_keep_connected)).setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this, 203, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)).build())
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Uvir:FirmwareUpdate").apply { acquire(15 * 60 * 1000L) }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY
    override fun onDestroy() { wakeLock?.let { if (it.isHeld) it.release() }; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        fun start(context: Context) { ContextCompat.startForegroundService(context, Intent(context, UvirFirmwareUpdateService::class.java)) }
        fun stop(context: Context) { context.stopService(Intent(context, UvirFirmwareUpdateService::class.java)) }
    }
}
