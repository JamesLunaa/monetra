package com.example.monetra.ui.debts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.monetra.core.money.Money
import com.example.monetra.core.money.MoneyFormat
import com.example.monetra.data.db.PersonEntity

/** Name field with one-tap suggestions from people already used. */
@Composable
fun PersonField(name: String, onName: (String) -> Unit, people: List<PersonEntity>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            name, { onName(it.take(40)) }, label = { Text("Who") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        val matches = people.filter { name.isBlank() || it.name.contains(name.trim(), ignoreCase = true) }
            .filter { !it.name.equals(name.trim(), ignoreCase = true) }.take(8)
        if (matches.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                matches.forEach { p -> AssistChip(onClick = { onName(p.name) }, label = { Text(p.name) }) }
            }
        }
    }
}

/** Amount field in major units ("150.50"); returns the parsed value or null. */
@Composable
fun AmountField(text: String, onText: (String) -> Unit, fmt: MoneyFormat, label: String, max: Long? = null): Money? {
    val parsed = Money.parse(text)?.takeIf { it.minor > 0 && (max == null || it.minor <= max) }
    OutlinedTextField(
        text, { onText(it.filter { c -> c.isDigit() || c == '.' }.take(12)) },
        label = { Text(label) }, prefix = { Text(fmt.symbol) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        isError = text.isNotEmpty() && parsed == null,
        supportingText = max?.let { m -> { Text("Up to ${fmt.format(Money(m))}") } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
    return parsed
}

internal fun Long.asInput() = Money(this).toBigDecimal().stripTrailingZeros().toPlainString()

/** "Someone owes part of this expense." Defaults to half. */
@Composable
fun SplitDialog(
    expenseAmount: Long,
    title: String,
    people: List<PersonEntity>,
    fmt: MoneyFormat,
    onConfirm: (name: String, amount: Long, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf((expenseAmount / 2).asInput()) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split $title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("They owe you part of ${fmt.format(Money(expenseAmount))}. When they pay it back, $title spending goes down by that much.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PersonField(name, { name = it }, people)
                AmountField(amount, { amount = it }, fmt, "They owe", max = expenseAmount)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { amount = (expenseAmount / 2).asInput() }, label = { Text("Half") })
                    AssistChip(onClick = { amount = expenseAmount.asInput() }, label = { Text("All") })
                }
                OutlinedTextField(note, { note = it.take(120) }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && (Money.parse(amount)?.minor ?: 0) in 1..expenseAmount,
                onClick = { onConfirm(name.trim(), Money.parse(amount)!!.minor, note.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
