package com.example.monetra.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.monetra.core.money.Money
import com.example.monetra.core.analysis.Analysis
import com.example.monetra.core.time.Period
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.monetra.data.db.AccountBalance
import com.example.monetra.data.db.AccountFlow
import com.example.monetra.data.db.AccountEntity
import com.example.monetra.data.db.Totals
import com.example.monetra.data.prefs.Settings
import com.example.monetra.di.AppContainer
import com.example.monetra.ui.appViewModel
import com.example.monetra.ui.components.FieldHeader
import com.example.monetra.ui.components.FieldStat
import com.example.monetra.ui.components.FitText
import com.example.monetra.ui.components.IconTile
import com.example.monetra.ui.components.RowMenu
import com.example.monetra.ui.components.SignedAmount
import com.example.monetra.ui.theme.MonetraTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountsState(
    val balances: List<AccountBalance>,
    val allTime: Totals,
    val settings: Settings,
    val period: Period,
    val flows: Map<Long, AccountFlow>,
    /** All accounts' total at each of the last six month-ends. */
    val history: List<Pair<YearMonth, Long>> = emptyList(),
    val owedToMe: Long = 0,
    val iOwe: Long = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModel(private val c: AppContainer) : ViewModel() {
    val state = c.periods.period.flatMapLatest { period ->
        combine(
            c.accounts.balances, c.transactions.allTimeTotals(), c.settings.settings, c.transactions.accountFlows(period),
            c.transactions.points(Long.MIN_VALUE, Long.MAX_VALUE),
        ) { b, t, s, f, pts ->
            val now = YearMonth.now()
            AccountsState(
                b, t, s, period, f.associateBy { it.accountId },
                Analysis.closingBalances(pts, b.sumOf { it.initialBalance }, now.minusMonths(5), now),
            )
        }
    }.combine(c.debts.debts) { s, debts ->
        val open = debts.filter { !it.settled }
        s.copy(
            owedToMe = open.filter { it.direction == com.example.monetra.data.db.DebtDirection.OWED_TO_ME }.sumOf { it.remaining },
            iOwe = open.filter { it.direction == com.example.monetra.data.db.DebtDirection.I_OWE }.sumOf { it.remaining },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun get(id: Long) = c.accounts.get(id)
    fun save(a: AccountEntity) = viewModelScope.launch { c.accounts.save(a) }

    /** Returns true if deleted, false if archived because it has records. */
    suspend fun delete(id: Long): Boolean? = c.accounts.get(id)?.let { c.accounts.deleteOrArchive(it) }
}

/** Open debts in one row; balances above don't include money still owed. */
@Composable
private fun DebtSummary(owedToMe: Long, iOwe: Long, fmt: com.example.monetra.core.money.MoneyFormat, onClick: () -> Unit) {
    val c = MonetraTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        com.example.monetra.ui.components.TransferTile(size = 48.dp, icon = com.example.monetra.ui.components.AppIcons["debt"])
        Column(Modifier.weight(1f)) {
            Text("Debts", style = MaterialTheme.typography.titleMedium)
            Text("Not in balances", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        // Same amount column as the ledger: one signed net, words beneath.
        val net = owedToMe - iOwe
        Column(horizontalAlignment = Alignment.End) {
            Text(fmt.format(Money(net), com.example.monetra.core.money.SignStyle.ALWAYS), style = MonetraTheme.amounts.row,
                color = when { net > 0 -> c.income; net < 0 -> c.expense; else -> MaterialTheme.colorScheme.onSurface }, maxLines = 1)
            Text(if (net >= 0) "owed to you" else "you owe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Occupies the same 48dp slot as the account rows' menu, so amounts share one right edge.
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val monthShort = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

/** Month-end totals as bars on one zero baseline; negative months drop below it in coral. */
@Composable
private fun BalanceHistory(history: List<Pair<YearMonth, Long>>, fmt: com.example.monetra.core.money.MoneyFormat, modifier: Modifier = Modifier) {
    val c = MonetraTheme.colors
    // Ink, not lamp: data marks don't borrow the action colour (One Lamp Rule).
    val lamp = MaterialTheme.colorScheme.onSurface
    val past = MaterialTheme.colorScheme.outline
    val dim = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Balance by month", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text("All accounts at the end of each month", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        val max = history.maxOf { kotlin.math.abs(it.second) }.coerceAtLeast(1)
        val hasNegative = history.any { it.second < 0 }
        Canvas(Modifier.fillMaxWidth().weight(1f).heightIn(min = 140.dp).semantics {
            contentDescription = "Balance by month: " + history.joinToString { (m, v) -> monthShort.format(m) + " " + fmt.format(Money(v)) }
        }) {
            val slot = size.width / history.size
            val bar = (slot * 0.5f).coerceAtMost(28.dp.toPx())
            val zero = if (hasNegative) size.height / 2 else size.height
            val scale = (if (hasNegative) size.height / 2 else size.height) / max
            history.forEachIndexed { i, (_, v) ->
                val h = kotlin.math.abs(v) * scale
                val left = slot * i + (slot - bar) / 2
                // Past months sit on the dusk tone of the stall ladder; the current month is ink.
                val color = if (v < 0) c.expense else if (i == history.lastIndex) lamp else past
                drawRoundRect(color, Offset(left, if (v < 0) zero else zero - h), Size(bar, h.coerceAtLeast(2f)), CornerRadius(4.dp.toPx()))
            }
            drawLine(dim, Offset(0f, zero), Offset(size.width, zero), strokeWidth = 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            history.forEach { (m, v) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(monthShort.format(m), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FitText(compact(v, fmt), MonetraTheme.amounts.row.copy(fontSize = 13.sp, lineHeight = 16.sp, textAlign = TextAlign.Center),
                        color = if (v < 0) c.expense else MaterialTheme.colorScheme.onSurface, min = 9.sp)
                }
            }
        }
    }
}

/** "₱5.5k" style for chart labels; full amounts stay in the rows above. */
private fun compact(v: Long, fmt: com.example.monetra.core.money.MoneyFormat): String {
    val abs = kotlin.math.abs(v) / 100.0
    val sign = if (v < 0) "−" else ""
    return sign + fmt.symbol + when {
        abs >= 1_000_000 -> "%.1fM".format(Locale.US, abs / 1_000_000)
        abs >= 1_000 -> "%.1fk".format(Locale.US, abs / 1_000)
        else -> "%.0f".format(Locale.US, abs)
    }
}

@Composable
private fun FlowFigure(label: String, value: String, color: androidx.compose.ui.graphics.Color, style: androidx.compose.ui.text.TextStyle, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        FitText(value, style, color = color)
    }
}

@Composable
fun AccountsScreen(contentPadding: PaddingValues, snackbar: SnackbarHostState, onMenu: () -> Unit, onOpenDebts: () -> Unit) {
    val vm = appViewModel { AccountsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<AccountEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    val fmt = s.settings.moneyFormat
    val amounts = MonetraTheme.amounts
    val c = MonetraTheme.colors

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FieldHeader("Accounts", onMenu) {
            SignedAmount(Money(s.balances.sumOf { it.balance }), fmt, amounts.hero, positive = c.onField, negative = c.fieldExpense)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                FieldStat("Spent") { FitText(fmt.format(Money(s.allTime.expense)), amounts.total) }
                FieldStat("Earned") { FitText(fmt.format(Money(s.allTime.income)), amounts.total) }
            }
        }
        // No FAB on this tab, so no space is reserved for one.
        LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = contentPadding.calculateBottomPadding() + 8.dp)) {
            items(s.balances, key = { it.id }) { a ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconTile(a.icon, a.colorIndex, size = 48.dp)
                    // Name and balance share the first line; the period's flow wraps freely below,
                    // so nothing breaks mid-word at large font scales.
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            SignedAmount(Money(a.balance), fmt, amounts.row)
                        }
                        val flow = s.flows[a.id]
                        if (flow == null) {
                            Text("Nothing in ${s.period.label()}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            // Spent and earned this period, as one labelled pair in fixed columns.
                            val small = amounts.row.copy(fontSize = 17.sp, lineHeight = 22.sp)
                            Text(s.period.label(), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FlowFigure("Spent", "−" + fmt.format(Money(flow.expense)), MonetraTheme.colors.expense, small, Modifier.weight(1f))
                                FlowFigure("Earned", "+" + fmt.format(Money(flow.income)), MonetraTheme.colors.income, small, Modifier.weight(1f))
                            }
                        }
                    }
                    RowMenu("Options for ${a.name}", listOf(
                        "Edit" to { scope.launch { editing = vm.get(a.id) } },
                        "Delete" to {
                            scope.launch {
                                val deleted = vm.delete(a.id) ?: return@launch
                                snackbar.showSnackbar(if (deleted) "${a.name} deleted" else "${a.name} has records, so it was archived instead")
                            }
                        },
                    ))
                }
            }
            if (s.owedToMe > 0 || s.iOwe > 0) item { DebtSummary(s.owedToMe, s.iOwe, fmt, onOpenDebts) }
            item {
                OutlinedButton(onClick = { creating = true }, modifier = Modifier.padding(20.dp)) {
                    Icon(Icons.Rounded.Add, null); Text("  Add account")
                }
            }
            // The chart takes the rest of the tab instead of leaving dead ground under it.
            if (s.history.isNotEmpty()) item { BalanceHistory(s.history, fmt, Modifier.fillParentMaxHeight(0.62f).heightIn(min = 260.dp)) }
        }
    }
    if (creating || editing != null) {
        AccountEditorDialog(
            initial = editing,
            onSave = { vm.save(it); creating = false; editing = null },
            onDismiss = { creating = false; editing = null },
        )
    }
}
