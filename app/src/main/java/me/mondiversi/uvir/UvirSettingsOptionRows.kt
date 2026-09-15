package me.mondiversi.uvir

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SensorSourceOptionRow(
    mode: SensorConnectionMode,
    selected: Boolean,
    label: String,
    primaryText: Color,
    secondaryText: Color,
    enabled: Boolean = true,
    signalLevel: Int? = null,
    signalPercentage: Int? = null,
    onClick: () -> Unit
) {
    val darkMode = isSystemInDarkTheme()
    val disabledForeground = secondaryText.copy(alpha = 0.46f)
    val iconType =
        when (mode) {
            SensorConnectionMode.USB ->
                ConnectivityIconType.USB

            SensorConnectionMode.WIFI ->
                ConnectivityIconType.WIFI

            SensorConnectionMode.BLUETOOTH ->
                ConnectivityIconType.BLUETOOTH

            SensorConnectionMode.INTERNET ->
                ConnectivityIconType.INTERNET
        }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled,
                    onClick = onClick
                ),
        shape = RoundedCornerShape(10.dp),
        color =
            if (selected) {
                if (enabled) {
                    primaryText.copy(alpha = 0.08f)
                } else {
                    secondaryText.copy(alpha = 0.04f)
                }
            } else {
                Color.Transparent
            }
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 9.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            ConnectivitySectionIcon(
                type = iconType,
                modifier = Modifier.size(20.dp),
                tint = if (enabled) primaryText else disabledForeground
            )

            Spacer(Modifier.width(10.dp))

            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color =
                    if (enabled) primaryText else disabledForeground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            if (signalLevel != null && signalPercentage != null) {
                SignalStrengthBars(
                    level = signalLevel,
                    activeColor =
                        if (enabled) primaryText else disabledForeground,
                    inactiveColor =
                        secondaryText.copy(
                            alpha = if (enabled) 0.22f else 0.10f
                        )
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "$signalPercentage%",
                    color = if (enabled) primaryText else disabledForeground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(9.dp))
            }

            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides 0.dp
            ) {
                RadioButton(
                    selected = selected,
                    onClick = onClick,
                    enabled = enabled,
                    modifier = Modifier.size(24.dp),
                    colors =
                        RadioButtonDefaults.colors(
                            selectedColor =
                                MaterialTheme.colorScheme.primary,
                            unselectedColor = secondaryText,
                            disabledSelectedColor = disabledForeground,
                            disabledUnselectedColor = disabledForeground
                        )
                )
            }
        }
    }
}

@Composable
internal fun SignalStrengthBars(
    level: Int,
    activeColor: Color,
    inactiveColor: Color
) {
    Row(
        modifier = Modifier.size(width = 20.dp, height = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(5.dp, 8.dp, 11.dp, 14.dp)
            .forEachIndexed { index, height ->
                Box(
                    modifier =
                        Modifier
                            .width(3.dp)
                            .height(height)
                            .background(
                                color =
                                    if (index < level.coerceIn(0, 4)) {
                                        activeColor
                                    } else {
                                        inactiveColor
                                    },
                                shape = RoundedCornerShape(1.dp)
                            )
                )
            }
    }
}

@Composable
internal fun SettingsCheckboxWithDescription(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    description: String,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    primaryText: Color,
    secondaryText: Color,
    descriptionColor: Color = secondaryText,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(UvirSettingsRelatedGap)
    ) {
        CompositionLocalProvider(
            LocalContentColor provides
                if (enabled) primaryText
                else secondaryText.copy(alpha = 0.62f)
        ) {
            CheckSettingRow(
                checked = checked,
                onCheckedChange = onCheckedChange,
                title = title,
                emphasized = emphasized,
                compact = true,
                enabled = enabled,
                trailingContent = trailingContent
            )
        }

        Text(
            text = description,
            modifier =
                Modifier
                    .fillMaxWidth(),
            color =
                if (enabled) descriptionColor
                else descriptionColor.copy(alpha = 0.62f),
            fontSize = 11.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
internal fun LanguageOptionRow(
    language: AppLanguage,
    selected: Boolean,
    label: String,
    primaryText: Color,
    secondaryText: Color,
    onClick: () -> Unit
) {
    SettingsRadioChoiceRow(
        selected = selected,
        primaryText = primaryText,
        secondaryText = secondaryText,
        onClick = onClick
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = primaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.width(10.dp))

        if (language == AppLanguage.SYSTEM) {
            ConnectivitySectionIcon(
                type = ConnectivityIconType.LANGUAGE,
                modifier = Modifier.size(22.dp),
                tint = primaryText
            )
        } else {
            Surface(
                modifier = Modifier.size(22.dp),
                shape = RoundedCornerShape(6.dp),
                color =
                    primaryText.copy(alpha = 0.09f),
                contentColor = primaryText
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text =
                            when (language) {
                                AppLanguage.ITALIAN -> "IT"
                                AppLanguage.ENGLISH -> "EN"
                                AppLanguage.SPANISH -> "ES"
                                AppLanguage.FRENCH -> "FR"
                                AppLanguage.GERMAN -> "DE"
                                AppLanguage.GREEK -> "EL"
                                AppLanguage.PORTUGUESE -> "PT"
                                AppLanguage.HEBREW -> "HE"
                                AppLanguage.HINDI -> "HI"
                                AppLanguage.ARABIC -> "AR"
                                AppLanguage.PERSIAN -> "FA"
                                AppLanguage.RUSSIAN -> "RU"
                                AppLanguage.TURKISH -> "TR"
                                AppLanguage.CHINESE -> "ZH"
                                AppLanguage.JAPANESE -> "JA"
                                AppLanguage.KOREAN -> "KO"
                                AppLanguage.SWAHILI -> "SW"
                                AppLanguage.SYSTEM -> ""
                            },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
internal fun ThresholdAlertRadioRow(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    val darkMode = isSystemInDarkTheme()
    val rowTextColor =
        if (darkMode) Color.White
        else Color(0xFF101418)
    val rowSecondaryColor =
        if (darkMode) Color(0xFF90A4AE)
        else Color(0xFF546E7A)

    SettingsRadioChoiceRow(
        selected = selected,
        primaryText = rowTextColor,
        secondaryText = rowSecondaryColor,
        onClick = onClick
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = rowTextColor,
            fontSize = 14.sp
        )
    }
}
