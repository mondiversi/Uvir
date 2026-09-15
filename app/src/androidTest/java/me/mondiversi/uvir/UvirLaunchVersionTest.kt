package me.mondiversi.uvir

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolated launch artwork: no app startup, database or sensor commands. */
class UvirLaunchVersionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun currentVersionIsVisibleInsideLogoInBothOrientationsThemesAndDirections() {
        val landscape = mutableStateOf(false)
        val dark = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    Box(Modifier.size(
                        width = if (landscape.value) 300.dp else 280.dp,
                        height = if (landscape.value) 240.dp else 400.dp
                    ).testTag("launch_trial")) {
                        UvirLaunchScreen()
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        for (wide in listOf(false, true)) {
            for (darkTheme in listOf(false, true)) {
                for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                    compose.runOnIdle {
                        landscape.value = wide
                        dark.value = darkTheme
                        direction.value = layoutDirection
                    }
                    compose.mainClock.advanceTimeByFrame()
                    assertEquals(1, compose.onAllNodesWithText(BuildConfig.VERSION_NAME)
                        .fetchSemanticsNodes().size)
                    val version = compose.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()
                        .fetchSemanticsNode().boundsInRoot
                    val root = compose.onNodeWithTag("launch_trial").fetchSemanticsNode().boundsInRoot
                    val name = compose.onNodeWithText(context.getString(R.string.app_name))
                        .fetchSemanticsNode().boundsInRoot
                    assertEquals(root.center.x, version.center.x, 1f)
                    assertTrue("Version must stay on the logo above the app name", version.bottom < name.top)
                    assertTrue(version.left >= root.left && version.right <= root.right)
                    assertTrue(version.top >= root.top && version.bottom <= root.bottom)
                    compose.onNodeWithTag("launch_trial").captureToImage()
                }
            }
        }
    }
}
