package me.mondiversi.uvir

import android.app.Notification
import android.app.NotificationManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Only the disposable test package posts notifications; no transports or archives are opened. */
class UvirMockConnectionNotificationServiceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun foregroundNotificationSwitchesToOrangeMockStatusAndBackWithoutStaleColorOrProgress() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        check(ctx.packageName.endsWith(".verification")) { "Use the isolated verification APK" }
        val manager = ctx.getSystemService(NotificationManager::class.java)
        compose.setContent { KeepUvirVerificationScreenOn() }
        val states = listOf(UvirConnectionSensorState("a", UvirConnectionState.CONNECTED, true),
            UvirConnectionSensorState("b", UvirConnectionState.CONNECTING))
        val real = buildUvirConnectionNotification(ctx.resources, states, emptyList(), "a")
        val fake = buildUvirConnectionNotification(ctx.resources, states, emptyList(), "", simulationActive = true)
        fun posted() = manager.activeNotifications.singleOrNull { it.id == 4106 }?.notification
        try {
            for (payload in listOf(real, fake, real)) {
                compose.runOnIdle { UvirConnectionForegroundService.update(ctx, active = true, notification = payload) }
                compose.waitUntil(10_000) {
                    posted()?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() == payload.text
                }
                val notification = requireNotNull(posted())
                assertEquals(payload.lines.joinToString("\n"),
                    notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
                assertEquals(payload.colorArgb ?: Notification.COLOR_DEFAULT, notification.color)
                assertEquals(R.drawable.ic_notification_uvir, notification.smallIcon.resId)
                assertEquals(payload.busy, notification.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
                assertEquals(ctx.getString(R.string.app_name),
                    notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                assertNull(notification.sound)
                assertNull(notification.vibrate)
            }
        } finally {
            UvirConnectionForegroundService.update(ctx, active = false)
            compose.waitUntil(10_000) { posted() == null }
        }
    }
}
