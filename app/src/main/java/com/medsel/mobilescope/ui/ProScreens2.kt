package com.medsel.mobilescope.ui

import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.net.Uri
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.medsel.mobilescope.data.Collectors
import com.medsel.mobilescope.data.Fmt
import com.medsel.mobilescope.data.History as HistoryStore
import com.medsel.mobilescope.data.Insights
import com.medsel.mobilescope.data.Prefs
import com.medsel.mobilescope.data.ReportPdf
import com.medsel.mobilescope.data.Speed
import com.medsel.mobilescope.data.Tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DAY_MS = 86_400_000L

private fun newTask(i: Intent): Intent = i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun openAppSettings(ctx: Context, pkg: String) {
    runCatching { ctx.startActivity(newTask(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))) }
}

fun uninstallApp(ctx: Context, pkg: String) {
    runCatching { ctx.startActivity(newTask(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))) }
}

/** Android interdit à une application ordinaire de vider le cache des autres : on vide le sien puis on ouvre la fenêtre système prévue pour tous les caches. */
fun requestClearAllCaches(ctx: Context): Long {
    val freed = Insights.clearOwnCache(ctx)
    val ok = runCatching { ctx.startActivity(newTask(Intent(StorageManager.ACTION_CLEAR_APP_CACHE))) }.isSuccess
    if (!ok) runCatching { ctx.startActivity(newTask(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))) }
    return freed
}

fun sharePdf(ctx: Context, file: File) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val i = Intent(Intent.ACTION_SEND)
    i.type = "application/pdf"
    i.putExtra(Intent.EXTRA_STREAM, uri)
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { ctx.startActivity(Intent.createChooser(i, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun hours(h: Float): String = Fmt.duration((h * 3600f).toLong())

private val dayFmt get() = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

// ============================================================ confidentialité
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<String?>(null) }
    val audit by loadState(tick) { Insights.permissionAudit(ctx) }
    val extras by loadState(tick) { Insights.securityExtras(ctx) }
    val color = Cat.Apps.color
    ProScaffold("Confidentialité", "Qui a accès à quoi", Icons.Rounded.Security, color, onBack, { tick++ }) {
        Loaded(audit) { groups ->
            fun count(k: String) = groups.firstOrNull { it.key == k }?.apps?.size ?: 0
            TilesGrid(listOf(
                Tile("Accès à la caméra", "${count("camera")}"),
                Tile("Accès au micro", "${count("mic")}"),
                Tile("Accès à la position", "${count("location")}"),
                Tile("Accès aux contacts", "${count("contacts")}"),
                Tile("Accès aux SMS", "${count("sms")}"),
                Tile("Accès au journal d'appels", "${count("calls")}")
            ), color)
            SectionTitle("PAR TYPE D'ACCÈS")
            groups.filter { it.apps.isNotEmpty() }.forEach { g ->
                Panel {
                    Row(
                        Modifier.fillMaxWidth().clickable { open = if (open == g.key) null else g.key },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(g.label, fontWeight = FontWeight.SemiBold)
                            Text("${g.apps.size} applications ont accès", fontSize = 12.sp, color = muted)
                        }
                        Icon(if (open == g.key) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = muted)
                    }
                    if (open == g.key) {
                        Spacer(Modifier.height(6.dp))
                        g.apps.forEach { a ->
                            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(a.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                IconButton(onClick = { openAppSettings(ctx, a.pkg) }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Rounded.Settings, "Réglages de l'application", tint = color, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
            Text("Seules les applications que vous avez installées sont listées. Touchez la roue dentée pour retirer une autorisation.", fontSize = 11.sp, color = muted)
        }
        SectionTitle("SÉCURITÉ ÉTENDUE")
        Loaded(extras) { x ->
            Panel {
                Text("Services d'accessibilité", fontWeight = FontWeight.SemiBold)
                Text(if (x.accessibility.isEmpty()) "Aucun service tiers actif" else x.accessibility.joinToString(", "), fontSize = 12.sp, color = muted)
                Spacer(Modifier.height(10.dp))
                Text("Applications administrateur", fontWeight = FontWeight.SemiBold)
                Text(if (x.admins.isEmpty()) "Aucune application administrateur" else x.admins.joinToString(", "), fontSize = 12.sp, color = muted)
                Spacer(Modifier.height(10.dp))
                Text("Applications hors Play Store (${x.sideloaded.size})", fontWeight = FontWeight.SemiBold)
                Text(if (x.sideloaded.isEmpty()) "Toutes viennent du Play Store" else x.sideloaded.take(12).joinToString(", "), fontSize = 12.sp, color = muted)
            }
        }
    }
}

// ============================================================ nettoyage
@Composable
fun CleanupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val resume = rememberResumeTick()
    var days by remember { mutableIntStateOf(1) }
    var message by remember { mutableStateOf<String?>(null) }
    val st by loadState(tick + resume * 1000) { Insights.cleanup(ctx) }
    val color = Cat.Storage.color
    ProScaffold("Nettoyage", "Caches, applications et stockage", Icons.Rounded.CleaningServices, color, onBack, { tick++ }) {
        Loaded(st) { r ->
            Panel {
                Text("Caches des applications", fontSize = 13.sp, color = muted)
                Text(if (r.hasAccess) Fmt.bytes(r.totalCache) else "—", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Vide d'abord le cache de MobileScope, puis ouvre la fenêtre Android qui vide les caches de toutes les applications. Android demande une confirmation : une application ne peut pas le faire seule.",
                    fontSize = 12.sp, color = muted
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        val freed = requestClearAllCaches(ctx)
                        message = "Cache de MobileScope vidé : " + Fmt.bytes(freed) + " libérés. Confirmez dans la fenêtre Android pour les autres applications."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color(0xFF0B0E14))
                ) {
                    Icon(Icons.Rounded.CleaningServices, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Vider les caches", fontWeight = FontWeight.Bold)
                }
                if (message != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(message!!, fontSize = 12.sp, color = color)
                }
            }
            if (!r.hasAccess) {
                UsageAccessBanner(Cat.Storage, resume, "Autorisez MobileScope dans « Accès aux données d'utilisation » pour voir le poids des caches, les applications inutilisées et le détail du stockage.")
            } else {
                if (r.categoriesOk) {
                    SectionTitle("STOCKAGE PAR CATÉGORIE")
                    Panel {
                        val c2 = Color(0xFF4C8DFF); val c3 = Color(0xFFFF5CA8); val c4 = Color(0xFF22C58B); val c5 = Color(0xFF8A94A8)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DonutChart(
                                listOf(r.images.toFloat() to c2, r.videos.toFloat() to c3, r.audio.toFloat() to c4, r.otherExternal.toFloat() to c5),
                                Modifier.size(120.dp), 16.dp
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Legend(c2, "Photos", Fmt.bytes(r.images))
                                Legend(c3, "Vidéos", Fmt.bytes(r.videos))
                                Legend(c4, "Audio", Fmt.bytes(r.audio))
                                Legend(c5, "Autres fichiers", Fmt.bytes(r.otherExternal))
                            }
                        }
                    }
                }
                val caches = r.apps.sortedByDescending { it.cacheBytes }.take(10).filter { it.cacheBytes > 0L }
                if (caches.isNotEmpty()) {
                    SectionTitle("CACHES LES PLUS LOURDS")
                    Panel {
                        val mx = caches.first().cacheBytes.toFloat().coerceAtLeast(1f)
                        caches.forEachIndexed { i, a ->
                            RankRow(i + 1, a.label, "", Fmt.bytes(a.cacheBytes), a.cacheBytes / mx, color) {
                                IconButton(onClick = { openAppSettings(ctx, a.pkg) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Rounded.Settings, "Réglages de l'application", tint = color, modifier = Modifier.size(19.dp))
                                }
                            }
                        }
                    }
                    Text("La roue dentée ouvre la page de l'application : touchez « Stockage », puis « Vider le cache ».", fontSize = 11.sp, color = muted)
                }
                SectionTitle("APPLICATIONS INUTILISÉES")
                Segmented(listOf("30 jours", "60 jours", "90 jours"), days, color) { days = it }
                val limit = listOf(30, 60, 90)[days.coerceIn(0, 2)] * DAY_MS
                val now = System.currentTimeMillis()
                val unused = r.apps.filter { !it.system && it.pkg != ctx.packageName && (it.lastUsed == null || now - it.lastUsed > limit) }
                    .sortedByDescending { it.sizeBytes }.take(15)
                Panel {
                    if (unused.isEmpty()) {
                        Text("Aucune application inutilisée sur cette période.", color = muted, fontSize = 13.sp)
                    } else {
                        unused.forEach { a ->
                            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(a.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        Fmt.bytes(a.sizeBytes) + " · " + (a.lastUsed?.let { "Dernier usage : " + dayFmt.format(Date(it)) } ?: "Aucun usage depuis 180 jours"),
                                        fontSize = 11.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { uninstallApp(ctx, a.pkg) }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Rounded.Delete, "Désinstaller", tint = Color(0xFFFF6B5E), modifier = Modifier.size(21.dp))
                                }
                            }
                        }
                    }
                }
                val heavy = r.apps.filter { !it.system && it.pkg != ctx.packageName }.sortedByDescending { it.sizeBytes }.take(10)
                if (heavy.isNotEmpty()) {
                    SectionTitle("APPLICATIONS LES PLUS LOURDES")
                    Panel {
                        val mx = heavy.first().sizeBytes.toFloat().coerceAtLeast(1f)
                        heavy.forEachIndexed { i, a ->
                            RankRow(i + 1, a.label, "", Fmt.bytes(a.sizeBytes), a.sizeBytes / mx, color) {
                                IconButton(onClick = { uninstallApp(ctx, a.pkg) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Rounded.Delete, "Désinstaller", tint = Color(0xFFFF6B5E), modifier = Modifier.size(19.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================ temps d'écran
@Composable
fun ScreenTimeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val resume = rememberResumeTick()
    val st by loadState(tick + resume * 1000) { Insights.screenTime(ctx, 14) }
    val apps by loadState(tick + resume * 1000) { Collectors.apps(ctx) }
    val color = Cat.Apps.color
    ProScaffold("Temps d'écran", "Usage quotidien et objectif", Icons.Rounded.Schedule, color, onBack, { tick++ }) {
        Loaded(st) { r ->
            if (!r.hasAccess) {
                UsageAccessBanner(Cat.Apps, resume, "Autorisez MobileScope dans « Accès aux données d'utilisation » pour mesurer votre temps d'écran.")
            } else {
                val goalMs = Prefs.screenGoalMin(ctx) * 60_000L
                val today = r.days.last().second
                val frac = if (goalMs > 0L) today.toFloat() / goalMs else 0f
                val over = today > goalMs
                Hero(if (over) Color(0xFFFF5C5C) else color, frac.coerceAtMost(1f), Fmt.duration(today / 1000) + " aujourd'hui",
                    "Objectif : " + Fmt.duration(goalMs / 1000) + " par jour." + (if (over) " Objectif dépassé." else "")) {
                    BigValue("${(frac * 100).toInt()}%", "de l'objectif")
                }
                val avg = r.thisWeekMs / 7
                val delta = if (r.lastWeekMs > 0L) ((r.thisWeekMs - r.lastWeekMs) * 100 / r.lastWeekMs).toInt() else null
                TilesGrid(listOf(
                    Tile("Moyenne par jour", Fmt.duration(avg / 1000), Icons.Rounded.Schedule),
                    Tile("Cette semaine", Fmt.duration(r.thisWeekMs / 1000)),
                    Tile("Semaine précédente", Fmt.duration(r.lastWeekMs / 1000)),
                    Tile("Écart", if (delta == null) "—" else (if (delta > 0) "+$delta %" else "$delta %"))
                ), color)
                SectionTitle("14 DERNIERS JOURS")
                Panel {
                    LineChart(listOf(r.days.map { it.second / 3_600_000f }), listOf(color), yMin = 0f, unit = " h",
                        startLabel = r.days.first().first, endLabel = r.days.last().first)
                }
                SectionTitle("7 DERNIERS JOURS")
                Panel { BarChart(r.days.takeLast(7).map { Bar(it.first, listOf(it.second / 3_600_000f)) }, listOf(color)) }
            }
        }
        Loaded(apps) { a ->
            if (a.hasAccess && a.top.isNotEmpty()) {
                SectionTitle("LES PLUS UTILISÉES (7 JOURS)")
                Panel {
                    val mx = a.top.first().fgMs.toFloat().coerceAtLeast(1f)
                    a.top.take(8).forEachIndexed { i, u -> RankRow(i + 1, u.label, "", Fmt.duration(u.fgMs / 1000), u.fgMs / mx, color) }
                }
            }
        }
    }
}

// ============================================================ débit en direct + test
@Composable
fun SpeedScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val down = remember { mutableStateListOf<Float>() }
    val up = remember { mutableStateListOf<Float>() }
    var curDown by remember { mutableFloatStateOf(0f) }
    var curUp by remember { mutableFloatStateOf(0f) }
    var phase by remember { mutableStateOf("idle") }
    var live by remember { mutableFloatStateOf(0f) }
    var dl by remember { mutableStateOf<Float?>(null) }
    var ul by remember { mutableStateOf<Float?>(null) }
    var failed by remember { mutableStateOf(false) }
    val color = Cat.Network.color
    val orange = Color(0xFFFF8A3D)

    LaunchedEffect(Unit) {
        var lastRx = TrafficStats.getTotalRxBytes()
        var lastTx = TrafficStats.getTotalTxBytes()
        var lastT = SystemClock.elapsedRealtime()
        while (true) {
            delay(1000)
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val t = SystemClock.elapsedRealtime()
            val dt = (t - lastT) / 1000f
            if (dt > 0f && rx >= lastRx && tx >= lastTx) {
                curDown = (rx - lastRx) * 8f / 1_000_000f / dt
                curUp = (tx - lastTx) * 8f / 1_000_000f / dt
                down.add(curDown); up.add(curUp)
                if (down.size > 60) { down.removeAt(0); up.removeAt(0) }
            }
            lastRx = rx; lastTx = tx; lastT = t
        }
    }

    ProScaffold("Débit en direct", "Vitesse et test de connexion", Icons.Rounded.Speed, color, onBack) {
        Panel {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Réception", fontSize = 12.sp, color = muted)
                    Text(String.format(Locale.US, "%.2f Mb/s", curDown), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
                }
                Column(Modifier.weight(1f)) {
                    Text("Envoi", fontSize = 12.sp, color = muted)
                    Text(String.format(Locale.US, "%.2f Mb/s", curUp), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = orange)
                }
            }
            Spacer(Modifier.height(12.dp))
            LineChart(listOf(down.toList(), up.toList()), listOf(color, orange), yMin = 0f, unit = " Mb/s", height = 120.dp,
                startLabel = "il y a 60 s", endLabel = "maintenant")
            Spacer(Modifier.height(8.dp))
            Legend(color, "Réception", "")
            Legend(orange, "Envoi", "")
        }
        Panel {
            Text("Test de débit", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Télécharge puis envoie des données de test vers les serveurs de mesure publics de Cloudflare. Utilise environ 20 Mo de données.", fontSize = 12.sp, color = muted)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (phase == "idle") scope.launch {
                        failed = false; dl = null; ul = null
                        phase = "down"; live = 0f
                        dl = withContext(Dispatchers.IO) { Speed.download { live = it } }
                        phase = "up"; live = 0f
                        ul = withContext(Dispatchers.IO) { Speed.upload { live = it } }
                        phase = "idle"
                        failed = dl == null && ul == null
                    }
                },
                enabled = phase == "idle", modifier = Modifier.fillMaxWidth()
            ) {
                if (phase != "idle") {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        (if (phase == "down") "Réception : " else "Envoi : ") + String.format(Locale.US, "%.1f Mb/s", live)
                    )
                } else {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mesurer le débit")
                }
            }
            if (failed) {
                Spacer(Modifier.height(8.dp))
                Text("Mesure impossible : vérifiez votre connexion.", fontSize = 12.sp, color = Color(0xFFFF8A80))
            }
        }
        if (dl != null || ul != null) {
            TilesGrid(listOf(
                Tile("Débit descendant", dl?.let { String.format(Locale.US, "%.1f Mb/s", it) } ?: "—", Icons.Rounded.Download),
                Tile("Débit montant", ul?.let { String.format(Locale.US, "%.1f Mb/s", it) } ?: "—", Icons.Rounded.Share)
            ), color)
            val d = dl
            if (d != null) Text(
                when {
                    d >= 50f -> "Excellent : streaming 4K, jeux en ligne et téléchargements rapides."
                    d >= 15f -> "Bon : vidéo HD et visioconférence sans souci."
                    d >= 5f -> "Correct : navigation et vidéo standard."
                    else -> "Faible : la vidéo et les téléchargements risquent de ralentir."
                }, fontSize = 13.sp, color = muted
            )
        }
    }
}

// ============================================================ forfait data
@Composable
fun DataPlanScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val resume = rememberResumeTick()
    var capGb by remember { mutableFloatStateOf(Prefs.dataCapMb(ctx) / 1024f) }
    var cycle by remember { mutableIntStateOf(Prefs.capCycleDay(ctx)) }
    val st by loadState(tick + resume * 1000 + cycle) { Insights.mobileUsedSince(ctx, Insights.cycleStart(cycle)) }
    val color = Cat.Network.color
    ProScaffold("Forfait data", "Suivi des données mobiles", Icons.Rounded.DataUsage, color, onBack, { tick++ }) {
        Panel {
            Text("Forfait mensuel", fontWeight = FontWeight.SemiBold)
            Text(if (capGb < 0.5f) "Désactivé" else String.format(Locale.US, "%.0f Go", capGb), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            Slider(
                value = capGb, onValueChange = { capGb = it },
                onValueChangeFinished = { Prefs.saveDataCapMb(ctx, (capGb * 1024f).toInt()); tick++ },
                valueRange = 0f..200f
            )
            Text("Jour de renouvellement : $cycle", fontWeight = FontWeight.SemiBold)
            Slider(
                value = cycle.toFloat(), onValueChange = { cycle = it.toInt().coerceIn(1, 28) },
                onValueChangeFinished = { Prefs.saveCapCycleDay(ctx, cycle) },
                valueRange = 1f..28f
            )
            Text("Une alerte est envoyée à 80 % et à 100 % si les alertes sont activées dans les réglages.", fontSize = 11.sp, color = muted)
        }
        Loaded(st) { used ->
            if (used == null) {
                UsageAccessBanner(Cat.Network, resume, "Autorisez MobileScope dans « Accès aux données d'utilisation » pour mesurer vos données mobiles.")
            } else {
                val capB = (capGb * 1024f).toLong() * (if (Fmt.decimal) 1_000_000L else 1_048_576L)
                val start = Insights.cycleStart(cycle)
                val elapsed = ((System.currentTimeMillis() - start) / DAY_MS + 1).coerceAtLeast(1L)
                val perDay = used / elapsed
                if (capB > 0L) {
                    val frac = used.toFloat() / capB
                    val col = if (frac >= 1f) Color(0xFFFF5C5C) else if (frac >= 0.8f) Color(0xFFFFB020) else color
                    Hero(col, frac.coerceAtMost(1f), Fmt.bytes(used) + " sur " + Fmt.bytes(capB),
                        if (used < capB) "Il reste " + Fmt.bytes(capB - used) + "." else "Forfait dépassé de " + Fmt.bytes(used - capB) + ".") {
                        BigValue("${(frac * 100).toInt()}%", "du forfait")
                    }
                } else {
                    Panel {
                        Text(Fmt.bytes(used), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text("consommés depuis le " + dayFmt.format(Date(start)), fontSize = 12.sp, color = muted)
                    }
                }
                TilesGrid(listOf(
                    Tile("Moyenne par jour", Fmt.bytes(perDay), Icons.Rounded.DataUsage),
                    Tile("Projection fin de cycle", Fmt.bytes(perDay * 30L)),
                    Tile("Début du cycle", dayFmt.format(Date(start)))
                ), color, 17.sp)
            }
        }
    }
}

// ============================================================ autonomie et charge
@Composable
fun AutonomyScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var bat by remember { mutableStateOf<com.medsel.mobilescope.data.BatteryInfo?>(null) }
    var samples by remember { mutableStateOf<List<com.medsel.mobilescope.data.Sample>>(emptyList()) }
    val current = remember { mutableStateListOf<Float>() }
    val refresh = remember { Prefs.refreshMs(ctx).toLong() }
    val color = Cat.Battery.color
    LaunchedEffect(Unit) {
        samples = withContext(Dispatchers.IO) { HistoryStore.load(ctx) }
        while (true) {
            val r = withContext(Dispatchers.IO) { runCatching { Collectors.battery(ctx) }.getOrNull() }
            if (r != null) {
                bat = r
                r.currentMa?.let { current.add(it.toFloat()); if (current.size > 60) current.removeAt(0) }
            }
            delay(refresh + 1000L)
        }
    }
    ProScaffold("Autonomie et charge", "Estimations d'après vos relevés", Icons.Rounded.BatteryChargingFull, color, onBack) {
        val b = bat
        if (b == null) {
            Loaded<Unit>(null) { }
        } else {
            val a = Insights.autonomy(samples, b.percent, b.charging, b.currentMa, b.capacityMah)
            val title = when {
                b.charging && a.hoursToFull != null -> "Charge complète dans " + hours(a.hoursToFull)
                b.charging -> "En charge"
                a.hoursLeft != null -> "Autonomie restante : " + hours(a.hoursLeft)
                else -> "Autonomie : données insuffisantes"
            }
            val body = when {
                b.charging -> "Branchée : ${b.plug}."
                a.drainPerHour != null -> String.format(Locale.US, "Décharge moyenne : %.1f %% par heure.", a.drainPerHour)
                else -> "Utilisez le téléphone quelques heures : l'estimation s'affine avec l'historique."
            }
            Hero(color, b.percent / 100f, title, body) { BigValue("${b.percent}%", if (b.charging) "en charge" else "restant") }
            TilesGrid(listOf(
                Tile("Rythme de décharge", a.drainPerHour?.let { String.format(Locale.US, "%.1f %%/h", it) } ?: "—", Icons.Rounded.Speed),
                Tile("Vitesse de charge", a.chargePerHour?.let { String.format(Locale.US, "%.1f %%/h", it) } ?: "—", Icons.Rounded.Bolt),
                Tile("Courant actuel", b.currentMa?.let { "$it mA" } ?: "—"),
                Tile("Capacité estimée", b.capacityMah?.let { "$it mAh" } ?: "—")
            ), color)
            if (current.size >= 2) {
                SectionTitle("COURANT EN DIRECT")
                Panel {
                    LineChart(listOf(current.toList()), listOf(color), yMin = 0f, unit = " mA", height = 110.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(if (b.charging) "Plus la valeur est haute, plus la charge est rapide." else "Plus la valeur est haute, plus la batterie se vide vite.", fontSize = 11.sp, color = muted)
                }
            }
            val recent = samples.filter { it.t >= System.currentTimeMillis() - DAY_MS }
            if (recent.size >= 2) {
                SectionTitle("NIVEAU SUR 24 HEURES")
                Panel {
                    val df = SimpleDateFormat("HH:mm", Locale.getDefault())
                    LineChart(listOf(recent.map { it.battery.toFloat() }), listOf(color), yMin = 0f, yMax = 100f, unit = " %",
                        startLabel = df.format(Date(recent.first().t)), endLabel = df.format(Date(recent.last().t)))
                }
            }
        }
    }
}

// ============================================================ rapport PDF
@Composable
fun rememberPdfAction(): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            Toast.makeText(ctx, Tr.t("Création du rapport…"), Toast.LENGTH_SHORT).show()
            val f = withContext(Dispatchers.IO) { runCatching { ReportPdf.build(ctx) }.getOrNull() }
            if (f != null) sharePdf(ctx, f) else Toast.makeText(ctx, Tr.t("Impossible de créer le rapport."), Toast.LENGTH_LONG).show()
        }
    }
}
