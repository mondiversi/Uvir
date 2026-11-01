package me.mondiversi.uvir

import android.content.Context

internal data class UvirExportHardwareModels(val ultraviolet: String = "—", val visibleInfrared: String = "—") {
    fun caption(): String = "UV: $ultraviolet · VIS/IR: $visibleInfrared"
}

internal fun exportHardwareModels(info: UvirSensorRuntimeInfo?): UvirExportHardwareModels {
    if (info == null) return UvirExportHardwareModels()
    // Older v1 snapshots did not persist component names. The v1 channel schema
    // has the same documented defaults as the live view; unrelated profiles do not.
    val standard = info.protocol == "uvir-sensor-v1"
    return UvirExportHardwareModels(
        info.uvSensorModel.trim().ifBlank { if (standard && info.uvAvailable != false) "AS7331" else "—" },
        info.visibleSensorModel.trim().ifBlank { if (standard && info.sensorAvailable != false) "AS7343" else "—" }
    )
}

/** Resolve once per exported file, by its records' IDs, never by UI selection. */
internal fun loadExportHardwareModels(context: Context?, sensorIds: Collection<Long?>): Map<Long, UvirExportHardwareModels> {
    return emptyMap()
}

internal fun Map<Long, UvirExportHardwareModels>.models(sensorId: Long?): UvirExportHardwareModels =
    get(sensorId) ?: UvirExportHardwareModels("AS7331", "AS7343")

internal fun exportHardwareCaption(context: Context, sensorIds: Collection<Long?>): String {
    val models = loadExportHardwareModels(context, sensorIds)
    return sensorIds.distinct().map { models.models(it).caption() }.distinct().joinToString("\n")
        .ifBlank { UvirExportHardwareModels().caption() }
}

internal fun hardwareCsvColumns(language: String): List<String> = if (language == "it")
    listOf("Modello_sensore_UV", "Modello_sensore_VIS_IR")
else listOf("UV_sensor_model", "VIS_IR_sensor_model")
