package me.mondiversi.uvir

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Resolve the final size during measurement, never by changing state after a rendered frame. */
internal fun fitUvirButtonFontSize(maximum: Float, minimum: Float, fits: (Float) -> Boolean): Float {
    var candidate = maximum.coerceAtLeast(minimum)
    while (candidate > minimum && !fits(candidate)) {
        candidate = (candidate - 0.5f).coerceAtLeast(minimum)
    }
    return candidate
}

@Composable
internal fun AdaptiveSingleLineButtonText(
    text: String,
    maxFontSize: TextUnit = 14.sp,
    minFontSize: TextUnit = 10.sp,
    sizingTexts: List<String> = listOf(text),
    fontWeight: FontWeight? = null,
    onTextLayout: (TextLayoutResult) -> Unit = {}
) {
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.merge(
        androidx.compose.ui.text.TextStyle(fontWeight = fontWeight, textAlign = TextAlign.Center)
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = constraints.maxWidth
        val fontSize = remember(measurer, width, style, text, sizingTexts, maxFontSize, minFontSize) {
            fitUvirButtonFontSize(maxFontSize.value, minFontSize.value) { candidate ->
                (sizingTexts + text).distinct().all { label ->
                    !measurer.measure(
                        text = label,
                        style = style.copy(fontSize = candidate.sp),
                        maxLines = 1,
                        softWrap = false,
                        constraints = Constraints(maxWidth = width)
                    ).hasVisualOverflow
                }
            }.sp
        }
        Text(text = text, modifier = Modifier.fillMaxWidth(), style = style.copy(fontSize = fontSize),
            maxLines = 1, softWrap = false, onTextLayout = onTextLayout)
    }
}
