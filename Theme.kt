package com.medsel.mobilescope.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

private val Dark = darkColorScheme(
    primary = Color(0xFF8B7CFF), onPrimary = Color(0xFF0B0E14),
    background = Color(0xFF0B0E14), onBackground = Color(0xFFEEF2F8),
    surface = Color(0xFF131822), onSurface = Color(0xFFEEF2F8),
    surfaceVariant = Color(0xFF1C2433), onSurfaceVariant = Color(0xFF8A94A8),
    outline = Color(0xFF232B3B)
)
private val Light = lightColorScheme(
    primary = Color(0xFF5B49E6), onPrimary = Color.White,
    background = Color(0xFFF4F6FA), onBackground = Color(0xFF0E1320),
    surface = Color.White, onSurface = Color(0xFF0E1320),
    surfaceVariant = Color(0xFFE7EBF3), onSurfaceVariant = Color(0xFF59647C),
    outline = Color(0xFFDCE2EE)
)

@Composable
fun MobileScopeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography(),
        content = content
    )
}

/** Une couleur par domaine : l'accent suit l'utilisateur de l'accueil jusqu'au détail. */
enum class Cat(val title: String, val sub: String, val icon: ImageVector, val color: Color) {
    Ram("Mémoire RAM", "Occupation en direct", Icons.Rounded.Memory, Color(0xFFFF5CA8)),
    Battery("Batterie", "Santé, courant, température", Icons.Rounded.BatteryChargingFull, Color(0xFF8FD11E)),
    Network("Réseau et Internet", "Mo/Go envoyés et reçus", Icons.Rounded.Wifi, Color(0xFF14B8D4)),
    Storage("Stockage", "Espace et poids des apps", Icons.Rounded.Storage, Color(0xFFFFB020)),
    Contacts("Contacts", "Favoris et doublons", Icons.Rounded.Contacts, Color(0xFF8B7CFF)),
    Calls("Appels", "Durées et top contacts", Icons.Rounded.Call, Color(0xFF22C58B)),
    Sms("SMS", "Envoyés, reçus, échanges", Icons.Rounded.Sms, Color(0xFF4C8DFF)),
    Apps("Applications", "Temps d'usage, installations", Icons.Rounded.Apps, Color(0xFFFF6B5E))
}
