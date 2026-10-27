package me.mondiversi.uvir

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Lifecycle contract: keep the app visible without changing system settings or taking a wake lock. */
class UvirForegroundScreenAwakeTest {
    private val activity = listOf(
        File("src/main/java/me/mondiversi/uvir/MainActivity.kt"),
        File("app/src/main/java/me/mondiversi/uvir/MainActivity.kt")
    ).first { it.isFile }.readText()

    private fun lifecycle(name: String): String = activity.substringAfter("override fun $name() {")
        .substringBefore("\n    override fun ")

    @Test fun everyResumeRequestsAnAwakeWindowIndependentlyOfTheSelectedScreenOrSensor() {
        val resume = lifecycle("onResume")
        val request = "window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)"
        assertTrue(resume.contains(request))
        assertTrue(resume.indexOf(request) > resume.indexOf("super.onResume()"))
        assertTrue(resume.indexOf(request) < resume.indexOf("UsbPhoneRuntimeStore.get(applicationContext).onForeground()"))
        assertEquals(1, Regex(Regex.escape(request)).findAll(activity).count())
    }

    @Test fun pausingReleasesTheRequestAndRetainsTheAndroidLifecycleCallback() {
        val pause = lifecycle("onPause")
        assertTrue(pause.contains("window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)"))
        assertTrue(pause.contains("super.onPause()"))
        assertFalse(pause.contains("window.addFlags"))
    }

    @Test fun theRequestNeverChangesScreenTimeoutOrWakesAUserLockedPhone() {
        for (name in listOf("onResume", "onPause")) {
            val lifecycle = lifecycle(name)
            for (forbidden in listOf("SCREEN_OFF_TIMEOUT", "Settings.System.put", "Settings.Global.put",
                "newWakeLock", "FULL_WAKE_LOCK", "ACQUIRE_CAUSES_WAKEUP", "FLAG_TURN_SCREEN_ON", "setTurnScreenOn"))
                assertFalse("$name must not use $forbidden", lifecycle.contains(forbidden))
        }
    }
}
