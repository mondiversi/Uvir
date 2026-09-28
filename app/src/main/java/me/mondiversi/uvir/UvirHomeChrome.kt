package me.mondiversi.uvir

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

internal val UvirHomeHeaderIconSize = 24.dp
internal const val UvirHomeSensorSourceVisualScale = 0.8f
private const val UvirHomeStatusGlyphScale = 0.6f
// Status-line glyph is 60% of the previous 19.2dp connection glyph.
internal val UvirHomeStatusConnectionIconSize = UvirHomeHeaderIconSize * UvirHomeSensorSourceVisualScale * UvirHomeStatusGlyphScale
internal val UvirHomeHeaderActionSize = 40.dp
internal val UvirHomeHeaderActionGap = 8.dp
internal val UvirHomeMenuRecordActionWidth = 42.dp
internal val UvirHomeMenuGroupPadding = 3.dp
internal val UvirHomeMenuGroupGap = 3.dp
internal val UvirHomeMenuGap = 6.dp
internal const val UvirHomeHeaderIconStrokeScale = 1.28f
// Physical connection stroke at the home-header size.
internal val UvirHomeHeaderEffectiveStrokeWidth = UvirHomeHeaderIconSize *
    (0.08f * UvirHomeHeaderIconStrokeScale)

// Only the main-view Settings glyph is lighter; its canvas and button stay unchanged.
internal val UvirHomeSettingsStrokeWidth = UvirTitleActionIconStrokeWidth

internal fun uvirHomeStatusTextRes(
    useFakeSensorData: Boolean,
    sensorActivityInProgress: Boolean,
    statusIsLive: Boolean,
    statusIsInitializing: Boolean,
    statusIsSearching: Boolean
): Int =
    when {
        useFakeSensorData -> R.string.sensor_status_connected
        sensorActivityInProgress -> R.string.sensor_info_activity
        statusIsLive -> R.string.sensor_status_connected
        statusIsInitializing -> R.string.sensor_info_connecting
        statusIsSearching -> R.string.sensor_info_searching
        else -> R.string.sensor_status_no_sensor
    }

internal enum class UvirStatusDot(val glyph: String, val colorArgb: Int) {
    RED("🔴", 0xFFE53935.toInt()),
    YELLOW("🟡", 0xFFFFC107.toInt()),
    GREEN("🟢", 0xFF43A047.toInt()),
    DEBUG("🟠", 0xFFF57C00.toInt())
}

internal data class UvirStatusIndicator(
    val dot: UvirStatusDot,
    val pulses: Boolean
)

internal fun uvirStatusIndicator(
    useFakeSensorData: Boolean,
    connectionConfirmed: Boolean,
    connectionInitializing: Boolean,
    activityInProgress: Boolean
): UvirStatusIndicator =
    UvirStatusIndicator(
        dot = when {
            useFakeSensorData -> UvirStatusDot.DEBUG
            connectionConfirmed -> UvirStatusDot.GREEN
            connectionInitializing -> UvirStatusDot.YELLOW
            else -> UvirStatusDot.RED
        },
        pulses = activityInProgress || connectionInitializing
    )

/** Live states only: never restore connection colours from persisted settings. */
internal fun uvirSensorSelectionIndicators(
    selectedDeviceId: String,
    selectedIndicator: UvirStatusIndicator,
    otherIndicators: Map<String, UvirStatusIndicator> = emptyMap()
): Map<String, UvirStatusIndicator> {
    val indicators = otherIndicators.entries.associate { normalizeSensorDeviceId(it.key) to it.value }
    val selectedId = normalizeSensorDeviceId(selectedDeviceId)
    return if (selectedId.isBlank()) indicators else indicators + (selectedId to selectedIndicator)
}

@Composable
internal fun UvirHomeSensorSourceIcon(
    type: ConnectivityIconType,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = UvirHomeHeaderIconSize,
    strokeScale: Float = UvirHomeHeaderIconStrokeScale
) {
    val effectiveStrokeScale = maxOf(strokeScale, LocalUvirActionGlyphStrokeScale.current)
    ConnectivitySectionIcon(
        type = type,
        modifier = modifier.size(iconSize)
            .graphicsLayer {
                // Center the visible bounds inside the badge, not just the canvas.
                // These bounds mirror ConnectivitySectionIcon, including rounded caps
                // and USB's hollow terminals. No shared upward optical offset is needed.
                val stroke = maxOf(1.6.dp.toPx(), minOf(size.width, size.height) * 0.08f) *
                    effectiveStrokeScale
                val drawingCenterX = when (type) {
                    ConnectivityIconType.USB -> (size.width * (0.25f + 0.81f) - stroke * 1.6f) / 2f
                    ConnectivityIconType.BLUETOOTH -> size.width * 0.48f
                    else -> size.width / 2f
                }
                val drawingCenterY = when (type) {
                    ConnectivityIconType.USB -> (size.height * (0.08f + 0.83f) + stroke * 1.6f) / 2f
                    ConnectivityIconType.WIFI -> size.height * 0.49f + stroke * 0.175f
                    ConnectivityIconType.DEBUG -> size.height * 0.46f
                    else -> size.height / 2f
                }
                translationX = size.width / 2f - drawingCenterX
                translationY = size.height / 2f - drawingCenterY
            },
        strokeScale = strokeScale,
        tint = tint
    )
}

@Composable
internal fun UvirHomeSettingsIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    UvirMenuIcon(
        type = MenuIconType.ACQUISITION_PARAMETERS,
        modifier = modifier.size(UvirHomeHeaderIconSize),
        tint = tint,
        uniformStrokeWidth = UvirHomeSettingsStrokeWidth,
        settingsKnobScale = 0.8f
    )
}

@Composable
internal fun UvirHomeHeader(
    sensorConnectionMode: SensorConnectionMode,
    useFakeSensorData: Boolean,
    usbSensorStatus: UsbSensorConnectionStatus,
    usbAppConnectionConfirmed: Boolean,
    wirelessSensorStatus: WirelessSensorConnectionStatus,
    wirelessSensorMode: SensorConnectionMode?,
    wirelessAppConnectionConfirmed: Boolean,
    sensorActivityInProgress: Boolean,
    versionInfoDescription: String,
    primaryText: Color,
    secondaryText: Color,
    onOpenVersionInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    sensorSelectionExpanded: Boolean,
    onSensorSelectionExpandedChange: (Boolean) -> Unit,
    onActivityInProgressChanged: (Boolean) -> Unit = {},
    sensorDisplayName: String = "",
    sensorSelectionEnabled: Boolean = false,
    sensorProfiles: List<UvirSensorProfile> = emptyList(),
    sensorConnectionModes: Map<String, SensorConnectionMode> = emptyMap(),
    selectedSensorDeviceId: String = "",
    onSensorSelected: (String) -> Unit = {},
    onOpenSensorInfo: () -> Unit = {},
    onSensorConnectionModeChanged: (SensorConnectionMode) -> Unit = {},
    wifiEnabled: Boolean = true,
    bluetoothEnabled: Boolean = true,
    internetEnabled: Boolean = true,
    sensorSourceEnabled: Boolean = true,
    sensorStatusIndicators: Map<String, UvirStatusIndicator> = emptyMap(),
    sensorAlertMonitoringDeviceIds: Set<String> = emptySet(),
    sensorDialogColor: Color = MaterialTheme.colorScheme.surface
) {
    val versionInteractionSource = remember { MutableInteractionSource() }
    val selectedConnectionIsConnected =
        if (sensorConnectionMode == SensorConnectionMode.USB) {
            usbSensorStatus == UsbSensorConnectionStatus.CONNECTED
        } else {
            wirelessSensorStatus == WirelessSensorConnectionStatus.CONNECTED &&
                wirelessSensorMode == sensorConnectionMode
        }
    val selectedConnectionIsConfirmed =
        selectedConnectionIsConnected &&
            if (sensorConnectionMode == SensorConnectionMode.USB) {
                usbAppConnectionConfirmed
            } else {
                wirelessAppConnectionConfirmed
            }
    val statusIsLive =
        useFakeSensorData ||
            selectedConnectionIsConfirmed
    val statusIsInitializing =
        !statusIsLive &&
            if (sensorConnectionMode == SensorConnectionMode.USB) {
                usbSensorStatus == UsbSensorConnectionStatus.CONNECTED &&
                    !usbAppConnectionConfirmed
            } else {
                wirelessSensorMode == sensorConnectionMode &&
                    wirelessSensorStatus ==
                        WirelessSensorConnectionStatus.CONNECTED &&
                    !wirelessAppConnectionConfirmed
            }
    val statusIsSearching =
        !statusIsLive &&
            !statusIsInitializing &&
            sensorConnectionMode != SensorConnectionMode.USB &&
            wirelessSensorMode == sensorConnectionMode &&
            wirelessSensorStatus == WirelessSensorConnectionStatus.CONNECTING
    val statusTextRes =
        uvirHomeStatusTextRes(
            useFakeSensorData = useFakeSensorData,
            sensorActivityInProgress = sensorActivityInProgress,
            statusIsLive = statusIsLive,
            statusIsInitializing = statusIsInitializing,
            statusIsSearching = statusIsSearching
        )
    val currentOnActivityInProgressChanged by rememberUpdatedState(onActivityInProgressChanged)
    LaunchedEffect(sensorActivityInProgress) {
        currentOnActivityInProgressChanged(sensorActivityInProgress)
    }
    val statusIndicator =
        uvirStatusIndicator(
            useFakeSensorData = useFakeSensorData,
            connectionConfirmed = selectedConnectionIsConfirmed,
            connectionInitializing = statusIsInitializing,
            activityInProgress = sensorActivityInProgress
        )
    val statusIndicatorColor = Color(statusIndicator.dot.colorArgb)
    val statusPulse = rememberInfiniteTransition(label = "homeStatusPulse")
    val activityDotAlpha by statusPulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.50f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "homeStatusDotAlpha"
    )
    val statusDotPulses = statusIndicator.pulses
    // Show the user's current choice even offline; history must not hide a mode change.
    val displayedMode = sensorConnectionMode
    val connectionLabel = stringResource(uvirSensorConnectionLabelRes(displayedMode))

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
    ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .testTag("home_info_logo")
                    .clickable(
                        interactionSource = versionInteractionSource,
                        indication = null,
                        onClick = onOpenVersionInfo
                    )
                    .uvirAccessibleAction(
                        label = versionInfoDescription,
                        onClick = onOpenVersionInfo
                    )
            ) {
                Image(
                    painter = painterResource(R.drawable.uvir_logo),
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop
                )
                Text(
                text = BuildConfig.VERSION_NAME,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 1.dp).testTag("home_info_version"),
                color = Color.White,
                fontSize = 8.sp,
                lineHeight = 9.sp,
                maxLines = 1,
                softWrap = false
                )
            }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            UvirSensorSelector(
                sensorDisplayName = sensorDisplayName,
                sensorSelectionEnabled = sensorSelectionEnabled,
                sensorInfoEnabled = selectedConnectionIsConfirmed && !useFakeSensorData && selectedSensorDeviceId.isNotBlank(),
                sensorProfiles = sensorProfiles,
                sensorConnectionModes = sensorConnectionModes,
                sensorStatusIndicators = uvirSensorSelectionIndicators(
                    selectedSensorDeviceId, statusIndicator, sensorStatusIndicators),
                statusPulseAlpha = activityDotAlpha,
                alertMonitoringDeviceIds = sensorAlertMonitoringDeviceIds,
                dialogColor = sensorDialogColor,
                selectedConnectionMode = sensorConnectionMode,
                selectedSensorDeviceId = selectedSensorDeviceId,
                primaryText = primaryText,
                secondaryText = secondaryText,
                onOpenSensorInfo = onOpenSensorInfo,
                onSensorSelected = onSensorSelected,
                selectionExpanded = sensorSelectionExpanded,
                onSelectionExpandedChange = onSensorSelectionExpandedChange,
                onConnectionModeSelected = onSensorConnectionModeChanged,
                connectionSelectionEnabled = sensorSourceEnabled,
                wifiEnabled = wifiEnabled,
                bluetoothEnabled = bluetoothEnabled,
                internetEnabled = internetEnabled,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .testTag("home_sensor_status")
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = sensorSelectionEnabled,
                            onClick = { onSensorSelectionExpandedChange(true) }
                        )
                        .uvirNestedAccessibleAction(
                            label = stringResource(R.string.sensor_selection_title) + ": " +
                                sensorDisplayName.ifBlank { stringResource(R.string.sensor_no_selection) } + ", " + connectionLabel + ", " + stringResource(statusTextRes),
                            enabled = sensorSelectionEnabled,
                            onClick = { onSensorSelectionExpandedChange(true) }
                        ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UvirConnectionLabel(
                    text = stringResource(statusTextRes),
                    type = uvirSensorConnectionIconType(displayedMode),
                    iconColor = statusIndicatorColor,
                    textColor = secondaryText,
                    iconSize = UvirHomeStatusConnectionIconSize,
                    strokeScale = UvirHomeHeaderIconStrokeScale * UvirHomeStatusGlyphScale,
                    modifier = Modifier.weight(1f),
                    iconModifier = Modifier.testTag("home_sensor_glyph"),
                    textModifier = Modifier.testTag("home_sensor_status_text"),
                    iconAlpha = if (statusDotPulses) activityDotAlpha else 1f,
                    style = TextStyle(fontSize = 11.sp),
                    softWrap = false,
                )
            }
        }

        Spacer(Modifier.width(UvirHomeHeaderActionGap))
        val settingsIconColor = MaterialTheme.colorScheme.primary
        UvirHomeHeaderActionButton(
            contentDescription = stringResource(R.string.acquisition_parameters),
            onClick = onOpenSettings
        ) {
            UvirHomeSettingsIcon(tint = settingsIconColor)
        }
    }
    }
}

@Composable
internal fun UvirHomeHeaderActionButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badgeColor: Color? = null,
    visualScale: Float = 1f,
    content: @Composable () -> Unit
) {
    UvirAccessibleIconButton(
        contentDescription = contentDescription,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(UvirHomeHeaderActionSize),
        pressedVisualSize = if (badgeColor == null) UvirHomeHeaderActionSize else UvirActionIconBadgeSize,
        badgeColor = badgeColor,
        visualScale = visualScale
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
internal fun UvirHomeMenuBar(
    pinned: Boolean,
    viewMode: ViewMode,
    showChart: Boolean,
    monitoringAlertMetrics: Collection<ThresholdAlertMetric>,
    backgroundColor: Color,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    unreadAcquisitionCount: Int,
    unreadAlertCount: Int,
    acquisitionActivityInProgress: Boolean,
    alertActivityInProgress: Boolean,
    acquisitionSyncInProgress: Boolean,
    alertSyncInProgress: Boolean,
    onViewModeChanged: (ViewMode) -> Unit,
    onShowChartChanged: (Boolean) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAlertLog: () -> Unit
) {
    val sessionIndicatorColor =
        uvirSessionIndicatorColor(isSystemInDarkTheme())

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical =
                            if (pinned) UvirPinnedSelectorBottomSpacing else 0.dp
                    ),
            horizontalArrangement = Arrangement.spacedBy(UvirHomeMenuGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                MeasurementViewSelector(
                    selectedMode = viewMode,
                    onModeSelected = onViewModeChanged,
                    cardColor = cardColor,
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    irradianceAlerted =
                        monitoringAlertMetrics.any {
                            !it.isBiologicalEffect()
                        },
                    biologicalAlerted =
                        monitoringAlertMetrics.any {
                            it.isBiologicalEffect()
                        }
                )
            }

            Row(
                modifier = Modifier
                    .background(cardColor, RoundedCornerShape(14.dp))
                    .padding(UvirHomeMenuGroupPadding),
                horizontalArrangement = Arrangement.spacedBy(UvirHomeMenuGroupGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UvirHomeActionButton(
                    type = MenuIconType.SAVED_MEASUREMENTS,
                    contentDescription =
                        stringResource(R.string.saved_measurements),
                    cardColor = cardColor,
                    primaryText = primaryText,
                    badgeCount = unreadAcquisitionCount,
                    badgeColor = sessionIndicatorColor,
                    badgePulseEnabled = acquisitionActivityInProgress,
                    syncInProgress = acquisitionSyncInProgress,
                    modifier = Modifier.size(width = UvirHomeMenuRecordActionWidth, height = 40.dp),
                    onClick = onOpenHistory
                )
                UvirHomeActionButton(
                    type = MenuIconType.ALERT_LOG,
                    contentDescription =
                        stringResource(R.string.threshold_alert_log_title),
                    cardColor = cardColor,
                    primaryText = primaryText,
                    badgeCount = unreadAlertCount,
                    badgeColor = UvirAttentionColor,
                    badgePulseEnabled = alertActivityInProgress,
                    syncInProgress = alertSyncInProgress,
                    modifier = Modifier.size(width = UvirHomeMenuRecordActionWidth, height = 40.dp),
                    onClick = onOpenAlertLog
                )
            }

            MeasurementDataChartSelector(
                showChart = showChart,
                onShowChartChanged = onShowChartChanged,
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun UvirDisconnectedSensorIsland(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UvirIslandSpacing)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = cardColor,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal = 20.dp,
                        vertical = 22.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConnectivitySectionIcon(
                        type = ConnectivityIconType.DISCONNECTED,
                        modifier = Modifier.size(22.dp),
                        tint = primaryText
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.home_sensor_disconnected_title),
                        color = primaryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    text =
                        stringResource(
                            R.string.home_sensor_disconnected_description
                        ),
                    color = secondaryText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text =
                        stringResource(
                            R.string.home_sensor_disconnected_hint
                        ),
                    color = secondaryText,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
