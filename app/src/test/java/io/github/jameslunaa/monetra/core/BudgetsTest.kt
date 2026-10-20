package io.github.jameslunaa.monetra.core

import io.github.jameslunaa.monetra.core.budget.Allowance
import io.github.jameslunaa.monetra.core.budget.BudgetLevel
import io.github.jameslunaa.monetra.core.budget.Budgets
import io.github.jameslunaa.monetra.core.budget.Limit
import io.github.jameslunaa.monetra.core.budget.LimitChange
import io.github.jameslunaa.monetra.data.db.BudgetCadence
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class BudgetsTest {
    private val aug = YearMonth.of(2026, 8)
    private val oct = YearMonth.of(2026, 10)

    @Test fun limitRepeatsUntilChanged() {
        val changes = listOf(LimitChange(1, aug, 500_00), LimitChange(1, oct, 800_00), LimitChange(2, aug, 300_00))
        fun amounts(m: YearMonth) = Budgets.limitsFor(changes, m).mapValues { it.value.amount }
        assertEquals(mapOf(1L to 500_00L, 2L to 300_00L), amounts(YearMonth.of(2026, 9)))
        assertEquals(mapOf(1L to 800_00L, 2L to 300_00L), amounts(YearMonth.of(2027, 1)))
        assertEquals(emptyMap<Long, Long>(), amounts(YearMonth.of(2026, 7)))
    }

    @Test fun zeroRemovesFromThatMonth() {
        val changes = listOf(LimitChange(1, aug, 500_00), LimitChange(1, oct, 0))
        assertEquals(mapOf(1L to 500_00L), Budgets.limitsFor(changes, YearMonth.of(2026, 9)).mapValues { it.value.amount })
        assertEquals(emptyMap<Long, Limit>(), Budgets.limitsFor(changes, oct))
    }

    @Test fun allowanceByMonthPosition() {
        val today = java.time.LocalDate.of(2026, 10, 17)
        // 15 days left in October counting today: 1500.00 left -> 100.00 a day.
        assertEquals(Allowance.PerDay(100_00, 15), Budgets.allowance(3000_00, 1500_00, oct, today))
        assertEquals(Allowance.Over(520_00), Budgets.allowance(1500_00, 2020_00, oct, today))
        assertEquals(Allowance.Left(200_00), Budgets.allowance(500_00, 300_00, YearMonth.of(2026, 9), today))
        assertEquals(Allowance.PerDay(100_00, 30), Budgets.allowance(3000_00, 0, YearMonth.of(2026, 11), today))
        assertEquals(16f / 31f, Budgets.elapsed(oct, today), 0.0001f)
    }

    @Test fun cadences() {
        val today = java.time.LocalDate.of(2026, 10, 17)
        // 15 days left = 3 weeks (the partial week counts): 1500.00 left -> 500.00 a week.
        assertEquals(Allowance.PerWeek(500_00, 3), Budgets.allowance(3000_00, 1500_00, oct, today, BudgetCadence.WEEKLY))
        assertEquals(Allowance.MonthLeft(150_00), Budgets.allowance(3000_00, 2850_00, oct, today, BudgetCadence.MONTHLY))
        assertEquals(Allowance.PaidInFull, Budgets.allowance(10000_00, 10000_00, oct, today, BudgetCadence.MONTHLY))
        assertEquals(Allowance.Over(1_00), Budgets.allowance(100_00, 101_00, oct, today, BudgetCadence.MONTHLY))
        val withCadence = listOf(LimitChange(1, aug, 500_00, BudgetCadence.MONTHLY))
        assertEquals(Limit(500_00, BudgetCadence.MONTHLY), Budgets.limitsFor(withCadence, oct)[1])
        assertEquals(BudgetCadence.MONTHLY, Budgets.defaultCadence("bills"))
        assertEquals(BudgetCadence.DAILY, Budgets.defaultCadence("food"))
    }

    @Test fun levels() {
        assertEquals(BudgetLevel.OK, Budgets.level(79_99, 100_00))
        assertEquals(BudgetLevel.NEAR, Budgets.level(80_00, 100_00))
        assertEquals(BudgetLevel.NEAR, Budgets.level(100_00, 100_00))
        assertEquals(BudgetLevel.OVER, Budgets.level(100_01, 100_00))
    }
}
