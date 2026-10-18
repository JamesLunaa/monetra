package com.example.monetra.core

import com.example.monetra.core.money.Money
import com.example.monetra.core.money.MoneyFormat
import com.example.monetra.core.money.SignStyle
import com.example.monetra.core.money.sum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun parsesDecimalsIntoCentavos() {
        assertEquals(Money(1500049), Money.parse("15000.49"))
        assertEquals(Money(13900), Money.parse(" 139 "))
        assertEquals(Money(-5), Money.parse("-0.05"))
        assertEquals(Money(1), Money.parse("0.005"))
        assertNull(Money.parse("abc"))
    }

    @Test fun sumsExactly() {
        val sum = listOf("0.10", "0.20", "0.30").map { Money.parse(it)!! }.sum()
        assertEquals(Money(60), sum)
    }

    @Test fun formatsPeso() {
        val f = MoneyFormat()
        assertEquals("₱12,345.00", f.format(Money(1234500)))
        assertEquals("−₱2,500.00", f.format(Money(-250000)))
        assertEquals("+₱1,250.00", f.format(Money(125000), SignStyle.ALWAYS))
        assertEquals("₱0.00", f.format(Money.ZERO, SignStyle.ALWAYS))
        assertEquals("₱5.00", f.format(Money(-500), SignStyle.NONE))
    }

    @Test fun formatsPreferences() {
        assertEquals("1,250 ₱", MoneyFormat(symbolAtStart = false, decimals = 0).format(Money(125000)))
        assertEquals("$1,250.5", MoneyFormat(symbol = "$", decimals = 1).format(Money(125050)))
    }
}
