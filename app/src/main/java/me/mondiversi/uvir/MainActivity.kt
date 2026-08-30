package me.mondiversi.uvir

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import java.io.File
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private lateinit var remoteServer:
            UvirRemoteServer

    private val remoteNetworkEnabledState =
        mutableStateOf(false)

    private val localNetworkPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts
                .RequestPermission()
        ) { granted ->
            applyRemoteNetworkEnabled(
                granted
            )

            if (!granted) {
                Toast.makeText(
                    this,
                    R.string.remote_permission_denied,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        remoteServer =
            UvirRemoteServer(
                applicationContext
            )

        val directAccessAllowed =
            hasLocalNetworkPermission()

        remoteNetworkEnabledState.value =
            directAccessAllowed

        remoteServer.start(
            directNetwork =
                directAccessAllowed
        )

        setContent {
            UvirApp(
                remoteNetworkEnabled =
                    remoteNetworkEnabledState.value
            )
        }

        if (!directAccessAllowed) {
            localNetworkPermissionLauncher.launch(
                Manifest.permission
                    .ACCESS_LOCAL_NETWORK
            )
        }
    }

    override fun onDestroy() {
        if (::remoteServer.isInitialized) {
            remoteServer.stop()
        }

        super.onDestroy()
    }

    private fun hasLocalNetworkPermission():
            Boolean {
        return Build.VERSION.SDK_INT < 37 ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission
                        .ACCESS_LOCAL_NETWORK
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun applyRemoteNetworkEnabled(
        enabled: Boolean
    ) {
        remoteNetworkEnabledState.value =
            enabled

        if (::remoteServer.isInitialized) {
            remoteServer.start(
                directNetwork = enabled
            )
        }
    }
}

enum class AppScreen {
    LIVE,
    HISTORY,
    DETAIL
}

private val UvirIslandSpacing = 9.dp

enum class MenuIconType {
    SAVED_MEASUREMENTS,
    AUTOMATIC_ACQUISITION,
    CONNECTIVITY,
    ACQUISITION_PARAMETERS,
    MEASUREMENT_DATE,
    SELECT,
    SHARE,
    DELETE,
    VERSION_INFO
}

@Composable
fun UvirMenuIcon(
    type: MenuIconType,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {

    Canvas(
        modifier =
            modifier.size(22.dp)
    ) {

        val iconWidth = size.width
        val iconHeight = size.height
        val strokeWidth =
            maxOf(
                1.5.dp.toPx(),
                size.minDimension * 0.075f
            )

        when (type) {

            MenuIconType.SAVED_MEASUREMENTS -> {

                drawRoundRect(
                    color = tint,
                    topLeft =
                        Offset(
                            iconWidth * 0.18f,
                            iconHeight * 0.10f
                        ),
                    size =
                        Size(
                            iconWidth * 0.64f,
                            iconHeight * 0.80f
                        ),
                    cornerRadius =
                        CornerRadius(
                            iconWidth * 0.08f,
                            iconWidth * 0.08f
                        ),
                    style =
                        Stroke(
                            width = strokeWidth
                        )
                )

                listOf(
                    0.35f,
                    0.53f,
                    0.71f
                ).forEach { yFraction ->

                    drawLine(
                        color = tint,
                        start =
                            Offset(
                                iconWidth * 0.31f,
                                iconHeight * yFraction
                            ),
                        end =
                            Offset(
                                iconWidth * 0.69f,
                                iconHeight * yFraction
                            ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            MenuIconType.AUTOMATIC_ACQUISITION -> {
                // A clear camera-style "A" for automatic mode.
                val automaticStrokeWidth =
                    2.2.dp.toPx()

                drawLine(
                    color = tint,
                    start =
                        Offset(
                            iconWidth * 0.20f,
                            iconHeight * 0.84f
                        ),
                    end =
                        Offset(
                            iconWidth * 0.50f,
                            iconHeight * 0.16f
                        ),
                    strokeWidth = automaticStrokeWidth,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = tint,
                    start =
                        Offset(
                            iconWidth * 0.50f,
                            iconHeight * 0.16f
                        ),
                    end =
                        Offset(
                            iconWidth * 0.80f,
                            iconHeight * 0.84f
                        ),
                    strokeWidth = automaticStrokeWidth,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = tint,
                    start =
                        Offset(
                            iconWidth * 0.32f,
                            iconHeight * 0.58f
                        ),
                    end =
                        Offset(
                            iconWidth * 0.68f,
                            iconHeight * 0.58f
                        ),
                    strokeWidth = automaticStrokeWidth,
                    cap = StrokeCap.Round
                )
            }

            MenuIconType.CONNECTIVITY -> {

                drawArc(
                    color = tint,
                    startAngle = 220f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft =
                        Offset(
                            iconWidth * 0.12f,
                            iconHeight * 0.10f
                        ),
                    size =
                        Size(
                            iconWidth * 0.76f,
                            iconHeight * 0.76f
                        ),
                    style =
                        Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round
                        )
                )

                drawArc(
                    color = tint,
                    startAngle = 220f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft =
                        Offset(
                            iconWidth * 0.28f,
                            iconHeight * 0.37f
                        ),
                    size =
                        Size(
                            iconWidth * 0.44f,
                            iconHeight * 0.44f
                        ),
                    style =
                        Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round
                        )
                )

                drawCircle(
                    color = tint,
                    radius = strokeWidth * 1.15f,
                    center =
                        Offset(
                            iconWidth * 0.50f,
                            iconHeight * 0.82f
                        )
                )
            }

            MenuIconType.ACQUISITION_PARAMETERS -> {

                val rows =
                    listOf(
                        0.25f to 0.36f,
                        0.50f to 0.66f,
                        0.75f to 0.44f
                    )

                rows.forEach {
                        (yFraction, knobFraction) ->

                    drawLine(
                        color = tint,
                        start =
                            Offset(
                                iconWidth * 0.12f,
                                iconHeight * yFraction
                            ),
                        end =
                            Offset(
                                iconWidth * 0.88f,
                                iconHeight * yFraction
                            ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )

                    drawCircle(
                        color = tint,
                        radius =
                            strokeWidth * 1.45f,
                        center =
                            Offset(
                                iconWidth * knobFraction,
                                iconHeight * yFraction
                            )
                    )
                }
            }

            MenuIconType.MEASUREMENT_DATE -> {

                drawRoundRect(
                    color = tint,
                    topLeft =
                        Offset(
                            iconWidth * 0.14f,
                            iconHeight * 0.19f
                        ),
                    size =
                        Size(
                            iconWidth * 0.72f,
                            iconHeight * 0.67f
                        ),
                    cornerRadius =
                        CornerRadius(
                            iconWidth * 0.08f,
                            iconWidth * 0.08f
                        ),
                    style =
                        Stroke(
                            width = strokeWidth
                        )
                )

                drawLine(
                    color = tint,
                    start =
                        Offset(
                            iconWidth * 0.14f,
                            iconHeight * 0.39f
                        ),
                    end =
                        Offset(
                            iconWidth * 0.86f,
                            iconHeight * 0.39f
                        ),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                listOf(
                    0.34f,
                    0.66f
                ).forEach { xFraction ->

                    drawLine(
                        color = tint,
                        start =
                            Offset(
                                iconWidth * xFraction,
                                iconHeight * 0.10f
                            ),
                        end =
                            Offset(
                                iconWidth * xFraction,
                                iconHeight * 0.28f
                            ),
                        strokeWidth =
                            strokeWidth * 1.10f,
                        cap = StrokeCap.Round
                    )
                }

                listOf(
                    0.33f to 0.55f,
                    0.52f to 0.55f,
                    0.71f to 0.55f,
                    0.33f to 0.72f,
                    0.52f to 0.72f
                ).forEach {
                        (xFraction, yFraction) ->

                    drawCircle(
                        color = tint,
                        radius =
                            strokeWidth * 0.65f,
                        center =
                            Offset(
                                iconWidth * xFraction,
                                iconHeight * yFraction
                            )
                    )
                }
            }

            MenuIconType.DELETE -> {

                drawLine(
                    color = tint,
                    start =
                        Offset(
                            iconWidth * 0.24f,
                            iconHeight * 0.29f
                        ),
                    end =
                        Offset(
                            iconWidth * 0.76f,
                            iconHeight * 0.29f
                        ),
                    strokeWidth =
                        strokeWidth * 1.15f,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = tint,
                    start =
                        Offset(
                            iconWidth * 0.41f,
                            iconHeight * 0.18f
                        ),
                    end =
                        Offset(
                            iconWidth * 0.59f,
                            iconHeight * 0.18f
                        ),
                    strokeWidth =
                        strokeWidth * 1.15f,
                    cap = StrokeCap.Round
                )

                drawRoundRect(
                    color = tint,
                    topLeft =
                        Offset(
                            iconWidth * 0.29f,
                            iconHeight * 0.36f
                        ),
                    size =
                        Size(
                            iconWidth * 0.42f,
                            iconHeight * 0.48f
                        ),
                    cornerRadius =
                        CornerRadius(
                            iconWidth * 0.06f,
                            iconWidth * 0.06f
                        ),
                    style =
                        Stroke(
                            width = strokeWidth
                        )
                )

                listOf(
                    0.43f,
                    0.57f
                ).forEach { xFraction ->

                    drawLine(
                        color = tint,
                        start =
                            Offset(
                                iconWidth * xFraction,
                                iconHeight * 0.47f
                            ),
                        end =
                            Offset(
                                iconWidth * xFraction,
                                iconHeight * 0.72f
                            ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            MenuIconType.SELECT -> {

                drawRoundRect(
                    color = tint,
                    topLeft =
                        Offset(
                            iconWidth * 0.14f,
                            iconHeight * 0.14f
                        ),
                    size =
                        Size(
                            iconWidth * 0.72f,
                            iconHeight * 0.72f
                        ),
                    cornerRadius =
                        CornerRadius(
                            iconWidth * 0.10f,
                            iconWidth * 0.10f
                        ),
                    style =
                        Stroke(
                            width = strokeWidth
                        )
                )

                drawLine(
                    color = tint,
                    start =
                        Offset(
                            iconWidth * 0.30f,
                            iconHeight * 0.51f
                        ),
                    end =
                        Offset(
                            iconWidth * 0.44f,
                            iconHeight * 0.66f
                        ),
                    strokeWidth =
                        strokeWidth * 1.15f,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = tint,
                    start =
                        Offset(
                            iconWidth * 0.44f,
                            iconHeight * 0.66f
                        ),
                    end =
                        Offset(
                            iconWidth * 0.72f,
                            iconHeight * 0.36f
                        ),
                    strokeWidth =
                        strokeWidth * 1.15f,
                    cap = StrokeCap.Round
                )
            }

            MenuIconType.SHARE -> {

                val leftCenter =
                    Offset(
                        iconWidth * 0.28f,
                        iconHeight * 0.50f
                    )
                val upperCenter =
                    Offset(
                        iconWidth * 0.70f,
                        iconHeight * 0.27f
                    )
                val lowerCenter =
                    Offset(
                        iconWidth * 0.70f,
                        iconHeight * 0.73f
                    )

                drawLine(
                    color = tint,
                    start = leftCenter,
                    end = upperCenter,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = tint,
                    start = leftCenter,
                    end = lowerCenter,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                listOf(
                    leftCenter,
                    upperCenter,
                    lowerCenter
                ).forEach { center ->
                    drawCircle(
                        color = tint,
                        radius =
                            size.minDimension * 0.105f,
                        center = center
                    )
                }
            }

            MenuIconType.VERSION_INFO -> {

                val center =
                    Offset(
                        iconWidth / 2f,
                        iconHeight / 2f
                    )

                drawCircle(
                    color = tint,
                    radius =
                        size.minDimension * 0.39f,
                    center = center,
                    style =
                        Stroke(
                            width = strokeWidth
                        )
                )

                drawCircle(
                    color = tint,
                    radius =
                        strokeWidth * 0.70f,
                    center =
                        Offset(
                            iconWidth * 0.50f,
                            iconHeight * 0.31f
                        )
                )

                drawLine(
                    color = tint,
                    start =
                        Offset(
                            iconWidth * 0.50f,
                            iconHeight * 0.45f
                        ),
                    end =
                        Offset(
                            iconWidth * 0.50f,
                            iconHeight * 0.70f
                        ),
                    strokeWidth =
                        strokeWidth * 1.10f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun CaptureMeasurementIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {

    Canvas(
        modifier =
            modifier.size(24.dp)
    ) {

        val strokeWidth =
            maxOf(
                1.7.dp.toPx(),
                size.minDimension * 0.072f
            )

        val center =
            Offset(
                size.width / 2f,
                size.height / 2f
            )

        val outerRadius =
            size.minDimension * 0.39f

        val innerRadius =
            size.minDimension * 0.13f

        fun pointAt(
            radius: Float,
            angleDegrees: Double
        ): Offset {

            val angle =
                Math.toRadians(
                    angleDegrees
                )

            return Offset(
                x =
                    center.x +
                            kotlin.math.cos(angle)
                                .toFloat() * radius,
                y =
                    center.y +
                            kotlin.math.sin(angle)
                                .toFloat() * radius
            )
        }

        drawCircle(
            color = tint,
            radius = outerRadius,
            center = center,
            style =
                Stroke(
                    width = strokeWidth
                )
        )

        val innerPoints =
            List(6) { index ->
                pointAt(
                    radius = innerRadius,
                    angleDegrees =
                        -60.0 + index * 60.0
                )
            }

        repeat(6) { index ->

            drawLine(
                color = tint,
                start =
                    pointAt(
                        radius = outerRadius,
                        angleDegrees =
                            -90.0 + index * 60.0
                    ),
                end =
                    innerPoints[index],
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            drawLine(
                color = tint,
                start =
                    innerPoints[index],
                end =
                    innerPoints[
                        (index + 1) % 6
                    ],
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun RepositoryIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Canvas(
        modifier = modifier.size(22.dp)
    ) {
        val strokeWidth =
            maxOf(
                1.5.dp.toPx(),
                size.minDimension * 0.075f
            )

        drawRoundRect(
            color = tint,
            topLeft =
                Offset(
                    size.width * 0.14f,
                    size.height * 0.10f
                ),
            size =
                Size(
                    size.width * 0.72f,
                    size.height * 0.80f
                ),
            cornerRadius =
                CornerRadius(
                    size.width * 0.08f,
                    size.width * 0.08f
                ),
            style =
                Stroke(width = strokeWidth)
        )

        val upper =
            Offset(
                size.width * 0.38f,
                size.height * 0.34f
            )
        val lower =
            Offset(
                size.width * 0.38f,
                size.height * 0.68f
            )
        val branch =
            Offset(
                size.width * 0.66f,
                size.height * 0.50f
            )

        drawLine(
            color = tint,
            start = upper,
            end = lower,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = upper,
            end = branch,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        listOf(
            upper,
            lower,
            branch
        ).forEach { center ->
            drawCircle(
                color = tint,
                radius = strokeWidth * 1.35f,
                center = center
            )
        }
    }
}

@Composable
fun UvirMenuTitle(
    text: String
) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun UvirBackButton(
    onClick: () -> Unit
) {
    val description =
        stringResource(
            R.string.navigate_back
        )

    val tint =
        MaterialTheme
            .colorScheme
            .primary

    IconButton(
        onClick = onClick,
        modifier =
            Modifier
                .size(44.dp)
                .semantics {
                    contentDescription =
                        description
                }
    ) {
        Canvas(
            modifier =
                Modifier.size(24.dp)
        ) {
            val strokeWidth =
                2.4.dp.toPx()

            val center =
                Offset(
                    x = size.width * 0.34f,
                    y = size.height * 0.50f
                )

            drawLine(
                color = tint,
                start =
                    Offset(
                        x = size.width * 0.68f,
                        y = size.height * 0.20f
                    ),
                end = center,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            drawLine(
                color = tint,
                start = center,
                end =
                    Offset(
                        x = size.width * 0.68f,
                        y = size.height * 0.80f
                    ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun Modifier.lazyScrollbarOverlay(
    state: LazyListState,
    color: Color
): Modifier =
    drawWithContent {
        drawContent()

        val layoutInfo =
            state.layoutInfo
        val visibleItems =
            layoutInfo.visibleItemsInfo
        val totalItems =
            layoutInfo.totalItemsCount

        if (
            visibleItems.isEmpty() ||
            (
                !state.canScrollBackward &&
                    !state.canScrollForward
                )
        ) {
            return@drawWithContent
        }

        val averageItemHeight =
            visibleItems
                .sumOf { it.size }
                .toFloat() /
                    visibleItems.size

        val viewportHeight =
            (
                    layoutInfo.viewportEndOffset -
                        layoutInfo.viewportStartOffset
                    ).toFloat()
                .coerceAtLeast(1f)

        val estimatedContentHeight =
            (averageItemHeight * totalItems)
                .coerceAtLeast(viewportHeight)

        val minThumbHeight =
            28.dp.toPx()
        val thumbHeight =
            (
                    size.height *
                        viewportHeight /
                        estimatedContentHeight
                    ).coerceIn(
                    minThumbHeight,
                    size.height
                )

        val estimatedScroll =
            state.firstVisibleItemIndex *
                averageItemHeight +
                state.firstVisibleItemScrollOffset

        val maxEstimatedScroll =
            (estimatedContentHeight - viewportHeight)
                .coerceAtLeast(1f)

        val scrollFraction =
            (estimatedScroll / maxEstimatedScroll)
                .coerceIn(0f, 1f)

        val barWidth =
            3.dp.toPx()
        val edgeInset =
            2.dp.toPx()
        val thumbY =
            (size.height - thumbHeight) *
                scrollFraction

        drawRoundRect(
            color = color,
            topLeft =
                Offset(
                    size.width - barWidth - edgeInset,
                    thumbY
                ),
            size =
                Size(
                    barWidth,
                    thumbHeight
                ),
            cornerRadius =
                CornerRadius(
                    barWidth,
                    barWidth
                )
        )
    }
        .pointerInput(state) {
            val touchWidth =
                16.dp.toPx()

            coroutineScope {
                var scrollJob: Job? =
                    null

                awaitEachGesture {
                    val down =
                        awaitFirstDown(
                            requireUnconsumed = false
                        )

                    if (
                        down.position.x <
                        size.width.toFloat() -
                            touchWidth
                    ) {
                        return@awaitEachGesture
                    }

                    down.consume()

                    fun requestScroll(
                        pointerY: Float
                    ) {
                        val totalItems =
                            state.layoutInfo
                                .totalItemsCount

                        if (totalItems <= 0) {
                            return
                        }

                        val scrollFraction =
                            (
                                pointerY /
                                    size.height
                                        .coerceAtLeast(1)
                                        .toFloat()
                                ).coerceIn(0f, 1f)

                        val targetIndex =
                            (
                                scrollFraction *
                                    (totalItems - 1)
                                ).roundToInt()
                                .coerceIn(
                                    0,
                                    totalItems - 1
                                )

                        scrollJob?.cancel()
                        scrollJob =
                            launch {
                                state.scrollToItem(
                                    targetIndex
                                )
                            }
                    }

                    requestScroll(
                        down.position.y
                    )

                    drag(down.id) { change ->
                        requestScroll(
                            change.position.y
                        )
                        change.consume()
                    }
                }
            }
        }

private fun Modifier.scrollbarOverlay(
    state: ScrollState,
    color: Color
): Modifier =
    drawWithContent {
        drawContent()

        if (state.maxValue <= 0) {
            return@drawWithContent
        }

        val totalContentHeight =
            size.height +
                state.maxValue.toFloat()
        val minThumbHeight =
            28.dp.toPx()
        val thumbHeight =
            (
                    size.height *
                        size.height /
                        totalContentHeight
                    ).coerceIn(
                    minThumbHeight,
                    size.height
                )

        val scrollFraction =
            state.value.toFloat() /
                state.maxValue.toFloat()
        val barWidth =
            3.dp.toPx()
        val edgeInset =
            2.dp.toPx()
        val thumbY =
            (size.height - thumbHeight) *
                scrollFraction

        drawRoundRect(
            color = color,
            topLeft =
                Offset(
                    size.width - barWidth - edgeInset,
                    thumbY
                ),
            size =
                Size(
                    barWidth,
                    thumbHeight
                ),
            cornerRadius =
                CornerRadius(
                    barWidth,
                    barWidth
                )
        )
    }
        .pointerInput(state) {
            val touchWidth =
                16.dp.toPx()

            coroutineScope {
                var scrollJob: Job? =
                    null

                awaitEachGesture {
                    val down =
                        awaitFirstDown(
                            requireUnconsumed = false
                        )

                    if (
                        down.position.x <
                        size.width.toFloat() -
                            touchWidth
                    ) {
                        return@awaitEachGesture
                    }

                    down.consume()

                    fun requestScroll(
                        pointerY: Float
                    ) {
                        if (state.maxValue <= 0) {
                            return
                        }

                        val scrollFraction =
                            (
                                pointerY /
                                    size.height
                                        .coerceAtLeast(1)
                                        .toFloat()
                                ).coerceIn(0f, 1f)

                        val targetValue =
                            (
                                scrollFraction *
                                    state.maxValue
                                ).roundToInt()
                                .coerceIn(
                                    0,
                                    state.maxValue
                                )

                        scrollJob?.cancel()
                        scrollJob =
                            launch {
                                state.scrollTo(
                                    targetValue
                                )
                            }
                    }

                    requestScroll(
                        down.position.y
                    )

                    drag(down.id) { change ->
                        requestScroll(
                            change.position.y
                        )
                        change.consume()
                    }
                }
            }
        }

@Composable
fun UvirOutlinedTextFieldColors(): TextFieldColors {

    val darkMode = isSystemInDarkTheme()

    val textColor =
        if (darkMode) Color(0xFFF5F7F8)
        else Color(0xFF101418)

    val labelColor =
        if (darkMode) Color(0xFFD5DEE3)
        else Color(0xFF546E7A)

    val borderColor =
        if (darkMode) Color(0xFF90A4AE)
        else Color(0xFF78909C)

    val accentColor =
        if (darkMode) Color(0xFFD0BCFF)
        else Color(0xFF6650A4)

    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        disabledTextColor =
            labelColor.copy(alpha = 0.65f),
        cursorColor = accentColor,
        focusedBorderColor = accentColor,
        unfocusedBorderColor = borderColor,
        disabledBorderColor =
            borderColor.copy(alpha = 0.55f),
        focusedLabelColor = textColor,
        unfocusedLabelColor = labelColor,
        disabledLabelColor =
            labelColor.copy(alpha = 0.65f),
        focusedPlaceholderColor = labelColor,
        unfocusedPlaceholderColor = labelColor,
        disabledPlaceholderColor =
            labelColor.copy(alpha = 0.65f),
        focusedSupportingTextColor = labelColor,
        unfocusedSupportingTextColor = labelColor,
        disabledSupportingTextColor =
            labelColor.copy(alpha = 0.65f)
    )
}

@Composable
fun UvirFullScreenPage(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    containerColor: Color,
    contentColor: Color,
    scrollState: ScrollState? = null,
    scrollbarColor: Color = Color.Unspecified,
    contentOverlay:
        @Composable BoxScope.() -> Unit = {},
    actionButton: (@Composable () -> Unit)? = null
) {

    BackHandler {
        onDismissRequest()
    }

    Scaffold(
        containerColor =
            containerColor,
        contentColor =
            contentColor,

        topBar = {

            Surface(
                color =
                    containerColor,
                contentColor =
                    contentColor
            ) {

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 10.dp,
                                vertical = 4.dp
                            ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    UvirBackButton(
                        onClick =
                            onDismissRequest
                    )

                    Box(
                        modifier =
                            Modifier.weight(1f),
                        contentAlignment =
                            Alignment.CenterStart
                    ) {
                        title()
                    }
                }
            }
        }
    ) { paddingValues ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(
                        top = 2.dp,
                        bottom = 20.dp
                    )
        ) {

            Box(
                modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(
                                if (scrollState != null) {
                                    Modifier.scrollbarOverlay(
                                        state = scrollState,
                                        color =
                                            if (
                                                scrollbarColor !=
                                                Color.Unspecified
                                            ) {
                                                scrollbarColor
                                            } else {
                                                contentColor.copy(
                                                    alpha = 0.46f
                                                )
                                            }
                                    )
                                } else {
                                    Modifier
                                }
                            )
            ) {

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                horizontal = 20.dp
                            )
                ) {
                    text()
                }

                contentOverlay()
            }

            if (actionButton != null) {

                Spacer(
                    Modifier.height(12.dp)
                )

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 20.dp
                            )
                ) {
                    actionButton()
                }
            }
        }
    }
}

enum class SensorGroup {
    UV,
    HEV_HEB,
    VISIBLE,
    NIR
}

enum class BiologicalEffectGroup {
    DNA_UV,
    UVA_PHOTOAGING,
    HEV_OXIDATIVE
}

enum class SensorConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

enum class ViewMode {
    IRRADIANCE,
    BIOLOGICAL_EFFECTS
}

data class AutomaticAcquisitionRequest(
    val intervalSeconds: Long,
    val note: String,
    val useStartDelay: Boolean,
    val startDelaySeconds: Long,
    val useDuration: Boolean,
    val durationSeconds: Long,
    val limitEnabled: Boolean,
    val maxAcquisitions: Int
)

data class AcquisitionParameters(
    val samplesPerMeasurement: Int,
    val sampleSpacingMs: Long,
    val discardExtremes: Boolean
)

internal fun formatAutomaticMeasurementNote(
    defaultNote: String,
    acquisitionNumber: Int
): String {
    val trimmedNote = defaultNote.trim()

    return if (trimmedNote.isBlank()) {
        "#$acquisitionNumber"
    } else {
        "$trimmedNote #$acquisitionNumber"
    }
}

internal fun automaticSessionNoteName(
    measurementNote: String,
    automaticSequence: Int?
): String {
    val trimmedNote = measurementNote.trim()
    val suffix =
        automaticSequence
            ?.let { " #$it" }
            .orEmpty()

    return if (
        suffix.isNotEmpty() &&
        trimmedNote.endsWith(suffix)
    ) {
        trimmedNote
            .removeSuffix(suffix)
            .trim()
    } else if (
        automaticSequence != null &&
        trimmedNote == "#${automaticSequence}"
    ) {
        ""
    } else {
        trimmedNote
    }
}

data class BiologicalEffectEstimate(
    val dnaUvProxy: Double,
    val dnaUvScore: Float,

    val uvaPhotoagingProxy: Double,
    val uvaPhotoagingScore: Float,

    val hevOxidativeProxy: Double,
    val hevOxidativeScore: Float
)

data class SensorSample(
    val uvc: Double = 0.0,
    val uvb: Double = 0.0,
    val uva: Double = 0.0,

    val violetto: Double = 0.0,
    val blu: Double = 0.0,
    val verde: Double = 0.0,
    val giallo: Double = 0.0,
    val arancione: Double = 0.0,
    val rosso: Double = 0.0,

    val f8: Double = 0.0,
    val nir: Double = 0.0
)

private const val SENSOR_SAMPLE_VALUE_COUNT = 11

private fun SensorSample.toSaveableValues(): List<Double> =
    listOf(
        uvc,
        uvb,
        uva,
        violetto,
        blu,
        verde,
        giallo,
        arancione,
        rosso,
        f8,
        nir
    )

private fun sensorSampleFromValues(
    values: List<Double>
): SensorSample =
    SensorSample(
        uvc = values[0],
        uvb = values[1],
        uva = values[2],
        violetto = values[3],
        blu = values[4],
        verde = values[5],
        giallo = values[6],
        arancione = values[7],
        rosso = values[8],
        f8 = values[9],
        nir = values[10]
    )

private val SensorSampleSaver =
    listSaver<SensorSample, Double>(
        save = {
            it.toSaveableValues()
        },
        restore = {
            sensorSampleFromValues(it)
        }
    )

private val SensorSampleListSaver =
    listSaver<List<SensorSample>, Double>(
        save = { samples ->
            samples.flatMap {
                it.toSaveableValues()
            }
        },
        restore = { values ->
            values
                .chunked(
                    SENSOR_SAMPLE_VALUE_COUNT
                )
                .filter {
                    it.size ==
                            SENSOR_SAMPLE_VALUE_COUNT
                }
                .map {
                    sensorSampleFromValues(it)
                }
        }
    )

data class SavedRecordSummary(
    val id: Long,
    val timestamp: Long,
    val note: String,
    val automatic: Boolean,
    val automaticSessionId: Long? = null,
    val automaticSequence: Int? = null
)

data class SavedRecordDetail(
    val id: Long,
    val timestamp: Long,
    val note: String,
    val automatic: Boolean,
    val sample: SensorSample,
    val automaticSessionId: Long? = null,
    val automaticSequence: Int? = null
)

// =====================================================
// PREFERENZE UI
// =====================================================

private const val PREFS_NAME = "uvir_preferences"
private const val BIOLOGICAL_MODEL_VERSION = "v1"

private const val KEY_AUTO_ENABLED = "auto_enabled"
private const val KEY_AUTO_INTERVAL_SECONDS = "auto_interval_seconds"
private const val KEY_AUTO_NOTE = "auto_note"
private const val KEY_AUTO_NEXT_SAVE_MS = "auto_next_save_ms"
private const val KEY_AUTO_USE_START_DELAY = "auto_use_start_delay"
private const val KEY_AUTO_START_DELAY_SECONDS = "auto_start_delay_seconds"
private const val KEY_AUTO_USE_DURATION = "auto_use_duration"
private const val KEY_AUTO_DURATION_SECONDS = "auto_duration_seconds"
private const val KEY_AUTO_END_MS = "auto_end_ms"
private const val KEY_AUTO_LIMIT_ENABLED = "auto_limit_enabled"
private const val KEY_AUTO_MAX_COUNT = "auto_max_count"
private const val KEY_AUTO_COMPLETED_COUNT = "auto_completed_count"
private const val KEY_AUTO_SESSION_ID = "auto_session_id"

// Used only to preserve the stop deadline of a session started by an older build.
private const val LEGACY_KEY_AUTO_USE_END = "auto_use_end"

private const val KEY_SAMPLES_PER_MEASUREMENT = "samples_per_measurement"
private const val KEY_SAMPLE_SPACING_MS = "sample_spacing_ms"
private const val KEY_DISCARD_EXTREMES = "discard_extremes"
private const val KEY_VIEW_MODE = "view_mode"
private const val KEY_SETTINGS_SAMPLING_EXPANDED =
    "settings_sampling_expanded"
private const val KEY_SETTINGS_USB_EXPANDED =
    "settings_usb_expanded"
private const val KEY_SETTINGS_BLUETOOTH_EXPANDED =
    "settings_bluetooth_expanded"
private const val KEY_SETTINGS_WIFI_EXPANDED =
    "settings_wifi_expanded"
private const val KEY_SETTINGS_MOBILE_EXPANDED =
    "settings_mobile_expanded"
private const val KEY_SETTINGS_COUNTERS_EXPANDED =
    "settings_counters_expanded"
private const val LIVE_UI_REFRESH_MS = 250L

private fun loadSettingsSectionExpanded(
    context: Context,
    key: String,
    defaultValue: Boolean
): Boolean =
    context
        .getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        .getBoolean(
            key,
            defaultValue
        )

private fun saveSettingsSectionExpanded(
    context: Context,
    key: String,
    expanded: Boolean
) {
    context
        .getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        .edit()
        .putBoolean(
            key,
            expanded
        )
        .apply()
}

fun saveGroupOrder(
    context: Context,
    groups: List<SensorGroup>
) {
    val value = groups.joinToString(",") { it.name }

    context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString("group_order", value)
        .apply()
}

fun loadGroupOrder(
    context: Context
): List<SensorGroup> {

    val defaultOrder = listOf(
        SensorGroup.UV,
        SensorGroup.HEV_HEB,
        SensorGroup.VISIBLE,
        SensorGroup.NIR
    )

    val saved = context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString("group_order", null)
        ?: return defaultOrder

    val result = saved
        .split(",")
        .mapNotNull { name ->
            try {
                SensorGroup.valueOf(name)
            } catch (_: Exception) {
                null
            }
        }
        .distinct()
        .toMutableList()

    defaultOrder.forEach { group ->
        if (!result.contains(group)) {
            result.add(group)
        }
    }

    return result
}

fun saveExpandedState(
    context: Context,
    group: SensorGroup,
    expanded: Boolean
) {
    context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean("expanded_${group.name}", expanded)
        .apply()
}

fun loadExpandedState(
    context: Context,
    group: SensorGroup
): Boolean {

    return context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(
            "expanded_${group.name}",
            true
        )
}

fun saveBiologicalEffectOrder(
    context: Context,
    groups: List<BiologicalEffectGroup>
) {
    context
        .getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        .edit()
        .putString(
            "biological_effect_order",
            groups.joinToString(",") {
                it.name
            }
        )
        .apply()
}

fun loadBiologicalEffectOrder(
    context: Context
): List<BiologicalEffectGroup> {
    val defaultOrder =
        BiologicalEffectGroup.entries

    val saved =
        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getString(
                "biological_effect_order",
                null
            )
            ?: return defaultOrder

    val result =
        saved
            .split(",")
            .mapNotNull { name ->
                runCatching {
                    BiologicalEffectGroup.valueOf(
                        name
                    )
                }.getOrNull()
            }
            .distinct()
            .toMutableList()

    defaultOrder.forEach { group ->
        if (group !in result) {
            result.add(group)
        }
    }

    return result
}

fun saveBiologicalEffectExpanded(
    context: Context,
    group: BiologicalEffectGroup,
    expanded: Boolean
) {
    context
        .getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        .edit()
        .putBoolean(
            "biological_effect_expanded_${group.name}",
            expanded
        )
        .apply()
}

fun loadBiologicalEffectExpanded(
    context: Context,
    group: BiologicalEffectGroup
): Boolean =
    context
        .getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        .getBoolean(
            "biological_effect_expanded_${group.name}",
            true
        )

// =====================================================
// DATABASE
// =====================================================

class UvirDatabaseHelper(
    context: Context
) : SQLiteOpenHelper(
    context.applicationContext,
    "uvir.db",
    null,
    4
) {

    private val appContext =
        context.applicationContext

    private fun createCountersTable(
        db: SQLiteDatabase
    ) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS uvir_counters (
                name TEXT PRIMARY KEY,
                value INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO uvir_counters(name, value)
            VALUES ('automatic_session_id', 0)
            """.trimIndent()
        )
    }

    private fun migrateAutomaticSessionIds(
        db: SQLiteDatabase
    ) {
        createCountersTable(db)

        val mappings =
            mutableListOf<Pair<Long, Long>>()

        db.rawQuery(
            """
            SELECT automatic_session_id
            FROM measurements
            WHERE automatic_session_id IS NOT NULL
            GROUP BY automatic_session_id
            ORDER BY MIN(timestamp) ASC, automatic_session_id ASC
            """.trimIndent(),
            null
        ).use { cursor ->
            var nextId = 1L
            while (cursor.moveToNext()) {
                mappings.add(
                    cursor.getLong(0) to nextId
                )
                nextId += 1L
            }
        }

        mappings.forEach { (oldId, newId) ->
            db.execSQL(
                """
                UPDATE measurements
                SET automatic_session_id = ?
                WHERE automatic_session_id = ?
                """.trimIndent(),
                arrayOf(-newId, oldId)
            )
        }

        db.execSQL(
            """
            UPDATE measurements
            SET automatic_session_id = -automatic_session_id
            WHERE automatic_session_id < 0
            """.trimIndent()
        )

        val preferences =
            appContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
        val oldActiveSessionId =
            preferences.getLong(
                KEY_AUTO_SESSION_ID,
                0L
            )
        val autoWasActive =
            preferences.getBoolean(
                KEY_AUTO_ENABLED,
                false
            )
        var counter =
            mappings.size.toLong()
        var migratedActiveSessionId =
            mappings
                .firstOrNull {
                    it.first == oldActiveSessionId
                }
                ?.second
                ?: 0L

        if (
            autoWasActive &&
            oldActiveSessionId > 0L &&
            migratedActiveSessionId == 0L
        ) {
            counter += 1L
            migratedActiveSessionId = counter
        }

        db.execSQL(
            """
            UPDATE uvir_counters
            SET value = ?
            WHERE name = 'automatic_session_id'
            """.trimIndent(),
            arrayOf(counter)
        )

        preferences.edit()
            .putLong(
                KEY_AUTO_SESSION_ID,
                migratedActiveSessionId
            )
            .apply()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE measurements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                note TEXT NOT NULL,
                automatic INTEGER NOT NULL DEFAULT 0,
                automatic_session_id INTEGER,
                automatic_sequence INTEGER,

                uvc REAL NOT NULL,
                uvb REAL NOT NULL,
                uva REAL NOT NULL,

                violetto REAL NOT NULL,
                blu REAL NOT NULL,
                verde REAL NOT NULL,
                giallo REAL NOT NULL,
                arancione REAL NOT NULL,
                rosso REAL NOT NULL,

                f8 REAL NOT NULL,
                nir REAL NOT NULL
            )
            """.trimIndent()
        )

        createCountersTable(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                ALTER TABLE measurements
                ADD COLUMN automatic INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
        }

        if (oldVersion < 3) {
            db.execSQL(
                """
                ALTER TABLE measurements
                ADD COLUMN automatic_session_id INTEGER
                """.trimIndent()
            )

            db.execSQL(
                """
                ALTER TABLE measurements
                ADD COLUMN automatic_sequence INTEGER
                """.trimIndent()
            )
        }

        if (oldVersion < 4) {
            migrateAutomaticSessionIds(db)
        }
    }

    fun nextAutomaticSessionId(): Long {
        val database = writableDatabase
        var nextId = 0L

        database.beginTransaction()
        try {
            createCountersTable(database)
            database.execSQL(
                """
                UPDATE uvir_counters
                SET value = value + 1
                WHERE name = 'automatic_session_id'
                """.trimIndent()
            )
            database.rawQuery(
                """
                SELECT value
                FROM uvir_counters
                WHERE name = 'automatic_session_id'
                """.trimIndent(),
                null
            ).use {
                if (it.moveToFirst()) {
                    nextId = it.getLong(0)
                }
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        return nextId
    }

    fun currentAutomaticSessionCounter(): Long {
        val database = readableDatabase
        createCountersTable(database)
        return database.rawQuery(
            """
            SELECT value
            FROM uvir_counters
            WHERE name = 'automatic_session_id'
            """.trimIndent(),
            null
        ).use {
            if (it.moveToFirst()) {
                it.getLong(0)
            } else {
                0L
            }
        }
    }

    private fun measurementCounter(
        database: SQLiteDatabase
    ): Long {
        return database.rawQuery(
            """
            SELECT seq
            FROM sqlite_sequence
            WHERE name = 'measurements'
            """.trimIndent(),
            null
        ).use {
            if (it.moveToFirst()) {
                it.getLong(0)
            } else {
                0L
            }
        }
    }

    fun currentMeasurementCounter(): Long =
        measurementCounter(readableDatabase)

    fun saveMeasurement(
        sample: SensorSample,
        note: String,
        automatic: Boolean = false,
        automaticSessionId: Long? = null,
        automaticSequence: Int? = null
    ): Long {

        val values = ContentValues().apply {
            put("timestamp", System.currentTimeMillis())
            put("note", note)
            put("automatic", if (automatic) 1 else 0)

            if (automaticSessionId != null) {
                put(
                    "automatic_session_id",
                    automaticSessionId
                )
            }

            if (automaticSequence != null) {
                put(
                    "automatic_sequence",
                    automaticSequence
                )
            }

            put("uvc", sample.uvc)
            put("uvb", sample.uvb)
            put("uva", sample.uva)

            put("violetto", sample.violetto)
            put("blu", sample.blu)
            put("verde", sample.verde)
            put("giallo", sample.giallo)
            put("arancione", sample.arancione)
            put("rosso", sample.rosso)

            put("f8", sample.f8)
            put("nir", sample.nir)
        }

        return writableDatabase.insert(
            "measurements",
            null,
            values
        )
    }

    fun readSavedRecords(): List<SavedRecordSummary> {

        val list = mutableListOf<SavedRecordSummary>()

        val cursor = readableDatabase.query(
            "measurements",
            arrayOf(
                "id",
                "timestamp",
                "note",
                "automatic",
                "automatic_session_id",
                "automatic_sequence"
            ),
            null,
            null,
            null,
            null,
            "timestamp DESC"
        )

        cursor.use {
            val idIndex = it.getColumnIndexOrThrow("id")
            val timestampIndex = it.getColumnIndexOrThrow("timestamp")
            val noteIndex = it.getColumnIndexOrThrow("note")
            val automaticIndex = it.getColumnIndexOrThrow("automatic")
            val automaticSessionIndex =
                it.getColumnIndexOrThrow(
                    "automatic_session_id"
                )
            val automaticSequenceIndex =
                it.getColumnIndexOrThrow(
                    "automatic_sequence"
                )

            while (it.moveToNext()) {
                list.add(
                    SavedRecordSummary(
                        id = it.getLong(idIndex),
                        timestamp = it.getLong(timestampIndex),
                        note = it.getString(noteIndex),
                        automatic = it.getInt(automaticIndex) != 0,
                        automaticSessionId =
                            if (
                                it.isNull(
                                    automaticSessionIndex
                                )
                            ) {
                                null
                            } else {
                                it.getLong(
                                    automaticSessionIndex
                                )
                            },
                        automaticSequence =
                            if (
                                it.isNull(
                                    automaticSequenceIndex
                                )
                            ) {
                                null
                            } else {
                                it.getInt(
                                    automaticSequenceIndex
                                )
                            }
                    )
                )
            }
        }

        return list
    }

    fun readRecord(id: Long): SavedRecordDetail? {

        val cursor = readableDatabase.query(
            "measurements",
            null,
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )

        cursor.use {
            if (!it.moveToFirst()) {
                return null
            }

            fun d(name: String): Double =
                it.getDouble(it.getColumnIndexOrThrow(name))

            return SavedRecordDetail(
                id = id,
                timestamp = it.getLong(
                    it.getColumnIndexOrThrow("timestamp")
                ),
                note = it.getString(
                    it.getColumnIndexOrThrow("note")
                ),
                automatic = it.getInt(
                    it.getColumnIndexOrThrow("automatic")
                ) != 0,
                sample = SensorSample(
                    uvc = d("uvc"),
                    uvb = d("uvb"),
                    uva = d("uva"),

                    violetto = d("violetto"),
                    blu = d("blu"),
                    verde = d("verde"),
                    giallo = d("giallo"),
                    arancione = d("arancione"),
                    rosso = d("rosso"),

                    f8 = d("f8"),
                    nir = d("nir")
                ),
                automaticSessionId =
                    it.getColumnIndexOrThrow(
                        "automatic_session_id"
                    ).let { index ->
                        if (it.isNull(index)) {
                            null
                        } else {
                            it.getLong(index)
                        }
                    },
                automaticSequence =
                    it.getColumnIndexOrThrow(
                        "automatic_sequence"
                    ).let { index ->
                        if (it.isNull(index)) {
                            null
                        } else {
                            it.getInt(index)
                        }
                    }
            )
        }
    }

    fun deleteRecord(id: Long): Int {
        return writableDatabase.delete(
            "measurements",
            "id = ?",
            arrayOf(id.toString())
        )
    }

    fun deleteRecords(ids: Collection<Long>): Int {
        if (ids.isEmpty()) {
            return 0
        }

        val placeholders =
            ids.joinToString(",") { "?" }

        return writableDatabase.delete(
            "measurements",
            "id IN ($placeholders)",
            ids.map { it.toString() }
                .toTypedArray()
        )
    }

    fun deleteAllMeasurements(): Int {
        val database = writableDatabase
        var deletedRows = 0

        database.beginTransaction()
        try {
            deletedRows = database.delete(
                "measurements",
                null,
                null
            )

            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        return deletedRows
    }

    fun resetAllCounters(): Int {
        val database = writableDatabase
        var deletedRows = 0

        database.beginTransaction()
        try {
            deletedRows = database.delete(
                "measurements",
                null,
                null
            )
            database.execSQL(
                "DELETE FROM sqlite_sequence WHERE name = 'measurements'"
            )
            createCountersTable(database)
            database.execSQL(
                """
                UPDATE uvir_counters
                SET value = 0
                WHERE name = 'automatic_session_id'
                """.trimIndent()
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        return deletedRows
    }

    fun readAllRecords():
            List<SavedRecordDetail> {
        return readSavedRecords()
            .mapNotNull {
                readRecord(it.id)
            }
    }

    fun updateMeasurement(
        record: SavedRecordDetail
    ): Int {
        val values =
            ContentValues().apply {
                put("timestamp", record.timestamp)
                put("note", record.note)
                put(
                    "automatic",
                    if (record.automatic) 1 else 0
                )

                record.automaticSessionId?.let {
                    put("automatic_session_id", it)
                } ?: putNull("automatic_session_id")

                record.automaticSequence?.let {
                    put("automatic_sequence", it)
                } ?: putNull("automatic_sequence")

                put("uvc", record.sample.uvc)
                put("uvb", record.sample.uvb)
                put("uva", record.sample.uva)
                put("violetto", record.sample.violetto)
                put("blu", record.sample.blu)
                put("verde", record.sample.verde)
                put("giallo", record.sample.giallo)
                put("arancione", record.sample.arancione)
                put("rosso", record.sample.rosso)
                put("f8", record.sample.f8)
                put("nir", record.sample.nir)
            }

        return writableDatabase.update(
            "measurements",
            values,
            "id = ?",
            arrayOf(record.id.toString())
        )
    }

    fun replaceAllMeasurements(
        records: List<SavedRecordDetail>,
        measurementCounter: Long? = null,
        sessionCounter: Long? = null
    ): Int {
        val database = writableDatabase

        database.beginTransaction()
        try {
            database.delete(
                "measurements",
                null,
                null
            )

            records.forEach { record ->
                val values =
                    ContentValues().apply {
                        put("id", record.id)
                        put("timestamp", record.timestamp)
                        put("note", record.note)
                        put(
                            "automatic",
                            if (record.automatic) 1 else 0
                        )

                        record.automaticSessionId?.let {
                            put("automatic_session_id", it)
                        } ?: putNull("automatic_session_id")

                        record.automaticSequence?.let {
                            put("automatic_sequence", it)
                        } ?: putNull("automatic_sequence")

                        put("uvc", record.sample.uvc)
                        put("uvb", record.sample.uvb)
                        put("uva", record.sample.uva)
                        put("violetto", record.sample.violetto)
                        put("blu", record.sample.blu)
                        put("verde", record.sample.verde)
                        put("giallo", record.sample.giallo)
                        put("arancione", record.sample.arancione)
                        put("rosso", record.sample.rosso)
                        put("f8", record.sample.f8)
                        put("nir", record.sample.nir)
                    }

                database.insertOrThrow(
                    "measurements",
                    null,
                    values
                )
            }

            val importedSessionCounter =
                maxOf(
                    sessionCounter ?: 0L,
                    records
                        .mapNotNull {
                            it.automaticSessionId
                        }
                        .maxOrNull()
                        ?: 0L
                )
            createCountersTable(database)
            database.execSQL(
                """
                UPDATE uvir_counters
                SET value = MAX(value, ?)
                WHERE name = 'automatic_session_id'
                """.trimIndent(),
                arrayOf(importedSessionCounter)
            )

            val importedMeasurementCounter =
                maxOf(
                    measurementCounter ?: 0L,
                    records.maxOfOrNull {
                        it.id
                    } ?: 0L
                )
            if (
                importedMeasurementCounter >
                measurementCounter(database)
            ) {
                val sequenceValues =
                    ContentValues().apply {
                        put(
                            "seq",
                            importedMeasurementCounter
                        )
                    }
                val updated =
                    database.update(
                        "sqlite_sequence",
                        sequenceValues,
                        "name = ?",
                        arrayOf("measurements")
                    )
                if (updated == 0) {
                    sequenceValues.put(
                        "name",
                        "measurements"
                    )
                    database.insertOrThrow(
                        "sqlite_sequence",
                        null,
                        sequenceValues
                    )
                }
            }

            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        return records.size
    }
}

// =====================================================
// UTILITÀ
// =====================================================

fun formatDateTime(timestamp: Long): String {
    return SimpleDateFormat(
        "dd/MM/yyyy  HH:mm:ss",
        Locale.getDefault()
    ).format(Date(timestamp))
}

fun formatDetailDateTime(timestamp: Long): String {
    return SimpleDateFormat(
        "dd/MM/yyyy\nHH:mm:ss",
        Locale.getDefault()
    ).format(Date(timestamp))
}

fun formatAutomaticSessionDateTime(
    timestamp: Long
): String {
    return SimpleDateFormat(
        "dd/MM/yyyy  HH:mm",
        Locale.getDefault()
    ).format(Date(timestamp))
}

enum class MeasurementShareFormat {
    CSV,
    READABLE_TABLE,
    BOTH
}

internal const val DATA_EXPORT_LANGUAGE = "en"

private fun csvCell(value: String): String =
    if (
        value.contains(';') ||
        value.contains('"') ||
        value.contains('\n') ||
        value.contains('\r')
    ) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }

private fun csvNumber(value: Double): String =
    value.toString()

internal val MEASUREMENT_EXPORT_COLUMNS_IT =
    listOf(
        "ID_misurazione",
        "ID_sessione",
        "Data/Ora",
        "Timestamp_ms",
        "Tipo_acquisizione",
        "Automatico",
        "Nota",
        "Progressivo_sessione",
        "UVC_100_280_nm_uW_cm2",
        "UVB_280_315_nm_uW_cm2",
        "UVA_315_400_nm_uW_cm2",
        "UV_totale_uW_cm2",
        "HEV_400_500_nm_uW_cm2",
        "HEB_400_450_nm_uW_cm2",
        "Violetto_400_450_nm_uW_cm2",
        "Blu_450_495_nm_uW_cm2",
        "Verde_495_570_nm_uW_cm2",
        "Giallo_570_590_nm_uW_cm2",
        "Arancione_590_620_nm_uW_cm2",
        "Rosso_620_700_nm_uW_cm2",
        "Visibile_totale_uW_cm2",
        "FarRed_picco_745_nm_uW_cm2",
        "NIR_picco_855_nm_uW_cm2",
        "FarRed_NIR_totale_uW_cm2",
        "Modello_biologico",
        "Irradianza_pesata_stimata_UV_effetto_DNA_uW_cm2_eq",
        "Indice_spettrale_UV_effetto_DNA_0_100",
        "Irradianza_pesata_stimata_fotoinvecchiamento_UVA_uW_cm2_eq",
        "Indice_spettrale_fotoinvecchiamento_UVA_0_100",
        "Irradianza_pesata_stimata_stress_ossidativo_HEV_uW_cm2_eq",
        "Indice_spettrale_stress_ossidativo_HEV_0_100"
    )

internal val MEASUREMENT_EXPORT_COLUMNS_EN =
    listOf(
        "Measurement_ID",
        "Session_ID",
        "Date/Time",
        "Timestamp_ms",
        "Acquisition_type",
        "Automatic",
        "Note",
        "Session_sequence",
        "UVC_100_280_nm_uW_cm2",
        "UVB_280_315_nm_uW_cm2",
        "UVA_315_400_nm_uW_cm2",
        "Total_UV_uW_cm2",
        "HEV_400_500_nm_uW_cm2",
        "HEB_400_450_nm_uW_cm2",
        "Violet_400_450_nm_uW_cm2",
        "Blue_450_495_nm_uW_cm2",
        "Green_495_570_nm_uW_cm2",
        "Yellow_570_590_nm_uW_cm2",
        "Orange_590_620_nm_uW_cm2",
        "Red_620_700_nm_uW_cm2",
        "Total_visible_uW_cm2",
        "FarRed_peak_745_nm_uW_cm2",
        "NIR_peak_855_nm_uW_cm2",
        "Total_FarRed_NIR_uW_cm2",
        "Biological_model",
        "Estimated_weighted_irradiance_UV_DNA_effect_uW_cm2_eq",
        "Spectral_index_UV_DNA_effect_0_100",
        "Estimated_weighted_irradiance_UVA_photoaging_uW_cm2_eq",
        "Spectral_index_UVA_photoaging_0_100",
        "Estimated_weighted_irradiance_HEV_oxidative_stress_uW_cm2_eq",
        "Spectral_index_HEV_oxidative_stress_0_100"
    )

internal fun measurementExportColumns(
    language: String
): List<String> =
    if (language == "it") {
        MEASUREMENT_EXPORT_COLUMNS_IT
    } else {
        MEASUREMENT_EXPORT_COLUMNS_EN
    }

private fun csvDateTime(
    timestamp: Long,
    language: String
): String =
    SimpleDateFormat(
        if (language == "it") {
            "dd/MM/yyyy HH:mm:ss"
        } else {
            "yyyy-MM-dd HH:mm:ss"
        },
        Locale.getDefault()
    ).format(Date(timestamp))

private fun measurementCsv(
    records: List<SavedRecordDetail>
): String = buildString {
    val language = DATA_EXPORT_LANGUAGE

    appendLine(
        measurementExportColumns(
            language
        ).joinToString(";") {
            csvCell(it)
        }
    )

    records.forEach { record ->
        val sample = record.sample
        val effects = biologicalEffects(sample)
        val uvTotal =
            sample.uvc +
                sample.uvb +
                sample.uva
        val hev =
            sample.violetto +
                sample.blu
        val visibleTotal =
            sample.violetto +
                sample.blu +
                sample.verde +
                sample.giallo +
                sample.arancione +
                sample.rosso
        val farRedNirTotal =
            sample.f8 +
                sample.nir

        appendLine(
            listOf(
                record.id.toString(),
                record.automaticSessionId
                    ?.toString()
                    .orEmpty(),
                csvDateTime(
                    record.timestamp,
                    language
                ),
                record.timestamp.toString(),
                if (record.automatic) {
                    if (language == "it") {
                        "Automatica"
                    } else {
                        "Automatic"
                    }
                } else {
                    if (language == "it") {
                        "Manuale"
                    } else {
                        "Manual"
                    }
                },
                if (record.automatic) "1" else "0",
                record.note,
                record.automaticSequence
                    ?.toString()
                    .orEmpty(),
                csvNumber(sample.uvc),
                csvNumber(sample.uvb),
                csvNumber(sample.uva),
                csvNumber(uvTotal),
                csvNumber(hev),
                csvNumber(sample.violetto),
                csvNumber(sample.violetto),
                csvNumber(sample.blu),
                csvNumber(sample.verde),
                csvNumber(sample.giallo),
                csvNumber(sample.arancione),
                csvNumber(sample.rosso),
                csvNumber(visibleTotal),
                csvNumber(sample.f8),
                csvNumber(sample.nir),
                csvNumber(farRedNirTotal),
                BIOLOGICAL_MODEL_VERSION,
                csvNumber(effects.dnaUvProxy),
                csvNumber(effects.dnaUvScore.toDouble() * 100.0),
                csvNumber(effects.uvaPhotoagingProxy),
                csvNumber(effects.uvaPhotoagingScore.toDouble() * 100.0),
                csvNumber(effects.hevOxidativeProxy),
                csvNumber(effects.hevOxidativeScore.toDouble() * 100.0)
            ).joinToString(";") {
                csvCell(it)
            }
        )
    }
}

private fun readableMeasurementTable(
    context: Context,
    records: List<SavedRecordDetail>
): String = buildString {
    appendLine("Uvir")
    appendLine()

    records.forEachIndexed { index, record ->
        appendLine(
            "${context.getString(R.string.share_measurement_id_label)}: " +
                record.id
        )
        appendLine(
            "${context.getString(R.string.share_session_id_label)}: " +
                (record.automaticSessionId?.toString() ?: "—")
        )

        appendLine(
            "${context.getString(R.string.share_date_label)}: " +
                csvDateTime(
                    record.timestamp,
                    DATA_EXPORT_LANGUAGE
                )
        )
        appendLine(
            "${context.getString(R.string.share_acquisition_label)}: " +
                context.getString(
                    if (record.automatic) {
                        R.string.share_automatic
                    } else {
                        R.string.share_manual
                    }
                )
        )
        appendLine(
            "${context.getString(R.string.share_note_label)}: " +
                if (record.note.isBlank()) {
                    context.getString(R.string.no_note)
                } else {
                    record.note
                }
        )
        appendLine()

        listOf(
            "UV-C" to record.sample.uvc,
            "UV-B" to record.sample.uvb,
            "UV-A" to record.sample.uva,
            context.getString(R.string.violet) to record.sample.violetto,
            context.getString(R.string.blue) to record.sample.blu,
            context.getString(R.string.green) to record.sample.verde,
            context.getString(R.string.yellow) to record.sample.giallo,
            context.getString(R.string.orange) to record.sample.arancione,
            context.getString(R.string.red) to record.sample.rosso,
            "FAR-RED" to record.sample.f8,
            "NIR" to record.sample.nir
        ).forEach { (name, value) ->
            appendLine(
                "%-8s  %s µW/cm²".format(
                    Locale.US,
                    name,
                    "%.3f".format(
                        Locale.US,
                        value
                    )
                )
            )
        }

        val effects =
            biologicalEffects(
                record.sample
            )

        appendLine()
        appendLine(
            context.getString(
                R.string.share_biological_effects_heading,
                BIOLOGICAL_MODEL_VERSION
            )
        )
        appendLine(
            context.getString(
                R.string.biological_effects_disclaimer
            )
        )
        appendLine()

        listOf(
            Triple(
                context.getString(R.string.dna_uv_proxy),
                effects.dnaUvProxy,
                effects.dnaUvScore
            ),
            Triple(
                context.getString(R.string.uva_photoaging_proxy),
                effects.uvaPhotoagingProxy,
                effects.uvaPhotoagingScore
            ),
            Triple(
                context.getString(R.string.hev_oxidative_proxy),
                effects.hevOxidativeProxy,
                effects.hevOxidativeScore
            )
        ).forEach { (name, weightedValue, score) ->
            appendLine(name)
            appendLine(
                context.getString(
                    R.string.share_biological_effect_values,
                    weightedValue,
                    score.toDouble() * 100.0
                )
            )
        }

        if (index < records.lastIndex) {
            appendLine()
            appendLine("────────────────────")
            appendLine()
        }
    }
}

private fun shareMeasurements(
    context: Context,
    records: List<SavedRecordDetail>,
    format: MeasurementShareFormat
) {
    if (records.isEmpty()) {
        return
    }

    val exportConfiguration =
        android.content.res.Configuration(
            context.resources.configuration
        ).apply {
            setLocale(Locale.ENGLISH)
            setLayoutDirection(Locale.ENGLISH)
        }

    val exportContext =
        context.createConfigurationContext(
            exportConfiguration
        )

    val subject =
        exportContext.getString(
            R.string.share_subject
        )

    val readableText =
        readableMeasurementTable(
            exportContext,
            records
        )

    val sharedDirectory =
        File(
            context.cacheDir,
            "shared"
        ).apply {
            mkdirs()
        }

    val sharedBaseName =
        if (records.size == 1) {
            "uvir_measurement_${records.first().id}"
        } else {
            "uvir_measurements_${System.currentTimeMillis()}"
        }

    fun writeSharedFile(
        extension: String,
        content: String
    ): File =
        File(
            sharedDirectory,
            "$sharedBaseName.$extension"
        ).apply {
            writeText(
                content,
                Charsets.UTF_8
            )
        }

    fun sharedUri(file: File) =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

    val sendIntent =
        when (format) {
            MeasurementShareFormat.CSV -> {
                val csvFile =
                    writeSharedFile(
                        "csv",
                        measurementCsv(records)
                    )

                val uri =
                    sharedUri(csvFile)

                Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData =
                        ClipData.newUri(
                            context.contentResolver,
                            csvFile.name,
                            uri
                        )
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }

            MeasurementShareFormat.READABLE_TABLE -> {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        readableText
                    )
                }
            }

            MeasurementShareFormat.BOTH -> {
                val csvFile =
                    writeSharedFile(
                        "csv",
                        measurementCsv(records)
                    )

                val readableFile =
                    writeSharedFile(
                        "txt",
                        readableText
                    )

                val csvUri =
                    sharedUri(csvFile)

                val readableUri =
                    sharedUri(readableFile)

                val uris =
                    arrayListOf(
                        csvUri,
                        readableUri
                    )

                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "text/*"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, readableText)
                    putParcelableArrayListExtra(
                        Intent.EXTRA_STREAM,
                        uris
                    )
                    clipData =
                        ClipData.newUri(
                            context.contentResolver,
                            csvFile.name,
                            csvUri
                        ).apply {
                            addItem(
                                ClipData.Item(
                                    readableUri
                                )
                            )
                        }
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
        }

    val chooser =
        Intent.createChooser(
            sendIntent,
            context.getString(
                R.string.share_measurements
            )
        )

    if (context !is Activity) {
        chooser.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )
    }

    context.startActivity(chooser)
}

@Composable
private fun MeasurementShareFormatDialog(
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onDismiss: () -> Unit,
    onFormatSelected: (MeasurementShareFormat) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    R.string.choose_share_format
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement =
                    Arrangement.spacedBy(2.dp)
            ) {
                TextButton(
                    onClick = {
                        onFormatSelected(
                            MeasurementShareFormat.CSV
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            R.string.share_as_csv
                        )
                    )
                }

                TextButton(
                    onClick = {
                        onFormatSelected(
                            MeasurementShareFormat.READABLE_TABLE
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            R.string.share_as_readable_table
                        )
                    )
                }

                TextButton(
                    onClick = {
                        onFormatSelected(
                            MeasurementShareFormat.BOTH
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            R.string.share_as_both
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor =
                            Color(0xFFD32F2F)
                    )
            ) {
                Text(
                    stringResource(
                        R.string.cancel
                    )
                )
            }
        },
        containerColor = cardColor,
        titleContentColor = primaryText,
        textContentColor = secondaryText
    )
}

@Composable
private fun HoldToConfirmDeleteButton(
    label: String,
    onConfirmed: () -> Unit,
    holdDurationMillis: Long = 2_000L
) {
    var isHolding by remember {
        mutableStateOf(false)
    }

    val currentOnConfirmed by
        rememberUpdatedState(onConfirmed)

    LaunchedEffect(
        isHolding,
        holdDurationMillis
    ) {
        if (!isHolding) {
            return@LaunchedEffect
        }

        delay(holdDurationMillis)

        if (isHolding) {
            currentOnConfirmed()
            isHolding = false
        }
    }

    val deleteColor =
        Color(0xFFD32F2F)

    Box(
        modifier =
            Modifier
                .heightIn(min = 48.dp)
                .clip(
                    RoundedCornerShape(50)
                )
                .background(
                    if (isHolding) {
                        deleteColor.copy(
                            alpha = 0.10f
                        )
                    } else {
                        Color.Transparent
                    }
                )
                .pointerInput(
                    holdDurationMillis
                ) {
                    detectTapGestures(
                        onPress = {
                            isHolding = true
                            tryAwaitRelease()
                            isHolding = false
                        }
                    )
                }
                .padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = deleteColor,
            fontWeight = FontWeight.Bold
        )
    }
}

fun formatInterval(totalSeconds: Long): String {

    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3600L
    val minutes = (safeSeconds % 3600L) / 60L
    val seconds = safeSeconds % 60L

    return "%02d:%02d:%02d".format(
        Locale.US,
        hours,
        minutes,
        seconds
    )
}

fun generateRandomSample(): SensorSample {
    return SensorSample(
        uvc = Random.nextDouble(0.0, 80.0),
        uvb = Random.nextDouble(10.0, 500.0),
        uva = Random.nextDouble(100.0, 2500.0),

        violetto = Random.nextDouble(20.0, 250.0),
        blu = Random.nextDouble(50.0, 500.0),
        verde = Random.nextDouble(100.0, 650.0),
        giallo = Random.nextDouble(50.0, 400.0),
        arancione = Random.nextDouble(30.0, 350.0),
        rosso = Random.nextDouble(80.0, 600.0),

        f8 = Random.nextDouble(50.0, 700.0),
        nir = Random.nextDouble(50.0, 900.0)
    )
}

fun averageValues(
    values: List<Double>,
    discardExtremes: Boolean
): Double {

    if (values.isEmpty()) {
        return 0.0
    }

    if (!discardExtremes || values.size < 3) {
        return values.average()
    }

    val sorted = values.sorted()

    return sorted
        .drop(1)
        .dropLast(1)
        .average()
}

fun combineSamples(
    samples: List<SensorSample>,
    discardExtremes: Boolean
): SensorSample {

    return SensorSample(
        uvc = averageValues(samples.map { it.uvc }, discardExtremes),
        uvb = averageValues(samples.map { it.uvb }, discardExtremes),
        uva = averageValues(samples.map { it.uva }, discardExtremes),

        violetto = averageValues(samples.map { it.violetto }, discardExtremes),
        blu = averageValues(samples.map { it.blu }, discardExtremes),
        verde = averageValues(samples.map { it.verde }, discardExtremes),
        giallo = averageValues(samples.map { it.giallo }, discardExtremes),
        arancione = averageValues(samples.map { it.arancione }, discardExtremes),
        rosso = averageValues(samples.map { it.rosso }, discardExtremes),

        f8 = averageValues(samples.map { it.f8 }, discardExtremes),
        nir = averageValues(samples.map { it.nir }, discardExtremes)
    )
}

fun nextOccurrenceMillis(
    hour: Int,
    minute: Int,
    nowMillis: Long = System.currentTimeMillis()
): Long {

    val zone = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(nowMillis)
        .atZone(zone)
        .toLocalDateTime()

    var candidate = LocalDateTime.of(
        now.toLocalDate(),
        LocalTime.of(hour, minute)
    )

    if (!candidate.isAfter(now)) {
        candidate = candidate.plusDays(1)
    }

    return candidate
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}

fun occurrenceAtOrAfterMillis(
    referenceMillis: Long,
    hour: Int,
    minute: Int
): Long {

    val zone = ZoneId.systemDefault()
    val reference = Instant.ofEpochMilli(referenceMillis)
        .atZone(zone)
        .toLocalDateTime()

    var candidate = LocalDateTime.of(
        reference.toLocalDate(),
        LocalTime.of(hour, minute)
    )

    if (!candidate.isAfter(reference)) {
        candidate = candidate.plusDays(1)
    }

    return candidate
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}

fun formatClockTime(timestamp: Long): String {
    return SimpleDateFormat(
        "HH:mm",
        Locale.getDefault()
    ).format(Date(timestamp))
}

fun biologicalEffects(
    sample: SensorSample
): BiologicalEffectEstimate {

    // Broad-band exploratory weighted estimates only.
    // The weighted signals preserve irradiance-like units because the
    // prototype weights are dimensionless.
    //
    // The 0–100 scores describe relative spectral weighting within the
    // relevant measured band. They are NOT percentages of biological damage
    // and are NOT safety thresholds.

    val uvTotal =
        sample.uvc +
                sample.uvb +
                sample.uva

    val visibleTotal =
        sample.violetto +
                sample.blu +
                sample.verde +
                sample.giallo +
                sample.arancione +
                sample.rosso

    val dnaUv =
        sample.uvc * 1.00 +
                sample.uvb * 0.60 +
                sample.uva * 0.01

    val dnaUvScore =
        if (uvTotal > 0.0)
            (dnaUv / uvTotal)
                .toFloat()
                .coerceIn(0f, 1f)
        else
            0f

    val uvaPhotoaging =
        sample.uva +
                sample.uvb * 0.05

    val uvaPhotoagingScore =
        if (uvTotal > 0.0)
            (uvaPhotoaging / uvTotal)
                .toFloat()
                .coerceIn(0f, 1f)
        else
            0f

    val hev =
        sample.violetto +
                sample.blu

    val hevOxidativeScore =
        if (visibleTotal > 0.0)
            (hev / visibleTotal)
                .toFloat()
                .coerceIn(0f, 1f)
        else
            0f

    return BiologicalEffectEstimate(
        dnaUvProxy = dnaUv,
        dnaUvScore = dnaUvScore,

        uvaPhotoagingProxy = uvaPhotoaging,
        uvaPhotoagingScore = uvaPhotoagingScore,

        hevOxidativeProxy = hev,
        hevOxidativeScore = hevOxidativeScore
    )
}

fun percentage(
    value: Double,
    total: Double
): Float {

    return if (total > 0.0) {
        (value / total)
            .toFloat()
            .coerceIn(0f, 1f)
    } else {
        0f
    }
}

// =====================================================
// APP
// =====================================================

@Composable
fun UvirApp(
    remoteNetworkEnabled: Boolean
) {

    val context = LocalContext.current

    val database = remember {
        UvirDatabaseHelper(context.applicationContext)
    }

    DisposableEffect(Unit) {
        onDispose {
            database.close()
        }
    }

    val darkMode = isSystemInDarkTheme()

    val backgroundColor =
        if (darkMode) Color(0xFF101418)
        else Color(0xFFF4F7F9)

    val cardColor =
        if (darkMode) Color(0xFF1C242B)
        else Color.White

    val primaryText =
        if (darkMode) Color.White
        else Color(0xFF101418)

    val secondaryText =
        if (darkMode) Color(0xFF90A4AE)
        else Color(0xFF546E7A)

    val trackColor =
        if (darkMode) Color(0xFF37474F)
        else Color(0xFFDCE3E7)

    val view =
        LocalView.current

    SideEffect {
        val activity =
            view.context as? Activity

        activity?.window?.let { window ->
            window.statusBarColor =
                backgroundColor.toArgb()

            WindowCompat
                .getInsetsController(
                    window,
                    view
                )
                .isAppearanceLightStatusBars =
                !darkMode

            if (Build.VERSION.SDK_INT >= 29) {
                window.isStatusBarContrastEnforced =
                    false
            }
        }
    }

    val preferences = remember {
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    var screen by rememberSaveable {
        mutableStateOf(AppScreen.LIVE)
    }

    var selectedRecordId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }

    val liveListState =
        rememberSaveable(
            saver = LazyListState.Saver
        ) {
            LazyListState()
        }

    val automaticListState =
        rememberSaveable(
            saver = LazyListState.Saver
        ) {
            LazyListState()
        }

    val historyListState =
        rememberSaveable(
            saver = LazyListState.Saver
        ) {
            LazyListState()
        }

    val detailListState =
        rememberSaveable(
            saver = LazyListState.Saver
        ) {
            LazyListState()
        }

    val versionInfoScrollState =
        rememberSaveable(
            saver = ScrollState.Saver
        ) {
            ScrollState(0)
        }

    val parametersScrollState =
        rememberSaveable(
            saver = ScrollState.Saver
        ) {
            ScrollState(0)
        }

    var viewMode by remember {
        mutableStateOf(
            runCatching {
                ViewMode.valueOf(
                    preferences.getString(
                        KEY_VIEW_MODE,
                        ViewMode.IRRADIANCE.name
                    ) ?: ViewMode.IRRADIANCE.name
                )
            }.getOrDefault(
                ViewMode.IRRADIANCE
            )
        )
    }

    fun setViewMode(mode: ViewMode) {
        viewMode = mode

        preferences.edit()
            .putString(
                KEY_VIEW_MODE,
                mode.name
            )
            .apply()
    }

    // -------------------------------------------------
    // ACQUISITION PARAMETERS
    // -------------------------------------------------

    var samplesPerMeasurement by remember {
        mutableIntStateOf(
            preferences
                .getInt(
                    KEY_SAMPLES_PER_MEASUREMENT,
                    5
                )
                .coerceIn(1, 21)
        )
    }

    var sampleSpacingMs by remember {
        mutableLongStateOf(
            preferences
                .getLong(
                    KEY_SAMPLE_SPACING_MS,
                    150L
                )
                .coerceIn(20L, 5000L)
        )
    }

    var discardExtremes by remember {
        mutableStateOf(
            preferences.getBoolean(
                KEY_DISCARD_EXTREMES,
                true
            )
        )
    }

    // -------------------------------------------------
    // CURRENT ACQUISITION
    // For now these are simulated values.
    // Later this is the point where USB sensor data enters Uvir.
    // -------------------------------------------------

    var measurement by rememberSaveable(
        stateSaver = SensorSampleSaver
    ) {
        mutableStateOf(SensorSample())
    }

    // Keeps the full-rate sensor result available to automatic acquisition,
    // independently from the intentionally throttled screen refresh.
    val latestMeasurement =
        remember {
            AtomicReference(
                measurement
            )
        }

    var liveReady by rememberSaveable {
        mutableStateOf(false)
    }

    var liveSamples by rememberSaveable(
        stateSaver = SensorSampleListSaver
    ) {
        mutableStateOf<List<SensorSample>>(
            emptyList()
        )
    }

    LaunchedEffect(
        samplesPerMeasurement,
        sampleSpacingMs,
        discardExtremes
    ) {

        var lastUiRefreshMs = 0L

        while (true) {

            liveSamples =
                (
                        liveSamples +
                                generateRandomSample()
                        ).takeLast(
                    samplesPerMeasurement
                        .coerceAtLeast(1)
                )

            val combinedMeasurement =
                combineSamples(
                    liveSamples,
                    discardExtremes
                )

            latestMeasurement.set(
                combinedMeasurement
            )

            val nowMs =
                System.currentTimeMillis()

            if (
                lastUiRefreshMs == 0L ||
                nowMs - lastUiRefreshMs >=
                LIVE_UI_REFRESH_MS
            ) {
                measurement =
                    combinedMeasurement

                lastUiRefreshMs =
                    nowMs
            }

            liveReady =
                liveSamples.size >=
                        samplesPerMeasurement

            delay(sampleSpacingMs)
        }
    }

    // -------------------------------------------------
    // AUTOMATIC ACQUISITION
    // -------------------------------------------------

    var autoEnabled by remember {
        mutableStateOf(
            preferences.getBoolean(
                KEY_AUTO_ENABLED,
                false
            )
        )
    }

    var autoIntervalSeconds by remember {
        mutableLongStateOf(
            preferences.getLong(
                KEY_AUTO_INTERVAL_SECONDS,
                60L
            )
        )
    }

    var autoNote by remember {
        mutableStateOf(
            preferences.getString(
                KEY_AUTO_NOTE,
                ""
            ) ?: ""
        )
    }

    var autoUseStartDelay by remember {
        mutableStateOf(
            preferences.getBoolean(
                KEY_AUTO_USE_START_DELAY,
                false
            )
        )
    }

    var autoStartDelaySeconds by remember {
        mutableLongStateOf(
            preferences.getLong(
                KEY_AUTO_START_DELAY_SECONDS,
                0L
            )
        )
    }

    var autoUseDuration by remember {
        mutableStateOf(
            preferences.getBoolean(
                KEY_AUTO_USE_DURATION,
                preferences.getBoolean(
                    LEGACY_KEY_AUTO_USE_END,
                    false
                )
            )
        )
    }

    var autoDurationSeconds by remember {
        mutableLongStateOf(
            preferences.getLong(
                KEY_AUTO_DURATION_SECONDS,
                3600L
            )
        )
    }

    var autoLimitEnabled by remember {
        mutableStateOf(
            preferences.getBoolean(
                KEY_AUTO_LIMIT_ENABLED,
                false
            )
        )
    }

    var autoMaxCount by remember {
        mutableIntStateOf(
            preferences.getInt(
                KEY_AUTO_MAX_COUNT,
                10
            ).coerceAtLeast(1)
        )
    }

    var autoCompletedCount by remember {
        mutableIntStateOf(
            preferences.getInt(
                KEY_AUTO_COMPLETED_COUNT,
                0
            ).coerceAtLeast(0)
        )
    }

    var autoSessionId by remember {
        mutableLongStateOf(
            preferences.getLong(
                KEY_AUTO_SESSION_ID,
                0L
            ).coerceAtLeast(0L)
        )
    }

    var autoNextSaveMs by remember {
        mutableLongStateOf(
            preferences.getLong(
                KEY_AUTO_NEXT_SAVE_MS,
                System.currentTimeMillis()
            )
        )
    }

    var autoEndMs by remember {
        mutableLongStateOf(
            preferences.getLong(
                KEY_AUTO_END_MS,
                0L
            )
        )
    }

    fun stopAutomaticAcquisition() {

        autoEnabled = false

        preferences.edit()
            .putBoolean(
                KEY_AUTO_ENABLED,
                false
            )
            .apply()
    }

    fun startAutomaticAcquisition(
        request: AutomaticAcquisitionRequest
    ) {

        // Safety guard: never start a second AUTO session
        // while one is already running.
        if (autoEnabled) {
            return
        }

        val now =
            System.currentTimeMillis()

        val startDelayMs =
            request.startDelaySeconds
                .coerceAtLeast(0L)
                .coerceAtMost(
                    Long.MAX_VALUE / 1000L
                ) * 1000L

        val startAt =
            if (request.useStartDelay) {
                now + startDelayMs
            } else {
                now
            }

        val newSessionId =
            database.nextAutomaticSessionId()

        val durationMs =
            request.durationSeconds
                .coerceAtLeast(0L)
                .coerceAtMost(
                    Long.MAX_VALUE / 1000L
                ) * 1000L

        val endAt =
            if (request.useDuration) {
                startAt + durationMs
            } else {
                0L
            }

        autoIntervalSeconds =
            request.intervalSeconds

        autoNote =
            request.note

        autoUseStartDelay =
            request.useStartDelay

        autoStartDelaySeconds =
            request.startDelaySeconds

        autoUseDuration =
            request.useDuration

        autoDurationSeconds =
            request.durationSeconds

        autoLimitEnabled =
            request.limitEnabled

        autoMaxCount =
            request.maxAcquisitions
                .coerceAtLeast(1)

        autoCompletedCount = 0
        autoSessionId = newSessionId
        autoNextSaveMs = startAt
        autoEndMs = endAt
        autoEnabled = true

        preferences.edit()
            .putBoolean(
                KEY_AUTO_ENABLED,
                true
            )
            .putLong(
                KEY_AUTO_INTERVAL_SECONDS,
                request.intervalSeconds
            )
            .putString(
                KEY_AUTO_NOTE,
                request.note
            )
            .putBoolean(
                KEY_AUTO_USE_START_DELAY,
                request.useStartDelay
            )
            .putLong(
                KEY_AUTO_START_DELAY_SECONDS,
                request.startDelaySeconds
            )
            .putBoolean(
                KEY_AUTO_USE_DURATION,
                request.useDuration
            )
            .putLong(
                KEY_AUTO_DURATION_SECONDS,
                request.durationSeconds
            )
            .putLong(
                KEY_AUTO_END_MS,
                endAt
            )
            .putBoolean(
                KEY_AUTO_LIMIT_ENABLED,
                request.limitEnabled
            )
            .putInt(
                KEY_AUTO_MAX_COUNT,
                request.maxAcquisitions
                    .coerceAtLeast(1)
            )
            .putInt(
                KEY_AUTO_COMPLETED_COUNT,
                0
            )
            .putLong(
                KEY_AUTO_SESSION_ID,
                newSessionId
            )
            .putLong(
                KEY_AUTO_NEXT_SAVE_MS,
                startAt
            )
            .apply()
    }

    SideEffect {
        UvirRemoteRuntime.snapshot.set(
            UvirRemoteSnapshot(
                measurement =
                    latestMeasurement.get(),
                liveReady = liveReady,
                autoEnabled = autoEnabled,
                autoIntervalSeconds =
                    autoIntervalSeconds,
                autoCompletedCount =
                    autoCompletedCount,
                autoLimitEnabled =
                    autoLimitEnabled,
                autoMaxCount =
                    autoMaxCount,
                autoNextSaveMs =
                    autoNextSaveMs,
                screen = screen
            )
        )
    }

    LaunchedEffect(database) {
        for (
            command in
            UvirRemoteRuntime.commands
        ) {
            val response =
                try {
                    val payload =
                        command.request
                            .optJSONObject(
                                "payload"
                            ) ?: JSONObject()

                    when (command.action) {
                        "save_measurement" -> {
                            if (!liveReady) {
                                throw IllegalStateException(
                                    "Misurazione non ancora pronta."
                                )
                            }

                            val id =
                                database.saveMeasurement(
                                    sample =
                                        latestMeasurement.get(),
                                    note =
                                        payload.optString(
                                            "note",
                                            ""
                                        ).trim(),
                                    automatic = false
                                )

                            if (id == -1L) {
                                throw IllegalStateException(
                                    "Salvataggio non riuscito."
                                )
                            }

                            remoteOk(
                                JSONObject()
                                    .put("id", id)
                            )
                        }

                        "start_auto" -> {
                            if (autoEnabled) {
                                throw IllegalStateException(
                                    "Acquisizione automatica già attiva."
                                )
                            }

                            val intervalSeconds =
                                payload.optLong(
                                    "interval_seconds",
                                    0L
                                )

                            if (intervalSeconds <= 0L) {
                                throw IllegalArgumentException(
                                    "Intervallo non valido."
                                )
                            }

                            val limitEnabled =
                                payload.optBoolean(
                                    "limit_enabled",
                                    false
                                )

                            val maxAcquisitions =
                                payload.optInt(
                                    "max_acquisitions",
                                    1
                                )

                            if (
                                limitEnabled &&
                                maxAcquisitions <= 0
                            ) {
                                throw IllegalArgumentException(
                                    "Numero massimo non valido."
                                )
                            }

                            startAutomaticAcquisition(
                                AutomaticAcquisitionRequest(
                                    intervalSeconds =
                                        intervalSeconds,
                                    note =
                                        payload.optString(
                                            "note",
                                            ""
                                        ).trim(),
                                    useStartDelay =
                                        payload.optBoolean(
                                            "use_start_delay",
                                            false
                                        ),
                                    startDelaySeconds =
                                        payload.optLong(
                                            "start_delay_seconds",
                                            0L
                                        ).coerceAtLeast(0L),
                                    useDuration =
                                        payload.optBoolean(
                                            "use_duration",
                                            false
                                        ),
                                    durationSeconds =
                                        payload.optLong(
                                            "duration_seconds",
                                            0L
                                        ).coerceAtLeast(0L),
                                    limitEnabled =
                                        limitEnabled,
                                    maxAcquisitions =
                                        maxAcquisitions
                                            .coerceAtLeast(1)
                                )
                            )

                            remoteOk(
                                JSONObject()
                                    .put("started", true)
                            )
                        }

                        "stop_auto" -> {
                            stopAutomaticAcquisition()

                            remoteOk(
                                JSONObject()
                                    .put("stopped", true)
                            )
                        }

                        "list_measurements" -> {
                            remoteOk(
                                recordsToJson(
                                    database
                                        .readAllRecords()
                                )
                                    .put(
                                        "measurement_counter",
                                        database
                                            .currentMeasurementCounter()
                                    )
                                    .put(
                                        "session_counter",
                                        database
                                            .currentAutomaticSessionCounter()
                                    )
                            )
                        }

                        "update_measurement" -> {
                            val record =
                                payload
                                    .getJSONObject(
                                        "record"
                                    )
                                    .toSavedRecordDetail()

                            val updated =
                                database.updateMeasurement(
                                    record
                                )

                            remoteOk(
                                JSONObject()
                                    .put(
                                        "updated",
                                        updated
                                    )
                            )
                        }

                        "delete_measurements" -> {
                            val idsJson =
                                payload.getJSONArray(
                                    "ids"
                                )

                            val ids =
                                buildList {
                                    for (
                                        index in
                                        0 until idsJson.length()
                                    ) {
                                        idsJson.optLong(
                                            index,
                                            -1L
                                        ).takeIf {
                                            it > 0L
                                        }?.let(::add)
                                    }
                                }.distinct()

                            val deleted =
                                database.deleteRecords(ids)

                            remoteOk(
                                JSONObject()
                                    .put(
                                        "deleted",
                                        deleted
                                    )
                            )
                        }

                        "delete_all" -> {
                            val deleted =
                                database
                                    .deleteAllMeasurements()

                            remoteOk(
                                JSONObject()
                                    .put(
                                        "deleted",
                                        deleted
                                    )
                            )
                        }

                        "reset_counters" -> {
                            if (autoEnabled) {
                                throw IllegalStateException(
                                    "Ferma prima l'acquisizione automatica."
                                )
                            }

                            val deleted =
                                database.resetAllCounters()
                            autoSessionId = 0L
                            autoCompletedCount = 0
                            preferences.edit()
                                .putLong(
                                    KEY_AUTO_SESSION_ID,
                                    0L
                                )
                                .putInt(
                                    KEY_AUTO_COMPLETED_COUNT,
                                    0
                                )
                                .apply()

                            remoteOk(
                                JSONObject()
                                    .put("deleted", deleted)
                                    .put("measurement_counter", 0)
                                    .put("session_counter", 0)
                            )
                        }

                        "replace_measurements" -> {
                            val recordsJson =
                                payload.getJSONArray(
                                    "records"
                                )

                            if (
                                recordsJson.length() >
                                100_000
                            ) {
                                throw IllegalArgumentException(
                                    "Troppe misurazioni."
                                )
                            }

                            val records =
                                buildList {
                                    for (
                                        index in
                                        0 until recordsJson.length()
                                    ) {
                                        add(
                                            recordsJson
                                                .getJSONObject(index)
                                                .toSavedRecordDetail()
                                        )
                                    }
                                }

                            val replaced =
                                database
                                    .replaceAllMeasurements(
                                        records,
                                        measurementCounter =
                                            payload.optLong(
                                                "measurement_counter",
                                                0L
                                            ),
                                        sessionCounter =
                                            payload.optLong(
                                                "session_counter",
                                                0L
                                            )
                                    )

                            remoteOk(
                                JSONObject()
                                    .put(
                                        "replaced",
                                        replaced
                                    )
                            )
                        }

                        "open_screen" -> {
                            when (
                                payload.optString(
                                    "screen",
                                    "live"
                                ).lowercase()
                            ) {
                                "live" -> {
                                    selectedRecordId = null
                                    screen = AppScreen.LIVE
                                }

                                "measurements",
                                "history" -> {
                                    selectedRecordId = null
                                    screen = AppScreen.HISTORY
                                }

                                "detail" -> {
                                    val id =
                                        payload.optLong(
                                            "id",
                                            -1L
                                        )

                                    if (
                                        id <= 0L ||
                                        database.readRecord(id) == null
                                    ) {
                                        throw IllegalArgumentException(
                                            "Misurazione non trovata."
                                        )
                                    }

                                    selectedRecordId = id
                                    screen = AppScreen.DETAIL
                                }

                                else -> {
                                    throw IllegalArgumentException(
                                        "Schermata non valida."
                                    )
                                }
                            }

                            remoteOk(
                                JSONObject()
                                    .put(
                                        "screen",
                                        screen.name.lowercase()
                                    )
                            )
                        }

                        else -> {
                            remoteError(
                                "Azione non supportata: ${command.action}"
                            )
                        }
                    }
                } catch (error: Exception) {
                    remoteError(
                        error.message ?:
                        error.javaClass.simpleName
                    )
                }

            command.response.complete(
                response
            )
        }
    }

    LaunchedEffect(
        autoEnabled,
        autoIntervalSeconds,
        autoNote,
        autoUseDuration,
        autoEndMs,
        autoLimitEnabled,
        autoMaxCount,
        autoCompletedCount,
        autoNextSaveMs,
        liveReady
    ) {

        if (
            !autoEnabled ||
            autoIntervalSeconds <= 0L
        ) {
            return@LaunchedEffect
        }

        val intervalMs =
            autoIntervalSeconds
                .coerceAtMost(
                    Long.MAX_VALUE / 1000L
                ) * 1000L

        while (autoEnabled) {

            val now =
                System.currentTimeMillis()

            if (
                autoUseDuration &&
                autoEndMs > 0L &&
                now >= autoEndMs
            ) {
                stopAutomaticAcquisition()
                break
            }

            if (
                autoLimitEnabled &&
                autoCompletedCount >=
                autoMaxCount
            ) {
                stopAutomaticAcquisition()
                break
            }

            if (now < autoNextSaveMs) {
                delay(
                    minOf(
                        1000L,
                        autoNextSaveMs - now
                    )
                )
                continue
            }

            if (!liveReady) {
                delay(250L)
                continue
            }

            val acquisitionNumber =
                autoCompletedCount + 1

            val automaticMeasurementNote =
                formatAutomaticMeasurementNote(
                    autoNote,
                    acquisitionNumber
                )

            val measurementSessionId =
                autoSessionId
                    .takeIf { it > 0L }
                    ?: System.currentTimeMillis()
                        .also { generatedId ->
                            autoSessionId = generatedId

                            preferences.edit()
                                .putLong(
                                    KEY_AUTO_SESSION_ID,
                                    generatedId
                                )
                                .apply()
                        }

            val result =
                database.saveMeasurement(
                    sample =
                        latestMeasurement.get(),
                    note =
                        automaticMeasurementNote,
                    automatic =
                        true,
                    automaticSessionId =
                        measurementSessionId,
                    automaticSequence =
                        acquisitionNumber
                )

            if (result != -1L) {

                val newCount =
                    acquisitionNumber

                autoCompletedCount =
                    newCount

                val newNext =
                    maxOf(
                        autoNextSaveMs +
                                intervalMs,
                        System.currentTimeMillis() +
                                intervalMs
                    )

                autoNextSaveMs =
                    newNext

                preferences.edit()
                    .putInt(
                        KEY_AUTO_COMPLETED_COUNT,
                        newCount
                    )
                    .putLong(
                        KEY_AUTO_NEXT_SAVE_MS,
                        newNext
                    )
                    .apply()

                if (
                    autoLimitEnabled &&
                    newCount >= autoMaxCount
                ) {
                    stopAutomaticAcquisition()
                    break
                }
            }

            delay(200L)
        }
    }

    when (screen) {

        AppScreen.LIVE -> {

            LiveScreen(
                database = database,
                measurement = measurement,
                liveReady = liveReady,
                liveListState = liveListState,
                automaticListState =
                    automaticListState,
                versionInfoScrollState =
                    versionInfoScrollState,
                parametersScrollState =
                    parametersScrollState,

                viewMode = viewMode,
                onViewModeChanged = {
                    setViewMode(it)
                },

                samplesPerMeasurement =
                    samplesPerMeasurement,
                sampleSpacingMs =
                    sampleSpacingMs,
                discardExtremes =
                    discardExtremes,

                onApplyAcquisitionParameters = {
                        params ->

                    samplesPerMeasurement =
                        params.samplesPerMeasurement

                    sampleSpacingMs =
                        params.sampleSpacingMs

                    discardExtremes =
                        params.discardExtremes

                    preferences.edit()
                        .putInt(
                            KEY_SAMPLES_PER_MEASUREMENT,
                            params.samplesPerMeasurement
                        )
                        .putLong(
                            KEY_SAMPLE_SPACING_MS,
                            params.sampleSpacingMs
                        )
                        .putBoolean(
                            KEY_DISCARD_EXTREMES,
                            params.discardExtremes
                        )
                        .apply()
                },

                autoEnabled =
                    autoEnabled,
                autoIntervalSeconds =
                    autoIntervalSeconds,
                autoNote =
                    autoNote,
                autoUseStartDelay =
                    autoUseStartDelay,
                autoStartDelaySeconds =
                    autoStartDelaySeconds,
                autoUseDuration =
                    autoUseDuration,
                autoDurationSeconds =
                    autoDurationSeconds,
                autoLimitEnabled =
                    autoLimitEnabled,
                autoMaxCount =
                    autoMaxCount,
                autoCompletedCount =
                    autoCompletedCount,
                autoNextSaveMs =
                    autoNextSaveMs,

                onStartAutomaticAcquisition = {
                        request ->
                    startAutomaticAcquisition(
                        request
                    )
                },

                onStopAutomaticAcquisition = {
                    stopAutomaticAcquisition()
                },

                onResetCounters = {
                    if (!autoEnabled) {
                        database.resetAllCounters()
                        autoSessionId = 0L
                        autoCompletedCount = 0
                        selectedRecordId = null
                        preferences.edit()
                            .putLong(
                                KEY_AUTO_SESSION_ID,
                                0L
                            )
                            .putInt(
                                KEY_AUTO_COMPLETED_COUNT,
                                0
                            )
                            .apply()
                    }
                },

                remoteNetworkEnabled =
                    remoteNetworkEnabled,

                backgroundColor =
                    backgroundColor,
                cardColor =
                    cardColor,
                primaryText =
                    primaryText,
                secondaryText =
                    secondaryText,
                trackColor =
                    trackColor,

                onOpenHistory = {
                    screen =
                        AppScreen.HISTORY
                }
            )
        }

        AppScreen.HISTORY -> {

            HistoryScreen(
                database = database,
                historyListState =
                    historyListState,
                backgroundColor =
                    backgroundColor,
                cardColor =
                    cardColor,
                primaryText =
                    primaryText,
                secondaryText =
                    secondaryText,

                onBack = {
                    screen =
                        AppScreen.LIVE
                },

                onOpenRecord = { record ->

                    selectedRecordId =
                        record.id

                    screen =
                        AppScreen.DETAIL
                }
            )
        }

        AppScreen.DETAIL -> {

            val record =
                remember(
                    selectedRecordId,
                    database
                ) {
                    selectedRecordId?.let {
                        database.readRecord(it)
                    }
                }

            if (record != null) {

                RecordDetailScreen(
                    record = record,
                    database = database,
                    detailListState =
                        detailListState,

                    viewMode = viewMode,
                    onViewModeChanged = {
                        setViewMode(it)
                    },

                    backgroundColor =
                        backgroundColor,
                    cardColor =
                        cardColor,
                    primaryText =
                        primaryText,
                    secondaryText =
                        secondaryText,
                    trackColor =
                        trackColor,

                    onBack = {
                        screen =
                            AppScreen.HISTORY
                    },

                    onDeleted = {
                        selectedRecordId =
                            null

                        screen =
                            AppScreen.HISTORY
                    }
                )

            } else {

                screen =
                    AppScreen.HISTORY
            }
        }
    }
}

// =====================================================
// SCHERMATA LIVE
// =====================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveScreen(
    database: UvirDatabaseHelper,
    measurement: SensorSample,
    liveReady: Boolean,
    liveListState: LazyListState,
    automaticListState: LazyListState,
    versionInfoScrollState: ScrollState,
    parametersScrollState: ScrollState,

    viewMode: ViewMode,
    onViewModeChanged: (ViewMode) -> Unit,

    samplesPerMeasurement: Int,
    sampleSpacingMs: Long,
    discardExtremes: Boolean,
    onApplyAcquisitionParameters:
        (AcquisitionParameters) -> Unit,

    autoEnabled: Boolean,
    autoIntervalSeconds: Long,
    autoNote: String,
    autoUseStartDelay: Boolean,
    autoStartDelaySeconds: Long,
    autoUseDuration: Boolean,
    autoDurationSeconds: Long,
    autoLimitEnabled: Boolean,
    autoMaxCount: Int,
    autoCompletedCount: Int,
    autoNextSaveMs: Long,

    onStartAutomaticAcquisition:
        (AutomaticAcquisitionRequest) -> Unit,

    onStopAutomaticAcquisition:
        () -> Unit,

    onResetCounters:
        () -> Unit,

    remoteNetworkEnabled: Boolean,

    backgroundColor: Color,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    trackColor: Color,

    onOpenHistory: () -> Unit
) {

    val context =
        LocalContext.current

    val remotePin =
        remember(context) {
            getOrCreateRemotePin(
                context.applicationContext
            )
        }

    var remoteAddresses by remember {
        mutableStateOf(
            localIpv4Addresses(
                context.applicationContext
            )
        )
    }

    val groups = remember {
        mutableStateListOf<SensorGroup>()
            .apply {
                addAll(
                    loadGroupOrder(
                        context
                    )
                )
            }
    }

    val expandedStates = remember {
        mutableStateMapOf<
                SensorGroup,
                Boolean
                >().apply {

            SensorGroup.values()
                .forEach { group ->

                    this[group] =
                        loadExpandedState(
                            context,
                            group
                        )
                }
        }
    }

    var showSaveDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showAutomaticDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showStopConfirmation by rememberSaveable {
        mutableStateOf(false)
    }

    var showParametersDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showResetCountersConfirmation by rememberSaveable {
        mutableStateOf(false)
    }

    var showVersionInfoDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var note by rememberSaveable {
        mutableStateOf("")
    }

    // -------------------------------------------------
    // AUTOMATIC ACQUISITION DIALOG STATE
    // -------------------------------------------------

    var timerHours by rememberSaveable {
        mutableStateOf("0")
    }

    var timerMinutes by rememberSaveable {
        mutableStateOf("1")
    }

    var timerSeconds by rememberSaveable {
        mutableStateOf("0")
    }

    var timerNote by rememberSaveable {
        mutableStateOf("")
    }

    var useStartDelay by rememberSaveable {
        mutableStateOf(false)
    }

    var startDelayHoursText by rememberSaveable {
        mutableStateOf("0")
    }

    var startDelayMinutesText by rememberSaveable {
        mutableStateOf("5")
    }

    var startDelaySecondsText by rememberSaveable {
        mutableStateOf("0")
    }

    var useDuration by rememberSaveable {
        mutableStateOf(false)
    }

    var durationHoursText by rememberSaveable {
        mutableStateOf("1")
    }

    var durationMinutesText by rememberSaveable {
        mutableStateOf("0")
    }

    var durationSecondsText by rememberSaveable {
        mutableStateOf("0")
    }

    var limitEnabled by rememberSaveable {
        mutableStateOf(false)
    }

    var maxCountText by rememberSaveable {
        mutableStateOf("10")
    }

    var timerError by rememberSaveable {
        mutableStateOf<String?>(
            null
        )
    }

    // -------------------------------------------------
    // ACQUISITION PARAMETERS DIALOG STATE
    // -------------------------------------------------

    var samplesText by rememberSaveable {
        mutableStateOf(
            samplesPerMeasurement
                .toString()
        )
    }

    var spacingText by rememberSaveable {
        mutableStateOf(
            sampleSpacingMs
                .toString()
        )
    }

    var trimEnabled by rememberSaveable {
        mutableStateOf(
            discardExtremes
        )
    }

    var parametersError by rememberSaveable {
        mutableStateOf<String?>(
            null
        )
    }

    var samplingSectionExpanded by rememberSaveable {
        mutableStateOf(
            loadSettingsSectionExpanded(
                context,
                KEY_SETTINGS_SAMPLING_EXPANDED,
                true
            )
        )
    }

    var usbSectionExpanded by rememberSaveable {
        mutableStateOf(
            loadSettingsSectionExpanded(
                context,
                KEY_SETTINGS_USB_EXPANDED,
                false
            )
        )
    }

    var bluetoothSectionExpanded by rememberSaveable {
        mutableStateOf(
            loadSettingsSectionExpanded(
                context,
                KEY_SETTINGS_BLUETOOTH_EXPANDED,
                false
            )
        )
    }

    var wifiSectionExpanded by rememberSaveable {
        mutableStateOf(
            loadSettingsSectionExpanded(
                context,
                KEY_SETTINGS_WIFI_EXPANDED,
                false
            )
        )
    }

    var mobileSectionExpanded by rememberSaveable {
        mutableStateOf(
            loadSettingsSectionExpanded(
                context,
                KEY_SETTINGS_MOBILE_EXPANDED,
                false
            )
        )
    }

    var countersSectionExpanded by rememberSaveable {
        mutableStateOf(
            loadSettingsSectionExpanded(
                context,
                KEY_SETTINGS_COUNTERS_EXPANDED,
                false
            )
        )
    }

    val homeMenuPinned by remember {
        derivedStateOf {
            liveListState.firstVisibleItemIndex >= 1
        }
    }

    LaunchedEffect(
        showParametersDialog
    ) {
        while (showParametersDialog) {
            remoteAddresses =
                localIpv4Addresses(
                    context.applicationContext
                )

            delay(2_000L)
        }
    }

    val invalidIntervalText =
        stringResource(
            R.string.invalid_interval
        )

    val invalidScheduleText =
        stringResource(
            R.string.invalid_schedule
        )

    val invalidParametersText =
        stringResource(
            R.string.invalid_acquisition_parameters
        )

    val automaticStartedText =
        stringResource(
            R.string.automatic_started
        )

    val automaticStoppedText =
        stringResource(
            R.string.automatic_stopped
        )

    val parametersSavedText =
        stringResource(
            R.string.parameters_saved
        )

    val versionInfoDescription =
        stringResource(
            R.string.version_info
        )

    val uvTotal =
        measurement.uvc +
                measurement.uvb +
                measurement.uva

    val visibleTotal =
        measurement.violetto +
                measurement.blu +
                measurement.verde +
                measurement.giallo +
                measurement.arancione +
                measurement.rosso

    val nirTotal =
        measurement.f8 +
                measurement.nir

    val heb =
        measurement.violetto

    val hev =
        measurement.violetto +
                measurement.blu

    fun openAutomaticAcquisitionPage() {

        val hours =
            autoIntervalSeconds /
                    3600L

        val minutes =
            (
                    autoIntervalSeconds %
                            3600L
                    ) / 60L

        val seconds =
            autoIntervalSeconds %
                    60L

        timerHours =
            hours.toString()

        timerMinutes =
            minutes.toString()

        timerSeconds =
            seconds.toString()

        timerNote =
            autoNote

        useStartDelay =
            autoUseStartDelay

        startDelayHoursText =
            (
                    autoStartDelaySeconds /
                            3600L
                    ).toString()

        startDelayMinutesText =
            (
                    autoStartDelaySeconds %
                            3600L /
                            60L
                    ).toString()

        startDelaySecondsText =
            (
                    autoStartDelaySeconds %
                            60L
                    ).toString()

        useDuration =
            autoUseDuration

        durationHoursText =
            (
                    autoDurationSeconds /
                            3600L
                    ).toString()

        durationMinutesText =
            (
                    autoDurationSeconds %
                            3600L /
                            60L
                    ).toString()

        durationSecondsText =
            (
                    autoDurationSeconds %
                            60L
                    ).toString()

        limitEnabled =
            autoLimitEnabled

        maxCountText =
            autoMaxCount.toString()

        timerError =
            null

        showAutomaticDialog =
            true
    }

    // -------------------------------------------------
    // MANUAL SAVE
    // -------------------------------------------------

    if (showSaveDialog) {

        val measurementSavedText =
            stringResource(
                R.string.measurement_saved
            )

        val saveErrorText =
            stringResource(
                R.string.save_error
            )

        AlertDialog(
            onDismissRequest = {
                showSaveDialog =
                    false
            },

            title = {
                Text(
                    stringResource(
                        R.string.save_measurement
                    )
                )
            },

            text = {

                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        )
                ) {

                    Text(
                        stringResource(
                            R.string.add_note_or_leave_empty
                        )
                    )

                    OutlinedTextField(
                        value = note,

                        onValueChange = {
                            note = it
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        label = {
                            AdaptiveFieldLabel(
                                stringResource(
                                    R.string.optional_note
                                )
                            )
                        },

                        placeholder = {
                            Text(
                                stringResource(
                                    R.string.note_example
                                )
                            )
                        },

                        colors =
                            UvirOutlinedTextFieldColors(),

                        minLines = 3
                    )
                }
            },

            confirmButton = {

                TextButton(
                    onClick = {

                        val result =
                            database.saveMeasurement(
                                sample =
                                    measurement,
                                note =
                                    note.trim(),
                                automatic =
                                    false
                            )

                        Toast.makeText(
                            context,

                            if (result != -1L)
                                measurementSavedText
                            else
                                saveErrorText,

                            Toast.LENGTH_SHORT
                        ).show()

                        note = ""
                        showSaveDialog =
                            false
                    }
                ) {
                    Text(
                        stringResource(
                            R.string.save
                        )
                    )
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {

                        note = ""

                        showSaveDialog =
                            false
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor =
                                Color(0xFFD32F2F)
                        )
                ) {
                    Text(
                        stringResource(
                            R.string.cancel
                        )
                    )
                }
            },

            containerColor = cardColor,
            titleContentColor = primaryText,
            textContentColor = secondaryText
        )
    }

    // -------------------------------------------------
    // STOP AUTOMATIC ACQUISITION CONFIRMATION
    // -------------------------------------------------

    if (showStopConfirmation) {

        AlertDialog(
            onDismissRequest = {
                showStopConfirmation = false
            },

            title = {
                Text(
                    stringResource(
                        R.string.stop_automatic_confirmation_title
                    )
                )
            },

            text = {
                Text(
                    stringResource(
                        R.string.stop_automatic_confirmation_message
                    )
                )
            },

            dismissButton = {

                TextButton(
                    onClick = {

                        onStopAutomaticAcquisition()

                        Toast.makeText(
                            context,
                            automaticStoppedText,
                            Toast.LENGTH_SHORT
                        ).show()

                        showStopConfirmation = false
                        showAutomaticDialog = false
                    },

                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFFD32F2F)
                        )
                ) {
                    Text(
                        stringResource(
                            R.string.stop
                        )
                    )
                }
            },

            confirmButton = {

                TextButton(
                    onClick = {
                        showStopConfirmation = false
                    }
                ) {
                    Text(
                        stringResource(
                            R.string.continue_acquisition
                        )
                    )
                }
            },

            containerColor = cardColor,
            titleContentColor = primaryText,
            textContentColor = secondaryText
        )
    }

    // -------------------------------------------------
    // AUTOMATIC ACQUISITION
    // -------------------------------------------------

    if (showAutomaticDialog) {

        UvirFullScreenPage(
            onDismissRequest = {

                timerError =
                    null

                showAutomaticDialog =
                    false
            },

            title = {
                UvirMenuTitle(
                    text =
                        stringResource(
                            R.string.automatic_acquisition_title
                        )
                )
            },

            containerColor =
                backgroundColor,

            contentColor =
                primaryText,

            text = {

                LazyColumn(
                    state =
                        automaticListState,
                    modifier =
                        Modifier
                            .fillMaxSize(),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            UvirIslandSpacing
                        )
                ) {

                    item {

                        SettingsIsland(
                            containerColor = cardColor,
                            contentColor = primaryText
                        ) {

                            Text(
                                text =
                                    stringResource(
                                        R.string.interval
                                    ),

                                fontWeight =
                                    FontWeight.Bold
                            )

                            HorizontalDivider(
                                color =
                                    secondaryText.copy(
                                        alpha = 0.28f
                                    )
                            )

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth(),

                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        8.dp
                                    )
                            ) {

                                NumberField(
                                    value =
                                        timerHours,
                                    onValueChange = {
                                        timerHours = it
                                    },
                                    label =
                                        stringResource(
                                            R.string.hours
                                        ),
                                    modifier =
                                        Modifier.weight(1f),
                                    maxChars = 5
                                )

                                NumberField(
                                    value =
                                        timerMinutes,
                                    onValueChange = {
                                        timerMinutes = it
                                    },
                                    label =
                                        stringResource(
                                            R.string.minutes
                                        ),
                                    modifier =
                                        Modifier.weight(1f),
                                    maxChars = 2
                                )

                                NumberField(
                                    value =
                                        timerSeconds,
                                    onValueChange = {
                                        timerSeconds = it
                                    },
                                    label =
                                        stringResource(
                                            R.string.seconds
                                        ),
                                    modifier =
                                        Modifier.weight(1f),
                                    maxChars = 2
                                )
                            }
                        }
                    }

                    item {

                        SettingsIsland(
                            containerColor = cardColor,
                            contentColor = primaryText
                        ) {

                            CheckSettingRow(
                                checked =
                                    useStartDelay,
                                onCheckedChange = {
                                    useStartDelay = it
                                },
                                title =
                                    stringResource(
                                        R.string.scheduled_start
                                    ),
                                emphasized = useStartDelay,
                                compact = true
                            )

                            if (useStartDelay) {

                                HorizontalDivider(
                                    color =
                                        secondaryText.copy(
                                            alpha = 0.28f
                                        )
                                )

                                DurationFields(
                                    hoursText =
                                        startDelayHoursText,
                                    minutesText =
                                        startDelayMinutesText,
                                    secondsText =
                                        startDelaySecondsText,
                                    onHoursChange = {
                                        startDelayHoursText = it
                                    },
                                    onMinutesChange = {
                                        startDelayMinutesText = it
                                    },
                                    onSecondsChange = {
                                        startDelaySecondsText = it
                                    }
                                )
                            }
                        }
                    }

                    item {

                        SettingsIsland(
                            containerColor = cardColor,
                            contentColor = primaryText
                        ) {

                            CheckSettingRow(
                                checked =
                                    useDuration,
                                onCheckedChange = {
                                    useDuration = it
                                },
                                title =
                                    stringResource(
                                        R.string.scheduled_end
                                    ),
                                emphasized = useDuration,
                                compact = true
                            )

                            if (useDuration) {

                                HorizontalDivider(
                                    color =
                                        secondaryText.copy(
                                            alpha = 0.28f
                                        )
                                )

                                DurationFields(
                                    hoursText =
                                        durationHoursText,
                                    minutesText =
                                        durationMinutesText,
                                    secondsText =
                                        durationSecondsText,
                                    onHoursChange = {
                                        durationHoursText = it
                                    },
                                    onMinutesChange = {
                                        durationMinutesText = it
                                    },
                                    onSecondsChange = {
                                        durationSecondsText = it
                                    }
                                )
                            }
                        }
                    }

                    item {

                        SettingsIsland(
                            containerColor = cardColor,
                            contentColor = primaryText
                        ) {

                            CheckSettingRow(
                                checked =
                                    limitEnabled,
                                onCheckedChange = {
                                    limitEnabled = it
                                },
                                title =
                                    stringResource(
                                        R.string.limit_acquisitions
                                    ),
                                emphasized = limitEnabled,
                                compact = true
                            )

                            if (limitEnabled) {

                                HorizontalDivider(
                                    color =
                                        secondaryText.copy(
                                            alpha = 0.28f
                                        )
                                )

                                NumberField(
                                    value =
                                        maxCountText,
                                    onValueChange = {
                                        maxCountText = it
                                    },
                                    label =
                                        stringResource(
                                            R.string.number_of_acquisitions
                                        ),
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    maxChars = 6
                                )
                            }
                        }
                    }

                    item {

                        SettingsIsland(
                            containerColor = cardColor,
                            contentColor = primaryText
                        ) {

                            OutlinedTextField(
                                value =
                                    timerNote,

                                onValueChange = {
                                    timerNote = it
                                },

                                modifier =
                                    Modifier.fillMaxWidth(),

                                label = {
                                    AdaptiveFieldLabel(
                                        stringResource(
                                            R.string.default_note
                                        )
                                    )
                                },

                                colors =
                                    UvirOutlinedTextFieldColors(),

                                minLines = 2
                            )

                            Text(
                                text =
                                    stringResource(
                                        R.string.default_note_hint
                                    ),
                                modifier =
                                    Modifier.fillMaxWidth(),
                                color =
                                    secondaryText,
                                fontSize =
                                    12.sp
                            )
                        }
                    }

                    timerError?.let { error ->

                        item {

                            Text(
                                text = error,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .error,
                                fontSize =
                                    12.sp
                            )
                        }
                    }

                }
            },

            contentOverlay = {
                Box(
                    modifier =
                        Modifier
                            .align(
                                Alignment.CenterEnd
                            )
                            .fillMaxHeight()
                            .width(16.dp)
                            .lazyScrollbarOverlay(
                                state =
                                    automaticListState,
                                color =
                                    secondaryText.copy(
                                        alpha = 0.46f
                                    )
                            )
                )
            },

            actionButton = {

                if (autoEnabled) {

                    Button(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),

                        onClick = {

                            timerError =
                                null

                            showStopConfirmation =
                                true
                        },

                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    Color(
                                        0xFFD32F2F
                                    ),
                                contentColor =
                                    Color.White
                            )
                    ) {

                        Text(
                            stringResource(
                                R.string.stop_with_count,
                                autoCompletedCount
                            )
                        )
                    }

                } else {

                    Button(
                        modifier =
                            Modifier.fillMaxWidth(),

                        onClick = {

                            val hours =
                                timerHours
                                    .toLongOrNull()
                                    ?: 0L

                            val minutes =
                                timerMinutes
                                    .toLongOrNull()
                                    ?: 0L

                            val seconds =
                                timerSeconds
                                    .toLongOrNull()
                                    ?: 0L

                            val validInterval =
                                minutes in 0L..59L &&
                                        seconds in 0L..59L

                            val totalSeconds =
                                if (validInterval) {

                                    hours
                                        .coerceAtMost(
                                            100000L
                                        ) * 3600L +
                                            minutes * 60L +
                                            seconds

                                } else {
                                    0L
                                }

                            val startDelayHours =
                                startDelayHoursText
                                    .toLongOrNull()
                                    ?: 0L

                            val startDelayMinutes =
                                startDelayMinutesText
                                    .toLongOrNull()
                                    ?: 0L

                            val startDelaySeconds =
                                startDelaySecondsText
                                    .toLongOrNull()
                                    ?: 0L

                            val validStartDelay =
                                startDelayMinutes in 0L..59L &&
                                        startDelaySeconds in 0L..59L

                            val totalStartDelaySeconds =
                                if (validStartDelay) {

                                    startDelayHours
                                        .coerceAtMost(
                                            100000L
                                        ) * 3600L +
                                            startDelayMinutes * 60L +
                                            startDelaySeconds

                                } else {
                                    0L
                                }

                            val durationHours =
                                durationHoursText
                                    .toLongOrNull()
                                    ?: 0L

                            val durationMinutes =
                                durationMinutesText
                                    .toLongOrNull()
                                    ?: 0L

                            val durationSeconds =
                                durationSecondsText
                                    .toLongOrNull()
                                    ?: 0L

                            val validDuration =
                                durationMinutes in 0L..59L &&
                                        durationSeconds in 0L..59L

                            val totalDurationSeconds =
                                if (validDuration) {

                                    durationHours
                                        .coerceAtMost(
                                            100000L
                                        ) * 3600L +
                                            durationMinutes * 60L +
                                            durationSeconds

                                } else {
                                    0L
                                }

                            val maxCount =
                                maxCountText
                                    .toIntOrNull()
                                    ?: 0

                            val validStart =
                                !useStartDelay ||
                                        (
                                                validStartDelay &&
                                                        totalStartDelaySeconds > 0L
                                                )

                            val validEnd =
                                !useDuration ||
                                        (
                                                validDuration &&
                                                        totalDurationSeconds > 0L
                                                )

                            val validLimit =
                                !limitEnabled ||
                                        maxCount > 0

                            when {

                                !validInterval ||
                                        totalSeconds <= 0L -> {

                                    timerError =
                                        invalidIntervalText
                                }

                                !validStart ||
                                        !validEnd ||
                                        !validLimit -> {

                                    timerError =
                                        invalidScheduleText
                                }

                                else -> {

                                    onStartAutomaticAcquisition(
                                        AutomaticAcquisitionRequest(
                                            intervalSeconds =
                                                totalSeconds,
                                            note =
                                                timerNote.trim(),
                                            useStartDelay =
                                                useStartDelay,
                                            startDelaySeconds =
                                                totalStartDelaySeconds,
                                            useDuration =
                                                useDuration,
                                            durationSeconds =
                                                totalDurationSeconds,
                                            limitEnabled =
                                                limitEnabled,
                                            maxAcquisitions =
                                                maxCount
                                                    .coerceAtLeast(
                                                        1
                                                    )
                                        )
                                    )

                                    Toast.makeText(
                                        context,
                                        automaticStartedText,
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    timerError =
                                        null

                                    showAutomaticDialog =
                                        false
                                }
                            }
                        }
                    ) {
                        Text(
                            stringResource(
                                R.string.start
                            )
                        )
                    }
                }
            }
        )
        return
    }

// -------------------------------------------------
// VERSION & INFO
// -------------------------------------------------

if (showVersionInfoDialog) {

    val githubRepositoryUrl =
        BuildConfig.GITHUB_REPOSITORY_URL
            .trim()
            .trimEnd('/')

    UvirFullScreenPage(
        onDismissRequest = {
            showVersionInfoDialog =
                false
        },

        title = {

            UvirMenuTitle(
                text =
                    stringResource(
                        R.string.version_info_title,
                        BuildConfig.VERSION_NAME
                    )
            )
        },

        containerColor =
            backgroundColor,

        contentColor =
            primaryText,

        scrollState =
            versionInfoScrollState,

        scrollbarColor =
            secondaryText.copy(
                alpha = 0.46f
            ),

        text = {

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(
                            versionInfoScrollState
                        ),

                verticalArrangement =
                    Arrangement.spacedBy(
                        UvirIslandSpacing
                    )
            ) {

                Surface(
                    modifier =
                        Modifier.fillMaxWidth(),
                    shape =
                        RoundedCornerShape(16.dp),
                    color = cardColor,
                    contentColor = primaryText
                ) {
                    Column(
                        modifier =
                            Modifier.padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.about_description
                                ),
                            fontSize = 13.sp
                        )

                        Text(
                            text =
                                stringResource(
                                    R.string.about_derived_values
                                ),
                            color = secondaryText,
                            fontSize = 12.sp
                        )
                    }
                }

                SettingsSection(
                    title =
                        stringResource(
                            R.string.about_whats_new_title,
                            BuildConfig.VERSION_NAME
                        ),
                    containerColor = cardColor,
                    titleColor = primaryText,
                    dividerColor =
                        secondaryText.copy(
                            alpha = 0.28f
                        )
                ) {
                    listOf(
                        stringResource(
                            R.string.about_whats_new_connectivity
                        ),
                        stringResource(
                            R.string.about_whats_new_connections
                        ),
                        stringResource(
                            R.string.about_whats_new_measurements
                        ),
                        stringResource(
                            R.string.about_whats_new_interface
                        ),
                        stringResource(
                            R.string.about_whats_new_github
                        )
                    ).forEach { change ->
                        Text(
                            text = "• $change",
                            color = secondaryText,
                            fontSize = 13.sp
                        )
                    }
                }

                SettingsSection(
                    title =
                        stringResource(
                            R.string.github_repository_title
                        ),
                    containerColor = cardColor,
                    titleColor = primaryText,
                    dividerColor =
                        secondaryText.copy(
                            alpha = 0.28f
                        )
                ) {
                    Text(
                        text =
                            if (githubRepositoryUrl.isBlank()) {
                                stringResource(
                                    R.string.github_repository_pending
                                )
                            } else {
                                githubRepositoryUrl
                            },
                        color = secondaryText,
                        fontSize = 12.sp
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement =
                            Arrangement.spacedBy(2.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(
                                            githubRepositoryUrl
                                        )
                                    )
                                )
                            },
                            enabled =
                                githubRepositoryUrl.isNotBlank(),
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            RepositoryIcon(
                                modifier =
                                    Modifier.size(20.dp)
                            )

                            Spacer(
                                Modifier.width(8.dp)
                            )

                            Text(
                                stringResource(
                                    R.string.open_github_repository
                                )
                            )
                        }

                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(
                                            "$githubRepositoryUrl/releases"
                                        )
                                    )
                                )
                            },
                            enabled =
                                githubRepositoryUrl.isNotBlank(),
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(
                                    R.string.check_for_updates
                                )
                            )
                        }
                    }
                }

                Surface(
                    modifier =
                        Modifier.fillMaxWidth(),
                    shape =
                        RoundedCornerShape(16.dp),
                    color = cardColor,
                    contentColor = primaryText
                ) {
                    Column(
                        modifier =
                            Modifier.padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text =
                                stringResource(
                                    R.string.biological_model_version,
                                    BIOLOGICAL_MODEL_VERSION
                                ),
                            fontSize = 12.sp,
                            fontWeight =
                                FontWeight.Medium
                        )

                        Text(
                            text =
                                stringResource(
                                    R.string.about_copyright
                                ),
                            color = secondaryText,
                            fontSize = 11.sp
                        )

                        Text(
                            text =
                                stringResource(
                                    R.string.package_name_value,
                                    context.packageName
                                ),
                            color = secondaryText,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    )
    return
}

// -------------------------------------------------
// ACQUISITION PARAMETERS
// -------------------------------------------------

if (showParametersDialog) {

    if (showResetCountersConfirmation) {
        val countersResetCompleteMessage =
            stringResource(
                R.string.counters_reset_complete
            )

        AlertDialog(
            onDismissRequest = {
                showResetCountersConfirmation = false
            },
            title = {
                Text(
                    stringResource(
                        R.string.reset_counters_confirmation_title
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.reset_counters_confirmation_message
                    )
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    HoldToConfirmDeleteButton(
                        label =
                            stringResource(
                                R.string.reset_counters_action
                            ),
                        onConfirmed = {
                            onResetCounters()
                            showResetCountersConfirmation = false
                            Toast.makeText(
                                context,
                                countersResetCompleteMessage,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )

                    TextButton(
                        onClick = {
                            showResetCountersConfirmation = false
                        }
                    ) {
                        Text(
                            stringResource(
                                R.string.cancel
                            )
                        )
                    }
                }
            },
            containerColor = cardColor,
            titleContentColor = primaryText,
            textContentColor = secondaryText
        )
    }

    UvirFullScreenPage(
        onDismissRequest = {

            parametersError =
                null

            showParametersDialog =
                false
        },

        title = {
            UvirMenuTitle(
                text =
                    stringResource(
                        R.string.acquisition_parameters
                    )
            )
        },

        containerColor =
            backgroundColor,

        contentColor =
            primaryText,

        scrollState =
            parametersScrollState,

        scrollbarColor =
            secondaryText.copy(
                alpha = 0.46f
            ),

        text = {

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(
                            parametersScrollState
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        UvirIslandSpacing
                    )
            ) {

                SettingsSection(
                    title =
                        stringResource(
                            R.string.settings_section_acquisition
                        ),
                    titleIcon =
                        ConnectivityIconType.SAMPLING,
                    expanded =
                        samplingSectionExpanded,
                    onExpandedChange = { expanded ->
                        samplingSectionExpanded = expanded
                        saveSettingsSectionExpanded(
                            context,
                            KEY_SETTINGS_SAMPLING_EXPANDED,
                            expanded
                        )
                    },
                    containerColor =
                        cardColor,
                    titleColor =
                        primaryText,
                    dividerColor =
                        secondaryText.copy(
                            alpha = 0.28f
                        )
                ) {

                NumberField(
                    value =
                        samplesText,
                    onValueChange = {
                        samplesText = it
                    },
                    label =
                        stringResource(
                            R.string.samples_per_measurement
                        ),
                    modifier =
                        Modifier.fillMaxWidth(),
                    maxChars = 2
                )

                NumberField(
                    value =
                        spacingText,
                    onValueChange = {
                        spacingText = it
                    },
                    label =
                        stringResource(
                            R.string.sample_spacing_ms
                        ),
                    modifier =
                        Modifier.fillMaxWidth(),
                    maxChars = 5
                )

                CheckSettingRow(
                    checked =
                        trimEnabled,
                    onCheckedChange = {
                        trimEnabled = it
                    },
                    title =
                        stringResource(
                            R.string.discard_extremes
                        )
                )

                Text(
                    text =
                        stringResource(
                            R.string.parameters_hint
                        ),
                    color =
                        secondaryText,
                    fontSize =
                        12.sp
                )

                parametersError?.let { error ->

                    Text(
                        text = error,
                        color =
                            MaterialTheme
                                .colorScheme
                                .error,
                        fontSize =
                            12.sp
                    )
                }
                }

                val wifiAddresses =
                    remoteAddresses.filter {
                        it.kind == UvirNetworkKind.WIFI
                    }

                val bluetoothAddresses =
                    remoteAddresses.filter {
                        it.kind == UvirNetworkKind.BLUETOOTH
                    }

                val mobileAddresses =
                    remoteAddresses.filter {
                        it.kind == UvirNetworkKind.MOBILE
                    }

                val otherAddresses =
                    remoteAddresses.filter {
                        it.kind == UvirNetworkKind.OTHER
                    }

                SettingsSection(
                    title =
                        stringResource(
                            R.string.remote_usb_title
                        ),
                    titleIcon =
                        ConnectivityIconType.USB,
                    expanded =
                        usbSectionExpanded,
                    onExpandedChange = { expanded ->
                        usbSectionExpanded = expanded
                        saveSettingsSectionExpanded(
                            context,
                            KEY_SETTINGS_USB_EXPANDED,
                            expanded
                        )
                    },
                    containerColor = cardColor,
                    titleColor = primaryText,
                    dividerColor =
                        secondaryText.copy(
                            alpha = 0.28f
                        )
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.remote_usb_description
                            ),
                        color = secondaryText,
                        fontSize = 12.sp
                    )

                }

                SettingsSection(
                    title =
                        stringResource(
                            R.string.remote_bluetooth_title
                        ),
                    titleIcon =
                        ConnectivityIconType.BLUETOOTH,
                    expanded =
                        bluetoothSectionExpanded,
                    onExpandedChange = { expanded ->
                        bluetoothSectionExpanded = expanded
                        saveSettingsSectionExpanded(
                            context,
                            KEY_SETTINGS_BLUETOOTH_EXPANDED,
                            expanded
                        )
                    },
                    containerColor = cardColor,
                    titleColor = primaryText,
                    dividerColor =
                        secondaryText.copy(
                            alpha = 0.28f
                        )
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.remote_bluetooth_phone_title
                            ),
                        color = primaryText,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.remote_bluetooth_phone_steps
                            ),
                        color = secondaryText,
                        fontSize = 12.sp
                    )

                    HorizontalDivider(
                        color =
                            secondaryText.copy(
                                alpha = 0.20f
                            )
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.remote_bluetooth_pc_title
                            ),
                        color = primaryText,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.remote_bluetooth_pc_steps
                            ),
                        color = secondaryText,
                        fontSize = 12.sp
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color =
                            MaterialTheme.colorScheme.primary
                                .copy(alpha = 0.09f),
                        contentColor = primaryText
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(6.dp)
                        ) {
                            if (bluetoothAddresses.isEmpty()) {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.remote_bluetooth_not_available
                                        ),
                                    color = secondaryText,
                                    fontSize = 12.sp
                                )
                            } else {
                                bluetoothAddresses.forEach {
                                        network ->
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.remote_bluetooth_address,
                                                network.address
                                            ),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color =
                            MaterialTheme.colorScheme.primary
                                .copy(alpha = 0.09f),
                        contentColor = primaryText
                    ) {
                        Text(
                            modifier = Modifier.padding(12.dp),
                            text =
                                stringResource(
                                    R.string.remote_pairing_pin,
                                    remotePin
                                ),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text =
                            stringResource(
                                R.string.remote_security_hint
                            ),
                        color = secondaryText,
                        fontSize = 11.sp
                    )

                    if (!remoteNetworkEnabled) {
                        Text(
                            text =
                                stringResource(
                                    R.string.remote_wireless_disabled
                                ),
                            color =
                                MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }
                }

                SettingsSection(
                    title =
                        stringResource(
                            R.string.remote_wifi_title
                        ),
                    titleIcon =
                        ConnectivityIconType.WIFI,
                    expanded =
                        wifiSectionExpanded,
                    onExpandedChange = { expanded ->
                        wifiSectionExpanded = expanded
                        saveSettingsSectionExpanded(
                            context,
                            KEY_SETTINGS_WIFI_EXPANDED,
                            expanded
                        )
                    },
                    containerColor = cardColor,
                    titleColor = primaryText,
                    dividerColor =
                        secondaryText.copy(
                            alpha = 0.28f
                        )
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.remote_wifi_description
                            ),
                        color = secondaryText,
                        fontSize = 12.sp
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color =
                            MaterialTheme.colorScheme.primary
                                .copy(alpha = 0.09f),
                        contentColor = primaryText
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(6.dp)
                        ) {
                            if (wifiAddresses.isEmpty()) {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.remote_wifi_not_available
                                        ),
                                    color = secondaryText,
                                    fontSize = 15.sp
                                )
                            } else {
                                wifiAddresses.forEach { network ->
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.remote_wifi_address,
                                                network.address
                                            ),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color =
                            MaterialTheme.colorScheme.primary
                                .copy(alpha = 0.09f),
                        contentColor = primaryText
                    ) {
                        Text(
                            modifier = Modifier.padding(12.dp),
                            text =
                                stringResource(
                                    R.string.remote_pairing_pin,
                                    remotePin
                                ),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text =
                            stringResource(
                                R.string.remote_security_hint
                            ),
                        color = secondaryText,
                        fontSize = 11.sp
                    )

                    if (!remoteNetworkEnabled) {
                        Text(
                            text =
                                stringResource(
                                    R.string.remote_wireless_disabled
                                ),
                            color =
                                MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }
                }

                SettingsSection(
                    title =
                        stringResource(
                            R.string.remote_mobile_title
                        ),
                    titleIcon =
                        ConnectivityIconType.MOBILE,
                    expanded =
                        mobileSectionExpanded,
                    onExpandedChange = { expanded ->
                        mobileSectionExpanded = expanded
                        saveSettingsSectionExpanded(
                            context,
                            KEY_SETTINGS_MOBILE_EXPANDED,
                            expanded
                        )
                    },
                    containerColor = cardColor,
                    titleColor = primaryText,
                    dividerColor =
                        secondaryText.copy(
                            alpha = 0.28f
                        )
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.remote_mobile_description
                            ),
                        color = secondaryText,
                        fontSize = 12.sp
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color =
                            MaterialTheme.colorScheme.primary
                                .copy(alpha = 0.09f),
                        contentColor = primaryText
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(6.dp)
                        ) {
                            if (mobileAddresses.isEmpty()) {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.remote_mobile_not_available
                                        ),
                                    color = secondaryText,
                                    fontSize = 15.sp
                                )
                            } else {
                                mobileAddresses.forEachIndexed {
                                        index,
                                        network ->
                                    Text(
                                        text =
                                            if (mobileAddresses.size == 1) {
                                                stringResource(
                                                    R.string.remote_mobile_address,
                                                    network.address
                                                )
                                            } else {
                                                stringResource(
                                                    R.string.remote_mobile_address_numbered,
                                                    index + 1,
                                                    mobileAddresses.size,
                                                    network.address
                                                )
                                            },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                otherAddresses.forEach { network ->
                                    Text(
                                        text =
                                            stringResource(
                                                R.string.remote_other_address,
                                                network.address
                                            ),
                                        color = secondaryText,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color =
                            MaterialTheme.colorScheme.primary
                                .copy(alpha = 0.09f),
                        contentColor = primaryText
                    ) {
                        Text(
                            modifier = Modifier.padding(12.dp),
                            text =
                                stringResource(
                                    R.string.remote_pairing_pin,
                                    remotePin
                                ),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text =
                            stringResource(
                                R.string.remote_external_access_hint
                            ),
                        color = secondaryText,
                        fontSize = 11.sp
                    )

                    if (!remoteNetworkEnabled) {
                        Text(
                            text =
                                stringResource(
                                    R.string.remote_wireless_disabled
                                ),
                            color =
                                MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }
                }

                SettingsSection(
                    title =
                        stringResource(
                            R.string.reset_counters_title
                        ),
                    titleIcon =
                        ConnectivityIconType.COUNTERS,
                    expanded =
                        countersSectionExpanded,
                    onExpandedChange = { expanded ->
                        countersSectionExpanded = expanded
                        saveSettingsSectionExpanded(
                            context,
                            KEY_SETTINGS_COUNTERS_EXPANDED,
                            expanded
                        )
                    },
                    containerColor = cardColor,
                    titleColor = primaryText,
                    dividerColor =
                        secondaryText.copy(
                            alpha = 0.28f
                        )
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.reset_counters_description
                            ),
                        color = secondaryText,
                        fontSize = 12.sp
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.reset_counters_data_warning
                            ),
                        color =
                            MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Button(
                        onClick = {
                            showResetCountersConfirmation = true
                        },
                        enabled = !autoEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    MaterialTheme.colorScheme.error,
                                contentColor =
                                    MaterialTheme.colorScheme.onError
                            )
                    ) {
                        Text(
                            stringResource(
                                R.string.reset_counters_action
                            )
                        )
                    }

                    if (autoEnabled) {
                        Text(
                            text =
                                stringResource(
                                    R.string.reset_counters_stop_auto
                                ),
                            color = secondaryText,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },

        actionButton = {

            Button(
                modifier =
                    Modifier.fillMaxWidth(),

                onClick = {

                    val samples =
                        samplesText
                            .toIntOrNull()
                            ?: 0

                    val spacing =
                        spacingText
                            .toLongOrNull()
                            ?: 0L

                    if (
                        samples !in 1..21 ||
                        spacing !in 20L..5000L
                    ) {

                        parametersError =
                            invalidParametersText

                    } else {

                        onApplyAcquisitionParameters(
                            AcquisitionParameters(
                                samplesPerMeasurement =
                                    samples,
                                sampleSpacingMs =
                                    spacing,
                                discardExtremes =
                                    trimEnabled
                            )
                        )

                        Toast.makeText(
                            context,
                            parametersSavedText,
                            Toast.LENGTH_SHORT
                        ).show()

                        parametersError =
                            null

                        showParametersDialog =
                            false
                    }
                }
            ) {
                Text(
                    stringResource(
                        R.string.apply
                    )
                )
            }
        }
    )
    return
}

// -------------------------------------------------
// MAIN SCREEN
// -------------------------------------------------

Scaffold(
containerColor =
backgroundColor,

floatingActionButton = {

    Row(
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {

        if (autoEnabled) {

            PulsingAutomaticCountBadge(
                completed =
                    autoCompletedCount,
                onClick = {
                    openAutomaticAcquisitionPage()
                },
                color =
                    Color(
                        0xFF43A047
                    ),
                containerColor =
                    cardColor,
                modifier =
                    Modifier.size(48.dp)
            )
        }

    FloatingActionButton(
        onClick = {

            if (autoEnabled) {

                showStopConfirmation =
                    true

            } else if (liveReady) {

                note = ""

                showSaveDialog =
                    true
            }
        },

        containerColor =
            if (autoEnabled)
                Color(
                    0xFFD32F2F
                )
            else
                MaterialTheme.colorScheme.primary,

        contentColor =
            if (autoEnabled)
                Color.White
            else
                MaterialTheme.colorScheme.onPrimary
    ) {

        if (autoEnabled) {

            Box(
                modifier =
                    Modifier
                        .size(
                            18.dp
                        )
                        .background(
                            Color.White
                        )
            )

        } else {

            CaptureMeasurementIcon(
                modifier =
                    Modifier.size(25.dp),
                tint =
                    Color.White
            )
        }
    }
    }
}

) { paddingValues ->

    LazyColumn(
        state =
            liveListState,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    paddingValues
                )
                .lazyScrollbarOverlay(
                    state =
                        liveListState,
                    color =
                        secondaryText.copy(
                            alpha = 0.46f
                        )
                ),

        contentPadding =
            PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = 100.dp
            ),

        verticalArrangement =
            Arrangement.spacedBy(
                UvirIslandSpacing
            )
    ) {

        item {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 10.dp
                        ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Image(
                    painter =
                        painterResource(
                            R.drawable.uvir_logo
                        ),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(46.dp)
                            .clip(
                                RoundedCornerShape(13.dp)
                            ),
                    contentScale =
                        ContentScale.Crop
                )

                Spacer(
                    Modifier.width(12.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Uvir",
                            color = primaryText,
                            fontSize = 29.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            Modifier.width(10.dp)
                        )

                        Text(
                            text =
                                BuildConfig.VERSION_NAME,
                            color = secondaryText,
                            fontSize = 15.sp,
                            fontWeight =
                                FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(9.dp)
                                    .background(
                                        if (liveReady) {
                                            Color(0xFF43A047)
                                        } else {
                                            Color(0xFFFFA000)
                                        },
                                        RoundedCornerShape(50)
                                    )
                        )

                        Spacer(
                            Modifier.width(7.dp)
                        )

                        Text(
                            text =
                                if (liveReady) {
                                    stringResource(
                                        R.string.live_status,
                                        samplesPerMeasurement,
                                        sampleSpacingMs
                                    )
                                } else {
                                    stringResource(
                                        R.string.initializing
                                    )
                                },
                            color = secondaryText,
                            fontSize = 11.sp
                        )
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .size(42.dp)
                            .semantics {
                                contentDescription =
                                    versionInfoDescription
                            }
                            .clickable {
                                showVersionInfoDialog = true
                            },
                    contentAlignment =
                        Alignment.Center
                ) {
                    UvirMenuIcon(
                        type =
                            MenuIconType.VERSION_INFO,
                        modifier =
                            Modifier.size(22.dp),
                        tint = primaryText
                    )
                }
            }
        }

        stickyHeader {

            Surface(
                modifier =
                    Modifier.fillMaxWidth(),
                color = backgroundColor
            ) {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical =
                                if (homeMenuPinned) {
                                    4.dp
                                } else {
                                    0.dp
                                }
                        ),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier =
                        Modifier.weight(1f)
                ) {
                    MeasurementViewSelector(
                        selectedMode =
                            viewMode,
                        onModeSelected =
                            onViewModeChanged,
                        cardColor =
                            cardColor,
                        primaryText =
                            primaryText,
                        secondaryText =
                            secondaryText
                    )
                }

                UvirHomeActionButton(
                    type =
                        MenuIconType.SAVED_MEASUREMENTS,
                    contentDescription =
                        stringResource(
                            R.string.saved_measurements
                        ),
                    cardColor =
                        cardColor,
                    primaryText =
                        primaryText,
                    onClick =
                        onOpenHistory
                )

                UvirHomeActionButton(
                    type =
                        MenuIconType.AUTOMATIC_ACQUISITION,
                    contentDescription =
                        stringResource(
                            R.string.automatic_acquisition
                        ),
                    cardColor =
                        cardColor,
                    primaryText =
                        primaryText,
                    onClick = {
                        openAutomaticAcquisitionPage()
                    }
                )

                UvirHomeActionButton(
                    type =
                        MenuIconType.ACQUISITION_PARAMETERS,
                    contentDescription =
                        stringResource(
                            R.string.acquisition_parameters
                        ),
                    cardColor =
                        cardColor,
                    primaryText =
                        primaryText,
                    onClick = {

                        samplesText =
                            samplesPerMeasurement
                                .toString()

                        spacingText =
                            sampleSpacingMs
                                .toString()

                        trimEnabled =
                            discardExtremes

                        parametersError =
                            null

                        showParametersDialog =
                            true
                    }
                )
            }
            }
        }

        if (
            viewMode ==
            ViewMode.IRRADIANCE
        ) {

            itemsIndexed(
                items = groups,

                key = { _, group ->
                    group.name
                }
            ) { _, group ->

                SensorGroupContent(
                    group = group,
                    groups = groups,

                    expanded =
                        expandedStates[group]
                            ?: false,

                    onToggle = {

                        val newState =
                            !(
                                    expandedStates[group]
                                        ?: false
                                    )

                        expandedStates[group] =
                            newState

                        saveExpandedState(
                            context,
                            group,
                            newState
                        )
                    },

                    onOrderChanged = {

                        saveGroupOrder(
                            context,
                            groups
                        )
                    },

                    allowReorder =
                        true,

                    sample =
                        measurement,

                    uvTotal =
                        uvTotal,

                    visibleTotal =
                        visibleTotal,

                    nirTotal =
                        nirTotal,

                    heb =
                        heb,

                    hev =
                        hev,

                    cardColor =
                        cardColor,

                    primaryText =
                        primaryText,

                    secondaryText =
                        secondaryText,

                    trackColor =
                        trackColor
                )
            }

        } else {

            item {

                BiologicalEffectsContent(
                    sample =
                        measurement,

                    allowReorder =
                        true,

                    cardColor =
                        cardColor,

                    primaryText =
                        primaryText,

                    secondaryText =
                        secondaryText
                )
            }
        }
    }
}

}

// =====================================================
// STORICO
// =====================================================

@Composable
fun HistoryScreen(
    database: UvirDatabaseHelper,
    historyListState: LazyListState,
    backgroundColor: Color,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    onBack: () -> Unit,
    onOpenRecord: (SavedRecordDetail) -> Unit
) {

    val context =
        LocalContext.current

    var records by remember(database) {
        mutableStateOf(
            database.readSavedRecords()
        )
    }

    var showDeleteAllConfirmation by rememberSaveable {
        mutableStateOf(false)
    }

    var showDeleteSelectedConfirmation by rememberSaveable {
        mutableStateOf(false)
    }

    var showShareAllConfirmation by rememberSaveable {
        mutableStateOf(false)
    }

    var showShareFormatDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var selectionMode by rememberSaveable {
        mutableStateOf(false)
    }

    var selectedRecordIds by rememberSaveable {
        mutableStateOf<List<Long>>(
            emptyList()
        )
    }

    var pendingShareIds by rememberSaveable {
        mutableStateOf<List<Long>>(
            emptyList()
        )
    }

    BackHandler {
        if (selectionMode) {
            selectionMode = false
            selectedRecordIds = emptyList()
        } else {
            onBack()
        }
    }

    val deleteAllDescription =
        stringResource(
            R.string.delete_all
        )

    val selectDescription =
        stringResource(
            if (selectionMode) {
                R.string.exit_selection_mode
            } else {
                R.string.select_measurements
            }
        )

    val shareDescription =
        stringResource(
            R.string.share_measurements
        )

    val shareErrorText =
        stringResource(
            R.string.share_error
        )

    val measurementsDeletedText =
        stringResource(
            R.string.measurements_deleted
        )

    val automaticSessionCounts =
        remember(records) {
            records
                .mapNotNull {
                    if (it.automatic) {
                        it.automaticSessionId
                    } else {
                        null
                    }
                }
                .groupingBy { it }
                .eachCount()
        }

    val automaticSessionStartTimestamps =
        remember(records) {
            records
                .asSequence()
                .filter {
                    it.automatic &&
                            it.automaticSessionId != null
                }
                .groupBy {
                    requireNotNull(
                        it.automaticSessionId
                    )
                }
                .mapValues { (_, sessionRecords) ->
                    sessionRecords.minOf {
                        it.timestamp
                    }
                }
        }

    val automaticSessionFirstRecordIds =
        remember(records) {
            mutableMapOf<Long, Long>()
                .apply {
                    records.forEach { record ->
                        if (record.automatic) {
                            record.automaticSessionId
                        } else {
                            null
                        }
                            ?.let { sessionId ->
                                putIfAbsent(
                                    sessionId,
                                    record.id
                                )
                            }
                    }
                }
        }

    fun openShareFormat(ids: List<Long>) {
        pendingShareIds = ids
        showShareFormatDialog = true
    }

    fun sharePendingRecords(
        format: MeasurementShareFormat
    ) {
        val details =
            pendingShareIds.mapNotNull { id ->
                database.readRecord(id)
            }

        if (details.isNotEmpty()) {
            runCatching {
                shareMeasurements(
                    context,
                    details,
                    format
                )
            }.onFailure {
                Toast.makeText(
                    context,
                    shareErrorText,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        showShareFormatDialog = false
        pendingShareIds = emptyList()
    }

    if (showDeleteAllConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showDeleteAllConfirmation = false
            },
            title = {
                Text(
                    stringResource(
                        R.string.delete_all_measurements_question
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.delete_all_measurements_warning
                    )
                )
            },
            dismissButton = {
                HoldToConfirmDeleteButton(
                    label =
                        stringResource(
                            R.string.delete_all
                        ),
                    onConfirmed = {
                        database.deleteAllMeasurements()
                        records = database.readSavedRecords()
                        showDeleteAllConfirmation = false
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllConfirmation = false
                    }
                ) {
                    Text(
                        stringResource(
                            R.string.cancel
                        )
                    )
                }
            },

            containerColor = cardColor,
            titleContentColor = primaryText,
            textContentColor = secondaryText
        )
    }

    if (showDeleteSelectedConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showDeleteSelectedConfirmation = false
            },
            title = {
                Text(
                    stringResource(
                        R.string.delete_selected_measurements_question
                    )
                )
            },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.delete_selected_measurements_warning,
                        selectedRecordIds.size,
                        selectedRecordIds.size
                    )
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val deleted =
                            database.deleteRecords(
                                selectedRecordIds
                            )

                        records =
                            database.readSavedRecords()
                        selectedRecordIds =
                            emptyList()
                        selectionMode = false
                        showDeleteSelectedConfirmation =
                            false

                        if (deleted > 0) {
                            Toast.makeText(
                                context,
                                measurementsDeletedText,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor =
                                Color(0xFFD32F2F)
                        )
                ) {
                    Text(
                        stringResource(
                            R.string.delete
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteSelectedConfirmation =
                            false
                    }
                ) {
                    Text(
                        stringResource(
                            R.string.cancel
                        )
                    )
                }
            },
            containerColor = cardColor,
            titleContentColor = primaryText,
            textContentColor = secondaryText
        )
    }

    if (showShareAllConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showShareAllConfirmation = false
            },
            title = {
                Text(
                    stringResource(
                        R.string.share_all_measurements_question
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.share_all_measurements_warning
                    )
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showShareAllConfirmation = false
                        openShareFormat(
                            records.map { it.id }
                        )
                    }
                ) {
                    Text(
                        stringResource(
                            R.string.share_all
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showShareAllConfirmation = false
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor =
                                Color(0xFFD32F2F)
                        )
                ) {
                    Text(
                        stringResource(
                            R.string.cancel
                        )
                    )
                }
            },
            containerColor = cardColor,
            titleContentColor = primaryText,
            textContentColor = secondaryText
        )
    }

    if (showShareFormatDialog) {
        MeasurementShareFormatDialog(
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onDismiss = {
                showShareFormatDialog = false
                pendingShareIds = emptyList()
            },
            onFormatSelected = {
                format ->
                sharePendingRecords(format)
            }
        )
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            Surface(color = backgroundColor) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 10.dp,
                            vertical = 4.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UvirBackButton(
                        onClick = onBack
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.saved_measurements
                            ),
                        modifier =
                            Modifier.weight(1f),
                        color =
                            primaryText,
                        fontSize =
                            20.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    IconButton(
                        onClick = {
                            selectionMode =
                                !selectionMode
                            selectedRecordIds =
                                emptyList()
                        },
                        enabled =
                            records.isNotEmpty(),
                        modifier =
                            Modifier
                                .size(40.dp)
                                .semantics {
                                    contentDescription =
                                        selectDescription
                                }
                    ) {
                        UvirMenuIcon(
                            type =
                                MenuIconType.SELECT,
                            modifier =
                                Modifier.size(23.dp),
                            tint =
                                if (selectionMode) {
                                    MaterialTheme.colorScheme.primary
                                } else if (records.isNotEmpty()) {
                                    primaryText
                                } else {
                                    secondaryText.copy(alpha = 0.38f)
                                }
                        )
                    }

                    IconButton(
                        onClick = {
                            if (selectedRecordIds.isEmpty()) {
                                showShareAllConfirmation = true
                            } else {
                                openShareFormat(
                                    selectedRecordIds
                                )
                            }
                        },
                        enabled =
                            records.isNotEmpty(),
                        modifier =
                            Modifier
                                .size(40.dp)
                                .semantics {
                                    contentDescription =
                                        shareDescription
                                }
                    ) {
                        UvirMenuIcon(
                            type =
                                MenuIconType.SHARE,
                            modifier =
                                Modifier.size(24.dp),
                            tint =
                                if (records.isNotEmpty()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    secondaryText.copy(alpha = 0.38f)
                                }
                        )
                    }

                    IconButton(
                        onClick = {
                            if (selectedRecordIds.isEmpty()) {
                                showDeleteAllConfirmation = true
                            } else {
                                showDeleteSelectedConfirmation = true
                            }
                        },
                        enabled =
                            records.isNotEmpty(),
                        modifier =
                            Modifier
                                .size(40.dp)
                                .semantics {
                                    contentDescription =
                                        deleteAllDescription
                                }
                    ) {
                        UvirMenuIcon(
                            type =
                                MenuIconType.DELETE,
                            modifier =
                                Modifier.size(24.dp),
                            tint =
                                if (records.isNotEmpty()) {
                                    Color(0xFFD32F2F)
                                } else {
                                    secondaryText.copy(alpha = 0.38f)
                                }
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (records.isNotEmpty()) {
                Surface(
                    color = cardColor,
                    shadowElevation = 4.dp
                ) {
                    Column {
                        HorizontalDivider(
                            color =
                                secondaryText.copy(
                                    alpha = 0.16f
                                )
                        )

                        Text(
                            text =
                                pluralStringResource(
                                    R.plurals.measurement_count_since,
                                    records.size,
                                    records.size,
                                    formatDateTime(
                                        records.last().timestamp
                                    )
                                ),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 20.dp,
                                        vertical = 6.dp
                                    ),
                            color = secondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_saved_measurements),
                    color = secondaryText
                )
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
            ) {
                if (selectionMode) {
                    val allSelected =
                        records.all { record ->
                            record.id in selectedRecordIds
                        }

                    val selectAllIds = {
                        selectedRecordIds =
                            if (allSelected) {
                                emptyList()
                            } else {
                                records.map { it.id }
                            }
                    }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 20.dp
                                )
                                .clickable {
                                    selectAllIds()
                                }
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides 0.dp
                        ) {
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = {
                                    checked ->
                                    selectedRecordIds =
                                        if (checked) {
                                            records.map { it.id }
                                        } else {
                                            emptyList()
                                        }
                                },
                                modifier =
                                    Modifier.size(24.dp)
                            )
                        }

                        Spacer(
                            Modifier.width(12.dp)
                        )

                        Text(
                            text =
                                stringResource(
                                    if (allSelected) {
                                        R.string.deselect_all
                                    } else {
                                        R.string.select_all
                                    }
                                ),
                            color =
                                MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    HorizontalDivider(
                        modifier =
                            Modifier.padding(
                                horizontal = 20.dp
                            ),
                        color =
                            secondaryText.copy(
                                alpha = 0.12f
                            )
                    )
                }

                LazyColumn(
                    state =
                        historyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .lazyScrollbarOverlay(
                            state =
                                historyListState,
                            color =
                                secondaryText.copy(
                                    alpha = 0.46f
                                )
                        ),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 4.dp,
                        bottom = 20.dp
                    ),
                    verticalArrangement =
                        Arrangement.spacedBy(5.dp)
                ) {

                    itemsIndexed(
                        items = records,
                        key = { _, record ->
                            record.id
                        }
                    ) { index, record ->
                        val selected =
                            record.id in selectedRecordIds

                        val sessionId =
                            if (record.automatic) {
                                record.automaticSessionId
                            } else {
                                null
                            }

                        val continuesSession =
                            index > 0 &&
                                    sessionId != null &&
                                    records[index - 1]
                                        .automaticSessionId ==
                                    sessionId

                        val headerSessionId =
                            sessionId?.takeIf {
                                automaticSessionFirstRecordIds[
                                    it
                                ] == record.id
                            }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top =
                                        if (
                                            index == 0 ||
                                            continuesSession
                                        ) {
                                            0.dp
                                        } else {
                                            4.dp
                                        }
                                )
                        ) {
                            if (headerSessionId != null) {
                                val sessionNoteName =
                                    automaticSessionNoteName(
                                        record.note,
                                        record.automaticSequence
                                    ).ifBlank {
                                        stringResource(
                                            R.string.automatic_session_unnamed
                                        )
                                    }

                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                start = 4.dp,
                                                bottom = 6.dp
                                            ),
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .width(3.dp)
                                                .height(18.dp)
                                                .background(
                                                    MaterialTheme
                                                        .colorScheme
                                                        .primary,
                                                    RoundedCornerShape(
                                                        50
                                                    )
                                                )
                                    )

                                    Spacer(
                                        Modifier.width(8.dp)
                                    )

                                    Text(
                                        text =
                                            pluralStringResource(
                                                R.plurals.automatic_session_summary,
                                                automaticSessionCounts[
                                                    headerSessionId
                                                ] ?: 1,
                                                sessionNoteName,
                                                headerSessionId,
                                                formatAutomaticSessionDateTime(
                                                    automaticSessionStartTimestamps[
                                                        headerSessionId
                                                    ] ?: record.timestamp
                                                ),
                                                automaticSessionCounts[
                                                    headerSessionId
                                                ] ?: 1
                                            ),
                                        color = secondaryText,
                                        fontSize = 12.sp,
                                        fontWeight =
                                            FontWeight.SemiBold
                                    )
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                    onClick = {
                                        if (selectionMode) {
                                            selectedRecordIds =
                                                if (selected) {
                                                    selectedRecordIds - record.id
                                                } else {
                                                    selectedRecordIds + record.id
                                                }
                                        } else {
                                            val detail =
                                                database.readRecord(record.id)
                                            if (detail != null) {
                                                onOpenRecord(detail)
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        selectionMode = true
                                        if (!selected) {
                                            selectedRecordIds =
                                                selectedRecordIds + record.id
                                        }
                                    }
                                    ),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor =
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary
                                                .copy(alpha = 0.14f)
                                        } else {
                                            cardColor
                                        }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                if (selectionMode) {
                                    CompositionLocalProvider(
                                        LocalMinimumInteractiveComponentSize provides 0.dp
                                    ) {
                                        Checkbox(
                                            checked = selected,
                                            onCheckedChange = {
                                                isChecked ->
                                                selectedRecordIds =
                                                    if (isChecked) {
                                                        selectedRecordIds + record.id
                                                    } else {
                                                        selectedRecordIds - record.id
                                                    }
                                            },
                                            modifier =
                                                Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(
                                        Modifier.width(12.dp)
                                    )
                                }

                                if (sessionId != null) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .width(3.dp)
                                                .height(42.dp)
                                                .background(
                                                    MaterialTheme
                                                        .colorScheme
                                                        .primary
                                                        .copy(
                                                            alpha = 0.58f
                                                        ),
                                                    RoundedCornerShape(
                                                        50
                                                    )
                                                )
                                    )

                                    Spacer(
                                        Modifier.width(11.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = formatDateTime(record.timestamp),
                                        color = primaryText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Spacer(Modifier.height(3.dp))

                                    Text(
                                        text = if (record.note.isBlank())
                                            stringResource(R.string.no_note)
                                        else
                                            record.note,
                                        color = secondaryText,
                                        fontSize = 12.sp,
                                        maxLines = 2
                                    )
                                }

                                if (
                                    record.automatic &&
                                    sessionId == null
                                ) {
                                    AutomaticBadge(
                                        primaryText = primaryText,
                                        secondaryText = secondaryText
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }

                                if (!selectionMode) {
                                    Text(
                                        text = "›",
                                        color = secondaryText,
                                        fontSize = 24.sp
                                    )
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}

// =====================================================
// DETTAGLIO RECORD
// =====================================================

@Composable
fun RecordDetailScreen(
    record: SavedRecordDetail,
    database: UvirDatabaseHelper,
    detailListState: LazyListState,

    viewMode: ViewMode,
    onViewModeChanged: (ViewMode) -> Unit,

    backgroundColor: Color,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    trackColor: Color,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {

    val context =
        LocalContext.current

    BackHandler {
        onBack()
    }

    var showDeleteConfirmation
            by rememberSaveable {
                mutableStateOf(false)
            }

    var showShareFormatDialog
            by rememberSaveable {
                mutableStateOf(false)
            }

    val deleteDescription =
        stringResource(
            R.string.delete
        )

    val shareDescription =
        stringResource(
            R.string.share_measurements
        )

    val shareErrorText =
        stringResource(
            R.string.share_error
        )

    val groups = remember {
        mutableStateListOf(
            SensorGroup.UV,
            SensorGroup.HEV_HEB,
            SensorGroup.VISIBLE,
            SensorGroup.NIR
        )
    }

    val expandedStates = remember {
        mutableStateMapOf(
            SensorGroup.UV to true,
            SensorGroup.HEV_HEB to true,
            SensorGroup.VISIBLE to true,
            SensorGroup.NIR to true
        )
    }

    val measurement =
        record.sample

    val uvTotal =
        measurement.uvc +
                measurement.uvb +
                measurement.uva

    val visibleTotal =
        measurement.violetto +
                measurement.blu +
                measurement.verde +
                measurement.giallo +
                measurement.arancione +
                measurement.rosso

    val nirTotal =
        measurement.f8 +
                measurement.nir

    val heb =
        measurement.violetto

    val hev =
        measurement.violetto +
                measurement.blu

    if (showDeleteConfirmation) {

        val measurementDeletedText =
            stringResource(
                R.string.measurement_deleted
            )

        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmation =
                    false
            },

            title = {
                Text(
                    stringResource(
                        R.string.delete_measurement_question
                    )
                )
            },

            text = {
                Text(
                    stringResource(
                        R.string.delete_measurement_warning
                    )
                )
            },

            dismissButton = {

                TextButton(
                    onClick = {

                        val deleted =
                            database.deleteRecord(
                                record.id
                            )

                        if (deleted > 0) {

                            Toast.makeText(
                                context,
                                measurementDeletedText,
                                Toast.LENGTH_SHORT
                            ).show()

                            onDeleted()
                        }
                    },

                    colors =
                        ButtonDefaults
                            .textButtonColors(
                                contentColor =
                                    Color(
                                        0xFFD32F2F
                                    )
                            )
                ) {
                    Text(
                        stringResource(
                            R.string.delete
                        )
                    )
                }
            },

            confirmButton = {

                TextButton(
                    onClick = {
                        showDeleteConfirmation =
                            false
                    }
                ) {
                    Text(
                        stringResource(
                            R.string.cancel
                        )
                    )
                }
            },

            containerColor = cardColor,
            titleContentColor = primaryText,
            textContentColor = secondaryText
        )
    }

    if (showShareFormatDialog) {
        MeasurementShareFormatDialog(
            cardColor = cardColor,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onDismiss = {
                showShareFormatDialog = false
            },
            onFormatSelected = {
                format ->
                runCatching {
                    shareMeasurements(
                        context,
                        listOf(record),
                        format
                    )
                }.onFailure {
                    Toast.makeText(
                        context,
                        shareErrorText,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                showShareFormatDialog = false
            }
        )
    }

    Scaffold(
        containerColor =
            backgroundColor,

        topBar = {

            Surface(
                color =
                    backgroundColor
            ) {

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 10.dp,
                                vertical = 4.dp
                            ),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    UvirBackButton(
                        onClick = onBack
                    )

                    Text(
                        text =
                            formatDetailDateTime(
                                record.timestamp
                            ),
                        modifier =
                            Modifier.weight(1f),
                        color =
                            primaryText,
                        fontSize =
                            20.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    IconButton(
                        onClick = {
                            showShareFormatDialog = true
                        },
                        modifier =
                            Modifier
                                .size(40.dp)
                                .semantics {
                                    contentDescription =
                                        shareDescription
                                }
                    ) {
                        UvirMenuIcon(
                            type =
                                MenuIconType.SHARE,
                            modifier =
                                Modifier.size(24.dp),
                            tint =
                                MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = {
                            showDeleteConfirmation =
                                true
                        },

                        modifier =
                            Modifier
                                .size(40.dp)
                                .semantics {
                                    contentDescription =
                                        deleteDescription
                                }
                    ) {
                        UvirMenuIcon(
                            type =
                                MenuIconType.DELETE,
                            modifier =
                                Modifier.size(24.dp),
                            tint =
                                Color(0xFFD32F2F)
                        )
                    }
                }
            }
        }

    ) { paddingValues ->

        LazyColumn(
            state =
                detailListState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        paddingValues
                    )
                    .lazyScrollbarOverlay(
                        state =
                            detailListState,
                        color =
                            secondaryText.copy(
                                alpha = 0.46f
                            )
                    ),

            contentPadding =
                PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = 40.dp
                ),

            verticalArrangement =
                Arrangement.spacedBy(
                    UvirIslandSpacing
                )
        ) {

            item {

                Card(
                    modifier =
                        Modifier.fillMaxWidth(),

                    shape =
                        RoundedCornerShape(
                            18.dp
                        ),

                    colors =
                        CardDefaults
                            .cardColors(
                                containerColor =
                                    cardColor
                            )
                ) {

                    Column(
                        modifier =
                            Modifier.padding(
                                18.dp
                            ),

                        verticalArrangement =
                            Arrangement.spacedBy(
                                7.dp
                            )
                    ) {

                        Text(
                            text =
                                stringResource(
                                    R.string.note
                                ),

                            color =
                                secondaryText,

                            fontSize =
                                12.sp,

                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            text =
                                if (
                                    record.note.isBlank()
                                )
                                    stringResource(
                                        R.string.no_note
                                    )
                                else
                                    record.note,

                            color =
                                primaryText,

                            fontSize =
                                15.sp
                        )

                        Spacer(
                            Modifier.height(
                                8.dp
                            )
                        )

                        Text(
                            text =
                                stringResource(
                                    R.string.acquisition_type
                                ),

                            color =
                                secondaryText,

                            fontSize =
                                12.sp,

                            fontWeight =
                                FontWeight.Bold
                        )

                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            if (record.automatic) {

                                AutomaticBadge(
                                    primaryText =
                                        primaryText,

                                    secondaryText =
                                        secondaryText
                                )

                                Spacer(
                                    Modifier.width(
                                        8.dp
                                    )
                                )
                            }

                            Text(
                                text =
                                    if (
                                        record.automatic
                                    )
                                        stringResource(
                                            R.string.automatic_measurement
                                        )
                                    else
                                        stringResource(
                                            R.string.manual_measurement
                                        ),

                                color =
                                    primaryText,

                                fontSize =
                                    14.sp
                            )
                        }
                    }
                }
            }

            item {

                MeasurementViewSelector(
                    selectedMode =
                        viewMode,

                    onModeSelected =
                        onViewModeChanged,

                    cardColor =
                        cardColor,

                    primaryText =
                        primaryText,

                    secondaryText =
                        secondaryText
                )
            }

            if (
                viewMode ==
                ViewMode.IRRADIANCE
            ) {

                itemsIndexed(
                    items = groups,

                    key = { _, group ->
                        group.name
                    }
                ) { _, group ->

                    SensorGroupContent(
                        group = group,
                        groups = groups,

                        expanded =
                            expandedStates[group]
                                ?: true,

                        onToggle = {

                            expandedStates[group] =
                                !(
                                        expandedStates[group]
                                            ?: true
                                        )
                        },

                        onOrderChanged = {
                        },

                        allowReorder =
                            false,

                        sample =
                            measurement,

                        uvTotal =
                            uvTotal,

                        visibleTotal =
                            visibleTotal,

                        nirTotal =
                            nirTotal,

                        heb =
                            heb,

                        hev =
                            hev,

                        cardColor =
                            cardColor,

                        primaryText =
                            primaryText,

                        secondaryText =
                            secondaryText,

                        trackColor =
                            trackColor
                    )
                }

            } else {

                item {

                    BiologicalEffectsContent(
                        sample =
                            measurement,

                        cardColor =
                            cardColor,

                        primaryText =
                            primaryText,

                        secondaryText =
                            secondaryText
                    )
                }
            }
        }
    }
}

@Composable
fun PulsingAutomaticCountBadge(
    completed: Int,
    onClick: () -> Unit,
    color: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {

    val pulseTransition =
        rememberInfiniteTransition(
            label =
                "automaticCountPulse"
        )

    val borderAlpha by
        pulseTransition.animateFloat(
            initialValue = 0.24f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 1200
                        ),
                    repeatMode =
                        RepeatMode.Reverse
                ),
            label =
                "automaticCountBorderAlpha"
        )

    val countText =
        completed.toString()

    val countFontSize =
        when {
            countText.length >= 6 -> 8.sp
            countText.length >= 4 -> 10.sp
            else -> 13.sp
        }

    val countDescription =
        stringResource(
            R.string.automatic_completed_count,
            completed
        )

    Surface(
        onClick = onClick,
        modifier =
            modifier.semantics {
                contentDescription =
                    countDescription
            },
        shape =
            RoundedCornerShape(50),
        color = containerColor,
        contentColor = color,
        border =
            BorderStroke(
                width = 1.8.dp,
                color =
                    color.copy(
                        alpha = borderAlpha
                    )
            ),
        shadowElevation = 5.dp,
        tonalElevation = 1.dp
    ) {

        Box(
            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = countText,
                color = color,
                fontSize = countFontSize,
                fontWeight =
                    FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun PulsingAutomaticStatusCard(
    autoNextSaveMs: Long,
    intervalSeconds: Long,
    limitEnabled: Boolean,
    completed: Int,
    maxCount: Int,
    color: Color,
    containerColor: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {

    val pulseTransition =
        rememberInfiniteTransition(
            label =
                "automaticStatusPulse"
        )

    val borderAlpha by
        pulseTransition.animateFloat(
            initialValue = 0.24f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 1200
                        ),
                    repeatMode =
                        RepeatMode.Reverse
                ),
            label =
                "automaticStatusBorderAlpha"
        )

    Surface(
        modifier = modifier,
        shape =
            RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = color,
        border =
            BorderStroke(
                width = 1.5.dp,
                color =
                    color.copy(
                        alpha = borderAlpha
                    )
            ),
        shadowElevation = 5.dp,
        tonalElevation = 1.dp
    ) {

        AutomaticStatusLabel(
            autoNextSaveMs =
                autoNextSaveMs,
            intervalSeconds =
                intervalSeconds,
            limitEnabled =
                limitEnabled,
            completed = completed,
            maxCount = maxCount,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 7.dp
                )
        )
    }
}

@Composable
fun AutomaticStatusLabel(
    autoNextSaveMs: Long,
    intervalSeconds: Long,
    limitEnabled: Boolean,
    completed: Int,
    maxCount: Int,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {

    // This state is local to the label: the countdown no longer invalidates
    // and recomposes the entire live screen while the user is scrolling.
    var nowMs by remember(
        autoNextSaveMs
    ) {
        mutableLongStateOf(
            System.currentTimeMillis()
        )
    }

    LaunchedEffect(
        autoNextSaveMs
    ) {
        while (true) {
            val currentTime =
                System.currentTimeMillis()

            nowMs = currentTime

            delay(
                (1000L - currentTime % 1000L)
                    .coerceAtLeast(50L)
            )
        }
    }

    val remainingSeconds =
        (
                (
                        autoNextSaveMs -
                                nowMs
                        ).coerceAtLeast(0L) +
                        999L
                ) / 1000L

    val countText =
        completed.toString()

    val countFontSize =
        when {
            countText.length >= 7 -> 6.5.sp
            countText.length >= 5 -> 7.5.sp
            countText.length >= 4 -> 9.sp
            else -> 11.sp
        }

    val statusDescription =
        automaticStatusText(
            autoNextSaveMs =
                autoNextSaveMs,
            intervalSeconds =
                intervalSeconds,
            limitEnabled =
                limitEnabled,
            completed =
                completed,
            maxCount =
                maxCount,
            nowMs =
                nowMs
        )

    Row(
        modifier =
            modifier.semantics(
                mergeDescendants = true
            ) {
                contentDescription =
                    statusDescription
            },
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(6.dp)
    ) {

        Surface(
            modifier =
                Modifier.size(34.dp),
            shape =
                RoundedCornerShape(50),
            color =
                color.copy(alpha = 0.16f),
            contentColor = color
        ) {

            Box(
                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text = countText,
                    color = color,
                    fontSize =
                        countFontSize,
                    fontWeight =
                        FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        Column(
            horizontalAlignment =
                Alignment.Start,
            verticalArrangement =
                Arrangement.spacedBy(1.dp)
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(3.dp)
            ) {

                AutomaticIntervalTimerIcon(
                    color = color,
                    modifier =
                        Modifier.size(14.dp)
                )

                Text(
                    text =
                        formatInterval(
                            intervalSeconds
                        ),
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    maxLines = 1
                )
            }

            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(3.dp)
            ) {

                AutomaticNextIcon(
                    color = color,
                    modifier =
                        Modifier.size(14.dp)
                )

                Text(
                    text =
                        formatInterval(
                            remainingSeconds
                        ),
                    color = color,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun AutomaticIntervalTimerIcon(
    color: Color,
    modifier: Modifier = Modifier
) {

    Canvas(
        modifier = modifier
    ) {

        val strokeWidth =
            maxOf(
                1.3.dp.toPx(),
                size.minDimension * 0.10f
            )

        val center =
            Offset(
                x = size.width * 0.50f,
                y = size.height * 0.57f
            )

        val radius =
            size.minDimension * 0.34f

        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style =
                Stroke(
                    width = strokeWidth
                )
        )

        drawLine(
            color = color,
            start =
                Offset(
                    x = size.width * 0.38f,
                    y = size.height * 0.10f
                ),
            end =
                Offset(
                    x = size.width * 0.62f,
                    y = size.height * 0.10f
                ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawLine(
            color = color,
            start =
                Offset(
                    x = size.width * 0.50f,
                    y = size.height * 0.10f
                ),
            end =
                Offset(
                    x = size.width * 0.50f,
                    y = size.height * 0.22f
                ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawLine(
            color = color,
            start = center,
            end =
                Offset(
                    x = size.width * 0.50f,
                    y = size.height * 0.35f
                ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawLine(
            color = color,
            start = center,
            end =
                Offset(
                    x = size.width * 0.68f,
                    y = size.height * 0.61f
                ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun AutomaticNextIcon(
    color: Color,
    modifier: Modifier = Modifier
) {

    Canvas(
        modifier = modifier
    ) {

        val strokeWidth =
            maxOf(
                1.3.dp.toPx(),
                size.minDimension * 0.10f
            )

        val arrowTip =
            Offset(
                x = size.width * 0.78f,
                y = size.height * 0.50f
            )

        drawLine(
            color = color,
            start =
                Offset(
                    x = size.width * 0.20f,
                    y = size.height * 0.50f
                ),
            end = arrowTip,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawLine(
            color = color,
            start =
                Offset(
                    x = size.width * 0.57f,
                    y = size.height * 0.29f
                ),
            end = arrowTip,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawLine(
            color = color,
            start =
                Offset(
                    x = size.width * 0.57f,
                    y = size.height * 0.71f
                ),
            end = arrowTip,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun automaticStatusText(
    autoNextSaveMs: Long,
    intervalSeconds: Long,
    limitEnabled: Boolean,
    completed: Int,
    maxCount: Int,
    nowMs: Long
): String {

    val remainingMs =
        (
                autoNextSaveMs -
                        nowMs
                ).coerceAtLeast(
                0L
            )

    // Ceiling to seconds so the countdown does not show 00:00:00
    // almost one second before the actual acquisition.
    val remainingSeconds =
        (
                remainingMs +
                        999L
                ) / 1000L

    val countdown =
        formatInterval(
            remainingSeconds
        )

    return when {

        autoNextSaveMs >
                nowMs + 1500L &&
                completed == 0 -> {

            stringResource(
                R.string.auto_waiting,
                countdown
            )
        }

        limitEnabled -> {

            stringResource(
                R.string.auto_status_limited,
                formatInterval(
                    intervalSeconds
                ),
                completed,
                maxCount,
                countdown
            )
        }

        else -> {

            stringResource(
                R.string.auto_status,
                formatInterval(
                    intervalSeconds
                ),
                completed,
                countdown
            )
        }
    }
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    maxChars: Int
) {

    OutlinedTextField(
        value = value,

        onValueChange = {
            onValueChange(
                it
                    .filter { c ->
                        c.isDigit()
                    }
                    .take(
                        maxChars
                    )
            )
        },

        modifier = modifier,

        label = {
            AdaptiveFieldLabel(label)
        },

        colors =
            UvirOutlinedTextFieldColors(),

        singleLine = true,

        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    KeyboardType.Number
            )
    )
}

@Composable
fun AdaptiveFieldLabel(
    text: String
) {

    BoxWithConstraints {

        val inheritedStyle =
            LocalTextStyle.current

        val baseFontSize =
            if (
                inheritedStyle.fontSize ==
                TextUnit.Unspecified
            ) {
                12.sp
            } else {
                inheritedStyle.fontSize
            }

        val textMeasurer =
            rememberTextMeasurer()

        val measuredWidth =
            textMeasurer.measure(
                text = AnnotatedString(text),
                style =
                    inheritedStyle.copy(
                        fontSize = baseFontSize
                    ),
                maxLines = 1,
                softWrap = false
            ).size.width.toFloat()

        val availableWidth =
            constraints.maxWidth.toFloat()

        val scale =
            if (
                availableWidth > 0f &&
                measuredWidth > availableWidth
            ) {
                (availableWidth / measuredWidth)
                    .coerceIn(0.72f, 1f)
            } else {
                1f
            }

        Text(
            text = text,
            fontSize =
                (baseFontSize.value * scale).sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun DurationFields(
    hoursText: String,
    minutesText: String,
    secondsText: String,
    onHoursChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    onSecondsChange: (String) -> Unit
) {

    Column {

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            verticalAlignment =
                Alignment.CenterVertically,

            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {

            NumberField(
                value =
                    hoursText,
                onValueChange =
                    onHoursChange,
                label =
                    stringResource(
                        R.string.hours
                    ),
                modifier =
                    Modifier.weight(1f),
                maxChars = 5
            )

            NumberField(
                value =
                    minutesText,
                onValueChange =
                    onMinutesChange,
                label =
                    stringResource(
                        R.string.minutes
                    ),
                modifier =
                    Modifier.weight(1f),
                maxChars = 2
            )

            NumberField(
                value =
                    secondsText,
                onValueChange =
                    onSecondsChange,
                label =
                    stringResource(
                        R.string.seconds
                    ),
                modifier =
                    Modifier.weight(1f),
                maxChars = 2
            )
        }
    }
}

enum class ConnectivityIconType {
    SAMPLING,
    USB,
    BLUETOOTH,
    WIFI,
    MOBILE,
    COUNTERS
}

@Composable
fun ConnectivitySectionIcon(
    type: ConnectivityIconType,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Canvas(
        modifier = modifier.size(20.dp)
    ) {
        val strokeWidth =
            maxOf(
                1.5.dp.toPx(),
                size.minDimension * 0.08f
            )

        when (type) {
            ConnectivityIconType.SAMPLING -> {
                val samples =
                    listOf(
                        Offset(size.width * 0.12f, size.height * 0.68f),
                        Offset(size.width * 0.31f, size.height * 0.36f),
                        Offset(size.width * 0.50f, size.height * 0.62f),
                        Offset(size.width * 0.69f, size.height * 0.25f),
                        Offset(size.width * 0.88f, size.height * 0.48f)
                    )

                samples.zipWithNext().forEach { (start, end) ->
                    drawLine(
                        color = tint,
                        start = start,
                        end = end,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }

                samples.forEach { sample ->
                    drawCircle(
                        color = tint,
                        radius = strokeWidth * 1.25f,
                        center = sample
                    )
                }
            }

            ConnectivityIconType.USB -> {
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.50f, size.height * 0.18f),
                    end = Offset(size.width * 0.50f, size.height * 0.80f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.50f, size.height * 0.48f),
                    end = Offset(size.width * 0.25f, size.height * 0.33f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.50f, size.height * 0.61f),
                    end = Offset(size.width * 0.75f, size.height * 0.45f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = tint,
                    radius = strokeWidth * 1.15f,
                    center = Offset(size.width * 0.25f, size.height * 0.33f)
                )
                drawRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.69f, size.height * 0.39f),
                    size = Size(size.width * 0.12f, size.height * 0.12f)
                )
                val arrow = Path().apply {
                    moveTo(size.width * 0.50f, size.height * 0.08f)
                    lineTo(size.width * 0.39f, size.height * 0.24f)
                    lineTo(size.width * 0.61f, size.height * 0.24f)
                    close()
                }
                drawPath(path = arrow, color = tint)
                drawCircle(
                    color = tint,
                    radius = strokeWidth * 1.35f,
                    center = Offset(size.width * 0.50f, size.height * 0.83f)
                )
            }

            ConnectivityIconType.BLUETOOTH -> {
                val center = Offset(size.width * 0.47f, size.height * 0.50f)
                val top = Offset(size.width * 0.47f, size.height * 0.10f)
                val bottom = Offset(size.width * 0.47f, size.height * 0.90f)
                val upper = Offset(size.width * 0.73f, size.height * 0.30f)
                val lower = Offset(size.width * 0.73f, size.height * 0.70f)

                listOf(
                    top to bottom,
                    center to upper,
                    upper to top,
                    center to lower,
                    lower to bottom,
                    Offset(size.width * 0.23f, size.height * 0.30f) to lower,
                    Offset(size.width * 0.23f, size.height * 0.70f) to upper
                ).forEach { (start, end) ->
                    drawLine(
                        color = tint,
                        start = start,
                        end = end,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            ConnectivityIconType.WIFI -> {
                drawArc(
                    color = tint,
                    startAngle = 220f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.10f, size.height * 0.08f),
                    size = Size(size.width * 0.80f, size.height * 0.80f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = tint,
                    startAngle = 220f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.28f, size.height * 0.38f),
                    size = Size(size.width * 0.44f, size.height * 0.44f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawCircle(
                    color = tint,
                    radius = strokeWidth * 1.10f,
                    center = Offset(size.width * 0.50f, size.height * 0.84f)
                )
            }

            ConnectivityIconType.MOBILE -> {
                val barWidth = size.width * 0.13f
                listOf(0.24f, 0.42f, 0.60f, 0.78f)
                    .forEachIndexed { index, xFraction ->
                        val heightFraction = 0.22f + index * 0.16f
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(
                                size.width * xFraction,
                                size.height * (0.88f - heightFraction)
                            ),
                            size = Size(
                                barWidth,
                                size.height * heightFraction
                            ),
                            cornerRadius = CornerRadius(
                                strokeWidth * 0.5f,
                                strokeWidth * 0.5f
                            )
                        )
                    }
            }

            ConnectivityIconType.COUNTERS -> {
                drawArc(
                    color = tint,
                    startAngle = 205f,
                    sweepAngle = 235f,
                    useCenter = false,
                    topLeft = Offset(
                        size.width * 0.14f,
                        size.height * 0.14f
                    ),
                    size = Size(
                        size.width * 0.72f,
                        size.height * 0.72f
                    ),
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )

                val arrow = Path().apply {
                    moveTo(
                        size.width * 0.16f,
                        size.height * 0.26f
                    )
                    lineTo(
                        size.width * 0.16f,
                        size.height * 0.49f
                    )
                    lineTo(
                        size.width * 0.37f,
                        size.height * 0.38f
                    )
                    close()
                }
                drawPath(
                    path = arrow,
                    color = tint
                )

                drawCircle(
                    color = tint,
                    radius = strokeWidth * 1.25f,
                    center = Offset(
                        size.width * 0.50f,
                        size.height * 0.50f
                    )
                )
            }
        }
    }
}

@Composable
fun ExpansionChevron(
    expanded: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.size(20.dp)
    ) {
        val strokeWidth =
            2.dp.toPx()
        val left =
            Offset(
                size.width * 0.27f,
                if (expanded) {
                    size.height * 0.62f
                } else {
                    size.height * 0.38f
                }
            )
        val center =
            Offset(
                size.width * 0.50f,
                if (expanded) {
                    size.height * 0.38f
                } else {
                    size.height * 0.62f
                }
            )
        val right =
            Offset(
                size.width * 0.73f,
                if (expanded) {
                    size.height * 0.62f
                } else {
                    size.height * 0.38f
                }
            )

        drawLine(
            color = tint,
            start = left,
            end = center,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = center,
            end = right,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    containerColor: Color,
    titleColor: Color,
    dividerColor: Color,
    titleIcon: ConnectivityIconType? = null,
    expanded: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {

    val bringIntoViewRequester =
        remember {
            BringIntoViewRequester()
        }

    var wasExpanded by remember {
        mutableStateOf(expanded)
    }

    LaunchedEffect(expanded) {
        val openedByUser =
            expanded && !wasExpanded

        wasExpanded = expanded

        if (openedByUser) {
            withFrameNanos { }
            bringIntoViewRequester
                .bringIntoView()
        }
    }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(
                    bringIntoViewRequester
                ),
        shape =
            RoundedCornerShape(16.dp),
        color =
            containerColor,
        contentColor =
            titleColor
    ) {

        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (onExpandedChange != null) {
                                Modifier.clickable {
                                    onExpandedChange(
                                        !expanded
                                    )
                                }
                            } else {
                                Modifier
                            }
                        ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                titleIcon?.let { icon ->
                    ConnectivitySectionIcon(
                        type = icon,
                        modifier = Modifier.size(20.dp),
                        tint = titleColor
                    )

                    Spacer(
                        Modifier.width(8.dp)
                    )
                }

                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )

                if (onExpandedChange != null) {
                    ExpansionChevron(
                        expanded = expanded,
                        tint = titleColor,
                        modifier =
                            Modifier.size(20.dp)
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(
                    color = dividerColor
                )

                content()
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
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(16.dp),
        color =
            containerColor,
        contentColor =
            contentColor
    ) {

        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun CheckSettingRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    emphasized: Boolean = false,
    compact: Boolean = false
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onCheckedChange(
                        !checked
                    )
                },

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        if (compact) {

            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides 0.dp
            ) {
                Checkbox(
                    checked =
                        checked,
                    onCheckedChange =
                        onCheckedChange,
                    modifier =
                        Modifier.size(24.dp)
                )
            }

        } else {

            Checkbox(
                checked =
                    checked,
                onCheckedChange =
                    onCheckedChange
            )
        }

        Spacer(
            Modifier.width(
                6.dp
            )
        )

        Text(
            text = title,
            fontSize =
                14.sp,
            fontWeight =
                if (emphasized)
                    FontWeight.Bold
                else
                    FontWeight.Normal
        )
    }
}

@Composable
fun UvirHomeActionButton(
    type: MenuIconType,
    contentDescription: String,
    cardColor: Color,
    primaryText: Color,
    onClick: () -> Unit
) {

    Surface(
        onClick = onClick,
        modifier =
            Modifier
                .size(42.dp)
                .semantics {
                    this.contentDescription =
                        contentDescription
                },
        shape =
            RoundedCornerShape(11.dp),
        color = cardColor,
        contentColor = primaryText
    ) {

        Box(
            contentAlignment =
                Alignment.Center
        ) {
            UvirMenuIcon(
                type = type,
                modifier =
                    Modifier.size(22.dp),
                tint = primaryText
            )
        }
    }
}

@Composable
fun MeasurementViewSelector(
    selectedMode: ViewMode,
    onModeSelected: (ViewMode) -> Unit,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {

    val irradianceDescription =
        stringResource(
            R.string.irradiance_view
        )

    val effectsDescription =
        stringResource(
            R.string.biological_effects_view
        )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    cardColor,
                    RoundedCornerShape(
                        14.dp
                    )
                )
                .padding(
                    3.dp
                ),

        horizontalArrangement =
            Arrangement.spacedBy(
                3.dp
            )
    ) {

        ViewModeButton(
            selected =
                selectedMode ==
                        ViewMode.IRRADIANCE,

            onClick = {
                onModeSelected(
                    ViewMode.IRRADIANCE
                )
            },

            contentDescription =
                irradianceDescription,

            modifier =
                Modifier.weight(1f),

            primaryText =
                primaryText,

            secondaryText =
                secondaryText
        ) {

            ElectromagneticWaveIcon(
                modifier =
                    Modifier.size(24.dp),
                color =
                    if (
                        selectedMode ==
                        ViewMode.IRRADIANCE
                    )
                        primaryText
                    else
                        secondaryText
            )
        }

        ViewModeButton(
            selected =
                selectedMode ==
                        ViewMode.BIOLOGICAL_EFFECTS,

            onClick = {
                onModeSelected(
                    ViewMode.BIOLOGICAL_EFFECTS
                )
            },

            contentDescription =
                effectsDescription,

            modifier =
                Modifier.weight(1f),

            primaryText =
                primaryText,

            secondaryText =
                secondaryText
        ) {

            DnaIcon(
                modifier =
                    Modifier.size(24.dp),
                color =
                    if (
                        selectedMode ==
                        ViewMode.BIOLOGICAL_EFFECTS
                    )
                        primaryText
                    else
                        secondaryText
            )
        }
    }
}

@Composable
fun ViewModeButton(
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    primaryText: Color,
    secondaryText: Color,
    content:
    @Composable () -> Unit
) {

    val selectedColor =
        primaryText.copy(
            alpha = 0.12f
        )

    Box(
        modifier =
            modifier
                .height(
                    40.dp
                )
                .background(
                    if (selected)
                        selectedColor
                    else
                        Color.Transparent,
                    RoundedCornerShape(
                        10.dp
                    )
                )
                .border(
                    width =
                        if (selected)
                            1.dp
                        else
                            0.dp,
                    color =
                        if (selected)
                            secondaryText.copy(
                                alpha = 0.45f
                            )
                        else
                            Color.Transparent,
                    shape =
                        RoundedCornerShape(
                            10.dp
                        )
                )
                .clickable {
                    onClick()
                }
                .semantics {
                    this.contentDescription =
                        contentDescription
                },

        contentAlignment =
            Alignment.Center
    ) {

        content()
    }
}

@Composable
fun ElectromagneticWaveIcon(
    color: Color,
    modifier: Modifier = Modifier
) {

    Canvas(
        modifier =
            modifier.size(
                28.dp
            )
    ) {

        val midY =
            size.height / 2f

        val amplitude =
            size.height * 0.23f

        val path =
            Path()

        val points =
            40

        for (i in 0..points) {

            val x =
                size.width *
                        i / points

            val radians =
                (
                        i.toFloat() /
                                points.toFloat()
                        ) *
                        Math.PI.toFloat() *
                        4f

            val y =
                midY +
                        kotlin.math.sin(
                            radians
                        ) *
                        amplitude

            if (i == 0) {
                path.moveTo(
                    x,
                    y
                )
            } else {
                path.lineTo(
                    x,
                    y
                )
            }
        }

        drawPath(
            path =
                path,
            color =
                color,
            style =
                Stroke(
                    width =
                        size.minDimension *
                                0.10f,
                    cap =
                        StrokeCap.Round
                )
        )
    }
}

@Composable
fun DnaIcon(
    color: Color,
    modifier: Modifier = Modifier
) {

    Canvas(
        modifier =
            modifier.size(
                28.dp
            )
    ) {

        val left =
            Path()

        val right =
            Path()

        val steps =
            32

        for (i in 0..steps) {

            val t =
                i.toFloat() /
                        steps.toFloat()

            val y =
                size.height *
                        t

            val phase =
                t *
                        Math.PI.toFloat() *
                        2.3f

            val center =
                size.width / 2f

            val offset =
                kotlin.math.sin(
                    phase
                ) *
                        size.width *
                        0.24f

            val x1 =
                center + offset

            val x2 =
                center - offset

            if (i == 0) {

                left.moveTo(
                    x1,
                    y
                )

                right.moveTo(
                    x2,
                    y
                )

            } else {

                left.lineTo(
                    x1,
                    y
                )

                right.lineTo(
                    x2,
                    y
                )
            }
        }

        val stroke =
            Stroke(
                width =
                    size.minDimension *
                            0.085f,
                cap =
                    StrokeCap.Round
            )

        drawPath(
            path = left,
            color = color,
            style = stroke
        )

        drawPath(
            path = right,
            color = color,
            style = stroke
        )

        for (i in 2 until steps step 5) {

            val t =
                i.toFloat() /
                        steps.toFloat()

            val y =
                size.height *
                        t

            val phase =
                t *
                        Math.PI.toFloat() *
                        2.3f

            val center =
                size.width / 2f

            val offset =
                kotlin.math.sin(
                    phase
                ) *
                        size.width *
                        0.24f

            drawLine(
                color =
                    color.copy(
                        alpha = 0.75f
                    ),

                start =
                    Offset(
                        center + offset,
                        y
                    ),

                end =
                    Offset(
                        center - offset,
                        y
                    ),

                strokeWidth =
                    size.minDimension *
                            0.055f,

                cap =
                    StrokeCap.Round
            )
        }
    }
}

@Composable
fun BiologicalEffectsContent(
    sample: SensorSample,
    allowReorder: Boolean = false,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {

    val context =
        LocalContext.current

    val groups =
        remember(allowReorder) {
            mutableStateListOf<BiologicalEffectGroup>()
                .apply {
                    addAll(
                        if (allowReorder) {
                            loadBiologicalEffectOrder(
                                context
                            )
                        } else {
                            BiologicalEffectGroup.entries
                        }
                    )
                }
        }

    val expandedStates =
        remember(allowReorder) {
            mutableStateMapOf<
                    BiologicalEffectGroup,
                    Boolean
                    >().apply {
                BiologicalEffectGroup.entries
                    .forEach { group ->
                        this[group] =
                            if (allowReorder) {
                                loadBiologicalEffectExpanded(
                                    context,
                                    group
                                )
                            } else {
                                true
                            }
                    }
            }
        }

    val estimate =
        biologicalEffects(
            sample
        )

    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                UvirIslandSpacing
            )
    ) {

        Card(
            modifier =
                Modifier.fillMaxWidth(),

            shape =
                RoundedCornerShape(
                    16.dp
                ),

            colors =
                CardDefaults.cardColors(
                    containerColor =
                        cardColor
                )
        ) {

            Text(
                text =
                    stringResource(
                        R.string.biological_effects_disclaimer
                    ),

                modifier =
                    Modifier.padding(
                        16.dp
                    ),

                color =
                    secondaryText,

                fontSize =
                    12.sp
            )
        }

        groups.forEach { group ->
            val title =
                when (group) {
                    BiologicalEffectGroup.DNA_UV ->
                        stringResource(
                            R.string.dna_uv_proxy
                        )

                    BiologicalEffectGroup.UVA_PHOTOAGING ->
                        stringResource(
                            R.string.uva_photoaging_proxy
                        )

                    BiologicalEffectGroup.HEV_OXIDATIVE ->
                        stringResource(
                            R.string.hev_oxidative_proxy
                        )
                }

            val description =
                when (group) {
                    BiologicalEffectGroup.DNA_UV ->
                        stringResource(
                            R.string.dna_uv_proxy_description
                        )

                    BiologicalEffectGroup.UVA_PHOTOAGING ->
                        stringResource(
                            R.string.uva_photoaging_proxy_description
                        )

                    BiologicalEffectGroup.HEV_OXIDATIVE ->
                        stringResource(
                            R.string.hev_oxidative_proxy_description
                        )
                }

            val value =
                when (group) {
                    BiologicalEffectGroup.DNA_UV ->
                        estimate.dnaUvProxy

                    BiologicalEffectGroup.UVA_PHOTOAGING ->
                        estimate.uvaPhotoagingProxy

                    BiologicalEffectGroup.HEV_OXIDATIVE ->
                        estimate.hevOxidativeProxy
                }

            val score =
                when (group) {
                    BiologicalEffectGroup.DNA_UV ->
                        estimate.dnaUvScore

                    BiologicalEffectGroup.UVA_PHOTOAGING ->
                        estimate.uvaPhotoagingScore

                    BiologicalEffectGroup.HEV_OXIDATIVE ->
                        estimate.hevOxidativeScore
                }

            BiologicalEffectCard(
                group = group,
                groups = groups,
                title = title,
                description = description,
                value = value,
                score = score,
                expanded =
                    expandedStates[group]
                        ?: true,
                onToggle = {
                    if (allowReorder) {
                        val newState =
                            !(expandedStates[group]
                                ?: true)

                        expandedStates[group] =
                            newState

                        saveBiologicalEffectExpanded(
                            context,
                            group,
                            newState
                        )
                    }
                },
                onOrderChanged = {
                    if (allowReorder) {
                        saveBiologicalEffectOrder(
                            context,
                            groups
                        )
                    }
                },
                allowReorder = allowReorder,
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText
            )
        }
    }
}

@Composable
fun BiologicalEffectCard(
    group: BiologicalEffectGroup,
    groups: MutableList<BiologicalEffectGroup>,
    title: String,
    description: String,
    value: Double,
    score: Float,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOrderChanged: () -> Unit,
    allowReorder: Boolean,
    cardColor: Color,
    primaryText: Color,
    secondaryText: Color
) {

    var dragDistance by remember {
        mutableFloatStateOf(0f)
    }

    val normalizedScore =
        score.coerceIn(
            0f,
            1f
        )

    Card(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(
                18.dp
            ),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    cardColor
            )
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),

            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(
                            group,
                            allowReorder
                        ) {
                            if (allowReorder) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragDistance = 0f
                                    },
                                    onDragEnd = {
                                        dragDistance = 0f
                                    },
                                    onDragCancel = {
                                        dragDistance = 0f
                                    },
                                    onDrag = {
                                            change,
                                            dragAmount ->
                                        change.consume()
                                        dragDistance +=
                                            dragAmount.y

                                        val index =
                                            groups.indexOf(
                                                group
                                            )
                                        val threshold =
                                            55.dp.toPx()

                                        if (
                                            dragDistance > threshold &&
                                            index < groups.lastIndex
                                        ) {
                                            groups.removeAt(index)
                                            groups.add(
                                                index + 1,
                                                group
                                            )
                                            dragDistance = 0f
                                            onOrderChanged()
                                        }

                                        if (
                                            dragDistance < -threshold &&
                                            index > 0
                                        ) {
                                            groups.removeAt(index)
                                            groups.add(
                                                index - 1,
                                                group
                                            )
                                            dragDistance = 0f
                                            onOrderChanged()
                                        }
                                    }
                                )
                            }
                        }
                        .then(
                            if (allowReorder) {
                                Modifier.clickable {
                                    onToggle()
                                }
                            } else {
                                Modifier
                            }
                        ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                if (allowReorder) {
                    Text(
                        text = "≡",
                        color = secondaryText,
                        fontSize = 24.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.width(10.dp)
                    )
                }

                Column(
                    modifier =
                        Modifier.weight(1f),
                    verticalArrangement =
                        Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        color = primaryText,
                        fontSize = 16.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    if (allowReorder && !expanded) {
                        Text(
                            text =
                                stringResource(
                                    R.string.biological_compact_summary,
                                    value,
                                    normalizedScore * 100f
                                ),
                            color = secondaryText,
                            fontSize = 12.sp
                        )
                    }
                }

                if (allowReorder) {
                    ExpansionChevron(
                        expanded = expanded,
                        tint = secondaryText,
                        modifier =
                            Modifier.size(20.dp)
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(
                    color =
                        secondaryText.copy(
                            alpha = 0.20f
                        )
                )

            Text(
                text = description,
                color =
                    secondaryText,
                fontSize =
                    12.sp
            )

            Spacer(
                Modifier.height(
                    2.dp
                )
            )

            Text(
                text =
                    stringResource(
                        R.string.estimated_weighted_signal
                    ),

                color =
                    secondaryText,

                fontSize =
                    11.sp,

                fontWeight =
                    FontWeight.Medium
            )

            Text(
                text =
                    stringResource(
                        R.string.weighted_signal_value,
                        value
                    ),

                color =
                    primaryText,

                fontSize =
                    17.sp,

                fontWeight =
                    FontWeight.Bold
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text =
                        stringResource(
                            R.string.relative_spectral_index
                        ),

                    color =
                        secondaryText,

                    fontSize =
                        11.sp
                )

                Text(
                    text =
                        "%.0f / 100".format(
                            Locale.US,
                            normalizedScore * 100f
                        ),

                    color =
                        primaryText,

                    fontSize =
                        12.sp,

                    fontWeight =
                        FontWeight.Bold
                )
            }

            SpectrumBar(
                percent =
                    normalizedScore,

                trackColor =
                    secondaryText.copy(
                        alpha = 0.18f
                    ),

                barColor =
                    primaryText.copy(
                        alpha = 0.82f
                    )
            )

            Text(
                text =
                    stringResource(
                        R.string.relative_spectral_index_explanation
                    ),

                color =
                    secondaryText,

                fontSize =
                    10.sp
            )
            }
        }
    }
}

// =====================================================
// CONTENUTO GRUPPI
// =====================================================

@Composable
fun SensorGroupContent(
    group: SensorGroup,
    groups: MutableList<SensorGroup>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOrderChanged: () -> Unit,
    allowReorder: Boolean,

    sample: SensorSample,

    uvTotal: Double,
    visibleTotal: Double,
    nirTotal: Double,

    heb: Double,
    hev: Double,

    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,
    trackColor: Color
) {

    when (group) {

        SensorGroup.UV -> {

            SpectrumCard(
                group = group,
                groups = groups,
                title = stringResource(R.string.uv_radiation),
                total = uvTotal,
                unit = "µW/cm²",
                expanded = expanded,
                onToggle = onToggle,
                onOrderChanged = onOrderChanged,
                allowReorder = allowReorder,
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) {

                SpectrumRow(
                    name = "UVC",
                    band = "100–280 nm",
                    value = sample.uvc,
                    percent =
                        percentage(
                            sample.uvc,
                            uvTotal
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = Color(0xFF9C27B0)
                )

                SpectrumRow(
                    name = "UVB",
                    band = "280–315 nm",
                    value = sample.uvb,
                    percent =
                        percentage(
                            sample.uvb,
                            uvTotal
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = Color(0xFF673AB7)
                )

                SpectrumRow(
                    name = "UVA",
                    band = "315–400 nm",
                    value = sample.uva,
                    percent =
                        percentage(
                            sample.uva,
                            uvTotal
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = Color(0xFF3F51B5)
                )
            }
        }

        SensorGroup.HEV_HEB -> {

            SpectrumCard(
                group = group,
                groups = groups,
                title = "HEV / HEB",
                total = hev,
                unit = "µW/cm² HEV",
                expanded = expanded,
                onToggle = onToggle,
                onOrderChanged = onOrderChanged,
                allowReorder = allowReorder,
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) {

                DerivedSpectrumRow(
                    name = "HEV",
                    subtitle = stringResource(R.string.hev_description),
                    band = "400–500 nm",
                    value = hev,
                    percent =
                        percentage(
                            hev,
                            visibleTotal
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = Color(0xFF3949AB)
                )

                DerivedSpectrumRow(
                    name = "HEB",
                    subtitle = stringResource(R.string.heb_description),
                    band = "400–450 nm",
                    value = heb,
                    percent =
                        percentage(
                            heb,
                            visibleTotal
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = Color(0xFF7B1FA2)
                )
            }
        }

        SensorGroup.VISIBLE -> {

            SpectrumCard(
                group = group,
                groups = groups,
                title = stringResource(R.string.visible_light),
                total = visibleTotal,
                unit = "µW/cm²",
                expanded = expanded,
                onToggle = onToggle,
                onOrderChanged = onOrderChanged,
                allowReorder = allowReorder,
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) {

                SpectrumRow(
                    stringResource(R.string.violet),
                    "400–450 nm",
                    sample.violetto,
                    percentage(
                        sample.violetto,
                        visibleTotal
                    ),
                    primaryText,
                    secondaryText,
                    trackColor,
                    Color(0xFF8E24AA)
                )

                SpectrumRow(
                    stringResource(R.string.blue),
                    "450–495 nm",
                    sample.blu,
                    percentage(
                        sample.blu,
                        visibleTotal
                    ),
                    primaryText,
                    secondaryText,
                    trackColor,
                    Color(0xFF1E88E5)
                )

                SpectrumRow(
                    stringResource(R.string.green),
                    "495–570 nm",
                    sample.verde,
                    percentage(
                        sample.verde,
                        visibleTotal
                    ),
                    primaryText,
                    secondaryText,
                    trackColor,
                    Color(0xFF43A047)
                )

                SpectrumRow(
                    stringResource(R.string.yellow),
                    "570–590 nm",
                    sample.giallo,
                    percentage(
                        sample.giallo,
                        visibleTotal
                    ),
                    primaryText,
                    secondaryText,
                    trackColor,
                    Color(0xFFFDD835)
                )

                SpectrumRow(
                    stringResource(R.string.orange),
                    "590–620 nm",
                    sample.arancione,
                    percentage(
                        sample.arancione,
                        visibleTotal
                    ),
                    primaryText,
                    secondaryText,
                    trackColor,
                    Color(0xFFFB8C00)
                )

                SpectrumRow(
                    stringResource(R.string.red),
                    "620–700 nm",
                    sample.rosso,
                    percentage(
                        sample.rosso,
                        visibleTotal
                    ),
                    primaryText,
                    secondaryText,
                    trackColor,
                    Color(0xFFE53935)
                )
            }
        }

        SensorGroup.NIR -> {

            SpectrumCard(
                group = group,
                groups = groups,
                title = stringResource(R.string.far_red_nir),
                total = nirTotal,
                unit = "µW/cm²",
                expanded = expanded,
                onToggle = onToggle,
                onOrderChanged = onOrderChanged,
                allowReorder = allowReorder,
                cardColor = cardColor,
                primaryText = primaryText,
                secondaryText = secondaryText
            ) {

                SpectrumRow(
                    name = "Far-red",
                    band = "picco 745 nm",
                    value = sample.f8,
                    percent =
                        percentage(
                            sample.f8,
                            nirTotal
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = Color(0xFFD32F2F)
                )

                SpectrumRow(
                    name = "NIR",
                    band = "picco 855 nm",
                    value = sample.nir,
                    percent =
                        percentage(
                            sample.nir,
                            nirTotal
                        ),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    trackColor = trackColor,
                    barColor = Color(0xFF8D6E63)
                )
            }
        }
    }
}

@Composable
fun AutomaticBadge(
    primaryText: Color,
    secondaryText: Color
) {
    Surface(
        modifier = Modifier.size(28.dp),
        shape = RoundedCornerShape(50),
        color = secondaryText.copy(alpha = 0.18f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.automatic_badge),
                color = primaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// =====================================================
// CARD RIORDINABILE/APRIBILE
// =====================================================

@Composable
fun SpectrumCard(
    group: SensorGroup,
    groups: MutableList<SensorGroup>,

    title: String,
    total: Double,
    unit: String,

    expanded: Boolean,
    onToggle: () -> Unit,

    onOrderChanged: () -> Unit,
    allowReorder: Boolean,

    cardColor: Color,
    primaryText: Color,
    secondaryText: Color,

    content:
    @Composable ColumnScope.() -> Unit
) {

    var dragDistance by remember {
        mutableFloatStateOf(0f)
    }

    Card(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(18.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    cardColor
            )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),

            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(
                        group,
                        allowReorder
                    ) {

                        if (allowReorder) {

                            detectDragGesturesAfterLongPress(

                                onDragStart = {
                                    dragDistance = 0f
                                },

                                onDragEnd = {
                                    dragDistance = 0f
                                },

                                onDragCancel = {
                                    dragDistance = 0f
                                },

                                onDrag = {
                                        change,
                                        dragAmount ->

                                    change.consume()

                                    dragDistance +=
                                        dragAmount.y

                                    val index =
                                        groups.indexOf(
                                            group
                                        )

                                    val threshold =
                                        55.dp.toPx()

                                    if (
                                        dragDistance >
                                        threshold &&
                                        index <
                                        groups.lastIndex
                                    ) {

                                        groups.removeAt(index)

                                        groups.add(
                                            index + 1,
                                            group
                                        )

                                        dragDistance = 0f

                                        onOrderChanged()
                                    }

                                    if (
                                        dragDistance <
                                        -threshold &&
                                        index > 0
                                    ) {

                                        groups.removeAt(index)

                                        groups.add(
                                            index - 1,
                                            group
                                        )

                                        dragDistance = 0f

                                        onOrderChanged()
                                    }
                                }
                            )
                        }
                    }
                    .clickable {
                        onToggle()
                    },

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                if (allowReorder) {

                    Text(
                        text = "≡",
                        color =
                            secondaryText,
                        fontSize =
                            24.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.width(10.dp)
                    )
                }

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text = title,
                        color =
                            primaryText,
                        fontSize =
                            17.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        text =
                            "%.1f %s".format(
                                total,
                                unit
                            ),
                        color =
                            secondaryText,
                        fontSize =
                            13.sp
                    )
                }

                ExpansionChevron(
                    expanded = expanded,
                    tint = secondaryText,
                    modifier =
                        Modifier.size(20.dp)
                )
            }

            if (expanded) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            secondaryText.copy(
                                alpha = 0.20f
                            )
                        )
                )

                content()
            }
        }
    }
}

// =====================================================
// RIGHE SPETTRALI
// =====================================================

@Composable
fun SpectrumRow(
    name: String,
    band: String,
    value: Double,
    percent: Float,
    primaryText: Color,
    secondaryText: Color,
    trackColor: Color,
    barColor: Color
) {

    Column(
        modifier =
            Modifier.fillMaxWidth(),

        verticalArrangement =
            Arrangement.spacedBy(4.dp)
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            Text(
                text = name,
                color =
                    primaryText,
                fontSize =
                    16.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text = band,
                color =
                    secondaryText,
                fontSize =
                    12.sp
            )
        }

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            Text(
                text =
                    "%.1f µW/cm²".format(
                        value
                    ),
                color =
                    secondaryText,
                fontSize =
                    12.sp
            )

            Text(
                text =
                    "%.1f%%".format(
                        percent * 100
                    ),
                color =
                    secondaryText,
                fontSize =
                    12.sp
            )
        }

        SpectrumBar(
            percent = percent,
            trackColor = trackColor,
            barColor = barColor
        )
    }
}

@Composable
fun DerivedSpectrumRow(
    name: String,
    subtitle: String,
    band: String,
    value: Double,
    percent: Float,
    primaryText: Color,
    secondaryText: Color,
    trackColor: Color,
    barColor: Color
) {

    Column(
        modifier =
            Modifier.fillMaxWidth(),

        verticalArrangement =
            Arrangement.spacedBy(4.dp)
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text = name,
                    color =
                        primaryText,
                    fontSize =
                        16.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text = subtitle,
                    color =
                        secondaryText,
                    fontSize =
                        10.sp
                )
            }

            Text(
                text = band,
                color =
                    secondaryText,
                fontSize =
                    12.sp
            )
        }

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            Text(
                text =
                    "%.1f µW/cm²".format(
                        value
                    ),
                color =
                    secondaryText,
                fontSize =
                    12.sp
            )

            Text(
                text =
                    stringResource(
                        R.string.percent_of_visible,
                        percent * 100
                    ),
                color =
                    secondaryText,
                fontSize =
                    12.sp
            )
        }

        SpectrumBar(
            percent = percent,
            trackColor = trackColor,
            barColor = barColor
        )
    }
}

@Composable
fun SpectrumBar(
    percent: Float,
    trackColor: Color,
    barColor: Color
) {

    val fraction =
        percent.coerceIn(
            0f,
            1f
        )

    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(7.dp)
    ) {
        val cornerRadius =
            CornerRadius(
                size.height / 2f,
                size.height / 2f
            )

        drawRoundRect(
            color = trackColor,
            cornerRadius = cornerRadius
        )

        if (fraction > 0f) {
            drawRoundRect(
                color = barColor,
                size =
                    Size(
                        size.width * fraction,
                        size.height
                    ),
                cornerRadius = cornerRadius
            )
        }
    }
}
