package io.github.jameslunaa.monetra.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jameslunaa.monetra.core.budget.Allowance
import io.github.jameslunaa.monetra.core.budget.BudgetLevel
import io.github.jameslunaa.monetra.core.budget.Budgets
import io.github.jameslunaa.monetra.data.db.BudgetCadence
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import io.github.jameslunaa.monetra.core.money.Money
import io.github.jameslunaa.monetra.core.money.MoneyFormat
import io.github.jameslunaa.monetra.data.db.CategoryEntity
import io.github.jameslunaa.monetra.ui.appViewModel
import io.github.jameslunaa.monetra.ui.components.FieldHeader
import io.github.jameslunaa.monetra.ui.components.FieldStat
import io.github.jameslunaa.monetra.ui.components.FitText
import io.github.jameslunaa.monetra.ui.components.IconTile
import io.github.jameslunaa.monetra.ui.components.PeriodSwitcher
import io.github.jameslunaa.monetra.ui.theme.MonetraTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val monthShort = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())

@Composable
fun BudgetsScreen(onMenu: () -> Unit) {
    val vm = appViewModel { BudgetsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    var editing by remember { mutableStateOf<Pair<CategoryEntity, BudgetLine?>?>(null) }
    val fmt = s.settings.moneyFormat

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Header(s, fmt, vm, onMenu)
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 104.dp)) {
            if (s.lines.isEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No budgets yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Give a category a monthly limit and Monetra turns it into what you can spend each day. " +
                                "Limits repeat every month until you change them.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Everyday budgets lead; fixed monthly costs follow in their own section.
            val split = s.everyday.isNotEmpty() && s.monthly.isNotEmpty()
            if (split) item { Heading("Everyday") }
            items(s.everyday, key = { it.category.id }) { line ->
                BudgetRow(line, s.elapsed, fmt) { editing = line.category to line }
            }
            if (s.monthly.isNotEmpty()) {
                item {
                    Heading(
                        "Monthly",
                        "${fmt.format(Money(s.monthly.sumOf { minOf(it.spent, it.limit) }))} of ${fmt.format(Money(s.monthly.sumOf { it.limit }))} paid",
                    )
                }
                items(s.monthly, key = { it.category.id }) { line ->
                    BudgetRow(line, elapsed = -1f, fmt) { editing = line.category to line }
                }
            }
            if (s.unbudgeted.isNotEmpty()) {
                item { Heading("Not budgeted") }
                items(s.unbudgeted, key = { "u" + it.id }) { cat ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = cat to null }.heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        IconTile(cat.icon, cat.colorIndex, size = 32.dp)
                        Text(cat.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("Set limit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    editing?.let { (cat, line) ->
        LimitDialog(cat, line, s.month, fmt, onSave = { amount, cadence -> vm.setLimit(cat.id, amount, cadence); editing = null }) { editing = null }
    }
}

@Composable
private fun Header(s: BudgetsState, fmt: MoneyFormat, vm: BudgetsViewModel, onMenu: () -> Unit) {
    val c = MonetraTheme.colors
    val amounts = MonetraTheme.amounts
    FieldHeader("Budgets", onMenu) {
        PeriodSwitcher(monthFormat.format(s.month), { vm.shift(false) }, { vm.shift(true) }, vm::thisMonth)
        if (s.lines.isEmpty()) return@FieldHeader
        val days = s.daysLeft
        // The hero is everyday money only; with only monthly budgets it shows what's left to pay.
        val group = s.everyday.ifEmpty { s.monthly }
        val left = s.left(group)
        if (days > 0 && s.everyday.isNotEmpty()) {
            Row(verticalAlignment = Alignment.Bottom) {
                FitText(fmt.format(Money(left / days)), amounts.hero, Modifier.weight(1f, false), color = c.onField, min = 32.sp)
                Text(" a day", style = amounts.total, color = c.onField, modifier = Modifier.padding(bottom = 8.dp))
            }
        } else {
            FitText(fmt.format(Money(left)) + " left", amounts.hero, color = c.onField, min = 32.sp)
        }
        Spacer(Modifier.height(12.dp))
        // Equal columns that shrink to fit, so three or four stats never overflow the field.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1.4f)) { FieldStat("Left") { FitText(fmt.format(Money(left)), amounts.total) } }
            Box(Modifier.weight(1.4f)) { FieldStat("Budget") { FitText(fmt.format(Money(group.sumOf { it.limit })), amounts.total) } }
            if (days > 0) Box(Modifier.weight(1f)) { FieldStat("Days") { FitText("$days", amounts.total) } }
            if (s.overCount > 0) Box(Modifier.weight(0.8f)) { FieldStat("Over") { FitText("${s.overCount}", amounts.total, color = c.fieldExpense) } }
        }
        // Say what the figures cover once monthly bills are kept out of them.
        if (s.everyday.isNotEmpty() && s.monthly.isNotEmpty()) {
            Text("Everyday budgets only. Monthly bills are listed below.", style = MaterialTheme.typography.bodySmall,
                color = c.onFieldVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun Heading(title: String, detail: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BudgetRow(line: BudgetLine, elapsed: Float, fmt: MoneyFormat, onClick: () -> Unit) {
    val c = MonetraTheme.colors
    val levelColor = when {
        line.level == BudgetLevel.OVER -> c.expense
        // A bill reaching its limit is expected, not a warning.
        line.level == BudgetLevel.NEAR && line.cadence != BudgetCadence.MONTHLY -> c.warning
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 72.dp).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(line.category.icon, line.category.colorIndex)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(line.category.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                val (text, color) = when (val a = line.allowance) {
                    is Allowance.PerDay -> "${fmt.format(Money(a.perDay))}/day" to (if (line.level == BudgetLevel.NEAR) c.warning else MaterialTheme.colorScheme.onSurface)
                    is Allowance.PerWeek -> "${fmt.format(Money(a.perWeek))}/week" to (if (line.level == BudgetLevel.NEAR) c.warning else MaterialTheme.colorScheme.onSurface)
                    is Allowance.MonthLeft -> "${fmt.format(Money(a.left))} left" to MaterialTheme.colorScheme.onSurface
                    Allowance.PaidInFull -> "Paid in full" to MaterialTheme.colorScheme.onSurface
                    is Allowance.Left -> "${fmt.format(Money(a.left))} left" to MaterialTheme.colorScheme.onSurface
                    is Allowance.Over -> "${fmt.format(Money(a.over))} over" to c.expense
                }
                Text(text, style = MonetraTheme.amounts.row.copy(fontSize = 19.sp, lineHeight = 24.sp), color = color, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProgressWithToday(line.spent / line.limit.toFloat(), elapsed, levelColor, Modifier.weight(1f))
                Text("${fmt.format(Money(line.spent))} of ${fmt.format(Money(line.limit))}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Spent share of the limit, with a tick where today falls in the month (spending at the tick = on pace). */
@Composable
private fun ProgressWithToday(fraction: Float, today: Float, color: Color, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    BoxWithConstraints(modifier.height(12.dp)) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(6.dp).background(track, RoundedCornerShape(3.dp)))
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(color, RoundedCornerShape(3.dp)))
        if (today in 0.01f..0.99f) {
            Box(Modifier.offset(x = maxWidth * today - 1.dp).width(2.dp).fillMaxHeight().background(tick, RoundedCornerShape(1.dp)))
        }
    }
}

@Composable
private fun LimitDialog(
    category: CategoryEntity,
    line: BudgetLine?,
    month: YearMonth,
    fmt: MoneyFormat,
    onSave: (Long, BudgetCadence) -> Unit,
    onDismiss: () -> Unit,
) {
    var cadence by remember { mutableStateOf(line?.cadence ?: Budgets.defaultCadence(category.icon)) }
    var text by remember { mutableStateOf(line?.limit?.let { Money(it).toBigDecimal().stripTrailingZeros().toPlainString() }.orEmpty()) }
    val parsed = Money.parse(text)?.takeIf { it.minor > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { IconTile(category.icon, category.colorIndex) },
        title = { Text(if (line == null) "Budget for ${category.name}" else category.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    text, { text = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) },
                    label = { Text("Monthly limit") }, prefix = { Text(fmt.symbol) }, singleLine = true,
                    isError = text.isNotEmpty() && parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text("Show what's left", style = MaterialTheme.typography.titleSmall)
                val options = listOf(BudgetCadence.DAILY to "Per day", BudgetCadence.WEEKLY to "Per week", BudgetCadence.MONTHLY to "Monthly")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, (cd, label) ->
                        SegmentedButton(
                            selected = cadence == cd, onClick = { cadence = cd }, icon = {},
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary,
                                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                activeBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) { FitText(label, MaterialTheme.typography.labelLarge, color = androidx.compose.material3.LocalContentColor.current, min = 10.sp) }
                    }
                }
                Text(
                    when (cadence) {
                        BudgetCadence.DAILY -> "For everyday spending like food and fares."
                        BudgetCadence.WEEKLY -> "For things bought a few times a week, like groceries."
                        BudgetCadence.MONTHLY -> "For bills and rent paid once a month. Kept out of the daily figure."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Applies from ${monthShort.format(month)} and repeats every month until you change it." +
                        (line?.let { if (it.since != month) " Current limit since ${monthShort.format(it.since)}." else "" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = { onSave(parsed!!.minor, cadence) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (line != null) {
                    TextButton(onClick = { onSave(0, cadence) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
