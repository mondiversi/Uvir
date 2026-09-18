package me.mondiversi.uvir

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics

internal const val UvirExpansionDurationMillis = 300
private const val UvirDetailSelectorItemIndex = 2

@Composable
internal fun uvirDetailSelectorPinned(listState: LazyListState): Boolean {
    val pinned by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex >= UvirDetailSelectorItemIndex
        }
    }
    return pinned
}

/** Exiting content stays painted briefly, but can no longer receive new actions. */
private fun Modifier.uvirRevealInteraction(visible: Boolean): Modifier =
    if (visible) this else clearAndSetSemantics { }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }

/** The detail title yields its space when the shared selector reaches the top. */
@Composable
internal fun UvirCollapsingDetailTitleBar(
    listState: LazyListState,
    content: @Composable () -> Unit
) {
    val visible by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex < UvirDetailSelectorItemIndex
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.uvirRevealInteraction(visible),
        enter =
            expandVertically(
                tween(UvirExpansionDurationMillis, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top
            ) + slideInVertically(
                tween(UvirExpansionDurationMillis, easing = FastOutSlowInEasing),
                initialOffsetY = { -it }
            ),
        exit =
            shrinkVertically(
                tween(UvirExpansionDurationMillis, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top
            ) + slideOutVertically(
                tween(UvirExpansionDurationMillis, easing = FastOutSlowInEasing),
                targetOffsetY = { -it }
            )
    ) {
        content()
    }
}

/** A stable top anchor prevents charts and legends from moving inside their card. */
@Composable
internal fun ColumnScope.UvirVerticalReveal(
    visible: Boolean,
    onSettled: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val visibilityState = remember { MutableTransitionState(visible) }
    visibilityState.targetState = visible
    val settledCallback by rememberUpdatedState(onSettled)
    LaunchedEffect(visible, visibilityState.isIdle, visibilityState.currentState) {
        if (visibilityState.isIdle && visibilityState.currentState == visible) {
            // Scroll only after the final expanded geometry has been laid out.
            withFrameNanos { }
            settledCallback?.invoke(visible)
        }
    }
    AnimatedVisibility(
        visibleState = visibilityState,
        modifier = Modifier.uvirRevealInteraction(visible),
        enter = expandVertically(tween(UvirExpansionDurationMillis, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Top) + fadeIn(tween(UvirExpansionDurationMillis)),
        exit = shrinkVertically(tween(UvirExpansionDurationMillis, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.Top) + fadeOut(tween(UvirExpansionDurationMillis))
    ) {
        Column(content = content)
    }
}

/** Selection checkboxes make room smoothly in either layout direction. */
@Composable
internal fun RowScope.UvirHorizontalReveal(
    visible: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.uvirRevealInteraction(visible),
        enter = expandHorizontally(tween(UvirExpansionDurationMillis, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Start) + fadeIn(tween(UvirExpansionDurationMillis)),
        exit = shrinkHorizontally(tween(UvirExpansionDurationMillis, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.Start) + fadeOut(tween(UvirExpansionDurationMillis))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }
}
