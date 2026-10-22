package me.mondiversi.uvir

import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit maintenance entry point. No dependency on the newer target app's code.
 * A same-signed test APK can back up the installed release without replacing it,
 * migrating it, changing settings, opening an activity or making it debuggable. */
class UvirMaintenanceArchiveBackupTest {
    @Test fun encryptedReadOnlyArchiveSnapshot() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("archiveBackup") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "me.mondiversi.uvir")
        val name = requireNotNull(args.getString("archiveName"))
        check(name.matches(Regex("[a-z0-9_-]+")))
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(
            Base64.decode(requireNotNull(args.getString("backupPublicKey")), Base64.DEFAULT)))
        val database = context.getDatabasePath("uvir.db")
        check(database.isFile)
        var version = 0
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            version = db.version
            check(version == requireNotNull(args.getString("expectedDbVersion")).toInt())
            db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { c -> check(c.moveToFirst() && c.getInt(0) == 0) }
            db.rawQuery("PRAGMA quick_check", null).use { c -> check(c.moveToFirst() && c.getString(0) == "ok") }
        }
        val root = File(context.applicationInfo.dataDir)
        val files = listOf(database) + listOf("shared_prefs", "files").flatMap { folder ->
            File(root, folder).walkTopDown().filter(File::isFile).toList()
        }
        val output = File(context.getExternalFilesDir(null), "maintenance-$name.uvbackup")
        check(!output.exists()) { "Never overwrite an existing backup" }
        val random = SecureRandom()
        val key = ByteArray(32).apply(random::nextBytes)
        val nonce = ByteArray(12).apply(random::nextBytes)
        val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding").apply {
            init(Cipher.ENCRYPT_MODE, publicKey, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT))
        }
        val wrapped = rsa.doFinal(key)
        val aes = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        }
        try {
            FileOutputStream(output).use { destination ->
                DataOutputStream(destination).apply {
                    write("UVIRBACKUP1".toByteArray(Charsets.US_ASCII))
                    writeInt(wrapped.size); write(wrapped); write(nonce); flush()
                }
                ZipOutputStream(CipherOutputStream(destination, aes)).use { zip ->
                    files.forEach { file ->
                        zip.putNextEntry(ZipEntry(file.relativeTo(root).invariantSeparatorsPath))
                        file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }
                    zip.putNextEntry(ZipEntry("archive-version.txt"))
                    zip.write("UVIR database schema $version\n".toByteArray()); zip.closeEntry()
                }
            }
            check(output.length() > 512)
            instrumentation.sendStatus(0, Bundle().apply {
                putString("stream", "Encrypted snapshot ready: schema=$version files=${files.size} bytes=${output.length()}\n")
            })
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        } finally { key.fill(0) }
    }
}
