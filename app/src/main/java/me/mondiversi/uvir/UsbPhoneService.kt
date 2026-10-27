package me.mondiversi.uvir

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Keeps PHONE-owned USB sessions running while the screen is off. It never starts/restarts a session. */
class UsbPhoneService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    override fun onCreate() {
        super.onCreate()
        val channel = "uvir_usb_recording"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel, getString(R.string.usb_sensor_name), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1403, NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification_uvir).setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.usb_phone_recording)).setOngoing(true).setContentIntent(open).build())
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:usb-recording")
            .apply { setReferenceCounted(false); acquire() }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onTaskRemoved(rootIntent: Intent?) { UsbPhoneRuntimeStore.close(); stopSelf() }
    override fun onDestroy() {
        UsbPhoneRuntimeStore.onRecordingServiceStopped()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }
    companion object {
        fun update(context: Context, active: Boolean) {
            if (active) ContextCompat.startForegroundService(context, Intent(context, UsbPhoneService::class.java))
            else context.stopService(Intent(context, UsbPhoneService::class.java))
        }
    }
}
