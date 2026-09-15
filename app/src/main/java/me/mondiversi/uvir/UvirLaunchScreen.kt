package me.mondiversi.uvir

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun UvirLaunchGate(
    showLaunchScreen: Boolean = true,
    content: @Composable () -> Unit
) {
    var splashVisible by remember {
        mutableStateOf(showLaunchScreen)
    }

    LaunchedEffect(showLaunchScreen) {
        if (showLaunchScreen) {
            delay(1_050L)
            splashVisible = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        content()

        AnimatedVisibility(
            visible = splashVisible,
            exit = fadeOut(tween(320))
        ) {
            UvirLaunchScreen()
        }
    }
}

@Composable
internal fun UvirLaunchScreen() {
    val transition =
        rememberInfiniteTransition(
            label = "uvir_launch"
        )
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1_350),
                repeatMode = RepeatMode.Reverse
            ),
        label = "uv_pulse"
    )
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2_200),
                repeatMode = RepeatMode.Restart
            ),
        label = "spectral_wave"
    )

    val spectrum =
        listOf(
            Color(0xFF7C3AED),
            Color(0xFF2563EB),
            Color(0xFF06B6D4),
            Color(0xFF22C55E),
            Color(0xFFFACC15),
            Color(0xFFF97316),
            Color(0xFFEF4444)
        )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF050815),
                            Color(0xFF111044),
                            Color(0xFF180B35),
                            Color(0xFF050711)
                        )
                    )
                )
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center =
                Offset(
                    size.width / 2f,
                    size.height * 0.42f
                )
            val baseRadius = size.minDimension * 0.27f

            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                Color(0xFF6D28D9).copy(alpha = 0.30f),
                                Color(0xFF2563EB).copy(alpha = 0.13f),
                                Color.Transparent
                            ),
                        center = center,
                        radius = baseRadius * 1.70f * pulse
                    ),
                radius = baseRadius * 1.70f * pulse,
                center = center
            )

            repeat(3) { index ->
                drawCircle(
                    color =
                        spectrum[index]
                            .copy(alpha = 0.14f - index * 0.025f),
                    radius =
                        baseRadius *
                                (1.02f + index * 0.20f) *
                                pulse,
                    center = center,
                    style = Stroke(
                        width = (1.4f + index * 0.45f).dp.toPx()
                    )
                )
            }

            val wave = Path()
            val waveY = size.height * 0.42f
            val amplitude = size.height * 0.018f
            val steps = 80

            repeat(steps + 1) { index ->
                val fraction = index.toFloat() / steps
                val x = size.width * fraction
                val y =
                    waveY +
                            sin(
                                (fraction * 4.0 * PI) +
                                        phase * 2.0 * PI
                            ).toFloat() * amplitude

                if (index == 0) {
                    wave.moveTo(x, y)
                } else {
                    wave.lineTo(x, y)
                }
            }

            drawPath(
                path = wave,
                brush =
                    Brush.horizontalGradient(spectrum),
                style = Stroke(
                    width = 9.dp.toPx(),
                    cap = StrokeCap.Round
                ),
                alpha = 0.13f
            )
            drawPath(
                path = wave,
                brush =
                    Brush.horizontalGradient(spectrum),
                style = Stroke(
                    width = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                ),
                alpha = 0.92f
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier =
                    Modifier
                        .size(196.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF080B18))
                        .border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(spectrum),
                            shape = CircleShape
                        ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.uvir_logo),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(184.dp)
                            .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = BuildConfig.VERSION_NAME,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )

            Spacer(Modifier.height(7.dp))

            Text(
                text = stringResource(R.string.launch_spectral_subtitle),
                color = Color(0xFFD7D7F8),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                letterSpacing = 0.7.sp
            )

            Spacer(Modifier.height(22.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                spectrum.forEachIndexed { index, color ->
                    Box(
                        modifier =
                            Modifier
                                .width(if (index == 0 || index == 6) 18.dp else 24.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color.copy(alpha = 0.95f))
                    )
                }
            }
        }
    }
}
