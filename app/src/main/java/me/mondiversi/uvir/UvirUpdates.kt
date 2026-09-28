package me.mondiversi.uvir

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.cert.CertificateFactory

internal data class UvirSensorUpdate(val uid: String, val name: String, val current: String)
internal data class UvirUpdateState(
    val dialog: Boolean = false, val checking: Boolean = false, val busy: Boolean = false,
    val progress: Float? = null, val stage: Int = R.string.update_download,
    val catalog: UvirUpdateCatalog? = null, val sensors: List<UvirSensorUpdate> = emptyList(),
    val message: Int? = null
)

/** Process-owned jobs survive navigation. Only explicit user actions install or flash. */
internal object UvirUpdates {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(UvirUpdateState())
    val state = mutableState.asStateFlow()
    private var started = false
    private var encodedCatalog = ""
    private var downloadedApk: File? = null
    fun start(context: Context) { if (!started) { started = true; check(context, manual = false) } }
    fun dismiss() { if (!mutableState.value.busy) mutableState.value = mutableState.value.copy(dialog = false, message = null) }
    fun check(context: Context, manual: Boolean) {
        if (mutableState.value.checking || mutableState.value.busy) return
        val app = context.applicationContext
        mutableState.value = mutableState.value.copy(checking = true, message = null)
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val client = UvirUpdateClient(app)
                    // A signed pending plan is useful when restarting offline after the APK upgrade.
                    val pending = app.getSharedPreferences("uvir_updates", Context.MODE_PRIVATE).getString("pending_catalog", null)
                    val catalog = try { client.catalog() } catch (error: Exception) {
                        if (manual || pending == null) throw error
                        UvirErrorLog.record(app, "update_check", error)
                        client.verifyCatalog(pending) to pending
                    }
                    val database = UvirDatabaseHelper(app)
                    val profiles = try { database.readSensorProfiles() } finally { database.close() }
                    val targets = UvirSensorCredentialStore.associatedDeviceIds(app).mapNotNull { uid ->
                        val credential = UvirSensorCredentialStore.loadForDevice(app, uid) ?: return@mapNotNull null
                        if (credential.firmwareVersion.isBlank() || compareFirmwareVersions(catalog.first.sensor.version, credential.firmwareVersion) <= 0) null
                        else UvirSensorUpdate(uid, profiles.firstOrNull { it.hardwareUid.equals(uid, true) }?.displayName?.ifBlank { uid } ?: uid, credential.firmwareVersion)
                    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    Triple(catalog.first, catalog.second, targets)
                }
                encodedCatalog = result.second
                val appAvailable = result.first.app.code > BuildConfig.VERSION_CODE
                val available = appAvailable || result.third.isNotEmpty()
                mutableState.value = UvirUpdateState(dialog = available, catalog = result.first, sensors = result.third)
                if (!available) {
                    app.getSharedPreferences("uvir_updates", Context.MODE_PRIVATE).edit().remove("pending_catalog").apply()
                    if (manual) Toast.makeText(context, R.string.update_none, Toast.LENGTH_SHORT).show()
                }
            } catch (error: Exception) {
                UvirErrorLog.record(app, "update_check", error)
                mutableState.value = mutableState.value.copy(checking = false)
                if (manual) Toast.makeText(context, R.string.update_check_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun appUpdate(context: Context, install: (File) -> Unit) {
        val catalog = mutableState.value.catalog ?: return
        if (mutableState.value.busy || catalog.app.code <= BuildConfig.VERSION_CODE) return
        run(context, R.string.update_download, "update_app") {
            require(Build.VERSION.SDK_INT >= catalog.app.minSdk) { "Android version not supported by release" }
            val file = UvirUpdateClient(context).download(catalog.app.asset, "apk") { progress(it) }
            verifyApk(context, file, catalog.app)
            // Keep the plan before the installer can replace this process.
            check(context.getSharedPreferences("uvir_updates", Context.MODE_PRIVATE).edit()
                .putString("pending_catalog", encodedCatalog).commit()) { "Cannot persist update plan" }
            val shared = File(context.cacheDir, "updates").apply { mkdirs() }
            downloadedApk = file.copyTo(File(shared, "Uvir-${catalog.app.version}.apk"), overwrite = true)
            withContext(Dispatchers.Main) { install(downloadedApk!!) }
        }
    }

    @Suppress("DEPRECATION")
    internal fun verifyApk(context: Context, file: File, release: UvirAppRelease) {
        val flags = if (Build.VERSION.SDK_INT >= 28) android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES else android.content.pm.PackageManager.GET_SIGNATURES
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: error("Invalid APK")
        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        require(info.packageName == context.packageName && versionCode == release.code.toLong() && info.versionName == release.version) { "APK identity mismatch" }
        val trusted = context.resources.openRawResource(R.raw.uvir_update_certificate).use {
            CertificateFactory.getInstance("X.509").generateCertificate(it).encoded
        }
        val signers = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners.orEmpty() else info.signatures.orEmpty()
        require(signers.size == 1 && signers.single().toByteArray().contentEquals(trusted)) { "APK signing certificate mismatch" }
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val installedSigners = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners.orEmpty() else installed.signatures.orEmpty()
        require(installedSigners.any { it.toByteArray().contentEquals(trusted) }) { "Installed app signing certificate mismatch" }
    }

    fun installIntent(context: Context, file: File): Intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
        data = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        putExtra(Intent.EXTRA_RETURN_RESULT, true)
    }
    fun permissionIntent(context: Context) = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
    fun installerReturned(context: Context, result: Int) {
        if (result != android.app.Activity.RESULT_OK) UvirErrorLog.record(context, "update_install", "Android installer cancelled or failed (result=$result)")
    }
    fun downloadedInstaller(): File? = downloadedApk

    fun firmware(context: Context, manager: UvirUsbSensorManager, target: UvirSensorUpdate) {
        val catalog = mutableState.value.catalog ?: return
        if (mutableState.value.busy || catalog.app.code > BuildConfig.VERSION_CODE) return
        run(context, R.string.update_download, "firmware_update") {
            require(BuildConfig.VERSION_CODE >= catalog.sensor.minAppCode) { "Update app before sensor" }
            val file = UvirUpdateClient(context).download(catalog.sensor.asset, "bin") { progress(it) }
            val image = file.readBytes()
            require(UvirUpdatePolicy.validFirmwareImage(image))
            val initial = manager.state.value.runtimeInfo
            require(initial.deviceId.equals(target.uid, true) &&
                initial.boardName == "ESP32 Dev Module" && initial.chipModel.startsWith("ESP32-D0WD") &&
                initial.appPartitionSizeBytes == UvirUpdatePolicy.APP_CAPACITY.toLong() &&
                compareFirmwareVersions(initial.firmwareVersion, "0.5.102") >= 0 &&
                compareFirmwareVersions(catalog.sensor.version, initial.firmwareVersion) > 0) { "Unsupported sensor identity, version or partition layout" }
            withContext(Dispatchers.Main) { UvirFirmwareUpdateService.start(context) }
            try {
                mutableState.value = mutableState.value.copy(stage = R.string.update_flash, progress = 0f)
                manager.withFirmwarePort(target.uid) { port, info ->
                    val serial = object : UvirRomSerial {
                        override fun write(bytes: ByteArray) = port.write(bytes, 5000)
                        override fun read(bytes: ByteArray, timeoutMs: Int): Int = port.read(bytes, timeoutMs)
                        override fun control(dtr: Boolean, rts: Boolean) { port.dtr = dtr; port.rts = rts }
                    }
                    val updater = UvirEsp32RomUpdater(serial)
                    try {
                        updater.flash(image, target.uid, (info.flashSizeBytes ?: 0).toInt(), catalog.sensor.partitionMd5) { progress(it) }
                    } catch (error: Exception) {
                        runCatching { updater.reboot() }
                        throw error
                    }
                }
                mutableState.value = mutableState.value.copy(stage = R.string.update_verify, progress = null)
                // Successful ROM verification is necessary, but a fresh HELLO is also required.
                var verified = false
                repeat(80) {
                    if (!verified) {
                        delay(500)
                        val actual = manager.state.value
                        verified = actual.runtimeInfo.deviceId.equals(target.uid, true) && actual.runtimeInfo.firmwareVersion == catalog.sensor.version
                    }
                }
                check(verified) { "Firmware written, but updated sensor HELLO not received" }
                mutableState.value = mutableState.value.copy(sensors = mutableState.value.sensors.filterNot { it.uid == target.uid }, message = R.string.update_done)
                if (mutableState.value.sensors.isEmpty()) context.getSharedPreferences("uvir_updates", Context.MODE_PRIVATE).edit().remove("pending_catalog").apply()
            } finally { UvirFirmwareUpdateService.stop(context) }
        }
    }
    private fun progress(value: Float) { mutableState.value = mutableState.value.copy(progress = value) }
    private fun run(context: Context, stage: Int, source: String, block: suspend () -> Unit) {
        mutableState.value = mutableState.value.copy(busy = true, stage = stage, progress = 0f, message = null)
        scope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (error: Exception) {
                UvirErrorLog.record(context, source, error)
                mutableState.value = mutableState.value.copy(message = R.string.update_failed)
            } finally { mutableState.value = mutableState.value.copy(busy = false, progress = null) }
        }
    }
}
