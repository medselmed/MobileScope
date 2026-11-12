package com.medsel.mobilescope.ui

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.medsel.mobilescope.data.Collectors
import com.medsel.mobilescope.data.Fmt
import com.medsel.mobilescope.data.TopContact
import com.medsel.mobilescope.data.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun day(t: Long) = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(t))

// =============== ACCUEIL ===============
@Composable
fun HomeScreen(user: User, onOpen: (Cat) -> Unit, onSignOut: () -> Unit) {
    val ctx = LocalContext.current
    val deviceState by loadState(0) { Collectors.device(ctx) }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("MobileScope", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (user.guest) "Mode invité" else "Bonjour, ${user.name}",
                    fontSize = 13.sp,
                    color = muted
                )
            }
            IconButton(onClick = onSignOut) {
                Icon(Icons.Rounded.Logout, "Se déconnecter", tint = muted)
            }
        }

        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionTitle("Votre appareil")
            Loaded(deviceState) { device ->
                Panel {
                    Text(device.model, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text(device.android, fontSize = 13.sp, color = muted)
                    Spacer(Modifier.height(12.dp))
                    Legend(MaterialTheme.colorScheme.primary, "Correctif de sécurité", device.patch)
                    Legend(MaterialTheme.colorScheme.primary, "Processeurs", "${device.cores} cœurs")
                    Legend(MaterialTheme.colorScheme.primary, "Écran", device.screen)
                }
            }

            SectionTitle("Diagnostic du téléphone")
            Cat.values().forEach { cat ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                        .clickable { onOpen(cat) }
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                            .background(cat.color.copy(alpha = .15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(cat.icon, null, tint = cat.color, modifier = Modifier.size(25.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cat.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(cat.sub, fontSize = 12.sp, color = muted)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = muted)
                }
            }
        }
    }
}

// =============== CONTACTS ===============
@Composable
fun ContactsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cat = Cat.Contacts
    var tick by remember { mutableIntStateOf(0) }
    DetailScaffold(cat, onBack, { tick++ }) {
        PermissionGate(cat, listOf(Manifest.permission.READ_CONTACTS),
            "MobileScope lit votre carnet pour compter vos contacts, repérer les doublons et afficher vos favoris. Les données restent sur l'appareil.") {
            val st by loadState(tick) { Collectors.contacts(ctx) }
            Loaded(st) { r ->
                val frac = if (r.total > 0) r.withPhone.toFloat() / r.total else 0f
                Hero(cat.color, frac, "${Fmt.pct(frac)} ont un numéro", "${r.withEmail} contacts ont un e-mail, ${r.starred} sont en favoris.") {
                    BigValue("${r.total}", "contacts")
                }
                TilesGrid(listOf(
                    Tile("Avec numéro", "${r.withPhone}", Icons.Rounded.Call),
                    Tile("Avec e-mail", "${r.withEmail}", Icons.Rounded.Email),
                    Tile("Favoris", "${r.starred}", Icons.Rounded.Star),
                    Tile("Numéros en doublon", "${r.dupNumbers}", Icons.Rounded.ContentCopy)
                ), cat.color)
                if (r.favorites.isNotEmpty()) {
                    SectionTitle("Favoris")
                    Panel {
                        r.favorites.forEach { c ->
                            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(38.dp).clip(CircleShape).background(cat.color.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                                    Text(c.name.take(1).uppercase(), color = cat.color, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (c.number != null) Text(c.number, fontSize = 12.sp, color = muted)
                                }
                                if (c.number != null) QuickActions(c.number, cat.color)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============== APPELS ===============
@Composable
private fun TopList(list: List<TopContact>, color: Color, unit: (TopContact) -> String) {
    val max = (list.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    Panel {
        list.forEachIndexed { i, t ->
            RankRow(i + 1, t.name, unit(t), "${t.count}×", t.count.toFloat() / max, color) { QuickActions(t.number, color) }
        }
    }
}

@Composable
fun CallsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cat = Cat.Calls
    var tick by remember { mutableIntStateOf(0) }
    DetailScaffold(cat, onBack, { tick++ }) {
        PermissionGate(cat, listOf(Manifest.permission.READ_CALL_LOG),
            "MobileScope lit le journal d'appels des 30 derniers jours pour calculer vos durées et vos contacts les plus appelés.") {
            val st by loadState(tick) { Collectors.calls(ctx) }
            Loaded(st) { r ->
                val answered = r.incoming + r.outgoing
                val frac = if (r.total > 0) answered.toFloat() / r.total else 0f
                val talk = r.inSec + r.outSec
                Hero(cat.color, frac, "${Fmt.duration(talk)} de conversation",
                    if (answered > 0) "Durée moyenne ${Fmt.duration(talk / answered)} par appel abouti." else "Aucun appel abouti sur 30 jours.") {
                    BigValue("${r.total}", "appels · 30 j")
                }
                TilesGrid(listOf(
                    Tile("Entrants", "${r.incoming}", Icons.Rounded.CallReceived),
                    Tile("Sortants", "${r.outgoing}", Icons.Rounded.CallMade),
                    Tile("Manqués", "${r.missed}", Icons.Rounded.CallMissed),
                    Tile("Rejetés", "${r.rejected}", Icons.Rounded.Block),
                    Tile("Temps en réception", Fmt.duration(r.inSec), Icons.Rounded.Schedule),
                    Tile("Plus long appel", Fmt.duration(r.longest), Icons.Rounded.Timer)
                ), cat.color)
                SectionTitle("Appels sur 7 jours")
                Panel {
                    val labels = Collectors.dayLabels(7)
                    BarChart(r.perDay.mapIndexed { i, v -> Bar(labels[i], listOf(v.toFloat())) }, listOf(cat.color))
                }
                if (r.top.isNotEmpty()) {
                    SectionTitle("Contacts les plus fréquents")
                    TopList(r.top, cat.color) { Fmt.duration(it.seconds) + " · " + it.number }
                }
            }
        }
    }
}

// =============== SMS ===============
@Composable
fun SmsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cat = Cat.Sms
    var tick by remember { mutableIntStateOf(0) }
    DetailScaffold(cat, onBack, { tick++ }) {
        PermissionGate(cat, listOf(Manifest.permission.READ_SMS),
            "MobileScope compte vos SMS reçus et envoyés sur 30 jours. Le contenu des messages n'est jamais affiché ni transmis, seule leur longueur est mesurée.") {
            val st by loadState(tick) { Collectors.sms(ctx) }
            Loaded(st) { r ->
                val total = r.received + r.sent
                val frac = if (total > 0) r.sent.toFloat() / total else 0f
                Hero(cat.color, frac, "${r.sent} envoyés, ${r.received} reçus",
                    "Moyenne de ${"%.1f".format(Locale.getDefault(), total / 30f)} messages par jour.") {
                    BigValue("$total", "SMS · 30 j")
                }
                TilesGrid(listOf(
                    Tile("Reçus", "${r.received}", Icons.Rounded.Download),
                    Tile("Envoyés", "${r.sent}", Icons.Rounded.Sms),
                    Tile("Caractères reçus", "${r.charsReceived}"),
                    Tile("Caractères envoyés", "${r.charsSent}")
                ), cat.color)
                SectionTitle("Activité sur 7 jours")
                Panel {
                    val labels = Collectors.dayLabels(7)
                    BarChart(
                        r.recvPerDay.indices.map { Bar(labels[it], listOf(r.recvPerDay[it].toFloat(), r.sentPerDay[it].toFloat())) },
                        listOf(cat.color, cat.color.copy(alpha = .4f))
                    )
                    Spacer(Modifier.height(12.dp))
                    Legend(cat.color, "Reçus", "${r.recvPerDay.sum()}")
                    Legend(cat.color.copy(alpha = .4f), "Envoyés", "${r.sentPerDay.sum()}")
                }
                if (r.top.isNotEmpty()) {
                    SectionTitle("Correspondants les plus actifs")
                    TopList(r.top, cat.color) { it.number }
                }
            }
        }
    }
}

// =============== RÉSEAU ===============
@Composable
fun NetworkScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cat = Cat.Network
    var tick by remember { mutableIntStateOf(0) }
    val resume = rememberResumeTick()
    var sel by remember { mutableIntStateOf(1) }
    DetailScaffold(cat, onBack, { tick++ }) {
        val st by loadState(tick + resume * 1000) { Collectors.network(ctx) }
        Loaded(st) { r ->
            val quality = when {
                !r.online -> 0f
                r.wifiRssi != null -> ((r.wifiRssi + 100) / 60f).coerceIn(0.1f, 1f)
                else -> 1f
            }
            val extra = buildString {
                append("Débit annoncé : ↓ ${r.downKbps / 1000} Mb/s, ↑ ${r.upKbps / 1000} Mb/s.")
                if (r.wifiLink != null) append(" Liaison ${r.wifiLink} Mb/s.")
                if (r.wifiRssi != null) append(" Signal ${r.wifiRssi} dBm.")
            }
            Hero(cat.color, quality, r.type, extra) {
                Icon(Icons.Rounded.Wifi, null, tint = cat.color, modifier = Modifier.size(40.dp))
            }
            SectionTitle("Depuis le dernier démarrage")
            TilesGrid(listOf(
                Tile("Reçu (total)", Fmt.bytes(r.boot.rx), Icons.Rounded.Download),
                Tile("Envoyé (total)", Fmt.bytes(r.boot.tx), Icons.Rounded.CallMade),
                Tile("Reçu en mobile", Fmt.bytes(r.bootMobile.rx), Icons.Rounded.SignalCellularAlt),
                Tile("Envoyé en mobile", Fmt.bytes(r.bootMobile.tx), Icons.Rounded.SignalCellularAlt)
            ), cat.color)
            if (!r.hasAccess || r.periods.isEmpty()) {
                UsageAccessBanner(cat, resume, "Autorisez MobileScope dans « Accès aux données d'utilisation » pour voir la consommation par jour, par période et par application.")
            } else {
                val p = r.periods[sel.coerceIn(0, r.periods.lastIndex)]
                Segmented(r.periods.map { it.label }, sel, cat.color) { sel = it }
                Panel {
                    val tw = p.wifi.total.toFloat(); val tm = p.mobile.total.toFloat()
                    Text(Fmt.bytes(p.wifi.total + p.mobile.total), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("consommés sur la période", fontSize = 12.sp, color = muted)
                    Spacer(Modifier.height(14.dp))
                    SegmentBar(listOf(tw to cat.color, tm to Color(0xFFFF8A3D)))
                    Spacer(Modifier.height(10.dp))
                    Legend(cat.color, "Wi-Fi", Fmt.bytes(p.wifi.total))
                    Legend(Color(0xFFFF8A3D), "Données mobiles", Fmt.bytes(p.mobile.total))
                }
                TilesGrid(listOf(
                    Tile("Wi-Fi reçu", Fmt.bytes(p.wifi.rx), Icons.Rounded.Download),
                    Tile("Wi-Fi envoyé", Fmt.bytes(p.wifi.tx), Icons.Rounded.CallMade),
                    Tile("Mobile reçu", Fmt.bytes(p.mobile.rx), Icons.Rounded.Download),
                    Tile("Mobile envoyé", Fmt.bytes(p.mobile.tx), Icons.Rounded.CallMade)
                ), cat.color)
                SectionTitle("7 derniers jours")
                Panel {
                    BarChart(r.days.map { Bar(it.label, listOf(it.wifi.toFloat(), it.mobile.toFloat())) }, listOf(cat.color, Color(0xFFFF8A3D)))
                }
                if (p.apps.isNotEmpty()) {
                    SectionTitle("Applications les plus gourmandes")
                    Panel {
                        val mx = p.apps.first().total.toFloat().coerceAtLeast(1f)
                        p.apps.forEachIndexed { i, a ->
                            RankRow(i + 1, a.label, "↓ ${Fmt.bytes(a.rx)}  ↑ ${Fmt.bytes(a.tx)}", Fmt.bytes(a.total), a.total / mx, cat.color)
                        }
                    }
                }
            }
        }
    }
}

// =============== STOCKAGE ===============
@Composable
fun StorageScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cat = Cat.Storage
    var tick by remember { mutableIntStateOf(0) }
    val resume = rememberResumeTick()
    DetailScaffold(cat, onBack, { tick++ }) {
        val st by loadState(tick + resume * 1000) { Collectors.storage(ctx, true) }
        Loaded(st) { r ->
            Hero(cat.color, r.usedFrac, "${Fmt.bytes(r.used)} utilisés", "Il reste ${Fmt.bytes(r.free)} sur ${Fmt.bytes(r.total)}.") {
                BigValue(Fmt.pct(r.usedFrac), "occupé")
            }
            Panel {
                val appsOnly = r.apps + r.data + r.cache
                val other = (r.used - appsOnly).coerceAtLeast(0L)
                val t = r.total.toFloat().coerceAtLeast(1f)
                val c2 = Color(0xFFFF8A3D); val c3 = Color(0xFFFF5CA8); val c4 = Color(0xFF8B7CFF)
                if (r.perAppAccess) {
                    SegmentBar(listOf(r.apps / t to cat.color, r.data / t to c2, r.cache / t to c3, other / t to c4, r.free / t to Color.Transparent))
                    Spacer(Modifier.height(12.dp))
                    Legend(cat.color, "Applications (code)", Fmt.bytes(r.apps))
                    Legend(c2, "Données des applications", Fmt.bytes(r.data))
                    Legend(c3, "Cache", Fmt.bytes(r.cache))
                    Legend(c4, "Photos, vidéos, système…", Fmt.bytes(other))
                } else {
                    SegmentBar(listOf(r.used / t to cat.color, r.free / t to Color.Transparent))
                    Spacer(Modifier.height(12.dp))
                    Legend(cat.color, "Utilisé", Fmt.bytes(r.used))
                }
                Legend(MaterialTheme.colorScheme.surfaceVariant, "Libre", Fmt.bytes(r.free))
            }
            if (!r.perAppAccess) {
                UsageAccessBanner(cat, resume, "Autorisez MobileScope dans « Accès aux données d'utilisation » pour détailler l'espace pris par chaque application.")
            } else if (r.top.isNotEmpty()) {
                SectionTitle("Applications les plus lourdes")
                Panel {
                    val mx = r.top.first().bytes.toFloat().coerceAtLeast(1f)
                    r.top.forEachIndexed { i, a -> RankRow(i + 1, a.label, "", Fmt.bytes(a.bytes), a.bytes / mx, cat.color) }
                }
            }
        }
    }
}

// =============== RAM (live) ===============
@Composable
fun RamScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cat = Cat.Ram
    var ram by remember { mutableStateOf<com.medsel.mobilescope.data.RamInfo?>(null) }
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            val r = withContext(Dispatchers.IO) { runCatching { Collectors.ram(ctx) }.getOrNull() }
            if (r != null) {
                ram = r
                history.add(r.usedFrac * 100f)
                if (history.size > 40) history.removeAt(0)
            }
            delay(2000)
        }
    }
    DetailScaffold(cat, onBack, null) {
        val r = ram
        if (r == null) { Loaded<Unit>(null) { } } else {
            Hero(cat.color, r.usedFrac, "${Fmt.bytes(r.used)} utilisés",
                if (r.low) "Mémoire basse : le système libère des applications." else "${Fmt.bytes(r.avail)} disponibles sur ${Fmt.bytes(r.total)}.") {
                BigValue(Fmt.pct(r.usedFrac), "en direct")
            }
            Panel {
                Text("Occupation, 80 dernières secondes", fontSize = 13.sp, color = muted)
                Spacer(Modifier.height(8.dp))
                Sparkline(history.toList(), cat.color, Modifier.fillMaxWidth().height(90.dp))
            }
            TilesGrid(listOf(
                Tile("Totale", Fmt.bytes(r.total), Icons.Rounded.Memory),
                Tile("Disponible", Fmt.bytes(r.avail), Icons.Rounded.Check),
                Tile("Seuil critique", Fmt.bytes(r.threshold), Icons.Rounded.Speed),
                Tile("En cache", Fmt.bytes(r.cached)),
                Tile("Swap utilisé", Fmt.bytes((r.swapTotal - r.swapFree).coerceAtLeast(0L))),
                Tile("Swap total", Fmt.bytes(r.swapTotal))
            ), cat.color)
        }
    }
}

// =============== BATTERIE (live) ===============
@Composable
fun BatteryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cat = Cat.Battery
    var b by remember { mutableStateOf<com.medsel.mobilescope.data.BatteryInfo?>(null) }
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            val r = withContext(Dispatchers.IO) { runCatching { Collectors.battery(ctx) }.getOrNull() }
            if (r != null) {
                b = r
                history.add(r.tempC)
                if (history.size > 40) history.removeAt(0)
            }
            delay(3000)
        }
    }
    DetailScaffold(cat, onBack, null) {
        val r = b
        if (r == null) { Loaded<Unit>(null) { } } else {
            Hero(cat.color, r.percent / 100f, r.status,
                if (r.charging) "Branchée : ${r.plug}." else "Sur batterie, santé ${r.health.lowercase()}.") {
                BigValue("${r.percent}%", if (r.charging) "en charge" else "restant")
            }
            Panel {
                Text("Température en direct", fontSize = 13.sp, color = muted)
                Spacer(Modifier.height(8.dp))
                Sparkline(history.toList(), cat.color, Modifier.fillMaxWidth().height(80.dp))
            }
            TilesGrid(listOf(
                Tile("Température", "${"%.1f".format(Locale.getDefault(), r.tempC)} °C", Icons.Rounded.Thermostat),
                Tile("Tension", "${r.voltageMv} mV", Icons.Rounded.Bolt),
                Tile("Courant", r.currentMa?.let { "$it mA" } ?: "—", Icons.Rounded.Speed),
                Tile("Santé", r.health, Icons.Rounded.Check),
                Tile("Capacité estimée", r.capacityMah?.let { "$it mAh" } ?: "—"),
                Tile("Cycles de charge", r.cycles?.toString() ?: "Non fourni"),
                Tile("Technologie", r.tech),
                Tile("Alimentation", r.plug)
            ), cat.color, 18.sp)
        }
    }
}

// =============== APPLICATIONS ===============
@Composable
fun AppsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cat = Cat.Apps
    var tick by remember { mutableIntStateOf(0) }
    val resume = rememberResumeTick()
    DetailScaffold(cat, onBack, { tick++ }) {
        val st by loadState(tick + resume * 1000) { Collectors.apps(ctx) }
        Loaded(st) { r ->
            val frac = if (r.total > 0) r.user.toFloat() / r.total else 0f
            Hero(cat.color, frac, "${r.user} applications installées par vous", "${r.system} applications système.") {
                BigValue("${r.total}", "au total")
            }
            if (!r.hasAccess) {
                UsageAccessBanner(cat, resume, "Autorisez MobileScope dans « Accès aux données d'utilisation » pour mesurer le temps passé sur chaque application.")
            } else {
                Panel {
                    Text(Fmt.duration(r.screenMs / 1000), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("de temps d'utilisation sur 7 jours", fontSize = 12.sp, color = muted)
                }
                if (r.top.isNotEmpty()) {
                    SectionTitle("Les plus utilisées")
                    Panel {
                        val mx = r.top.first().fgMs.toFloat().coerceAtLeast(1f)
                        r.top.forEachIndexed { i, a ->
                            RankRow(i + 1, a.label, "Dernier usage : ${day(a.lastUsed)}", Fmt.duration(a.fgMs / 1000), a.fgMs / mx, cat.color)
                        }
                    }
                }
            }
            if (r.newest.isNotEmpty()) {
                SectionTitle("Installées récemment")
                Panel {
                    r.newest.forEach { a ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Version ${a.version}", fontSize = 12.sp, color = muted)
                            }
                            Text(day(a.installedAt), fontSize = 12.sp, color = cat.color, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
