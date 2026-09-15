package me.mondiversi.uvir

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val UvirHomeHeaderIconSize = 24.dp
private val UvirHomeSourceColumnWidth = 28.dp
private const val UvirHomeHeaderIconStrokeScale = 1.6f
private const val UvirHomeSensorSourceIconScale = 0.8f
// Physical connection stroke after its existing 80% drawing scale.
internal val UvirHomeHeaderEffectiveStrokeWidth = UvirHomeHeaderIconSize *
    (0.08f * UvirHomeHeaderIconStrokeScale * UvirHomeSensorSourceIconScale)

// Only the main-view Settings glyph is lighter; its canvas and button stay unchanged.
internal val UvirHomeSettingsStrokeWidth = UvirTitleActionIconStrokeWidth

@Composable
internal fun UvirHomeSensorSourceIcon(
    type: ConnectivityIconType,
    tint: Color,
    modifier: Modifier = Modifier
) {
    ConnectivitySectionIcon(
        type = type,
        modifier = modifier.size(UvirHomeHeaderIconSize).graphicsLayer {
            // Center the visible drawing, including its stroke, rather than only its canvas.
            val strokeFraction = 0.08f * UvirHomeHeaderIconStrokeScale
            val drawingCenterY = when (type) {
                ConnectivityIconType.USB -> (0.08f + 0.83f + strokeFraction * 1.35f) / 2f
                ConnectivityIconType.DEBUG -> (0.10f + 0.82f) / 2f
                else -> 0.5f
            }
            scaleX = UvirHomeSensorSourceIconScale
            scaleY = UvirHomeSensorSourceIconScale
            translationY = (0.5f - drawingCenterY) * size.height * UvirHomeSensorSourceIconScale
        },
        strokeScale = UvirHomeHeaderIconStrokeScale,
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
    onOpenSensorSource: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val versionInteractionSource = remember { MutableInteractionSource() }
    val selectedSourceLabel =
        stringResource(
            when (sensorConnectionMode) {
                SensorConnectionMode.USB ->
                    R.string.sensor_connection_usb

                SensorConnectionMode.WIFI ->
                    R.string.sensor_connection_wifi

                SensorConnectionMode.BLUETOOTH ->
                    R.string.sensor_connection_bluetooth

                SensorConnectionMode.INTERNET ->
                    R.string.sensor_connection_internet
            }
        )
    val sensorSourceDescription =
        if (useFakeSensorData) {
            stringResource(R.string.sensor_source_debug_accessibility)
        } else {
            stringResource(
                R.string.sensor_source_accessibility,
                selectedSourceLabel
            )
        }
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
    val debugColor = Color(0xFFF57C00)
    val connectingColor = Color(0xFFFFC107)
    val statusIndicatorColor =
        when {
            useFakeSensorData -> debugColor
            selectedConnectionIsConfirmed && sensorActivityInProgress -> Color(0xFF2979FF)
            selectedConnectionIsConfirmed -> Color(0xFF43A047)
            statusIsInitializing -> connectingColor
            else -> Color(0xFFE53935)
        }
    val sourceInteractionSource = remember { MutableInteractionSource() }
    val sourceIconType =
        if (useFakeSensorData) {
            ConnectivityIconType.DEBUG
        } else {
            when (sensorConnectionMode) {
                SensorConnectionMode.USB -> ConnectivityIconType.USB
                SensorConnectionMode.WIFI -> ConnectivityIconType.WIFI
                SensorConnectionMode.BLUETOOTH -> ConnectivityIconType.BLUETOOTH
                SensorConnectionMode.INTERNET -> ConnectivityIconType.INTERNET
            }
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier =
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .testTag("home_info_logo")
                    .semantics {
                        contentDescription = versionInfoDescription
                    }
                    .clickable(
                        interactionSource = versionInteractionSource,
                        indication = null,
                        onClick = onOpenVersionInfo
                    )
        ) {
            Image(
                painter = painterResource(R.drawable.uvir_logo),
                modifier = Modifier.fillMaxSize(),
                contentDescription = null,
                contentScale = ContentScale.Crop
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Uvir",
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = versionInteractionSource,
                                indication = null,
                                onClickLabel = versionInfoDescription,
                                onClick = onOpenVersionInfo
                            )
                            .padding(end = 4.dp, bottom = 1.dp),
                    color = primaryText,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = BuildConfig.VERSION_NAME,
                    modifier = Modifier
                        .testTag("home_info_version")
                        .clickable(
                            interactionSource = versionInteractionSource,
                            indication = null,
                            onClickLabel = versionInfoDescription,
                            onClick = onOpenVersionInfo
                        )
                        .padding(start = 3.dp),
                    color = secondaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .testTag("home_status_dot")
                        .background(statusIndicatorColor, RoundedCornerShape(50))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text =
                        stringResource(
                            when {
                                useFakeSensorData -> R.string.sensor_status_connected
                                statusIsLive && sensorActivityInProgress ->
                                    R.string.sensor_info_activity
                                statusIsLive -> R.string.sensor_status_connected
                                statusIsInitializing -> R.string.sensor_info_connecting
                                statusIsSearching -> R.string.sensor_info_searching
                                else -> R.string.sensor_status_no_sensor
                            }
                        ),
                    modifier = Modifier.weight(1f),
                    color = secondaryText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(width = UvirHomeSourceColumnWidth, height = 42.dp)
                .semantics { contentDescription = sensorSourceDescription }
                .clickable(
                    interactionSource = sourceInteractionSource,
                    indication = null,
                    onClick = onOpenSensorSource
                ),
            contentAlignment = Alignment.Center
        ) {
            UvirHomeSensorSourceIcon(
                type = sourceIconType,
                tint = statusIndicatorColor
            )
        }
        Spacer(Modifier.width(3.dp))
        UvirHomeHeaderActionButton(
            contentDescription = stringResource(R.string.acquisition_parameters),
            primaryText = primaryText,
            onClick = onOpenSettings
        ) {
            UvirHomeSettingsIcon(tint = primaryText)
        }
    }
}

@Composable
private fun UvirHomeHeaderActionButton(
    contentDescription: String,
    primaryText: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(42.dp).semantics {
            this.contentDescription = contentDescription
        },
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        contentColor = primaryText
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
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
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
                    modifier = Modifier.size(width = 42.dp, height = 40.dp),
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
                    modifier = Modifier.size(width = 42.dp, height = 40.dp),
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
                Text(
                    text =
                        stringResource(
                            R.string.home_sensor_disconnected_title
                        ),
                    color = primaryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
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
