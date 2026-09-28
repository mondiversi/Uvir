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
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
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
    CompositionLocalProvider(
        LocalUvirSettingsActionButtons provides false,
        LocalUvirActionGlyphStrokeScale provides 1f
    ) {
        if (confirmButton == null && dismissButton == null) {
            // No action row: keep Material's normal 24dp inset, without its
            // additional 24dp gap between text and confirmation buttons.
            BasicAlertDialog(
                onDismissRequest = onDismissRequest,
                modifier = modifier.then(scrollbar.dialogModifier),
                properties = properties
            ) {
                Surface(
                    shape = shape,
                    color = containerColor,
                    contentColor = textContentColor,
                    tonalElevation = tonalElevation
                ) {
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
                                Box(Modifier.weight(1f, fill = false)) {
                                    displayedText()
                                }
                            }
                        }
                    }
                }
            }
        } else {
            MaterialAlertDialog(
                onDismissRequest = onDismissRequest,
                confirmButton = confirmButton ?: {},
                modifier = modifier.then(scrollbar.dialogModifier),
                dismissButton = dismissButton,
                icon = icon,
                title = displayedTitle,
                text = displayedText,
                shape = shape,
                containerColor = containerColor,
                iconContentColor = iconContentColor,
                titleContentColor = titleContentColor,
                textContentColor = textContentColor,
                tonalElevation = tonalElevation,
                properties = properties
            )
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
