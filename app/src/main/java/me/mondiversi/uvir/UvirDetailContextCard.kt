package me.mondiversi.uvir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun detailSensorDisplayName(profile: UvirSensorProfile?): String =
    profile?.let { it.displayName.ifBlank { it.hardwareUid }.ifBlank { "—" } } ?: "—"

@Composable
internal fun rememberDetailSensorName(sensorId: Long?, database: UvirDatabaseHelper): String {
    val revision by database.sensorProfileRevision.collectAsState()
    val name by produceState("—", sensorId, database, revision) {
        value = "—"
        value = withContext(Dispatchers.IO) {
            detailSensorDisplayName(sensorId?.let { database.findSensorProfile(it) })
        }
    }
    return name
}

@Composable
internal fun UvirDetailContextCard(
    automatic: Boolean?,
    sensorName: String,
    note: String,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(UvirIslandContentPadding),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(UvirDetailLeadingColumnWeight),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.acquisition_mode_label),
                    color = secondaryText,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                UvirDetailContextRow(
                    icon = null,
                    text = when (automatic) {
                        true -> stringResource(R.string.automatic_measurement)
                        false -> stringResource(R.string.manual_measurement)
                        null -> "—"
                    },
                    primaryText = primaryText
                )
            }
            Column(
                modifier = Modifier.weight(UvirDetailTrailingColumnWeight),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.note_and_sensor),
                    color = secondaryText,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                UvirDetailContextRow(
                    icon = UvirDetailMetadataIconKind.NOTE,
                    text = note,
                    primaryText = primaryText
                )
                UvirDetailContextRow(
                    icon = UvirDetailMetadataIconKind.SENSOR,
                    text = sensorName,
                    primaryText = primaryText,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun UvirDetailContextRow(
    icon: UvirDetailMetadataIconKind?,
    text: String,
    primaryText: Color,
    maxLines: Int = Int.MAX_VALUE
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let { UvirDetailMetadataIcon(kind = it, tint = primaryText) }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = primaryText,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Normal,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}
