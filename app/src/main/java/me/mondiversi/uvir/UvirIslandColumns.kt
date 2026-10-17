package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp

private object UvirFullWidthIsland : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@UvirFullWidthIsland
}

/** Headings, separators and nested island groups stay across the whole page. */
internal fun Modifier.uvirIslandFullWidth(): Modifier = then(UvirFullWidthIsland)

internal fun uvirIslandColumnCount(landscape: Boolean, widthDp: Float): Int =
    if (landscape && widthDp >= 480f) 2 else 1

internal data class UvirIslandPosition(val column: Int, val top: Int)
internal data class UvirIslandPlacement(val positions: List<UvirIslandPosition>, val height: Int)

/** Pack unequal-height islands without blank rows; full-width items separate groups. */
internal fun uvirPlaceIslands(
    heights: List<Int>,
    fullWidth: List<Boolean>,
    columns: Int,
    gap: Int
): UvirIslandPlacement {
    require(heights.size == fullWidth.size && columns in 1..2 && gap >= 0)
    require(heights.all { it >= 0 })
    val nextTop = IntArray(columns)
    val positions = heights.mapIndexed { index, height ->
        if (fullWidth[index]) {
            val top = nextTop.max()
            nextTop.fill(top + height + gap)
            UvirIslandPosition(0, top)
        } else {
            val column = nextTop.indices.minBy { nextTop[it] }
            val top = nextTop[column]
            nextTop[column] = top + height + gap
            UvirIslandPosition(column, top)
        }
    }
    return UvirIslandPlacement(positions, if (heights.isEmpty()) 0 else nextTop.max() - gap)
}

/** Only settings/information pages opt in. Reflow never recomposes a second copy of controls. */
@Composable
internal fun UvirIslandColumns(
    modifier: Modifier = Modifier,
    spacing: Dp = UvirIslandSpacing,
    twoColumnsEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Keep the existing ColumnScope API without branching the composition on rotation.
    Column(modifier.fillMaxWidth().uvirIslandFullWidth()) {
        val scope = this
        Layout(content = { content(scope) }, modifier = Modifier.fillMaxWidth()) { children, constraints ->
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
            val columns = uvirIslandColumnCount(twoColumnsEnabled && landscape, width.toDp().value)
            val gap = spacing.roundToPx()
            val cellWidth = ((width - gap * (columns - 1)) / columns).coerceAtLeast(0)
            val fullWidth = children.map { it.parentData === UvirFullWidthIsland }
            val placeables = children.mapIndexed { index, child ->
                val childWidth = if (fullWidth[index]) width else cellWidth
                child.measure(Constraints(minWidth = childWidth, maxWidth = childWidth))
            }
            val placement = uvirPlaceIslands(placeables.map { it.height }, fullWidth, columns, gap)
            layout(width, constraints.constrainHeight(placement.height)) {
                placeables.forEachIndexed { index, child ->
                    val position = placement.positions[index]
                    child.placeRelative(position.column * (cellWidth + gap), position.top)
                }
            }
        }
    }
}
