@file:OptIn(ExperimentalMaterial3Api::class)

package com.godskingdomnow.familybank.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.godskingdomnow.familybank.AppContainer
import com.godskingdomnow.familybank.sync.Session
import com.godskingdomnow.familybank.sync.SyncManager
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(app: AppContainer, back: () -> Unit) {
    val session by app.sessions.session.collectAsStateWithLifecycle(initialValue = Session())
    val syncing by app.sync.syncing.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family sync") },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (session.signedIn) SignedIn(app, session, syncing) else SignInForm(app, session)
            if (Support.enabled) {
                var coffee by remember { mutableStateOf(false) }
                CoffeeButton(onClick = { coffee = true }, modifier = Modifier.padding(top = 12.dp))
                Text("Family Bank is free with no ads. Coffee keeps it that way. Thank you!",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (coffee) CoffeeDialog(COFFEE_MESSAGE, fromMenu = true, onDismiss = { coffee = false })
            }
        }
    }
}

@Composable
private fun SignInForm(app: AppContainer, s: Session) {
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf(s.baseUrl.ifEmpty { Session.DEFAULT_BASE_URL }) }
    var user by rememberSaveable { mutableStateOf(s.username) }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showUrl by remember { mutableStateOf(s.baseUrl.isNotEmpty() && s.baseUrl != Session.DEFAULT_BASE_URL) }

    Text("Optional", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text("Family Bank works fully on its own. If your family uses the Family app on GodsKingdomNow.com, sign in to share the bank with the website and the rest of your family.",
        style = MaterialTheme.typography.bodyMedium)
    if (s.lastError.isNotEmpty()) Text(s.lastError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)

    OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
    OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
    if (showUrl) {
        OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Family app address") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            supportingText = { Text("Only change this if your family app lives somewhere else.") })
    } else {
        TextButton(onClick = { showUrl = true }) { Text("Use a different website address") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

    Button(
        onClick = {
            busy = true; error = null
            scope.launch {
                val r = app.sync.signIn(url, user.trim(), pass)
                busy = false
                if (r is SyncManager.Outcome.Failed) error = r.message else pass = ""
            }
        },
        enabled = !busy && user.isNotBlank() && pass.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Sign in")
    }
    Text("When a parent signs in, children already on this phone are added to the family’s bank. A child with the same name as one on the website is joined to that child.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Your password is sent only to sign in. The phone keeps a private key that ends when you sign out or change your password.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SignedIn(app: AppContainer, s: Session, syncing: Boolean) {
    val scope = rememberCoroutineScope()
    var confirmOut by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Groups, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Column(Modifier.padding(start = 12.dp)) {
                Text(s.familyName.ifEmpty { "Your family" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("${s.displayName.ifEmpty { s.username }} · ${if (s.isParent) "parent" else "family member (view-only)"}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }

    val status = when {
        syncing -> "Syncing…"
        s.lastError.isNotEmpty() -> s.lastError
        s.lastSyncAt > 0 -> "Last synced " + DateUtils.getRelativeTimeSpanString(s.lastSyncAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        else -> "Not synced yet"
    }
    Text(status, color = if (s.lastError.isNotEmpty() && !syncing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)

    Button(onClick = { scope.launch { app.sync.syncNow() } }, enabled = !syncing, modifier = Modifier.fillMaxWidth()) { Text("Sync now") }

    Text("How sharing works", style = MaterialTheme.typography.titleSmall)
    Text(
        "• This is the same Family Bank as on the website.\n" +
            "• Parents can add children and record deposits and withdrawals; other family members can look.\n" +
            "• Changes sync a few seconds after you make them, and whenever you open the app.\n" +
            "• No signal? Keep going. It catches up later and keeps the day you recorded it.\n" +
            "• Something removed on the website is removed here too.",
        style = MaterialTheme.typography.bodyMedium,
    )

    OutlinedButton(onClick = { confirmOut = true }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }

    if (confirmOut) {
        AlertDialog(
            onDismissRequest = { confirmOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your family’s children and history leave this phone. They stay safe on the website and come back when you sign in again. Children kept only on this phone stay.") },
            confirmButton = { TextButton({ confirmOut = false; scope.launch { app.sync.signOut() } }) { Text("Sign out") } },
            dismissButton = { TextButton({ confirmOut = false }) { Text("Cancel") } },
        )
    }
}
