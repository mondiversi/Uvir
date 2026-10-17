package me.mondiversi.uvir

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal val LocalUvirMeasurementPanels = staticCompositionLocalOf { false }
internal const val UvirMeasurementHeaderWeight = 0.5f
internal val UvirMeasurementHeaderGap = 4.dp
internal enum class UvirMeasurementPane { METADATA, SELECTOR, VALUES }

internal data class UvirMeasurementPanelGeometry(
    val leftWidth: Int, val rightWidth: Int, val rightLeft: Int,
    val headerHeight: Int, val bodyTop: Int, val bodyHeight: Int
)

/** Both scroll viewports start below the tallest header and one non-scrolling gap. */
internal fun uvirMeasurementPanelGeometry(
    width: Int, height: Int, titleHeight: Int, selectorHeight: Int,
    columnGap: Int, headerGap: Int
): UvirMeasurementPanelGeometry {
    val gap = columnGap.coerceIn(0, width)
    val availableWidth = width - gap
    val leftWidth = (availableWidth * UvirMeasurementHeaderWeight).roundToInt()
    val headerHeight = maxOf(titleHeight, selectorHeight).coerceIn(0, height)
    val bodyTop = (headerHeight + headerGap.coerceAtLeast(0)).coerceAtMost(height)
    return UvirMeasurementPanelGeometry(leftWidth, availableWidth - leftWidth,
        leftWidth + gap, headerHeight, bodyTop, height - bodyTop)
}

internal fun uvirMeasurementPanelsEnabled(landscape: Boolean, widthDp: Float): Boolean =
    landscape && widthDp >= 480f

internal data class UvirMeasurementScrollAnchor(val index: Int, val offset: Int)

/** Reflow at the top should not immediately hide portrait headers on returning. */
internal fun uvirMeasurementReflowAnchor(
    index: Int, offset: Int, toSplit: Boolean, metadataItems: Int
): UvirMeasurementScrollAnchor = when {
    toSplit && index < metadataItems -> UvirMeasurementScrollAnchor(0, 0)
    toSplit -> UvirMeasurementScrollAnchor(index - metadataItems, offset)
    index == 0 && offset == 0 -> UvirMeasurementScrollAnchor(0, 0)
    else -> UvirMeasurementScrollAnchor(index + metadataItems, offset)
}

@Composable
internal fun uvirLandscapeMeasurementPanels(): Boolean {
    val configuration = LocalConfiguration.current
    return uvirMeasurementPanelsEnabled(
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        configuration.screenWidthDp - UvirScreenHorizontalPadding.value * 2
    )
}

/** The existing sticky selector is the boundary between metadata and measurements.
 * Building the partitioned scopes does not compose copies: each item is emitted only
 * into its owning pane. Values remain lazy, even for large saved sessions.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class UvirMeasurementPaneScope(
    private val target: LazyListScope,
    private val pane: UvirMeasurementPane
) : LazyListScope {
    private var beforeSelector = true
    private var metadataCount = 0

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        if (pane != UvirMeasurementPane.SELECTOR &&
            beforeSelector == (pane == UvirMeasurementPane.METADATA)) target.item(key, contentType, content)
        if (beforeSelector) metadataCount++
    }

    override fun items(
        count: Int,
        key: ((Int) -> Any)?,
        contentType: (Int) -> Any?,
        itemContent: @Composable LazyItemScope.(Int) -> Unit
    ) {
        if (pane != UvirMeasurementPane.SELECTOR &&
            beforeSelector == (pane == UvirMeasurementPane.METADATA)) target.items(count, key, contentType, itemContent)
        if (beforeSelector) metadataCount += count
    }

    override fun stickyHeader(
        key: Any?, contentType: Any?, content: @Composable LazyItemScope.(Int) -> Unit
    ) {
        check(beforeSelector) { "Measurement pages must have exactly one selector boundary" }
        val selectorIndex = metadataCount
        if (pane == UvirMeasurementPane.SELECTOR) target.item(key, contentType) { content(selectorIndex) }
        beforeSelector = false
    }
}

/** Portrait keeps the original scrolling title; landscape puts it above the left pane. */
@Composable
internal fun UvirMeasurementScaffold(
    state: LazyListState,
    containerColor: Color,
    scrollbarColor: Color,
    contentPadding: PaddingValues,
    topBar: @Composable () -> Unit,
    content: LazyListScope.() -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val split = uvirMeasurementPanelsEnabled(
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
            maxWidth.value - UvirScreenHorizontalPadding.value * 2
        )
        CompositionLocalProvider(LocalUvirMeasurementPanels provides split) {
            Scaffold(containerColor = containerColor, topBar = { if (!split) topBar() }) { padding ->
                UvirMeasurementList(
                    state = state,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = contentPadding,
                    scrollbarColor = scrollbarColor,
                    titleBar = if (split) topBar else null,
                    content = content
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UvirMeasurementList(
    state: LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(UvirIslandSpacing),
    scrollbarColor: Color? = null,
    titleBar: (@Composable () -> Unit)? = null,
    metadataItems: Int = 3,
    content: LazyListScope.() -> Unit
) {
    val metadataState = rememberLazyListState()
    val selectorState = rememberLazyListState()
    BoxWithConstraints(modifier) {
        val split = uvirMeasurementPanelsEnabled(
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
            maxWidth.value - UvirScreenHorizontalPadding.value * 2
        )
        var previousSplit by remember { mutableStateOf(split) }
        val reflow = remember(split) {
            if (split == previousSplit) null else uvirMeasurementReflowAnchor(
                state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, split, metadataItems
            )
        }
        SideEffect { previousSplit = split }
        LaunchedEffect(reflow) {
            reflow?.let { state.requestScrollToItem(it.index, it.offset) }
        }
        CompositionLocalProvider(LocalUvirMeasurementPanels provides split) {
            fun Modifier.scrollbar(list: LazyListState): Modifier =
                scrollbarColor?.let { lazyScrollbarOverlay(list, it) } ?: this
            if (!split) {
                LazyColumn(state = state, modifier = Modifier.fillMaxSize().scrollbar(state),
                    contentPadding = contentPadding, verticalArrangement = verticalArrangement,
                    content = content)
            } else {
                val selectorMaxHeight = maxHeight / 2
                Layout(modifier = Modifier.fillMaxSize().padding(horizontal = UvirScreenHorizontalPadding),
                    content = {
                        Box(Modifier.fillMaxWidth()) { titleBar?.invoke() }
                        LazyColumn(state = selectorState, modifier = Modifier.fillMaxWidth()
                            .heightIn(max = selectorMaxHeight).scrollbar(selectorState)) {
                            content(UvirMeasurementPaneScope(this, UvirMeasurementPane.SELECTOR))
                        }
                        LazyColumn(state = metadataState,
                            modifier = Modifier.fillMaxSize().clipToBounds().scrollbar(metadataState)
                                .testTag("measurement-metadata-pane"),
                            verticalArrangement = verticalArrangement) {
                            content(UvirMeasurementPaneScope(this, UvirMeasurementPane.METADATA))
                        }
                        LazyColumn(state = state,
                            modifier = Modifier.fillMaxSize().clipToBounds().scrollbar(state)
                                .testTag("measurement-values-pane"),
                            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                            verticalArrangement = verticalArrangement) {
                            content(UvirMeasurementPaneScope(this, UvirMeasurementPane.VALUES))
                        }
                    }) { measurables, constraints ->
                    val columnGap = UvirIslandSpacing.roundToPx()
                    val headerGap = UvirMeasurementHeaderGap.roundToPx()
                    val columns = uvirMeasurementPanelGeometry(constraints.maxWidth,
                        constraints.maxHeight, 0, 0, columnGap, headerGap)
                    fun headerConstraints(width: Int) = Constraints(minWidth = width,
                        maxWidth = width, maxHeight = constraints.maxHeight / 2)
                    val title = measurables[0].measure(headerConstraints(columns.leftWidth))
                    val selector = measurables[1].measure(headerConstraints(columns.rightWidth))
                    val geometry = uvirMeasurementPanelGeometry(constraints.maxWidth,
                        constraints.maxHeight, title.height, selector.height, columnGap, headerGap)
                    val metadata = measurables[2].measure(Constraints.fixed(geometry.leftWidth, geometry.bodyHeight))
                    val values = measurables[3].measure(Constraints.fixed(geometry.rightWidth, geometry.bodyHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        title.placeRelative(0, (geometry.headerHeight - title.height) / 2)
                        selector.placeRelative(geometry.rightLeft, (geometry.headerHeight - selector.height) / 2)
                        metadata.placeRelative(0, geometry.bodyTop)
                        values.placeRelative(geometry.rightLeft, geometry.bodyTop)
                    }
                }
            }
        }
    }
}

/** Measurement mode, optional variants and data/chart controls always share one row. */
@Composable
internal fun UvirMeasurementControls(
    modifier: Modifier = Modifier,
    gap: androidx.compose.ui.unit.Dp = 6.dp,
    leading: @Composable RowScope.() -> Unit,
    middle: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically) { leading(); middle(); trailing() }
}
