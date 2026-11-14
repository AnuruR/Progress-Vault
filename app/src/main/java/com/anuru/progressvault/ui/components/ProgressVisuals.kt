package com.anuru.progressvault.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anuru.progressvault.R
import com.anuru.progressvault.ui.theme.VaultColors
import kotlin.math.roundToInt
import kotlin.random.Random

private const val DIAL_START = 135f
private const val DIAL_SWEEP = 270f

/** Large 270° gauge. Colour shifts cyan → green as it fills; layered strokes give the glow. */
@Composable
fun ProgressDial(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    stroke: Dp = 16.dp,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "dial",
    )
    val color = lerp(VaultColors.Cyan, VaultColors.Green, animated)
    val percent = (animated * 100).roundToInt()
    val description = stringResource(R.string.cd_dial, (progress * 100).roundToInt())
    Box(
        modifier = modifier
            .size(size)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw * 1.6f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(VaultColors.Border, DIAL_START, DIAL_SWEEP, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            // Tick marks every 10%.
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val rOuter = this.size.width / 2 - sw * 0.2f
            for (i in 0..10) {
                rotate(DIAL_START + DIAL_SWEEP * i / 10f, center) {
                    drawLine(
                        VaultColors.TextMuted.copy(alpha = 0.5f),
                        Offset(center.x + rOuter - sw * 0.6f, center.y),
                        Offset(center.x + rOuter, center.y),
                        strokeWidth = 2f,
                    )
                }
            }
            val sweep = DIAL_SWEEP * animated
            if (sweep > 0f) {
                listOf(2.6f to 0.06f, 1.8f to 0.12f, 1.25f to 0.22f).forEach { (mult, alpha) ->
                    drawArc(color.copy(alpha = alpha), DIAL_START, sweep, false, topLeft, arcSize, style = Stroke(sw * mult, cap = StrokeCap.Round))
                }
                drawArc(color, DIAL_START, sweep, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.percent_value, percent), style = MaterialTheme.typography.displayLarge, color = color)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = VaultColors.TextMuted)
        }
    }
}

/** Rounded linear bar with a soft glow behind the fill. */
@Composable
fun GlowProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    contentDescription: String? = null,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "bar",
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height + 6.dp)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
            },
    ) {
        val h = height.toPx()
        val top = (size.height - h) / 2
        val r = CornerRadius(h / 2, h / 2)
        drawRoundRect(VaultColors.Border, Offset(0f, top), Size(size.width, h), r)
        val w = size.width * animated
        if (w > 0f) {
            val glow = 3.dp.toPx()
            drawRoundRect(color.copy(alpha = 0.18f), Offset(-glow / 2, top - glow), Size(w + glow, h + glow * 2), CornerRadius(h, h))
            drawRoundRect(color, Offset(0f, top), Size(w.coerceAtLeast(h), h), r)
        }
    }
}

private data class Particle(
    val x: Float, val vx: Float, val vy: Float, val color: Color, val size: Float, val spin: Float,
)

/** Full-screen confetti that plays once per [burstId], then calls [onFinished]. */
@Composable
fun ConfettiOverlay(burstId: Long, big: Boolean, onFinished: () -> Unit) {
    val particles = remember(burstId) {
        val rnd = Random(burstId)
        val colors = listOf(VaultColors.Cyan, VaultColors.Green, VaultColors.Purple, VaultColors.Amber, VaultColors.Pink, VaultColors.Blue)
        List(if (big) 140 else 60) {
            Particle(
                x = rnd.nextFloat(),
                vx = rnd.nextFloat() * 0.5f - 0.25f,
                vy = rnd.nextFloat() * 0.6f + 0.2f,
                color = colors[rnd.nextInt(colors.size)],
                size = rnd.nextFloat() * 10f + 8f,
                spin = rnd.nextFloat() * 720f - 360f,
            )
        }
    }
    val t = remember(burstId) { Animatable(0f) }
    LaunchedEffect(burstId) {
        t.animateTo(1f, tween(if (big) 2600 else 1700, easing = LinearEasing))
        onFinished()
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = t.value
        particles.forEach { pt ->
            val x = (pt.x + pt.vx * p) * size.width
            val y = (-0.05f + pt.vy * p + 0.9f * p * p) * size.height
            rotate(pt.spin * p, Offset(x, y)) {
                drawRect(
                    pt.color.copy(alpha = (1f - p).coerceIn(0f, 1f)),
                    topLeft = Offset(x, y),
                    size = Size(pt.size, pt.size * 0.45f),
                )
            }
        }
    }
}
