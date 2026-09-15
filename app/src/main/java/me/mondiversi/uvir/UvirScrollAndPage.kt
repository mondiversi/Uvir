package me.mondiversi.uvir

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal fun Modifier.lazyScrollbarOverlay(
    state: LazyListState,
    color: Color
): Modifier =
    drawWithContent {
        drawContent()

        val layoutInfo =
            state.layoutInfo
        val visibleItems =
            layoutInfo.visibleItemsInfo
        val totalItems =
            layoutInfo.totalItemsCount

        if (
            size.height <= 0f || size.width <= 0f ||
            visibleItems.isEmpty() ||
            (
                !state.canScrollBackward &&
                    !state.canScrollForward
                )
        ) {
            return@drawWithContent
        }

        val averageItemHeight =
            visibleItems
                .sumOf { it.size }
                .toFloat() /
                    visibleItems.size

        val viewportHeight =
            (
                    layoutInfo.viewportEndOffset -
                        layoutInfo.viewportStartOffset
                    ).toFloat()
                .coerceAtLeast(1f)

        val estimatedContentHeight =
            (averageItemHeight * totalItems)
                .coerceAtLeast(viewportHeight)

        val thumbHeight = uvirScrollbarThumbHeight(
            trackHeight = size.height,
            visibleFraction = viewportHeight / estimatedContentHeight,
            minimumThumbHeight = 28.dp.toPx()
        )

        val estimatedScroll =
            state.firstVisibleItemIndex *
                averageItemHeight +
                state.firstVisibleItemScrollOffset

        val maxEstimatedScroll =
            (estimatedContentHeight - viewportHeight)
                .coerceAtLeast(1f)

        val scrollFraction =
            (estimatedScroll / maxEstimatedScroll)
                .coerceIn(0f, 1f)

        val barWidth =
            3.dp.toPx()
        val edgeInset =
            2.dp.toPx()
        val thumbY =
            (size.height - thumbHeight) *
                scrollFraction

        drawRoundRect(
            color = color,
            topLeft =
                Offset(
                    size.width - barWidth - edgeInset,
                    thumbY
                ),
            size =
                Size(
                    barWidth,
                    thumbHeight
                ),
            cornerRadius =
                CornerRadius(
                    barWidth,
                    barWidth
                )
        )
    }
        .pointerInput(state) {
            val touchWidth =
                16.dp.toPx()

            coroutineScope {
                var scrollJob: Job? =
                    null

                awaitEachGesture {
                    val down =
                        awaitFirstDown(
                            requireUnconsumed = false
                        )

                    if (
                        down.position.x <
                        size.width.toFloat() -
                            touchWidth
                    ) {
                        return@awaitEachGesture
                    }

                    down.consume()

                    fun requestScroll(
                        pointerY: Float
                    ) {
                        val totalItems =
                            state.layoutInfo
                                .totalItemsCount

                        if (totalItems <= 0) {
                            return
                        }

                        val scrollFraction =
                            (
                                pointerY /
                                    size.height
                                        .coerceAtLeast(1)
                                        .toFloat()
                                ).coerceIn(0f, 1f)

                        val targetIndex =
                            (
                                scrollFraction *
                                    (totalItems - 1)
                                ).roundToInt()
                                .coerceIn(
                                    0,
                                    totalItems - 1
                                )

                        scrollJob?.cancel()
                        scrollJob =
                            launch {
                                state.scrollToItem(
                                    targetIndex
                                )
                            }
                    }

                    requestScroll(
                        down.position.y
                    )

                    drag(down.id) { change ->
                        requestScroll(
                            change.position.y
                        )
                        change.consume()
                    }
                }
            }
        }

internal fun Modifier.scrollbarOverlay(
    state: ScrollState,
    color: Color,
    trackTopInset: Dp = 0.dp,
    trackBottomInset: Dp = 0.dp
): Modifier =
    drawWithContent {
        drawContent()

        if (state.maxValue <= 0 || size.height <= 0f || size.width <= 0f) {
            return@drawWithContent
        }

        val trackTop = trackTopInset.toPx()
        val trackHeight =
            (size.height - trackTop - trackBottomInset.toPx())
                .coerceAtLeast(0f)
        if (trackHeight <= 0f) return@drawWithContent
        val totalContentHeight =
            trackHeight +
                state.maxValue.toFloat()
        val thumbHeight = uvirScrollbarThumbHeight(
            trackHeight = trackHeight,
            visibleFraction = trackHeight / totalContentHeight,
            minimumThumbHeight = 28.dp.toPx()
        )

        val scrollFraction =
            state.value.toFloat() /
                state.maxValue.toFloat()
        val barWidth =
            3.dp.toPx()
        val edgeInset =
            2.dp.toPx()
        val thumbY =
            trackTop +
                (trackHeight - thumbHeight) *
                    scrollFraction

        drawRoundRect(
            color = color,
            topLeft =
                Offset(
                    size.width - barWidth - edgeInset,
                    thumbY
                ),
            size =
                Size(
                    barWidth,
                    thumbHeight
                ),
            cornerRadius =
                CornerRadius(
                    barWidth,
                    barWidth
                )
        )
    }
        .pointerInput(state, trackTopInset, trackBottomInset) {
            val touchWidth =
                16.dp.toPx()

            coroutineScope {
                var scrollJob: Job? =
                    null
                val trackTop = trackTopInset.toPx()
                awaitEachGesture {
                    val down =
                        awaitFirstDown(
                            requireUnconsumed = false
                        )

                    if (
                        down.position.x <
                        size.width.toFloat() -
                            touchWidth
                    ) {
                        return@awaitEachGesture
                    }

                    down.consume()

                    fun requestScroll(
                        pointerY: Float
                    ) {
                        // The keyboard/animation can resize the track without
                        // restarting this pointer handler.
                        val trackHeight = size.height.toFloat() - trackTop - trackBottomInset.toPx()
                        if (state.maxValue <= 0 || trackHeight <= 0f) {
                            return
                        }

                        val scrollFraction =
                            (
                                (pointerY - trackTop) /
                                    trackHeight
                                ).coerceIn(0f, 1f)

                        val targetValue =
                            (
                                scrollFraction *
                                    state.maxValue
                                ).roundToInt()
                                .coerceIn(
                                    0,
                                    state.maxValue
                                )

                        scrollJob?.cancel()
                        scrollJob =
                            launch {
                                state.scrollTo(
                                    targetValue
                                )
                            }
                    }

                    requestScroll(
                        down.position.y
                    )

                    drag(down.id) { change ->
                        requestScroll(
                            change.position.y
                        )
                        change.consume()
                    }
                }
            }
        }

internal data class UvirDialogScrollbar(
    val scrollState: ScrollState,
    val dialogModifier: Modifier,
    val viewportModifier: Modifier
)

/**
 * Keeps a dialog scrollbar constrained to the scrollable body, excluding the
 * title and action rows. Use the returned viewport modifier on the body Box.
 */
@Composable
internal fun rememberUvirDialogScrollbar(
    color: Color
): UvirDialogScrollbar {
    val scrollState = rememberScrollState()
    // Bound the whole window, not just its body, so larger titles and stacked
    // actions reduce the scroll viewport instead of pushing buttons off screen.
    val maximumDialogHeight = (LocalConfiguration.current.screenHeightDp.dp - 48.dp).coerceAtLeast(1.dp)
    var dialogBounds by remember { mutableStateOf<Rect?>(null) }
    var viewportBounds by remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current
    val trackTopInset =
        with(density) {
            val dialog = dialogBounds
            val viewport = viewportBounds
            if (dialog != null && viewport != null) {
                (viewport.top - dialog.top).coerceAtLeast(0f).toDp()
            } else {
                0.dp
            }
        }
    val trackBottomInset =
        with(density) {
            val dialog = dialogBounds
            val viewport = viewportBounds
            if (dialog != null && viewport != null) {
                (dialog.bottom - viewport.bottom).coerceAtLeast(0f).toDp()
            } else {
                0.dp
            }
        }

    return UvirDialogScrollbar(
        scrollState = scrollState,
        dialogModifier =
            Modifier
                .heightIn(max = maximumDialogHeight)
                .onGloballyPositioned {
                    dialogBounds = it.boundsInWindow()
                }
                .scrollbarOverlay(
                    state = scrollState,
                    color = color,
                    trackTopInset = trackTopInset,
                    trackBottomInset = trackBottomInset
                ),
        viewportModifier =
            Modifier.onGloballyPositioned {
                viewportBounds = it.boundsInWindow()
            }
    )
}

@Composable
fun UvirOutlinedTextFieldColors(): TextFieldColors {

    val darkMode = isSystemInDarkTheme()

    val textColor =
        if (darkMode) Color(0xFFF5F7F8)
        else Color(0xFF101418)

    val labelColor =
        if (darkMode) Color(0xFFD5DEE3)
        else Color(0xFF546E7A)

    val borderColor =
        if (darkMode) Color(0xFF90A4AE)
        else Color(0xFF78909C)

    val accentColor =
        if (darkMode) me.mondiversi.uvir.ui.theme.Purple80
        else Color(0xFF6650A4)

    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        disabledTextColor =
            labelColor.copy(alpha = 0.65f),
        cursorColor = accentColor,
        focusedBorderColor = accentColor,
        unfocusedBorderColor = borderColor,
        disabledBorderColor =
            borderColor.copy(alpha = 0.55f),
        focusedLabelColor = textColor,
        unfocusedLabelColor = labelColor,
        disabledLabelColor =
            labelColor.copy(alpha = 0.65f),
        focusedPlaceholderColor = labelColor,
        unfocusedPlaceholderColor = labelColor,
        disabledPlaceholderColor =
            labelColor.copy(alpha = 0.65f),
        focusedSupportingTextColor = labelColor,
        unfocusedSupportingTextColor = labelColor,
        disabledSupportingTextColor =
            labelColor.copy(alpha = 0.65f)
    )
}

@Composable
fun UvirFullScreenPage(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    containerColor: Color,
    contentColor: Color,
    scrollState: ScrollState? = null,
    scrollbarColor: Color = Color.Unspecified,
    contentOverlay:
        @Composable BoxScope.() -> Unit = {},
    actionButton: (@Composable () -> Unit)? = null,
    topActionButton: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null
) {

    BackHandler {
        onDismissRequest()
    }

    Scaffold(
        containerColor =
            containerColor,
        contentColor =
            contentColor,

        floatingActionButton = {
            floatingActionButton?.invoke()
        },

        topBar = {

            Surface(
                color =
                    containerColor,
                contentColor =
                    contentColor
            ) {

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                if (topActionButton != null) {
                                    UvirTitleBarContentPadding
                                } else {
                                    PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                }
                            ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    UvirBackButton(
                        onClick =
                            onDismissRequest
                    )

                    Box(
                        modifier =
                            Modifier.weight(1f),
                        contentAlignment =
                            Alignment.CenterStart
                    ) {
                        title()
                    }

                    topActionButton?.invoke()
                }
            }
        }
    ) { paddingValues ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(
                        top = 2.dp,
                        bottom = 20.dp
                    )
        ) {

            Box(
                modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(
                                if (scrollState != null) {
                                    Modifier.scrollbarOverlay(
                                        state = scrollState,
                                        color =
                                            if (
                                                scrollbarColor !=
                                                Color.Unspecified
                                            ) {
                                                scrollbarColor
                                            } else {
                                                contentColor.copy(
                                                    alpha = 0.46f
                                                )
                                            }
                                    )
                                } else {
                                    Modifier
                                }
                            )
            ) {

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                horizontal = 20.dp
                            )
                ) {
                    text()
                }

                contentOverlay()
            }

            if (actionButton != null) {

                Spacer(
                    Modifier.height(12.dp)
                )

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 20.dp
                            )
                ) {
                    actionButton()
                }
            }
        }
    }
}
