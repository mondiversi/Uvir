package me.mondiversi.uvir

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** Isolated rendering only: no user records or sensor settings are changed. */
class UvirSessionVariantCountTypographyTest : SessionNoteEditingFixture() {
    @get:Rule val compose = createComposeRule()

    @Test fun variantCountIsSmallerAndVerticallyCenteredInBothThemesAndDirections() {
        val night = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val scale = mutableStateOf(1f)
        val suffix = mutableStateOf<String?>("(3×2)")
        compose.setContent {
            val density = LocalDensity.current
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, scale.value),
                LocalLayoutDirection provides direction.value
            ) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    Box(Modifier.fillMaxSize()) {
                    val colors = MaterialTheme.colorScheme
                    UvirDetailContextCard(
                        automatic = true, sensorName = "Portatile", note = "Test",
                        cardColor = colors.surface, primaryText = colors.onSurface,
                        secondaryText = colors.onSurfaceVariant,
                        detailIcon = UvirDetailMetadataIconKind.ACQUISITION,
                        detailText = "Acquisizioni: 6", detailSuffix = suffix.value,
                        detailMaxLines = 2
                    )
                    }
                }
            }
        }
        for (dark in listOf(false, true)) {
            for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                for (fontScale in listOf(1f, 1.5f)) {
                    compose.runOnIdle {
                        night.value = dark
                        direction.value = layout
                        scale.value = fontScale
                    }
                    val count = compose.onNodeWithText("Acquisizioni: 6").assertIsDisplayed()
                    val variant = compose.onNodeWithText("(3×2)").assertIsDisplayed()
                    assertEquals(12.sp, textLayout(count).layoutInput.style.fontSize)
                    assertEquals(10.sp, textLayout(variant).layoutInput.style.fontSize)
                    assertCompleteText(textLayout(count))
                    assertCompleteText(textLayout(variant))
                    val countBounds = count.fetchSemanticsNode().boundsInRoot
                    val variantBounds = variant.fetchSemanticsNode().boundsInRoot
                    assertTrue("Count and variants must have the same vertical center",
                        abs(countBounds.center.y - variantBounds.center.y) <= 1f)
                    assertTrue("Variants must stay beside the acquisition count",
                        if (layout == LayoutDirection.Ltr) variantBounds.left >= countBounds.right
                        else variantBounds.right <= countBounds.left)
                }
            }
        }
        compose.runOnIdle { suffix.value = null }
        compose.onNodeWithText("(3×2)").assertDoesNotExist()
        compose.onNodeWithText("Acquisizioni: 6").assertIsDisplayed()
    }

    @Test fun realSessionUsesTheSmallerCenteredVariantSuffix() {
        database.startAcquisitionSession(12L, "original", 900L, a)
        val records = List(6) { database.readRecord(acquisition())!! }
        assertTrue(database.updateAcquisitionSessionVariantsPerPosition(12L, 2))
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            CompositionLocalProvider(LocalContext provides context) {
                MaterialTheme {
                    UvirActionTheme {
                        val colors = MaterialTheme.colorScheme
                        SessionChartScreen(
                            12L, records, database, colors.background, colors.surface,
                            colors.onSurface, colors.onSurfaceVariant,
                            onDeleteSession = {}, onOpenRecord = { _, _ -> }, onBack = {}
                        )
                    }
                }
            }
        }
        val count = compose.onNodeWithText(context.getString(R.string.session_acquisitions_detail, 6))
            .assertIsDisplayed()
        val variant = compose.onNodeWithText("(3×2)").assertIsDisplayed()
        assertEquals(12.sp, textLayout(count).layoutInput.style.fontSize)
        assertEquals(10.sp, textLayout(variant).layoutInput.style.fontSize)
        assertTrue(abs(count.fetchSemanticsNode().boundsInRoot.center.y -
            variant.fetchSemanticsNode().boundsInRoot.center.y) <= 1f)
    }

    private fun assertCompleteText(layout: TextLayoutResult) {
        // A wrap-content weighted Text can report unused paragraph width as overflow.
        // Check the actual rendered lines and characters instead.
        assertFalse(layout.didOverflowHeight)
        for (line in 0 until layout.lineCount) assertFalse(layout.isLineEllipsized(line))
        assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
}
