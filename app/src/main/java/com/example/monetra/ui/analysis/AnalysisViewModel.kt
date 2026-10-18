package com.example.monetra.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monetra.core.analysis.Analysis
import com.example.monetra.core.analysis.CategoryChange
import com.example.monetra.core.analysis.Flow
import com.example.monetra.core.analysis.Range
import com.example.monetra.data.db.AmountPoint
import com.example.monetra.data.db.CategoryEntity
import com.example.monetra.data.db.RecordRow
import com.example.monetra.data.db.TxType
import com.example.monetra.data.prefs.Settings
import com.example.monetra.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class Lens { WHERE, COMPARE, PACE, MONTHS }

data class CategoryLine(
    val category: CategoryEntity,
    val now: Long,
    val before: Long,
    /** Share of the period's total, 0..1. */
    val share: Float,
) {
    val percent: Int? get() = CategoryChange(category.id, now, before).percent
}

data class AnalysisState(
    val range: Range,
    val lens: Lens,
    val kind: TxType,
    val settings: Settings,
    val total: Long,
    val totalBefore: Long,
    val lines: List<CategoryLine>,
    /** Running total by day for this range and the previous one (same day index). */
    val paceNow: List<Long>,
    val paceBefore: List<Long>,
    val months: List<Pair<YearMonth, Flow>>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModel(private val c: AppContainer) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val range = MutableStateFlow<Range>(Range.Months(1, YearMonth.now()))
    private val lens = MutableStateFlow(Lens.WHERE)
    private val kind = MutableStateFlow(TxType.EXPENSE)

    val state: StateFlow<AnalysisState?> = combine(range, lens, kind, ::Triple).flatMapLatest { (r, l, k) ->
        val prev = r.previous()
        // The Months lens covers the range's own months; a single month (or a short custom span) gets the six months up to it.
        val lastMonth = YearMonth.from(r.endExclusive.minusDays(1))
        val ownFirst = YearMonth.from(r.start)
        val monthsFirst = if (java.time.temporal.ChronoUnit.MONTHS.between(ownFirst, lastMonth) >= 2) ownFirst else lastMonth.minusMonths(5)
        val from = minOf(prev.start, monthsFirst.atDay(1))
        combine(c.transactions.points(millis(from), millis(r.endExclusive)), c.categories.categories, c.settings.settings) { pts, cats, s ->
            build(r, l, k, prev, monthsFirst, pts, cats, s)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun millis(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun build(
        r: Range, l: Lens, k: TxType, prev: Range, monthsFirst: YearMonth,
        pts: List<AmountPoint>, cats: List<CategoryEntity>, s: Settings,
    ): AnalysisState {
        fun inRange(x: Range) = pts.filter { it.occurredAt >= millis(x.start) && it.occurredAt < millis(x.endExclusive) }
        val now = inRange(r).filter { it.type == k }
        val before = inRange(prev).filter { it.type == k }
        val byCat = cats.associateBy { it.id }
        fun totals(l: List<AmountPoint>) = l.filter { it.categoryId != null }.groupBy { it.categoryId!! }.mapValues { (_, v) -> v.sumOf { it.amount } }
        val n = totals(now)
        val b = totals(before)
        val total = now.sumOf { it.amount }
        val lines = (n.keys + b.keys).mapNotNull { id ->
            byCat[id]?.let { CategoryLine(it, n[id] ?: 0, b[id] ?: 0, if (total == 0L) 0f else (n[id] ?: 0) / total.toFloat()) }
        }.sortedWith(compareByDescending<CategoryLine> { it.now }.thenByDescending { it.before })
        val expenseNow = inRange(r).filter { it.type == TxType.EXPENSE }
        val expenseBefore = inRange(prev).filter { it.type == TxType.EXPENSE }
        return AnalysisState(
            range = r, lens = l, kind = k, settings = s,
            total = total, totalBefore = before.sumOf { it.amount }, lines = lines,
            paceNow = Analysis.cumulativeExpense(Analysis.byDay(expenseNow, r.start, r.endExclusive, zone))
                .take(daysElapsed(r)),
            paceBefore = Analysis.cumulativeExpense(Analysis.byDay(expenseBefore, prev.start, prev.endExclusive, zone)),
            months = Analysis.byMonth(pts.filter { it.occurredAt < millis(r.endExclusive) }, monthsFirst,
                YearMonth.from(r.endExclusive.minusDays(1)), zone),
        )
    }

    /** Days of the range that have happened, so the pace line stops at today. */
    private fun daysElapsed(r: Range): Int {
        val today = LocalDate.now(zone)
        val end = if (today.isBefore(r.endExclusive)) today.plusDays(1) else r.endExclusive
        return java.time.temporal.ChronoUnit.DAYS.between(r.start, end).toInt().coerceAtLeast(0)
    }

    fun setMonths(n: Int) = range.update { cur ->
        val end = (cur as? Range.Months)?.end ?: YearMonth.from(cur.endExclusive.minusDays(1))
        Range.Months(n, end)
    }
    fun setCustom(start: LocalDate, last: LocalDate) { range.value = Range.Custom(start, last) }
    fun shift(forward: Boolean) = range.update { it.shift(forward) }
    fun thisPeriod() = range.update { if (it is Range.Months) Range.Months(it.months, YearMonth.now()) else it }
    fun setLens(l: Lens) { lens.value = l }
    fun setKind(k: TxType) { kind.value = k }

    /** Records of one category in the current range, for the drill-down sheet. */
    fun recordsOf(categoryId: Long, r: Range): kotlinx.coroutines.flow.Flow<List<RecordRow>> =
        c.transactions.recordsBetween(millis(r.start), millis(r.endExclusive)).map { l -> l.filter { it.categoryId == categoryId } }
}
