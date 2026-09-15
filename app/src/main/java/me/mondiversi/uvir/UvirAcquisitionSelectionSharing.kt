package me.mondiversi.uvir

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

internal sealed interface AcquisitionExportItem {
    data class CompleteSession(
        val sessionId: Long,
        val records: List<SavedRecordDetail>
    ) : AcquisitionExportItem

    data class IndividualAcquisition(
        val record: SavedRecordDetail,
        val fromPartialSession: Boolean
    ) : AcquisitionExportItem
}

internal data class AcquisitionExportPlan(
    val selectedRecords: List<SavedRecordDetail>,
    val items: List<AcquisitionExportItem>,
    val partialSessionIds: Set<Long>
) {
    val hasPartialSessions: Boolean
        get() = partialSessionIds.isNotEmpty()
}

internal fun buildAcquisitionExportPlan(
    selectedRecords: List<SavedRecordDetail>,
    allRecords: List<SavedRecordDetail>
): AcquisitionExportPlan {
    val selected =
        selectedRecords
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
    val allIdsBySession =
        allRecords
            .mapNotNull { record ->
                record.sessionId?.let { it to record.id }
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
            .filter { (sessionId, sessionRecords) ->
                val allIds = allIdsBySession[sessionId].orEmpty()
                allIds.isNotEmpty() &&
                    sessionRecords.map { it.id }.toSet() == allIds
            }
            .keys
    val partialSessionIds =
        selectedBySession.keys - completeSessionIds
    val emittedSessions = mutableSetOf<Long>()
    val items = buildList {
        selected.forEach { record ->
            val sessionId = record.sessionId
            if (
                sessionId != null &&
                sessionId in completeSessionIds
            ) {
                if (emittedSessions.add(sessionId)) {
                    add(
                        AcquisitionExportItem.CompleteSession(
                            sessionId = sessionId,
                            records =
                                selectedBySession
                                    .getValue(sessionId)
                                    .sortedBy { it.timestamp }
                        )
                    )
                }
            } else {
                add(
                    AcquisitionExportItem.IndividualAcquisition(
                        record = record,
                        fromPartialSession = sessionId != null
                    )
                )
            }
        }
    }

    return AcquisitionExportPlan(
        selectedRecords = selected,
        items = items,
        partialSessionIds = partialSessionIds
    )
}

private fun readableMeasurementBody(
    context: Context,
    record: SavedRecordDetail,
    numericFormat: UvirNumericFormat
): String =
    readableMeasurementTable(
        context = context,
        records = listOf(record),
        numericFormat = numericFormat
    ).substringAfter("Uvir acquisition log").trim()

internal fun readableAcquisitionExportTable(
    context: Context,
    plan: AcquisitionExportPlan,
    numericFormat: UvirNumericFormat
): String = buildString {
    appendLine("Uvir acquisitions")

    plan.items.forEachIndexed { index, item ->
        appendLine()
        when (item) {
            is AcquisitionExportItem.CompleteSession -> {
                appendLine("SESSION ACQUISITIONS")
                appendLine("Session ID: ${item.sessionId}")
                appendLine("Sensor: ${exportSensorNames(item.records.map { it.sensorDisplayName })}")
                appendLine(
                    "Started: " +
                        csvDateTime(
                            item.records.minOf { it.timestamp },
                            DATA_EXPORT_LANGUAGE
                        )
                )
                appendLine("Session note: ${sessionChartNote(item.records)}")
                appendLine("Total acquisitions: ${item.records.size}")
                appendLine()
                item.records.forEachIndexed { recordIndex, record ->
                    appendLine(
                        readableMeasurementBody(
                            context,
                            record,
                            numericFormat
                        )
                    )
                    if (recordIndex < item.records.lastIndex) {
                        appendLine()
                        appendLine("────────────────────")
                        appendLine()
                    }
                }
            }

            is AcquisitionExportItem.IndividualAcquisition -> {
                appendLine("ACQUISITION")
                appendLine(
                    readableMeasurementBody(
                        context,
                        item.record,
                        numericFormat
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

internal fun shareAcquisitionExportPlan(
    context: Context,
    plan: AcquisitionExportPlan,
    selection: MeasurementDetailShareSelection
) {
    require(plan.selectedRecords.isNotEmpty())
    require(selection.dataFormat != null || selection.includeCharts)

    val exportConfiguration =
        android.content.res.Configuration(
            context.resources.configuration
        ).apply {
            setLocale(Locale.ENGLISH)
            setLayoutDirection(Locale.ENGLISH)
        }
    val exportContext =
        context.createConfigurationContext(exportConfiguration)
    val sharedDirectory =
        File(context.cacheDir, "shared").apply { mkdirs() }
    val sharedBaseName =
        uvirExportBaseName(UvirExportContent.ACQUISITIONS)
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
                    measurementCsv(
                        plan.selectedRecords,
                        UvirNumericFormat.INTERNATIONAL
                    )
                )

        MeasurementShareFormat.READABLE_TABLE ->
            files +=
                writeSharedFile(
                    "txt",
                    readableAcquisitionExportTable(
                        exportContext,
                        plan,
                        UvirNumericFormat.INTERNATIONAL
                    )
                )

        MeasurementShareFormat.BOTH -> {
            files +=
                writeSharedFile(
                    "csv",
                    measurementCsv(
                        plan.selectedRecords,
                        UvirNumericFormat.INTERNATIONAL
                    )
                )
            files +=
                writeSharedFile(
                    "txt",
                    readableAcquisitionExportTable(
                        exportContext,
                        plan,
                        UvirNumericFormat.INTERNATIONAL
                    )
                )
        }

        null -> Unit
    }

    if (selection.includeCharts) {
        plan.items.forEach { item ->
            when (item) {
                is AcquisitionExportItem.CompleteSession ->
                    files +=
                        SessionChartGroup.entries.map { group ->
                            createSessionChartFile(
                                context = context,
                                sessionId = item.sessionId,
                                records = item.records,
                                group = group
                            )
                        }

                is AcquisitionExportItem.IndividualAcquisition ->
                    files +=
                        AcquisitionChartGroup.entries.flatMap { group ->
                            createAcquisitionChartFiles(
                                context = context,
                                record = item.record,
                                group = group
                            )
                        }
            }
        }
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
                exportContext.getString(R.string.share_subject)
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
            context.getString(R.string.share_measurements)
        )

    if (context !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
