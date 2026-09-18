package me.mondiversi.uvir

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

internal sealed interface AlertExportItem {
    data class CompleteSession(
        val sessionId: Long,
        val entries: List<ThresholdAlertLogEntry>
    ) : AlertExportItem

    data class IndividualAlert(
        val entry: ThresholdAlertLogEntry,
        val fromPartialSession: Boolean
    ) : AlertExportItem
}

internal data class AlertExportPlan(
    val selectedEntries: List<ThresholdAlertLogEntry>,
    val items: List<AlertExportItem>,
    val partialSessionIds: Set<Long>
) {
    val hasPartialSessions: Boolean
        get() = partialSessionIds.isNotEmpty()
}

internal fun buildAlertExportPlan(
    selectedEntries: List<ThresholdAlertLogEntry>,
    allEntries: List<ThresholdAlertLogEntry>
): AlertExportPlan {
    val selected =
        selectedEntries
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
    val allIdsBySession =
        allEntries
            .mapNotNull { entry ->
                entry.sessionId?.let { it to entry.id }
            }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second }
            )
            .mapValues { (_, ids) -> ids.toSet() }
    val selectedBySession =
        selected
            .filter { it.sessionId != null }
            .groupBy { requireNotNull(it.sessionId) }
    val completeSessionIds =
        selectedBySession
            .filter { (sessionId, sessionEntries) ->
                val allIds = allIdsBySession[sessionId].orEmpty()
                allIds.isNotEmpty() &&
                    sessionEntries.map { it.id }.toSet() == allIds
            }
            .keys
    val partialSessionIds =
        selectedBySession.keys - completeSessionIds
    val emittedSessions = mutableSetOf<Long>()
    val items = buildList {
        selected.forEach { entry ->
            val sessionId = entry.sessionId
            if (
                sessionId != null &&
                sessionId in completeSessionIds
            ) {
                if (emittedSessions.add(sessionId)) {
                    add(
                        AlertExportItem.CompleteSession(
                            sessionId = sessionId,
                            entries =
                                selectedBySession
                                    .getValue(sessionId)
                                    .sortedBy { it.timestamp }
                        )
                    )
                }
            } else {
                add(
                    AlertExportItem.IndividualAlert(
                        entry = entry,
                        fromPartialSession = sessionId != null
                    )
                )
            }
        }
    }

    return AlertExportPlan(
        selectedEntries = selected,
        items = items,
        partialSessionIds = partialSessionIds
    )
}

private fun readableAlertBody(
    context: Context,
    entry: ThresholdAlertLogEntry,
    numericFormat: UvirNumericFormat,
    dateFormat: UvirDateFormat,
    timeFormat: UvirTimeFormat,
    irradianceUnit: UvirIrradianceUnit
): String =
    readableThresholdAlertLog(
        context = context,
        entries = listOf(entry),
        numericFormat = numericFormat,
        dateFormat = dateFormat,
        timeFormat = timeFormat,
        irradianceUnit = irradianceUnit
    ).substringAfter('\n').trim()

internal fun readableAlertExportTable(
    context: Context,
    plan: AlertExportPlan,
    numericFormat: UvirNumericFormat,
    dateFormat: UvirDateFormat = UvirDateFormat.INTERNATIONAL,
    timeFormat: UvirTimeFormat = UvirTimeFormat.H24,
    irradianceUnit: UvirIrradianceUnit = UvirIrradianceUnit.UW_CM2
): String = buildString {
    val locale = context.resources.configuration.locales[0]
    appendLine("Uvir ${context.getString(R.string.threshold_alert_log_title)}")

    plan.items.forEachIndexed { index, item ->
        appendLine()
        when (item) {
            is AlertExportItem.CompleteSession -> {
                appendLine(
                    context.getString(R.string.alert_session_chart_title).uppercase(locale)
                )
                appendLine(
                    "${context.getString(R.string.share_session_id_label)}: ${item.sessionId}"
                )
                appendLine(
                    "${context.getString(R.string.sensor_selector_label)}: " +
                        exportSensorNames(item.entries.map { it.sensorDisplayName })
                )
                appendLine(
                    "${context.getString(R.string.session_chart_note)}: " +
                        item.entries.firstOrNull()?.note.orEmpty().ifBlank {
                            context.getString(R.string.no_note)
                        }
                )
                appendLine(
                    context.getString(
                        R.string.alert_session_chart_start,
                        csvDateTime(
                            item.entries.minOf { it.timestamp },
                            dateFormat,
                            context.resources.configuration.locales[0],
                            timeFormat
                        )
                    )
                )
                appendLine(
                    context.resources.getQuantityString(
                        R.plurals.alert_session_chart_alert_count,
                        item.entries.size,
                        item.entries.size
                    )
                )
                appendLine()
                item.entries.forEachIndexed { entryIndex, entry ->
                    appendLine(
                        readableAlertBody(
                            context,
                            entry,
                            numericFormat,
                            dateFormat,
                            timeFormat,
                            irradianceUnit
                        )
                    )
                    if (entryIndex < item.entries.lastIndex) {
                        appendLine()
                        appendLine("────────────────────")
                        appendLine()
                    }
                }
            }

            is AlertExportItem.IndividualAlert -> {
                appendLine(
                    context.getString(R.string.alert_chart_title).uppercase(locale)
                )
                appendLine(
                    readableAlertBody(
                        context,
                        item.entry,
                        numericFormat,
                        dateFormat,
                        timeFormat,
                        irradianceUnit
                    )
                )
            }
        }

        if (index < plan.items.lastIndex) {
            appendLine()
            appendLine("════════════════════")
        }
    }
}

internal fun shareAlertExportPlan(
    context: Context,
    plan: AlertExportPlan,
    selection: MeasurementDetailShareSelection,
    destination: UvirExportDestination = UvirExportDestination.SHARE
) {
    require(plan.selectedEntries.isNotEmpty())
    require(selection.dataFormat != null || selection.includeCharts)

    val exportFormatting = uvirExportFormatting(context)
    val exportContext = exportFormatting.context
    val sharedDirectory =
        File(context.cacheDir, "shared").apply { mkdirs() }
    val sharedBaseName =
        uvirExportBaseName(UvirExportContent.ALERTS)
    val files = mutableListOf<File>()

    fun writeSharedFile(
        extension: String,
        content: String
    ): File =
        File(sharedDirectory, "$sharedBaseName.$extension").apply {
            writeText(content, Charsets.UTF_8)
        }

    when (selection.dataFormat) {
        MeasurementShareFormat.CSV ->
            files +=
                writeSharedFile(
                    "csv",
                    thresholdAlertLogCsv(
                        exportContext,
                        plan.selectedEntries,
                        exportFormatting.numericFormat,
                        exportFormatting.dateFormat,
                        exportFormatting.timeFormat,
                        exportFormatting.irradianceUnit
                    )
                )

        MeasurementShareFormat.READABLE_TABLE ->
            files +=
                writeSharedFile(
                    "txt",
                    readableAlertExportTable(
                        exportContext,
                        plan,
                        exportFormatting.numericFormat,
                        exportFormatting.dateFormat,
                        exportFormatting.timeFormat,
                        exportFormatting.irradianceUnit
                    )
                )

        MeasurementShareFormat.BOTH -> {
            files +=
                writeSharedFile(
                    "csv",
                    thresholdAlertLogCsv(
                        exportContext,
                        plan.selectedEntries,
                        exportFormatting.numericFormat,
                        exportFormatting.dateFormat,
                        exportFormatting.timeFormat,
                        exportFormatting.irradianceUnit
                    )
                )
            files +=
                writeSharedFile(
                    "txt",
                    readableAlertExportTable(
                        exportContext,
                        plan,
                        exportFormatting.numericFormat,
                        exportFormatting.dateFormat,
                        exportFormatting.timeFormat,
                        exportFormatting.irradianceUnit
                    )
                )
        }

        null -> Unit
    }

    if (selection.includeCharts) {
        plan.items.forEach { item ->
            when (item) {
                is AlertExportItem.CompleteSession ->
                    files +=
                        when (selection.chartExportMode) {
                            UvirChartExportMode.COMBINED ->
                                listOf(
                                    createAlertSessionCombinedChartFile(
                                        context = context,
                                        sessionId = item.sessionId,
                                        entries = item.entries
                                    )
                                )
                            UvirChartExportMode.SEPARATE ->
                                createAlertSessionSeparateChartFiles(
                                    context = context,
                                    sessionId = item.sessionId,
                                    entries = item.entries
                                )
                        }

                is AlertExportItem.IndividualAlert ->
                    files +=
                        when (selection.chartExportMode) {
                            UvirChartExportMode.COMBINED ->
                                listOf(createAlertChartFile(context, item.entry))
                            UvirChartExportMode.SEPARATE ->
                                createAlertSeparateChartFiles(context, item.entry)
                        }
            }
        }
    }

    if (destination == UvirExportDestination.SAVE) {
        requestUvirExportSave(context, files)
        return
    }

    val uris =
        ArrayList(
            files.map { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
        )
    val sendIntent =
        Intent(
            if (files.size == 1) {
                Intent.ACTION_SEND
            } else {
                Intent.ACTION_SEND_MULTIPLE
            }
        ).apply {
            type =
                if (files.size > 1) {
                    "*/*"
                } else {
                    when (files.first().extension.lowercase(Locale.US)) {
                        "csv" -> "text/csv"
                        "txt" -> "text/plain"
                        else -> "image/png"
                    }
                }
            putExtra(
                Intent.EXTRA_SUBJECT,
                exportContext.getString(R.string.threshold_alert_export_subject)
            )
            if (files.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
            clipData =
                ClipData.newUri(
                    context.contentResolver,
                    files.first().name,
                    uris.first()
                ).apply {
                    uris.drop(1).forEach { uri ->
                        addItem(ClipData.Item(uri))
                    }
                }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    val chooser =
        Intent.createChooser(
            sendIntent,
            context.getString(R.string.threshold_alert_log_share)
        )

    if (context !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

internal fun alertExportChartFileCount(
    plan: AlertExportPlan,
    mode: UvirChartExportMode
): Int =
    plan.items.sumOf { item ->
        when (item) {
            is AlertExportItem.CompleteSession ->
                if (mode == UvirChartExportMode.SEPARATE) {
                    alertSessionChartGroupCount(item.entries)
                } else {
                    1
                }
            is AlertExportItem.IndividualAlert ->
                if (mode == UvirChartExportMode.SEPARATE) {
                    alertChartGroupCount(item.entry)
                } else {
                    1
                }
        }
    }
