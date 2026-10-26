package com.medsel.mobilescope.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medsel.mobilescope.data.Collectors
import com.medsel.mobilescope.data.Fmt
import com.medsel.mobilescope.data.Prefs

@Composable
fun SettingsScreen(onBack: () -> Unit, onThemeChanged: (String) -> Unit) {
    val ctx = LocalContext.current
    var theme by remember { mutableStateOf(Prefs.theme(ctx)) }
    var compact by remember { mutableStateOf(Prefs.compactCards(ctx)) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Retour") }
            Column(Modifier.weight(1f)) {
                Text("Préférences", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Personnalisez MobileScope", fontSize = 12.sp, color = muted)
            }
            Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp).size(25.dp))
        }

        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionTitle("APPARENCE")
            Panel {
                Text("Thème de l'application", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Choisissez un affichage clair, sombre ou celui du système.", fontSize = 12.sp, color = muted)
                Spacer(Modifier.height(8.dp))
                listOf("system" to "Automatique (système)", "dark" to "Sombre", "light" to "Clair").forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable {
                                theme = value
                                Prefs.saveTheme(ctx, value)
                                onThemeChanged(value)
                            }.padding(vertical = 7.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = theme == value, onClick = {
                            theme = value
                            Prefs.saveTheme(ctx, value)
                            onThemeChanged(value)
                        })
                        Spacer(Modifier.width(8.dp))
                        Text(label, modifier = Modifier.weight(1f))
                    }
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Cartes compactes", fontWeight = FontWeight.SemiBold)
                        Text("Réduit l'espacement du tableau de bord.", fontSize = 12.sp, color = muted)
                    }
                    Switch(checked = compact, onCheckedChange = {
                        compact = it
                        Prefs.saveCompactCards(ctx, it)
                    })
                }
            }

            SectionTitle("RÉGLAGES DU TÉLÉPHONE")
            SettingsAction(
                icon = Icons.Rounded.PhonelinkSetup,
                title = "Informations et réglages système",
                subtitle = "Ouvrir les paramètres Android",
                onClick = {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            )
            SettingsAction(
                icon = Icons.Rounded.DisplaySettings,
                title = "Affichage",
                subtitle = "Luminosité, taille du texte et fréquence d'écran",
                onClick = {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            )
            SettingsAction(
                icon = Icons.Rounded.AppSettingsAlt,
                title = "Autorisations de MobileScope",
                subtitle = "Contrôler les accès accordés à l'application",
                onClick = {
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )

            SectionTitle("CONFIDENTIALITÉ")
            Panel {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Security, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Accès contrôlés", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Les modules sensibles demandent leurs autorisations au moment où vous les ouvrez. Vous pouvez les retirer à tout moment dans les réglages Android. Les statistiques et les graphiques dépendent des données accessibles sur votre téléphone.",
                            fontSize = 12.sp, color = muted
                        )
                    }
                }
            }

            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("MobileScope", fontWeight = FontWeight.Bold)
                        Text("Diagnostic local de l'appareil · version ${com.medsel.mobilescope.BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = muted)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = muted)
    }
}

@Composable
fun DeviceDetailsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val state by loadState(0) { Collectors.device(ctx) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Retour") }
            Column(Modifier.weight(1f)) {
                Text("Mon appareil", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Fiche technique détaillée", fontSize = 12.sp, color = muted)
            }
            Icon(Icons.Rounded.Smartphone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp).size(25.dp))
        }
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Loaded(state) { d ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(60.dp).clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.model, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text(d.android, color = muted, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Legend(MaterialTheme.colorScheme.primary, "Correctif de sécurité", d.patch)
                    Legend(MaterialTheme.colorScheme.primary, "Cœurs processeur", d.cores.toString())
                    Legend(MaterialTheme.colorScheme.primary, "Écran", d.screen)
                    Legend(MaterialTheme.colorScheme.primary, "Temps depuis démarrage", Fmt.duration(d.uptimeMs / 1000))
                }
                SectionTitle("MATÉRIEL ET SYSTÈME")
                Panel {
                    Legend(MaterialTheme.colorScheme.primary, "Modèle", d.model)
                    Legend(MaterialTheme.colorScheme.primary, "Version Android", d.android)
                    Legend(MaterialTheme.colorScheme.primary, "Matériel / carte", d.hardware)
                    Legend(MaterialTheme.colorScheme.primary, "Architecture processeur", d.abi)
                    Legend(MaterialTheme.colorScheme.primary, "Processeurs logiques", d.cores.toString())
                    Legend(MaterialTheme.colorScheme.primary, "Résolution / écran", d.screen)
                    Legend(MaterialTheme.colorScheme.primary, "Correctif de sécurité", d.patch)
                }
            }
            SectionTitle("OUTILS")
            SettingsAction(
                icon = Icons.Rounded.Settings,
                title = "Paramètres système",
                subtitle = "Afficher les réglages Android",
                onClick = {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            )
            SettingsAction(
                icon = Icons.Rounded.DisplaySettings,
                title = "Réglages d'affichage",
                subtitle = "Écran et fréquence de rafraîchissement",
                onClick = {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            )
        }
    }
}
