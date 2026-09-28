package me.mondiversi.uvir

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Shared title/name overflow: full accessible text, fixed viewport and soft edges. */
@Composable
internal fun UvirScrollingText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    onTextLayout: (TextLayoutResult) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val fadeLeft = if (rtl) scrollState.canScrollForward else scrollState.canScrollBackward
    val fadeRight = if (rtl) scrollState.canScrollBackward else scrollState.canScrollForward

    LaunchedEffect(text, scrollState.maxValue, rtl) {
        val maximum = scrollState.maxValue
        scrollState.scrollTo(0)
        if (maximum <= 0) return@LaunchedEffect

        val travelDuration = (1_800 + maximum * 12).coerceIn(2_600, 8_000)
        delay(900)
        while (true) {
            scrollState.animateScrollTo(maximum, tween(travelDuration, easing = LinearEasing))
            delay(900)
            scrollState.animateScrollTo(0, tween(travelDuration, easing = LinearEasing))
            delay(1_200)
        }
    }

    Box(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fadeWidth = minOf(14.dp.toPx(), size.width / 2f)
            if (fadeLeft) drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, fadeWidth),
                blendMode = BlendMode.DstIn
            )
            if (fadeRight) drawRect(
                Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), size.width - fadeWidth, size.width),
                blendMode = BlendMode.DstIn
            )
        }
    ) {
        Text(text, modifier = Modifier.horizontalScroll(scrollState), color = color,
            style = style, maxLines = 1, softWrap = false, onTextLayout = onTextLayout)
    }
}
