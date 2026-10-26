package me.mondiversi.uvir

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel

/** Activity-session memory only: survives navigation/recreation, never process or task closure. */
internal class UvirSavedPresentationStore : ViewModel() {
    private val screens = mutableMapOf<String, UvirSavedScreenPresentation>()

    fun screen(key: String, initialViewMode: ViewMode = ViewMode.IRRADIANCE): UvirSavedScreenPresentation =
        screens.getOrPut(key) { UvirSavedScreenPresentation(initialViewMode) }

    fun clear() = screens.clear()

    override fun onCleared() = clear()
}

/** Each saved record/session owns its choices; none is passed to storage or export code. */
internal class UvirSavedScreenPresentation(initialViewMode: ViewMode) {
    val valueDisplay = mutableStateOf(UvirSavedValueDisplay())
    val viewMode = mutableStateOf(initialViewMode)
    val showChart = mutableStateOf(false)
    val sequencePosition = mutableIntStateOf(1)
    val expandedGroups = mutableStateMapOf<SessionChartGroup, Boolean>()
    val expandedAcquisitionCharts = mutableStateMapOf<AcquisitionChartSection, Boolean>()
    val expandedDataViews = mutableStateMapOf<ViewMode, Boolean>()
    val expandedChartViews = mutableStateMapOf<ViewMode, Boolean>()
    val dataExpanded = mutableStateOf(true)
    val chartExpanded = mutableStateOf(true)
}

internal val LocalUvirSavedPresentationStore = staticCompositionLocalOf<UvirSavedPresentationStore?> { null }

@Composable
internal fun rememberUvirSavedScreenPresentation(
    key: String,
    initialViewMode: ViewMode = ViewMode.IRRADIANCE
): UvirSavedScreenPresentation {
    val store = LocalUvirSavedPresentationStore.current
    return remember(store, key) {
        store?.screen(key, initialViewMode) ?: UvirSavedScreenPresentation(initialViewMode)
    }
}
