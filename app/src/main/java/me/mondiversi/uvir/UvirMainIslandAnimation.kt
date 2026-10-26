package me.mondiversi.uvir

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round

internal const val UvirMainIslandMoveDurationMillis = 280

/** Height changes already have a reveal animation; only a new order needs placement motion. */
@Composable
internal fun rememberUvirMainIslandReordering(order: List<SensorGroup>): Boolean {
    val generation = remember(order) { Any() }
    var settledGeneration by remember { mutableStateOf(generation) }
    val clock = remember { Animatable(1f) }
    LaunchedEffect(generation) {
        if (generation !== settledGeneration) {
            clock.snapTo(0f)
            clock.animateTo(1f, tween(UvirMainIslandMoveDurationMillis + 32))
            settledGeneration = generation
        }
    }
    return generation !== settledGeneration
}

/** Stable lazy-list keys retain each island while its neighbours exchange positions. */
@Composable
internal fun LazyItemScope.UvirAnimatedMainIsland(animatePlacement: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().uvirMainIslandPlacement(animatePlacement)) { content() }
}

/** Animate a reorder's position only. Height changes and scrolling follow layout directly. */
@Composable
internal fun Modifier.uvirMainIslandPlacement(reordering: Boolean): Modifier {
    var target by remember { mutableStateOf<IntOffset?>(null) }
    // The first placement initializes the animation at its actual position, without an entrance.
    val animated = target?.let {
        animateIntOffsetAsState(it,
            animationSpec = if (reordering)
                tween(UvirMainIslandMoveDurationMillis, easing = FastOutSlowInEasing) else snap(),
            label = "main_island_position")
    }
    return onPlaced { target = it.positionInParent().round() }.graphicsLayer {
        val destination = target
        val current = animated?.value
        val offset = if (reordering && destination != null && current != null) current - destination else IntOffset.Zero
        translationX = offset.x.toFloat()
        translationY = offset.y.toFloat()
    }
}
