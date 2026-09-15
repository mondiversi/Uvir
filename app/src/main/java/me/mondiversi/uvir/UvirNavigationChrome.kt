package me.mondiversi.uvir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val UvirTitleActionButtonSize = 40.dp
internal val UvirTitleActionIconSize = 24.dp
internal val UvirTitleActionIconStrokeWidth = 2.21.dp
// Only the back arrow keeps additional visual weight; other title actions use the base stroke.
internal val UvirTitleBackIconStrokeWidth = 2.6.dp
internal val UvirTitleBarContentPadding = PaddingValues(
    start = 10.dp,
    top = 4.dp,
    end = 10.dp,
    bottom = 4.dp
)

@Composable
fun UvirMenuTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 20.sp
) {
    val scrollState = rememberScrollState()
    val layoutDirection = LocalLayoutDirection.current
    val fadeLeft =
        if (layoutDirection == LayoutDirection.Ltr) {
            scrollState.canScrollBackward
        } else {
            scrollState.canScrollForward
        }
    val fadeRight =
        if (layoutDirection == LayoutDirection.Ltr) {
            scrollState.canScrollForward
        } else {
            scrollState.canScrollBackward
        }

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    compositingStrategy =
                        CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    val fadeWidth = 14.dp.toPx()

                    if (fadeLeft) {
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black
                                    ),
                                    startX = 0f,
                                    endX = fadeWidth
                                ),
                            blendMode = BlendMode.DstIn
                        )
                    }

                    if (fadeRight) {
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Black,
                                        Color.Transparent
                                    ),
                                    startX = size.width - fadeWidth,
                                    endX = size.width
                                ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
    ) {
        Text(
            text = text,
            modifier = Modifier.horizontalScroll(scrollState),
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun UvirBackButton(
    onClick: () -> Unit
) {
    val description =
        stringResource(
            R.string.navigate_back
        )

    val tint =
        MaterialTheme
            .colorScheme
            .primary

    IconButton(
        onClick = onClick,
        modifier =
            Modifier
                .size(44.dp)
                .semantics {
                    contentDescription =
                        description
                }
    ) {
        Canvas(
            modifier =
                Modifier.size(24.dp)
        ) {
            val strokeWidth =
                UvirTitleBackIconStrokeWidth.toPx()

            val center =
                Offset(
                    x = size.width * 0.34f,
                    y = size.height * 0.50f
                )

            drawLine(
                color = tint,
                start =
                    Offset(
                        x = size.width * 0.68f,
                        y = size.height * 0.20f
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
                        x = size.width * 0.68f,
                        y = size.height * 0.80f
                    ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
