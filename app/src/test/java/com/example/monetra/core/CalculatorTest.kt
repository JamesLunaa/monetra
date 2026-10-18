package com.example.monetra.core

import com.example.monetra.core.calc.AmountInput
import com.example.monetra.core.calc.Calculator
import com.example.monetra.core.calc.Key
import com.example.monetra.core.calc.Op
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    private fun eval(s: String) = Calculator.evaluate(s)?.toPlainString()

    @Test fun precedence() {
        assertEquals("190.00", eval("120+35×2"))
        assertEquals("115.00", eval("120−10÷2"))
        assertEquals("33.33", eval("100÷3"))
        assertEquals("450.00", eval("450"))
    }

    @Test fun invalidInput() {
        assertNull(eval("12+"))
        assertNull(eval("5÷0"))
        assertNull(eval(""))
        assertNull(eval("1.2.3"))
    }

    private fun type(vararg keys: Key) = keys.fold(AmountInput()) { acc, k -> acc.press(k) }
    private fun d(n: Int) = Key.Digit(n)

    @Test fun typingReplacesLeadingZeroAndLimitsDecimals() {
        assertEquals("12.34", type(d(1), d(2), Key.Dot, d(3), d(4), d(5)).text)
        assertEquals("0.5", type(Key.Dot, d(5)).text)
        assertEquals("7", type(d(0), d(7)).text)
    }

    @Test fun operatorsDoNotRepeat() {
        assertEquals("5×", type(d(5), Key.Operator(Op.PLUS), Key.Operator(Op.TIMES)).text)
        assertEquals("5+0.", type(d(5), Key.Operator(Op.PLUS), Key.Dot).text)
    }

    @Test fun equalsAndBackspace() {
        assertEquals("250", type(d(2), d(0), d(0), Key.Operator(Op.PLUS), d(5), d(0), Key.Equals).text)
        assertEquals("0", type(d(9), Key.Backspace).text)
        // Negative results are refused.
        assertEquals("5−10", type(d(5), Key.Operator(Op.MINUS), d(1), d(0), Key.Equals).text)
    }
}
