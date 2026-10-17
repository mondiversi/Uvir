package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated layout fixtures: no database, transport or production preferences. */
class UvirIslandColumnsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun landscapeUsesEqualColumnsWithFullWidthLabelsAndCorrectRtlPlacement() {
        val rtl = mutableStateOf(false)
        val night = mutableStateOf(false)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
            }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                UvirIslandColumns(Modifier.requiredWidth(600.dp), spacing = 15.dp) {
                    Box(Modifier.fillMaxWidth().height(20.dp).uvirIslandFullWidth().testTag("heading"))
                    Box(Modifier.fillMaxWidth().height(200.dp).background(
                        if (night.value) Color.DarkGray else Color.White).testTag("first"))
                    Box(Modifier.fillMaxWidth().height(50.dp).testTag("second"))
                    Box(Modifier.fillMaxWidth().height(80.dp).testTag("third"))
                }
            }
        }
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { rtl.value = rightToLeft; night.value = dark }
            val heading = compose.onNodeWithTag("heading").getUnclippedBoundsInRoot()
            val first = compose.onNodeWithTag("first").getUnclippedBoundsInRoot()
            val second = compose.onNodeWithTag("second").getUnclippedBoundsInRoot()
            val third = compose.onNodeWithTag("third").getUnclippedBoundsInRoot()
            assertEquals((first.right - first.left).value, (second.right - second.left).value, 0.1f)
            assertEquals(600f, (heading.right - heading.left).value, 0.1f)
            assertEquals(first.top.value, second.top.value, 0.1f)
            assertEquals(second.left.value, third.left.value, 0.1f)
            assertEquals(15f, third.top.value - second.bottom.value, 0.1f)
            assertEquals(rightToLeft, first.left > second.left)
        }
    }

    @Test fun rotationReflowsTheSameControlsWithoutResettingTheirState() {
        val landscape = mutableStateOf(true)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = if (landscape.value) Configuration.ORIENTATION_LANDSCAPE
                    else Configuration.ORIENTATION_PORTRAIT
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                UvirIslandColumns(Modifier.requiredWidth(600.dp), spacing = 15.dp) {
                    Box(Modifier.height(60.dp).testTag("first"))
                    var clicks by remember { mutableIntStateOf(0) }
                    Button(onClick = { clicks++ }, modifier = Modifier.height(60.dp).testTag("action")) {
                        Text("Clicks: $clicks")
                    }
                }
            }
        }
        compose.onNodeWithTag("action").performClick()
        compose.onNodeWithText("Clicks: 1").assertExists()
        compose.runOnIdle { landscape.value = false }
        compose.onNodeWithText("Clicks: 1").assertExists()
        val first = compose.onNodeWithTag("first").getUnclippedBoundsInRoot()
        val action = compose.onNodeWithTag("action").getUnclippedBoundsInRoot()
        assertEquals(600f, (action.right - action.left).value, 0.1f)
        assertEquals(first.left.value, action.left.value, 0.1f)
        assertEquals(15f, action.top.value - first.bottom.value, 0.1f)
        compose.runOnIdle { landscape.value = true }
        compose.onNodeWithText("Clicks: 1").assertExists()
    }

    @Test fun nestedIslandGroupsKeepTheWholePageWidthInsteadOfBeingHalvedTwice() {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                UvirIslandColumns(Modifier.requiredWidth(600.dp)) {
                    UvirIslandColumns(spacing = 15.dp) {
                        Box(Modifier.height(40.dp).testTag("one"))
                        Box(Modifier.height(40.dp).testTag("two"))
                    }
                }
            }
        }
        val one = compose.onNodeWithTag("one").getUnclippedBoundsInRoot()
        val two = compose.onNodeWithTag("two").getUnclippedBoundsInRoot()
        assertEquals(292.5f, (one.right - one.left).value, 0.6f)
        assertEquals((one.right - one.left).value, (two.right - two.left).value, 0.1f)
        assertEquals(one.top.value, two.top.value, 0.1f)
    }
}
