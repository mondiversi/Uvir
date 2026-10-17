package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A simulated tablet viewport; never changes the connected phone's display settings. */
class UvirAdaptiveDisplayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun iconsTextAndTouchTargetsGrowTogetherWhileTheViewportStaysConsistent() {
        var viewportWidthDp = 0
        var viewportHeightDp = 0
        var physicalSmallestWidthDp = 0
        var originalFontScale = 0f
        var adaptiveFontScale = 0f
        compose.setContent {
            val deviceConfiguration = LocalConfiguration.current
            originalFontScale = LocalDensity.current.fontScale
            Column {
                Text("Uvir", fontSize = 14.sp, modifier = Modifier.testTag("phone-text"))
                Box(Modifier.size(20.dp).testTag("phone-icon"))
                Box(Modifier.size(48.dp).clickable {}.testTag("phone-target"))
                val tabletConfiguration = Configuration(deviceConfiguration).apply {
                    smallestScreenWidthDp = 800
                    screenWidthDp = 800
                    screenHeightDp = 1280
                }
                CompositionLocalProvider(LocalConfiguration provides tabletConfiguration) {
                    UvirAdaptiveDisplay {
                        val effective = LocalConfiguration.current
                        val density = LocalDensity.current
                        SideEffect {
                            viewportWidthDp = effective.screenWidthDp
                            viewportHeightDp = effective.screenHeightDp
                            physicalSmallestWidthDp = effective.smallestScreenWidthDp
                            adaptiveFontScale = density.fontScale
                        }
                        Text("Uvir", fontSize = 14.sp, modifier = Modifier.testTag("tablet-text"))
                        Box(Modifier.size(20.dp).testTag("tablet-icon"))
                        Box(Modifier.size(48.dp).clickable {}.testTag("tablet-target"))
                    }
                }
            }
        }
        val iconRatio = compose.onNodeWithTag("tablet-icon").fetchSemanticsNode().boundsInRoot.width /
            compose.onNodeWithTag("phone-icon").fetchSemanticsNode().boundsInRoot.width
        val targetRatio = compose.onNodeWithTag("tablet-target").fetchSemanticsNode().boundsInRoot.width /
            compose.onNodeWithTag("phone-target").fetchSemanticsNode().boundsInRoot.width
        val textRatio = compose.onNodeWithTag("tablet-text").fetchSemanticsNode().boundsInRoot.height /
            compose.onNodeWithTag("phone-text").fetchSemanticsNode().boundsInRoot.height
        assertEquals(2f, iconRatio, 0.04f)
        assertEquals(2f, targetRatio, 0.04f)
        assertTrue("Text should grow with the layout", textRatio > 1.75f)
        compose.runOnIdle {
            assertEquals(400, viewportWidthDp)
            assertEquals(640, viewportHeightDp)
            assertEquals(800, physicalSmallestWidthDp)
            assertEquals(originalFontScale, adaptiveFontScale, 0f)
        }
    }

    @Test fun automaticEnlargementPreservesTheSystemFontConversion() {
        val nonLinearFontDensity = object : Density {
            override val density = 2f
            override val fontScale = 1.5f
            override fun Dp.toSp(): TextUnit = (value / 2f).sp
            override fun TextUnit.toDp(): Dp = (value * 2f).dp
        }
        var adaptedTextPx = 0f
        var adaptedDpPx = 0f
        var adaptedSp = 0f
        compose.setContent {
            val deviceConfiguration = LocalConfiguration.current
            val tabletConfiguration = Configuration(deviceConfiguration).apply {
                smallestScreenWidthDp = 800
                screenWidthDp = 800
                screenHeightDp = 1280
            }
            CompositionLocalProvider(
                LocalConfiguration provides tabletConfiguration,
                LocalDensity provides nonLinearFontDensity
            ) {
                UvirAdaptiveDisplay {
                    val density = LocalDensity.current
                    SideEffect {
                        adaptedTextPx = with(density) { 10.sp.toPx() }
                        adaptedDpPx = with(density) { 20.dp.toPx() }
                        adaptedSp = with(density) { 20.dp.toSp().value }
                    }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(80f, adaptedTextPx, 0f)
            assertEquals(80f, adaptedDpPx, 0f)
            assertEquals(10f, adaptedSp, 0f)
        }
    }
}
