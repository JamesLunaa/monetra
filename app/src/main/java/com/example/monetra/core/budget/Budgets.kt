package com.example.monetra.core.budget

import com.example.monetra.data.db.BudgetCadence
import java.time.LocalDate
import java.time.YearMonth

/** A limit change: from [from] onward, [categoryId] may spend [limit] (minor units) a month; 0 removes it. */
data class LimitChange(val categoryId: Long, val from: YearMonth, val limit: Long, val cadence: BudgetCadence = BudgetCadence.DAILY)

/** The limit in force for a category in a month, and how it is shown. */
data class Limit(val amount: Long, val cadence: BudgetCadence)

enum class BudgetLevel { OK, NEAR, OVER }

/** What a budget still allows, phrased for its month relative to today. */
sealed interface Allowance {
    /** Current or future month with money left: spendable per remaining day. */
    data class PerDay(val perDay: Long, val daysLeft: Int) : Allowance
    /** Spendable per remaining week (the current, partial week counts as one). */
    data class PerWeek(val perWeek: Long, val weeksLeft: Int) : Allowance
    /** A monthly budget (rent, bills) with money still to pay or spend this month. */
    data class MonthLeft(val left: Long) : Allowance
    /** A monthly budget used exactly: the bill is paid. */
    data object PaidInFull : Allowance
    /** A finished month that stayed within its limit. */
    data class Left(val left: Long) : Allowance
    /** Spending passed the limit by [over]. */
    data class Over(val over: Long) : Allowance
}

object Budgets {
    /** Share of the limit at which a budget turns amber. */
    const val NEAR_RATIO = 0.8

    /** Limits that apply in [month]: each category's latest change on or before it. Removed (0) limits are omitted. */
    fun limitsFor(changes: List<LimitChange>, month: YearMonth): Map<Long, Limit> =
        changes.filter { !it.from.isAfter(month) }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.maxBy { it.from }.let { Limit(it.limit, it.cadence) } }
            .filterValues { it.amount > 0 }

    /** Default display for a new budget: fixed monthly costs read as monthly, everything else per day. */
    fun defaultCadence(icon: String): BudgetCadence =
        if (icon in setOf("bills", "home", "phone")) BudgetCadence.MONTHLY else BudgetCadence.DAILY

    /** Days still to come in [month] as of [today], counting today; 0 for past months. */
    fun daysLeft(month: YearMonth, today: LocalDate): Int = when {
        month.isBefore(YearMonth.from(today)) -> 0
        month.isAfter(YearMonth.from(today)) -> month.lengthOfMonth()
        else -> month.lengthOfMonth() - today.dayOfMonth + 1
    }

    /** Fraction of [month] already behind [today] (0..1), for the "today" tick on a budget bar. */
    fun elapsed(month: YearMonth, today: LocalDate): Float =
        1f - daysLeft(month, today) / month.lengthOfMonth().toFloat()

    fun allowance(
        limit: Long, spent: Long, month: YearMonth, today: LocalDate,
        cadence: BudgetCadence = BudgetCadence.DAILY,
    ): Allowance {
        val left = limit - spent
        if (left < 0) return Allowance.Over(-left)
        val days = daysLeft(month, today)
        if (days == 0) return Allowance.Left(left)
        return when (cadence) {
            BudgetCadence.DAILY -> Allowance.PerDay(left / days, days)
            BudgetCadence.WEEKLY -> ((days + 6) / 7).let { weeks -> Allowance.PerWeek(left / weeks, weeks) }
            BudgetCadence.MONTHLY -> if (left == 0L) Allowance.PaidInFull else Allowance.MonthLeft(left)
        }
    }

    fun level(spent: Long, limit: Long): BudgetLevel = when {
        limit <= 0 -> BudgetLevel.OK
        spent > limit -> BudgetLevel.OVER
        spent >= limit * NEAR_RATIO -> BudgetLevel.NEAR
        else -> BudgetLevel.OK
    }
}
