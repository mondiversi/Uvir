package me.mondiversi.uvir

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal const val UvirMainIslandMoveDurationMillis = 280

/** Stable lazy-list keys retain each island while its neighbours exchange positions. */
@Composable
internal fun LazyItemScope.UvirAnimatedMainIsland(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().animateItem(
        fadeInSpec = null,
        fadeOutSpec = null,
        placementSpec = tween(UvirMainIslandMoveDurationMillis, easing = FastOutSlowInEasing)
    )) { content() }
}
