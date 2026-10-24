package me.mondiversi.uvir

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal fun loadUvirMainIslandOrder(preferences: SharedPreferences): List<SensorGroup> =
    decodeUvirMainIslandOrder(runCatching { preferences.getString(KEY_MAIN_ISLAND_ORDER, null) }.getOrNull())

private class MainIslandOrderState(private val preferences: SharedPreferences) : MutableState<List<SensorGroup>> {
    private var current by mutableStateOf(loadUvirMainIslandOrder(preferences))
    override var value: List<SensorGroup>
        get() = current
        set(value) {
            val next = decodeUvirMainIslandOrder(value.joinToString(",") { it.name })
            if (next == current) return
            current = next
            preferences.edit().putString(KEY_MAIN_ISLAND_ORDER, next.joinToString(",") { it.name }).apply()
        }
    fun reload() { current = loadUvirMainIslandOrder(preferences) }
    override fun component1(): List<SensorGroup> = value
    override fun component2(): (List<SensorGroup>) -> Unit = { value = it }
}

/** App-level presentation only; never part of sensor settings, recordings or exports. */
@Composable
internal fun rememberUvirMainIslandOrder(): MutableState<List<SensorGroup>> {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val state = remember(preferences) { MainIslandOrderState(preferences) }
    DisposableEffect(preferences, state) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == KEY_MAIN_ISLAND_ORDER) state.reload()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

@Composable
internal fun UvirMainIslandMoveControls(
    group: SensorGroup,
    title: String,
    tint: Color,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    Column(Modifier.padding(horizontal = 4.dp).testTag("main_island_moves_${group.name}"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        for ((up, action) in listOf(true to onMoveUp, false to onMoveDown)) {
            val description = stringResource(
                if (up) R.string.main_island_move_up else R.string.main_island_move_down, title)
            Box(Modifier.size(width = 32.dp, height = 24.dp)
                .clip(CircleShape)
                .testTag("main_island_move_${if (up) "up" else "down"}_${group.name}")
                .semantics { contentDescription = description }
                .clickable(enabled = action != null, role = Role.Button) { action?.invoke() },
                contentAlignment = Alignment.Center) {
                ExpansionChevron(expanded = up, tint = if (action != null) tint else tint.copy(alpha = .3f), iconSize = 20.dp)
            }
        }
    }
}
