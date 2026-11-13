package com.medsel.mobilescope.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medsel.mobilescope.data.Extras

/** Carte « Santé de l'appareil » de l'accueil : score + premier conseil, cliquable. */
@Composable
fun HealthMiniCard(onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val st by loadState(0) { Extras.health(ctx) }
    Panel(Modifier.clickable(onClick = onOpen)) {
        Loaded(st) { h ->
            val col = scoreColor(h.score)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring(h.score / 100f, col, Modifier.size(86.dp), 10.dp) {
                    Text("${h.score}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Santé de l'appareil", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(verdict(h.score), fontSize = 12.sp, color = col, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(h.advice.first(), fontSize = 12.sp, color = muted, maxLines = 2)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = muted)
            }
        }
    }
}

/** Raccourcis vers les outils avancés, en une rangée défilante. */
@Composable
fun AdvancedToolsRow(onOpen: (String) -> Unit) {
    val items = listOf(
        Triple("health", "Santé", Icons.Rounded.HealthAndSafety),
        Triple("history", "Historique", Icons.Rounded.Timeline),
        Triple("hardware", "Matériel", Icons.Rounded.Memory),
        Triple("sensors", "Capteurs", Icons.Rounded.Sensors),
        Triple("security", "Sécurité", Icons.Rounded.Security),
        Triple("nettools", "Réseau avancé", Icons.Rounded.NetworkCheck),
        Triple("widgets", "Widgets", Icons.Rounded.Widgets)
    )
    Column {
        SectionTitle("OUTILS AVANCÉS")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (route, label, icon) ->
                AssistChip(
                    onClick = { onOpen(route) },
                    label = { Text(label) },
                    leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp)) }
                )
            }
        }
    }
}
