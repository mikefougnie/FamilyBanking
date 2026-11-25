@file:OptIn(ExperimentalMaterial3Api::class)

package com.godskingdomnow.familybank.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.godskingdomnow.familybank.AppContainer
import com.godskingdomnow.familybank.data.ChildRow
import com.godskingdomnow.familybank.data.Money
import com.godskingdomnow.familybank.sync.Session
import kotlinx.coroutines.launch

private data class Pending(val child: ChildRow, val deposit: Boolean)

@Composable
fun HomeScreen(app: AppContainer, openChild: (Long) -> Unit, openAccount: () -> Unit) {
    val kids by app.repo.children.collectAsStateWithLifecycle(initialValue = null as List<ChildRow>?)
    val session by app.sessions.session.collectAsStateWithLifecycle(initialValue = Session())
    val syncing by app.sync.syncing.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var entry by remember { mutableStateOf<Pending?>(null) }
    var coffee by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Family Bank", fontWeight = FontWeight.SemiBold)
                        if (session.signedIn && session.familyName.isNotEmpty())
                            Text(session.familyName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (Support.enabled) IconButton(onClick = { coffee = true }) { Icon(Icons.Outlined.LocalCafe, "Buy me a coffee", tint = CoffeeYellow) }
                    if (session.signedIn) {
                        if (syncing) CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
                        else IconButton(onClick = { scope.launch { app.sync.syncNow() } }) { Icon(Icons.Outlined.CloudSync, "Sync now") }
                    }
                    IconButton(onClick = openAccount) { Icon(Icons.Outlined.AccountCircle, "Family sync") }
                },
            )
        },
        floatingActionButton = {
            if (session.canAdd && !kids.isNullOrEmpty())
                ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add child") })
        },
    ) { pad ->
        val list = kids
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (list != null) {
                item { TotalCard(list) }
                if (session.signedIn && session.lastError.isNotEmpty() && !syncing) item {
                    Text(session.lastError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable(onClick = openAccount))
                }
                if (session.signedIn && !session.isParent) item {
                    Text("You’re signed in as a family member, so your family’s bank is view-only. Parents record deposits and withdrawals.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (list.isEmpty()) item { EmptyState(session, onAdd = { adding = true }, openAccount = openAccount) }
                items(list, key = { it.id }) { c ->
                    ChildCard(c, canEdit = session.canEdit(c.remoteId), onOpen = { openChild(c.id) },
                        onDeposit = { entry = Pending(c, true) }, onWithdraw = { entry = Pending(c, false) })
                }
            }
        }
    }

    if (adding) NameDialog("Add a child", "", "Add", onDismiss = { adding = false }) { name ->
        adding = false
        scope.launch { app.repo.addChild(name) }
    }
    entry?.let { p ->
        EntryDialog(p.child.name, p.deposit, p.child.balanceCents, onDismiss = { entry = null }) { cents, note, at ->
            entry = null
            scope.launch { app.repo.addEntry(p.child.id, p.deposit, cents, note, at) }
        }
    }
    if (coffee) CoffeeDialog(COFFEE_MESSAGE, fromMenu = true, onDismiss = { coffee = false })
    CoffeePrompt(COFFEE_MESSAGE) { !adding && entry == null && !coffee }
}

@Composable
private fun TotalCard(list: List<ChildRow>) {
    val total = list.sumOf { it.balanceCents }
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("You’re holding", style = MaterialTheme.typography.labelLarge)
                Text(Money.format(total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(
                    when (list.size) { 0 -> "No children added yet"; 1 -> "for 1 child"; else -> "for ${list.size} children" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(Icons.Outlined.Savings, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.secondaryContainer)
        }
    }
}

@Composable
private fun EmptyState(session: Session, onAdd: () -> Unit, openAccount: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Keep track of money you hold for your children", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text("Add each child, then tap Deposit when they hand you money to keep and Withdraw when you give some back.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (session.canAdd) Button(onClick = onAdd) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Add a child") }
        if (!session.signedIn) OutlinedButton(onClick = openAccount) { Text("Sign in to your family (optional)") }
    }
}

@Composable
private fun ChildCard(c: ChildRow, canEdit: Boolean, onOpen: () -> Unit, onDeposit: () -> Unit, onWithdraw: () -> Unit) {
    val colors = LocalMoneyColors.current
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(c.name)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        c.lastAt?.let { "Last change " + DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS) }
                            ?: "No money yet",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(Money.format(c.balanceCents), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    color = if (c.balanceCents < 0) colors.withdraw else MaterialTheme.colorScheme.onSurface)
            }
            if (canEdit) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onDeposit, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.deposit, contentColor = androidx.compose.ui.graphics.Color.White)) { Text("Deposit") }
                    Button(onClick = onWithdraw, modifier = Modifier.weight(1f), enabled = c.balanceCents > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.withdraw, contentColor = androidx.compose.ui.graphics.Color.White)) { Text("Withdraw") }
                }
            }
        }
    }
}
