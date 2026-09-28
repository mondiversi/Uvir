package me.mondiversi.uvir

import java.io.File
import java.nio.file.Files
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirDatabaseSharingTest {
    @Test
    fun databaseArchiveIsAes256EncryptedAndReadableWithPassword() {
        val directory = Files.createTempDirectory("uvir-database-export").toFile()
        try {
            val contents = "SQLite format 3\u0000private records".toByteArray()
            val database = File(directory, "uvir.db").apply { writeBytes(contents) }
            val archive = File(directory, "backup.zip")
            val password = "strong-passphrase".toCharArray()

            createEncryptedUvirDatabaseArchive(database, archive, password)

            val zip = ZipFile(archive, password)
            assertTrue(zip.isValidZipFile)
            assertTrue(zip.isEncrypted)
            assertEquals(1, zip.fileHeaders.size)
            assertEquals(EncryptionMethod.AES, zip.fileHeaders.single().encryptionMethod)
            assertEquals(AesKeyStrength.KEY_STRENGTH_256, zip.fileHeaders.single().aesExtraDataRecord.aesKeyStrength)
            val extracted = File(directory, "extracted")
            zip.extractAll(extracted.absolutePath)
            assertArrayEquals(contents, File(extracted, "uvir.db").readBytes())
            assertArrayEquals(
                contents,
                extractEncryptedUvirDatabaseArchive(
                    archive,
                    File(directory, "imported.db"),
                    password
                ).readBytes()
            )

            assertThrows(ZipException::class.java) {
                extractEncryptedUvirDatabaseArchive(
                    archive,
                    File(directory, "wrong.db"),
                    "wrong-password".toCharArray()
                )
            }
            assertTrue(!File(directory, "wrong.db").exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
