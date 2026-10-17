package me.mondiversi.uvir

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UvirNotificationIconTest {
    @get:Rule val compose = createComposeRule()

    @Test fun speechBubbleKeepsStandardSizeAndThemeColors() {
        val night = mutableStateOf(false)
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            MaterialTheme {
                val card = if (night.value) Color(0xFF1C242B) else Color.White
                val text = if (night.value) Color.White else Color(0xFF101418)
                Box(Modifier.background(card).testTag("notification-preview").padding(12.dp)) {
                    Column(Modifier.width(280.dp)) {
                        CompositionLocalProvider(LocalUvirSettingsNavigation provides
                            UvirSettingsNavigation(null, {})) {
                            SettingsSection("Notifiche", card, text, Color.Gray,
                                titleIcon = ConnectivityIconType.NOTIFICATIONS,
                                settingsPage = UvirSettingsPage.ALERTS) {}
                        }
                        ConnectivitySectionIcon(ConnectivityIconType.NOTIFICATIONS,
                            modifier = Modifier.testTag("notification-glyph"), tint = text)
                    }
                }
            }
        }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            val glyph = compose.onNodeWithTag("notification-glyph")
                .assertWidthIsEqualTo(20.dp).assertHeightIsEqualTo(20.dp)
            val pixels = glyph.captureToImage().toPixelMap()
            val ink = (0 until pixels.height).sumOf { y ->
                (0 until pixels.width).count { x ->
                    val c = pixels[x, y]
                    if (dark) c.red > .9f && c.green > .9f && c.blue > .9f
                    else c.red < .2f && c.green < .2f && c.blue < .2f
                }
            }
            assertTrue("Visible bubble in ${if (dark) "night" else "day"} mode",
                ink > pixels.width * pixels.height / 12)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.externalCacheDir, "notification-icon-${if (dark) "night" else "day"}.png")
                .outputStream().use { output ->
                    compose.onNodeWithTag("notification-preview").captureToImage()
                        .asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
                }
        }
    }
}
