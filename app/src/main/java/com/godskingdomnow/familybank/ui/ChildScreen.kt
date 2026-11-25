@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.godskingdomnow.familybank.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.godskingdomnow.familybank.AppContainer
import com.godskingdomnow.familybank.data.ChildEntity
import com.godskingdomnow.familybank.data.EntryEntity
import com.godskingdomnow.familybank.data.Money
import com.godskingdomnow.familybank.sync.Session
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
private val shortDay = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/** One history row, with the child's balance right after it. */
private data class Line(val entry: EntryEntity, val balanceAfter: Long, val month: String)

@Composable
fun ChildScreen(app: AppContainer, childId: Long, back: () -> Unit) {
    val child by app.repo.child(childId).collectAsStateWithLifecycle(initialValue = null as ChildEntity?)
    val entries by app.repo.entries(childId).collectAsStateWithLifecycle(initialValue = emptyList<EntryEntity>())
    val session by app.sessions.session.collectAsStateWithLifecycle(initialValue = Session())
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val colors = LocalMoneyColors.current

    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(child) { if (child != null) loaded = true }
    // removed (here or on the website): go back
    LaunchedEffect(child, loaded) { val c = child; if (loaded && (c == null || c.deleted)) back() }

    val c = child ?: return
    val canEdit = session.canEdit(c.remoteId)
    val balance = entries.sumOf { it.signedCents }
    val zone = ZoneId.systemDefault()
    val lines = remember(entries) {
        var running = balance
        entries.map { e ->
            val l = Line(e, running, Instant.ofEpochMilli(e.at).atZone(zone).format(monthFmt))
            running -= e.signedCents
            l
        }
    }

    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var entryKind by remember { mutableStateOf<Boolean?>(null) }   // true = deposit
    var deleting by remember { mutableStateOf<EntryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Share statement…") }, onClick = { menu = false; shareStatement(ctx, c.name, balance, entries) })
                        if (canEdit) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text("Remove ${c.name}", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; removing = true })
                        }
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp)) {
            item {
                Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(c.name, 56.dp)
                        Text("${c.name}’s balance", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                        Text(Money.format(balance), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                        if (c.remoteId == null && session.signedIn)
                            Text("On this phone only", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (canEdit) item {
                Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { entryKind = true }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.deposit, contentColor = Color.White)) { Text("Deposit") }
                    Button(onClick = { entryKind = false }, modifier = Modifier.weight(1f), enabled = balance > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.withdraw, contentColor = Color.White)) { Text("Withdraw") }
                }
            } else item {
                Text("View-only: parents record deposits and withdrawals.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 14.dp))
            }

            if (lines.isEmpty()) item {
                Text("No deposits or withdrawals yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            } else if (canEdit) item {
                Text("Press and hold an entry to delete it.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }

            lines.forEachIndexed { i, line ->
                if (i == 0 || lines[i - 1].month != line.month) item(key = "m-${line.month}") {
                    Text(line.month, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                }
                item(key = line.entry.id) {
                    EntryRow(line, onLongPress = { if (canEdit) deleting = line.entry })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    entryKind?.let { dep ->
        EntryDialog(c.name, dep, balance, onDismiss = { entryKind = null }) { cents, note, at ->
            entryKind = null
            scope.launch { app.repo.addEntry(c.id, dep, cents, note, at) }
        }
    }
    if (renaming) NameDialog("Rename", c.name, "Save", onDismiss = { renaming = false }) { name ->
        renaming = false
        scope.launch { app.repo.renameChild(c.id, name) }
    }
    if (removing) ConfirmDialog(
        "Remove ${c.name}?",
        "This removes ${c.name} and all of their history" + (if (c.remoteId != null) ", here and on the website." else ".") +
            (if (balance > 0) " You’re still holding ${Money.format(balance)} for them." else ""),
        "Remove", onDismiss = { removing = false },
    ) {
        removing = false
        scope.launch { app.repo.removeChild(c.id) }
    }
    deleting?.let { e ->
        ConfirmDialog(
            "Delete this entry?",
            "${if (e.deposit) "Deposit" else "Withdrawal"} of ${Money.format(e.amountCents)} on ${formatDay(e.at)}" +
                (if (e.note.isNotBlank()) " (${e.note})" else "") + ". The balance will change by ${Money.format(e.amountCents)}.",
            "Delete", onDismiss = { deleting = null },
        ) {
            deleting = null
            scope.launch { app.repo.deleteEntry(e.id) }
        }
    }
}

@Composable
private fun EntryRow(line: Line, onLongPress: () -> Unit) {
    val e = line.entry
    val colors = LocalMoneyColors.current
    val tint = if (e.deposit) colors.deposit else colors.withdraw
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongPress).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (e.deposit) colors.depositSoft else colors.withdrawSoft),
            contentAlignment = Alignment.Center) {
            Icon(if (e.deposit) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(e.note.ifBlank { if (e.deposit) "Deposit" else "Withdrawal" }, style = MaterialTheme.typography.bodyLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            val day = Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault()).format(shortDay)
            Text(day + (if (e.byName.isNotBlank()) " · ${e.byName}" else "") + (if (e.remoteId == null && e.dirty) " · not synced" else ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.signed(e.signedCents), color = tint, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Text(Money.format(line.balanceAfter), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun shareStatement(ctx: Context, name: String, balance: Long, entries: List<EntryEntity>) {
    val zone = ZoneId.systemDefault()
    val sb = StringBuilder("$name’s Family Bank\nBalance: ${Money.format(balance)}\n")
    if (entries.isNotEmpty()) sb.append('\n')
    entries.take(100).forEach { e ->
        sb.append(Instant.ofEpochMilli(e.at).atZone(zone).format(shortDay)).append("  ")
            .append(Money.signed(e.signedCents))
            .append(if (e.note.isNotBlank()) "  ${e.note}" else if (e.deposit) "  Deposit" else "  Withdrawal").append('\n')
    }
    if (entries.size > 100) sb.append("…and ${entries.size - 100} older entries\n")
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sb.toString())
        .putExtra(Intent.EXTRA_SUBJECT, "$name’s Family Bank")
    ctx.startActivity(Intent.createChooser(send, "Share statement").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
