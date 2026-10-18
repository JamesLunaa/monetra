package com.example.monetra.ui.debts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.monetra.core.money.Money
import com.example.monetra.core.money.MoneyFormat
import com.example.monetra.core.money.SignStyle
import com.example.monetra.data.db.AccountEntity
import com.example.monetra.data.db.CategoryEntity
import com.example.monetra.data.db.CategoryType
import com.example.monetra.data.db.DebtDirection
import com.example.monetra.data.db.DebtRow
import com.example.monetra.data.db.PersonEntity
import com.example.monetra.ui.appViewModel
import com.example.monetra.ui.components.FieldHeader
import com.example.monetra.ui.components.FieldStat
import com.example.monetra.ui.components.FitText
import com.example.monetra.ui.components.categoryColor
import com.example.monetra.ui.theme.MonetraTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

@Composable
fun DebtsScreen(onBack: () -> Unit) {
    val vm = appViewModel { DebtsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val s = state ?: return
    var openPerson by remember { mutableLongStateOf(0L) }
    var adding by remember { mutableStateOf(false) }
    val fmt = s.settings.moneyFormat
    val c = MonetraTheme.colors

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            FieldHeader(
                "Debts", onMenu = null,
                navigation = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    // Zero is neutral ink with no sign; only real balances carry sign and colour.
                    FieldStat("Owed to you") {
                        FitText(fmt.format(Money(s.owedToMe), SignStyle.ALWAYS), MonetraTheme.amounts.total,
                            color = if (s.owedToMe > 0) c.fieldIncome else c.onField)
                    }
                    FieldStat("You owe") {
                        FitText(fmt.format(-Money(s.iOwe)), MonetraTheme.amounts.total,
                            color = if (s.iOwe > 0) c.fieldExpense else c.onField)
                    }
                }
            }
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 104.dp), modifier = Modifier.navigationBarsPadding()) {
                if (s.people.isEmpty()) {
                    item {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Nobody owes anything", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Split a bill from its record (open it and tap Split with someone), or tap + to log cash you lent or borrowed.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    item { Heading("People") }
                    items(s.people, key = { it.personId }) { p -> PersonRow(p, fmt) { openPerson = p.personId } }
                }
                if (s.settled.isNotEmpty()) {
                    item {
                        Text(
                            "Settled with " + s.settled.joinToString { it.name },
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { adding = true },
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
        ) { Icon(Icons.Rounded.Add, contentDescription = "Add debt") }
    }

    (s.people + s.settled).firstOrNull { it.personId == openPerson }?.let { p ->
        PersonSheet(p, fmt, accounts, vm, onDismiss = { openPerson = 0 })
    }
    if (adding) AddDebtDialog(people, accounts, categories.filter { it.type == CategoryType.EXPENSE }, fmt,
        onAdd = { k, n, a, acc, cat, d, note -> vm.add(k, n, a, acc, cat, d, note); adding = false }) { adding = false }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp).semantics { heading() })
}

/** Initial on a stall colour picked from the name, so a person keeps one colour everywhere. */
@Composable
fun PersonTile(name: String, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).background(categoryColor(Math.floorMod(name.lowercase().hashCode(), 12)), MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MonetraTheme.colors.onCategory)
    }
}

private fun kindOf(d: DebtRow) = when {
    d.direction == DebtDirection.OWED_TO_ME && d.categoryName != null -> "${d.categoryName} split"
    d.direction == DebtDirection.OWED_TO_ME -> "Lent cash"
    d.categoryName != null -> "Paid for you · ${d.categoryName}"
    else -> "Borrowed cash"
}

@Composable
private fun PersonRow(p: PersonLine, fmt: MoneyFormat, onClick: () -> Unit) {
    val c = MonetraTheme.colors
    val net = p.net
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 72.dp).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PersonTile(p.name)
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val kinds = p.open.map(::kindOf).distinct()
            Text("${p.open.size} open · ${kinds.joinToString()}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(fmt.format(Money(net), SignStyle.ALWAYS), style = MonetraTheme.amounts.row,
                color = if (net >= 0) c.income else c.expense, maxLines = 1)
            Text(if (net >= 0) "owes you" else "you owe", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonSheet(p: PersonLine, fmt: MoneyFormat, accounts: List<AccountEntity>, vm: DebtsViewModel, onDismiss: () -> Unit) {
    var repaying by remember { mutableStateOf<DebtRow?>(null) }
    var deleting by remember { mutableStateOf<DebtRow?>(null) }
    val c = MonetraTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.large).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PersonTile(p.name, 52.dp)
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(if (p.net >= 0) "Owes you" else "You owe", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FitText(fmt.format(Money(p.net), SignStyle.ALWAYS), MonetraTheme.amounts.total, color = if (p.net >= 0) c.income else c.expense)
            }
            Spacer(Modifier.height(12.dp))
            p.debts.sortedWith(compareBy<DebtRow> { it.settled }.thenByDescending { it.createdAt }).forEach { d ->
                DebtItem(d, fmt, onRepay = { repaying = d }, onDelete = { deleting = d })
            }
        }
    }
    repaying?.let { d ->
        RepayDialog(d, accounts, fmt, onRepay = { amount, acc, date -> vm.repay(d, amount, acc, date); repaying = null }) { repaying = null }
    }
    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this debt?") },
            text = { Text("Removes the debt and any lending, borrowing or repayment records it created. A split bill itself stays.") },
            confirmButton = { TextButton(onClick = { vm.delete(d.id); deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DebtItem(d: DebtRow, fmt: MoneyFormat, onRepay: () -> Unit, onDelete: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = if (d.settled) muted else MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(kindOf(d), style = MaterialTheme.typography.bodyLarge, color = ink)
                val date = dateFormat.format(Instant.ofEpochMilli(d.createdAt).atZone(ZoneId.systemDefault()))
                Text(listOf(date, d.note).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                // Only the figure takes the narrow cut; words stay in the body face.
                if (d.settled) Text("Settled", style = MaterialTheme.typography.bodyLarge, color = ink)
                else Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmt.format(Money(d.remaining)), style = MonetraTheme.amounts.row.copy(fontSize = 18.sp, lineHeight = 22.sp), color = ink)
                    Text(" left", style = MaterialTheme.typography.bodySmall, color = muted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("of ", style = MaterialTheme.typography.bodySmall, color = muted)
                    Text(fmt.format(Money(d.amount)), style = MonetraTheme.amounts.row.copy(fontSize = 14.sp, lineHeight = 18.sp), color = muted)
                }
            }
        }
        // Paid share as data: dusk, not the lamp.
        Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth((d.paid / d.amount.toFloat()).coerceIn(0f, 1f)).height(6.dp).background(MaterialTheme.colorScheme.outline, RoundedCornerShape(3.dp)))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(fmt.format(Money(d.paid)), style = MonetraTheme.amounts.row.copy(fontSize = 14.sp, lineHeight = 18.sp), color = muted)
                Text(if (d.direction == DebtDirection.OWED_TO_ME) " paid back" else " paid", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            if (!d.settled) TextButton(onClick = onRepay) { Text(if (d.direction == DebtDirection.OWED_TO_ME) "Record repayment" else "Pay back") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountChips(accounts: List<AccountEntity>, selected: Long?, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        accounts.forEach { a -> FilterChip(selected == a.id, { onSelect(a.id) }, label = { Text(a.name) }) }
    }
}

@Composable
private fun RepayDialog(d: DebtRow, accounts: List<AccountEntity>, fmt: MoneyFormat, onRepay: (Long, Long, LocalDate) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(d.remaining.asInput()) }
    var account by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    val owedToMe = d.direction == DebtDirection.OWED_TO_ME
    val parsed = Money.parse(amount)?.minor?.takeIf { it in 1..d.remaining }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (owedToMe) "${d.personName} paid back" else "Pay ${d.personName} back") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AmountField(amount, { amount = it }, fmt, "Amount", max = d.remaining)
                Text(if (owedToMe) "Into" else "From", style = MaterialTheme.typography.titleSmall)
                AccountChips(accounts, account) { account = it }
                Text(
                    when {
                        owedToMe && d.categoryName != null -> "${d.categoryName} spending goes down by this amount."
                        owedToMe -> "Comes back into the account; it isn't income."
                        d.categoryName != null -> "Counts as ${d.categoryName} spending today."
                        else -> "Leaves the account; it isn't spending."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(enabled = parsed != null && account != null, onClick = { onRepay(parsed!!, account!!, LocalDate.now()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddDebtDialog(
    people: List<PersonEntity>,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    fmt: MoneyFormat,
    onAdd: (NewDebt, String, Long, Long?, Long?, LocalDate, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(NewDebt.LENT) }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var account by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    var category by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    val parsed = Money.parse(amount)?.minor?.takeIf { it > 0 }
    val ok = name.isNotBlank() && parsed != null && (if (kind == NewDebt.PAID_FOR_ME) category != null else account != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New debt") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(NewDebt.LENT to "I lent cash", NewDebt.BORROWED to "I borrowed", NewDebt.PAID_FOR_ME to "They paid for me").forEach { (k, l) ->
                        FilterChip(kind == k, { kind = k }, label = { Text(l) })
                    }
                }
                PersonField(name, { name = it }, people)
                AmountField(amount, { amount = it }, fmt, "Amount")
                if (kind == NewDebt.PAID_FOR_ME) {
                    Text("For", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { cat -> FilterChip(category == cat.id, { category = cat.id }, label = { Text(cat.name) }) }
                    }
                } else {
                    Text(if (kind == NewDebt.LENT) "From" else "Into", style = MaterialTheme.typography.titleSmall)
                    AccountChips(accounts, account) { account = it }
                }
                OutlinedTextField(note, { note = it.take(120) }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    when (kind) {
                        NewDebt.LENT -> "The cash leaves the account now and comes back as they repay. Neither counts as spending."
                        NewDebt.BORROWED -> "The cash arrives now and leaves as you repay. Neither counts as income or spending."
                        NewDebt.PAID_FOR_ME -> "Nothing moves yet. It becomes spending in that category when you pay them back."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onAdd(kind, name.trim(), parsed!!, account, category, LocalDate.now(), note.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
