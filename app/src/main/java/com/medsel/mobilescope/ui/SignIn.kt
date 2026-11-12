package com.medsel.mobilescope.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.medsel.mobilescope.BuildConfig
import com.medsel.mobilescope.data.User
import kotlinx.coroutines.launch

private val Ink = Color(0xFF0B0E14)

@Composable
fun SignInScreen(onSignedIn: (User) -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val w = (view.context as? Activity)?.window
        val c = w?.let { WindowCompat.getInsetsController(it, view) }
        val old = c?.isAppearanceLightStatusBars
        c?.isAppearanceLightStatusBars = false
        onDispose { if (c != null && old != null) c.isAppearanceLightStatusBars = old }
    }

    fun signIn() {
        if (BuildConfig.WEB_CLIENT_ID.isBlank() || BuildConfig.WEB_CLIENT_ID == "REPLACE_ME") {
            error = "ID client Google non configuré. Ajoutez le secret WEB_CLIENT_ID, ou continuez en mode invité."
            return
        }
        busy = true; error = null
        scope.launch {
            try {
                val option = GetSignInWithGoogleOption.Builder(BuildConfig.WEB_CLIENT_ID).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val cred = CredentialManager.create(ctx).getCredential(ctx, request).credential
                if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val g = GoogleIdTokenCredential.createFrom(cred.data)
                    onSignedIn(User(g.displayName ?: g.id, g.id, g.profilePictureUri?.toString(), false))
                } else error = "Type d'identifiant non pris en charge."
            } catch (e: Exception) {
                error = "Connexion impossible : ${e.message ?: e.javaClass.simpleName}"
            } finally { busy = false }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Ink).systemBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(104.dp).clip(CircleShape).background(Color(0xFF131822)), contentAlignment = Alignment.Center) {
            Ring(0.72f, Color(0xFF2DD4F0), Modifier.size(104.dp), 8.dp, Color(0xFF232B3B)) {
                Icon(Icons.Rounded.Speed, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("MobileScope", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(10.dp))
        Text(
            "Comprenez ce que fait votre téléphone : mémoire, réseau, batterie, appels et applications, en détail.",
            color = Color(0xFF8A94A8), fontSize = 15.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))
        if (error != null) {
            Text(error!!, color = Color(0xFFFF8A80), fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = { signIn() }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp, color = Ink)
            else {
                Icon(Icons.Rounded.AccountCircle, null)
                Spacer(Modifier.width(10.dp))
                Text("Continuer avec Google", fontWeight = FontWeight.SemiBold)
            }
        }
        TextButton(onClick = { onSignedIn(User("Invité", "Mode invité", null, true)) }) {
            Text("Continuer sans compte", color = Color(0xFF8A94A8))
        }
    }
}
