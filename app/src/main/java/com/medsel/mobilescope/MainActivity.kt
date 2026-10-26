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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.medsel.mobilescope.data.Prefs
import com.medsel.mobilescope.data.User
import com.medsel.mobilescope.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeMode by remember { mutableStateOf(Prefs.theme(this@MainActivity)) }
            MobileScopeTheme(themeMode) {
                AppRoot(onThemeChanged = { themeMode = it })
            }
        }
    }
}

@Composable
fun AppRoot(onThemeChanged: (String) -> Unit) {
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
                        }
                    )
                    "settings" -> SettingsScreen(onBack = back, onThemeChanged = onThemeChanged)
                    "device" -> DeviceDetailsScreen(onBack = back)
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
    }
}
