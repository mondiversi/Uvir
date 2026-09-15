package me.mondiversi.uvir

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val UvirDetailLeadingColumnWeight = 0.35f
internal const val UvirDetailTrailingColumnWeight = 0.65f

@Composable
fun RecordIdentifier(
    label: String,
    secondaryText: Color,
    valueContent: @Composable () -> Unit
) {
    Column(
        modifier =
            Modifier.width(104.dp),
        verticalArrangement =
            Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = label,
            color = secondaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )

        valueContent()
    }
}

@Composable
fun SessionIdBadge(
    id: Long,
    textColor: Color,
    large: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null
) {
    val darkMode =
        isSystemInDarkTheme()

    val idText = id.toString()
    val description =
        "${stringResource(R.string.share_session_id_label)} $idText"

    val fontSize =
        if (large) {
            when {
                idText.length >= 7 -> 8.sp
                idText.length >= 5 -> 9.sp
                else -> 12.sp
            }
        } else {
            when {
                idText.length >= 7 -> 7.sp
                idText.length >= 5 -> 8.sp
                else -> 10.sp
            }
        }

    val badgeHeight =
        if (large) 28.dp else 22.dp

    Surface(
        modifier =
            Modifier
                .height(badgeHeight)
                .widthIn(min = badgeHeight)
                .semantics {
                    contentDescription = description
                },
        shape = RoundedCornerShape(50),
        color =
            containerColor ?: uvirSessionIndicatorColor(darkMode),
        contentColor =
            contentColor ?: if (containerColor == null) {
                uvirSessionContentColor()
            } else {
                textColor
            }
    ) {
        Box(
            modifier =
                Modifier.padding(
                    horizontal =
                        if (large) 6.dp else 5.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = idText,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AcquisitionIdBadge(
    id: Long,
    primaryText: Color,
    large: Boolean = false,
    descriptionLabelResource: Int =
        R.string.share_measurement_id_label
) {
    val idText = id.toString()
    val description =
        "${stringResource(descriptionLabelResource)} $idText"

    val fontSize =
        if (large) {
            when {
                idText.length >= 7 -> 8.sp
                idText.length >= 5 -> 9.sp
                else -> 12.sp
            }
        } else {
            when {
                idText.length >= 7 -> 7.sp
                idText.length >= 5 -> 8.sp
                else -> 10.sp
            }
        }

    val badgeHeight =
        if (large) 28.dp else 22.dp

    Surface(
        modifier =
            Modifier
                .height(badgeHeight)
                .widthIn(min = badgeHeight)
                .semantics {
                    contentDescription = description
                },
        shape = RoundedCornerShape(50),
        color = primaryText.copy(alpha = 0.08f),
        contentColor = primaryText
    ) {
        Box(
            modifier =
                Modifier.padding(
                    horizontal =
                        if (large) 6.dp else 5.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = idText,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun MissingIdentifierBadge(
    primaryText: Color,
    large: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null
) {
    val badgeHeight = if (large) 28.dp else 22.dp
    Surface(
        modifier = Modifier.size(badgeHeight),
        shape = RoundedCornerShape(50),
        color = containerColor ?: primaryText.copy(alpha = 0.08f),
        contentColor = contentColor ?: primaryText
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "—",
                fontSize = if (large) 11.sp else 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun UvirDetailIdentityCard(
    primaryId: Long?,
    sessionId: Long?,
    idLabel: String,
    dateLabel: String,
    dateText: String,
    endDateText: String? = null,
    durationText: String? = null,
    durationCount: Int? = null,
    durationCountKind: UvirDetailDurationCountKind? = null,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    primaryIdDescriptionLabelResource: Int =
        R.string.share_measurement_id_label,
    sessionContainerColor: Color? = null,
    sessionContentColor: Color? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(UvirIslandContentPadding),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(UvirDetailLeadingColumnWeight),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = idLabel,
                    color = secondaryText,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    primaryId?.let { id ->
                        AcquisitionIdBadge(
                            id = id,
                            primaryText = primaryText,
                            descriptionLabelResource =
                                primaryIdDescriptionLabelResource
                        )
                    } ?: MissingIdentifierBadge(primaryText)

                    Spacer(Modifier.width(5.dp))

                    sessionId?.let { id ->
                        SessionIdBadge(
                            id = id,
                            textColor = primaryText,
                            containerColor = sessionContainerColor,
                            contentColor = sessionContentColor
                        )
                    } ?: MissingIdentifierBadge(
                        primaryText = primaryText,
                        containerColor =
                            sessionContainerColor
                                ?: uvirSessionIndicatorColor(isSystemInDarkTheme()),
                        contentColor =
                            sessionContentColor ?: uvirSessionContentColor()
                    )
                }
            }

            Column(
                modifier = Modifier.weight(UvirDetailTrailingColumnWeight),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = dateLabel,
                    color = secondaryText,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                if (endDateText == null) {
                    UvirDetailDateTimeRow(
                        text = dateText,
                        primaryText = primaryText,
                        marker = UvirDetailMetadataIconKind.DATE
                    )
                } else {
                    UvirDetailDateTimeRow(
                        text = dateText,
                        primaryText = primaryText,
                        marker = UvirDetailMetadataIconKind.START
                    )
                    UvirDetailDateTimeRow(
                        text = endDateText,
                        primaryText = primaryText,
                        marker = UvirDetailMetadataIconKind.END
                    )
                    durationText?.let { text ->
                        if (durationCount != null && durationCountKind != null) {
                            UvirDetailDurationRow(
                                text = text,
                                count = durationCount,
                                countKind = durationCountKind,
                                primaryText = primaryText
                            )
                        } else {
                            UvirDetailDateTimeRow(
                                text = text,
                                primaryText = primaryText,
                                marker = UvirDetailMetadataIconKind.DURATION
                            )
                        }
                    }
                }
            }
        }
    }
}

internal enum class UvirDetailDurationCountKind {
    ACQUISITION,
    ALERT
}

@Composable
private fun UvirDetailDateTimeRow(
    text: String,
    primaryText: Color,
    marker: UvirDetailMetadataIconKind
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UvirDetailMetadataIcon(kind = marker, tint = primaryText)
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = primaryText,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun UvirDetailDurationRow(
    text: String,
    count: Int,
    countKind: UvirDetailDurationCountKind,
    primaryText: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UvirDetailMetadataIcon(
            kind = UvirDetailMetadataIconKind.DURATION,
            tint = primaryText
        )
        Text(
            text = text,
            color = primaryText,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "—",
            color = primaryText,
            fontSize = 12.sp,
            lineHeight = 15.sp
        )
        UvirDetailMetadataIcon(
            kind =
                when (countKind) {
                    UvirDetailDurationCountKind.ACQUISITION -> UvirDetailMetadataIconKind.ACQUISITION
                    UvirDetailDurationCountKind.ALERT -> UvirDetailMetadataIconKind.ALERT
                },
            tint = primaryText
        )
        Text(
            text = count.toString(),
            color = primaryText,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1
        )
    }
}


@Composable
fun AcquisitionTypeBadge(
    automatic: Boolean,
    primaryText: Color,
    compact: Boolean = false,
    large: Boolean = false
) {
    val description =
        stringResource(
            if (automatic) {
                R.string.automatic_badge_description
            } else {
                R.string.manual_measurement
            }
        )

    Surface(
        modifier =
            Modifier
                .size(
                    when {
                        large -> 28.dp
                        compact -> 22.dp
                        else -> 22.dp
                    }
                )
                .semantics {
                    contentDescription = description
                },
        shape = RoundedCornerShape(50),
        color = primaryText.copy(alpha = 0.08f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            AcquisitionTypeGlyph(
                automatic = automatic,
                modifier =
                    Modifier.size(
                        when {
                            large -> 15.dp
                            compact -> 12.dp
                            else -> 13.dp
                        }
                    ),
                tint = primaryText
            )
        }
    }
}

@Composable
private fun AcquisitionTypeGlyph(
    automatic: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Canvas(modifier = modifier) {
        val iconWidth = size.width
        val iconHeight = size.height

        // Keep the same stroke-to-size proportion as the automatic icon
        // on the main screen, without making these smaller glyphs heavy.
        val strokeWidth =
            maxOf(
                1.dp.toPx(),
                size.minDimension * 0.08f
            )

        if (automatic) {
            drawLine(
                color = tint,
                start = Offset(
                    iconWidth * 0.20f,
                    iconHeight * 0.84f
                ),
                end = Offset(
                    iconWidth * 0.50f,
                    iconHeight * 0.16f
                ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            drawLine(
                color = tint,
                start = Offset(
                    iconWidth * 0.50f,
                    iconHeight * 0.16f
                ),
                end = Offset(
                    iconWidth * 0.80f,
                    iconHeight * 0.84f
                ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            drawLine(
                color = tint,
                start = Offset(
                    iconWidth * 0.32f,
                    iconHeight * 0.58f
                ),
                end = Offset(
                    iconWidth * 0.68f,
                    iconHeight * 0.58f
                ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        } else {
            drawLine(
                color = tint,
                start = Offset(
                    iconWidth * 0.18f,
                    iconHeight * 0.84f
                ),
                end = Offset(
                    iconWidth * 0.18f,
                    iconHeight * 0.18f
                ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            drawLine(
                color = tint,
                start = Offset(
                    iconWidth * 0.18f,
                    iconHeight * 0.18f
                ),
                end = Offset(
                    iconWidth * 0.50f,
                    iconHeight * 0.56f
                ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            drawLine(
                color = tint,
                start = Offset(
                    iconWidth * 0.50f,
                    iconHeight * 0.56f
                ),
                end = Offset(
                    iconWidth * 0.82f,
                    iconHeight * 0.18f
                ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            drawLine(
                color = tint,
                start = Offset(
                    iconWidth * 0.82f,
                    iconHeight * 0.18f
                ),
                end = Offset(
                    iconWidth * 0.82f,
                    iconHeight * 0.84f
                ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

// =====================================================
// CARD RIORDINABILE/APRIBILE
// =====================================================

@Composable
internal fun ThresholdAlertBellButton(
    active: Boolean,
    onClick: () -> Unit,
    inactiveTint: Color
) {
    val description =
        stringResource(
            if (active) {
                R.string.threshold_bell_active
            } else {
                R.string.threshold_bell_configure
            }
        )
    val tint =
        if (active) {
            Color(0xFFF57C00)
        } else {
            inactiveTint
        }

    Box(
        modifier =
            Modifier
                .size(30.dp)
                .semantics {
                    contentDescription = description
                }
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(18.dp)
        ) {
            val strokeWidth =
                if (active) {
                    maxOf(
                        1.9.dp.toPx(),
                        size.minDimension * 0.105f
                    )
                } else {
                    maxOf(
                        1.5.dp.toPx(),
                        size.minDimension * 0.08f
                    )
                }

            drawArc(
                color = tint,
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(
                    size.width * 0.24f,
                    size.height * 0.17f
                ),
                size = Size(
                    size.width * 0.52f,
                    size.height * 0.62f
                ),
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )
            drawLine(
                color = tint,
                start = Offset(
                    size.width * 0.24f,
                    size.height * 0.57f
                ),
                end = Offset(
                    size.width * 0.15f,
                    size.height * 0.77f
                ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(
                    size.width * 0.76f,
                    size.height * 0.57f
                ),
                end = Offset(
                    size.width * 0.85f,
                    size.height * 0.77f
                ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(
                    size.width * 0.15f,
                    size.height * 0.77f
                ),
                end = Offset(
                    size.width * 0.85f,
                    size.height * 0.77f
                ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = tint,
                radius = strokeWidth,
                center = Offset(
                    size.width * 0.50f,
                    size.height * 0.89f
                )
            )
        }
    }
}

@Composable
internal fun Modifier.uvirLateralGlow(
    active: Boolean,
    color: Color
): Modifier {
    if (!active) {
        return this
    }

    val transition =
        rememberInfiniteTransition(
            label = "uvir-lateral-glow"
        )
    val glowAlpha by
        transition.animateFloat(
            initialValue = 0.07f,
            targetValue = 0.22f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(1100),
                    repeatMode = RepeatMode.Reverse
                ),
            label = "uvir-lateral-glow-alpha"
        )

    return drawBehind {
        drawRoundRect(
            brush =
                Brush.horizontalGradient(
                    colors =
                        listOf(
                            color.copy(
                                alpha = glowAlpha
                            ),
                            color.copy(
                                alpha = glowAlpha * 0.42f
                            ),
                            Color.Transparent
                        )
                ),
            cornerRadius =
                CornerRadius(
                    10.dp.toPx(),
                    10.dp.toPx()
                )
        )
    }
}
