package me.mondiversi.uvir

import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal const val UVIR_ENCRYPTED_SETTINGS_SCHEMA = "uvir-settings-encrypted"
internal const val UVIR_ENCRYPTED_SETTINGS_VERSION = 1
internal const val UVIR_SETTINGS_ENCRYPTION_NAME = "AES-256-GCM"
internal const val UVIR_SETTINGS_KDF_NAME = "PBKDF2-HMAC-SHA256"
internal const val UVIR_SETTINGS_KDF_ITERATIONS = 210_000
internal const val UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH = 8

private const val AES_KEY_BITS = 256
private const val GCM_TAG_BITS = 128
private const val SALT_BYTES = 16
private const val IV_BYTES = 12
private val settingsEncryptionAad =
    "$UVIR_ENCRYPTED_SETTINGS_SCHEMA:$UVIR_ENCRYPTED_SETTINGS_VERSION"
        .toByteArray(StandardCharsets.UTF_8)

internal data class UvirEncryptedSettingsData(
    val iterations: Int,
    val salt: String,
    val iv: String,
    val ciphertext: String
)

internal class UvirSettingsDecryptionException(cause: Throwable? = null) :
    GeneralSecurityException("Unable to decrypt Uvir settings", cause)

internal class UvirSettingsPasswordRequiredException :
    GeneralSecurityException("An encryption password is required")

internal fun encryptUvirSettingsText(
    plaintext: String,
    password: CharArray,
    secureRandom: SecureRandom = SecureRandom()
): UvirEncryptedSettingsData {
    require(password.size >= UVIR_SETTINGS_MINIMUM_PASSWORD_LENGTH)
    val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
    val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
    val key = deriveUvirSettingsKey(password, salt, UVIR_SETTINGS_KDF_ITERATIONS)
    val ciphertext =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            updateAAD(settingsEncryptionAad)
            doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        }
    return UvirEncryptedSettingsData(
        iterations = UVIR_SETTINGS_KDF_ITERATIONS,
        salt = Base64.getEncoder().encodeToString(salt),
        iv = Base64.getEncoder().encodeToString(iv),
        ciphertext = Base64.getEncoder().encodeToString(ciphertext)
    )
}

internal fun decryptUvirSettingsText(
    encrypted: UvirEncryptedSettingsData,
    password: CharArray
): String {
    try {
        require(encrypted.iterations in 100_000..2_000_000)
        val salt = Base64.getDecoder().decode(encrypted.salt)
        val iv = Base64.getDecoder().decode(encrypted.iv)
        require(salt.size == SALT_BYTES)
        require(iv.size == IV_BYTES)
        val key = deriveUvirSettingsKey(password, salt, encrypted.iterations)
        val plaintext =
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(settingsEncryptionAad)
                doFinal(Base64.getDecoder().decode(encrypted.ciphertext))
            }
        return String(plaintext, StandardCharsets.UTF_8)
    } catch (error: AEADBadTagException) {
        throw UvirSettingsDecryptionException(error)
    } catch (error: IllegalArgumentException) {
        throw UvirSettingsDecryptionException(error)
    } catch (error: GeneralSecurityException) {
        throw UvirSettingsDecryptionException(error)
    }
}

private fun deriveUvirSettingsKey(
    password: CharArray,
    salt: ByteArray,
    iterations: Int
): SecretKeySpec {
    val specification = PBEKeySpec(password, salt, iterations, AES_KEY_BITS)
    return try {
        val encoded =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(specification)
                .encoded
        SecretKeySpec(encoded, "AES")
    } finally {
        specification.clearPassword()
    }
}
