package me.mondiversi.uvir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    externalCommand: Boolean = false,
    sensorName: String,
    note: String,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onEditNote: (() -> Unit)? = null
) {
    val editNoteDescription = stringResource(R.string.edit_note)
    val editNoteColor = MaterialTheme.colorScheme.primary
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
                    text = when {
                        externalCommand -> stringResource(R.string.external_measurement)
                        automatic == true -> stringResource(R.string.automatic_measurement)
                        automatic == false -> stringResource(R.string.manual_measurement)
                        else -> "—"
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
                    primaryText = primaryText,
                    onEditNote = onEditNote,
                    editNoteDescription = editNoteDescription,
                    editNoteColor = editNoteColor
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
    maxLines: Int = Int.MAX_VALUE,
    onEditNote: (() -> Unit)? = null,
    editNoteDescription: String = "",
    editNoteColor: Color = primaryText
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
        onEditNote?.let { editNote ->
            Canvas(
                modifier =
                    Modifier
                        .size(18.dp)
                        .semantics {
                            contentDescription = editNoteDescription
                        }
                        .clickable(onClick = editNote)
            ) {
                val sx = size.width / 24f
                val sy = size.height / 24f
                val pencil = Path().apply {
                    moveTo(3f * sx, 17.25f * sy)
                    lineTo(3f * sx, 21f * sy)
                    lineTo(6.75f * sx, 21f * sy)
                    lineTo(17.81f * sx, 9.94f * sy)
                    lineTo(14.06f * sx, 6.19f * sy)
                    close()
                }
                val eraser = Path().apply {
                    moveTo(20.71f * sx, 7.04f * sy)
                    cubicTo(
                        21.10f * sx,
                        6.65f * sy,
                        21.10f * sx,
                        6.02f * sy,
                        20.71f * sx,
                        5.63f * sy
                    )
                    lineTo(18.37f * sx, 3.29f * sy)
                    cubicTo(
                        17.98f * sx,
                        2.90f * sy,
                        17.35f * sx,
                        2.90f * sy,
                        16.96f * sx,
                        3.29f * sy
                    )
                    lineTo(15.13f * sx, 5.12f * sy)
                    lineTo(18.88f * sx, 8.87f * sy)
                    close()
                }
                drawPath(path = pencil, color = editNoteColor)
                drawPath(path = eraser, color = editNoteColor)
            }
        }
    }
}
