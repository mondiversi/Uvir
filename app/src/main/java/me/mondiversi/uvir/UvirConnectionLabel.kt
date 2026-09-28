package me.mondiversi.uvir

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Only USB and Wi-Fi need a small lift beside text; keep the other symbols centered.
internal fun uvirConnectionLabelOpticalLift(type: ConnectivityIconType): Dp = when (type) {
    ConnectivityIconType.USB, ConnectivityIconType.WIFI -> 1.dp
    else -> 0.dp
}

/** Use the same per-symbol optical alignment in status and sensor rows. */
@Composable
internal fun UvirConnectionLabel(
    text: String,
    type: ConnectivityIconType,
    iconColor: Color,
    textColor: Color,
    iconSize: Dp,
    style: TextStyle,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    strokeScale: Float = UvirHomeHeaderIconStrokeScale,
    iconAlpha: Float = 1f,
    maxLines: Int = 1,
    softWrap: Boolean = true,
    scrollOverflow: Boolean = false,
    textMaxWidth: Dp? = null
) {
    val resolvedStyle = LocalTextStyle.current.merge(style)
    val density = LocalDensity.current
    val opticalLiftPx = with(density) { uvirConnectionLabelOpticalLift(type).toPx() }
    val typeface by LocalFontFamilyResolver.current.resolve(
        resolvedStyle.fontFamily,
        resolvedStyle.fontWeight ?: FontWeight.Normal,
        resolvedStyle.fontStyle ?: FontStyle.Normal,
        resolvedStyle.fontSynthesis ?: FontSynthesis.All
    )
    val textSize = with(density) { resolvedStyle.fontSize.toPx() }
    val paint = remember(typeface, textSize) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface as Typeface
            this.textSize = textSize
        }
    }
    var inkCenterOffset by remember(text, paint) { mutableFloatStateOf(0f) }
    val updateInkCenter: (TextLayoutResult) -> Unit = { layout ->
        val bounds = Rect()
        var top = Float.POSITIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        for (line in 0 until layout.lineCount) {
            val start = layout.getLineStart(line)
            val end = layout.getLineEnd(line, visibleEnd = true)
            if (end > start) {
                paint.getTextBounds(text, start, end, bounds)
                if (!bounds.isEmpty) {
                    val baseline = layout.getLineBaseline(line)
                    top = minOf(top, baseline + bounds.top)
                    bottom = maxOf(bottom, baseline + bounds.bottom)
                }
            }
        }
        inkCenterOffset = if (top.isFinite()) (top + bottom) / 2f - layout.size.height / 2f else 0f
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        UvirHomeSensorSourceIcon(
            type = type, tint = iconColor, iconSize = iconSize, strokeScale = strokeScale,
            modifier = iconModifier.graphicsLayer {
                alpha = iconAlpha
                translationY = inkCenterOffset - opticalLiftPx
            }
        )
        Spacer(Modifier.width(6.dp))
        val labelModifier = textModifier.weight(1f, fill = textMaxWidth == null)
            .then(if (textMaxWidth == null) Modifier else Modifier.widthIn(max = textMaxWidth))
        if (scrollOverflow) {
            UvirScrollingText(text, labelModifier, textColor, resolvedStyle, updateInkCenter)
        } else {
            Text(text, modifier = labelModifier, color = textColor,
                style = resolvedStyle, maxLines = maxLines, softWrap = softWrap,
                overflow = TextOverflow.Ellipsis, onTextLayout = updateInkCenter)
        }
    }
}
