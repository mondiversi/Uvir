package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

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

/** Only the islands reflow: UV above NIR on the left, visible light on the right. */
@Composable
internal fun UvirLiveLandscapeIslands(
    onGroupBounds: (SensorGroup, UvirLiveIslandBounds) -> Unit,
    content: @Composable (SensorGroup) -> Unit
) {
    @Composable fun Island(group: SensorGroup) {
        Box(Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
            val top = coordinates.positionInWindow().y
            onGroupBounds(group, UvirLiveIslandBounds(top, top + coordinates.size.height))
        }) { content(group) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UvirIslandSpacing)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UvirIslandSpacing)) {
            Island(SensorGroup.UV)
            Island(SensorGroup.NIR)
        }
        Column(Modifier.weight(1f)) { Island(SensorGroup.VISIBLE) }
    }
}
