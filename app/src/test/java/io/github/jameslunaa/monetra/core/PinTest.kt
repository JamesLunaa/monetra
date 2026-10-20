package io.github.jameslunaa.monetra.core

import io.github.jameslunaa.monetra.core.security.Pin
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinTest {
    @Test fun verifiesOnlyTheRightPin() {
        val stored = Pin.hash("4821")
        assertTrue(Pin.verify("4821", stored))
        assertFalse(Pin.verify("4822", stored))
        assertFalse(Pin.verify("4821", "garbage"))
    }

    @Test fun saltsEveryHash() {
        assertNotEquals(Pin.hash("1234"), Pin.hash("1234"))
        assertFalse(Pin.hash("1234").contains("1234"))
    }

    @Test fun validation() {
        assertTrue(Pin.isValid("1234"))
        assertTrue(Pin.isValid("123456"))
        assertFalse(Pin.isValid("123"))
        assertFalse(Pin.isValid("1234567"))
        assertFalse(Pin.isValid("12a4"))
    }
}
