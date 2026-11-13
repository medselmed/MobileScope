package com.medsel.mobilescope.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

fun scoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF8FD11E)
    score >= 55 -> Color(0xFFFFB020)
    else -> Color(0xFFFF5C5C)
}

fun verdict(score: Int): String = when {
    score >= 85 -> "Excellent état"
    score >= 70 -> "Bon état"
    score >= 55 -> "État correct, quelques points à surveiller"
    else -> "Attention : des actions sont recommandées"
}

/** Jauge à 270° avec le score au centre. */
@Composable
fun ScoreGauge(score: Int, color: Color, modifier: Modifier = Modifier.size(176.dp), label: String = "sur 100") {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val p by animateFloatAsState(if (go) score.coerceIn(0, 100) / 100f else 0f, tween(1200, easing = FastOutSlowInEasing), label = "gauge")
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = 16.dp.toPx()
            val tl = Offset(s / 2, s / 2)
            val sz = Size(size.width - s, size.height - s)
            drawArc(track, 135f, 270f, false, tl, sz, style = Stroke(s, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * p, false, tl, sz, style = Stroke(s, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(p * 100).toInt()}", fontSize = 46.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, color = muted)
        }
    }
}

/** Anneau segmenté (répartition). Les parts sont des poids relatifs. */
@Composable
fun DonutChart(
    parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier.size(150.dp), stroke: Dp = 18.dp,
    center: @Composable BoxScope.() -> Unit = {}
) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val k by animateFloatAsState(if (go) 1f else 0f, tween(1000, easing = FastOutSlowInEasing), label = "donut")
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val tl = Offset(s / 2, s / 2)
            val sz = Size(size.width - s, size.height - s)
            drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(s))
            val total = parts.sumOf { it.first.toDouble() }.toFloat()
            if (total > 0f) {
                var start = -90f
                parts.forEach { (w, c) ->
                    val sweep = 360f * (w / total) * k
                    if (sweep > 2.5f) drawArc(c, start, sweep - 2f, false, tl, sz, style = Stroke(s))
                    start += sweep
                }
            }
        }
        center()
    }
}

private fun axis(v: Float, span: Float): String =
    if (span < 10f) String.format(Locale.US, "%.1f", v) else String.format(Locale.US, "%.0f", v)

/** Courbes multiples avec grille, repères d'axe et point final. */
@Composable
fun LineChart(
    series: List<List<Float>>, colors: List<Color>, modifier: Modifier = Modifier, height: Dp = 140.dp,
    yMin: Float? = null, yMax: Float? = null, unit: String = "", startLabel: String = "", endLabel: String = ""
) {
    val all = series.flatten()
    if (all.size < 2) {
        Text("Pas encore assez de points pour tracer la courbe.", fontSize = 12.sp, color = muted)
        return
    }
    val lo = yMin ?: all.min()
    val hiRaw = yMax ?: all.max()
    val hi = if (hiRaw <= lo) lo + 1f else hiRaw
    val grid = MaterialTheme.colorScheme.outline
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Column(
                Modifier.height(height).padding(end = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End
            ) {
                Text(axis(hi, hi - lo) + unit, fontSize = 10.sp, color = muted)
                Text(axis((hi + lo) / 2f, hi - lo) + unit, fontSize = 10.sp, color = muted)
                Text(axis(lo, hi - lo) + unit, fontSize = 10.sp, color = muted)
            }
            Canvas(Modifier.weight(1f).height(height)) {
                for (k in 0..3) {
                    val y = size.height * k / 3f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                series.forEachIndexed { si, vals ->
                    if (vals.size >= 2) {
                        val color = colors[si % colors.size]
                        val path = Path()
                        val dx = size.width / (vals.size - 1)
                        var lastY = 0f
                        vals.forEachIndexed { i, v ->
                            val x = i * dx
                            val y = size.height - ((v - lo) / (hi - lo)).coerceIn(0f, 1f) * size.height
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            lastY = y
                        }
                        drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawCircle(color, 4.dp.toPx(), Offset(size.width, lastY))
                    }
                }
            }
        }
        if (startLabel.isNotEmpty() || endLabel.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 36.dp, top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(startLabel, fontSize = 10.sp, color = muted)
                Text(endLabel, fontSize = 10.sp, color = muted)
            }
        }
    }
}
