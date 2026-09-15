package me.mondiversi.uvir

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STATUS_BUZZER_TEST_CONNECTED_DURATION_MS = 280L
private const val STATUS_BUZZER_TEST_PAUSE_DURATION_MS = 800L
private const val STATUS_BUZZER_TEST_DISCONNECTED_DURATION_MS = 280L
private const val STATUS_BUZZER_TEST_ACTIVITY_STARTED_DURATION_MS = 340L
private const val STATUS_BUZZER_TEST_ACTIVITY_STOPPED_DURATION_MS = 340L
private const val STATUS_BUZZER_TEST_SAVED_DURATION_MS = 90L
private const val STATUS_BUZZER_TEST_ALERT_SAVED_DURATION_MS = 290L
private val STATUS_BUZZER_TEST_MINIMUM_FIRMWARE = listOf(0, 5, 74)

internal enum class StatusBuzzerGraphic {
    TWO_ASCENDING,
    TWO_DESCENDING,
    THREE_ASCENDING,
    THREE_DESCENDING,
    SHORT_BEEP,
    TRIPLE_BEEP
}

internal data class StatusBuzzerSignal(
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val graphic: StatusBuzzerGraphic,
    val testDurationMs: Long
)

internal val statusBuzzerSignals = listOf(
        StatusBuzzerSignal(
            R.string.sensor_buzzer_signal_startup,
            R.string.sensor_buzzer_signal_startup_description,
            StatusBuzzerGraphic.TWO_ASCENDING,
            STATUS_BUZZER_TEST_CONNECTED_DURATION_MS
        ),
        StatusBuzzerSignal(
            R.string.sensor_buzzer_signal_shutdown,
            R.string.sensor_buzzer_signal_shutdown_description,
            StatusBuzzerGraphic.TWO_DESCENDING,
            STATUS_BUZZER_TEST_DISCONNECTED_DURATION_MS
        ),
        StatusBuzzerSignal(
            R.string.sensor_buzzer_signal_activity_started,
            R.string.sensor_buzzer_signal_activity_started_description,
            StatusBuzzerGraphic.THREE_ASCENDING,
            STATUS_BUZZER_TEST_ACTIVITY_STARTED_DURATION_MS
        ),
        StatusBuzzerSignal(
            R.string.sensor_buzzer_signal_activity_stopped,
            R.string.sensor_buzzer_signal_activity_stopped_description,
            StatusBuzzerGraphic.THREE_DESCENDING,
            STATUS_BUZZER_TEST_ACTIVITY_STOPPED_DURATION_MS
        ),
        StatusBuzzerSignal(
            R.string.sensor_buzzer_signal_saved,
            R.string.sensor_buzzer_signal_saved_description,
            StatusBuzzerGraphic.SHORT_BEEP,
            STATUS_BUZZER_TEST_SAVED_DURATION_MS
        ),
        StatusBuzzerSignal(
            R.string.sensor_buzzer_signal_alert_saved,
            R.string.sensor_buzzer_signal_alert_saved_description,
            StatusBuzzerGraphic.TRIPLE_BEEP,
            STATUS_BUZZER_TEST_ALERT_SAVED_DURATION_MS
        )
    )

@Composable
internal fun UvirStatusBuzzerInfoDialog(
    primaryText: Color,
    secondaryText: Color,
    cardColor: Color,
    testEnabled: Boolean,
    onTestBuzzer: suspend () -> Boolean,
    onDismissRequest: () -> Unit
) {
    val dialogScrollbar =
        rememberUvirDialogScrollbar(
            color = secondaryText.copy(alpha = 0.58f)
        )
    val testScope = rememberCoroutineScope()
    var testInProgress by remember { mutableStateOf(false) }
    var activeTestSignalIndex by remember { mutableStateOf<Int?>(null) }
    val signals = statusBuzzerSignals

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = dialogScrollbar.dialogModifier,
        title = { Text(stringResource(R.string.sensor_buzzer_info_title)) },
        text = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .then(dialogScrollbar.viewportModifier)
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(dialogScrollbar.scrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    signals.forEachIndexed { index, signal ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(11.dp),
                            color =
                                uvirStatusLegendContainerColor(
                                    highlighted = activeTestSignalIndex == index,
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    primaryText = primaryText
                                )
                        ) {
                            Row(
                                modifier =
                                    Modifier.padding(
                                        horizontal = 10.dp,
                                        vertical = 8.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusBuzzerSignalGraphic(
                                    graphic = signal.graphic,
                                    color = primaryText.copy(alpha = 0.82f)
                                )
                                Spacer(Modifier.width(9.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
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
                                        lineHeight = 13.sp
                                    )
                                }
                            }
                        }
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
                            if (onTestBuzzer()) {
                                signals.forEachIndexed { index, signal ->
                                    activeTestSignalIndex = index
                                    delay(signal.testDurationMs)
                                    activeTestSignalIndex = null
                                    if (index < signals.lastIndex) {
                                        delay(STATUS_BUZZER_TEST_PAUSE_DURATION_MS)
                                    }
                                }
                            }
                        } finally {
                            activeTestSignalIndex = null
                            testInProgress = false
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.sensor_buzzer_test))
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = primaryText
    )
}

@Composable
private fun StatusBuzzerSignalGraphic(
    graphic: StatusBuzzerGraphic,
    color: Color
) {
    Canvas(modifier = Modifier.size(width = 35.dp, height = 25.dp)) {
        val lineWidth = 1.8.dp.toPx()
        when (graphic) {
            StatusBuzzerGraphic.SHORT_BEEP, StatusBuzzerGraphic.TRIPLE_BEEP -> {
                val count = if (graphic == StatusBuzzerGraphic.SHORT_BEEP) 1 else 3
                val spacing = 10.dp.toPx()
                val firstX = (size.width - (count - 1) * spacing) / 2f
                repeat(count) { index ->
                    drawCircle(
                        color = color,
                        radius = 2.6.dp.toPx(),
                        center = Offset(firstX + index * spacing, size.height / 2f)
                    )
                }
            }

            else -> {
                val ascending =
                    graphic == StatusBuzzerGraphic.TWO_ASCENDING ||
                        graphic == StatusBuzzerGraphic.THREE_ASCENDING
                val arrowCount =
                    if (
                        graphic == StatusBuzzerGraphic.TWO_ASCENDING ||
                        graphic == StatusBuzzerGraphic.TWO_DESCENDING
                    ) {
                        2
                    } else {
                        3
                    }
                val arrowWidth = 7.dp.toPx()
                val arrowHeight = 7.dp.toPx()
                val headSize = 3.2.dp.toPx()
                val spacing = 3.5.dp.toPx()
                val totalWidth = arrowCount * arrowWidth + (arrowCount - 1) * spacing
                val firstX = (size.width - totalWidth) / 2f

                repeat(arrowCount) { index ->
                    val startX = firstX + index * (arrowWidth + spacing)
                    val endX = startX + arrowWidth
                    val startY =
                        if (ascending) {
                            size.height / 2f + arrowHeight / 2f
                        } else {
                            size.height / 2f - arrowHeight / 2f
                        }
                    val endY =
                        if (ascending) {
                            startY - arrowHeight
                        } else {
                            startY + arrowHeight
                        }
                    drawLine(
                        color = color,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = lineWidth,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = color,
                        start = Offset(endX, endY),
                        end = Offset(endX - headSize, endY),
                        strokeWidth = lineWidth,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = color,
                        start = Offset(endX, endY),
                        end =
                            Offset(
                                endX,
                                endY + if (ascending) headSize else -headSize
                            ),
                        strokeWidth = lineWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

internal fun firmwareSupportsStatusBuzzerTest(version: String): Boolean {
    val parts =
        version.substringBefore('-')
            .split('.')
            .mapNotNull(String::toIntOrNull)
    if (parts.size < 3) return false
    return (0..2)
        .map { parts.getOrElse(it) { 0 } }
        .zip(STATUS_BUZZER_TEST_MINIMUM_FIRMWARE)
        .firstOrNull { (actual, required) -> actual != required }
        ?.let { (actual, required) -> actual > required }
        ?: true
}
