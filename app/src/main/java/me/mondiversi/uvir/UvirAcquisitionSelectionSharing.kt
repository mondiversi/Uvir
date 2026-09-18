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

private fun acquisitionExportRecords(
    plan: AcquisitionExportPlan
): List<SavedRecordDetail> =
    plan.items.flatMap { item ->
        when (item) {
            is AcquisitionExportItem.CompleteSession ->
                recordsInVariantExportOrder(item.records)
            is AcquisitionExportItem.IndividualAcquisition ->
                listOf(item.record)
        }
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
    numericFormat: UvirNumericFormat,
    dateFormat: UvirDateFormat,
    timeFormat: UvirTimeFormat,
    irradianceUnit: UvirIrradianceUnit
): String =
    readableMeasurementTable(
        context = context,
        records = listOf(record),
        numericFormat = numericFormat,
        dateFormat = dateFormat,
        timeFormat = timeFormat,
        irradianceUnit = irradianceUnit
    ).substringAfter('\n').trim()

internal fun readableAcquisitionExportTable(
    context: Context,
    plan: AcquisitionExportPlan,
    numericFormat: UvirNumericFormat,
    dateFormat: UvirDateFormat = UvirDateFormat.INTERNATIONAL,
    timeFormat: UvirTimeFormat = UvirTimeFormat.H24,
    irradianceUnit: UvirIrradianceUnit = UvirIrradianceUnit.UW_CM2
): String = buildString {
    val locale = context.resources.configuration.locales[0]
    appendLine("Uvir ${context.getString(R.string.saved_measurements)}")

    plan.items.forEachIndexed { index, item ->
        appendLine()
        when (item) {
            is AcquisitionExportItem.CompleteSession -> {
                appendLine(
                    context.getString(R.string.session_chart_title).uppercase(locale)
                )
                appendLine(
                    "${context.getString(R.string.share_session_id_label)}: ${item.sessionId}"
                )
                appendLine(
                    "${context.getString(R.string.sensor_selector_label)}: " +
                        exportSensorNames(item.records.map { it.sensorDisplayName })
                )
                appendLine(
                    context.getString(
                        R.string.session_chart_start,
                        csvDateTime(
                            item.records.minOf { it.timestamp },
                            dateFormat,
                            context.resources.configuration.locales[0],
                            timeFormat
                        )
                    )
                )
                appendLine(
                    "${context.getString(R.string.session_chart_note)}: " +
                        sessionChartNote(
                            item.records,
                            context.getString(R.string.no_note)
                        )
                )
                appendLine(
                    "${context.getString(R.string.session_chart_total_acquisitions)}: " +
                        item.records.size
                )
                appendLine()
                val variantGroups = acquisitionVariantGroups(item.records)
                val orderedRecords =
                    if (variantGroups.isNotEmpty()) {
                        variantGroups.flatMap { it.records }
                    } else {
                        item.records
                    }
                val variantCount = variantGroups.maxOfOrNull { it.count } ?: 1
                var previousVariant: Int? = null
                orderedRecords.forEachIndexed { recordIndex, record ->
                    if (
                        variantGroups.isNotEmpty() &&
                        record.variantIndex != previousVariant
                    ) {
                        appendLine(
                            context.getString(
                                R.string.session_variant_heading,
                                record.variantIndex ?: 1,
                                variantCount
                            ).uppercase(locale)
                        )
                        appendLine()
                        previousVariant = record.variantIndex
                    }
                    appendLine(
                        readableMeasurementBody(
                            context,
                            record,
                            numericFormat,
                            dateFormat,
                            timeFormat,
                            irradianceUnit
                        )
                    )
                    if (recordIndex < orderedRecords.lastIndex) {
                        appendLine()
                        appendLine("────────────────────")
                        appendLine()
                    }
                }
            }

            is AcquisitionExportItem.IndividualAcquisition -> {
                appendLine(
                    context.getString(R.string.share_acquisition_label).uppercase(locale)
                )
                appendLine(
                    readableMeasurementBody(
                        context,
                        item.record,
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

internal fun shareAcquisitionExportPlan(
    context: Context,
    plan: AcquisitionExportPlan,
    selection: MeasurementDetailShareSelection,
    destination: UvirExportDestination = UvirExportDestination.SHARE
) {
    require(plan.selectedRecords.isNotEmpty())
    require(selection.dataFormat != null || selection.includeCharts)

    val exportFormatting = uvirExportFormatting(context)
    val exportContext = exportFormatting.context
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
                        acquisitionExportRecords(plan),
                        exportFormatting.numericFormat,
                        exportFormatting.dateFormat,
                        exportFormatting.language,
                        exportContext.resources.configuration.locales[0],
                        exportContext,
                        exportFormatting.timeFormat,
                        exportFormatting.irradianceUnit
                    )
                )

        MeasurementShareFormat.READABLE_TABLE ->
            files +=
                writeSharedFile(
                    "txt",
                    readableAcquisitionExportTable(
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
                    measurementCsv(
                        acquisitionExportRecords(plan),
                        exportFormatting.numericFormat,
                        exportFormatting.dateFormat,
                        exportFormatting.language,
                        exportContext.resources.configuration.locales[0],
                        exportContext,
                        exportFormatting.timeFormat,
                        exportFormatting.irradianceUnit
                    )
                )
            files +=
                writeSharedFile(
                    "txt",
                    readableAcquisitionExportTable(
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
                is AcquisitionExportItem.CompleteSession ->
                    files +=
                        createSessionVariantChartFiles(
                            context = context,
                            sessionId = item.sessionId,
                            records = item.records,
                            mode = selection.chartExportMode
                        )

                is AcquisitionExportItem.IndividualAcquisition ->
                    files +=
                        when (selection.chartExportMode) {
                            UvirChartExportMode.COMBINED ->
                                listOf(
                                    createAcquisitionCombinedChartFile(
                                        context = context,
                                        record = item.record
                                    )
                                )
                            UvirChartExportMode.SEPARATE ->
                                createAcquisitionSeparateChartFiles(
                                    context = context,
                                    record = item.record
                                )
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

internal data class AcquisitionExportChartFileBreakdown(
    val completeSessionFiles: Int,
    val individualAcquisitionFiles: Int
) {
    val totalFiles: Int
        get() = completeSessionFiles + individualAcquisitionFiles
}

/**
 * Mirrors the actual list export rules: complete sessions retain their variant
 * grouping, while every record from a partially selected session is exported
 * exactly like an individual acquisition.
 */
internal fun acquisitionExportChartFileBreakdown(
    plan: AcquisitionExportPlan,
    mode: UvirChartExportMode
): AcquisitionExportChartFileBreakdown {
    var completeSessionFiles = 0
    var individualAcquisitionFiles = 0
    plan.items.forEach { item ->
        when (item) {
            is AcquisitionExportItem.CompleteSession ->
                completeSessionFiles +=
                    acquisitionSessionChartFileCount(item.records, mode)
            is AcquisitionExportItem.IndividualAcquisition ->
                individualAcquisitionFiles +=
                    if (mode == UvirChartExportMode.SEPARATE) {
                        acquisitionChartGroupCount(item.record)
                    } else {
                        1
                    }
        }
    }

    return AcquisitionExportChartFileBreakdown(
        completeSessionFiles = completeSessionFiles,
        individualAcquisitionFiles = individualAcquisitionFiles
    )
}

internal fun acquisitionExportChartFileCount(
    plan: AcquisitionExportPlan,
    mode: UvirChartExportMode
): Int = acquisitionExportChartFileBreakdown(plan, mode).totalFiles
