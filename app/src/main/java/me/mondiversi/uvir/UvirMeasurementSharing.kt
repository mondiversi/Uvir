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
        "Automatico",
        "Nota",
        "Progressivo_sessione",
        "UVC_100_280_nm_uW_cm2",
        "UVB_280_315_nm_uW_cm2",
        "UVA_315_400_nm_uW_cm2",
        "UV_uW_cm2",
        "HEV_400_500_nm_uW_cm2",
        "Violetto_400_450_nm_uW_cm2",
        "Blu_450_495_nm_uW_cm2",
        "Verde_495_570_nm_uW_cm2",
        "Giallo_570_590_nm_uW_cm2",
        "Arancione_590_620_nm_uW_cm2",
        "Rosso_620_700_nm_uW_cm2",
        "Visibile_uW_cm2",
        "FarRed_picco_745_nm_uW_cm2",
        "NIR_picco_855_nm_uW_cm2",
        "Infrarosso_uW_cm2",
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
        "Automatic",
        "Note",
        "Session_sequence",
        "UVC_100_280_nm_uW_cm2",
        "UVB_280_315_nm_uW_cm2",
        "UVA_315_400_nm_uW_cm2",
        "UV_uW_cm2",
        "HEV_400_500_nm_uW_cm2",
        "Violet_400_450_nm_uW_cm2",
        "Blue_450_495_nm_uW_cm2",
        "Green_495_570_nm_uW_cm2",
        "Yellow_570_590_nm_uW_cm2",
        "Orange_590_620_nm_uW_cm2",
        "Red_620_700_nm_uW_cm2",
        "Visible_uW_cm2",
        "FarRed_peak_745_nm_uW_cm2",
        "NIR_peak_855_nm_uW_cm2",
        "Infrared_uW_cm2",
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
    language: String
): List<String> =
    if (language == "it") {
        MEASUREMENT_EXPORT_COLUMNS_IT
    } else {
        MEASUREMENT_EXPORT_COLUMNS_EN
    }

internal fun csvDateTime(
    timestamp: Long,
    language: String
): String =
    SimpleDateFormat(
        if (language == "it") {
            "dd/MM/yyyy HH:mm:ss"
        } else {
            "yyyy-MM-dd HH:mm:ss"
        },
        Locale.getDefault()
    ).format(Date(timestamp))

internal fun measurementCsv(
    records: List<SavedRecordDetail>,
    numericFormat: UvirNumericFormat
): String = buildString {
    val language = DATA_EXPORT_LANGUAGE

    appendLine(
        measurementExportColumns(
            language
        ).joinToString(";") {
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

        appendLine(
            listOf(
                record.id.toString(),
                record.sessionId
                    ?.toString()
                    .orEmpty(),
                csvDateTime(
                    record.timestamp,
                    language
                ),
                record.timestamp.toString(),
                if (record.automatic) {
                    if (language == "it") {
                        "Automatica"
                    } else {
                        "Automatic"
                    }
                } else {
                    if (language == "it") {
                        "Manuale"
                    } else {
                        "Manual"
                    }
                },
                if (record.automatic) "1" else "0",
                record.note,
                record.sessionSequence
                    ?.toString()
                    .orEmpty(),
                csvNumber(sample.uvc, numericFormat),
                csvNumber(sample.uvb, numericFormat),
                csvNumber(sample.uva, numericFormat),
                csvNumber(uvTotal, numericFormat),
                csvNumber(hev, numericFormat),
                csvNumber(sample.violetto, numericFormat),
                csvNumber(sample.blu, numericFormat),
                csvNumber(sample.verde, numericFormat),
                csvNumber(sample.giallo, numericFormat),
                csvNumber(sample.arancione, numericFormat),
                csvNumber(sample.rosso, numericFormat),
                csvNumber(visibleTotal, numericFormat),
                csvNumber(sample.f8, numericFormat),
                csvNumber(sample.nir, numericFormat),
                csvNumber(farRedNirTotal, numericFormat),
                BIOLOGICAL_MODEL_VERSION,
                csvNumber(effects.dnaUvProxy, numericFormat),
                csvNumber(effects.dnaUvScore.toDouble() * 100.0, numericFormat),
                csvNumber(effects.uvaPhotoagingProxy, numericFormat),
                csvNumber(effects.uvaPhotoagingScore.toDouble() * 100.0, numericFormat),
                csvNumber(effects.hevOxidativeProxy, numericFormat),
                csvNumber(effects.hevOxidativeScore.toDouble() * 100.0, numericFormat),
                exportSensorName(record.sensorDisplayName)
            ).joinToString(";") {
                csvCell(it)
            }
        )
    }
}

internal fun readableMeasurementTable(
    context: Context,
    records: List<SavedRecordDetail>,
    numericFormat: UvirNumericFormat
): String = buildString {
    appendLine("Uvir acquisition log")
    appendLine()

    records.forEachIndexed { index, record ->
        appendLine("Sensor: ${exportSensorName(record.sensorDisplayName)}")
        appendLine(
            "${context.getString(R.string.share_measurement_id_label)}: " +
                record.id
        )
        appendLine(
            "${context.getString(R.string.share_session_id_label)}: " +
                (record.sessionId?.toString() ?: "—")
        )

        appendLine(
            "${context.getString(R.string.share_date_label)}: " +
                csvDateTime(
                    record.timestamp,
                    DATA_EXPORT_LANGUAGE
                )
        )
        appendLine(
            "${context.getString(R.string.share_acquisition_label)}: " +
                context.getString(
                    if (record.automatic) {
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

        listOf(
            "UV-C" to record.sample.uvc,
            "UV-B" to record.sample.uvb,
            "UV-A" to record.sample.uva,
            "HEV" to (record.sample.violetto + record.sample.blu),
            context.getString(R.string.violet) to record.sample.violetto,
            context.getString(R.string.blue) to record.sample.blu,
            context.getString(R.string.green) to record.sample.verde,
            context.getString(R.string.yellow) to record.sample.giallo,
            context.getString(R.string.orange) to record.sample.arancione,
            context.getString(R.string.red) to record.sample.rosso,
            "FAR-RED" to record.sample.f8,
            "NIR" to record.sample.nir
        ).forEach { (name, value) ->
            appendLine(
                "%-8s  %s µW/cm²".format(
                    Locale.US,
                    name,
                    formatUvirNumber(
                        value,
                        3,
                        numericFormat
                    )
                )
            )
        }

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
        ).forEach { (name, weightedValue, score) ->
            appendLine(name)
            appendLine(
                context.getString(
                    R.string.share_biological_effect_values,
                    formatUvirNumber(
                        weightedValue,
                        3,
                        numericFormat
                    ),
                    formatUvirNumber(
                        score.toDouble() * 100.0,
                        1,
                        numericFormat
                    )
                )
            )
        }

        if (index < records.lastIndex) {
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

    // Exports are intentionally stable and internationally exchangeable:
    // the user's display preference must never alter shared data files.
    val numericFormat =
        UvirNumericFormat.INTERNATIONAL

    val exportConfiguration =
        android.content.res.Configuration(
            context.resources.configuration
        ).apply {
            setLocale(Locale.ENGLISH)
            setLayoutDirection(Locale.ENGLISH)
        }

    val exportContext =
        context.createConfigurationContext(
            exportConfiguration
        )

    val subject =
        exportContext.getString(
            R.string.share_subject
        )

    val readableText =
        readableMeasurementTable(
            exportContext,
            records,
            numericFormat
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
                            numericFormat
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
                            numericFormat
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
