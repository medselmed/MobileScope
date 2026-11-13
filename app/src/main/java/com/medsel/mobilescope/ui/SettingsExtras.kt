package com.medsel.mobilescope.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medsel.mobilescope.BuildConfig
import com.medsel.mobilescope.data.Background
import com.medsel.mobilescope.data.Fmt
import com.medsel.mobilescope.data.Prefs
import com.medsel.mobilescope.data.UpdateInfo
import com.medsel.mobilescope.data.Updater
import kotlinx.coroutines.launch

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(title: String, valueText: String, value: Int, range: IntRange, onChange: (Int) -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(title, modifier = Modifier.weight(1f), fontSize = 13.sp)
            Text(valueText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            onValueChangeFinished = onDone,
            valueRange = range.first.toFloat()..range.last.toFloat()
        )
    }
}

/** Préférences avancées : langue, données en direct, unités, accueil, historique, alertes, mises à jour. */
@Composable
fun AdvancedSettings(onLanguageChanged: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary

    var lang by remember { mutableStateOf(Prefs.lang(ctx)) }
    var refresh by remember { mutableIntStateOf(Prefs.refreshMs(ctx)) }
    var decimal by remember { mutableStateOf(Prefs.decimalUnits(ctx)) }
    var health by remember { mutableStateOf(Prefs.showHealthCard(ctx)) }
    var quick by remember { mutableStateOf(Prefs.showQuickControls(ctx)) }
    var history by remember { mutableStateOf(Prefs.historyEnabled(ctx)) }
    var alerts by remember { mutableStateOf(Prefs.alertsEnabled(ctx)) }
    var temp by remember { mutableIntStateOf(Prefs.alertTemp(ctx)) }
    var storage by remember { mutableIntStateOf(Prefs.alertStorage(ctx)) }
    var battery by remember { mutableIntStateOf(Prefs.alertBattery(ctx)) }
    var autoUpd by remember { mutableStateOf(Prefs.autoCheckUpdates(ctx)) }
    var confirmReset by remember { mutableStateOf(false) }

    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var checking by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        alerts = granted
        Prefs.saveAlertsEnabled(ctx, granted)
        if (!granted) message = "Autorisez les notifications pour recevoir les alertes."
    }

    fun check(manual: Boolean) {
        scope.launch {
            checking = true
            val r = runCatching { Updater.check() }
            checking = false
            update = r.getOrNull()
            if (manual) message = when {
                r.isFailure -> "Vérification impossible. Contrôlez la connexion."
                update == null -> "Vous avez déjà la dernière version."
                else -> null
            }
        }
    }
    LaunchedEffect(Unit) { if (Prefs.autoCheckUpdates(ctx)) check(false) }

    SectionTitle("LANGUE")
    Panel {
        Text("Langue de l'application", fontWeight = FontWeight.SemiBold)
        Text("Français par défaut. L'anglais est appliqué immédiatement.", fontSize = 12.sp, color = muted)
        Spacer(Modifier.height(10.dp))
        Segmented(listOf("Français", "English"), if (lang == "en") 1 else 0, primary) {
            lang = if (it == 1) "en" else "fr"
            Prefs.saveLang(ctx, lang)
            onLanguageChanged(lang)
        }
    }

    SectionTitle("DONNÉES ET UNITÉS")
    Panel {
        Text("Fréquence des mesures en direct", fontWeight = FontWeight.SemiBold)
        Text("RAM et batterie : plus rapide = plus précis, mais plus de consommation.", fontSize = 12.sp, color = muted)
        Spacer(Modifier.height(10.dp))
        Segmented(listOf("1 s", "2 s", "5 s"), when (refresh) { 1000 -> 0; 5000 -> 2; else -> 1 }, primary) {
            refresh = listOf(1000, 2000, 5000)[it]
            Prefs.saveRefreshMs(ctx, refresh)
        }
        Spacer(Modifier.height(14.dp))
        Text("Unités de taille", fontWeight = FontWeight.SemiBold)
        Text("Binaire : 1 Go = 1024 Mo (Android). Décimal : 1 Go = 1000 Mo (fabricants).", fontSize = 12.sp, color = muted)
        Spacer(Modifier.height(10.dp))
        Segmented(listOf("Binaire (1024)", "Décimal (1000)"), if (decimal) 1 else 0, primary) {
            decimal = it == 1
            Prefs.saveDecimalUnits(ctx, decimal)
            Fmt.decimal = decimal
        }
    }

    SectionTitle("ACCUEIL")
    Panel {
        SwitchRow("Carte Santé", "Affiche le score de l'appareil en haut de l'accueil.", health) {
            health = it; Prefs.saveShowHealthCard(ctx, it)
        }
        HorizontalDivider()
        SwitchRow("Commandes rapides", "Raccourcis Wi-Fi, Bluetooth, son et luminosité.", quick) {
            quick = it; Prefs.saveShowQuickControls(ctx, it)
        }
    }

    SectionTitle("HISTORIQUE ET ALERTES")
    Panel {
        SwitchRow("Historique en arrière-plan", "Un relevé toutes les 30 minutes pour tracer les courbes.", history) {
            history = it
            Prefs.saveHistoryEnabled(ctx, it)
            Background.sync(ctx)
        }
        HorizontalDivider()
        SwitchRow("Alertes", "Notification si la batterie chauffe, le stockage se remplit ou la charge est faible.", alerts) { on ->
            if (!on) {
                alerts = false; Prefs.saveAlertsEnabled(ctx, false)
            } else if (Build.VERSION.SDK_INT >= 33) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                alerts = true; Prefs.saveAlertsEnabled(ctx, true)
            }
        }
        if (alerts) {
            Spacer(Modifier.height(6.dp))
            SliderRow("Température maximale", "$temp °C", temp, 35..50, { temp = it }, { Prefs.saveAlertTemp(ctx, temp) })
            SliderRow("Stockage utilisé au-delà de", "$storage %", storage, 70..98, { storage = it }, { Prefs.saveAlertStorage(ctx, storage) })
            SliderRow("Batterie en dessous de", "$battery %", battery, 5..40, { battery = it }, { Prefs.saveAlertBattery(ctx, battery) })
            Text("Les alertes sont vérifiées lors des relevés en arrière-plan.", fontSize = 11.sp, color = muted)
        }
    }

    SectionTitle("MISES À JOUR")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = primary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("MobileScope ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
                val u = update
                Text(
                    message ?: if (u != null) "Version ${u.version} disponible (${Fmt.bytes(u.size)})" else "Mises à jour via les publications GitHub",
                    fontSize = 12.sp, color = muted
                )
            }
            if (checking) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
            else OutlinedButton(onClick = { check(true) }) { Text("Vérifier") }
        }
        val u = update
        if (u != null) {
            Spacer(Modifier.height(12.dp))
            val p = progress
            if (p != null) {
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(6.dp))
            } else {
                Button(onClick = {
                    scope.launch {
                        progress = 0f
                        val f = runCatching { Updater.download(ctx, u) { progress = it } }
                        progress = null
                        f.onSuccess {
                            if (!Updater.install(ctx, it)) message = "Autorisez l'installation depuis MobileScope, puis relancez la mise à jour."
                        }.onFailure { message = "Échec du téléchargement." }
                    }
                }) { Text("Télécharger et installer") }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        SwitchRow("Vérifier au lancement", "Recherche automatiquement une nouvelle version.", autoUpd) {
            autoUpd = it; Prefs.saveAutoCheckUpdates(ctx, it)
        }
    }

    SectionTitle("PRÉFÉRENCES")
    OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Réinitialiser les préférences")
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Réinitialiser les préférences ?") },
            text = { Text("Thème, langue, unités et options reviennent aux valeurs d'origine. Votre compte Google et l'historique ne sont pas touchés.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    Prefs.resetSettings(ctx)
                    Background.sync(ctx)
                    (ctx as? Activity)?.recreate()
                }) { Text("Réinitialiser") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Annuler") } }
        )
    }
}
