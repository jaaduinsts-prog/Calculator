package com.example.data.crypto

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val cipherTextBase64: String,
    val ivBase64: String,
    val saltBase64: String,
    val keyFingerprint: String
)

object CryptoEngine {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12
    private const val SALT_LENGTH_BYTE = 16
    private const val KEY_LENGTH_BIT = 256
    private const val ITERATIONS = 10000

    private val secureRandom = SecureRandom()

    private fun deriveKey(passcode: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passcode.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BIT)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    fun computeKeyFingerprint(roomCode: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(roomCode.toByteArray(StandardCharsets.UTF_8))
        return hash.take(8).joinToString(":") { "%02X".format(it) }
    }

    fun encrypt(plainText: String, roomCode: String): EncryptedPayload {
        val salt = ByteArray(SALT_LENGTH_BYTE).apply { secureRandom.nextBytes(this) }
        val iv = ByteArray(IV_LENGTH_BYTE).apply { secureRandom.nextBytes(this) }
        val key = deriveKey(roomCode, salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        return EncryptedPayload(
            cipherTextBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            keyFingerprint = computeKeyFingerprint(roomCode)
        )
    }

    fun decrypt(payload: EncryptedPayload, roomCode: String): String {
        return try {
            val salt = Base64.decode(payload.saltBase64, Base64.NO_WRAP)
            val iv = Base64.decode(payload.ivBase64, Base64.NO_WRAP)
            val cipherBytes = Base64.decode(payload.cipherTextBase64, Base64.NO_WRAP)
            val key = deriveKey(roomCode, salt)

            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val decryptedBytes = cipher.doFinal(cipherBytes)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            "[ENCRYPTED_PAYLOAD: INVALID_KEY_OR_TAMPERED]"
        }
    }
}
