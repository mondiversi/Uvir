package me.mondiversi.uvir

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.text.BidiFormatter

/** OSF is an oscillator-stop warning, not a battery voltage or charge measurement. */
@Composable
internal fun UvirRtcClockWarningDialog(
    deviceIds: List<String>,
    profiles: List<UvirSensorProfile>,
    cardColor: Color,
    primaryText: Color,
    onDismiss: () -> Unit,
    showWarnings: Boolean = true,
    onShowWarningsChange: (Boolean) -> Unit = {}
) {
    if (deviceIds.isEmpty()) return
    val resources = LocalResources.current
    val bidi = BidiFormatter.getInstance(resources.configuration.locales[0])
    val names = deviceIds.map { id ->
        bidi.unicodeWrap(uvirConnectionAnnouncementSensorName(id, profiles))
    }
    val single = names.size == 1
    UvirAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = null,
        modifier = Modifier.testTag("rtc_clock_warning_dialog"),
        title = {
            UvirClosableDialogTitle(
                title = stringResource(if (single) R.string.rtc_clock_warning_title_single
                    else R.string.rtc_clock_warning_title_multiple),
                onDismiss = onDismiss
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (single) {
                    Text(stringResource(R.string.rtc_clock_warning_message_single, names.single()))
                } else {
                    Text(stringResource(R.string.rtc_clock_warning_message_multiple))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        names.forEach { Text("• $it") }
                    }
                    Text(stringResource(R.string.rtc_clock_warning_batteries_guidance))
                }
                UvirDisplayCheckbox(
                    checked = showWarnings,
                    onCheckedChange = onShowWarningsChange,
                    title = stringResource(R.string.rtc_clock_warning_show_each_launch),
                    primaryText = primaryText
                )
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = primaryText
    )
}

@Composable
internal fun UvirRtcClockWarningHost(
    deviceIds: List<String>,
    profiles: List<UvirSensorProfile>,
    preferences: SharedPreferences,
    cardColor: Color,
    primaryText: Color,
    blocked: Boolean,
    onDismiss: (List<String>) -> Unit
) {
    val enabled by rememberUvirRtcBatteryWarning(preferences)
    var presenting by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(deviceIds, enabled, blocked) {
        if (deviceIds.isEmpty()) presenting = false
        else if (enabled && !blocked) presenting = true
    }
    // Keep the current popup open when opting out, so the checkbox can be
    // undone before closing. Suppression applies to subsequent presentations.
    if (presenting && deviceIds.isNotEmpty() && !blocked) {
        UvirRtcClockWarningDialog(
            deviceIds, profiles, cardColor, primaryText,
            onDismiss = {
                presenting = false
                onDismiss(deviceIds)
            },
            showWarnings = enabled,
            onShowWarningsChange = { setUvirRtcBatteryWarning(preferences, it) }
        )
    }
}
