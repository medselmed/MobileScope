package com.medsel.mobilescope.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medsel.mobilescope.data.Collectors
import com.medsel.mobilescope.data.Extras
import com.medsel.mobilescope.data.Fmt
import com.medsel.mobilescope.data.History as HistoryStore
import com.medsel.mobilescope.data.LatencyResult
import com.medsel.mobilescope.data.LatencyTarget
import com.medsel.mobilescope.data.Prefs
import com.medsel.mobilescope.data.Tr
import com.medsel.mobilescope.widget.BatteryWidget
import com.medsel.mobilescope.widget.HealthWidget
import com.medsel.mobilescope.widget.NetworkWidget
import com.medsel.mobilescope.widget.StorageWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

// ============================================================ outils communs
@Composable
fun ProScaffold(
    title: String, subtitle: String, icon: ImageVector, color: Color, onBack: () -> Unit,
    onRefresh: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .16f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 12.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onRefresh != null) IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Actualiser") }
        }
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

fun shareText(ctx: Context, subject: String, text: String) {
    val i = Intent(Intent.ACTION_SEND)
    i.type = "text/plain"
    i.putExtra(Intent.EXTRA_SUBJECT, Tr.t(subject))
    i.putExtra(Intent.EXTRA_TEXT, Tr.t(text))
    runCatching { ctx.startActivity(Intent.createChooser(i, Tr.t(subject)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun ToolRow(icon: ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = muted)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = muted)
    }
}

// ============================================================ hub « Outils »
private data class ToolEntry(val route: String, val title: String, val sub: String, val icon: ImageVector, val color: Color)

private val TOOLS = listOf(
    ToolEntry("health", "Santé", "Score global et conseils", Icons.Rounded.HealthAndSafety, Color(0xFF8FD11E)),
    ToolEntry("history", "Historique", "Courbes dans le temps", Icons.Rounded.Timeline, Color(0xFF4C8DFF)),
    ToolEntry("hardware", "Matériel", "CPU, écran, Wi-Fi", Icons.Rounded.Memory, Color(0xFFFF5CA8)),
    ToolEntry("sensors", "Capteurs", "Mesures en direct", Icons.Rounded.Sensors, Color(0xFF14B8D4)),
    ToolEntry("security", "Sécurité", "Contrôles de l'appareil", Icons.Rounded.Security, Color(0xFFFF6B5E)),
    ToolEntry("nettools", "Réseau avancé", "Latence et adresses IP", Icons.Rounded.NetworkCheck, Color(0xFFFFB020)),
    ToolEntry("device", "Mon appareil", "Fiche technique", Icons.Rounded.Smartphone, Color(0xFF8B7CFF)),
    ToolEntry("widgets", "Widgets", "Écran d'accueil Android", Icons.Rounded.Widgets, Color(0xFF22C58B)),
    ToolEntry("cleanup", "Nettoyage", "Caches, apps inutilisées", Icons.Rounded.CleaningServices, Color(0xFFFFB020)),
    ToolEntry("privacy", "Confidentialité", "Qui a accès à quoi", Icons.Rounded.Lock, Color(0xFFFF6B5E)),
    ToolEntry("screentime", "Temps d'écran", "Usage et objectif", Icons.Rounded.Schedule, Color(0xFF8B7CFF)),
    ToolEntry("speed", "Débit en direct", "Vitesse et test", Icons.Rounded.Speed, Color(0xFF14B8D4)),
    ToolEntry("dataplan", "Forfait data", "Suivi mensuel", Icons.Rounded.DataUsage, Color(0xFF4C8DFF)),
    ToolEntry("autonomy", "Autonomie", "Estimation et charge", Icons.Rounded.BatteryChargingFull, Color(0xFF8FD11E))
)

@Composable
fun ToolsHubScreen(onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val makePdf = rememberPdfAction()
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().verticalScroll(rememberScrollState())
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)) {
            Text("Outils", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("Analyses avancées et utilitaires", fontSize = 12.sp, color = muted)
        }
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TOOLS.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { t ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                                .clickable { onOpen(t.route) }.padding(14.dp)
                        ) {
                            Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(t.color.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                                Icon(t.icon, null, tint = t.color, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(t.title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(t.sub, fontSize = 11.sp, color = muted, maxLines = 2)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            SectionTitle("EXPORTER")
            ToolRow(Icons.Rounded.Description, "Rapport PDF", "Santé, courbes sur 7 jours, sécurité et conseils", MaterialTheme.colorScheme.primary) { makePdf() }
            ToolRow(Icons.Rounded.Share, "Partager un rapport complet", "Santé, sécurité, mémoire, stockage, batterie", MaterialTheme.colorScheme.primary) {
                scope.launch { shareText(ctx, "Rapport MobileScope", withContext(Dispatchers.IO) { Extras.report(ctx) }) }
            }
            ToolRow(Icons.Rounded.Description, "Exporter l'historique (CSV)", "Relevés au format tableur", MaterialTheme.colorScheme.primary) {
                scope.launch {
                    val csv = withContext(Dispatchers.IO) { HistoryStore.csv(HistoryStore.load(ctx)) }
                    shareText(ctx, "Historique MobileScope", csv)
                }
            }
        }
    }
}

// ============================================================ santé
@Composable
fun HealthScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val st by loadState(tick) { Extras.health(ctx) }
    ProScaffold("Santé de l'appareil", "Score global, détails et conseils", Icons.Rounded.HealthAndSafety,
        MaterialTheme.colorScheme.primary, onBack, { tick++ }) {
        Loaded(st) { h ->
            val col = scoreColor(h.score)
            Panel {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ScoreGauge(h.score, col) }
                Spacer(Modifier.height(8.dp))
                Text(verdict(h.score), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, color = col)
            }
            SectionTitle("DÉTAIL PAR DOMAINE")
            Panel {
                h.areas.forEachIndexed { i, a ->
                    val route = when (a.key) { "ram" -> "Ram"; "storage" -> "Storage"; "battery" -> "Battery"; else -> "security" }
                    Box(Modifier.clickable { onOpen(route) }) {
                        RankRow(i + 1, a.label, a.detail, "${a.score}", a.score / 100f, scoreColor(a.score))
                    }
                }
            }
            SectionTitle("CONSEILS")
            Panel {
                h.advice.forEach { tip ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Lightbulb, null, tint = Color(0xFFFFB020), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(tip, fontSize = 13.sp)
                    }
                }
            }
            Button(
                onClick = { scope.launch { shareText(ctx, "Rapport MobileScope", withContext(Dispatchers.IO) { Extras.report(ctx) }) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Partager le rapport")
            }
        }
    }
}

// ============================================================ historique
@Composable
private fun ChartPanel(title: String, summary: String, color: Color, values: List<Float>, yMin: Float?, yMax: Float?, unit: String, a: String, b: String) {
    Panel {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(summary, fontSize = 12.sp, color = muted)
        Spacer(Modifier.height(10.dp))
        LineChart(listOf(values), listOf(color), yMin = yMin, yMax = yMax, unit = unit, startLabel = a, endLabel = b)
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var range by remember { mutableIntStateOf(1) }
    var confirmClear by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val st by loadState(tick) { HistoryStore.load(ctx) }
    ProScaffold("Historique", "Évolution mesurée dans le temps", Icons.Rounded.Timeline, primary, onBack, { tick++ }) {
        Loaded(st) { all ->
            val now = System.currentTimeMillis()
            val cutoff = when (range) { 0 -> now - 86_400_000L; 1 -> now - 7 * 86_400_000L; else -> 0L }
            val s = all.filter { it.t >= cutoff }
            Segmented(listOf("24 h", "7 jours", "Tout"), range, primary) { range = it }
            if (s.size < 2) {
                Panel {
                    Icon(Icons.Rounded.Timeline, null, tint = muted, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Pas encore assez de relevés", fontWeight = FontWeight.SemiBold)
                    Text(
                        "L'historique se remplit à chaque ouverture de l'application et toutes les 30 minutes en arrière-plan (réglable dans les préférences).",
                        fontSize = 12.sp, color = muted
                    )
                }
            } else {
                val df = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                val a = df.format(Date(s.first().t)); val b = df.format(Date(s.last().t))
                val ram = s.map { it.ram.toFloat() }
                val sto = s.map { it.storage.toFloat() }
                val bat = s.map { it.battery.toFloat() }
                val tmp = s.map { it.tempX10 / 10f }
                ChartPanel("Mémoire vive utilisée", "Moyenne ${ram.average().toInt()} %, pic ${ram.max().toInt()} %", Cat.Ram.color, ram, 0f, 100f, " %", a, b)
                ChartPanel("Stockage utilisé", "Actuellement ${sto.last().toInt()} %", Cat.Storage.color, sto, 0f, 100f, " %", a, b)
                ChartPanel("Niveau de batterie", "Minimum ${bat.min().toInt()} %, maximum ${bat.max().toInt()} %", Cat.Battery.color, bat, 0f, 100f, " %", a, b)
                ChartPanel("Température de la batterie", "Pic ${"%.1f".format(Locale.US, tmp.max())} °C", Color(0xFFFF8A3D), tmp, null, null, " °", a, b)
                val days = HistoryStore.dailyTraffic(all, 7)
                Panel {
                    Text("Trafic réseau par jour", fontWeight = FontWeight.SemiBold)
                    Text("Déduit des relevés : ${Fmt.bytes(days.sumOf { it.second })} sur 7 jours", fontSize = 12.sp, color = muted)
                    Spacer(Modifier.height(10.dp))
                    BarChart(days.map { Bar(it.first, listOf(it.second.toFloat())) }, listOf(Cat.Network.color))
                }
                TilesGrid(listOf(
                    Tile("Relevés", "${s.size}", Icons.Rounded.Timeline),
                    Tile("Période", Fmt.duration((s.last().t - s.first().t) / 1000), Icons.Rounded.Schedule)
                ), primary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { scope.launch { withContext(Dispatchers.IO) { HistoryStore.record(ctx, force = true) }; tick++ } },
                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("Relever", maxLines = 1) }
                OutlinedButton(
                    onClick = { shareText(ctx, "Historique MobileScope", HistoryStore.csv(all)) },
                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("Exporter CSV", maxLines = 1) }
                OutlinedButton(
                    onClick = { confirmClear = true },
                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("Effacer", maxLines = 1) }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Effacer l'historique ?") },
            text = { Text("Tous les relevés enregistrés sur cet appareil seront supprimés.") },
            confirmButton = { TextButton(onClick = { HistoryStore.clear(ctx); confirmClear = false; tick++ }) { Text("Effacer") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } }
        )
    }
}

// ============================================================ capteurs
private data class LiveSensor(val type: Int, val label: String, val unit: String)

@Composable
fun SensorsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val live = remember {
        listOf(
            LiveSensor(Sensor.TYPE_ACCELEROMETER, "Accéléromètre", "m/s²"),
            LiveSensor(Sensor.TYPE_GYROSCOPE, "Gyroscope", "rad/s"),
            LiveSensor(Sensor.TYPE_MAGNETIC_FIELD, "Magnétomètre", "µT"),
            LiveSensor(Sensor.TYPE_LIGHT, "Luminosité", "lx"),
            LiveSensor(Sensor.TYPE_PROXIMITY, "Proximité", "cm"),
            LiveSensor(Sensor.TYPE_PRESSURE, "Baromètre", "hPa")
        ).filter { sm.getDefaultSensor(it.type) != null }
    }
    var sel by remember { mutableIntStateOf(0) }
    var values by remember { mutableStateOf(FloatArray(0)) }
    val history = remember { mutableStateListOf<Float>() }
    val color = Cat.Network.color

    DisposableEffect(sel) {
        history.clear()
        values = FloatArray(0)
        val sensor = live.getOrNull(sel)?.let { sm.getDefaultSensor(it.type) }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val v = e.values
                if (v.isEmpty()) return
                values = v.clone()
                val m = if (v.size >= 3) sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) else v[0]
                history.add(m)
                if (history.size > 80) history.removeAt(0)
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    val list by loadState(0) { Extras.sensors(ctx) }
    ProScaffold("Capteurs", "Mesures en direct et inventaire", Icons.Rounded.Sensors, color, onBack) {
        if (live.isEmpty()) {
            Panel { Text("Aucun capteur mesurable en direct sur cet appareil.", color = muted) }
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                live.forEachIndexed { i, s ->
                    FilterChip(selected = sel == i, onClick = { sel = i }, label = { Text(s.label) })
                }
            }
            val cur = live[sel.coerceIn(0, live.lastIndex)]
            Panel {
                val mag = history.lastOrNull()
                Text(cur.label, fontSize = 13.sp, color = muted)
                Text(
                    if (mag == null) "En attente d'une mesure…" else String.format(Locale.US, "%.2f %s", mag, cur.unit),
                    fontSize = 30.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Sparkline(history.toList(), color, Modifier.fillMaxWidth().height(90.dp))
            }
            if (values.size >= 3) {
                TilesGrid(listOf(
                    Tile("Axe X", String.format(Locale.US, "%.2f", values[0])),
                    Tile("Axe Y", String.format(Locale.US, "%.2f", values[1])),
                    Tile("Axe Z", String.format(Locale.US, "%.2f", values[2]))
                ), color)
            }
        }
        SectionTitle("INVENTAIRE DES CAPTEURS")
        Loaded(list) { items ->
            var showAll by remember { mutableStateOf(false) }
            val shown = if (showAll) items else items.take(8)
            Panel {
                shown.forEach { s ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.typeName.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${s.name} · ${s.vendor}", fontSize = 11.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(String.format(Locale.US, "%.2f mA", s.powerMa), fontSize = 11.sp, color = color)
                    }
                }
                if (items.size > 8) TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Réduire" else "Voir les ${items.size} capteurs")
                }
            }
        }
    }
}

// ============================================================ sécurité
@Composable
fun SecurityScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val st by loadState(tick) { Extras.security(ctx) }
    ProScaffold("Sécurité", "Contrôles de l'appareil", Icons.Rounded.Security, Cat.Apps.color, onBack, { tick++ }) {
        Loaded(st) { r ->
            val col = scoreColor(r.score)
            Panel {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ScoreGauge(r.score, col, label = "sécurité") }
                Spacer(Modifier.height(6.dp))
                Text("${r.checks.count { it.ok }} contrôles réussis sur ${r.checks.size}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = muted, fontSize = 13.sp)
            }
            Panel {
                r.checks.forEachIndexed { i, c ->
                    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            if (c.ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, null,
                            tint = if (c.ok) Color(0xFF22C58B) else Color(0xFFFFB020), modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(c.detail, fontSize = 12.sp, color = muted)
                        }
                    }
                    if (i < r.checks.lastIndex) HorizontalDivider()
                }
            }
            Text("Le contrôle « root » est une heuristique : il détecte des indices courants mais ne prouve rien.", fontSize = 11.sp, color = muted)
            OutlinedButton(onClick = {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, modifier = Modifier.fillMaxWidth()) { Text("Ouvrir les réglages de sécurité Android") }
        }
    }
}

// ============================================================ matériel
@Composable
fun HardwareScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Prefs.refreshMs(ctx).toLong().coerceAtLeast(1000L))
            tick++
        }
    }
    val cpu by loadState(tick) { Extras.cpu(ctx) }
    val disp by loadState(0) { Extras.display(ctx) }
    val wifi by loadState(tick) { Extras.wifi(ctx) }
    val pink = Cat.Ram.color
    ProScaffold("Matériel", "Processeur, écran et connexion", Icons.Rounded.Memory, pink, onBack) {
        SectionTitle("PROCESSEUR")
        Loaded(cpu) { c ->
            Panel {
                Text(c.soc, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${c.cores.size} cœurs · ${c.abi}", fontSize = 12.sp, color = muted)
                Spacer(Modifier.height(12.dp))
                if (c.cores.any { it.curKhz != null }) {
                    BarChart(c.cores.map { Bar("C${it.index}", listOf((it.curKhz ?: 0L) / 1000f)) }, listOf(pink), height = 110.dp)
                    Spacer(Modifier.height(6.dp))
                    Text("Fréquence actuelle par cœur, en MHz (actualisée en continu)", fontSize = 11.sp, color = muted)
                } else {
                    Text("Android masque les fréquences des cœurs sur cet appareil.", fontSize = 12.sp, color = muted)
                }
            }
            TilesGrid(listOf(
                Tile("Gouverneur", c.governor ?: "—"),
                Tile("Température CPU", c.tempC?.let { String.format(Locale.US, "%.1f °C", it) } ?: "Non exposée"),
                Tile("Fréquence max", c.cores.mapNotNull { it.maxKhz }.maxOrNull()?.let { "${it / 1000} MHz" } ?: "—"),
                Tile("Cœurs actifs", "${c.cores.count { (it.curKhz ?: 0L) > 0L }}/${c.cores.size}")
            ), pink, 17.sp)
        }
        SectionTitle("ÉCRAN")
        Loaded(disp) { d ->
            TilesGrid(listOf(
                Tile("Résolution", d.resolution),
                Tile("Densité", "${d.dpi} dpi"),
                Tile("Fréquence actuelle", String.format(Locale.US, "%.0f Hz", d.refreshHz)),
                Tile("Modes disponibles", if (d.modes.isEmpty()) "—" else d.modes.joinToString(" / ") { String.format(Locale.US, "%.0f", it) } + " Hz"),
                Tile("Diagonale", d.inches?.let { String.format(Locale.US, "%.1f pouces", it) } ?: "—"),
                Tile("Luminosité", d.brightnessPct?.let { "$it %" } ?: "Auto"),
                Tile("Mise en veille", d.timeoutSec?.let { "$it s" } ?: "—")
            ), Cat.Network.color, 17.sp)
        }
        SectionTitle("CONNEXION")
        Loaded(wifi) { w ->
            TilesGrid(listOf(
                Tile("Type", w.type),
                Tile("Adresse IP", w.ip ?: "—"),
                Tile("Signal", w.rssi?.let { "$it dBm" } ?: "—"),
                Tile("Débit de liaison", w.linkMbps?.let { "$it Mb/s" } ?: "—"),
                Tile("Fréquence", w.freqMhz?.let { "$it MHz" + (w.channel?.let { c -> " (canal $c)" } ?: "") } ?: "—"),
                Tile("Norme Wi-Fi", w.standard ?: "—")
            ), Cat.Storage.color, 16.sp)
            if (w.dns.isNotEmpty()) Panel {
                Text("Serveurs DNS", fontWeight = FontWeight.SemiBold)
                w.dns.forEach { Text(it, fontSize = 13.sp, color = muted) }
            }
        }
    }
}

// ============================================================ réseau avancé
private val TARGETS = listOf(
    LatencyTarget("Cloudflare", "1.1.1.1", 443),
    LatencyTarget("Google", "8.8.8.8", 443),
    LatencyTarget("GitHub", "github.com", 443)
)

@Composable
fun NetToolsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<LatencyResult>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    val wifi by loadState(tick) { Extras.wifi(ctx) }
    val colors = listOf(Cat.Network.color, Cat.Ram.color, Cat.Storage.color)
    ProScaffold("Réseau avancé", "Latence et adresses", Icons.Rounded.NetworkCheck, Cat.Network.color, onBack, { tick++ }) {
        Loaded(wifi) { w ->
            TilesGrid(listOf(
                Tile("Connexion", w.type),
                Tile("Adresse IP", w.ip ?: "—"),
                Tile("DNS principal", w.dns.firstOrNull() ?: "—")
            ), Cat.Network.color, 16.sp)
        }
        Panel {
            Text("Test de latence", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Mesure le délai d'ouverture d'une connexion vers trois serveurs publics (5 essais chacun).", fontSize = 12.sp, color = muted)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (!running) scope.launch {
                        running = true
                        results = withContext(Dispatchers.IO) { TARGETS.map { Extras.latency(it) } }
                        running = false
                    }
                },
                enabled = !running, modifier = Modifier.fillMaxWidth()
            ) {
                if (running) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Mesure en cours…")
                } else {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Lancer le test")
                }
            }
        }
        if (results.isNotEmpty()) {
            Panel {
                LineChart(results.map { r -> r.samples.map { (it ?: 0L).toFloat() } }, colors, yMin = 0f, unit = " ms")
                Spacer(Modifier.height(12.dp))
                results.forEachIndexed { i, r ->
                    Legend(colors[i % colors.size], r.target.label, r.avg?.let { "$it ms" } ?: "Injoignable")
                }
            }
            val avgs = results.mapNotNull { it.avg }
            val avg = if (avgs.isEmpty()) null else avgs.sum() / avgs.size
            TilesGrid(listOf(
                Tile("Latence moyenne", avg?.let { "$it ms" } ?: "—"),
                Tile("Meilleure", results.mapNotNull { it.min }.minOrNull()?.let { "$it ms" } ?: "—"),
                Tile("Pire", results.mapNotNull { it.max }.maxOrNull()?.let { "$it ms" } ?: "—"),
                Tile("Perte", "${results.sumOf { it.lossPct } / results.size} %")
            ), Cat.Network.color)
            Text(
                when {
                    avg == null -> "Aucun serveur n'a répondu : vérifiez votre connexion."
                    avg < 40 -> "Excellente réactivité, adaptée aux jeux et aux appels vidéo."
                    avg < 100 -> "Bonne réactivité pour un usage quotidien."
                    else -> "Latence élevée : la navigation et les appels peuvent ralentir."
                },
                fontSize = 13.sp, color = muted
            )
        }
    }
}

// ============================================================ widgets
private fun pinWidget(ctx: Context, cls: Class<*>) {
    val awm = AppWidgetManager.getInstance(ctx)
    if (awm.isRequestPinAppWidgetSupported) {
        awm.requestPinAppWidget(ComponentName(ctx, cls), null, null)
    } else {
        Toast.makeText(ctx, "Ajoutez-le depuis l'écran d'accueil : appui long, puis Widgets.", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun WidgetsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val awm = remember { AppWidgetManager.getInstance(ctx) }
    val healthCount = awm.getAppWidgetIds(ComponentName(ctx, HealthWidget::class.java)).size
    val batteryCount = awm.getAppWidgetIds(ComponentName(ctx, BatteryWidget::class.java)).size
    ProScaffold("Widgets", "Sur votre écran d'accueil", Icons.Rounded.Widgets, Cat.Calls.color, onBack) {
        Panel {
            Text("Quatre widgets sont disponibles", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Ils se mettent à jour automatiquement environ toutes les 30 minutes et à chaque appui sur ↻. Un appui sur le widget ouvre MobileScope.", fontSize = 12.sp, color = muted)
        }
        ToolRow(Icons.Rounded.HealthAndSafety, "Widget Santé", "Score global + anneaux mémoire, stockage, batterie · $healthCount actif(s)", Color(0xFF8FD11E)) {
            pinWidget(ctx, HealthWidget::class.java)
        }
        ToolRow(Icons.Rounded.BatteryChargingFull, "Widget Batterie", "Niveau, état, température, tension · $batteryCount actif(s)", Color(0xFF2DD4F0)) {
            pinWidget(ctx, BatteryWidget::class.java)
        }
        ToolRow(Icons.Rounded.Wifi, "Widget Réseau", "Connexion, trafic et forfait data", Color(0xFF14B8D4)) {
            pinWidget(ctx, NetworkWidget::class.java)
        }
        ToolRow(Icons.Rounded.Storage, "Widget Stockage", "Occupation et espace libre", Color(0xFFFFB020)) {
            pinWidget(ctx, StorageWidget::class.java)
        }
        Text("Si le bouton n'affiche rien, faites un appui long sur l'écran d'accueil, choisissez Widgets, puis MobileScope.", fontSize = 11.sp, color = muted)
    }
}
