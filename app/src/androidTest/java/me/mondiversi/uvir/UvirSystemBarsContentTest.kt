package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolated UI fixtures: no sensor connection, preferences or archive writes. */
class UvirSystemBarsContentTest {
    @get:Rule val compose = createComposeRule()
    private val width = mutableIntStateOf(400)
    private val night = mutableStateOf(false)
    private val direction = mutableStateOf(LayoutDirection.Ltr)
    private val hasInsets = mutableStateOf(true)

    @Test fun physicalInsetsAreConsumedOnceWithoutAddingMarginsToAlreadyFittedWindows() {
        val physicalInsets = WindowInsets(left = 17, top = 52, right = 23, bottom = 60)
        val noInsets = WindowInsets(0, 0, 0, 0)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                smallestScreenWidthDp = width.intValue
                screenWidthDp = width.intValue
                screenHeightDp = width.intValue * 2
            }
            val insets = if (hasInsets.value) physicalInsets else noInsets
            Box(Modifier.fillMaxSize().testTag("window")) {
                CompositionLocalProvider(LocalConfiguration provides configuration,
                    LocalLayoutDirection provides direction.value) {
                    UvirAdaptiveDisplay {
                        UvirSystemBarsContent(if (night.value) Color.Black else Color.White, insets) {
                            // A nested consumer must not reapply the safe area.
                            Box(Modifier.fillMaxSize().windowInsetsPadding(insets).testTag("content"))
                        }
                    }
                }
            }
        }
        for (screenWidth in listOf(400, 800)) for (dark in listOf(false, true))
            for (rtl in listOf(false, true)) for (insetWindow in listOf(false, true)) {
                compose.runOnIdle {
                    width.intValue = screenWidth
                    night.value = dark
                    direction.value = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                    hasInsets.value = insetWindow
                }
                val window = compose.onNodeWithTag("window").fetchSemanticsNode().boundsInRoot
                val content = compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
                assertEquals(if (insetWindow) 17f else 0f, content.left - window.left, 1f)
                assertEquals(if (insetWindow) 52f else 0f, content.top - window.top, 1f)
                assertEquals(if (insetWindow) 23f else 0f, window.right - content.right, 1f)
                assertEquals(if (insetWindow) 60f else 0f, window.bottom - content.bottom, 1f)
            }
    }

    @Test fun sharedPageTitleAndFloatingButtonRespectRealSystemBarsAtBothScales() {
        var pageInsets: WindowInsets = WindowInsets(0, 0, 0, 0)
        var pageDensity: Density = compose.density
        var titlePaddingPx = 0
        var floatingPaddingPx = 0
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            val configuration = Configuration(LocalConfiguration.current).apply {
                smallestScreenWidthDp = width.intValue
                screenWidthDp = width.intValue
                screenHeightDp = width.intValue * 2
            }
            MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.fillMaxSize().testTag("window")) {
                    CompositionLocalProvider(LocalConfiguration provides configuration,
                        LocalLayoutDirection provides direction.value) {
                        UvirAdaptiveDisplay {
                            val density = LocalDensity.current
                            val insets = WindowInsets.safeDrawing
                            SideEffect {
                                pageInsets = insets
                                pageDensity = density
                                titlePaddingPx = with(density) { 4.dp.roundToPx() }
                                floatingPaddingPx = with(density) { 16.dp.roundToPx() }
                            }
                            UvirSystemBarsContent(MaterialTheme.colorScheme.background) {
                                UvirFullScreenPage(
                                    onDismissRequest = {},
                                    title = { UvirMenuTitle("Trial page", Modifier.testTag("title")) },
                                    text = { Box(Modifier.fillMaxSize().testTag("body")) },
                                    containerColor = MaterialTheme.colorScheme.background,
                                    contentColor = MaterialTheme.colorScheme.onBackground,
                                    topActionButton = { Box(Modifier.size(40.dp).testTag("title_action")) },
                                    floatingActionButton = { Box(Modifier.size(56.dp).testTag("floating_action")) }
                                )
                            }
                        }
                    }
                }
            }
        }
        for (screenWidth in listOf(400, 800)) for (dark in listOf(false, true))
            for (rtl in listOf(false, true)) {
                compose.runOnIdle {
                    width.intValue = screenWidth
                    night.value = dark
                    direction.value = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                }
                val window = compose.onNodeWithTag("window").fetchSemanticsNode().boundsInRoot
                val title = compose.onNodeWithTag("title").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val action = compose.onNodeWithTag("title_action").fetchSemanticsNode().boundsInRoot
                val floating = compose.onNodeWithTag("floating_action").fetchSemanticsNode().boundsInRoot
                val body = compose.onNodeWithTag("body").fetchSemanticsNode().boundsInRoot
                compose.runOnIdle {
                    // Android delivers insets after composition; sample their
                    // current pixel values only once the layout is settled.
                    val safeTop = pageInsets.getTop(pageDensity)
                    val safeBottom = pageInsets.getBottom(pageDensity)
                    assertEquals("Status padding must not be doubled or enlarged (width=$screenWidth, night=$dark, rtl=$rtl, title=$title)",
                        safeTop.toFloat() + titlePaddingPx, action.top - window.top, 1f)
                    assertTrue("Title stays below the system bar", title.top >= window.top + safeTop)
                    assertTrue("Body stays below the title", body.top >= action.bottom)
                    assertEquals("Navigation padding is consumed only once by the Scaffold",
                        safeBottom.toFloat() + floatingPaddingPx, window.bottom - floating.bottom, 1f)
                }
            }
    }
}
