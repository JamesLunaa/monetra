package com.example.monetra.core.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PINs are never stored, only a salted PBKDF2 hash ("salt:hash", both Base64). */
object Pin {
    private const val ITERATIONS = 120_000
    private const val BITS = 256

    fun isValid(pin: String) = pin.length in 4..6 && pin.all { it.isDigit() }

    fun hash(pin: String, salt: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }): String {
        val key = derive(pin, salt)
        val b64 = Base64.getEncoder()
        return b64.encodeToString(salt) + ":" + b64.encodeToString(key)
    }

    fun verify(pin: String, stored: String): Boolean {
        val parts = stored.split(':')
        if (parts.size != 2) return false
        val d = Base64.getDecoder()
        val expected = d.decode(parts[1])
        val actual = derive(pin, d.decode(parts[0]))
        // Constant-time compare.
        if (expected.size != actual.size) return false
        var diff = 0
        for (i in expected.indices) diff = diff or (expected[i].toInt() xor actual[i].toInt())
        return diff == 0
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, BITS)).encoded
}
