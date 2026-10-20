package io.github.jameslunaa.monetra.ui.records

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jameslunaa.monetra.core.money.Money
import io.github.jameslunaa.monetra.core.money.MoneyFormat
import io.github.jameslunaa.monetra.core.money.SignStyle
import io.github.jameslunaa.monetra.data.db.RecordRow
import io.github.jameslunaa.monetra.data.db.TxType
import io.github.jameslunaa.monetra.ui.appViewModel
import io.github.jameslunaa.monetra.ui.components.FieldHeader
import io.github.jameslunaa.monetra.ui.components.FieldStat
import io.github.jameslunaa.monetra.ui.components.FitText
import io.github.jameslunaa.monetra.ui.components.HeroFigure
import io.github.jameslunaa.monetra.ui.components.IconTile
import io.github.jameslunaa.monetra.ui.components.PeriodSwitcher
import io.github.jameslunaa.monetra.ui.components.RecordAmount
import io.github.jameslunaa.monetra.ui.components.SignedAmount
import io.github.jameslunaa.monetra.ui.components.TransferTile
import io.github.jameslunaa.monetra.ui.theme.MonetraTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

@Composable
fun RecordsScreen(
    contentPadding: PaddingValues,
    snackbar: SnackbarHostState,
    onMenu: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onImport: () -> Unit,
    onSearch: () -> Unit,
    onOpenDebts: () -> Unit,
) {
    val vm = appViewModel { RecordsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state != null) { if (state != null) vm.markReady() }
    val s = state ?: return
    val scope = rememberCoroutineScope()
    var openId by remember { mutableLongStateOf(0L) }
    var showOptions by remember { mutableStateOf(false) }
    var splitting by remember { mutableStateOf<RecordRow?>(null) }
    val people by vm.people.collectAsStateWithLifecycle()
    val fmt = s.settings.moneyFormat

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        RecordsHeader(s, vm, onMenu, onSearch, onOptions = { showOptions = true })
        if (s.days.isEmpty()) {
            EmptyPeriod(s.period.label(), s.hasAnyRecords, onImport, Modifier.weight(1f).padding(contentPadding))
        } else {
            DayList(s.days, fmt, s.settings.notesPreview, onOpen = { openId = it }, contentPadding)
        }
    }

    s.days.asSequence().flatMap { it.rows }.firstOrNull { it.id == openId }?.let { row ->
        RecordSheet(
            row, fmt,
            onDismiss = { openId = 0 },
            onEdit = { openId = 0; onEdit(row.id) },
            onDuplicate = { openId = 0; onDuplicate(row.id) },
            onSplit = { openId = 0; splitting = row },
            onOpenDebts = { openId = 0; onOpenDebts() },
            onDelete = {
                openId = 0
                scope.launch {
                    val removed = vm.delete(row.id) ?: return@launch
                    val r = snackbar.showSnackbar("Record deleted", actionLabel = "Undo", duration = SnackbarDuration.Long)
                    if (r == SnackbarResult.ActionPerformed) vm.restore(removed)
                }
            },
        )
    }
    splitting?.let { row ->
        io.github.jameslunaa.monetra.ui.debts.SplitDialog(
            row.amount, row.categoryName ?: "this", people, fmt,
            onConfirm = { name, amount, note ->
                vm.split(row.id, name, amount, note); splitting = null
                scope.launch { snackbar.showSnackbar("$name owes you ${fmt.format(Money(amount))}") }
            },
            onDismiss = { splitting = null },
        )
    }
    if (showOptions) {
        DisplayOptionsSheet(s.settings, vm::setPeriodType, vm::setShowTotal, vm::setCarryOver, onDismiss = { showOptions = false })
    }
}

@Composable
private fun RecordsHeader(s: RecordsState, vm: RecordsViewModel, onMenu: () -> Unit, onSearch: () -> Unit, onOptions: () -> Unit) {
    val fmt = s.settings.moneyFormat
    val amounts = MonetraTheme.amounts
    val c = MonetraTheme.colors
    val net = Money(s.totals.net + (s.carried ?: 0))
    FieldHeader(
        title = "Monetra",
        onMenu = onMenu,
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, contentDescription = "Search records") }
            IconButton(onClick = onOptions) { Icon(Icons.Rounded.Tune, contentDescription = "Display options") }
        },
    ) {
        PeriodSwitcher(s.period.label(), { vm.previous(s.period) }, { vm.next(s.period) }, vm::today)
        val heroText = if (s.settings.showTotal) fmt.format(net, SignStyle.ALWAYS) else fmt.format(Money(s.totals.expense))
        HeroFigure(
            key = s.period to heroText,
            forward = vm.forward,
            description = (if (s.settings.showTotal) "Net " else "Spent ") + heroText,
        ) { (_, text) ->
            FitText(text, amounts.hero, color = if (s.settings.showTotal && net.isNegative) c.fieldExpense else c.onField, min = 32.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.fillMaxWidth()) {
            if (s.settings.showTotal) {
                FieldStat("Spent") { FitText(fmt.format(Money(s.totals.expense)), amounts.total) }
            }
            FieldStat("Earned") { FitText(fmt.format(Money(s.totals.income)), amounts.total) }
            s.carried?.let { carried ->
                FieldStat("Carried over") { SignedAmount(Money(carried), fmt, amounts.total, positive = c.onField, negative = c.fieldExpense) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayList(
    days: List<DayGroup>,
    fmt: MoneyFormat,
    notesPreview: Boolean,
    onOpen: (Long) -> Unit,
    contentPadding: PaddingValues,
) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    // A new record lands at the top of the list (and so does a new period): show it instead of leaving it above the fold.
    val newest = days.firstOrNull()?.rows?.firstOrNull()?.id
    androidx.compose.runtime.LaunchedEffect(newest) { state.animateScrollToItem(0) }
    LazyColumn(state = state, contentPadding = PaddingValues(top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 88.dp)) {
        days.forEach { day ->
            stickyHeader(key = "d" + day.date) { DayHeader(day, fmt) }
            items(day.rows, key = { it.id }) { RecordRowItem(it, fmt, notesPreview, onOpen) }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

@Composable
private fun DayHeader(day: DayGroup, fmt: MoneyFormat) {
    val today = LocalDate.now()
    val weekday = when (day.date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.date.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.getDefault())
    }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(dayFormat.format(day.date), style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
        Text("  $weekday", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(fmt.format(Money(day.net), SignStyle.ALWAYS), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
}

@Composable
internal fun RecordRowItem(row: RecordRow, fmt: MoneyFormat, notesPreview: Boolean, onOpen: (Long) -> Unit, showDate: Boolean = false) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(row.id) }.heightIn(min = 64.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RecordTile(row)
        Column(Modifier.weight(1f)) {
            Text(recordTitle(row), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildString {
                if (showDate) append(rowDate.format(java.time.Instant.ofEpochMilli(row.occurredAt).atZone(java.time.ZoneId.systemDefault()))).append(" · ")
                append(accountLine(row))
                if (notesPreview && row.note.isNotBlank()) append(" · ").append(row.note.trim())
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RecordAmount(row.type, row.amount, fmt, MonetraTheme.amounts.row)
    }
}

private val rowDate = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

/** Category tile, or a neutral tile for transfers (arrows) and loans (handshake). */
@Composable
internal fun RecordTile(row: RecordRow, size: androidx.compose.ui.unit.Dp = 40.dp) = when {
    row.type == TxType.TRANSFER -> TransferTile(size = size)
    row.categoryId == null -> TransferTile(size = size, icon = io.github.jameslunaa.monetra.ui.components.AppIcons["debt"])
    else -> IconTile(row.categoryIcon, row.categoryColor, size = size)
}

internal fun recordTitle(row: RecordRow): String {
    val who = row.personName ?: "someone"
    val owedToMe = row.debtDirection == io.github.jameslunaa.monetra.data.db.DebtDirection.OWED_TO_ME
    return when (row.type) {
        TxType.TRANSFER -> "Transfer"
        TxType.REFUND -> "$who paid back"
        TxType.DEBT_OUT -> if (owedToMe) "Lent to $who" else "Paid $who back"
        TxType.DEBT_IN -> if (owedToMe) "$who paid back" else "Borrowed from $who"
        else -> row.categoryName ?: "Uncategorized"
    }
}

internal fun accountLine(row: RecordRow) =
    if (row.type == TxType.TRANSFER) "${row.accountName} → ${row.toAccountName}" else row.accountName

@Composable
private fun EmptyPeriod(label: String, hasAny: Boolean, onImport: () -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (hasAny) "Nothing recorded in $label" else "Your first record goes here",
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                if (hasAny) "Switch periods with the arrows, or tap + to add a record."
                else "Tap + to log what you spend, or import past records from a CSV file.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            // The FAB is the add action; the empty state only offers what the FAB can't.
            if (!hasAny) {
                Spacer(Modifier.height(4.dp))
                Button(onClick = onImport) { Text("Import CSV") }
            }
        }
    }
}
