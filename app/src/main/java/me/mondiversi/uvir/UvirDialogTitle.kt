package me.mondiversi.uvir

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight

@Composable
internal fun UvirAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
    fixedBottomContent: @Composable (() -> Unit)? = null
) {
    // A Dialog owns another Android Compose view. Restore the host's density
    // AND logical configuration inside that window, before measuring anything.
    val hostDensity = LocalDensity.current
    val hostConfiguration = LocalConfiguration.current
    // Use the full available window width on every device, without platform side margins.
    val windowProperties = DialogProperties(
        dismissOnBackPress = properties.dismissOnBackPress,
        dismissOnClickOutside = properties.dismissOnClickOutside,
        securePolicy = properties.securePolicy,
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = properties.decorFitsSystemWindows,
        windowTitle = properties.windowTitle
    )
    // All popup bodies share one bounded scroll viewport. Titles and actions
    // stay fixed, and the scrollbar is drawn at the window edge only on overflow.
    val scrollbar = rememberUvirDialogScrollbar(textContentColor.copy(alpha = 0.58f))
    val displayedTitle: @Composable () -> Unit = {
        UvirClosableDialogTitleContent(
            onDismiss = onDismissRequest,
            title = title ?: {}
        )
    }
    val displayedText: (@Composable () -> Unit)? = text?.let { body ->
        {
            val scrollableBody: @Composable (Modifier) -> Unit = { viewport ->
                Box(viewport.fillMaxWidth().then(scrollbar.viewportModifier)) {
                    Column(
                        Modifier.fillMaxWidth()
                            .verticalScroll(scrollbar.scrollState)
                            .testTag("uvir_dialog_scroll")
                    ) {
                        body()
                    }
                }
            }
            if (fixedBottomContent == null) {
                scrollableBody(Modifier)
            } else {
                Column(Modifier.fillMaxWidth()) {
                    // Reserve the footer first; only the list consumes the remaining viewport.
                    scrollableBody(Modifier.weight(1f, fill = false))
                    fixedBottomContent()
                }
            }
        }
    }
    Dialog(onDismissRequest = onDismissRequest, properties = windowProperties) {
        CompositionLocalProvider(
            LocalDensity provides hostDensity,
            LocalConfiguration provides hostConfiguration,
            LocalUvirSettingsActionButtons provides false,
            LocalUvirActionGlyphStrokeScale provides 1f
        ) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .then(scrollbar.dialogModifier),
                shape = shape,
                color = containerColor,
                contentColor = textContentColor,
                tonalElevation = tonalElevation
            ) {
                // Keep the original content inset inside the full-width window.
                // Host density scales it on tablets without changing touch targets.
                Column(Modifier.padding(24.dp)) {
                    if (icon != null) {
                        Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)) {
                            CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                                icon()
                            }
                        }
                    }
                    CompositionLocalProvider(
                        LocalContentColor provides titleContentColor,
                        LocalTextStyle provides MaterialTheme.typography.headlineSmall
                    ) {
                        Box(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                            displayedTitle()
                        }
                    }
                    if (displayedText != null) {
                        CompositionLocalProvider(
                            LocalContentColor provides textContentColor,
                            LocalTextStyle provides MaterialTheme.typography.bodyMedium
                        ) {
                            Box(Modifier.weight(1f, fill = false).padding(
                                bottom = if (confirmButton != null || dismissButton != null) 24.dp else 0.dp
                            )) {
                                displayedText()
                            }
                        }
                    }
                    if (confirmButton != null || dismissButton != null) {
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.primary,
                            LocalTextStyle provides MaterialTheme.typography.labelLarge
                        ) {
                            UvirDialogActionFlow(Modifier.align(Alignment.End)) {
                                dismissButton?.invoke()
                                confirmButton?.invoke()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Preserve Material's end-aligned actions and confirm-first order when wrapping. */
@Composable
private fun UvirDialogActionFlow(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val horizontalGap = 8.dp.roundToPx()
        val verticalGap = 12.dp.roundToPx()
        val rows = mutableListOf<List<Placeable>>()
        var row = mutableListOf<Placeable>()
        var rowWidth = 0
        measurables.forEach { measurable ->
            val child = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            val gap = if (row.isEmpty()) 0 else horizontalGap
            if (row.isNotEmpty() && rowWidth + gap + child.width > constraints.maxWidth) {
                rows.add(row)
                row = mutableListOf()
                rowWidth = 0
            }
            rowWidth += (if (row.isEmpty()) 0 else horizontalGap) + child.width
            row.add(child)
        }
        if (row.isNotEmpty()) rows.add(row)
        val widths = rows.map { it.sumOf { child -> child.width } + horizontalGap * (it.size - 1) }
        val heights = rows.map { it.maxOf { child -> child.height } }
        val width = constraints.constrainWidth(widths.maxOrNull() ?: 0)
        val height = constraints.constrainHeight(heights.sum() + verticalGap * (rows.size - 1).coerceAtLeast(0))
        layout(width, height) {
            var y = 0
            rows.indices.reversed().forEach { index ->
                var x = width - widths[index]
                rows[index].forEach { child ->
                    child.placeRelative(x, y + (heights[index] - child.height) / 2)
                    x += child.width + horizontalGap
                }
                y += heights[index] + verticalGap
            }
        }
    }
}

@Composable
internal fun UvirClosableDialogTitle(
    title: String,
    @Suppress("UNUSED_PARAMETER") onDismiss: () -> Unit
) {
    Text(
        text = title,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun UvirClosableDialogTitleContent(
    onDismiss: () -> Unit,
    title: @Composable () -> Unit
) {
    val closeDescription = stringResource(R.string.close)
    val iconColor = LocalContentColor.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f)) {
            title()
        }
        Spacer(Modifier.width(6.dp))
        UvirAccessibleIconButton(
            contentDescription = closeDescription,
            onClick = onDismiss,
            modifier =
                Modifier
                    .size(36.dp)
        ) {
            Canvas(
                Modifier
                    .size(18.dp)
                    .graphicsLayer(alpha = iconColor.alpha)
            ) {
                val iconColor = iconColor.copy(alpha = 1f)
                val stroke = 2.2.dp.toPx()
                drawLine(
                    color = iconColor,
                    start = Offset(size.width * 0.22f, size.height * 0.22f),
                    end = Offset(size.width * 0.78f, size.height * 0.78f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = iconColor,
                    start = Offset(size.width * 0.78f, size.height * 0.22f),
                    end = Offset(size.width * 0.22f, size.height * 0.78f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
