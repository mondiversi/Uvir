package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.constrainHeight

internal data class UvirLiveIslandBounds(val top: Float, val bottom: Float)

internal fun uvirLiveIslandEdgeGlow(
    groups: Collection<SensorGroup>,
    bounds: Map<SensorGroup, UvirLiveIslandBounds>,
    viewport: UvirLiveIslandBounds?
): Pair<Boolean, Boolean> {
    if (viewport == null) return false to false
    val activeBounds = groups.mapNotNull(bounds::get)
    return activeBounds.any { it.top < viewport.top } to
        activeBounds.any { it.bottom > viewport.bottom }
}

/** Row-major user order; default remains UV above NIR on the left, visible on the right. */
@Composable
internal fun UvirLiveLandscapeIslands(
    onGroupBounds: (SensorGroup, UvirLiveIslandBounds) -> Unit,
    order: List<SensorGroup> = UvirDefaultMainIslandOrder,
    content: @Composable (SensorGroup) -> Unit
) {
    val groups = decodeUvirMainIslandOrder(order.joinToString(",") { it.name })
    LookaheadScope {
        // One parent keeps the keyed islands alive when they cross between columns.
        Layout(modifier = Modifier.fillMaxWidth(), content = {
            groups.forEach { group ->
                key(group) {
                    Box(Modifier.animateBounds(
                        lookaheadScope = this@LookaheadScope,
                        boundsTransform = { _, _ ->
                            tween(UvirMainIslandMoveDurationMillis, easing = FastOutSlowInEasing)
                        }
                    ).onGloballyPositioned { coordinates ->
                        val top = coordinates.positionInWindow().y
                        onGroupBounds(group, UvirLiveIslandBounds(top, top + coordinates.size.height))
                    }) { content(group) }
                }
            }
        }) { measurables, constraints ->
            val spacing = UvirIslandSpacing.roundToPx()
            val columnWidth = ((constraints.maxWidth - spacing) / 2).coerceAtLeast(0)
            val placeables = measurables.map { it.measure(Constraints.fixedWidth(columnWidth)) }
            val columnHeights = IntArray(2)
            val positions = placeables.mapIndexed { index, placeable ->
                val column = index % 2
                IntOffset(column * (columnWidth + spacing), columnHeights[column]).also {
                    columnHeights[column] += placeable.height + spacing
                }
            }
            val height = (columnHeights.maxOrNull()!! - spacing).coerceAtLeast(0)
            layout(constraints.maxWidth, constraints.constrainHeight(height)) {
                placeables.forEachIndexed { index, placeable ->
                    placeable.placeRelative(positions[index])
                }
            }
        }
    }
}
