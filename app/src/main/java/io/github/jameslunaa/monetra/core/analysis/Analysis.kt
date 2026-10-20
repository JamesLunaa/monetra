package io.github.jameslunaa.monetra.core.analysis

import io.github.jameslunaa.monetra.data.db.AmountPoint
import io.github.jameslunaa.monetra.data.db.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Income and expense for one bucket (a day or a month), in minor units. */
data class Flow(val income: Long = 0, val expense: Long = 0) {
    operator fun plus(o: Flow) = Flow(income + o.income, expense + o.expense)
}

/** A category's total now and in the comparison period. */
data class CategoryChange(val categoryId: Long, val now: Long, val before: Long) {
    /** Change in percent, or null when there was nothing before ("new"). */
    val percent: Int? get() = if (before == 0L) null else Math.round((now - before) * 100.0 / before).toInt()
}

object Analysis {
    private fun day(at: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

    private fun AmountPoint.flow() = if (type == TxType.INCOME) Flow(income = amount) else Flow(expense = amount)

    /** Totals per calendar month for every month from [first] to [last], empty months included. */
    fun byMonth(points: List<AmountPoint>, first: YearMonth, last: YearMonth, zone: ZoneId = ZoneId.systemDefault()): List<Pair<YearMonth, Flow>> {
        val sums = points.groupBy { YearMonth.from(day(it.occurredAt, zone)) }
            .mapValues { (_, l) -> l.fold(Flow()) { acc, p -> acc + p.flow() } }
        return generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }
            .map { it to (sums[it] ?: Flow()) }.toList()
    }

    /** Totals per day from [start] (inclusive) to [endExclusive], empty days included. */
    fun byDay(points: List<AmountPoint>, start: LocalDate, endExclusive: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, Flow>> {
        val sums = points.groupBy { day(it.occurredAt, zone) }
            .mapValues { (_, l) -> l.fold(Flow()) { acc, p -> acc + p.flow() } }
        val days = ChronoUnit.DAYS.between(start, endExclusive)
        return (0 until days).map { start.plusDays(it) }.map { it to (sums[it] ?: Flow()) }
    }

    /**
     * Total balance at the end of each month from [first] to [last]: starting balances plus every
     * income minus expense up to that month's end. Transfers move money between accounts and don't count.
     */
    fun closingBalances(points: List<AmountPoint>, startingTotal: Long, first: YearMonth, last: YearMonth, zone: ZoneId = ZoneId.systemDefault()): List<Pair<YearMonth, Long>> {
        val before = points.filter { YearMonth.from(day(it.occurredAt, zone)).isBefore(first) }
            .sumOf { if (it.type == TxType.INCOME) it.amount else -it.amount }
        var running = startingTotal + before
        return byMonth(points, first, last, zone).map { (m, f) -> running += f.income - f.expense; m to running }
    }

    /** Running total of spending through each day; the pace line. */
    fun cumulativeExpense(days: List<Pair<LocalDate, Flow>>): List<Long> {
        var sum = 0L
        return days.map { (_, f) -> sum += f.expense; sum }
    }

    /** Expense per category now vs before, largest first. Categories that only existed before are kept (now = 0). */
    fun categoryChanges(now: List<AmountPoint>, before: List<AmountPoint>): List<CategoryChange> {
        fun totals(l: List<AmountPoint>) = l.filter { it.type == TxType.EXPENSE && it.categoryId != null }
            .groupBy { it.categoryId!! }.mapValues { (_, v) -> v.sumOf { it.amount } }
        val n = totals(now)
        val b = totals(before)
        return (n.keys + b.keys).map { CategoryChange(it, n[it] ?: 0, b[it] ?: 0) }
            .sortedWith(compareByDescending<CategoryChange> { it.now }.thenByDescending { it.before })
    }
}
