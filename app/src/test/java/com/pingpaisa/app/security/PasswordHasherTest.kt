package com.pingpaisa.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun correctPasswordVerifies() {
        val stored = PasswordHasher.hash("paisa123".toCharArray())
        assertTrue(PasswordHasher.verify("paisa123".toCharArray(), stored.hash, stored.salt))
    }

    @Test
    fun wrongPasswordFails() {
        val stored = PasswordHasher.hash("paisa123".toCharArray())
        assertFalse(PasswordHasher.verify("paisa124".toCharArray(), stored.hash, stored.salt))
        assertFalse(PasswordHasher.verify("".toCharArray(), stored.hash, stored.salt))
    }

    @Test
    fun samePasswordGetsDifferentSaltAndHash() {
        val a = PasswordHasher.hash("same-password".toCharArray())
        val b = PasswordHasher.hash("same-password".toCharArray())
        assertNotEquals(a.salt, b.salt)
        assertNotEquals(a.hash, b.hash)
    }

    @Test
    fun storedHashNeverContainsThePassword() {
        val stored = PasswordHasher.hash("paisa123".toCharArray())
        assertFalse(stored.hash.contains("paisa123"))
        assertTrue(stored.hash.startsWith("v1$${PasswordHasher.ITERATIONS}$"))
        assertEquals(3, stored.hash.split("$").size)
    }

    @Test
    fun corruptedStoredValuesAreRejectedNotCrashing() {
        val stored = PasswordHasher.hash("paisa123".toCharArray())
        assertFalse(PasswordHasher.verify("paisa123".toCharArray(), "garbage", stored.salt))
        assertFalse(PasswordHasher.verify("paisa123".toCharArray(), "v1\$abc\$xyz", stored.salt))
        assertFalse(PasswordHasher.verify("paisa123".toCharArray(), stored.hash, "%%%not-base64%%%"))
    }
}
