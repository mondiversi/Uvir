package me.mondiversi.uvir

import android.content.Context
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
        "Variante",
        "Numero_varianti",
        "UVC_240_280_nm_uW_cm2",
        "UVC_Percentuali_sul_totale",
        "UVC_Percentuali_per_gruppo",
        "UVB_280_315_nm_uW_cm2",
        "UVB_Percentuali_sul_totale",
        "UVB_Percentuali_per_gruppo",
        "UVA_315_410_nm_uW_cm2",
        "UVA_Percentuali_sul_totale",
        "UVA_Percentuali_per_gruppo",
        "Totale_ultravioletti_uW_cm2",
        "Totale_ultravioletti_Percentuali_sul_totale",
        "Totale_ultravioletti_Percentuali_per_gruppo",
        "HEV_uW_cm2",
        "HEV_Percentuali_sul_totale",
        "HEV_Percentuali_per_gruppo",
        "Violetto_peaks_405_425_nm_uW_cm2",
        "Violetto_Percentuali_sul_totale",
        "Violetto_Percentuali_per_gruppo",
        "Blu_peaks_450_475_nm_uW_cm2",
        "Blu_Percentuali_sul_totale",
        "Blu_Percentuali_per_gruppo",
        "Verde_peak_515_nm_uW_cm2",
        "Verde_Percentuali_sul_totale",
        "Verde_Percentuali_per_gruppo",
        "Verde_giallo_peaks_550_555_nm_uW_cm2",
        "Verde_giallo_Percentuali_sul_totale",
        "Verde_giallo_Percentuali_per_gruppo",
        "Arancione_peak_600_nm_uW_cm2",
        "Arancione_Percentuali_sul_totale",
        "Arancione_Percentuali_per_gruppo",
        "Rosso_peak_640_nm_uW_cm2",
        "Rosso_Percentuali_sul_totale",
        "Rosso_Percentuali_per_gruppo",
        "Totale_luce_visibile_uW_cm2",
        "Totale_luce_visibile_Percentuali_sul_totale",
        "Totale_luce_visibile_Percentuali_per_gruppo",
        "FarRed_picchi_690_745_nm_uW_cm2",
        "FarRed_Percentuali_sul_totale",
        "FarRed_Percentuali_per_gruppo",
        "NIR_picco_855_nm_uW_cm2",
        "NIR_Percentuali_sul_totale",
        "NIR_Percentuali_per_gruppo",
        "Totale_infrarosso_uW_cm2",
        "Totale_infrarosso_Percentuali_sul_totale",
        "Totale_infrarosso_Percentuali_per_gruppo",
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
        "Variant",
        "Variant_count",
        "UVC_240_280_nm_uW_cm2",
        "UVC_Percentages_of_total",
        "UVC_Percentages_within_group",
        "UVB_280_315_nm_uW_cm2",
        "UVB_Percentages_of_total",
        "UVB_Percentages_within_group",
        "UVA_315_410_nm_uW_cm2",
        "UVA_Percentages_of_total",
        "UVA_Percentages_within_group",
        "Ultraviolet_total_uW_cm2",
        "Ultraviolet_total_Percentages_of_total",
        "Ultraviolet_total_Percentages_within_group",
        "HEV_uW_cm2",
        "HEV_Percentages_of_total",
        "HEV_Percentages_within_group",
        "Violet_peaks_405_425_nm_uW_cm2",
        "Violet_Percentages_of_total",
        "Violet_Percentages_within_group",
        "Blue_peaks_450_475_nm_uW_cm2",
        "Blue_Percentages_of_total",
        "Blue_Percentages_within_group",
        "Green_peak_515_nm_uW_cm2",
        "Green_Percentages_of_total",
        "Green_Percentages_within_group",
        "Green_yellow_peaks_550_555_nm_uW_cm2",
        "Green_yellow_Percentages_of_total",
        "Green_yellow_Percentages_within_group",
        "Orange_peak_600_nm_uW_cm2",
        "Orange_Percentages_of_total",
        "Orange_Percentages_within_group",
        "Red_peak_640_nm_uW_cm2",
        "Red_Percentages_of_total",
        "Red_Percentages_within_group",
        "Visible_total_uW_cm2",
        "Visible_total_Percentages_of_total",
        "Visible_total_Percentages_within_group",
        "FarRed_peaks_690_745_nm_uW_cm2",
        "FarRed_Percentages_of_total",
        "FarRed_Percentages_within_group",
        "NIR_peak_855_nm_uW_cm2",
        "NIR_Percentages_of_total",
        "NIR_Percentages_within_group",
        "Infrared_total_uW_cm2",
        "Infrared_total_Percentages_of_total",
        "Infrared_total_Percentages_within_group",
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
    } + (if (language == "it") {
        "Valore_fuori_scala"
    } else {
        "Out_of_range"
    }) + "Optical_window" + hardwareCsvColumns(language)

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
        label(R.string.session_sequence_filter_position),
        label(R.string.export_variant_count_label),
        "UVC_240_280_nm_${irradianceUnit.csvSymbol}",
        "UVC_${label(R.string.export_percentages_total)}",
        "UVC_${label(R.string.export_percentages_group)}",
        "UVB_280_315_nm_${irradianceUnit.csvSymbol}",
        "UVB_${label(R.string.export_percentages_total)}",
        "UVB_${label(R.string.export_percentages_group)}",
        "UVA_315_410_nm_${irradianceUnit.csvSymbol}",
        "UVA_${label(R.string.export_percentages_total)}",
        "UVA_${label(R.string.export_percentages_group)}",
        "${label(R.string.export_total_label)}_${label(R.string.uv_radiation)}_${irradianceUnit.csvSymbol}",
        "${label(R.string.export_total_label)}_${label(R.string.uv_radiation)}_${label(R.string.export_percentages_total)}",
        "${label(R.string.export_total_label)}_${label(R.string.uv_radiation)}_${label(R.string.export_percentages_group)}",
        "HEV_${irradianceUnit.csvSymbol}",
        "HEV_${label(R.string.export_percentages_total)}",
        "HEV_${label(R.string.export_percentages_group)}",
        "${label(R.string.violet)}_peaks_405_425_nm_${irradianceUnit.csvSymbol}",
        "${label(R.string.violet)}_${label(R.string.export_percentages_total)}",
        "${label(R.string.violet)}_${label(R.string.export_percentages_group)}",
        "${label(R.string.blue)}_peaks_450_475_nm_${irradianceUnit.csvSymbol}",
        "${label(R.string.blue)}_${label(R.string.export_percentages_total)}",
        "${label(R.string.blue)}_${label(R.string.export_percentages_group)}",
        "${label(R.string.green)}_peak_515_nm_${irradianceUnit.csvSymbol}",
        "${label(R.string.green)}_${label(R.string.export_percentages_total)}",
        "${label(R.string.green)}_${label(R.string.export_percentages_group)}",
        "${label(R.string.yellow)}_peaks_550_555_nm_${irradianceUnit.csvSymbol}",
        "${label(R.string.yellow)}_${label(R.string.export_percentages_total)}",
        "${label(R.string.yellow)}_${label(R.string.export_percentages_group)}",
        "${label(R.string.orange)}_peak_600_nm_${irradianceUnit.csvSymbol}",
        "${label(R.string.orange)}_${label(R.string.export_percentages_total)}",
        "${label(R.string.orange)}_${label(R.string.export_percentages_group)}",
        "${label(R.string.red)}_peak_640_nm_${irradianceUnit.csvSymbol}",
        "${label(R.string.red)}_${label(R.string.export_percentages_total)}",
        "${label(R.string.red)}_${label(R.string.export_percentages_group)}",
        "${label(R.string.export_total_label)}_${label(R.string.visible_light)}_${irradianceUnit.csvSymbol}",
        "${label(R.string.export_total_label)}_${label(R.string.visible_light)}_${label(R.string.export_percentages_total)}",
        "${label(R.string.export_total_label)}_${label(R.string.visible_light)}_${label(R.string.export_percentages_group)}",
        "${label(R.string.session_chart_series_far_red)}_peaks_690_745_nm_${irradianceUnit.csvSymbol}",
        "${label(R.string.session_chart_series_far_red)}_${label(R.string.export_percentages_total)}",
        "${label(R.string.session_chart_series_far_red)}_${label(R.string.export_percentages_group)}",
        "NIR_peak_855_nm_${irradianceUnit.csvSymbol}",
        "NIR_${label(R.string.export_percentages_total)}",
        "NIR_${label(R.string.export_percentages_group)}",
        "${label(R.string.export_total_label)}_${label(R.string.far_red_nir)}_${irradianceUnit.csvSymbol}",
        "${label(R.string.export_total_label)}_${label(R.string.far_red_nir)}_${label(R.string.export_percentages_total)}",
        "${label(R.string.export_total_label)}_${label(R.string.far_red_nir)}_${label(R.string.export_percentages_group)}",
        "${label(R.string.biological_effects_group_name)}_model",
        "${estimatedWeighted}_${label(R.string.dna_uv_proxy)}_${irradianceUnit.csvSymbol}_eq",
        "${spectralIndex}_${label(R.string.dna_uv_proxy)}_0_100",
        "${estimatedWeighted}_${label(R.string.uva_photoaging_proxy)}_${irradianceUnit.csvSymbol}_eq",
        "${spectralIndex}_${label(R.string.uva_photoaging_proxy)}_0_100",
        "${estimatedWeighted}_${label(R.string.hev_oxidative_proxy)}_${irradianceUnit.csvSymbol}_eq",
        "${spectralIndex}_${label(R.string.hev_oxidative_proxy)}_0_100",
        label(R.string.sensor_name_label),
        label(R.string.out_of_range_csv_header),
        "Optical_window"
    ) + hardwareCsvColumns(language)
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
    irradianceUnit: UvirIrradianceUnit = UvirIrradianceUnit.UW_CM2,
    variantCountsBySession: Map<Long, Int> = emptyMap(),
    irradianceDecimals: Int? = labelContext?.let { uvirExportIrradianceDecimals(it, irradianceUnit) }
): String = buildString {
    val hardware = loadExportHardwareModels(labelContext, records.map { it.sensorId })
    val columns =
        labelContext?.let {
            measurementExportColumns(it, language, irradianceUnit)
        } ?: measurementExportColumns(language, irradianceUnit)
    appendLine(
        columns.joinToString(";") {
            csvCell(it)
        }
    )
    val inferredVariantCountsBySession =
        records
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
    val effectiveVariantCountsBySession =
        inferredVariantCountsBySession + variantCountsBySession

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
        val variantCount =
            record.sessionId
                ?.let(effectiveVariantCountsBySession::get)
                ?.takeIf { it > 1 }
        fun irradiance(value: Double, group: SensorGroup): String =
            if (sample.isOutOfRange(group)) {
                ""
            } else {
                formatUvirIrradianceExportNumber(value, numericFormat, irradianceUnit, irradianceDecimals)
            }
        fun relativePercent(value: Double, group: SensorGroup, withinGroup: Boolean): String {
            val percentages = sample.exportSpectralPercentages(value, group)
            return (if (withinGroup) percentages.group else percentages.total)?.let {
                formatUvirExportPercentage(it, numericFormat, irradianceDecimals)
            } ?: "—"
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
                record.variantIndex
                    ?.takeIf { variantCount != null }
                    ?.let(::uvirVariantLabel)
                    .orEmpty(),
                variantCount
                    ?.toString()
                    .orEmpty(),
                irradiance(sample.uvc, SensorGroup.UV),
                relativePercent(sample.uvc, SensorGroup.UV, withinGroup = false),
                relativePercent(sample.uvc, SensorGroup.UV, withinGroup = true),
                irradiance(sample.uvb, SensorGroup.UV),
                relativePercent(sample.uvb, SensorGroup.UV, withinGroup = false),
                relativePercent(sample.uvb, SensorGroup.UV, withinGroup = true),
                irradiance(sample.uva, SensorGroup.UV),
                relativePercent(sample.uva, SensorGroup.UV, withinGroup = false),
                relativePercent(sample.uva, SensorGroup.UV, withinGroup = true),
                irradiance(uvTotal, SensorGroup.UV),
                relativePercent(uvTotal, SensorGroup.UV, withinGroup = false),
                relativePercent(uvTotal, SensorGroup.UV, withinGroup = true),
                irradiance(hev, SensorGroup.VISIBLE),
                relativePercent(hev, SensorGroup.VISIBLE, withinGroup = false),
                relativePercent(hev, SensorGroup.VISIBLE, withinGroup = true),
                irradiance(sample.violetto, SensorGroup.VISIBLE),
                relativePercent(sample.violetto, SensorGroup.VISIBLE, withinGroup = false),
                relativePercent(sample.violetto, SensorGroup.VISIBLE, withinGroup = true),
                irradiance(sample.blu, SensorGroup.VISIBLE),
                relativePercent(sample.blu, SensorGroup.VISIBLE, withinGroup = false),
                relativePercent(sample.blu, SensorGroup.VISIBLE, withinGroup = true),
                irradiance(sample.verde, SensorGroup.VISIBLE),
                relativePercent(sample.verde, SensorGroup.VISIBLE, withinGroup = false),
                relativePercent(sample.verde, SensorGroup.VISIBLE, withinGroup = true),
                irradiance(sample.giallo, SensorGroup.VISIBLE),
                relativePercent(sample.giallo, SensorGroup.VISIBLE, withinGroup = false),
                relativePercent(sample.giallo, SensorGroup.VISIBLE, withinGroup = true),
                irradiance(sample.arancione, SensorGroup.VISIBLE),
                relativePercent(sample.arancione, SensorGroup.VISIBLE, withinGroup = false),
                relativePercent(sample.arancione, SensorGroup.VISIBLE, withinGroup = true),
                irradiance(sample.rosso, SensorGroup.VISIBLE),
                relativePercent(sample.rosso, SensorGroup.VISIBLE, withinGroup = false),
                relativePercent(sample.rosso, SensorGroup.VISIBLE, withinGroup = true),
                irradiance(visibleTotal, SensorGroup.VISIBLE),
                relativePercent(visibleTotal, SensorGroup.VISIBLE, withinGroup = false),
                relativePercent(visibleTotal, SensorGroup.VISIBLE, withinGroup = true),
                irradiance(sample.f8, SensorGroup.NIR),
                relativePercent(sample.f8, SensorGroup.NIR, withinGroup = false),
                relativePercent(sample.f8, SensorGroup.NIR, withinGroup = true),
                irradiance(sample.nir, SensorGroup.NIR),
                relativePercent(sample.nir, SensorGroup.NIR, withinGroup = false),
                relativePercent(sample.nir, SensorGroup.NIR, withinGroup = true),
                irradiance(farRedNirTotal, SensorGroup.NIR),
                relativePercent(farRedNirTotal, SensorGroup.NIR, withinGroup = false),
                relativePercent(farRedNirTotal, SensorGroup.NIR, withinGroup = true),
                BIOLOGICAL_MODEL_VERSION,
                irradiance(effects.dnaUvProxy, SensorGroup.UV),
                formatUvirExportPercentage(effects.dnaUvScore.toDouble() * 100.0, numericFormat, irradianceDecimals),
                irradiance(effects.uvaPhotoagingProxy, SensorGroup.UV),
                formatUvirExportPercentage(effects.uvaPhotoagingScore.toDouble() * 100.0, numericFormat, irradianceDecimals),
                irradiance(effects.hevOxidativeProxy, SensorGroup.VISIBLE),
                formatUvirExportPercentage(effects.hevOxidativeScore.toDouble() * 100.0, numericFormat, irradianceDecimals),
                exportSensorName(record.sensorDisplayName),
                if (sample.hasAnyOutOfRangeValue()) "1" else "0",
                sample.opticalWindow?.toJson()?.toString().orEmpty(),
                hardware.models(record.sensorId).ultraviolet,
                hardware.models(record.sensorId).visibleInfrared
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
    groupByVariant: Boolean = false,
    variantCountOverride: Int? = null,
    includeRecordVariant: Boolean = true,
    grouping: UvirReadableTableGrouping = UvirReadableTableGrouping.BY_ACQUISITION,
    irradianceDecimals: Int? = uvirExportIrradianceDecimals(context, irradianceUnit)
): String {
    if (
        grouping == UvirReadableTableGrouping.BY_SPECTRAL_AREA &&
        records.size > 1
    ) {
        return buildString {
            appendLine("Uvir ${context.getString(R.string.saved_measurements)}")
            appendLine()
            append(
                readableMeasurementSpectralAreaBody(
                    context = context,
                    records = records,
                    numericFormat = numericFormat,
                    dateFormat = dateFormat,
                    timeFormat = timeFormat,
                    irradianceUnit = irradianceUnit,
                    irradianceDecimals = irradianceDecimals
                )
            )
        }
    }

    return buildString {
    val hardware = loadExportHardwareModels(context, records.map { it.sensorId })
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
    val variantCount =
        variantCountOverride
            ?.coerceAtLeast(1)
            ?: variantGroups.maxOfOrNull { it.count }
            ?: orderedRecords.mapNotNull { it.variantIndex }.maxOrNull()
            ?: 1
    var previousVariant: Int? = null

    orderedRecords.forEachIndexed { index, record ->
        if (
            variantGroups.isNotEmpty() &&
            record.variantIndex != previousVariant
        ) {
            appendLine(
                uvirVariantHeading(
                    context.resources,
                    record.variantIndex ?: 1
                ).uppercase(context.resources.configuration.locales[0])
            )
            appendLine()
            previousVariant = record.variantIndex
        }
        appendLine(
            "${context.getString(R.string.sensor_selector_label)}: " +
                exportSensorName(record.sensorDisplayName)
        )
        appendLine(hardware.models(record.sensorId).caption())
        appendLine(
            "${context.getString(R.string.share_measurement_id_label)}: " +
                record.id
        )
        appendLine(
            "${context.getString(R.string.share_session_id_label)}: " +
                (record.sessionId?.toString() ?: "—")
        )
        record.variantIndex
            ?.takeIf { includeRecordVariant && variantCount > 1 }
            ?.let { variant ->
                appendLine(
                    uvirVariantHeading(
                        context.resources,
                        variant
                    )
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

        appendLine("${context.getString(R.string.optical_window_title)}: ${opticalWindowSummary(record.sample.opticalWindow, numericFormat)}")
        val hev = record.sample.violetto + record.sample.blu

        fun appendIrradianceGroup(
            title: String,
            group: SensorGroup,
            components: List<Pair<String, Double>>
        ) {
            append(readableIrradianceExportTable(context, record.sample, group, title,
                components, numericFormat, irradianceUnit, irradianceDecimals))
            appendLine()
        }

        appendIrradianceGroup(
            title = context.getString(R.string.threshold_channel_uv_total),
            group = SensorGroup.UV,
            components =
                listOf(
                    sensorChannelLabel("UVC", null, "UVC") to record.sample.uvc,
                    sensorChannelLabel("UVB", null, "UVB") to record.sample.uvb,
                    sensorChannelLabel("UVA", null, "UVA") to record.sample.uva
                )
        )
        appendIrradianceGroup(
            title = context.getString(R.string.threshold_channel_visible_total),
            group = SensorGroup.VISIBLE,
            components =
                listOf(
                    sensorChannelLabel("HEV", null, "HEV") to hev,
                    context.resources.sensorChannelLabel(R.string.violet) to record.sample.violetto,
                    context.resources.sensorChannelLabel(R.string.blue) to record.sample.blu,
                    context.resources.sensorChannelLabel(R.string.green) to record.sample.verde,
                    context.resources.sensorChannelLabel(R.string.yellow) to record.sample.giallo,
                    context.resources.sensorChannelLabel(R.string.orange) to record.sample.arancione,
                    context.resources.sensorChannelLabel(R.string.red) to record.sample.rosso
                )
        )
        appendIrradianceGroup(
            title = context.getString(R.string.threshold_channel_nir_total),
            group = SensorGroup.NIR,
            components =
                listOf(
                    context.resources.sensorChannelLabel(R.string.session_chart_series_far_red) to record.sample.f8,
                    sensorChannelLabel("NIR", null, "NIR") to record.sample.nir
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
                formatUvirExportPercentage(score.toDouble() * 100.0, numericFormat, irradianceDecimals)
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
                        formatUvirIrradianceExportNumber(
                            weightedValue,
                            numericFormat,
                            irradianceUnit,
                            irradianceDecimals
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

    val sharedBaseName =
        uvirExportBaseName(
            UvirExportContent.ACQUISITIONS
        )

    fun writeSharedFile(
        fileFormat: UvirExportFileFormat,
        content: String
    ): File =
        prepareUvirTextExport(
            context = context,
            fileName = fileFormat.fileName(sharedBaseName),
            text = content
        )

    fun csvFile(): File =
        writeSharedFile(
            UvirExportFileFormat.CSV,
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

    fun readableFile(): File =
        writeSharedFile(
            UvirExportFileFormat.TXT,
            readableText
        )

    val files =
        when (format) {
            MeasurementShareFormat.CSV -> listOf(csvFile())
            MeasurementShareFormat.READABLE_TABLE -> listOf(readableFile())
            MeasurementShareFormat.BOTH -> listOf(csvFile(), readableFile())
        }

    deliverUvirExportFiles(
        context = context,
        files = files,
        destination = UvirExportDestination.SHARE,
        chooserTitle = context.getString(R.string.share_measurements),
        subject = subject
    )
}
