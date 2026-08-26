package com.reps.app.data.auth

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Password hashing with PBKDF2-HMAC-SHA256, available on every API level via
 * the platform JCA. Accounts store only `hash(salt, password)`: the salt is
 * unique per account so two equal passwords never produce equal rows, and
 * verification runs in constant time to keep timing side channels closed.
 */
@Singleton
class PasswordHasher @Inject constructor() {

    fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        Random.Default.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(
            password.toCharArray(),
            Base64.decode(salt, Base64.NO_WRAP),
            ITERATIONS,
            KEY_LENGTH_BITS,
        )
        val digest = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec)
        spec.clearPassword()
        return Base64.encodeToString(digest.encoded, Base64.NO_WRAP)
    }

    /** Constant-time comparison; never short-circuit on the first differing byte. */
    fun verify(password: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(password, salt).toByteArray(), expectedHash.toByteArray())

    private companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val SALT_BYTES = 16
        const val ITERATIONS = 120_000
        const val KEY_LENGTH_BITS = 256
    }
}
