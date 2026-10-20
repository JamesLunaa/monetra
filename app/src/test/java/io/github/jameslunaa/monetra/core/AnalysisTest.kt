package io.github.jameslunaa.monetra.core

import io.github.jameslunaa.monetra.core.analysis.Analysis
import io.github.jameslunaa.monetra.data.db.AmountPoint
import io.github.jameslunaa.monetra.data.db.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class AnalysisTest {
    private val zone = ZoneId.of("Asia/Manila")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun exp(amount: Long, ms: Long, cat: Long = 1) = AmountPoint(TxType.EXPENSE, amount, ms, cat)
    private fun inc(amount: Long, ms: Long) = AmountPoint(TxType.INCOME, amount, ms, 9)

    @Test fun monthsIncludeEmptyOnes() {
        val pts = listOf(exp(100, at(2026, 8, 3)), inc(500, at(2026, 8, 20)), exp(50, at(2026, 10, 1)))
        val m = Analysis.byMonth(pts, YearMonth.of(2026, 8), YearMonth.of(2026, 10), zone)
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), m.map { it.first })
        assertEquals(100L, m[0].second.expense)
        assertEquals(500L, m[0].second.income)
        assertEquals(0L, m[1].second.expense)
    }

    @Test fun daysUseLocalMidnight() {
        // 11:30 PM local on Oct 2 is still Oct 2 even though it is Oct 2 15:30 UTC.
        val pts = listOf(exp(10, at(2026, 10, 2, 23)), exp(5, at(2026, 10, 3, 0)))
        val d = Analysis.byDay(pts, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4), zone)
        assertEquals(listOf(0L, 10L, 5L), d.map { it.second.expense })
        assertEquals(listOf(0L, 10L, 15L), Analysis.cumulativeExpense(d))
    }

    @Test fun closingBalancesCarryEarlierMonths() {
        val pts = listOf(inc(1000, at(2026, 7, 1)), exp(300, at(2026, 9, 5)), inc(50, at(2026, 10, 2)))
        val b = Analysis.closingBalances(pts, startingTotal = 200, first = YearMonth.of(2026, 9), last = YearMonth.of(2026, 10), zone = zone)
        assertEquals(listOf(YearMonth.of(2026, 9) to 900L, YearMonth.of(2026, 10) to 950L), b)
    }

    @Test fun categoryChanges() {
        val now = listOf(exp(300, 0, cat = 1), exp(50, 0, cat = 2), inc(999, 0))
        val before = listOf(exp(200, 0, cat = 1), exp(70, 0, cat = 3))
        val c = Analysis.categoryChanges(now, before)
        assertEquals(listOf(1L, 2L, 3L), c.map { it.categoryId })
        assertEquals(50, c[0].percent)
        assertNull(c[1].percent)
        assertEquals(-100, c[2].percent)
    }
}
