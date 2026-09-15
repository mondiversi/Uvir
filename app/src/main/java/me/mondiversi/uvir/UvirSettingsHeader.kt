package me.mondiversi.uvir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
internal fun UvirSettingsHeader(
    title: String,
    onCollapseAll: () -> Unit
) {
    val collapseDescription =
        stringResource(R.string.collapse_all_settings_content_description)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        UvirMenuTitle(
            text = title,
            modifier = Modifier.weight(1f)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onCollapseAll,
                modifier =
                    Modifier
                        .size(UvirTitleActionButtonSize)
                        .semantics {
                            contentDescription = collapseDescription
                        }
            ) {
                UvirTitleCollapseAllIcon(
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
