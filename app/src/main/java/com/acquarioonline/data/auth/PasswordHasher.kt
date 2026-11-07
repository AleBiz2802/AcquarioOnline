package com.acquarioonline.data.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Hash delle password con PBKDF2-HMAC-SHA256 e salt casuale per ogni utente. */
internal object PasswordHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun verify(password: String, salt: ByteArray, expectedHash: ByteArray): Boolean =
        MessageDigest.isEqual(hash(password, salt), expectedHash)

    fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}
