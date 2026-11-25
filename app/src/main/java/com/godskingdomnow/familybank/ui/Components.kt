@file:OptIn(ExperimentalMaterial3Api::class)

package com.godskingdomnow.familybank.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.godskingdomnow.familybank.data.Money
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val avatarColors = listOf(
    Color(0xFF3B6EA8), Color(0xFF2E7D4F), Color(0xFFB0472A), Color(0xFF7B4FA0),
    Color(0xFFB07D2B), Color(0xFF1F7A7A), Color(0xFFA03D67), Color(0xFF5A6B2E),
)

/** A coloured circle with the child's first letter. */
@Composable
fun Avatar(name: String, size: Dp = 44.dp) {
    val color = avatarColors[Math.floorMod(name.lowercase().hashCode(), avatarColors.size)]
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase().ifEmpty { "?" }, color = Color.White,
            fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}

private val dayFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)
fun formatDay(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dayFmt)

/** Type a name: adding or renaming a child. */
@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(name, { name = it.take(80) }, Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text("Child’s name") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Record a deposit or withdrawal. [balanceCents] stops a withdrawal of more than the child has.
 * Gives back cents, note and when it happened.
 */
@Composable
fun EntryDialog(
    childName: String,
    deposit: Boolean,
    balanceCents: Long,
    onDismiss: () -> Unit,
    onSave: (cents: Long, note: String, at: Long) -> Unit,
) {
    val colors = LocalMoneyColors.current
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var at by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var picking by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val cents = Money.parseCents(amount)
    val tooMuch = !deposit && cents != null && cents > balanceCents
    val error = when {
        amount.isNotBlank() && cents == null -> "Enter an amount like 5 or 12.50"
        tooMuch -> "That’s more than $childName has (${Money.format(balanceCents)})."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (deposit) "Deposit for $childName" else "Withdraw for $childName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (deposit) "Money you’re holding for $childName." else "Money you’re giving back to $childName.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(amount, { amount = it.take(12) }, Modifier.fillMaxWidth().focusRequester(focus),
                    label = { Text("Amount") }, prefix = { Text("$") }, singleLine = true,
                    isError = error != null, supportingText = if (error != null) { { Text(error) } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(note, { note = it.take(255) }, Modifier.fillMaxWidth(),
                    label = { Text(if (deposit) "What for? (optional)" else "What for? (optional)") },
                    placeholder = { Text(if (deposit) "Birthday money, chores…" else "Toy store, savings…") },
                    singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                AssistChip(onClick = { picking = true }, label = { Text(formatDay(at)) },
                    leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null) })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (cents != null && !tooMuch) onSave(cents, note, at) },
                enabled = cents != null && !tooMuch,
            ) {
                Text(
                    (if (deposit) "Deposit " else "Withdraw ") + (cents?.let { Money.format(it) } ?: ""),
                    color = if (cents != null && !tooMuch) (if (deposit) colors.deposit else colors.withdraw) else Color.Unspecified,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (picking) {
        val zone = ZoneId.systemDefault()
        val shownDay = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = shownDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        val today = LocalDate.now(zone)
                        at = when {
                            day == today -> System.currentTimeMillis()
                            day.isAfter(today) -> System.currentTimeMillis()      // no future dates
                            else -> day.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
                        }
                    }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

/** A yes/no question. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
