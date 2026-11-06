package com.pingpaisa.app.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Turns a password into something safe to store, and checks a typed password
 * against it. The password itself is never saved anywhere.
 *
 * How it works:
 *  - A random 16-byte salt is made for each account, so two people with the
 *    same password still get different hashes.
 *  - PBKDF2-HMAC-SHA256 runs [ITERATIONS] rounds, which makes guessing
 *    passwords slow for an attacker who copies the database.
 *  - The stored hash string records its own settings ("v1$<rounds>$<hash>"),
 *    so the number of rounds can be raised later without breaking old accounts.
 *
 * Uses only the Java crypto built into Android 8+ (minSdk 26); no extra library.
 * Hashing takes a noticeable fraction of a second on purpose, so call it off
 * the UI thread.
 */
object PasswordHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val VERSION = "v1"
    const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256

    /** Result of [hash]; both values go into UserProfile.passwordHash / passwordSalt. */
    data class Hashed(val hash: String, val salt: String)

    private val random = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun hash(password: CharArray): Hashed {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val derived = derive(password, salt, ITERATIONS)
        return Hashed(
            hash = "$VERSION$$ITERATIONS$${encoder.encodeToString(derived)}",
            salt = encoder.encodeToString(salt)
        )
    }

    /** True only if [password] is the one that produced [storedHash] with [storedSalt]. */
    fun verify(password: CharArray, storedHash: String, storedSalt: String): Boolean {
        val parts = storedHash.split("$")
        if (parts.size != 3 || parts[0] != VERSION) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return false
        val salt = runCatching { decoder.decode(storedSalt) }.getOrNull() ?: return false

        val actual = derive(password, salt, iterations)
        // Constant-time comparison: does not leak how many bytes matched.
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, HASH_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()   // wipe the copy of the password held by the spec
        }
    }
}
