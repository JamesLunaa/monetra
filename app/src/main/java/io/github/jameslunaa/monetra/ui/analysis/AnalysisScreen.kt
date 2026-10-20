package io.github.jameslunaa.monetra.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jameslunaa.monetra.core.analysis.Range
import io.github.jameslunaa.monetra.core.money.Money
import io.github.jameslunaa.monetra.core.money.MoneyFormat
import io.github.jameslunaa.monetra.core.money.SignStyle
import io.github.jameslunaa.monetra.data.db.TxType
import io.github.jameslunaa.monetra.ui.appViewModel
import io.github.jameslunaa.monetra.ui.components.FieldHeader
import io.github.jameslunaa.monetra.ui.components.FitText
import io.github.jameslunaa.monetra.ui.components.IconTile
import io.github.jameslunaa.monetra.ui.components.PeriodSwitcher
import io.github.jameslunaa.monetra.ui.components.RecordAmount
import io.github.jameslunaa.monetra.ui.components.categoryColor
import io.github.jameslunaa.monetra.ui.theme.MonetraTheme
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

@Composable
fun AnalysisScreen(onMenu: () -> Unit) {
    val vm = appViewModel { AnalysisViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return
    var customOpen by remember { mutableStateOf(false) }
    var drill by remember { mutableStateOf<CategoryLine?>(null) }
    val fmt = s.settings.moneyFormat

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FieldHeader("Analysis", onMenu) {
            RangeChips(s.range, vm::setMonths)
            // Custom ranges open from the calendar beside the period, keeping the chip row to four.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PeriodSwitcher(s.range.label(), { vm.shift(false) }, { vm.shift(true) }, vm::thisPeriod) }
                IconButton(onClick = { customOpen = true }) {
                    Icon(Icons.Rounded.DateRange, contentDescription = "Choose dates",
                        tint = if (s.range is Range.Custom) MaterialTheme.colorScheme.primary else MonetraTheme.colors.onField)
                }
            }
            LensSwitch(s.lens, vm::setLens)
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 104.dp), modifier = Modifier.fillMaxSize()) {
            when (s.lens) {
                Lens.WHERE -> where(s, fmt, vm::setKind) { drill = it }
                Lens.COMPARE -> compare(s, fmt, vm::setKind) { drill = it }
                Lens.PACE -> pace(s, fmt)
                Lens.MONTHS -> months(s, fmt)
            }
        }
    }
    if (customOpen) CustomRangeDialog(s.range, onPick = { a, b -> vm.setCustom(a, b); customOpen = false }) { customOpen = false }
    drill?.let { line -> CategorySheet(vm, line, s.range, fmt) { drill = null } }
}

// ---- Field controls --------------------------------------------------------------------------

@Composable
private fun RangeChips(range: Range, onMonths: (Int) -> Unit) {
    val c = MonetraTheme.colors
    val colors = FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent, labelColor = c.onField,
        selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    )
    val months = (range as? Range.Months)?.months
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(1 to "Month", 3 to "3 mo", 6 to "6 mo", 12 to "Year").forEach { (n, label) ->
            FilterChip(
                selected = months == n, onClick = { onMonths(n) }, label = { Text(label) }, colors = colors,
                border = FilterChipDefaults.filterChipBorder(true, months == n, borderColor = c.onFieldVariant.copy(alpha = 0.5f)),
            )
        }
    }
}

@Composable
private fun LensSwitch(lens: Lens, onLens: (Lens) -> Unit) {
    val c = MonetraTheme.colors
    val options = listOf(Lens.WHERE to "Where", Lens.COMPARE to "Compare", Lens.PACE to "Pace", Lens.MONTHS to "Months")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        options.forEachIndexed { i, (l, label) ->
            SegmentedButton(
                selected = lens == l, onClick = { onLens(l) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                    inactiveContainerColor = c.field, inactiveContentColor = c.onField,
                    inactiveBorderColor = c.onFieldVariant.copy(alpha = 0.5f),
                ),
            ) {
                // Shrinks to fit at large font scales rather than clipping mid-word.
                FitText(label, MaterialTheme.typography.labelMedium, color = androidx.compose.material3.LocalContentColor.current, min = 9.sp)
            }
        }
    }
}

// ---- Shared pieces ---------------------------------------------------------------------------

@Composable
private fun KindSwitch(kind: TxType, onKind: (TxType) -> Unit) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(kind == TxType.EXPENSE, { onKind(TxType.EXPENSE) }, label = { Text("Spending") })
        FilterChip(kind == TxType.INCOME, { onKind(TxType.INCOME) }, label = { Text("Income") })
    }
}

/**
 * Change against the previous period. Arrow plus color, never color alone. For spending, up is
 * the bad direction (coral); for income it is the good one (green).
 */
@Composable
private fun Delta(now: Long, before: Long, kind: TxType, modifier: Modifier = Modifier) {
    val c = MonetraTheme.colors
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val (text, color) = when {
        before == 0L && now == 0L -> "—" to muted
        before == 0L -> "new" to muted
        else -> {
            val pct = Math.round((now - before) * 100.0 / before).toInt()
            val good = if (kind == TxType.EXPENSE) pct < 0 else pct > 0
            when {
                pct == 0 -> "0%" to muted
                pct > 0 -> "▲ $pct%" to (if (good) c.income else c.expense)
                else -> "▼ ${-pct}%" to (if (good) c.income else c.expense)
            }
        }
    }
    Text(text, style = MonetraTheme.amounts.row.copy(fontSize = 15.sp, lineHeight = 20.sp), color = color, modifier = modifier, maxLines = 1)
}

@Composable
private fun SectionNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

private fun kindWord(k: TxType) = if (k == TxType.EXPENSE) "spending" else "income"

// ---- Lenses ----------------------------------------------------------------------------------

private fun LazyListScope.where(s: AnalysisState, fmt: MoneyFormat, onKind: (TxType) -> Unit, onOpen: (CategoryLine) -> Unit) {
    item { KindSwitch(s.kind, onKind) }
    val shown = s.lines.filter { it.now > 0 }
    if (shown.isEmpty()) { item { Empty("No ${kindWord(s.kind)} in ${s.range.label()}.") }; return }
    item {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            val colors = shown.map { categoryColor(it.category.colorIndex) }
            Donut(shown.map { it.share }, colors, MaterialTheme.colorScheme.surfaceContainerHigh, size = 220.dp, thickness = 28.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(140.dp)) {
                    Text(if (s.kind == TxType.EXPENSE) "Spent" else "Earned", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FitText(fmt.format(Money(s.total)), MonetraTheme.amounts.total.copy(fontSize = 30.sp, lineHeight = 34.sp, textAlign = TextAlign.Center), min = 16.sp)
                    Delta(s.total, s.totalBefore, s.kind)
                }
            }
        }
    }
    item { SectionNote("Change is against ${s.range.previous().label()}.") }
    items(shown, key = { it.category.id }) { line -> CategoryRow(line, fmt, s.kind) { onOpen(line) } }
}

@Composable
private fun CategoryRow(line: CategoryLine, fmt: MoneyFormat, kind: TxType, onClick: () -> Unit) {
    val color = categoryColor(line.category.colorIndex)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(line.category.icon, line.category.colorIndex)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(line.category.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Text(fmt.format(Money(line.now)), style = MonetraTheme.amounts.row.copy(fontSize = 19.sp, lineHeight = 24.sp), maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShareBar(line.share, color, Modifier.weight(1f))
                Text("${Math.round(line.share * 100)}%", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                Delta(line.now, line.before, kind, Modifier.width(64.dp))
            }
        }
    }
}

/** Bar whose filled length is the exact share. */
@Composable
private fun ShareBar(share: Float, color: Color, modifier: Modifier) {
    Box(modifier.height(6.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(3.dp))) {
        Box(Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).height(6.dp).background(color, RoundedCornerShape(3.dp)))
    }
}

private fun LazyListScope.compare(s: AnalysisState, fmt: MoneyFormat, onKind: (TxType) -> Unit, onOpen: (CategoryLine) -> Unit) {
    item { KindSwitch(s.kind, onKind) }
    if (s.lines.isEmpty()) { item { Empty("Nothing to compare in these two periods.") }; return }
    item {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(s.range.label(), style = MaterialTheme.typography.titleSmall)
                Text(fmt.format(Money(s.total)), style = MonetraTheme.amounts.total)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(s.range.previous().label(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt.format(Money(s.totalBefore)), style = MonetraTheme.amounts.total, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val max = s.lines.maxOf { maxOf(it.now, it.before) }.coerceAtLeast(1)
    items(s.lines, key = { it.category.id }) { line ->
        val color = categoryColor(line.category.colorIndex)
        Column(
            Modifier.fillMaxWidth().clickable { onOpen(line) }.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconTile(line.category.icon, line.category.colorIndex, size = 28.dp)
                Text(line.category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Delta(line.now, line.before, s.kind)
            }
            CompareBar(line.now, max, color, fmt, MaterialTheme.colorScheme.onSurface)
            CompareBar(line.before, max, color.copy(alpha = 0.4f), fmt, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompareBar(value: Long, max: Long, color: Color, fmt: MoneyFormat, ink: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).height(10.dp)) {
            Box(Modifier.fillMaxWidth((value / max.toFloat()).coerceIn(0.005f, 1f)).height(10.dp).background(color, RoundedCornerShape(3.dp)))
        }
        Text(fmt.format(Money(value)), style = MonetraTheme.amounts.row.copy(fontSize = 15.sp, lineHeight = 20.sp), color = ink,
            modifier = Modifier.width(104.dp), textAlign = TextAlign.End, maxLines = 1)
    }
}

private fun LazyListScope.pace(s: AnalysisState, fmt: MoneyFormat) {
    val sofar = s.paceNow.lastOrNull() ?: 0L
    val day = s.paceNow.size
    val sameDayBefore = s.paceBefore.getOrNull(day - 1) ?: s.paceBefore.lastOrNull() ?: 0L
    val days = s.paceBefore.size.coerceAtLeast(day)
    item {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Spent so far", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FitText(fmt.format(Money(sofar)), MonetraTheme.amounts.hero.copy(fontSize = 48.sp, lineHeight = 50.sp), Modifier.weight(1f, false), min = 24.sp)
                Delta(sofar, sameDayBefore, TxType.EXPENSE, Modifier.padding(bottom = 6.dp))
            }
            Text(
                if (day == 0) "This period hasn't started yet."
                else "After $day ${if (day == 1) "day" else "days"}. ${s.range.previous().label()} was at ${fmt.format(Money(sameDayBefore))} by the same day.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item {
        // Ink, not lamp: the lamp is reserved for actions and selection (One Lamp Rule).
        val lamp = MaterialTheme.colorScheme.onSurface
        PaceChart(
            s.paceNow, s.paceBefore, days, lamp, MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceContainerHigh,
            Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 20.dp).semantics {
                contentDescription = "Spending pace chart: ${fmt.format(Money(sofar))} so far against ${fmt.format(Money(sameDayBefore))} last period by the same day"
            },
        )
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Legend(lamp, "This period", dashed = false)
            Legend(MaterialTheme.colorScheme.onSurfaceVariant, "Previous", dashed = true)
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (dashed) repeat(2) { Box(Modifier.size(7.dp, 2.dp).background(color)) }
            else Box(Modifier.size(18.dp, 3.dp).background(color, RoundedCornerShape(2.dp)))
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val monthShort = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
private val monthLong = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

private fun LazyListScope.months(s: AnalysisState, fmt: MoneyFormat) {
    val c = s.months
    item {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            val inc = c.sumOf { it.second.income }
            val exp = c.sumOf { it.second.expense }
            // Name the span shown: a single-month range is set in its six-month context.
            Text(io.github.jameslunaa.monetra.core.analysis.Range.Months(c.size, c.last().first).label(),
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column {
                    Text("Earned", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FitText(fmt.format(Money(inc), SignStyle.ALWAYS), MonetraTheme.amounts.total, color = MonetraTheme.colors.income)
                }
                Column {
                    Text("Spent", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FitText(fmt.format(-Money(exp)), MonetraTheme.amounts.total, color = MonetraTheme.colors.expense)
                }
            }
        }
    }
    item {
        PairBars(
            c.map { it.second.income to it.second.expense }, MonetraTheme.colors.income, MonetraTheme.colors.expense,
            highlight = c.lastIndex, dim = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 20.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            c.forEach { (m, _) ->
                Text(monthShort.format(m).take(if (c.size > 6) 1 else 3), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
    }
    items(c.reversed(), key = { it.first.toString() }) { (m, f) ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(monthLong.format(m), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(fmt.format(Money(f.income - f.expense), SignStyle.ALWAYS), style = MonetraTheme.amounts.row.copy(fontSize = 19.sp, lineHeight = 24.sp),
                    color = if (f.income < f.expense) MonetraTheme.colors.expense else MaterialTheme.colorScheme.onSurface)
                Text("+${fmt.format(Money(f.income))} · −${fmt.format(Money(f.expense))}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---- Dialogs and sheets ----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(current: Range, onPick: (java.time.LocalDate, java.time.LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = current.start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        initialSelectedEndDateMillis = current.endExclusive.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                onClick = { onPick(day(state.selectedStartDateMillis!!), day(state.selectedEndDateMillis!!)) },
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DateRangePicker(state, Modifier.weight(1f)) }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(vm: AnalysisViewModel, line: CategoryLine, range: Range, fmt: MoneyFormat, onDismiss: () -> Unit) {
    val records by remember(line.category.id, range) { vm.recordsOf(line.category.id, range) }.collectAsStateWithLifecycle(emptyList())
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).background(categoryColor(line.category.colorIndex), MaterialTheme.shapes.large).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(line.category.name, style = MaterialTheme.typography.titleLarge, color = MonetraTheme.colors.onCategory,
                    modifier = Modifier.semantics { heading() })
                Text(range.label(), style = MaterialTheme.typography.bodyMedium, color = MonetraTheme.colors.onCategory)
            }
            Text(fmt.format(Money(line.now)), style = MonetraTheme.amounts.total, color = MonetraTheme.colors.onCategory)
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            items(records, key = { it.id }) { r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(dayFormat.format(Instant.ofEpochMilli(r.occurredAt).atZone(java.time.ZoneId.systemDefault())),
                            style = MaterialTheme.typography.bodyLarge)
                        Text(listOf(r.accountName, r.note.trim()).filter { it.isNotEmpty() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    RecordAmount(r.type, r.amount, fmt, MonetraTheme.amounts.row)
                }
            }
            if (records.isEmpty()) item { Empty("No records in this range.") }
        }
    }
}
