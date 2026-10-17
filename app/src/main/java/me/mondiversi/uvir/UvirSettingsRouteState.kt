package me.mondiversi.uvir

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/** Navigation only: never retain sensor settings or job state across a context switch. */
@Stable
internal class UvirSettingsRouteState(
    initiallyOpen: Boolean = false,
    initialPage: UvirSettingsPage? = null,
    initialScroll: Int = 0,
    initialSimulationFocusRequested: Boolean = false
) {
    val isOpen = mutableStateOf(initiallyOpen)
    val page = mutableStateOf(initialPage)
    val scrollState = ScrollState(initialScroll)
    val simulationFocusRequested = mutableStateOf(initialSimulationFocusRequested)

    fun openSimulationSettings() {
        page.value = UvirSettingsPage.DEBUG
        simulationFocusRequested.value = true
        isOpen.value = true
    }

    fun close() {
        isOpen.value = false
        page.value = null
        simulationFocusRequested.value = false
    }
}

private val UvirSettingsRouteStateSaver = listSaver<UvirSettingsRouteState, Any>(
    save = { listOf(it.isOpen.value, it.page.value?.name.orEmpty(), it.scrollState.value,
        it.simulationFocusRequested.value) },
    restore = {
        UvirSettingsRouteState(it[0] as Boolean,
            runCatching { UvirSettingsPage.valueOf(it[1] as String) }.getOrNull(), it[2] as Int,
            it.getOrNull(3) as? Boolean ?: false)
    }
)

@Composable
internal fun rememberUvirSettingsRouteState(): UvirSettingsRouteState =
    rememberSaveable(saver = UvirSettingsRouteStateSaver) { UvirSettingsRouteState() }
