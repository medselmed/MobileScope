package com.medsel.mobilescope.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.medsel.mobilescope.data.Collectors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

// ---------- conteneurs ----------
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(18.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = muted, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
}

@Composable
fun DetailScaffold(cat: Cat, onBack: () -> Unit, onRefresh: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(cat.color.copy(alpha = .16f)),
                contentAlignment = Alignment.Center
            ) { Icon(cat.icon, null, tint = cat.color, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
            Text(cat.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (onRefresh != null) IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Actualiser") }
        }
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

// ---------- visualisations ----------
@Composable
fun Ring(
    progress: Float, color: Color, modifier: Modifier = Modifier.size(96.dp), stroke: Dp = 10.dp,
    track: Color = MaterialTheme.colorScheme.surfaceVariant, content: @Composable BoxScope.() -> Unit = {}
) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val p by animateFloatAsState(if (go) progress.coerceIn(0f, 1f) else 0f, tween(1000, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val tl = Offset(s / 2, s / 2)
            val sz = Size(size.width - s, size.height - s)
            drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(s))
            rotate(-90f) {
                drawArc(
                    Brush.sweepGradient(listOf(color.copy(alpha = .5f), color)),
                    0f, 360f * p, false, tl, sz, style = Stroke(s, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier.fillMaxWidth().height(64.dp)) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val mx = values.max(); val mn = values.min()
        val range = (mx - mn).coerceAtLeast(mx * 0.05f).coerceAtLeast(0.0001f)
        val dx = size.width / (values.size - 1)
        val line = Path(); val fill = Path()
        values.forEachIndexed { i, v ->
            val x = i * dx
            val y = size.height * 0.95f - ((v - mn) / range) * size.height * 0.85f
            if (i == 0) { line.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y) }
            else { line.lineTo(x, y); fill.lineTo(x, y) }
        }
        fill.lineTo(size.width, size.height); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .28f), Color.Transparent)))
        drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

data class Bar(val label: String, val parts: List<Float>)

@Composable
fun BarChart(bars: List<Bar>, colors: List<Color>, modifier: Modifier = Modifier, height: Dp = 130.dp) {
    val max = (bars.maxOfOrNull { it.parts.sum() } ?: 0f).coerceAtLeast(1f)
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val f by animateFloatAsState(if (go) 1f else 0f, tween(900, easing = FastOutSlowInEasing), label = "bars")
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        bars.forEach { b ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.BottomCenter) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))) {
                        b.parts.indices.reversed().forEach { idx ->
                            Box(
                                Modifier.fillMaxWidth().height(height * (b.parts[idx] / max) * f)
                                    .background(colors[idx % colors.size])
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(b.label, fontSize = 11.sp, color = muted, maxLines = 1)
            }
        }
    }
}

@Composable
fun SegmentBar(parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val a by animateFloatAsState(if (go) 1f else 0f, tween(900), label = "seg")
    Row(
        modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).graphicsLayer { alpha = a },
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        parts.filter { it.first > 0f }.forEach { (w, c) -> Box(Modifier.weight(w).fillMaxHeight().background(c)) }
    }
}

@Composable
fun Legend(color: Color, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun RankRow(rank: Int, title: String, subtitle: String, value: String, fraction: Float, color: Color, actions: @Composable () -> Unit = {}) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val a by animateFloatAsState(if (go) fraction.coerceIn(0.02f, 1f) else 0f, tween(900, easing = FastOutSlowInEasing), label = "rank")
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Text("$rank", color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 12.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(value, fontWeight = FontWeight.Bold, color = color)
            actions()
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(a).fillMaxHeight().clip(CircleShape).background(color))
        }
    }
}

// ---------- tuiles ----------
data class Tile(val label: String, val value: String, val icon: ImageVector? = null)

@Composable
fun StatTile(t: Tile, color: Color, modifier: Modifier = Modifier, valueSize: TextUnit = 20.sp) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = .11f)).padding(14.dp)) {
        if (t.icon != null) {
            Icon(t.icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(8.dp))
        }
        Text(t.value, fontSize = valueSize, fontWeight = FontWeight.Bold, maxLines = 2)
        Text(t.label, fontSize = 12.sp, color = muted)
    }
}

@Composable
fun TilesGrid(tiles: List<Tile>, color: Color, valueSize: TextUnit = 20.sp) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { StatTile(it, color, Modifier.weight(1f).fillMaxHeight(), valueSize) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun Hero(color: Color, progress: Float, title: String, body: String, center: @Composable BoxScope.() -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(progress, color, Modifier.size(124.dp), 12.dp, content = center)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(body, fontSize = 13.sp, color = muted)
            }
        }
    }
}

@Composable
fun BigValue(value: String, unit: String = "") {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (unit.isNotEmpty()) Text(unit, fontSize = 12.sp, color = muted)
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, color: Color, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                    .background(if (on) color else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(o, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color(0xFF0B0E14) else muted)
            }
        }
    }
}

// ---------- chargement, permissions ----------
@Composable
fun <T> loadState(key: Int, block: () -> T): State<Result<T>?> =
    produceState<Result<T>?>(null, key) { value = withContext(Dispatchers.IO) { runCatching(block) } }

@Composable
fun <T> Loaded(state: Result<T>?, content: @Composable (T) -> Unit) {
    when {
        state == null -> Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(14.dp))
                Text("Analyse en cours…", color = muted)
            }
        }
        state.isFailure -> Panel { Text("Lecture impossible : ${state.exceptionOrNull()?.message ?: "erreur inconnue"}", color = muted) }
        else -> content(state.getOrThrow())
    }
}

@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var paused = false
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) paused = true
            if (e == Lifecycle.Event.ON_RESUME && paused) { paused = false; tick++ }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

@Composable
fun PermissionGate(cat: Cat, perms: List<String>, why: String, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    fun ok() = perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    var granted by remember { mutableStateOf(ok()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = ok() }
    if (granted) content() else Panel {
        Icon(Icons.Rounded.Lock, null, tint = cat.color, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(10.dp))
        Text("Autorisation requise", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(why, fontSize = 13.sp, color = muted)
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { launcher.launch(perms.toTypedArray()) },
            colors = ButtonDefaults.buttonColors(containerColor = cat.color, contentColor = Color(0xFF0B0E14))
        ) { Text("Autoriser l'accès") }
    }
}

@Composable
fun UsageAccessBanner(cat: Cat, tick: Int, why: String) {
    val ctx = LocalContext.current
    if (tick < 0 || Collectors.hasUsageAccess(ctx)) return
    Panel {
        Icon(Icons.Rounded.Lock, null, tint = cat.color, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text("Accès aux statistiques d'utilisation", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(why, fontSize = 13.sp, color = muted)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            colors = ButtonDefaults.buttonColors(containerColor = cat.color, contentColor = Color(0xFF0B0E14))
        ) { Text("Ouvrir les réglages") }
    }
}

// ---------- actions rapides ----------
fun dial(ctx: Context, number: String) =
    ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

fun composeSms(ctx: Context, number: String) =
    ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

@Composable
fun QuickActions(number: String, color: Color) {
    val ctx = LocalContext.current
    Row {
        IconButton(onClick = { dial(ctx, number) }, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Rounded.Call, "Appeler", tint = color, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { composeSms(ctx, number) }, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Rounded.Sms, "Écrire", tint = color, modifier = Modifier.size(20.dp))
        }
    }
}
