package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UvirSettingsEncryptionTest {
    @Test
    fun encryptedSettingsRoundTripPreservesUnicode() {
        val password = "una password robusta".toCharArray()
        val plaintext = "{\"nota\":\"שלום · مرحباً · UVIR\"}"

        val encrypted = encryptUvirSettingsText(plaintext, password)

        assertEquals(plaintext, decryptUvirSettingsText(encrypted, password))
    }

    @Test
    fun wrongPasswordIsRejected() {
        val encrypted =
            encryptUvirSettingsText(
                "{\"settings\":true}",
                "password corretta".toCharArray()
            )

        assertThrows(UvirSettingsDecryptionException::class.java) {
            decryptUvirSettingsText(encrypted, "password errata".toCharArray())
        }
    }

    @Test
    fun repeatedEncryptionUsesFreshSaltAndIv() {
        val password = "password ripetuta".toCharArray()

        val first = encryptUvirSettingsText("same", password)
        val second = encryptUvirSettingsText("same", password)

        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.iv, second.iv)
        assertNotEquals(first.ciphertext, second.ciphertext)
    }
}
