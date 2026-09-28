package me.mondiversi.uvir

import android.content.Context
import java.io.File

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
    val partialSessionIds: Set<Long>,
    val variantCountsBySession: Map<Long, Int> = emptyMap()
) {
    val hasPartialSessions: Boolean
        get() = partialSessionIds.isNotEmpty()

    val hasCompleteVariantSessions: Boolean
        get() =
            items.any { item ->
                item is AcquisitionExportItem.CompleteSession &&
                    acquisitionVariantGroups(item.records).isNotEmpty()
            }
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
    val variantCountsBySession =
        allRecords
            .mapNotNull { record ->
                val sessionId = record.sessionId
                val variantIndex = record.variantIndex
                if (sessionId != null && variantIndex != null) {
                    sessionId to variantIndex
                } else {
                    null
                }
            }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second }
            )
            .mapValues { (_, variants) -> variants.maxOrNull() ?: 1 }
            .filterValues { it > 1 }
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
        partialSessionIds = partialSessionIds,
        variantCountsBySession = variantCountsBySession
    )
}

private fun readableMeasurementBody(
    context: Context,
    record: SavedRecordDetail,
    numericFormat: UvirNumericFormat,
    dateFormat: UvirDateFormat,
    timeFormat: UvirTimeFormat,
    irradianceUnit: UvirIrradianceUnit,
    variantCount: Int? = null,
    includeVariant: Boolean = true
): String =
    readableMeasurementTable(
        context = context,
        records = listOf(record),
        numericFormat = numericFormat,
        dateFormat = dateFormat,
        timeFormat = timeFormat,
        irradianceUnit = irradianceUnit,
        variantCountOverride = variantCount,
        includeRecordVariant = includeVariant
    ).substringAfter('\n').trim()

internal fun readableAcquisitionExportTable(
    context: Context,
    plan: AcquisitionExportPlan,
    numericFormat: UvirNumericFormat,
    dateFormat: UvirDateFormat = UvirDateFormat.INTERNATIONAL,
    timeFormat: UvirTimeFormat = UvirTimeFormat.H24,
    irradianceUnit: UvirIrradianceUnit = UvirIrradianceUnit.UW_CM2,
    grouping: UvirReadableTableGrouping = UvirReadableTableGrouping.BY_ACQUISITION
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
                if (grouping == UvirReadableTableGrouping.BY_SPECTRAL_AREA) {
                    append(
                        readableMeasurementSpectralAreaBody(
                            context = context,
                            records = item.records,
                            numericFormat = numericFormat,
                            dateFormat = dateFormat,
                            timeFormat = timeFormat,
                            irradianceUnit = irradianceUnit
                        )
                    )
                } else {
                    val variantGroups = acquisitionVariantGroups(item.records)
                    val orderedRecords =
                        if (variantGroups.isNotEmpty()) {
                            variantGroups.flatMap { it.records }
                        } else {
                            item.records
                        }
                    val variantCount =
                        plan.variantCountsBySession[item.sessionId]
                            ?: variantGroups.maxOfOrNull { it.count }
                            ?: 1
                    var previousVariant: Int? = null
                    orderedRecords.forEachIndexed { recordIndex, record ->
                        if (
                            variantCount > 1 &&
                            record.variantIndex != previousVariant
                        ) {
                            appendLine(
                                uvirVariantHeading(
                                    context.resources,
                                    record.variantIndex ?: 1
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
                                irradianceUnit,
                                variantCount = variantCount,
                                includeVariant = false
                            )
                        )
                        if (recordIndex < orderedRecords.lastIndex) {
                            appendLine()
                            appendLine("────────────────────")
                            appendLine()
                        }
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
                        irradianceUnit,
                        variantCount =
                            item.record.sessionId?.let {
                                plan.variantCountsBySession[it]
                            }
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

    fun writeReadableExportFiles(): List<File> =
        plan.items.flatMap { item ->
            when (item) {
                is AcquisitionExportItem.CompleteSession -> {
                    val variantGroups = acquisitionVariantGroups(item.records)
                    val parts =
                        if (variantGroups.isEmpty()) {
                            listOf(null to item.records)
                        } else {
                            variantGroups.map { it to it.records }
                        }
                    parts.map { (variant, records) ->
                        val partPlan =
                            AcquisitionExportPlan(
                                selectedRecords = records,
                                items =
                                    listOf(
                                        AcquisitionExportItem.CompleteSession(
                                            sessionId = item.sessionId,
                                            records = records
                                        )
                                    ),
                                partialSessionIds = emptySet(),
                                variantCountsBySession =
                                    mapOf(item.sessionId to (variant?.count ?: 1))
                            )
                        val variantSuffix =
                            variant?.let {
                                "_${uvirVariantFileToken(it.index)}"
                            }.orEmpty()
                        File(
                            sharedDirectory,
                            "${sharedBaseName}_Session_ID${item.sessionId}$variantSuffix.txt"
                        ).apply {
                            writeText(
                                readableAcquisitionExportTable(
                                    exportContext,
                                    partPlan,
                                    exportFormatting.numericFormat,
                                    exportFormatting.dateFormat,
                                    exportFormatting.timeFormat,
                                    exportFormatting.irradianceUnit,
                                    grouping = selection.readableTableGrouping
                                ),
                                Charsets.UTF_8
                            )
                        }
                    }
                }

                is AcquisitionExportItem.IndividualAcquisition -> {
                    val partPlan =
                        AcquisitionExportPlan(
                            selectedRecords = listOf(item.record),
                            items = listOf(item),
                            partialSessionIds = emptySet(),
                            variantCountsBySession = plan.variantCountsBySession
                        )
                    listOf(
                        File(
                            sharedDirectory,
                            "${sharedBaseName}_Acquisition_ID${item.record.id}.txt"
                        ).apply {
                            writeText(
                                readableAcquisitionExportTable(
                                    exportContext,
                                    partPlan,
                                    exportFormatting.numericFormat,
                                    exportFormatting.dateFormat,
                                    exportFormatting.timeFormat,
                                    exportFormatting.irradianceUnit,
                                    grouping = UvirReadableTableGrouping.BY_ACQUISITION
                                ),
                                Charsets.UTF_8
                            )
                        }
                    )
                }
            }
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
                        exportFormatting.irradianceUnit,
                        variantCountsBySession = plan.variantCountsBySession
                    )
                )

        MeasurementShareFormat.READABLE_TABLE ->
            files += writeReadableExportFiles()

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
                        exportFormatting.irradianceUnit,
                        variantCountsBySession = plan.variantCountsBySession
                    )
                )
            files += writeReadableExportFiles()
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
                            mode = selection.chartExportMode,
                            variantGrouping = selection.variantChartGrouping
                        )

                is AcquisitionExportItem.IndividualAcquisition ->
                    files +=
                        when (selection.chartExportMode) {
                            UvirChartExportMode.COMBINED ->
                                listOf(
                                    createAcquisitionCombinedChartFile(
                                        context = context,
                                        record = item.record,
                                        variantCount =
                                            item.record.sessionId?.let {
                                                plan.variantCountsBySession[it]
                                            }
                                    )
                                )
                            UvirChartExportMode.SEPARATE ->
                                createAcquisitionSeparateChartFiles(
                                    context = context,
                                    record = item.record,
                                    variantCount =
                                        item.record.sessionId?.let {
                                            plan.variantCountsBySession[it]
                                        }
                                )
                        }
            }
        }
    }

    deliverUvirExportFiles(
        context = context,
        files = files,
        destination = destination,
        chooserTitle = context.getString(R.string.share_measurements),
        subject = exportContext.getString(R.string.share_subject)
    )
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
    mode: UvirChartExportMode,
    variantGrouping: UvirVariantChartGrouping =
        UvirVariantChartGrouping.BY_VARIANT
): AcquisitionExportChartFileBreakdown {
    var completeSessionFiles = 0
    var individualAcquisitionFiles = 0
    plan.items.forEach { item ->
        when (item) {
            is AcquisitionExportItem.CompleteSession ->
                completeSessionFiles +=
                    if (
                        mode == UvirChartExportMode.COMBINED &&
                        variantGrouping == UvirVariantChartGrouping.BY_GROUP
                    ) {
                        acquisitionSessionGroupedVariantChartFileCount(
                            item.records
                        )
                    } else {
                        acquisitionSessionChartFileCount(item.records, mode)
                    }
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
    mode: UvirChartExportMode,
    variantGrouping: UvirVariantChartGrouping =
        UvirVariantChartGrouping.BY_VARIANT
): Int =
    acquisitionExportChartFileBreakdown(
        plan,
        mode,
        variantGrouping
    ).totalFiles

internal fun acquisitionSessionReadableTableFileCount(
    records: List<SavedRecordDetail>
): Int = acquisitionVariantGroups(records).size.coerceAtLeast(1)

internal fun acquisitionExportReadableTableFileCount(
    plan: AcquisitionExportPlan
): Int =
    plan.items.sumOf { item ->
        when (item) {
            is AcquisitionExportItem.CompleteSession ->
                acquisitionSessionReadableTableFileCount(item.records)
            is AcquisitionExportItem.IndividualAcquisition -> 1
        }
    }
