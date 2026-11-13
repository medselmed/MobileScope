package com.medsel.mobilescope

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.medsel.mobilescope.data.Background
import com.medsel.mobilescope.data.Fmt
import com.medsel.mobilescope.data.History
import com.medsel.mobilescope.data.Prefs
import com.medsel.mobilescope.data.Tr
import com.medsel.mobilescope.data.User
import com.medsel.mobilescope.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Langue, unités et planification de l'historique appliquées avant l'affichage.
        Tr.apply(this)
        Fmt.decimal = Prefs.decimalUnits(this)
        Background.sync(applicationContext)
        val app = applicationContext
        Thread { runCatching { History.record(app) } }.start()
        setContent {
            var themeMode by remember { mutableStateOf(Prefs.theme(this@MainActivity)) }
            var accentMode by remember { mutableStateOf(Prefs.accent(this@MainActivity)) }
            var lang by remember { mutableStateOf(Prefs.lang(this@MainActivity)) }
            MobileScopeTheme(themeMode, accentMode) {
                // L'arabe s'écrit de droite à gauche ; key(lang) reconstruit l'interface au changement de langue.
                CompositionLocalProvider(
                    LocalLayoutDirection provides (if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr)
                ) {
                    key(lang) {
                        AppRoot(
                            onThemeChanged = { themeMode = it },
                            onAccentChanged = { accentMode = it },
                            onLanguageChanged = { Tr.set(it); lang = it }
                        )
                    }
                }
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    Tab("home", "Accueil", Icons.Rounded.GridView),
    Tab("health", "Santé", Icons.Rounded.HealthAndSafety),
    Tab("tools", "Outils", Icons.Rounded.Handyman),
    Tab("settings", "Réglages", Icons.Rounded.Tune)
)

@Composable
fun AppRoot(onThemeChanged: (String) -> Unit, onAccentChanged: (String) -> Unit, onLanguageChanged: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(Prefs.load(ctx)) }
    var route by rememberSaveable { mutableStateOf("home") }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val u: User? = user
        if (u == null) {
            SignInScreen { Prefs.save(ctx, it); user = it }
        } else {
            BackHandler(enabled = route != "home") { route = "home" }
            val showBar = TABS.any { it.route == route }
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(1f).let { if (showBar) it.consumeWindowInsets(WindowInsets.navigationBars) else it }
                ) {
                    AnimatedContent(
                        targetState = route,
                        transitionSpec = {
                            if (targetState != "home")
                                (slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(260))) togetherWith fadeOut(tween(120))
                            else fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(160)))
                        },
                        label = "nav"
                    ) { r ->
                        val back = { route = "home" }
                        val open: (String) -> Unit = { route = it }
                        when (r) {
                            "home" -> HomeScreen(
                                user = u,
                                onOpen = { route = it.name },
                                onOpenSettings = { route = "settings" },
                                onOpenDevice = { route = "device" },
                                onSignOut = {
                                    scope.launch {
                                        runCatching { CredentialManager.create(ctx).clearCredentialState(ClearCredentialStateRequest()) }
                                        Prefs.clear(ctx)
                                        user = null
                                        route = "home"
                                    }
                                },
                                onOpenRoute = open
                            )
                            "settings" -> SettingsScreen(
                                onBack = back,
                                onThemeChanged = onThemeChanged,
                                onAccentChanged = onAccentChanged,
                                onLanguageChanged = onLanguageChanged
                            )
                            "device" -> DeviceDetailsScreen(onBack = back)
                            "tools" -> ToolsHubScreen(onOpen = open)
                            "health" -> HealthScreen(onBack = back, onOpen = open)
                            "history" -> HistoryScreen(onBack = back)
                            "sensors" -> SensorsScreen(onBack = back)
                            "security" -> SecurityScreen(onBack = back)
                            "hardware" -> HardwareScreen(onBack = back)
                            "nettools" -> NetToolsScreen(onBack = back)
                            "widgets" -> WidgetsScreen(onBack = back)
                            "cleanup" -> CleanupScreen(onBack = back)
                            "privacy" -> PrivacyScreen(onBack = back)
                            "screentime" -> ScreenTimeScreen(onBack = back)
                            "speed" -> SpeedScreen(onBack = back)
                            "dataplan" -> DataPlanScreen(onBack = back)
                            "autonomy" -> AutonomyScreen(onBack = back)
                            else -> when (Cat.valueOf(r)) {
                                Cat.Contacts -> ContactsScreen(back)
                                Cat.Calls -> CallsScreen(back)
                                Cat.Sms -> SmsScreen(back)
                                Cat.Network -> NetworkScreen(back)
                                Cat.Storage -> StorageScreen(back)
                                Cat.Ram -> RamScreen(back)
                                Cat.Battery -> BatteryScreen(back)
                                Cat.Apps -> AppsScreen(back)
                            }
                        }
                    }
                }
                if (showBar) {
                    NavigationBar {
                        TABS.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = { route = tab.route },
                                icon = { Icon(tab.icon, tab.label) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        }
    }
}
