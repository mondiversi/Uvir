package me.mondiversi.uvir

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

internal val UvirSettingsChoiceSpacing = 2.dp
internal val UvirSettingsChoiceVerticalPadding = 6.dp
internal val UvirSettingsRelatedGap = 4.dp
internal val UvirSettingsControlGap = 8.dp
internal val UvirSettingsGroupGap = 12.dp

@Composable
internal fun SettingsChoiceGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(
                UvirSettingsChoiceSpacing
            ),
        content = content
    )
}

@Composable
internal fun SettingsRadioChoiceRow(
    selected: Boolean,
    primaryText: Color,
    secondaryText: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val commit = LocalSettingsCommit.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val click: () -> Unit = {
        if (enabled) {
            if (commit != null) focusManager.clearFocus()
            onClick()
            commit?.commit()
        }
    }
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = click),
        shape = RoundedCornerShape(10.dp),
        color =
            if (selected) {
                primaryText.copy(alpha = 0.08f)
            } else {
                Color.Transparent
            }
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical =
                        UvirSettingsChoiceVerticalPadding
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides 0.dp
            ) {
                RadioButton(
                    selected = selected,
                    onClick = click,
                    enabled = enabled,
                    modifier = Modifier.size(24.dp),
                    colors =
                        RadioButtonDefaults.colors(
                            selectedColor =
                                MaterialTheme.colorScheme.primary,
                            unselectedColor = secondaryText,
                            disabledSelectedColor = secondaryText.copy(alpha = 0.62f),
                            disabledUnselectedColor = secondaryText.copy(alpha = 0.62f)
                        )
                )
            }

            Spacer(Modifier.width(10.dp))
            content()
        }
    }
}

enum class UvirSettingsHeaderControl { CHEVRON, CHECKBOX }

enum class UvirSettingsPage {
    SENSOR_CONNECTION,
    SENSOR_PARAMETERS,
    SENSOR_CALIBRATION,
    SAMPLING,
    ALERTS,
    NUMERIC_FORMAT,
    LANGUAGE,
    DATA_RESTORE,
    DEBUG
}

internal data class UvirSettingsNavigation(
    val selectedPage: UvirSettingsPage?,
    val onOpenPage: (UvirSettingsPage) -> Unit
)

internal val LocalUvirSettingsNavigation =
    staticCompositionLocalOf<UvirSettingsNavigation?> { null }

@Composable
fun SettingsSection(
    title: String,
    containerColor: Color,
    titleColor: Color,
    dividerColor: Color,
    chevronColor: Color = titleColor,
    titleIcon: ConnectivityIconType? = null,
    titleIconContent: (@Composable (Color) -> Unit)? = null,
    expanded: Boolean = true,
    enabled: Boolean = true,
    dimContentWhenDisabled: Boolean = true,
    disabledNotice: (@Composable () -> Unit)? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    contentSpacing: Dp = UvirSettingsGroupGap,
    headerAccentColor: Color = MaterialTheme.colorScheme.primary,
    highlightExpandedHeader: Boolean = true,
    headerControl: UvirSettingsHeaderControl = UvirSettingsHeaderControl.CHEVRON,
    titleFontSize: TextUnit = 15.sp,
    settingsPage: UvirSettingsPage? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val navigation = LocalUvirSettingsNavigation.current
    if (settingsPage != null && navigation != null) {
        when (navigation.selectedPage) {
            null -> {
                UvirSettingsPageRow(
                    title = title,
                    containerColor = containerColor,
                    titleColor = titleColor,
                    chevronColor = chevronColor,
                    titleIcon = titleIcon,
                    titleIconContent = titleIconContent,
                    titleFontSize = titleFontSize,
                    onClick = { navigation.onOpenPage(settingsPage) }
                )
            }

            settingsPage -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = containerColor,
                    contentColor = titleColor
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(UvirIslandContentPadding),
                        verticalArrangement = Arrangement.spacedBy(contentSpacing)
                    ) {
                        if (!enabled) disabledNotice?.invoke()
                        CompositionLocalProvider(LocalUvirSettingsNavigation provides null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (enabled || !dimContentWhenDisabled) 1f else 0.48f),
                                verticalArrangement = Arrangement.spacedBy(contentSpacing),
                                content = content
                            )
                        }
                    }
                }
            }

            else -> Unit
        }
        return
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val bringIntoViewRequester =
        remember {
            BringIntoViewRequester()
        }
    val coroutineScope = rememberCoroutineScope()
    var pendingExpansionScroll by remember { mutableStateOf(false) }
    val changeExpanded: (Boolean) -> Unit = { opening ->
        if (onExpandedChange != null) {
            focusManager.clearFocus()
            pendingExpansionScroll = opening
            onExpandedChange(opening)
            if (opening) {
                coroutineScope.launch {
                    withFrameNanos { }
                    bringIntoViewRequester.bringIntoView()
                }
            }
        }
    }
    val darkTheme = isSystemInDarkTheme()
    val expandedAccentColor = headerAccentColor
    val displayedTitleColor =
        if (expanded && highlightExpandedHeader) expandedAccentColor else titleColor
    val displayedChevronColor =
        if (expanded && highlightExpandedHeader) expandedAccentColor else chevronColor
    val displayedHeaderColor =
        if (expanded && highlightExpandedHeader) {
            headerAccentColor.copy(
                alpha = if (darkTheme) 0.18f else 0.10f
            )
        } else {
            Color.Transparent
        }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(
                    bringIntoViewRequester
                ),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = displayedTitleColor
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(displayedHeaderColor)
                        .then(
                            if (onExpandedChange != null) {
                                // An unavailable settings group must still be
                                // inspectable: only its controls are disabled.
                                Modifier.clickable { changeExpanded(!expanded) }
                            } else {
                                Modifier
                            }
                        )
                        .padding(UvirIslandContentPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                titleIconContent?.let { iconContent ->
                    iconContent(displayedTitleColor)
                    Spacer(Modifier.width(8.dp))
                } ?: titleIcon?.let { icon ->
                    ConnectivitySectionIcon(
                        type = icon,
                        modifier = Modifier.size(20.dp),
                        tint = displayedTitleColor
                    )
                    Spacer(Modifier.width(8.dp))
                }

                if (headerControl == UvirSettingsHeaderControl.CHECKBOX && onExpandedChange != null) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Checkbox(checked = expanded, onCheckedChange = changeExpanded,
                            modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                }

                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    fontSize = titleFontSize,
                    fontWeight = if (headerControl == UvirSettingsHeaderControl.CHECKBOX && !expanded)
                        FontWeight.Normal else FontWeight.Bold,
                    color = displayedTitleColor
                )

                if (onExpandedChange != null && headerControl == UvirSettingsHeaderControl.CHEVRON) {
                    ExpansionChevron(
                        expanded = expanded,
                        tint = displayedChevronColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            UvirVerticalReveal(
                visible = expanded,
                onSettled = { fullyExpanded ->
                    if (fullyExpanded && pendingExpansionScroll) {
                        pendingExpansionScroll = false
                        coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                    }
                }
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = UvirIslandContentPadding,
                                top = contentSpacing,
                                end = UvirIslandContentPadding,
                                bottom = UvirIslandContentPadding
                            ),
                    verticalArrangement =
                        Arrangement.spacedBy(contentSpacing)
                ) {
                    if (!enabled) {
                        disabledNotice?.invoke()
                    }

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .alpha(
                                    if (
                                        enabled ||
                                        !dimContentWhenDisabled
                                    ) {
                                        1f
                                    } else {
                                        0.48f
                                    }
                                ),
                        verticalArrangement =
                            Arrangement.spacedBy(contentSpacing),
                        content = content
                    )
                }
            }
        }
    }
}

@Composable
private fun UvirSettingsPageRow(
    title: String,
    containerColor: Color,
    titleColor: Color,
    chevronColor: Color,
    titleIcon: ConnectivityIconType?,
    titleIconContent: (@Composable (Color) -> Unit)?,
    titleFontSize: TextUnit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = titleColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UvirIslandContentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            titleIconContent?.let { iconContent ->
                iconContent(titleColor)
                Spacer(Modifier.width(8.dp))
            } ?: titleIcon?.let { icon ->
                ConnectivitySectionIcon(
                    type = icon,
                    modifier = Modifier.size(20.dp),
                    tint = titleColor
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = titleFontSize,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                UvirDisclosureChevron(tint = chevronColor)
            }
        }
    }
}

@Composable
fun SettingsIsland(
    containerColor: Color,
    contentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier.padding(UvirIslandContentPadding),
            verticalArrangement =
                Arrangement.spacedBy(UvirSettingsGroupGap),
            content = content
        )
    }
}
