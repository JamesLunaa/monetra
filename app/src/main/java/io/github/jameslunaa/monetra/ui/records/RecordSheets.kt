package io.github.jameslunaa.monetra.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jameslunaa.monetra.core.money.MoneyFormat
import io.github.jameslunaa.monetra.core.time.PeriodType
import io.github.jameslunaa.monetra.data.db.RecordRow
import io.github.jameslunaa.monetra.data.db.TxType
import io.github.jameslunaa.monetra.data.prefs.Settings
import io.github.jameslunaa.monetra.ui.components.AppIcons
import io.github.jameslunaa.monetra.ui.components.categoryColor
import io.github.jameslunaa.monetra.ui.components.RecordAmount
import io.github.jameslunaa.monetra.ui.components.SwitchRow
import io.github.jameslunaa.monetra.ui.theme.MonetraTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun typeLabel(t: TxType) = when (t) {
    TxType.INCOME -> "Income"
    TxType.EXPENSE -> "Expense"
    TxType.TRANSFER -> "Transfer"
    TxType.REFUND -> "Paid back · lowers spending"
    TxType.DEBT_IN -> "Loan · money in"
    TxType.DEBT_OUT -> "Loan · money out"
}

private val whenFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy · h:mm a", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordSheet(
    row: RecordRow,
    fmt: MoneyFormat,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onSplit: () -> Unit,
    onOpenDebts: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            // The record's category color carries past the list tile into a full header band.
            val transfer = row.categoryId == null
            val band = if (transfer) MaterialTheme.colorScheme.surfaceContainerHighest else categoryColor(row.categoryColor)
            val onBand = if (transfer) MaterialTheme.colorScheme.onSurface else MonetraTheme.colors.onCategory
            Row(
                Modifier.fillMaxWidth().background(band, MaterialTheme.shapes.large).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(when { row.type == TxType.TRANSFER -> Icons.AutoMirrored.Rounded.CompareArrows; transfer -> AppIcons["debt"]; else -> AppIcons[row.categoryIcon] }, null,
                    tint = onBand, modifier = Modifier.size(32.dp))
                Column {
                    Text(recordTitle(row), style = MaterialTheme.typography.titleLarge, color = onBand)
                    Text(typeLabel(row.type),
                        style = MaterialTheme.typography.bodyMedium, color = onBand)
                }
            }
            Spacer(Modifier.height(16.dp))
            RecordAmount(row.type, row.amount, fmt, MonetraTheme.amounts.hero.copy(fontSize = 48.sp, lineHeight = 50.sp))
            Text(
                whenFormat.format(Instant.ofEpochMilli(row.occurredAt).atZone(ZoneId.systemDefault())),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (row.type == TxType.TRANSFER) {
                Detail("From", row.accountName)
                Detail("To", row.toAccountName.orEmpty())
            } else {
                Detail("Account", row.accountName)
            }
            if (row.note.isNotBlank()) Detail("Note", row.note.trim())
            if (row.debtId != null) {
                // Debt records are managed from Debts so the debt's balance stays consistent.
                Text("Part of a debt with ${row.personName ?: "someone"}.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (row.type == TxType.EXPENSE) {
                TextButton(onClick = onSplit, contentPadding = PaddingValues(0.dp)) { Text("Split with someone…") }
            }
            Spacer(Modifier.height(16.dp))
            if (row.debtId != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
                    Button(onClick = onOpenDebts, modifier = Modifier.weight(1f)) { Text("Open Debts") }
                }
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val pad = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                OutlinedButton(
                    onClick = onDelete, contentPadding = pad, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Delete", maxLines = 1) }
                FilledTonalButton(onClick = onDuplicate, contentPadding = pad, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Copy", maxLines = 1)
                }
                Button(onClick = onEdit, contentPadding = pad, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Edit", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private val periodLabels = listOf(
    PeriodType.DAY to "Daily", PeriodType.WEEK to "Weekly", PeriodType.MONTH to "Monthly",
    PeriodType.QUARTER to "3 months", PeriodType.HALF_YEAR to "6 months", PeriodType.YEAR to "Yearly",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DisplayOptionsSheet(
    settings: Settings,
    onPeriodType: (PeriodType) -> Unit,
    onShowTotal: (Boolean) -> Unit,
    onCarryOver: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Display options", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Text("View by", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                periodLabels.forEach { (type, label) ->
                    FilterChip(selected = settings.periodType == type, onClick = { onPeriodType(type) }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow("Show net total", settings.showTotal, onShowTotal, "Lead with income minus spending. Off shows spending first.")
            SwitchRow("Carry over", settings.carryOver, onCarryOver, "Add what was left from earlier periods to this period's total.")
        }
    }
}
