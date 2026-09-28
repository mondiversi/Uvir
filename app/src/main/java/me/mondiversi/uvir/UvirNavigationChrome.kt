package me.mondiversi.uvir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val UvirTitleActionButtonSize = 40.dp
internal const val UvirTitleActionVisualScale = 0.96f
internal val UvirTitleActionIconSize = 24.dp
internal val UvirTitleActionIconStrokeWidth = 2.21.dp
// Only the back arrow keeps additional visual weight; other title actions use the base stroke.
internal val UvirTitleBackButtonSize = 40.dp
// Keep the back arrow independent: resizing the right-hand actions must not resize it.
internal val UvirTitleBackIconSize = 15.36.dp
internal val UvirTitleBackPressedVisualSize = 28.dp
internal val UvirTitleBackIconStrokeWidth = 2.6.dp
// The rounded chevron's ink lies slightly right of its canvas center.
// Move only the drawing, not the pressed area or the accessible touch target.
internal val UvirTitleBackIconOpticalOffset = UvirTitleBackIconSize * -0.0375f
internal val UvirTitleBarContentPadding = PaddingValues(
    start = 5.dp,
    top = 4.dp,
    end = 5.dp,
    bottom = 4.dp
)

/** Bare themed glyph and circular pressed feedback, with the full touch target. */
@Composable
internal fun UvirTitleActionButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    UvirAccessibleIconButton(
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier.size(UvirTitleActionButtonSize),
        enabled = enabled,
        pressedColor = iconColor,
        pressedVisualSize = UvirActionIconBadgeSize,
        visualScale = UvirTitleActionVisualScale,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) iconColor
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            content = content
        )
    }
}

@Composable
fun UvirMenuTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 20.sp
) {
    UvirScrollingText(text, modifier, color,
        style = LocalTextStyle.current.copy(fontSize = fontSize, fontWeight = FontWeight.Bold))
}

@Composable
fun UvirBackButton(
    onClick: () -> Unit
) {
    val description =
        stringResource(
            R.string.navigate_back
        )

    val accent = MaterialTheme.colorScheme.primary
    val tint = accent

    UvirAccessibleIconButton(
        contentDescription = description,
        onClick = onClick,
        modifier = Modifier.size(UvirTitleBackButtonSize),
        pressedColor = accent,
        pressedVisualSize = UvirTitleBackPressedVisualSize
    ) {
        Canvas(
            modifier =
                Modifier.size(UvirTitleBackIconSize)
        ) {
            val strokeWidth =
                UvirTitleBackIconStrokeWidth.toPx()
            val opticalOffset = UvirTitleBackIconOpticalOffset.toPx()
            val glyphWidth = size.width
            val glyphHeight = size.height
            val left = (size.width - glyphWidth) / 2f
            val top = (size.height - glyphHeight) / 2f

            val center =
                Offset(
                    x = left + glyphWidth * 0.34f + opticalOffset,
                    y = top + glyphHeight * 0.50f
                )

            drawLine(
                color = tint,
                start =
                    Offset(
                        x = left + glyphWidth * 0.68f + opticalOffset,
                        y = top + glyphHeight * 0.20f
                    ),
                end = center,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            drawLine(
                color = tint,
                start = center,
                end =
                    Offset(
                        x = left + glyphWidth * 0.68f + opticalOffset,
                        y = top + glyphHeight * 0.80f
                    ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
