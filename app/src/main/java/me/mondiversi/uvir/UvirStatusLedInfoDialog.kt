package me.mondiversi.uvir

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class StatusLedPattern {
    FIXED,
    BLINKING,
    TRIPLE_BLINKING
}

private const val STATUS_LED_BLINK_HALF_PERIOD_MS = 500
private const val STATUS_LED_BLINK_PERIOD_MS = STATUS_LED_BLINK_HALF_PERIOD_MS * 2
private const val STATUS_LED_TRIPLE_BLINK_PERIOD_MS = 4_000
private const val STATUS_LED_TEST_RED_FIXED_DURATION_MS = 1_500L
private const val STATUS_LED_TEST_RED_BLINK_DURATION_MS = 3_000L
private const val STATUS_LED_TEST_YELLOW_DURATION_MS = 3_000L
private const val STATUS_LED_TEST_GREEN_FIXED_DURATION_MS = 1_500L
private const val STATUS_LED_TEST_GREEN_BLINK_DURATION_MS = 3_000L
private const val STATUS_LED_TEST_BLUE_FIXED_DURATION_MS = 1_500L
private const val STATUS_LED_TEST_BLUE_BLINK_DURATION_MS = 3_000L
private val STATUS_LED_TEST_MINIMUM_FIRMWARE = listOf(0, 5, 39)

private data class StatusLedSignal(
    val color: Color,
    val pattern: StatusLedPattern,
    val title: Int,
    val description: Int
)

@Composable
internal fun UvirStatusLedInfoDialog(
    primaryText: Color,
    secondaryText: Color,
    cardColor: Color,
    testEnabled: Boolean,
    onTestLed: suspend () -> Boolean,
    onDismissRequest: () -> Unit
) {
    val green = Color(0xFF00C853)
    val yellow = Color(0xFFFFC107)
    val blue = Color(0xFF2979FF)
    val dialogScrollbar =
        rememberUvirDialogScrollbar(
            color = secondaryText.copy(alpha = 0.58f)
        )
    val testScope = rememberCoroutineScope()
    var testInProgress by remember { mutableStateOf(false) }
    var activeTestSignalIndex by remember { mutableStateOf<Int?>(null) }
    val signals =
        listOf(
            StatusLedSignal(
                Color(0xFFE53935),
                StatusLedPattern.FIXED,
                R.string.sensor_led_signal_disconnected,
                R.string.sensor_led_signal_disconnected_description
            ),
            StatusLedSignal(
                Color(0xFFE53935),
                StatusLedPattern.BLINKING,
                R.string.sensor_led_signal_pending_disconnected,
                R.string.sensor_led_signal_pending_disconnected_description
            ),
            StatusLedSignal(
                yellow,
                StatusLedPattern.BLINKING,
                R.string.sensor_led_signal_connecting,
                R.string.sensor_led_signal_connecting_description
            ),
            StatusLedSignal(
                green,
                StatusLedPattern.FIXED,
                R.string.sensor_led_signal_connected,
                R.string.sensor_led_signal_connected_description
            ),
            StatusLedSignal(
                green,
                StatusLedPattern.BLINKING,
                R.string.sensor_led_signal_pending_connected,
                R.string.sensor_led_signal_pending_connected_description
            ),
            StatusLedSignal(
                blue,
                StatusLedPattern.FIXED,
                R.string.sensor_led_signal_operation_active,
                R.string.sensor_led_signal_operation_active_description
            ),
            StatusLedSignal(
                blue,
                StatusLedPattern.TRIPLE_BLINKING,
                R.string.sensor_led_signal_recorded,
                R.string.sensor_led_signal_recorded_description
            )
        )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = dialogScrollbar.dialogModifier,
        title = {
            Text(stringResource(R.string.sensor_led_info_title))
        },
        text = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 490.dp)
                        .then(dialogScrollbar.viewportModifier)
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(dialogScrollbar.scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    signals.forEachIndexed { index, signal ->
                        StatusLedSignalRow(
                            signal = signal,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            highlighted = activeTestSignalIndex == index,
                            indicatorActive =
                                !testInProgress || activeTestSignalIndex == index
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.close))
            }
        },
        dismissButton = {
            TextButton(
                enabled = testEnabled && !testInProgress,
                onClick = {
                    testInProgress = true
                    testScope.launch {
                        try {
                            if (onTestLed()) {
                                val stageDurations =
                                    listOf(
                                        STATUS_LED_TEST_RED_FIXED_DURATION_MS,
                                        STATUS_LED_TEST_RED_BLINK_DURATION_MS,
                                        STATUS_LED_TEST_YELLOW_DURATION_MS,
                                        STATUS_LED_TEST_GREEN_FIXED_DURATION_MS,
                                        STATUS_LED_TEST_GREEN_BLINK_DURATION_MS,
                                        STATUS_LED_TEST_BLUE_FIXED_DURATION_MS,
                                        STATUS_LED_TEST_BLUE_BLINK_DURATION_MS
                                    )
                                stageDurations.forEachIndexed { index, duration ->
                                    activeTestSignalIndex = index
                                    delay(duration)
                                }
                            }
                        } finally {
                            activeTestSignalIndex = null
                            testInProgress = false
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.sensor_led_test))
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = primaryText
    )
}

internal fun firmwareSupportsStatusLedTest(version: String): Boolean {
    val parts =
        version.substringBefore('-')
            .split('.')
            .mapNotNull(String::toIntOrNull)
    if (parts.size < 3) return false
    return (0..2)
        .map { parts.getOrElse(it) { 0 } }
        .zip(STATUS_LED_TEST_MINIMUM_FIRMWARE)
        .firstOrNull { (actual, required) -> actual != required }
        ?.let { (actual, required) -> actual > required }
        ?: true
}

@Composable
private fun StatusLedSignalRow(
    signal: StatusLedSignal,
    primaryText: Color,
    secondaryText: Color,
    highlighted: Boolean,
    indicatorActive: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        color =
            uvirStatusLegendContainerColor(
                highlighted = highlighted,
                accentColor = signal.color,
                primaryText = primaryText
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(46.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedStatusLed(
                    color = signal.color,
                    pattern = signal.pattern,
                    active = indicatorActive
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(signal.title),
                    color = primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(signal.description),
                    color = secondaryText,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2
                )
            }
            Spacer(Modifier.width(10.dp))
        }
    }
}

@Composable
private fun AnimatedStatusLed(
    color: Color,
    pattern: StatusLedPattern,
    active: Boolean
) {
    if (!active) {
        Box(
            modifier =
                Modifier
                    .size(20.dp)
                    .background(
                        color = color.copy(alpha = 0.14f),
                        shape = CircleShape
                    )
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "status-led-signal")
    val ledAlpha by
        when (pattern) {
            StatusLedPattern.FIXED ->
                transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1000)),
                    label = "fixed-led"
                )

            StatusLedPattern.BLINKING ->
                transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.14f,
                    animationSpec =
                        infiniteRepeatable(
                            keyframes {
                                durationMillis = STATUS_LED_BLINK_PERIOD_MS
                                1f at 0
                                1f at STATUS_LED_BLINK_HALF_PERIOD_MS - 1
                                0.14f at STATUS_LED_BLINK_HALF_PERIOD_MS
                                0.14f at STATUS_LED_BLINK_PERIOD_MS - 1
                            }
                        ),
                    label = "blinking-led"
                )

            StatusLedPattern.TRIPLE_BLINKING ->
                transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.14f,
                    animationSpec =
                        infiniteRepeatable(
                            keyframes {
                                durationMillis = STATUS_LED_TRIPLE_BLINK_PERIOD_MS
                                1f at 0
                                1f at 499
                                0.14f at 500
                                0.14f at 999
                                1f at 1000
                                1f at 1499
                                0.14f at 1500
                                0.14f at 1999
                                1f at 2000
                                1f at 2499
                                0.14f at 2500
                                0.14f at STATUS_LED_TRIPLE_BLINK_PERIOD_MS - 1
                            }
                        ),
                    label = "triple-blinking-led"
                )
        }

    Box(
        modifier =
            Modifier
                .size(20.dp)
                .alpha(ledAlpha)
                .background(color = color, shape = CircleShape)
    )
}
