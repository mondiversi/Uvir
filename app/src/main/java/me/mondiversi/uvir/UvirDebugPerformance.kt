package me.mondiversi.uvir

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEBUG_PERFORMANCE_MINIMUM_FIRMWARE = "0.5.64"
private const val DEBUG_PERFORMANCE_COMPLETION_GRACE_MS = 1_000L
private const val DEBUG_SECRET_MELODY_HOLD_MS = 2_000L

internal enum class UvirDebugPerformance(
    val displayName: String,
    val protocolValue: String,
    val expectedDurationMs: Long
) {
    HAPPY_BIRTHDAY(
        displayName = "Happy Birthday",
        protocolValue = "HAPPY_BIRTHDAY",
        expectedDurationMs = 9_540L
    ),
    INDIANA_JONES(
        displayName = "Indiana Jones",
        protocolValue = "INDIANA_JONES",
        expectedDurationMs = 14_220L
    ),
    JURASSIC_PARK(
        displayName = "Jurassic Park",
        protocolValue = "JURASSIC_PARK",
        expectedDurationMs = 17_000L
    ),
    STAR_WARS(
        displayName = "Star Wars",
        protocolValue = "STAR_WARS",
        expectedDurationMs = 16_620L
    )
}

internal val UvirVisibleDebugPerformances =
    UvirDebugPerformance.entries.filter { it != UvirDebugPerformance.HAPPY_BIRTHDAY }

internal fun debugPerformanceCommand(performance: String): String =
    "DEBUG_PERFORMANCE ${performance.trim()}"

internal fun firmwareSupportsDebugPerformance(version: String): Boolean =
    compareFirmwareVersions(version, DEBUG_PERFORMANCE_MINIMUM_FIRMWARE) >= 0

@Composable
internal fun UvirDebugPerformanceContent(
    context: Context,
    sensorConnected: Boolean,
    enabled: Boolean,
    firmwareSupported: Boolean,
    completionToken: Long,
    primaryText: Color,
    secondaryText: Color,
    onStart: suspend (String) -> Boolean,
    onStop: suspend () -> Boolean
) {
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable {
        mutableStateOf(UvirDebugPerformance.INDIANA_JONES)
    }
    var playbackJob by remember { mutableStateOf<Job?>(null) }
    var observedCompletionToken by remember {
        mutableStateOf(completionToken)
    }
    val playing = playbackJob?.isActive == true
    val playbackEnabled = sensorConnected && enabled && firmwareSupported
    val selectorEnabled = playbackEnabled && !playing
    val visibleSelection =
        selected.takeIf { it in UvirVisibleDebugPerformances }
            ?: UvirDebugPerformance.INDIANA_JONES
    val secretInteractionSource = remember { MutableInteractionSource() }
    val secretHolding by secretInteractionSource.collectIsPressedAsState()
    val contentPrimaryText =
        if (sensorConnected) primaryText else secondaryText.copy(alpha = 0.58f)
    val contentSecondaryText =
        if (sensorConnected) secondaryText else secondaryText.copy(alpha = 0.46f)

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
    }

    fun startPlayback(performance: UvirDebugPerformance, secret: Boolean = false) {
        if (!selectorEnabled || playbackJob?.isActive == true) {
            return
        }
        playbackJob =
            scope.launch {
                var failed = false
                try {
                    val started =
                        withContext(Dispatchers.IO) {
                            onStart(performance.protocolValue)
                        }
                    if (started) {
                        if (secret) {
                            withContext(Dispatchers.Main) {
                                showUvirBottomMessage(
                                    context,
                                    context.getString(R.string.debug_secret_melody_found)
                                )
                            }
                        }
                        delay(
                            performance.expectedDurationMs +
                                DEBUG_PERFORMANCE_COMPLETION_GRACE_MS
                        )
                    } else {
                        failed = true
                    }
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) {
                        onStop()
                    }
                    playbackJob = null
                    if (failed) {
                        withContext(Dispatchers.Main) {
                            showUvirBottomMessage(
                                context,
                                context.getString(R.string.debug_performance_connection_lost)
                            )
                        }
                    }
                }
            }
    }

    val currentOnSecretMelody by rememberUpdatedState {
        startPlayback(UvirDebugPerformance.HAPPY_BIRTHDAY, secret = true)
    }
    LaunchedEffect(secretHolding, selectorEnabled) {
        if (secretHolding && selectorEnabled) {
            delay(DEBUG_SECRET_MELODY_HOLD_MS)
            currentOnSecretMelody()
        }
    }

    LaunchedEffect(playbackEnabled) {
        if (!playbackEnabled && playbackJob != null) {
            playbackJob?.cancel()
            playbackJob = null
        }
    }

    LaunchedEffect(completionToken) {
        if (completionToken != observedCompletionToken) {
            observedCompletionToken = completionToken
            playbackJob?.cancel()
            playbackJob = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playbackJob?.cancel()
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(UvirSettingsControlGap)
    ) {
        Text(
            text = stringResource(R.string.debug_performance_title),
            modifier =
                Modifier.clickable(
                    enabled = selectorEnabled,
                    interactionSource = secretInteractionSource,
                    indication = null,
                    // The secret melody requires a full hold; a short click does nothing.
                    onClick = {}
                ),
            color = contentPrimaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.debug_performance_description),
            color = contentSecondaryText,
            fontSize = 11.sp,
            lineHeight = 14.sp
        )

        UvirVisibleDebugPerformances.forEach { performance ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = selectorEnabled) {
                            selected = performance
                        },
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides 0.dp
                ) {
                    RadioButton(
                        selected = visibleSelection == performance,
                        onClick = { selected = performance },
                        enabled = selectorEnabled,
                        modifier = Modifier.size(24.dp),
                        colors =
                            RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = contentSecondaryText,
                                disabledSelectedColor = contentSecondaryText,
                                disabledUnselectedColor = contentSecondaryText
                            )
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = performance.displayName,
                    modifier = Modifier.weight(1f),
                    color = contentPrimaryText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Start,
                    maxLines = 1
                )
            }
        }

        if (sensorConnected && !firmwareSupported) {
            Text(
                text = stringResource(R.string.debug_performance_firmware_required),
                color = secondaryText,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (playing) {
                Button(
                    onClick = ::stopPlayback,
                    modifier = Modifier.weight(1f),
                    colors = uvirDestructiveButtonColors()
                ) {
                    UvirLabeledButtonContent(
                        text = stringResource(R.string.debug_performance_stop)
                    ) {
                        UvirButtonGlyphIcon(UvirButtonGlyph.STOP)
                    }
                }
            } else {
                Button(
                    enabled = playbackEnabled,
                    onClick = { startPlayback(visibleSelection) },
                    modifier = Modifier.weight(1f),
                    colors = uvirPrimaryButtonColors()
                ) {
                    UvirLabeledButtonContent(
                        text = stringResource(R.string.debug_performance_play)
                    ) {
                        UvirButtonGlyphIcon(UvirButtonGlyph.PLAY)
                    }
                }
            }
        }
    }
}
