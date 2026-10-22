package me.mondiversi.uvir

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UvirExportBottomActions(
    enabled: Boolean,
    secondaryText: Color,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    CompositionLocalProvider(LocalUvirSettingsActionButtons provides true) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(UvirCompactActionButtonGap)
        ) {
            val buttonColors = uvirPrimaryOutlinedButtonColors()
            val buttonBorder = uvirPrimaryOutlinedButtonBorder(enabled, secondaryText)
            HorizontalDivider(color = secondaryText.copy(alpha = 0.22f))
            Spacer(Modifier.height(2.dp))
            OutlinedButton(
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors = buttonColors,
                border = buttonBorder
            ) {
                UvirLabeledButtonContent(
                    text = stringResource(R.string.save),
                    maxFontSize = 14.sp,
                    minFontSize = 10.sp
                ) {
                    UvirTitleSaveToFolderIcon(
                        modifier = Modifier.size(20.dp),
                        tint = LocalContentColor.current
                    )
                }
            }
            OutlinedButton(
                onClick = onShare,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors = buttonColors,
                border = buttonBorder
            ) {
                UvirLabeledButtonContent(
                    text = stringResource(R.string.share),
                    maxFontSize = 14.sp,
                    minFontSize = 10.sp
                ) {
                    UvirTitleActionIcon(
                        type = MenuIconType.SHARE,
                        modifier = Modifier.size(20.dp),
                        tint = LocalContentColor.current
                    )
                }
            }
        }
    }
}

@Composable
internal fun UvirExportFileCountText(
    format: UvirExportFileFormat,
    count: Int,
    secondaryText: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = stringResource(
            R.string.export_file_count,
            format.displayName,
            count.coerceAtLeast(0)
        ),
        modifier = modifier,
        color = secondaryText,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
}

@Composable
internal fun UvirExportFilesHeading(primaryText: Color) {
    Text(
        text = stringResource(R.string.data_export_files_heading),
        color = primaryText,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
internal fun UvirExportProfileSection(
    exportMode: UvirExportMode,
    onExportModeChange: (UvirExportMode) -> Unit,
    primaryText: Color,
    secondaryText: Color,
    showPreview: Boolean = false
) {
    Text(
        text = stringResource(R.string.export_profile_heading),
        color = primaryText,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold
    )
    UvirExportMode.entries.forEach { mode ->
        SettingsRadioChoiceRow(
            selected = exportMode == mode,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onClick = { onExportModeChange(mode) }
        ) {
            Text(
                text =
                    stringResource(
                        when (mode) {
                            UvirExportMode.SELECTED ->
                                R.string.export_format_selected
                            UvirExportMode.INTERNATIONAL ->
                                R.string.export_format_international
                        }
                    ),
                modifier = Modifier.weight(1f),
                color = primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    if (showPreview) {
        val context = LocalContext.current
        val preview = uvirExportFormatting(context, exportMode)
        val previewTimestamp = 1_789_499_742_000L
        val previewDateTime =
            "${formatUvirDateOnly(previewTimestamp, preview.dateFormat)} · " +
                formatUvirTimeOnly(previewTimestamp, preview.timeFormat)
        val previewTotal = preview.irradiance(1234.5678)
        val previewUva = preview.irradiance(892.1432)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = secondaryText.copy(alpha = 0.08f),
            contentColor = primaryText
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text =
                        "${preview.context.getString(R.string.share_acquisition_label)} #12",
                    color = primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = previewDateTime,
                    color = secondaryText,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
                HorizontalDivider(color = secondaryText.copy(alpha = 0.20f))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = preview.context.getString(R.string.uv_radiation),
                        modifier = Modifier.weight(1f),
                        color = primaryText,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "$previewTotal ${preview.irradianceUnit.symbol}",
                        color = primaryText,
                        fontSize = 12.sp
                    )
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "UVA",
                        modifier = Modifier.weight(1f),
                        color = primaryText,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "$previewUva ${preview.irradianceUnit.symbol}",
                        color = primaryText,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
