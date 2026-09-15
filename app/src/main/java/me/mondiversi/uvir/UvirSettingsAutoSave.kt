package me.mondiversi.uvir

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

internal enum class SettingsSaveGroup {
    NAME, PARAMETERS, CALIBRATION, SAMPLING, ALERTS, RADIO, WIFI, INTERNET,
    NUMERIC_FORMAT, LANGUAGE, FAKE_DATA
}

/** User commits only: readback, recomposition and opening a card never save. */
internal class UvirSettingsSaveQueue(
    private val scope: CoroutineScope,
    private val onBusy: (Boolean) -> Unit,
    private val onFailure: () -> Unit = {}
) {
    var save: suspend (SettingsSaveGroup) -> Unit = {}
    var prepare: ((SettingsSaveGroup) -> (suspend () -> Unit))? = null
    private val pending = linkedMapOf<SettingsSaveGroup, suspend () -> Unit>()
    private var worker: Job? = null
    private var editor: Any? = null
    private var finishEditor: (() -> Unit)? = null

    fun request(group: SettingsSaveGroup) {
        pending[group] = prepare?.invoke(group) ?: { save(group) }
        if (worker?.isActive == true) return
        worker = scope.launch {
            onBusy(true)
            try {
                // Let normalization and checkbox callbacks update their state first.
                yield()
                while (pending.isNotEmpty()) {
                    val next = pending.keys.firstOrNull { it != SettingsSaveGroup.LANGUAGE } ?: pending.keys.first()
                    val action = pending.remove(next) ?: continue
                    try {
                        action()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        onFailure()
                    }
                    yield()
                }
            } finally {
                onBusy(false)
            }
        }
    }

    fun editing(token: Any, finish: () -> Unit) {
        if (editor != token) finishEditing()
        editor = token
        finishEditor = finish
    }

    fun finishEditing(token: Any) {
        if (editor != token) return
        val finish = finishEditor
        editor = null
        finishEditor = null
        finish?.invoke()
    }

    fun finishEditing() { editor?.let(::finishEditing) }

    fun cancel() {
        pending.clear()
        editor = null
        finishEditor = null
        worker?.cancel()
    }
}

internal data class UvirSettingsCommitScope(
    val queue: UvirSettingsSaveQueue,
    val group: SettingsSaveGroup
) {
    fun commit() = queue.request(group)
}

internal val LocalSettingsCommit = staticCompositionLocalOf<UvirSettingsCommitScope?> { null }

@Composable
internal fun SettingsAutoSaveGroup(group: SettingsSaveGroup, content: @Composable () -> Unit) {
    val parent = LocalSettingsCommit.current
    CompositionLocalProvider(LocalSettingsCommit provides parent?.copy(group = group), content = content)
}

/** Registers the current editor so Back/collapse also commits before disposal. */
internal fun Modifier.settingsCommitOnBlur(
    group: SettingsSaveGroup? = null,
    onEditingComplete: () -> Unit = {}
): Modifier = composed {
    val parent = LocalSettingsCommit.current
    val commit = if (group == null) parent else parent?.copy(group = group)
    val latestComplete by rememberUpdatedState(onEditingComplete)
    val latestCommit by rememberUpdatedState(commit)
    val token = remember { Any() }
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { state ->
        if (state.isFocused && !focused) {
            latestCommit?.queue?.editing(token) {
                latestComplete()
                latestCommit?.commit()
            }
        } else if (!state.isFocused && focused) {
            if (latestCommit == null) latestComplete()
            else latestCommit?.queue?.finishEditing(token)
        }
        focused = state.isFocused
    }
}
