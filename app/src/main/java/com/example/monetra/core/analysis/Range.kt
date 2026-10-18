package com.example.monetra.core.analysis

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** What the Analysis tab looks at: whole months ending at [end], or any custom span of days. */
sealed interface Range {
    val start: LocalDate
    /** Exclusive. */
    val endExclusive: LocalDate

    /** The same length immediately before, for comparisons. */
    fun previous(): Range
    fun shift(forward: Boolean): Range
    fun label(locale: Locale = Locale.getDefault()): String

    /** [months] whole months ending with [end] (1, 3, 6 or 12). */
    data class Months(val months: Int, val end: YearMonth) : Range {
        val first: YearMonth get() = end.minusMonths(months - 1L)
        override val start: LocalDate get() = first.atDay(1)
        override val endExclusive: LocalDate get() = end.plusMonths(1).atDay(1)
        override fun previous() = Months(months, end.minusMonths(months.toLong()))
        override fun shift(forward: Boolean) = Months(months, end.plusMonths(if (forward) months.toLong() else -months.toLong()))
        override fun label(locale: Locale): String {
            fun f(p: String, m: YearMonth) = DateTimeFormatter.ofPattern(p, locale).format(m)
            return when {
                months == 1 -> f("MMMM yyyy", end)
                first.year == end.year -> "${f("MMM", first)} – ${f("MMM yyyy", end)}"
                else -> "${f("MMM yyyy", first)} – ${f("MMM yyyy", end)}"
            }
        }
    }

    /** Any days from [start] to [last], inclusive. */
    data class Custom(override val start: LocalDate, val last: LocalDate) : Range {
        override val endExclusive: LocalDate get() = last.plusDays(1)
        private val days get() = ChronoUnit.DAYS.between(start, endExclusive)
        override fun previous() = Custom(start.minusDays(days), start.minusDays(1))
        override fun shift(forward: Boolean) =
            if (forward) Custom(endExclusive, last.plusDays(days)) else previous()
        override fun label(locale: Locale): String {
            fun f(p: String, d: LocalDate) = DateTimeFormatter.ofPattern(p, locale).format(d)
            return if (start.year == last.year) "${f("MMM d", start)} – ${f("MMM d, yyyy", last)}"
            else "${f("MMM d, yyyy", start)} – ${f("MMM d, yyyy", last)}"
        }
    }
}
