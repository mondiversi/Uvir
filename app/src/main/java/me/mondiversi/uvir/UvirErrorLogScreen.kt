package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Live log with explicit, held confirmation before clearing the bounded file. */
@Composable
internal fun UvirErrorLogScreen(background: Color, card: Color, text: Color, secondary: Color,
    onBack: () -> Unit) {
    val context = LocalContext.current
    val revision by UvirErrorLog.revision.collectAsState()
    var failed by remember { mutableStateOf(false) }
    val log by produceState<String?>(null, revision) {
        try {
            value = withContext(Dispatchers.IO) { UvirErrorLog.read(context) }
            failed = false
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            failed = true; value = ""
        }
    }
    val entries = remember(log) { log.orEmpty().split("────────────────────────────────")
        .map(String::trim).filter(String::isNotBlank).asReversed() }
    val list = rememberLazyListState()
    var exporting by remember { mutableStateOf(false) }
    var confirmingClear by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (confirmingClear) UvirErrorLogClearDialog(card, text, secondary, clearing,
        onDismiss = { confirmingClear = false }, onClear = {
            if (!clearing) {
                clearing = true
                scope.launch {
                    try {
                        val cleared = withContext(Dispatchers.IO) { UvirErrorLog.clear(context) }
                        withContext(Dispatchers.Main.immediate) {
                            if (cleared) confirmingClear = false
                            showUvirBottomMessage(context, context.getString(if (cleared)
                                R.string.error_log_clear_complete else R.string.error_log_clear_failed))
                        }
                    } finally { clearing = false }
                }
            }
        })
    if (exporting) UvirPhoneBackupExportDialog(R.string.export_error_log_dialog_title,
        R.string.export_error_log_dialog_description, UvirExportFileFormat.TXT, card, text, secondary,
        onDismiss = { exporting = false }, onExport = { destination ->
            val file = withContext(Dispatchers.IO) { UvirErrorLog.createExport(context) }
            if (file == null) showUvirBottomMessage(context, context.getString(R.string.debug_error_log_empty))
            else deliverUvirExportFiles(context, listOf(file), destination,
                context.getString(R.string.export_error_log_dialog_title), context.getString(R.string.debug_error_log_subject))
            exporting = false
        })
    UvirFullScreenPage(onDismissRequest = onBack,
        title = { UvirMenuTitle(stringResource(R.string.data_management_error_log_group)) },
        containerColor = background, contentColor = text, lazyListState = list,
        topActionButton = {
            Row {
                UvirTitleActionButton(stringResource(R.string.export_error_log_dialog_title), { exporting = true },
                    enabled = entries.isNotEmpty() && !clearing, modifier = Modifier.testTag("error_log_export")) {
                    UvirTitleActionIcon(MenuIconType.EXPORT, Modifier.size(UvirTitleActionIconSize), LocalContentColor.current)
                }
                UvirTitleActionButton(stringResource(R.string.error_log_clear_title), { confirmingClear = true },
                    enabled = entries.isNotEmpty() && !clearing, iconColor = UvirDestructiveActionColor,
                    modifier = Modifier.testTag("error_log_clear")) {
                    UvirTitleActionIcon(MenuIconType.DELETE, Modifier.size(UvirTitleActionIconSize), LocalContentColor.current)
                }
            }
        }, text = {
            if (log == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (entries.isEmpty()) Text(stringResource(if (failed) R.string.error_log_read_failed
                else R.string.debug_error_log_empty), color = secondary, modifier = Modifier.testTag("error_log_empty"))
            else SelectionContainer {
                LazyColumn(Modifier.fillMaxSize().testTag("error_log_entries"), state = list,
                    verticalArrangement = Arrangement.spacedBy(UvirIslandSpacing)) {
                    items(entries) { entry ->
                        Surface(color = card, shape = MaterialTheme.shapes.medium) {
                            Text(entry, color = text, fontSize = 11.sp, lineHeight = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxWidth().padding(12.dp))
                        }
                    }
                }
            }
        })
}
