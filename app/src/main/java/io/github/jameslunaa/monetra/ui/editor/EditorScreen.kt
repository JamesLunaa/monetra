package io.github.jameslunaa.monetra.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jameslunaa.monetra.core.budget.BudgetLevel
import io.github.jameslunaa.monetra.core.calc.Key
import io.github.jameslunaa.monetra.core.money.Money
import io.github.jameslunaa.monetra.data.db.AccountEntity
import io.github.jameslunaa.monetra.data.db.CategoryEntity
import io.github.jameslunaa.monetra.data.db.TxType
import io.github.jameslunaa.monetra.data.prefs.Settings
import io.github.jameslunaa.monetra.ui.appViewModel
import io.github.jameslunaa.monetra.ui.components.FitText
import io.github.jameslunaa.monetra.ui.components.IconTile
import io.github.jameslunaa.monetra.ui.theme.MonetraTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun EditorScreen(id: Long, copyOf: Long, onClose: () -> Unit) {
    val vm = appViewModel(key = "editor-$id-$copyOf") { EditorViewModel(it, id, copyOf) }
    val s by vm.state.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val settings by vm.format.collectAsStateWithLifecycle(Settings())
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf<Picker?>(null) }
    if (!s.loaded) return

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        EditorHeader(
            title = if (s.isEdit) "Edit record" else "New record",
            type = s.type,
            onType = vm::setType,
            onCancel = onClose,
            onSave = { scope.launch { if (vm.save()) onClose() } },
        )
        Column(
            Modifier.weight(1f).navigationBarsPadding().imePadding().padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            val account = accounts.firstOrNull { it.id == s.accountId }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.type == TxType.TRANSFER) {
                    Choice("From", account?.name, account?.icon, account?.colorIndex, Modifier.weight(1f)) { picker = Picker.FROM }
                    val to = accounts.firstOrNull { it.id == s.toAccountId }
                    Choice("To", to?.name, to?.icon, to?.colorIndex, Modifier.weight(1f)) { picker = Picker.TO }
                } else {
                    Choice("Account", account?.name, account?.icon, account?.colorIndex, Modifier.weight(1f)) { picker = Picker.FROM }
                    val cat = categories.firstOrNull { it.id == s.categoryId }
                    Choice("Category", cat?.name, cat?.icon, cat?.colorIndex, Modifier.weight(1f)) { picker = Picker.CATEGORY }
                }
            }
            val budget by vm.budget.collectAsStateWithLifecycle()
            budget?.let { BudgetLine(it, settings.moneyFormat) }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                s.note, vm::setNote, placeholder = { Text("Add a note") }, maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
            AmountDisplay(s, settings, onBackspace = { vm.press(Key.Backspace) }, onClear = vm::clearAmount)
            Keypad(
                vm::press,
                pending = s.input.hasOperator,
                onSave = { scope.launch { if (vm.save()) onClose() } },
                modifier = Modifier.weight(1f).heightIn(min = 220.dp),
            )
            WhenRow(s, vm::setDate, vm::setTime)
        }
    }

    when (picker) {
        Picker.FROM -> AccountPickerSheet(accounts, s.accountId, { vm.setAccount(it); picker = null }) { picker = null }
        Picker.TO -> AccountPickerSheet(accounts.filter { it.id != s.accountId }, s.toAccountId, { vm.setToAccount(it); picker = null }) { picker = null }
        Picker.CATEGORY -> CategoryPickerSheet(
            categories.filter { it.matches(s.type) }, s.type, s.categoryId,
            onPick = { vm.setCategory(it); picker = null },
            onCreate = { vm.createCategory(it); picker = null },
            onDismiss = { picker = null },
        )
        null -> Unit
    }
}

private enum class Picker { FROM, TO, CATEGORY }

/** In-app budget warning under the category: amber near the limit, coral past it; words carry it too. */
@Composable
private fun BudgetLine(hint: BudgetHint, fmt: io.github.jameslunaa.monetra.core.money.MoneyFormat) {
    val c = MonetraTheme.colors
    val (text, color) = when (hint.level) {
        BudgetLevel.OVER -> "${fmt.format(Money(-hint.left))} over this month's budget" to c.expense
        BudgetLevel.NEAR -> "Only ${fmt.format(Money(hint.left))} left of ${fmt.format(Money(hint.limit))} this month" to c.warning
        BudgetLevel.OK -> "${fmt.format(Money(hint.left))} left of ${fmt.format(Money(hint.limit))} this month" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorHeader(title: String, type: TxType, onType: (TxType) -> Unit, onCancel: () -> Unit, onSave: () -> Unit) {
    val c = MonetraTheme.colors
    CompositionLocalProvider(LocalContentColor provides c.onField) {
        Column(Modifier.fillMaxWidth().background(c.field).statusBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, contentDescription = "Cancel") }
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onSave, colors = ButtonDefaults.textButtonColors(contentColor = c.onField)) {
                    Text("Save", style = MaterialTheme.typography.titleMedium)
                }
            }
            val options = listOf(TxType.INCOME to "Income", TxType.EXPENSE to "Expense", TxType.TRANSFER to "Transfer")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                options.forEachIndexed { i, (t, label) ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = { onType(t) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            activeBorderColor = MaterialTheme.colorScheme.primary,
                            inactiveContainerColor = c.field,
                            inactiveContentColor = c.onField,
                            inactiveBorderColor = c.onFieldVariant.copy(alpha = 0.5f),
                        ),
                    ) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun Choice(label: String, name: String?, icon: String?, color: Int?, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (name != null) IconTile(icon, color, size = 36.dp)
        else Box(
            Modifier.size(36.dp).border(1.5.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(name ?: "Choose", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (name == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AmountDisplay(s: EditorState, settings: Settings, onBackspace: () -> Unit, onClear: () -> Unit) {
    val fmt = settings.moneyFormat
    val text = s.input.text
    val color = when (s.type) {
        TxType.EXPENSE -> MonetraTheme.colors.expense
        TxType.INCOME -> MonetraTheme.colors.income
        // Refunds and loans never open in the editor; they're managed from Debts.
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FitText(
                fmt.symbol + text,
                MonetraTheme.amounts.hero.copy(fontSize = 60.sp, lineHeight = 60.sp, textAlign = TextAlign.End),
                color = color, min = 28.sp,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Amount $text" },
            )
            Box(
                Modifier.size(48.dp).clip(MaterialTheme.shapes.small)
                    .combinedClickable(onClick = onBackspace, onLongClick = onClear, onLongClickLabel = "Clear")
                    .semantics { contentDescription = "Delete last digit" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.Backspace, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        val line = s.error ?: s.input.value?.takeIf { s.input.hasOperator }?.let { "= " + fmt.format(Money.of(it)) }
        Text(
            line.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (s.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(end = 48.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhenRow(s: EditorState, onDate: (java.time.LocalDate) -> Unit, onTime: (LocalTime) -> Unit) {
    var dateOpen by remember { mutableStateOf(false) }
    var timeOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        TextButton(onClick = { dateOpen = true }, modifier = Modifier.weight(1f)) { Text(dateFormat.format(s.date), style = MaterialTheme.typography.titleSmall) }
        TextButton(onClick = { timeOpen = true }, modifier = Modifier.weight(1f)) { Text(timeFormat.format(s.time), style = MaterialTheme.typography.titleSmall) }
    }
    if (dateOpen) {
        // The date picker works in UTC midnight millis.
        val state = rememberDatePickerState(s.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { dateOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    dateOpen = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { dateOpen = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (timeOpen) {
        val state = rememberTimePickerState(s.time.hour, s.time.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { timeOpen = false },
            confirmButton = { TextButton(onClick = { onTime(LocalTime.of(state.hour, state.minute)); timeOpen = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { timeOpen = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

