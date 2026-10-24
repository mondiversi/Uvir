package me.mondiversi.uvir

internal const val KEY_MAIN_ISLAND_ORDER = "main_irradiance_island_order"
internal val UvirDefaultMainIslandOrder = listOf(SensorGroup.UV, SensorGroup.VISIBLE, SensorGroup.NIR)

/** Append missing groups and ignore unknown/duplicate names from old or imported preferences. */
internal fun decodeUvirMainIslandOrder(stored: String?): List<SensorGroup> {
    val byName = UvirDefaultMainIslandOrder.associateBy { it.name }
    return (stored.orEmpty().split(',').mapNotNull { byName[it.trim()] } + UvirDefaultMainIslandOrder).distinct()
}

internal fun moveUvirMainIsland(order: List<SensorGroup>, group: SensorGroup, step: Int): List<SensorGroup> {
    val normalized = decodeUvirMainIslandOrder(order.joinToString(",") { it.name })
    val index = normalized.indexOf(group)
    val target = index + step
    if (step !in listOf(-1, 1) || index < 0 || target !in normalized.indices) return normalized
    return normalized.toMutableList().apply {
        this[index] = normalized[target]
        this[target] = group
    }
}
