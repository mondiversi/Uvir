package me.mondiversi.uvir

internal const val UVIR_REQUIRED_SENSOR_FIRMWARE = "0.5.47"

internal fun firmwareIsCurrentForApp(version: String): Boolean =
    compareFirmwareVersions(version, UVIR_REQUIRED_SENSOR_FIRMWARE) >= 0

internal fun compareFirmwareVersions(
    actualVersion: String,
    requiredVersion: String
): Int {
    val actual = firmwareVersionParts(actualVersion)
    val required = firmwareVersionParts(requiredVersion)
    if (actual.isEmpty()) return -1

    val count = maxOf(actual.size, required.size)
    repeat(count) { index ->
        val comparison =
            actual.getOrElse(index) { 0 }
                .compareTo(required.getOrElse(index) { 0 })
        if (comparison != 0) return comparison
    }
    return 0
}

private fun firmwareVersionParts(version: String): List<Int> =
    version
        .substringBefore('-')
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .split('.')
        .let { parts ->
            if (parts.isEmpty() || parts.any { it.toIntOrNull() == null }) {
                emptyList()
            } else {
                parts.map(String::toInt)
            }
        }
