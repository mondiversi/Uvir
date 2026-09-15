package me.mondiversi.uvir

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Clip before the indication: the normal press effect covers the card, including
 * its padding, without spilling beyond rounded corners. No new ripple colors. */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.uvirRecordListPressTarget(
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier = clip(RoundedCornerShape(16.dp))
    .combinedClickable(onClick = onClick, onLongClick = onLongClick)

// Shared reduced gap between record cards in the same session, including
// the first card below its header. It stays outside the header's press region.
internal val UvirRecordSessionItemGap = 5.dp

// Header and record cards share the same horizontal footprint, outside the rail.
internal val UvirRecordSessionContentInset = 14.dp

// The old 6 dp lower gap is split evenly; the leading inset mirrors the
// disclosure arrow's inner spacing without shifting its trailing alignment.
internal val UvirRecordSessionHeaderContentPadding = PaddingValues(
    start = UvirIslandContentPadding, top = 3.dp, bottom = 3.dp
)

/** Apply before content padding so the whole session title row is interactive.
 * The caller supplies the same action for the row and its selection checkbox. */
internal fun Modifier.uvirRecordSessionHeaderPressTarget(
    onClick: () -> Unit
): Modifier = uvirRecordSessionHeaderPressTarget(onClick, onLongClick = null)

/** A long press selects the session, while the normal tap keeps its open action. */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.uvirRecordSessionHeaderPressTarget(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
): Modifier = clip(RoundedCornerShape(8.dp))
    .combinedClickable(onClick = onClick, onLongClick = onLongClick)

/** Draw on the complete list item: header, reduced gap and record card.
 * Continuing lazy items meet without introducing a gap in the session rail. */
internal fun Modifier.uvirRecordSessionRail(
    color: Color, isFirst: Boolean, isLast: Boolean
): Modifier = drawBehind {
    val stroke = 3.dp.toPx()
    val x = if (layoutDirection == LayoutDirection.Ltr) 4.dp.toPx()
        else size.width - 4.dp.toPx()
    drawLine(
        color = color,
        start = Offset(x, if (isFirst) stroke / 2f else 0f),
        end = Offset(x, if (isLast) size.height - UvirRecordSessionItemGap.toPx()
            else size.height),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
}
