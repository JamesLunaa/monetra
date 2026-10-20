package io.github.jameslunaa.monetra.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class PeriodType(val months: Long) {
    DAY(0), WEEK(0), MONTH(1), QUARTER(3), HALF_YEAR(6), YEAR(12)
}

/** A calendar range [start, endExclusive) the records screen is looking at. */
data class Period(val type: PeriodType, val start: LocalDate) {
    val endExclusive: LocalDate = when (type) {
        PeriodType.DAY -> start.plusDays(1)
        PeriodType.WEEK -> start.plusWeeks(1)
        else -> start.plusMonths(type.months)
    }

    fun next() = Period(type, endExclusive)
    fun previous() = Period(type, when (type) {
        PeriodType.DAY -> start.minusDays(1)
        PeriodType.WEEK -> start.minusWeeks(1)
        else -> start.minusMonths(type.months)
    })

    operator fun contains(date: LocalDate) = !date.isBefore(start) && date.isBefore(endExclusive)

    fun startMillis(zone: ZoneId = ZoneId.systemDefault()) = start.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endMillis(zone: ZoneId = ZoneId.systemDefault()) = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

    fun label(locale: Locale = Locale.getDefault()): String {
        val last = endExclusive.minusDays(1)
        fun f(p: String, d: LocalDate) = DateTimeFormatter.ofPattern(p, locale).format(d)
        return when (type) {
            PeriodType.DAY -> f("EEE, MMM d, yyyy", start)
            PeriodType.WEEK -> when {
                start.year != last.year -> "${f("MMM d, yyyy", start)} – ${f("MMM d, yyyy", last)}"
                start.month != last.month -> "${f("MMM d", start)} – ${f("MMM d, yyyy", last)}"
                else -> "${f("MMM d", start)} – ${f("d, yyyy", last)}"
            }
            PeriodType.MONTH -> f("MMMM yyyy", start)
            PeriodType.QUARTER, PeriodType.HALF_YEAR ->
                if (start.year == last.year) "${f("MMM", start)} – ${f("MMM yyyy", last)}"
                else "${f("MMM yyyy", start)} – ${f("MMM yyyy", last)}"
            PeriodType.YEAR -> f("yyyy", start)
        }
    }

    companion object {
        /** The period of [type] containing [date]. Multi-month periods align to Jan/Apr/Jul/Oct and Jan/Jul. */
        fun containing(type: PeriodType, date: LocalDate, firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY): Period {
            val start = when (type) {
                PeriodType.DAY -> date
                PeriodType.WEEK -> date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
                PeriodType.MONTH -> date.withDayOfMonth(1)
                PeriodType.QUARTER, PeriodType.HALF_YEAR -> {
                    val m = type.months.toInt()
                    date.withDayOfMonth(1).withMonth((date.monthValue - 1) / m * m + 1)
                }
                PeriodType.YEAR -> date.withDayOfYear(1)
            }
            return Period(type, start)
        }
    }
}
