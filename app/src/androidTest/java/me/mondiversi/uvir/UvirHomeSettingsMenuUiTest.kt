package me.mondiversi.uvir

import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Local popup fixtures: callbacks never access settings, archives or sensor transports. */
class UvirHomeSettingsMenuUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun groupHeadingsAreTextOnlyInDayNightAndRtl() {
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                val colors = MaterialTheme.colorScheme
                CompositionLocalProvider(LocalLayoutDirection provides
                    if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                        UvirHomeSettingsDropdown(colors.onSurface, colors.onSurfaceVariant, colors.surface,
                            onOpenPage = {}, onOpenInfo = {})
                    }
                }
            }
        }
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; rtl.value = rightToLeft }
            compose.onNodeWithTag("home_settings_menu_button").performClick()
            uvirHomeSettingsMenuGroups.filter { it.titleRes != null }.forEach { group ->
                val caption = compose.onNodeWithText(context.getString(group.titleRes!!))
                caption.performScrollTo().assertIsDisplayed()
            }
            for (name in listOf("SENSOR", "APP")) {
                compose.onNodeWithTag("home_settings_group_icon_$name",
                    useUnmergedTree = true).assertDoesNotExist()
            }
            Espresso.pressBack()
        }
    }

    @Test fun everyEntryIsScrollableAndRoutesDirectlyInDayNightAndRtl() {
        val night = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        val selected = mutableListOf<UvirSettingsPage>()
        var infoClicks = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                val colors = MaterialTheme.colorScheme
                CompositionLocalProvider(LocalLayoutDirection provides
                    if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                        UvirHomeSettingsDropdown(colors.onSurface, colors.onSurfaceVariant, colors.surface,
                            onOpenPage = { selected += it }, onOpenInfo = { infoClicks++ })
                    }
                }
            }
        }
        for (dark in listOf(false, true)) for (rightToLeft in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; rtl.value = rightToLeft }
            uvirHomeSettingsMenuGroups.flatMap { it.entries }.forEach { entry ->
                compose.onNodeWithTag("home_settings_menu_button").assertHasClickAction().performClick()
                compose.onNodeWithTag("home_settings_menu").assertIsDisplayed()
                val item = compose.onNodeWithTag("home_settings_item_${entry.page?.name ?: "INFO"}")
                item.performScrollTo().assertIsDisplayed()
                val text = compose.onNodeWithText(context.getString(entry.titleRes))
                val layouts = mutableListOf<TextLayoutResult>()
                text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty())
                assertFalse("Menu label must fit: ${entry.titleRes}", layouts.single().hasVisualOverflow)
                item.performClick()
                compose.onNodeWithTag("home_settings_menu").assertDoesNotExist()
                compose.runOnIdle {
                    if (entry.page != null) assertEquals(entry.page, selected.last())
                }
            }
        }
        compose.runOnIdle { assertEquals(4, infoClicks); assertEquals(36, selected.size) }
    }

    @Test fun dismissingWithBackDoesNotNavigateOrOpenTheOldIndex() {
        var callbacks = 0
        compose.setContent {
            MaterialTheme {
                val colors = MaterialTheme.colorScheme
                UvirHomeSettingsDropdown(colors.onSurface, colors.onSurfaceVariant, colors.surface,
                    onOpenPage = { callbacks++ }, onOpenInfo = { callbacks++ })
            }
        }
        compose.onNodeWithTag("home_settings_menu_button").performClick()
        compose.onNodeWithTag("home_settings_menu").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithTag("home_settings_menu").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, callbacks) }
    }

    @Test fun backgroundDimmingIsOptInAndRestoredWhenDisabledOrDismissed() {
        val expanded = mutableStateOf(true)
        val amount = mutableStateOf(0f)
        var popupView: View? = null
        compose.setContent {
            MaterialTheme {
                UvirAdaptiveDropdownMenu(expanded.value, { expanded.value = false },
                    dimAmount = amount.value) {
                    val view = LocalView.current
                    SideEffect { popupView = view }
                    Text("Menu item")
                }
            }
        }
        var params: WindowManager.LayoutParams? = null
        var originalDimFlag = 0
        var originalDimAmount = 0f
        val dimFlag = WindowManager.LayoutParams.FLAG_DIM_BEHIND
        compose.runOnIdle {
            params = popupView!!.rootView.layoutParams as WindowManager.LayoutParams
            originalDimFlag = params!!.flags and dimFlag
            originalDimAmount = params!!.dimAmount
            assertEquals(0, originalDimFlag)
            amount.value = .32f
        }
        compose.runOnIdle {
            assertEquals(dimFlag, params!!.flags and dimFlag)
            assertEquals(.32f, params!!.dimAmount, .001f)
            amount.value = 0f
        }
        compose.runOnIdle {
            assertEquals(originalDimFlag, params!!.flags and dimFlag)
            assertEquals(originalDimAmount, params!!.dimAmount, .001f)
            amount.value = .32f
        }
        Espresso.pressBack()
        compose.runOnIdle {
            assertFalse(expanded.value)
            assertEquals(originalDimFlag, params!!.flags and dimFlag)
            assertEquals(originalDimAmount, params!!.dimAmount, .001f)
        }
    }
}
