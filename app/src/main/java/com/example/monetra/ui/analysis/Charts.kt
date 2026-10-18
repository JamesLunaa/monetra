package com.example.monetra.ui.analysis

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Grows charts in once per data change; a crossfade-free 0→1 sweep. */
@Composable
private fun rememberReveal(key: Any?): Float {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, tween(450)) }
    return a.value
}

/**
 * Donut of [shares] (0..1, in order) in [colors]. Segment length is exact share; a hairline gap
 * of ground separates neighbours. [center] sits in the hole.
 */
@Composable
fun Donut(
    shares: List<Float>,
    colors: List<Color>,
    track: Color,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    thickness: Dp = 26.dp,
    center: @Composable BoxScope.() -> Unit,
) {
    val reveal = rememberReveal(shares)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = thickness.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            val gap = if (shares.count { it > 0f } > 1) 1.2f else 0f
            var start = -90f
            shares.forEachIndexed { i, share ->
                val sweep = share * 360f * reveal
                if (sweep > gap) {
                    drawArc(colors[i], start + gap / 2, sweep - gap, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                }
                start += sweep
            }
        }
        center()
    }
}

/**
 * Running totals as straight segments (no smoothing): [now] solid in [color], [before] as a
 * dashed comparison line. Both share one scale; x is the day index across [days] slots.
 */
@Composable
fun PaceChart(now: List<Long>, before: List<Long>, days: Int, color: Color, beforeColor: Color, grid: Color, modifier: Modifier) {
    val reveal = rememberReveal(now to before)
    Canvas(modifier) {
        val max = (now + before).maxOrNull()?.coerceAtLeast(1) ?: 1
        val slots = maxOf(days, before.size, now.size, 2) - 1
        fun x(i: Int) = size.width * i / slots
        fun y(v: Long) = size.height - size.height * v / max.toFloat()
        for (k in 0..3) {
            val gy = size.height * k / 3f
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
        }
        fun line(values: List<Long>, c: Color, dashed: Boolean) {
            if (values.isEmpty()) return
            val count = (values.size * reveal).toInt().coerceIn(1, values.size)
            val p = Path().apply {
                moveTo(x(0), y(values[0]))
                for (i in 1 until count) lineTo(x(i), y(values[i]))
            }
            drawPath(
                p, c,
                style = Stroke(
                    width = if (dashed) 2.dp.toPx() else 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null,
                ),
            )
            if (!dashed) drawCircle(c, 5.dp.toPx(), Offset(x(count - 1), y(values[count - 1])))
        }
        line(before, beforeColor, dashed = true)
        line(now, color, dashed = false)
    }
}

/** Pairs of bars per slot (e.g. income and expense per month), bottom-aligned on one scale. */
@Composable
fun PairBars(
    pairs: List<Pair<Long, Long>>,
    first: Color,
    second: Color,
    highlight: Int,
    dim: Color,
    modifier: Modifier,
) {
    val reveal = rememberReveal(pairs)
    Canvas(modifier) {
        val max = pairs.maxOfOrNull { maxOf(it.first, it.second) }?.coerceAtLeast(1) ?: 1
        val slot = size.width / pairs.size.coerceAtLeast(1)
        val bar = (slot * 0.28f).coerceAtMost(14.dp.toPx())
        val gap = 2.dp.toPx()
        pairs.forEachIndexed { i, (a, b) ->
            val cx = slot * i + slot / 2
            fun col(v: Long, left: Float, c: Color) {
                val h = size.height * v / max.toFloat() * reveal
                if (h > 0f) drawRoundRect(
                    if (i == highlight || highlight < 0) c else c.copy(alpha = 0.45f),
                    Offset(left, size.height - h), Size(bar, h),
                    androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                )
            }
            col(a, cx - bar - gap / 2, first)
            col(b, cx + gap / 2, second)
        }
        drawLine(dim, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
    }
}
