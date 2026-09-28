package me.mondiversi.uvir

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.util.Base64

internal data class UvirUpdateAsset(val url: String, val bytes: Long, val sha256: String)
internal data class UvirAppRelease(val version: String, val code: Int, val minSdk: Int, val asset: UvirUpdateAsset)
internal data class UvirFirmwareRelease(val version: String, val minAppCode: Int, val asset: UvirUpdateAsset, val partitionMd5: String)
internal data class UvirUpdateCatalog(val app: UvirAppRelease, val sensor: UvirFirmwareRelease)

internal object UvirUpdatePolicy {
    const val INDEX = "https://github.com/mondiversi/Uvir/releases/latest/download/uvir-update.json"
    const val APP_OFFSET = 0x10000
    const val APP_CAPACITY = 0x300000
    fun allowedUrl(url: String, redirect: Boolean = false): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
            ((uri.host == "github.com" && uri.path.startsWith("/mondiversi/Uvir/releases/")) ||
                (redirect && uri.host in setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com")))
    }.getOrDefault(false)
    fun validFirmwareImage(image: ByteArray): Boolean = image.size in 24..APP_CAPACITY &&
        image[0].toInt() and 255 == 0xE9 && image[12].toInt() == 0 && image[13].toInt() == 0
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32768)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }

internal class UvirUpdateClient(private val context: Context) {
    fun catalog(): Pair<UvirUpdateCatalog, String> {
        val encoded = request(UvirUpdatePolicy.INDEX, 256 * 1024).toString(Charsets.UTF_8)
        return verifyCatalog(encoded) to encoded
    }

    fun verifyCatalog(encoded: String): UvirUpdateCatalog {
        require(encoded.length <= 256 * 1024) { "Update index too large" }
        val envelope = JSONObject(encoded)
        require(envelope.getString("algorithm") == "SHA256withRSA") { "Unsupported update signature" }
        val payload = Base64.getDecoder().decode(envelope.getString("payload"))
        val signature = Base64.getDecoder().decode(envelope.getString("signature"))
        val certificate = context.resources.openRawResource(R.raw.uvir_update_certificate).use {
            CertificateFactory.getInstance("X.509").generateCertificate(it)
        }
        require(Signature.getInstance("SHA256withRSA").run {
            initVerify(certificate.publicKey); update(payload); verify(signature)
        }) { "Invalid update signature" }
        val json = JSONObject(payload.toString(Charsets.UTF_8))
        require(json.getInt("schema") == 1 && json.getString("repository") == "mondiversi/Uvir")
        val app = json.getJSONObject("app")
        val sensor = json.getJSONObject("sensor")
        require(app.getString("package") == BuildConfig.APPLICATION_ID)
        require(sensor.getString("layout") == "uvir-esp32-huge-app-v1" &&
            sensor.getInt("offset") == UvirUpdatePolicy.APP_OFFSET &&
            sensor.getInt("capacity") == UvirUpdatePolicy.APP_CAPACITY && sensor.getString("chip") == "ESP32")
        fun asset(value: JSONObject, max: Long): UvirUpdateAsset {
            val result = UvirUpdateAsset(value.getString("url"), value.getLong("bytes"), value.getString("sha256"))
            require(UvirUpdatePolicy.allowedUrl(result.url) && result.bytes in 24..max &&
                result.sha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid update asset" }
            return result
        }
        val version = sensor.getString("version")
        require(version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
        val partitionMd5 = sensor.getString("partition_md5")
        require(partitionMd5.matches(Regex("[0-9a-f]{32}")))
        return UvirUpdateCatalog(
            UvirAppRelease(app.getString("version"), app.getInt("code"), app.getInt("min_sdk"), asset(app, 200L * 1024 * 1024)),
            UvirFirmwareRelease(version, sensor.getInt("min_app_code"), asset(sensor, UvirUpdatePolicy.APP_CAPACITY.toLong()), partitionMd5)
        )
    }

    fun download(asset: UvirUpdateAsset, suffix: String, progress: (Float) -> Unit): File {
        val directory = File(context.noBackupFilesDir, "updates").apply { mkdirs() }
        val target = File(directory, "${asset.sha256}.$suffix")
        if (target.isFile && target.length() == asset.bytes && UvirUpdatePolicy.sha256(target) == asset.sha256) return target
        val partial = File(directory, "${asset.sha256}.part")
        try {
            connection(asset.url).let { connection ->
                try {
                    require(connection.responseCode == 200) { "Update HTTP ${connection.responseCode}" }
                    val length = connection.contentLengthLong
                    require(length == -1L || length == asset.bytes) { "Update size mismatch" }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    connection.inputStream.use { input -> partial.outputStream().use { output ->
                        val buffer = ByteArray(32768)
                        while (true) {
                            val count = input.read(buffer); if (count < 0) break
                            total += count
                            require(total <= asset.bytes) { "Update exceeds signed size" }
                            digest.update(buffer, 0, count); output.write(buffer, 0, count)
                            progress(total.toFloat() / asset.bytes)
                        }
                        output.fd.sync()
                    } }
                    require(total == asset.bytes && digest.digest().toHex() == asset.sha256) { "Update checksum mismatch" }
                    check(partial.renameTo(target)) { "Cannot finalize update download" }
                } finally { connection.disconnect() }
            }
            return target
        } finally { partial.delete() }
    }

    private fun request(url: String, limit: Int): ByteArray = connection(url).let { connection ->
        try {
            require(connection.responseCode == 200) { "Update HTTP ${connection.responseCode}" }
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= limit) {
                    val count = input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                val bytes = output.toByteArray()
                require(bytes.size <= limit) { "Update index too large" }
                bytes
            }
        } finally { connection.disconnect() }
    }

    private fun connection(initial: String): HttpURLConnection {
        require(UvirUpdatePolicy.allowedUrl(initial))
        var url = initial
        repeat(6) {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 15000; connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Uvir/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode in setOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location") ?: error("Missing update redirect")
                val next = URI(url).resolve(location).toString()
                connection.disconnect()
                require(UvirUpdatePolicy.allowedUrl(next, redirect = true)) { "Unsafe update redirect" }
                url = next
            } else return connection
        }
        error("Too many update redirects")
    }
}
