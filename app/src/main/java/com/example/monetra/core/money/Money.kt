package com.example.monetra.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/** An amount of money in minor units (centavos). Never use floating point for money. */
@JvmInline
value class Money(val minor: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(Math.addExact(minor, other.minor))
    operator fun minus(other: Money) = Money(Math.subtractExact(minor, other.minor))
    operator fun unaryMinus() = Money(Math.negateExact(minor))
    override fun compareTo(other: Money) = minor.compareTo(other.minor)

    val isNegative get() = minor < 0
    val isZero get() = minor == 0L
    fun abs() = if (minor < 0) -this else this

    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(minor, SCALE)

    companion object {
        const val SCALE = 2
        val ZERO = Money(0)

        /** Rounds half-up to centavos. */
        fun of(value: BigDecimal) = Money(value.setScale(SCALE, RoundingMode.HALF_UP).unscaledValue().longValueExact())

        /** Parses a plain decimal like "1500.49" or "-139". Returns null if it isn't a number. */
        fun parse(text: String): Money? = text.trim().toBigDecimalOrNull()?.let(::of)
    }
}

fun Iterable<Money>.sum(): Money = fold(Money.ZERO, Money::plus)
