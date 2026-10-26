package com.medsel.mobilescope.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.medsel.mobilescope.data.CallEntry
import com.medsel.mobilescope.data.Collectors
import com.medsel.mobilescope.data.Fmt
import com.medsel.mobilescope.data.Prefs
import com.medsel.mobilescope.data.TopContact
import com.medsel.mobilescope.data.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import java.util.TimeZone

private fun day(t: Long) = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(t))


private fun localDateToPickerMillis(timestamp: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = timestamp }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
    }.timeInMillis
}

private fun pickerMillisToLocalDay(timestamp: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = timestamp }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

@Composable
private fun DateRangeSelector(
    startMs: Long,
    endMs: Long,
    color: Color,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit
) {
    var showStart by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    Panel {
        Text("Période personnalisée", fontWeight = FontWeight.SemiBold)
        Text("Choisissez les dates à analyser.", fontSize = 12.sp, color = muted)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showStart = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.CalendarMonth, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Column {
                    Text("Début", fontSize = 10.sp, color = muted)
                    Text(day(startMs), fontSize = 12.sp, color = color)
                }
            }
            OutlinedButton(onClick = { showEnd = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Event, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Column {
                    Text("Fin", fontSize = 10.sp, color = muted)
                    Text(day(endMs), fontSize = 12.sp, color = color)
                }
            }
        }
        TextButton(onClick = {
            onStartChange(Collectors.midnight(6))
            onEndChange(System.currentTimeMillis())
        }, modifier = Modifier.align(Alignment.End)) { Text("7 derniers jours") }
    }
    if (showStart) {
        val state = rememberDatePickerState(initialSelectedDateMillis = localDateToPickerMillis(startMs))
        DatePickerDialog(
            onDismissRequest = { showStart = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selected ->
                        val date = pickerMillisToLocalDay(selected)
                        onStartChange(date)
                        if (date > endMs) onEndChange(date)
                    }
                    showStart = false
                }) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { showStart = false }) { Text("Annuler") } }
        ) { DatePicker(state = state, title = { Text("Date de début") }, showModeToggle = true) }
    }
    if (showEnd) {
        val state = rememberDatePickerState(initialSelectedDateMillis = localDateToPickerMillis(endMs))
        DatePickerDialog(
            onDismissRequest = { showEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selected ->
                        val date = pickerMillisToLocalDay(selected)
                        onEndChange(date)
                        if (date < startMs) onStartChange(date)
                    }
                    showEnd = false
                }) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { showEnd = false }) { Text("Annuler") } }
        ) { DatePicker(state = state, title = { Text("Date de fin") }, showModeToggle = true) }
    }
}

// =============== ACCUEIL ===============
@Composable
fun HomeScreen(
    user: User,
    onOpen: (Cat) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDevice: () -> Unit,
    onSignOut: () -> Unit
) {
    val ctx = LocalContext.current
    val compactCards = Prefs.compactCards(ctx)
    var searchQuery by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var favoriteModules by remember { mutableStateOf(Prefs.favoriteModules(ctx)) }
    val visibleCats = Cat.values().toList().filter { cat ->
        (searchQuery.isBlank() || cat.title.contains(searchQuery, ignoreCase = true) || cat.sub.contains(searchQuery, ignoreCase = true)) &&
            (!favoritesOnly || cat.name in favoriteModules)
    }
    val deviceState by loadState(0) { Collectors.device(ctx) }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("MobileScope", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text("Tableau de bord intelligent", fontSize = 12.sp, color = muted)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Tune, "Préférences", tint = muted)
            }
            IconButton(onClick = onSignOut) {
                Icon(Icons.Rounded.Logout, "Se déconnecter", tint = muted)
            }
        }

        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!user.photo.isNullOrBlank()) {
                        AsyncImage(
                            model = user.photo,
                            contentDescription = "Photo de profil Google",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(66.dp).clip(CircleShape)
                        )
                    } else {
                        Box(
                            Modifier.size(66.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = .14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.AccountCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (user.guest) "Mode invité" else "Bonjour,", fontSize = 12.sp, color = muted)
                        Text(user.name.ifBlank { "Utilisateur MobileScope" }, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!user.guest && user.email.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(user.email, fontSize = 12.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = onOpenDevice, label = { Text("Mon appareil") }, leadingIcon = {
                        Icon(Icons.Rounded.Smartphone, null, modifier = Modifier.size(17.dp))
                    })
                    AssistChip(onClick = onOpenSettings, label = { Text("Personnaliser") }, leadingIcon = {
                        Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(17.dp))
                    })
                }
            }

            SectionTitle("APERÇU DE L'APPAREIL")
            Loaded(deviceState) { device ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                                .background(Color(0xFF14B8D4).copy(alpha = .15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.PhoneAndroid, null, tint = Color(0xFF14B8D4), modifier = Modifier.size(27.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(device.model, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(device.android, fontSize = 12.sp, color = muted)
                        }
                        IconButton(onClick = onOpenDevice) {
                            Icon(Icons.Rounded.ChevronRight, "Détails de l'appareil", tint = muted)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Legend(MaterialTheme.colorScheme.primary, "Processeur", "${device.cores} cœurs")
                    Legend(MaterialTheme.colorScheme.primary, "Écran", device.screen)
                    Legend(MaterialTheme.colorScheme.primary, "Correctif de sécurité", device.patch)
                    TextButton(onClick = onOpenDevice, modifier = Modifier.align(Alignment.End)) {
                        Text("Tous les détails")
                        Icon(Icons.Rounded.ArrowForward, null, modifier = Modifier.padding(start = 6.dp).size(16.dp))
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Centre de diagnostic", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Ouvrez une carte pour explorer ses données.", fontSize = 12.sp, color = muted)
                }
                Text("${Cat.values().size} modules", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, "Effacer la recherche")
                        }
                    }
                },
                placeholder = { Text("Rechercher un module…") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = !favoritesOnly,
                    onClick = { favoritesOnly = false },
                    label = { Text("Tous les modules") },
                    leadingIcon = { Icon(Icons.Rounded.GridView, null, modifier = Modifier.size(17.dp)) }
                )
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { favoritesOnly = true },
                    label = { Text("Favoris (${favoriteModules.size})") },
                    leadingIcon = { Icon(Icons.Rounded.Star, null, modifier = Modifier.size(17.dp)) }
                )
            }
            if (visibleCats.isEmpty()) {
                Panel {
                    Icon(Icons.Rounded.SearchOff, null, tint = muted, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(if (favoritesOnly) "Aucun module favori" else "Aucun module trouvé", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (favoritesOnly) "Ajoutez des cartes aux favoris depuis leur menu ⋮." else "Essayez un autre terme de recherche.",
                        color = muted, fontSize = 12.sp
                    )
                }
            }
            visibleCats.chunked(2).forEach { rowCats ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowCats.forEach { cat ->
                        var menuExpanded by remember(cat) { mutableStateOf(false) }
                        var infoOpen by remember(cat) { mutableStateOf(false) }
                        Column(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                                .clickable { onOpen(cat) }
                                .padding(if (compactCards) 10.dp else 14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    Modifier.size(43.dp).clip(RoundedCornerShape(14.dp))
                                        .background(cat.color.copy(alpha = .15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(cat.icon, null, tint = cat.color, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.weight(1f))
                                Box {
                                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Rounded.MoreVert, "Options de ${cat.title}", tint = muted, modifier = Modifier.size(19.dp))
                                    }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                        DropdownMenuItem(
                                            text = { Text(if (cat.name in favoriteModules) "Retirer des favoris" else "Ajouter aux favoris") },
                                            leadingIcon = { Icon(if (cat.name in favoriteModules) Icons.Rounded.Star else Icons.Rounded.StarBorder, null) },
                                            onClick = {
                                                menuExpanded = false
                                                Prefs.toggleFavoriteModule(ctx, cat.name)
                                                favoriteModules = Prefs.favoriteModules(ctx)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Ouvrir le module") },
                                            leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) },
                                            onClick = { menuExpanded = false; onOpen(cat) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("À propos de cette carte") },
                                            leadingIcon = { Icon(Icons.Rounded.Info, null) },
                                            onClick = { menuExpanded = false; infoOpen = true }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Réglages associés") },
                                            leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                                            onClick = { menuExpanded = false; openModuleSettings(ctx, cat) }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(cat.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(cat.sub, fontSize = 11.sp, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Explorer", fontSize = 12.sp, color = cat.color, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Rounded.ArrowForward, null, tint = cat.color, modifier = Modifier.size(15.dp))
                            }
                        }
                        if (infoOpen) {
                            AlertDialog(
                                onDismissRequest = { infoOpen = false },
                                icon = { Icon(cat.icon, null, tint = cat.color) },
                                title = { Text(cat.title) },
                                text = {
                                    Text(
                                        when (cat) {
                                            Cat.Ram -> "Suivez l'occupation de la mémoire en direct, la mémoire disponible, le cache et le swap avec un graphique d'évolution."
                                            Cat.Battery -> "Consultez le niveau, la température, la tension, l'état de santé, le courant et les données de charge disponibles."
                                            Cat.Network -> "Analysez la connexion, les transferts Wi-Fi et mobiles, les périodes et les applications consommatrices."
                                            Cat.Storage -> "Explorez l'espace utilisé, l'espace libre et les applications qui occupent le plus de stockage."
                                            Cat.Contacts -> "Comptez les contacts, repérez les doublons et consultez les favoris. L'accès au carnet est demandé uniquement si nécessaire."
                                            Cat.Calls -> "Analysez les appels sur 30 jours, les durées, les appels manqués et les correspondants les plus fréquents."
                                            Cat.Sms -> "Consultez les volumes de SMS envoyés et reçus et leur évolution. Le contenu des messages n'est pas affiché."
                                            Cat.Apps -> "Examinez les applications installées, les usages disponibles et les installations récentes."
                                        }
                                    )
                                },
                                confirmButton = { TextButton(onClick = { infoOpen = false; onOpen(cat) }) { Text("Explorer") } },
                                dismissButton = { TextButton(onClick = { infoOpen = false }) { Text("Fermer") } }
                            )
                        }
                    }
                    if (rowCats.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Insights, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Données et graphiques", fontWeight = FontWeight.SemiBold)
                        Text("Chaque module propose ses indicateurs, ses détails et ses visualisations lorsque les données sont disponibles.", fontSize = 12.sp, color = muted)
                    }
                }
            }
        }
    }
}


private fun openModuleSettings(ctx: android.content.Context, cat: Cat) {
    val intent = when (cat) {
        Cat.Network -> Intent(Settings.ACTION_WIFI_SETTINGS)
        Cat.Battery -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        Cat.Apps, Cat.Ram -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
        Cat.Storage -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        Cat.Contacts, Cat.Calls, Cat.Sms -> Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${ctx.packageName}")
        )
    }
    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// =============== CONTACTS ===============
@Composable
fun ContactsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cat = Cat.Contacts
    var tick by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Tous") }
    var limit by remember { mutableIntStateOf(100) }
    DetailScaffold(cat, onBack, { tick++ }) {
        PermissionGate(cat, listOf(Manifest.permission.READ_CONTACTS),
            "MobileScope lit votre carnet pour afficher les fiches, les numéros, les e-mails, les favoris et les cas particuliers. Les données restent sur l'appareil.") {
            val st by loadState(tick) { Collectors.contacts(ctx) }
            Loaded(st) { r ->
                val frac = if (r.total > 0) r.withPhone.toFloat() / r.total else 0f
                Hero(cat.color, frac, "${Fmt.pct(frac)} ont un numéro",
                    "${r.withEmail} avec e-mail · ${r.starred} favoris · ${r.dupNumbers} groupes de numéros en doublon.") {
                    BigValue("${r.total}", "contacts")
                }
                TilesGrid(listOf(
                    Tile("Avec numéro", "${r.withPhone}", Icons.Rounded.Call),
                    Tile("Sans numéro", "${r.total - r.withPhone}", Icons.Rounded.Person),
                    Tile("Avec e-mail", "${r.withEmail}", Icons.Rounded.Email),
                    Tile("Favoris", "${r.starred}", Icons.Rounded.Star),
                    Tile("Numéros en doublon", "${r.dupNumbers}", Icons.Rounded.ContentCopy)
                ), cat.color)

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; limit = 100 },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotBlank()) IconButton(onClick = { query = ""; limit = 100 }) {
                            Icon(Icons.Rounded.Close, "Effacer")
                        }
                    },
                    placeholder = { Text("Rechercher un nom ou un numéro…") }
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Tous", "Favoris", "Sans numéro", "Avec e-mail", "Doublons").forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option; limit = 100 },
                            label = { Text(option) }
                        )
                    }
                }
                val filtered = r.contacts.filter { contact ->
                    val matchesQuery = query.isBlank() ||
                        contact.name.contains(query, ignoreCase = true) ||
                        (contact.number?.contains(query, ignoreCase = true) == true)
                    val matchesFilter = when (filter) {
                        "Favoris" -> contact.starred
                        "Sans numéro" -> contact.number.isNullOrBlank()
                        "Avec e-mail" -> contact.hasEmail
                        "Doublons" -> contact.duplicateNumber
                        else -> true
                    }
                    matchesQuery && matchesFilter
                }
                SectionTitle("Contacts (${filtered.size})")
                if (filtered.isEmpty()) {
                    Panel {
                        Icon(Icons.Rounded.Search, null, tint = muted, modifier = Modifier.size(30.dp))
                        Text("Aucun contact correspondant", fontWeight = FontWeight.SemiBold)
                        Text("Changez le filtre ou la recherche.", fontSize = 12.sp, color = muted)
                    }
                } else {
                    Panel {
                        filtered.take(limit).forEachIndexed { index, contact ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .25f))
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    runCatching {
                                        ctx.startActivity(Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contact.id.toString())
                                        ))
                                    }
                                }.padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(42.dp).clip(CircleShape)
                                        .background(cat.color.copy(alpha = .15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(contact.name.firstOrNull()?.uppercase() ?: "?", color = cat.color, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(contact.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        contact.number ?: "Aucun numéro enregistré",
                                        fontSize = 12.sp,
                                        color = if (contact.number == null) MaterialTheme.colorScheme.error else muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val tags = buildList {
                                        if (contact.starred) add("Favori")
                                        if (contact.hasEmail) add("E-mail")
                                        if (contact.duplicateNumber) add("Numéro en doublon")
                                        if (contact.number == null) add("Cas à compléter")
                                    }
                                    if (tags.isNotEmpty()) Text(tags.joinToString(" · "), fontSize = 10.sp, color = cat.color, maxLines = 1)
                                }
                                if (!contact.number.isNullOrBlank()) QuickActions(contact.number, cat.color)
                                else Icon(Icons.Rounded.ChevronRight, "Ouvrir la fiche", tint = muted)
                            }
                        }
                    }
                    if (filtered.size > limit) {
                        TextButton(onClick = { limit += 100 }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Afficher davantage (${limit}/${filtered.size})")
                        }
                    }
                }
                Text(
                    "Touchez une ligne pour ouvrir la fiche native du contact. Les contacts sans numéro restent accessibles pour être complétés.",
                    fontSize = 12.sp, color = muted
                )
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
    var startDate by remember { mutableLongStateOf(Collectors.midnight(6)) }
    var endDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedCallList by remember { mutableStateOf<String?>(null) }
    DetailScaffold(cat, onBack, { tick++ }) {
        PermissionGate(cat, listOf(Manifest.permission.READ_CALL_LOG),
            "MobileScope lit le journal d'appels uniquement pendant la période sélectionnée.") {
            DateRangeSelector(startDate, endDate, cat.color,
                onStartChange = { startDate = it; tick++ },
                onEndChange = { endDate = it; tick++ })
            val st by loadState(tick) { Collectors.calls(ctx, startDate, endDate) }
            Loaded(st) { r ->
                val answered = r.incoming + r.outgoing
                val frac = if (r.total > 0) answered.toFloat() / r.total else 0f
                val talk = r.inSec + r.outSec
                Hero(cat.color, frac, "${Fmt.duration(talk)} de conversation",
                    if (answered > 0) "Durée moyenne ${Fmt.duration(talk / answered)} par appel abouti." else "Aucun appel abouti pendant cette période.") {
                    BigValue("${r.total}", "appels")
                }
                TilesGrid(listOf(
                    Tile("Entrants", "${r.incoming}", Icons.Rounded.CallReceived),
                    Tile("Sortants", "${r.outgoing}", Icons.Rounded.CallMade),
                    Tile("Temps en réception", Fmt.duration(r.inSec), Icons.Rounded.Schedule),
                    Tile("Plus long appel", Fmt.duration(r.longest), Icons.Rounded.Timer)
                ), cat.color)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Panel(Modifier.weight(1f).clickable { selectedCallList = if (selectedCallList == "missed") null else "missed" }) {
                        Icon(Icons.Rounded.CallMissed, null, tint = cat.color)
                        Text("${r.missed}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Appels manqués", fontSize = 12.sp)
                        Text("Toucher pour afficher", fontSize = 10.sp, color = muted)
                    }
                    Panel(Modifier.weight(1f).clickable { selectedCallList = if (selectedCallList == "rejected") null else "rejected" }) {
                        Icon(Icons.Rounded.Block, null, tint = cat.color)
                        Text("${r.rejected}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Appels refusés", fontSize = 12.sp)
                        Text("Toucher pour afficher", fontSize = 10.sp, color = muted)
                    }
                }
                val entries = when (selectedCallList) {
                    "missed" -> r.missedCalls
                    "rejected" -> r.rejectedCalls
                    else -> emptyList()
                }
                if (selectedCallList != null) {
                    SectionTitle(if (selectedCallList == "missed") "Liste des appels manqués" else "Liste des appels refusés")
                    if (entries.isEmpty()) {
                        Panel { Text("Aucun appel dans cette catégorie pour la période choisie.", color = muted) }
                    } else Panel {
                        entries.forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .3f))
                            CallEntryRow(entry, cat.color)
                        }
                        if (entries.size == 300) Text("Affichage limité aux 300 entrées les plus récentes.", fontSize = 11.sp, color = muted)
                    }
                }
                SectionTitle("Volume d'appels par jour")
                Panel {
                    BarChart(r.perDay.mapIndexed { i, v -> Bar(r.dayLabels.getOrElse(i) { "J${i + 1}" }, listOf(v.toFloat())) }, listOf(cat.color))
                    Text("Période : ${r.dayLabels.firstOrNull() ?: "—"} – ${r.dayLabels.lastOrNull() ?: "—"} · ${r.total} appels.", fontSize = 11.sp, color = muted)
                }
                if (r.top.isNotEmpty()) {
                    SectionTitle("Contacts les plus fréquents")
                    TopList(r.top, cat.color) { Fmt.duration(it.seconds) + " · " + it.number }
                }
            }
        }
    }
}

@Composable
private fun CallEntryRow(entry: CallEntry, color: Color) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.number.ifBlank { "Numéro masqué" }, fontSize = 12.sp, color = muted)
            Text("${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(entry.date))} · ${Fmt.duration(entry.durationSec)}",
                fontSize = 11.sp, color = muted)
        }
        if (entry.number.isNotBlank()) QuickActions(entry.number, color)
        IconButton(onClick = {
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("content://call_log/calls")))
            }
        }) { Icon(Icons.Rounded.History, "Ouvrir le journal d'appels", tint = color) }
    }
}

// =============== SMS ===============
@Composable
fun SmsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cat = Cat.Sms
    var tick by remember { mutableIntStateOf(0) }
    var smsView by remember { mutableIntStateOf(0) }
    var startDate by remember { mutableLongStateOf(Collectors.midnight(6)) }
    var endDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    DetailScaffold(cat, onBack, { tick++ }) {
        PermissionGate(cat, listOf(Manifest.permission.READ_SMS),
            "MobileScope compte les SMS reçus et envoyés pendant la période sélectionnée. Le contenu n'est jamais affiché ni transmis.") {
            DateRangeSelector(startDate, endDate, cat.color,
                onStartChange = { startDate = it; tick++ },
                onEndChange = { endDate = it; tick++ })
            val st by loadState(tick) { Collectors.sms(ctx, startDate, endDate) }
            Loaded(st) { r ->
                val total = r.received + r.sent
                val frac = if (total > 0) r.sent.toFloat() / total else 0f
                val periodDays = r.dayLabels.size.coerceAtLeast(1)
                Hero(cat.color, frac, "${r.sent} envoyés, ${r.received} reçus",
                    "Moyenne de ${"%.1f".format(Locale.getDefault(), total / periodDays.toFloat())} messages par jour.") {
                    BigValue("$total", "SMS")
                }
                TilesGrid(listOf(
                    Tile("Reçus", "${r.received}", Icons.Rounded.Download),
                    Tile("Envoyés", "${r.sent}", Icons.Rounded.Sms),
                    Tile("Caractères reçus", "${r.charsReceived}"),
                    Tile("Caractères envoyés", "${r.charsSent}")
                ), cat.color)
                SectionTitle("Activité quotidienne")
                Segmented(listOf("Reçus + envoyés", "Reçus", "Envoyés"), smsView, cat.color) { smsView = it }
                Panel {
                    val bars = r.recvPerDay.indices.map { i ->
                        val values = when (smsView) {
                            1 -> listOf(r.recvPerDay[i].toFloat())
                            2 -> listOf(r.sentPerDay[i].toFloat())
                            else -> listOf(r.recvPerDay[i].toFloat(), r.sentPerDay[i].toFloat())
                        }
                        Bar(r.dayLabels.getOrElse(i) { "J${i + 1}" }, values)
                    }
                    val colors = when (smsView) {
                        1 -> listOf(cat.color)
                        2 -> listOf(cat.color.copy(alpha = .48f))
                        else -> listOf(cat.color, cat.color.copy(alpha = .48f))
                    }
                    if (total == 0) {
                        Icon(Icons.Rounded.BarChart, null, tint = muted, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("Aucun SMS trouvé sur cette période.", fontWeight = FontWeight.SemiBold)
                        Text("Essayez une période plus large ou vérifiez l'autorisation SMS.", fontSize = 12.sp, color = muted)
                    } else {
                        BarChart(bars, colors)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (smsView != 2) Legend(cat.color, "Reçus", "${r.recvPerDay.sum()}")
                    if (smsView != 1) Legend(cat.color.copy(alpha = .48f), "Envoyés", "${r.sentPerDay.sum()}")
                    Text("Du ${r.dayLabels.firstOrNull() ?: "—"} au ${r.dayLabels.lastOrNull() ?: "—"} · ${r.dayLabels.size} jours.", fontSize = 11.sp, color = muted)
                }
                if (r.top.isNotEmpty()) {
                    SectionTitle("Correspondants les plus actifs")
                    TopList(r.top, cat.color) { "${it.count} SMS · ${it.number}" }
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
                val samples = history.toList()
                Text("Utilisation de la RAM · 80 dernières secondes", fontSize = 13.sp, color = muted)
                Spacer(Modifier.height(8.dp))
                Sparkline(samples, cat.color, Modifier.fillMaxWidth().height(100.dp), minValue = 0f, maxValue = 100f)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Min. ${samples.minOrNull()?.let { "%.1f".format(Locale.getDefault(), it) } ?: "—"} %", fontSize = 11.sp, color = muted)
                    Text("Actuel ${"%.1f".format(Locale.getDefault(), r.usedFrac * 100f)} %", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cat.color)
                    Text("Max. ${samples.maxOrNull()?.let { "%.1f".format(Locale.getDefault(), it) } ?: "—"} %", fontSize = 11.sp, color = muted)
                }
                Text("Échelle fixe : 0 à 100 % · un point toutes les 2 secondes.", fontSize = 11.sp, color = muted)
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
                val samples = history.toList()
                Text("Température de la batterie · 2 dernières minutes", fontSize = 13.sp, color = muted)
                Spacer(Modifier.height(8.dp))
                Sparkline(samples, cat.color, Modifier.fillMaxWidth().height(96.dp), minValue = 10f, maxValue = 60f)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Min. ${samples.minOrNull()?.let { "%.1f".format(Locale.getDefault(), it) } ?: "—"} °C", fontSize = 11.sp, color = muted)
                    Text("Actuelle ${"%.1f".format(Locale.getDefault(), r.tempC)} °C", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cat.color)
                    Text("Max. ${samples.maxOrNull()?.let { "%.1f".format(Locale.getDefault(), it) } ?: "—"} °C", fontSize = 11.sp, color = muted)
                }
                Text("Échelle fixe : 10 à 60 °C · un point toutes les 3 secondes.", fontSize = 11.sp, color = muted)
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
