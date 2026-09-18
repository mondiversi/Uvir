package me.mondiversi.uvir

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ExternalCommandSignal(
    val mark: String,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int
)

private val externalCommandSignals = listOf(
    ExternalCommandSignal(
        "1×",
        R.string.sensor_external_command_short,
        R.string.sensor_external_command_short_description
    ),
    ExternalCommandSignal(
        "3×",
        R.string.sensor_external_command_triple,
        R.string.sensor_external_command_triple_description
    ),
    ExternalCommandSignal(
        "2 s",
        R.string.sensor_external_command_long,
        R.string.sensor_external_command_long_description
    )
)

@Composable
internal fun UvirExternalCommandInfoDialog(
    primaryText: Color,
    secondaryText: Color,
    cardColor: Color,
    onDismissRequest: () -> Unit
) {
    val dialogScrollbar = rememberUvirDialogScrollbar(
        color = secondaryText.copy(alpha = 0.58f)
    )
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = dialogScrollbar.dialogModifier,
        title = { Text(stringResource(R.string.external_command)) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .then(dialogScrollbar.viewportModifier)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(dialogScrollbar.scrollState),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    externalCommandSignals.forEach { signal ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(11.dp),
                            color = primaryText.copy(alpha = 0.055f)
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 10.dp,
                                    vertical = 8.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = signal.mark,
                                    modifier = Modifier.width(38.dp),
                                    color = primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
                                        lineHeight = 14.sp
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
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = primaryText
    )
}
