package me.mondiversi.uvir

import android.content.Context

/** Builds the body of a readable session export grouped by spectral area. */
internal fun readableMeasurementSpectralAreaBody(
    context: Context,
    records: List<SavedRecordDetail>,
    numericFormat: UvirNumericFormat,
    dateFormat: UvirDateFormat,
    timeFormat: UvirTimeFormat,
    irradianceUnit: UvirIrradianceUnit
): String = buildString {
    val locale = context.resources.configuration.locales[0]
    val orderedRecords = records.sortedBy { it.timestamp }

    fun readableValue(
        record: SavedRecordDetail,
        value: Double,
        group: SensorGroup
    ): String =
        if (record.sample.isOutOfRange(group)) {
            context.getString(R.string.out_of_range_short)
        } else {
            "${formatUvirIrradianceNumber(value, 3, numericFormat, irradianceUnit)} " +
                irradianceUnit.symbol
        }

    fun readablePercentage(
        record: SavedRecordDetail,
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

    fun appendAcquisitionHeading(record: SavedRecordDetail) {
        appendLine(
            "${context.getString(R.string.share_measurement_id_label)}: ${record.id} · " +
                csvDateTime(record.timestamp, dateFormat, locale, timeFormat)
        )
    }

    fun appendIrradianceArea(
        title: String,
        group: SensorGroup,
        totalLabel: String,
        total: (SavedRecordDetail) -> Double,
        components: (SavedRecordDetail) -> List<Pair<String, Double>>
    ) {
        appendLine(title.uppercase(locale))
        appendLine()
        orderedRecords.forEachIndexed { index, record ->
            appendAcquisitionHeading(record)
            val totalValue = total(record)
            val share = record.sample.spectralTotals().share(group)?.let { value ->
                formatUvirNumber(value.toDouble() * 100.0, 1, numericFormat) + "%"
            } ?: "—"
            appendLine("$totalLabel: ${readableValue(record, totalValue, group)} · $share")
            components(record).forEach { (name, value) ->
                appendLine(
                    "$name: ${readableValue(record, value, group)} · " +
                        readablePercentage(record, value, totalValue, group)
                )
            }
            if (index < orderedRecords.lastIndex) {
                appendLine()
                appendLine("────────────────────")
                appendLine()
            }
        }
    }

    appendLine(context.getString(R.string.saved_measurements).uppercase(locale))
    appendLine()
    orderedRecords.forEachIndexed { index, record ->
        appendLine(
            "${context.getString(R.string.share_measurement_id_label)}: ${record.id}"
        )
        appendLine(
            "${context.getString(R.string.sensor_selector_label)}: " +
                exportSensorName(record.sensorDisplayName)
        )
        appendLine(
            "${context.getString(R.string.share_session_id_label)}: " +
                (record.sessionId?.toString() ?: "—")
        )
        appendLine(
            "${context.getString(R.string.share_date_label)}: " +
                csvDateTime(record.timestamp, dateFormat, locale, timeFormat)
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
                    sessionSequence = record.sessionSequence,
                    emptyNote = context.getString(R.string.no_note)
                )
        )
        if (index < orderedRecords.lastIndex) {
            appendLine()
        }
    }
    appendLine()
    appendLine("════════════════════")
    appendLine()

    appendIrradianceArea(
        title = context.getString(R.string.threshold_channel_uv_total),
        group = SensorGroup.UV,
        totalLabel = context.getString(R.string.threshold_channel_uv_total),
        total = { it.sample.uvc + it.sample.uvb + it.sample.uva },
        components = {
            listOf(
                "UVC" to it.sample.uvc,
                "UVB" to it.sample.uvb,
                "UVA" to it.sample.uva
            )
        }
    )
    appendLine()
    appendLine("════════════════════")
    appendLine()

    appendIrradianceArea(
        title = context.getString(R.string.threshold_channel_visible_total),
        group = SensorGroup.VISIBLE,
        totalLabel = context.getString(R.string.threshold_channel_visible_total),
        total = {
            it.sample.violetto + it.sample.blu + it.sample.verde +
                it.sample.giallo + it.sample.arancione + it.sample.rosso
        },
        components = {
            listOf(
                "HEV" to (it.sample.violetto + it.sample.blu),
                context.getString(R.string.violet) to it.sample.violetto,
                context.getString(R.string.blue) to it.sample.blu,
                context.getString(R.string.green) to it.sample.verde,
                context.getString(R.string.yellow) to it.sample.giallo,
                context.getString(R.string.orange) to it.sample.arancione,
                context.getString(R.string.red) to it.sample.rosso
            )
        }
    )
    appendLine()
    appendLine("════════════════════")
    appendLine()

    appendIrradianceArea(
        title = context.getString(R.string.threshold_channel_nir_total),
        group = SensorGroup.NIR,
        totalLabel = context.getString(R.string.threshold_channel_nir_total),
        total = { it.sample.f8 + it.sample.nir },
        components = {
            listOf(
                context.getString(R.string.session_chart_series_far_red) to it.sample.f8,
                "NIR" to it.sample.nir
            )
        }
    )
    appendLine()
    appendLine("════════════════════")
    appendLine()

    appendLine(context.getString(R.string.biological_effects_group_name).uppercase(locale))
    appendLine(
        context.getString(
            R.string.share_biological_effects_heading,
            BIOLOGICAL_MODEL_VERSION
        )
    )
    appendLine(context.getString(R.string.share_biological_effects_disclaimer))
    appendLine()
    orderedRecords.forEachIndexed { index, record ->
        appendAcquisitionHeading(record)
        val effects = biologicalEffects(record.sample)
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
            val scoreText =
                formatUvirNumber(score.toDouble() * 100.0, 1, numericFormat)
            appendLine(name)
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
