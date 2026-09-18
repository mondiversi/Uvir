package me.mondiversi.uvir

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MeasurementShareFormat {
    CSV,
    READABLE_TABLE,
    BOTH
}

internal const val DATA_EXPORT_LANGUAGE = "en"

internal fun csvCell(value: String): String =
    if (
        value.contains(';') ||
        value.contains('"') ||
        value.contains('\n') ||
        value.contains('\r')
    ) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }

internal fun csvNumber(
    value: Double,
    numericFormat: UvirNumericFormat
): String =
    formatUvirExportNumber(
        value,
        numericFormat
    )

internal val MEASUREMENT_EXPORT_COLUMNS_IT =
    listOf(
        "ID_acquisizione",
        "ID_sessione",
        "Data/Ora",
        "Timestamp_ms",
        "Tipo_acquisizione",
        "Nota",
        "Progressivo_sessione",
        "Posizione",
        "Variante",
        "UVC_100_280_nm_uW_cm2",
        "UVC_percentuale",
        "UVB_280_315_nm_uW_cm2",
        "UVB_percentuale",
        "UVA_315_400_nm_uW_cm2",
        "UVA_percentuale",
        "Totale_ultravioletti_uW_cm2",
        "HEV_400_500_nm_uW_cm2",
        "HEV_percentuale",
        "Violetto_400_450_nm_uW_cm2",
        "Violetto_percentuale",
        "Blu_450_495_nm_uW_cm2",
        "Blu_percentuale",
        "Verde_495_570_nm_uW_cm2",
        "Verde_percentuale",
        "Giallo_570_590_nm_uW_cm2",
        "Giallo_percentuale",
        "Arancione_590_620_nm_uW_cm2",
        "Arancione_percentuale",
        "Rosso_620_700_nm_uW_cm2",
        "Rosso_percentuale",
        "Totale_luce_visibile_uW_cm2",
        "FarRed_picco_745_nm_uW_cm2",
        "FarRed_percentuale",
        "NIR_picco_855_nm_uW_cm2",
        "NIR_percentuale",
        "Totale_infrarosso_uW_cm2",
        "Modello_biologico",
        "Irradianza_pesata_stimata_UV_effetto_DNA_uW_cm2_eq",
        "Indice_spettrale_UV_effetto_DNA_0_100",
        "Irradianza_pesata_stimata_fotoinvecchiamento_UVA_uW_cm2_eq",
        "Indice_spettrale_fotoinvecchiamento_UVA_0_100",
        "Irradianza_pesata_stimata_stress_ossidativo_HEV_uW_cm2_eq",
        "Indice_spettrale_stress_ossidativo_HEV_0_100",
        "Nome_sensore"
    )

internal val MEASUREMENT_EXPORT_COLUMNS_EN =
    listOf(
        "Acquisition_ID",
        "Session_ID",
        "Date/Time",
        "Timestamp_ms",
        "Acquisition_type",
        "Note",
        "Session_sequence",
        "Position_index",
        "Variant_index",
        "UVC_100_280_nm_uW_cm2",
        "UVC_percent",
        "UVB_280_315_nm_uW_cm2",
        "UVB_percent",
        "UVA_315_400_nm_uW_cm2",
        "UVA_percent",
        "Ultraviolet_total_uW_cm2",
        "HEV_400_500_nm_uW_cm2",
        "HEV_percent",
        "Violet_400_450_nm_uW_cm2",
        "Violet_percent",
        "Blue_450_495_nm_uW_cm2",
        "Blue_percent",
        "Green_495_570_nm_uW_cm2",
        "Green_percent",
        "Yellow_570_590_nm_uW_cm2",
        "Yellow_percent",
        "Orange_590_620_nm_uW_cm2",
        "Orange_percent",
        "Red_620_700_nm_uW_cm2",
        "Red_percent",
        "Visible_total_uW_cm2",
        "FarRed_peak_745_nm_uW_cm2",
        "FarRed_percent",
        "NIR_peak_855_nm_uW_cm2",
        "NIR_percent",
        "Infrared_total_uW_cm2",
        "Biological_model",
        "Estimated_weighted_irradiance_UV_DNA_effect_uW_cm2_eq",
        "Spectral_index_UV_DNA_effect_0_100",
        "Estimated_weighted_irradiance_UVA_photoaging_uW_cm2_eq",
        "Spectral_index_UVA_photoaging_0_100",
        "Estimated_weighted_irradiance_HEV_oxidative_stress_uW_cm2_eq",
        "Spectral_index_HEV_oxidative_stress_0_100",
        "Sensor_name"
    )

internal fun measurementExportColumns(
    language: String,
    irradianceUnit: UvirIrradianceUnit = UvirIrradianceUnit.UW_CM2
): List<String> =
    (if (language == "it") {
        MEASUREMENT_EXPORT_COLUMNS_IT
    } else {
        MEASUREMENT_EXPORT_COLUMNS_EN
    }).map { column ->
        column.replace("uW_cm2", irradianceUnit.csvSymbol)
    } + if (language == "it") {
        "Valore_fuori_scala"
    } else {
        "Out_of_range"
    }

private fun csvHeaderPart(value: String): String =
    value.trim()
        .replace(Regex("[\\s/]+"), "_")
        .replace(Regex("[^\\p{L}\\p{N}_-]"), "")

internal fun measurementExportColumns(
    context: Context,
    language: String,
    irradianceUnit: UvirIrradianceUnit = UvirIrradianceUnit.UW_CM2
): List<String> {
    if (language == DATA_EXPORT_LANGUAGE || language == "it") {
        return measurementExportColumns(language, irradianceUnit)
    }

    fun label(resource: Int): String = csvHeaderPart(context.getString(resource))

    val spectralIndex = label(R.string.relative_spectral_index)
    val estimatedWeighted = label(R.string.estimated_weighted_signal)
    return listOf(
        label(R.string.share_measurement_id_label),
        label(R.string.share_session_id_label),
        label(R.string.share_date_label),
        "Timestamp_ms",
        label(R.string.acquisition_mode_label),
        label(R.string.share_note_label),
        "${label(R.string.share_session_id_label)}_sequence",
        label(R.string.acquisition_position_label),
        label(R.string.session_sequence_filter_position),
        "UVC_100_280_nm_${irradianceUnit.csvSymbol}",
        "UVC_%",
        "UVB_280_315_nm_${irradianceUnit.csvSymbol}",
        "UVB_%",
        "UVA_315_400_nm_${irradianceUnit.csvSymbol}",
        "UVA_%",
        "${label(R.string.export_total_label)}_${label(R.string.uv_radiation)}_${irradianceUnit.csvSymbol}",
        "HEV_400_500_nm_${irradianceUnit.csvSymbol}",
        "HEV_%",
        "${label(R.string.violet)}_400_450_nm_${irradianceUnit.csvSymbol}",
        "${label(R.string.violet)}_%",
        "${label(R.string.blue)}_450_495_nm_${irradianceUnit.csvSymbol}",
        "${label(R.string.blue)}_%",
        "${label(R.string.green)}_495_570_nm_${irradianceUnit.csvSymbol}",
        "${label(R.string.green)}_%",
        "${label(R.string.yellow)}_570_590_nm_${irradianceUnit.csvSymbol}",
        "${label(R.string.yellow)}_%",
        "${label(R.string.orange)}_590_620_nm_${irradianceUnit.csvSymbol}",
        "${label(R.string.orange)}_%",
        "${label(R.string.red)}_620_700_nm_${irradianceUnit.csvSymbol}",
        "${label(R.string.red)}_%",
        "${label(R.string.export_total_label)}_${label(R.string.visible_light)}_${irradianceUnit.csvSymbol}",
        "${label(R.string.session_chart_series_far_red)}_peak_745_nm_${irradianceUnit.csvSymbol}",
        "${label(R.string.session_chart_series_far_red)}_%",
        "NIR_peak_855_nm_${irradianceUnit.csvSymbol}",
        "NIR_%",
        "${label(R.string.export_total_label)}_${label(R.string.far_red_nir)}_${irradianceUnit.csvSymbol}",
        "${label(R.string.biological_effects_group_name)}_model",
        "${estimatedWeighted}_${label(R.string.dna_uv_proxy)}_${irradianceUnit.csvSymbol}_eq",
        "${spectralIndex}_${label(R.string.dna_uv_proxy)}_0_100",
        "${estimatedWeighted}_${label(R.string.uva_photoaging_proxy)}_${irradianceUnit.csvSymbol}_eq",
        "${spectralIndex}_${label(R.string.uva_photoaging_proxy)}_0_100",
        "${estimatedWeighted}_${label(R.string.hev_oxidative_proxy)}_${irradianceUnit.csvSymbol}_eq",
        "${spectralIndex}_${label(R.string.hev_oxidative_proxy)}_0_100",
        label(R.string.sensor_name_label),
        label(R.string.out_of_range_csv_header)
    )
}

internal fun csvDateTime(
    timestamp: Long,
    dateFormat: UvirDateFormat = UvirDateFormat.INTERNATIONAL,
    locale: Locale = Locale.US,
    timeFormat: UvirTimeFormat = UvirTimeFormat.H24
): String =
    formatUvirDateTime(
        timestamp = timestamp,
        format = dateFormat,
        locale = locale,
        separator = " ",
        timeFormat = timeFormat
    )

internal fun measurementCsv(
    records: List<SavedRecordDetail>,
    numericFormat: UvirNumericFormat,
    dateFormat: UvirDateFormat = UvirDateFormat.INTERNATIONAL,
    language: String = DATA_EXPORT_LANGUAGE,
    locale: Locale = Locale.US,
    labelContext: Context? = null,
    timeFormat: UvirTimeFormat = UvirTimeFormat.H24,
    irradianceUnit: UvirIrradianceUnit = UvirIrradianceUnit.UW_CM2
): String = buildString {
    val columns =
        labelContext?.let {
            measurementExportColumns(it, language, irradianceUnit)
        } ?: measurementExportColumns(language, irradianceUnit)
    appendLine(
        columns.joinToString(";") {
            csvCell(it)
        }
    )

    records.forEach { record ->
        val sample = record.sample
        val effects = biologicalEffects(sample)
        val uvTotal =
            sample.uvc +
                sample.uvb +
                sample.uva
        val hev =
            sample.violetto +
                sample.blu
        val visibleTotal =
            sample.violetto +
                sample.blu +
                sample.verde +
                sample.giallo +
                sample.arancione +
                sample.rosso
        val farRedNirTotal =
            sample.f8 +
                sample.nir
        fun irradiance(value: Double, group: SensorGroup): String =
            if (sample.isOutOfRange(group)) {
                ""
            } else {
                formatUvirIrradianceExportNumber(value, numericFormat, irradianceUnit)
            }
        fun relativePercent(
            value: Double,
            total: Double,
            group: SensorGroup
        ): String =
            if (sample.isOutOfRange(group)) {
                ""
            } else {
                formatUvirNumber(
                    percentage(value, total).toDouble() * 100.0,
                    1,
                    numericFormat
                )
            }

        appendLine(
            listOf(
                record.id.toString(),
                record.sessionId
                    ?.toString()
                    .orEmpty(),
                csvDateTime(
                    record.timestamp,
                    dateFormat,
                    locale,
                    timeFormat
                ),
                record.timestamp.toString(),
                if (record.externalCommand) {
                    labelContext?.getString(R.string.external_measurement)
                        ?: if (language == "it") "Esterna" else "External"
                } else if (record.automatic) {
                    labelContext?.getString(R.string.share_automatic)
                        ?: if (language == "it") "Automatica" else "Automatic"
                } else {
                    labelContext?.getString(R.string.share_manual)
                        ?: if (language == "it") "Manuale" else "Manual"
                },
                record.note.ifBlank {
                    labelContext?.getString(R.string.no_note)
                        ?: if (language == "it") "Nessuna nota" else "No note"
                },
                record.sessionSequence
                    ?.toString()
                    .orEmpty(),
                record.positionIndex
                    ?.toString()
                    .orEmpty(),
                record.variantIndex
                    ?.toString()
                    .orEmpty(),
                irradiance(sample.uvc, SensorGroup.UV),
                relativePercent(sample.uvc, uvTotal, SensorGroup.UV),
                irradiance(sample.uvb, SensorGroup.UV),
                relativePercent(sample.uvb, uvTotal, SensorGroup.UV),
                irradiance(sample.uva, SensorGroup.UV),
                relativePercent(sample.uva, uvTotal, SensorGroup.UV),
                irradiance(uvTotal, SensorGroup.UV),
                irradiance(hev, SensorGroup.VISIBLE),
                relativePercent(hev, visibleTotal, SensorGroup.VISIBLE),
                irradiance(sample.violetto, SensorGroup.VISIBLE),
                relativePercent(sample.violetto, visibleTotal, SensorGroup.VISIBLE),
                irradiance(sample.blu, SensorGroup.VISIBLE),
                relativePercent(sample.blu, visibleTotal, SensorGroup.VISIBLE),
                irradiance(sample.verde, SensorGroup.VISIBLE),
                relativePercent(sample.verde, visibleTotal, SensorGroup.VISIBLE),
                irradiance(sample.giallo, SensorGroup.VISIBLE),
                relativePercent(sample.giallo, visibleTotal, SensorGroup.VISIBLE),
                irradiance(sample.arancione, SensorGroup.VISIBLE),
                relativePercent(sample.arancione, visibleTotal, SensorGroup.VISIBLE),
                irradiance(sample.rosso, SensorGroup.VISIBLE),
                relativePercent(sample.rosso, visibleTotal, SensorGroup.VISIBLE),
                irradiance(visibleTotal, SensorGroup.VISIBLE),
                irradiance(sample.f8, SensorGroup.NIR),
                relativePercent(sample.f8, farRedNirTotal, SensorGroup.NIR),
                irradiance(sample.nir, SensorGroup.NIR),
                relativePercent(sample.nir, farRedNirTotal, SensorGroup.NIR),
                irradiance(farRedNirTotal, SensorGroup.NIR),
                BIOLOGICAL_MODEL_VERSION,
                irradiance(effects.dnaUvProxy, SensorGroup.UV),
                csvNumber(effects.dnaUvScore.toDouble() * 100.0, numericFormat),
                irradiance(effects.uvaPhotoagingProxy, SensorGroup.UV),
                csvNumber(effects.uvaPhotoagingScore.toDouble() * 100.0, numericFormat),
                irradiance(effects.hevOxidativeProxy, SensorGroup.VISIBLE),
                csvNumber(effects.hevOxidativeScore.toDouble() * 100.0, numericFormat),
                exportSensorName(record.sensorDisplayName),
                if (sample.hasAnyOutOfRangeValue()) "1" else "0"
            ).joinToString(";") {
                csvCell(it)
            }
        )
    }
}

internal fun readableMeasurementTable(
    context: Context,
    records: List<SavedRecordDetail>,
    numericFormat: UvirNumericFormat,
    dateFormat: UvirDateFormat = UvirDateFormat.INTERNATIONAL,
    timeFormat: UvirTimeFormat = UvirTimeFormat.H24,
    irradianceUnit: UvirIrradianceUnit = UvirIrradianceUnit.UW_CM2,
    groupByVariant: Boolean = false
): String = buildString {
    appendLine("Uvir ${context.getString(R.string.saved_measurements)}")
    appendLine()

    val variantGroups =
        if (groupByVariant) acquisitionVariantGroups(records) else emptyList()
    val orderedRecords =
        if (variantGroups.isNotEmpty()) {
            variantGroups.flatMap { it.records }
        } else {
            records
        }
    val variantCount = variantGroups.maxOfOrNull { it.count } ?: 1
    var previousVariant: Int? = null

    orderedRecords.forEachIndexed { index, record ->
        if (
            variantGroups.isNotEmpty() &&
            record.variantIndex != previousVariant
        ) {
            appendLine(
                context.getString(
                    R.string.session_variant_heading,
                    record.variantIndex ?: 1,
                    variantCount
                ).uppercase(context.resources.configuration.locales[0])
            )
            appendLine()
            previousVariant = record.variantIndex
        }
        appendLine(
            "${context.getString(R.string.sensor_selector_label)}: " +
                exportSensorName(record.sensorDisplayName)
        )
        appendLine(
            "${context.getString(R.string.share_measurement_id_label)}: " +
                record.id
        )
        appendLine(
            "${context.getString(R.string.share_session_id_label)}: " +
                (record.sessionId?.toString() ?: "—")
        )
        record.positionIndex?.let { position ->
            appendLine(
                "${context.getString(R.string.acquisition_position_label)}: $position"
            )
        }
        record.variantIndex?.let { variant ->
            appendLine(
                "${context.getString(R.string.session_sequence_filter_position)}: " +
                    if (variantCount > 1) "$variant/$variantCount" else variant.toString()
            )
        }

        appendLine(
            "${context.getString(R.string.share_date_label)}: " +
                csvDateTime(
                    record.timestamp,
                    dateFormat,
                    context.resources.configuration.locales[0],
                    timeFormat
                )
        )
        appendLine(
            "${context.getString(R.string.share_acquisition_label)}: " +
                context.getString(
                    if (record.externalCommand) {
                        R.string.external_measurement
                    } else if (record.automatic) {
                        R.string.share_automatic
                    } else {
                        R.string.share_manual
                    }
                )
        )
        appendLine(
            "${context.getString(R.string.share_note_label)}: " +
                acquisitionDisplayNote(
                    note = record.note,
                    automatic = record.automatic,
                    sessionSequence =
                        record.sessionSequence,
                    emptyNote =
                        context.getString(R.string.no_note)
                )
        )
        appendLine()

        val uvTotal = record.sample.uvc + record.sample.uvb + record.sample.uva
        val hev = record.sample.violetto + record.sample.blu
        val visibleTotal =
            record.sample.violetto + record.sample.blu + record.sample.verde +
                record.sample.giallo + record.sample.arancione + record.sample.rosso
        val infraredTotal = record.sample.f8 + record.sample.nir

        fun readableValue(value: Double, group: SensorGroup): String =
            if (record.sample.isOutOfRange(group)) {
                context.getString(R.string.out_of_range_short)
            } else {
                "${formatUvirIrradianceNumber(value, 3, numericFormat, irradianceUnit)} " +
                    irradianceUnit.symbol
            }

        fun readablePercentage(
            value: Double,
            total: Double,
            group: SensorGroup
        ): String =
            if (record.sample.isOutOfRange(group)) {
                "—"
            } else {
                formatUvirNumber(
                    percentage(value, total).toDouble() * 100.0,
                    1,
                    numericFormat
                ) + "%"
            }

        fun appendIrradianceGroup(
            title: String,
            total: Double,
            group: SensorGroup,
            components: List<Pair<String, Double>>
        ) {
            val totalText = readableValue(total, group)
            val rows =
                components.map { (name, value) ->
                    Triple(
                        name,
                        readableValue(value, group),
                        readablePercentage(value, total, group)
                    )
                }
            val nameWidth =
                maxOf(title.length, rows.maxOfOrNull { it.first.length } ?: 0)
            val valueWidth =
                maxOf(totalText.length, rows.maxOfOrNull { it.second.length } ?: 0)

            appendLine(
                title.padEnd(nameWidth) + "  " + totalText.padEnd(valueWidth)
            )
            rows.forEach { (name, value, relativePercentage) ->
                appendLine(
                    name.padEnd(nameWidth) + "  " + value.padEnd(valueWidth) +
                        "  " + relativePercentage
                )
            }
            appendLine()
        }

        appendIrradianceGroup(
            title = context.getString(R.string.threshold_channel_uv_total),
            total = uvTotal,
            group = SensorGroup.UV,
            components =
                listOf(
                    "UVC" to record.sample.uvc,
                    "UVB" to record.sample.uvb,
                    "UVA" to record.sample.uva
                )
        )
        appendIrradianceGroup(
            title = context.getString(R.string.threshold_channel_visible_total),
            total = visibleTotal,
            group = SensorGroup.VISIBLE,
            components =
                listOf(
                    "HEV" to hev,
                    context.getString(R.string.violet) to record.sample.violetto,
                    context.getString(R.string.blue) to record.sample.blu,
                    context.getString(R.string.green) to record.sample.verde,
                    context.getString(R.string.yellow) to record.sample.giallo,
                    context.getString(R.string.orange) to record.sample.arancione,
                    context.getString(R.string.red) to record.sample.rosso
                )
        )
        appendIrradianceGroup(
            title = context.getString(R.string.threshold_channel_nir_total),
            total = infraredTotal,
            group = SensorGroup.NIR,
            components =
                listOf(
                    context.getString(R.string.session_chart_series_far_red) to record.sample.f8,
                    "NIR" to record.sample.nir
                )
        )

        val effects =
            biologicalEffects(
                record.sample
            )

        appendLine()
        appendLine(
            context.getString(
                R.string.share_biological_effects_heading,
                BIOLOGICAL_MODEL_VERSION
            )
        )
        appendLine(
            context.getString(
                R.string.share_biological_effects_disclaimer
            )
        )
        appendLine()

        listOf(
            Triple(
                context.getString(R.string.dna_uv_proxy),
                effects.dnaUvProxy,
                effects.dnaUvScore
            ),
            Triple(
                context.getString(R.string.uva_photoaging_proxy),
                effects.uvaPhotoagingProxy,
                effects.uvaPhotoagingScore
            ),
            Triple(
                context.getString(R.string.hev_oxidative_proxy),
                effects.hevOxidativeProxy,
                effects.hevOxidativeScore
            )
        ).forEachIndexed { effectIndex, (name, weightedValue, score) ->
            appendLine(name)
            val scoreText =
                formatUvirNumber(
                    score.toDouble() * 100.0,
                    1,
                    numericFormat
                )
            if (
                record.sample.isOutOfRange(
                    if (effectIndex == 2) SensorGroup.VISIBLE else SensorGroup.UV
                )
            ) {
                appendLine(
                    "${context.getString(R.string.out_of_range_short)} · $scoreText/100"
                )
            } else {
                appendLine(
                    context.getString(
                        R.string.share_biological_effect_values,
                        formatUvirIrradianceNumber(
                            weightedValue,
                            3,
                            numericFormat,
                            irradianceUnit
                        ),
                        scoreText
                    ).withUvirIrradianceUnit(irradianceUnit)
                )
            }
        }

        if (index < orderedRecords.lastIndex) {
            appendLine()
            appendLine("────────────────────")
            appendLine()
        }
    }
}

internal fun shareMeasurements(
    context: Context,
    records: List<SavedRecordDetail>,
    format: MeasurementShareFormat
) {
    if (records.isEmpty()) {
        return
    }

    val exportFormatting = uvirExportFormatting(context)
    val exportContext = exportFormatting.context
    val numericFormat = exportFormatting.numericFormat
    val dateFormat = exportFormatting.dateFormat
    val timeFormat = exportFormatting.timeFormat

    val subject =
        exportContext.getString(
            R.string.share_subject
        )

    val readableText =
        readableMeasurementTable(
            exportContext,
            records,
            numericFormat,
            dateFormat,
            timeFormat,
            exportFormatting.irradianceUnit
        )

    val sharedDirectory =
        File(
            context.cacheDir,
            "shared"
        ).apply {
            mkdirs()
        }

    val sharedBaseName =
        uvirExportBaseName(
            UvirExportContent.ACQUISITIONS
        )

    fun writeSharedFile(
        extension: String,
        content: String
    ): File =
        File(
            sharedDirectory,
            "$sharedBaseName.$extension"
        ).apply {
            writeText(
                content,
                Charsets.UTF_8
            )
        }

    fun sharedUri(file: File) =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

    val sendIntent =
        when (format) {
            MeasurementShareFormat.CSV -> {
                val csvFile =
                    writeSharedFile(
                        "csv",
                        measurementCsv(
                            records,
                            numericFormat,
                            dateFormat,
                            exportFormatting.language,
                            exportContext.resources.configuration.locales[0],
                            exportContext,
                            timeFormat,
                            exportFormatting.irradianceUnit
                        )
                    )

                val uri =
                    sharedUri(csvFile)

                Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData =
                        ClipData.newUri(
                            context.contentResolver,
                            csvFile.name,
                            uri
                        )
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }

            MeasurementShareFormat.READABLE_TABLE -> {
                val readableFile =
                    writeSharedFile(
                        "txt",
                        readableText
                    )

                val uri =
                    sharedUri(readableFile)

                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData =
                        ClipData.newUri(
                            context.contentResolver,
                            readableFile.name,
                            uri
                        )
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }

            MeasurementShareFormat.BOTH -> {
                val csvFile =
                    writeSharedFile(
                        "csv",
                        measurementCsv(
                            records,
                            numericFormat,
                            dateFormat,
                            exportFormatting.language,
                            exportContext.resources.configuration.locales[0],
                            exportContext,
                            timeFormat,
                            exportFormatting.irradianceUnit
                        )
                    )

                val readableFile =
                    writeSharedFile(
                        "txt",
                        readableText
                    )

                val csvUri =
                    sharedUri(csvFile)

                val readableUri =
                    sharedUri(readableFile)

                val uris =
                    arrayListOf(
                        csvUri,
                        readableUri
                    )

                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "text/*"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putParcelableArrayListExtra(
                        Intent.EXTRA_STREAM,
                        uris
                    )
                    clipData =
                        ClipData.newUri(
                            context.contentResolver,
                            csvFile.name,
                            csvUri
                        ).apply {
                            addItem(
                                ClipData.Item(
                                    readableUri
                                )
                            )
                        }
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
        }

    val chooser =
        Intent.createChooser(
            sendIntent,
            context.getString(
                R.string.share_measurements
            )
        )

    if (context !is Activity) {
        chooser.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )
    }

    context.startActivity(chooser)
}
