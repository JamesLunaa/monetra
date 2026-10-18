package com.example.monetra.core.money

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

enum class SignStyle {
    /** No sign; magnitude only. */
    NONE,
    /** Minus for negatives only. */
    NEGATIVE,
    /** Plus or minus always (zero gets no sign). */
    ALWAYS,
}

/** Formats [Money] for display according to the user's currency preferences. */
data class MoneyFormat(
    val symbol: String = "₱",
    val symbolAtStart: Boolean = true,
    val decimals: Int = 2,
) {
    init {
        require(decimals in 0..Money.SCALE)
    }

    private val number: DecimalFormat
        get() = DecimalFormat(if (decimals == 0) "#,##0" else "#,##0." + "0".repeat(decimals),
            DecimalFormatSymbols(Locale.US)).apply { roundingMode = RoundingMode.HALF_UP }

    fun format(money: Money, sign: SignStyle = SignStyle.NEGATIVE): String {
        val digits = number.format(money.abs().toBigDecimal())
        val body = if (symbolAtStart) symbol + digits else "$digits $symbol"
        val prefix = when {
            sign == SignStyle.NONE || money.isZero -> ""
            money.isNegative -> MINUS
            sign == SignStyle.ALWAYS -> "+"
            else -> ""
        }
        return prefix + body
    }

    companion object {
        /** True minus sign, typographically matches "+" in tabular figures. */
        const val MINUS = "−"
    }
}
