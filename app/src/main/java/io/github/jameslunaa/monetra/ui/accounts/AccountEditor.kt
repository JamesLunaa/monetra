package io.github.jameslunaa.monetra.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.monetra.core.money.Money
import io.github.jameslunaa.monetra.data.db.AccountEntity
import io.github.jameslunaa.monetra.ui.components.AppIcons
import io.github.jameslunaa.monetra.ui.components.ColorPicker
import io.github.jameslunaa.monetra.ui.components.IconPicker

/** Create ([initial] null) or edit an account, including its starting balance. */
@Composable
fun AccountEditorDialog(initial: AccountEntity?, onSave: (AccountEntity) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var balance by remember {
        mutableStateOf(initial?.initialBalance?.takeIf { it != 0L }?.let { Money(it).toBigDecimal().toPlainString() }.orEmpty())
    }
    var icon by remember { mutableStateOf(initial?.icon ?: "wallet") }
    var color by remember { mutableIntStateOf(initial?.colorIndex ?: 4) }
    val parsed = if (balance.isBlank()) Money.ZERO else Money.parse(balance)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New account" else "Edit account") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    balance, { balance = it.filter { c -> c.isDigit() || c == '.' || c == '-' }.take(16) },
                    label = { Text("Starting balance") }, singleLine = true,
                    isError = parsed == null,
                    supportingText = { Text(if (parsed == null) "Enter a number like 1500 or 1500.50" else "What the account held before your first record") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text("Color", style = MaterialTheme.typography.titleSmall)
                ColorPicker(color) { color = it }
                Text("Icon", style = MaterialTheme.typography.titleSmall)
                IconPicker(AppIcons.accountKeys, icon, color) { icon = it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && parsed != null, onClick = {
                val base = initial ?: AccountEntity(name = "", icon = icon, colorIndex = color)
                onSave(base.copy(name = name.trim(), icon = icon, colorIndex = color, initialBalance = parsed!!.minor))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
