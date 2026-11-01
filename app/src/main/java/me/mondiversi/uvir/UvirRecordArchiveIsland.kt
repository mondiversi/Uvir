package me.mondiversi.uvir

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val UvirRecordArchiveMaxBytes = 64 * 1024 * 1024

@Composable
internal fun UvirRecordArchiveIsland(
    database: UvirDatabaseHelper,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val acquisitionRevision by database.acquisitionRevision.collectAsState()
    val alertRevision by database.alertRevision.collectAsState()
    val count by produceState(0, acquisitionRevision, alertRevision) {
        value = withContext(Dispatchers.IO) { UvirArchiveKind.entries.sumOf { kind ->
            database.readableDatabase.rawQuery("SELECT COUNT(*) FROM ${kind.recordsTable}", null).use { it.moveToFirst(); it.getInt(0) }
        } }
    }
    var showExport by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<List<UvirRecordArchive>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    fun reportFailure(operation: String, error: Throwable) {
        UvirErrorLog.record(context, operation, error)
        showUvirBottomMessage(context, context.getString(R.string.record_archive_failed))
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !busy) {
            busy = true
            scope.launch {
                try {
                    pending = withContext(Dispatchers.IO) {
                        require(uris.size <= 50) { "Too many archive files" }
                        var remainingBytes = UvirRecordArchiveMaxBytes
                        uris.flatMap { uri ->
                            val bytes = checkNotNull(context.contentResolver.openInputStream(uri)).use {
                                it.readBytesUpTo(remainingBytes)
                            }
                            remainingBytes -= bytes.size
                            parseCombinedUvirRecordArchive(bytes.toString(Charsets.UTF_8))
                        }.also { require(it.sumOf { archive -> archive.records.size } <= 100_000) { "Too many records" } }
                    }
                } catch (error: Exception) { reportFailure("read_record_archive", error) }
                finally { busy = false }
            }
        }
    }
    SettingsIsland(containerColor = cardColor, contentColor = primaryText) {
        SettingsIslandHeader(stringResource(R.string.record_archive_title), primaryText, ConnectivityIconType.DATA)
        SettingsPageDescription(stringResource(R.string.record_archive_description), secondaryText)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UvirActionButtonGap)) {
                ArchiveActionButton(R.string.export, MenuIconType.EXPORT,
                    enabled = !busy && count > 0, primaryText = primaryText,
                    modifier = Modifier.weight(1f).testTag("archive_export_all")) { showExport = true }
                ArchiveActionButton(R.string.data_management_import_button, MenuIconType.IMPORT,
                    enabled = !busy, primaryText = primaryText,
                    modifier = Modifier.weight(1f).testTag("archive_import_all")) {
                    picker.launch(arrayOf("application/json", "application/octet-stream"))
                }
            }
    }
    if (showExport) {
        UvirRecordArchiveExportDialog(cardColor, primaryText, secondaryText, busy,
            title = stringResource(R.string.record_archive_export_title),
            description = stringResource(R.string.record_archive_export_description),
            onDismiss = { if (!busy) showExport = false },
            onExport = { destination ->
                if (!busy) {
                    busy = true
                    scope.launch { try {
                        val file = withContext(Dispatchers.IO) {
                            val archives = exportCombinedUvirRecordArchive(database)
                            check(archives.any { it.records.isNotEmpty() }) { "No saved records" }
                            File(uvirSharedExportDirectory(context),
                                "uvir_records_${System.nanoTime().toString(36)}.uvirrecords")
                                .apply { writeText(combinedUvirRecordArchiveJson(archives).toString(), Charsets.UTF_8) }
                        }
                        deliverUvirExportFiles(context, listOf(file), destination, context.getString(R.string.record_archive_title))
                        showExport = false
                    } catch (error: Exception) { reportFailure("export_record_archive", error) }
                    finally { busy = false } }
                }
            })
    }
    if (pending.isNotEmpty()) {
        UvirRecordArchiveImportDialog(pending.sumOf { it.records.size }, pending.sumOf { it.sessions.size },
            busy, cardColor, primaryText, secondaryText,
            onDismiss = { if (!busy) pending = emptyList() },
            onImport = {
                if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) { importUvirRecordArchives(database, pending) }
                            pending = emptyList()
                            showUvirBottomMessage(context, context.getString(R.string.record_archive_import_success, result.imported, result.skipped))
                        } catch (error: Exception) { reportFailure("import_record_archive", error) }
                        finally { busy = false }
                    }
                }
            })
    } else if (busy && !showExport) {
        UvirAlertDialog(onDismissRequest = {}, confirmButton = null,
            title = { Text(stringResource(R.string.record_archive_title)) },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
            containerColor = cardColor, titleContentColor = primaryText, textContentColor = secondaryText)
    }
}

@Composable
private fun ArchiveActionButton(label: Int, icon: MenuIconType, enabled: Boolean,
    primaryText: Color, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier, enabled = enabled,
        colors = uvirOutlinedActionColors(primaryText), border = uvirOutlinedActionBorder(enabled, primaryText)) {
        UvirLabeledButtonContent(uvirActionLabel(label)) {
            UvirMenuIcon(icon, Modifier.size(20.dp), LocalContentColor.current)
        }
    }
}

@Composable
private fun UvirRecordArchiveExportDialog(cardColor: Color, primaryText: Color, secondaryText: Color,
    busy: Boolean, title: String, description: String, onDismiss: () -> Unit,
    onExport: (UvirExportDestination) -> Unit) {
    UvirAlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description, color = secondaryText, fontSize = 13.sp, lineHeight = 18.sp)
                UvirExportFileCountText(UvirExportFileFormat.JSON, 1, secondaryText)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(UvirExportDestination.SAVE) }, enabled = !busy) { Text(uvirActionLabel(R.string.save)) }
            TextButton(onClick = { onExport(UvirExportDestination.SHARE) }, enabled = !busy) { Text(uvirActionLabel(R.string.share)) }
        },
        dismissButton = {
            UvirDialogCancelButton(onDismiss, enabled = !busy,
                modifier = Modifier.testTag("archive_export_close"))
        },
        containerColor = cardColor, titleContentColor = primaryText, textContentColor = secondaryText)
}

@Composable
internal fun UvirRecordArchiveImportDialog(records: Int, sessions: Int, busy: Boolean,
    cardColor: Color, primaryText: Color, secondaryText: Color,
    onDismiss: () -> Unit, onImport: () -> Unit) {
    UvirAlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        modifier = Modifier.testTag("record_archive_import_dialog"),
        title = { Text(stringResource(R.string.record_archive_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.record_archive_import_summary, records, sessions), color = primaryText)
                Text(stringResource(R.string.record_archive_import_description), color = secondaryText, fontSize = 13.sp, lineHeight = 18.sp)
                UvirHoldConfirmationInstruction(uvirActionLabel(R.string.data_management_import_button), UvirImportHoldSeconds)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically) {
                UvirHoldToImportButton(label = uvirActionLabel(R.string.data_management_import_button), enabled = !busy,
                    modifier = Modifier.testTag("archive_import_confirm"), onConfirmed = onImport)
                TextButton(onClick = onDismiss, enabled = !busy, colors = uvirCancelTextButtonColors(),
                    modifier = Modifier.testTag("archive_import_cancel")) {
                    Text(uvirActionLabel(R.string.cancel))
                }
            }
        },
        containerColor = cardColor, titleContentColor = primaryText, textContentColor = secondaryText)
}

private fun java.io.InputStream.readBytesUpTo(maximum: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size().toLong() + count <= maximum) { "Archive is too large" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
