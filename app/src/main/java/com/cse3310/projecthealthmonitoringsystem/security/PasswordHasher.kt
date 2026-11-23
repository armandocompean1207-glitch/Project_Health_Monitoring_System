package com.cse3310.projecthealthmonitoringsystem.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    data class Hash(val value: String, val salt: String)
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun derive(secret: CharArray, salt: ByteArray, algorithm: String, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, 256)
        return try { SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    fun hash(secret: CharArray): Hash {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val sha256 = runCatching { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256") }.isSuccess
        val algorithm = if (sha256) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val rounds = if (sha256) 600_000 else 1_300_000
        val derived = derive(secret, salt, algorithm, rounds)
        return Hash("$algorithm:$rounds:${encode(derived)}", encode(salt))
    }
    fun verify(secret: CharArray, hash: String, salt: String): Boolean = try {
        val parts = hash.split(":")
        if (parts.size != 3) false else {
            val algorithm = parts[0]
            val rounds = parts[1].toInt()
            require(algorithm in setOf("PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA1"))
            require(rounds in 600_000..1_300_000)
            val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
            val expected = Base64.decode(parts[2], Base64.NO_WRAP)
            require(saltBytes.size >= 16 && expected.size == 32)
            MessageDigest.isEqual(expected, derive(secret, saltBytes, algorithm, rounds))
        }
    } catch (_: Exception) { false }
}
