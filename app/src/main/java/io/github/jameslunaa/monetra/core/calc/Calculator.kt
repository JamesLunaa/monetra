package io.github.jameslunaa.monetra.core.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Operators shown on the amount keypad. */
enum class Op(val symbol: Char) {
    PLUS('+'), MINUS('−'), TIMES('×'), DIVIDE('÷');

    companion object {
        fun of(c: Char) = entries.firstOrNull { it.symbol == c }
    }
}

/**
 * Evaluates a keypad expression such as "120+35×2" with normal precedence.
 * Returns null for incomplete or invalid input (trailing operator, divide by zero).
 */
object Calculator {
    fun evaluate(expression: String): BigDecimal? {
        val tokens = tokenize(expression) ?: return null
        if (tokens.isEmpty() || tokens.size % 2 == 0) return null
        // First pass: × and ÷. Second pass: + and −.
        val terms = mutableListOf(tokens[0] as BigDecimal)
        val addOps = mutableListOf<Op>()
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i] as Op
            val n = tokens[i + 1] as BigDecimal
            when (op) {
                Op.TIMES -> terms[terms.lastIndex] = terms.last().multiply(n)
                Op.DIVIDE -> {
                    if (n.signum() == 0) return null
                    terms[terms.lastIndex] = terms.last().divide(n, MathContext.DECIMAL64)
                }
                else -> { addOps += op; terms += n }
            }
            i += 2
        }
        var result = terms[0]
        addOps.forEachIndexed { k, op ->
            result = if (op == Op.PLUS) result.add(terms[k + 1]) else result.subtract(terms[k + 1])
        }
        return result.setScale(2, RoundingMode.HALF_UP)
    }

    private fun tokenize(s: String): List<Any>? {
        val out = mutableListOf<Any>()
        val num = StringBuilder()
        for (c in s) {
            val op = Op.of(c)
            if (op != null) {
                if (num.isEmpty()) return null
                out += num.toString().toBigDecimalOrNull() ?: return null
                num.clear()
                out += op
            } else if (c.isDigit() || c == '.') {
                num.append(c)
            } else {
                return null
            }
        }
        if (num.isNotEmpty()) out += num.toString().toBigDecimalOrNull() ?: return null
        return out
    }
}

/** Key presses on the amount keypad. */
sealed interface Key {
    data class Digit(val d: Int) : Key
    data object Dot : Key
    data class Operator(val op: Op) : Key
    data object Backspace : Key
    data object Equals : Key
}

/**
 * The expression shown in the amount field while the user types, e.g. "120+35×2".
 * Each number holds at most two decimals and operators can't repeat.
 */
@JvmInline
value class AmountInput(val text: String = "0") {
    val hasOperator get() = text.drop(1).any { Op.of(it) != null }

    /** Evaluated value, or null while incomplete. */
    val value: BigDecimal? get() = Calculator.evaluate(text)

    fun press(key: Key): AmountInput = when (key) {
        is Key.Digit -> digit(key.d)
        Key.Dot -> dot()
        is Key.Operator -> operator(key.op)
        Key.Backspace -> AmountInput(text.dropLast(1).ifEmpty { "0" })
        // Record amounts are never negative, so "5−10=" is refused rather than producing "-5".
        Key.Equals -> value?.takeIf { it.signum() >= 0 }
            ?.let { AmountInput(it.stripTrailingZeros().toPlainString()) } ?: this
    }

    private val currentNumber get() = text.takeLastWhile { Op.of(it) == null }

    private fun digit(d: Int): AmountInput {
        val cur = currentNumber
        if (cur.contains('.') && cur.substringAfter('.').length >= 2) return this
        if (cur.replace(".", "").length >= MAX_DIGITS) return this
        return AmountInput(if (cur == "0") text.dropLast(1) + d else text + d)
    }

    private fun dot(): AmountInput {
        val cur = currentNumber
        return when {
            cur.contains('.') -> this
            cur.isEmpty() -> AmountInput(text + "0.")
            else -> AmountInput("$text.")
        }
    }

    private fun operator(op: Op): AmountInput {
        val last = text.last()
        return when {
            Op.of(last) != null -> AmountInput(text.dropLast(1) + op.symbol)
            last == '.' -> AmountInput(text.dropLast(1) + op.symbol)
            else -> AmountInput(text + op.symbol)
        }
    }

    companion object {
        private const val MAX_DIGITS = 12
    }
}
