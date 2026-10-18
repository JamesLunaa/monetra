package com.example.monetra.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monetra.core.budget.Allowance
import com.example.monetra.core.budget.BudgetLevel
import com.example.monetra.core.budget.Budgets
import com.example.monetra.core.time.Period
import com.example.monetra.core.time.PeriodType
import com.example.monetra.data.db.BudgetCadence
import com.example.monetra.data.db.CategoryEntity
import com.example.monetra.data.db.CategoryType
import com.example.monetra.data.db.TxType
import com.example.monetra.data.prefs.Settings
import com.example.monetra.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class BudgetLine(
    val category: CategoryEntity,
    val limit: Long,
    val spent: Long,
    val allowance: Allowance,
    val level: BudgetLevel,
    val cadence: BudgetCadence,
    /** Since when this limit has applied, for the editor's note. */
    val since: YearMonth,
)

data class BudgetsState(
    val month: YearMonth,
    val today: LocalDate,
    val settings: Settings,
    val lines: List<BudgetLine>,
    val unbudgeted: List<CategoryEntity>,
) {
    val totalLimit get() = lines.sumOf { it.limit }
    val totalSpent get() = lines.sumOf { it.spent }
    val daysLeft get() = Budgets.daysLeft(month, today)
    val elapsed get() = Budgets.elapsed(month, today)
    val overCount get() = lines.count { it.level == BudgetLevel.OVER }
    /** Flexible spending, shown per day or per week; drives the header's daily figure. */
    val everyday get() = lines.filter { it.cadence != BudgetCadence.MONTHLY }
    /** Fixed monthly costs (rent, bills); kept out of the daily figure. */
    val monthly get() = lines.filter { it.cadence == BudgetCadence.MONTHLY }
    /** What's left in [group], never counting one category's overspend against another. */
    fun left(group: List<BudgetLine>) = group.sumOf { (it.limit - it.spent).coerceAtLeast(0) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModel(private val c: AppContainer) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<BudgetsState?> = month.flatMapLatest { m ->
        combine(
            c.budgets.changes,
            c.categories.categories,
            c.transactions.categoryTotals(TxType.EXPENSE, Period(PeriodType.MONTH, m.atDay(1))),
            c.settings.settings,
        ) { changes, cats, spentList, settings ->
            val today = LocalDate.now()
            val limits = Budgets.limitsFor(changes, m)
            val spent = spentList.associate { it.categoryId to it.total }
            val expense = cats.filter { it.type == CategoryType.EXPENSE }
            val lines = expense.filter { it.id in limits }.map { cat ->
                val limit = limits.getValue(cat.id)
                val s = spent[cat.id] ?: 0
                BudgetLine(
                    cat, limit.amount, s, Budgets.allowance(limit.amount, s, m, today, limit.cadence), Budgets.level(s, limit.amount),
                    cadence = limit.cadence,
                    since = changes.filter { it.categoryId == cat.id && !it.from.isAfter(m) }.maxOf { it.from },
                )
            }.sortedWith(compareByDescending<BudgetLine> { it.level.ordinal }.thenByDescending { it.spent.toDouble() / it.limit })
            BudgetsState(m, today, settings, lines, expense.filter { it.id !in limits })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shift(forward: Boolean) = month.update { if (forward) it.plusMonths(1) else it.minusMonths(1) }
    fun thisMonth() { month.value = YearMonth.now() }

    /** Sets the limit from the month on screen onward; 0 removes it from that month. */
    fun setLimit(categoryId: Long, limit: Long, cadence: BudgetCadence) = viewModelScope.launch {
        c.budgets.setLimit(categoryId, month.value, limit, cadence)
    }
}
