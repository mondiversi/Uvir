package me.mondiversi.uvir

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.*
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.mondiversi.uvir.usb.*
import java.util.UUID
import java.util.concurrent.Executors

internal enum class UsbPhoneStatus { DETACHED, PERMISSION, INITIALIZING, READY, ERROR }
internal data class UsbPhoneState(
    val status: UsbPhoneStatus = UsbPhoneStatus.DETACHED,
    val identity: String = "",
    val persistentIdentity: Boolean = false,
    val uvAvailable: Boolean = false,
    val visibleAvailable: Boolean = false,
    val sample: SensorSample? = null,
    val sequence: Long = 0,
    val uvGain: Double? = null,
    val visibleGain: Double? = null,
    val error: String? = null,
    val simulation: Boolean = false,
    val automaticSession: Long? = null,
    val automaticCount: Int = 0,
    val alertSession: Long? = null,
    val alertCount: Int = 0,
    val measuringReference: Boolean = false,
    val busy: Boolean = false,
    val manualSession: Long? = null,
    val manualCount: Int = 0,
    val outOfRangeSimulation: Boolean = false,
    val calibration: PhoneCalibration = PhoneCalibration(),
    val samples: Int = 1,
    val spacingMs: Long = 100,
    val trim: Boolean = false
) {
    val ready: Boolean get() = (status == UsbPhoneStatus.READY || simulation) && sample != null
}

/** Application-owned hardware and recording. Activity recreation never reconnects or restarts a job. */
internal class UvirUsbPhoneRuntime(private val context: Context) {
    val database = UvirDatabaseHelper(context)
    private val preferences = context.getSharedPreferences(USB_PHONE_PREFS, Context.MODE_PRIVATE)
    private val usb = context.getSystemService(UsbManager::class.java)
    private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "Uvir-USB-I2C") }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, error ->
        handleFailure(error)
    })
    private val mutable = MutableStateFlow(UsbPhoneState(
        calibration = loadCalibration(),
        samples = preferences.getInt("samples", 5).coerceIn(1, 100),
        spacingMs = preferences.getLong("spacing", 100).coerceIn(0, 60000),
        trim = preferences.getBoolean("trim", false)))
    val state = mutable.asStateFlow()
    @Volatile private var device: UsbDevice? = null
    @Volatile private var bus: Ft232hMpsse? = null
    private var loop: Job? = null
    private val generationCounter = java.util.concurrent.atomic.AtomicLong(0L)
    private val generation: Long get() = generationCounter.get()
    private val revisionCounter = java.util.concurrent.atomic.AtomicLong(0L)
    private val revision: Long get() = revisionCounter.get()
    private val rawFrames = MutableSharedFlow<Pair<Long, DoubleArray>>(extraBufferCapacity = 2)
    private var auto: AutomaticAcquisitionRequest? = null
    private var autoNextAt = 0L
    private var autoConditionStarted = false
    private var autoEndAt: Long? = null
    private var autoStarted = 0L
    private var autoFirst = 0L
    private var manualRequest: Pair<String, ManualSaveMode>? = null
    private var alertFirstAt = 0L
    private var alertEndAt: Long? = null
    private var lastAlertAt = Long.MIN_VALUE
    private var alerts = loadThresholdAlertSettings(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)).withMonitoringStopped()
    private var running = false
    private val permissionRequests = UsbPermissionRequests()
    private var activityTimer: Job? = null
    private var manualRequestedAt = 0L
    @Volatile private var referenceJob: Job? = null

    private val permissionAction = "${context.packageName}.FT232H_PERMISSION"
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    @Suppress("DEPRECATION") val detached = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (detached != null && (detached.deviceName == device?.deviceName || detached.deviceName == permissionRequests.requestedDeviceName)) {
                        permissionRequests.clear()
                        disconnect()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    @Suppress("DEPRECATION") val attached = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (attached == null || !isFt232h(attached)) return
                    // Android is already resolving/opening the matching application.
                    scan(mayRequestPermission = false)
                }
                permissionAction -> {
                    @Suppress("DEPRECATION") val granted = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (!permissionRequests.acceptsReply(granted?.deviceName,
                            granted != null && usb.deviceList.containsKey(granted.deviceName)) || granted == null || !isFt232h(granted)) return
                    // An earlier denial cannot replace access granted by the system chooser.
                    if (usb.hasPermission(granted)) connect(granted)
                    else mutable.update { it.copy(status = UsbPhoneStatus.ERROR, sample = null, error = "USB permission denied") }
                }
            }
        }
    }

    fun start() {
        if (running) return
        running = true
        database.writableDatabase // Keep the existing schema and W/m² migration, without resetting archives.
        // A process that disappeared cannot have continued collecting from a USB peripheral.
        preferences.getLong("open_acquisition", 0).takeIf { it > 0 }?.let {
            database.finishAcquisitionSession(it, interruptedAt("acquisition_sessions", "acquisitions", it))
        }
        preferences.getLong("open_alert", 0).takeIf { it > 0 }?.let {
            database.finishAlertSession(it, interruptedAt("alert_sessions", "alerts", it))
        }
        preferences.edit().remove("open_acquisition").remove("open_alert").apply()
        ContextCompat.registerReceiver(context, receiver, IntentFilter(permissionAction).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        scan()
    }

    @Synchronized fun scan(mayRequestPermission: Boolean = true) {
        if (mutable.value.simulation || device != null) return
        val candidates = usb.deviceList.values.filter(::isFt232h)
        if (candidates.size > 1) {
            mutable.update { it.copy(status = UsbPhoneStatus.ERROR, error = "Connect one FT232H at a time") }; return
        }
        val found = candidates.singleOrNull() ?: return
        if (usb.hasPermission(found)) connect(found) else {
            // Returning from Android's denied permission dialog must not open it again.
            if (permissionRequests.requestedDeviceName == found.deviceName) return
            mutable.update { it.copy(status = UsbPhoneStatus.PERMISSION) }
            if (!permissionRequests.shouldRequest(found.deviceName, alreadyGranted = false, mayPrompt = mayRequestPermission)) return
            usb.requestPermission(found, PendingIntent.getBroadcast(context, 0,
                Intent(permissionAction).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
    }

    @Synchronized private fun connect(found: UsbDevice) {
        if (device != null || mutable.value.simulation) return
        device = found
        val epoch = generationCounter.incrementAndGet()
        mutable.update { it.copy(status = UsbPhoneStatus.INITIALIZING, sample = null, error = null) }
        loop = scope.launch {
            var localBus: Ft232hMpsse? = null
            try {
                if (epoch != generation || mutable.value.simulation) return@launch
                val link = Ft232hMpsse(AndroidFt232h(usb, found))
                localBus = link
                bus = link
                link.initialize()
                val uv = As7331(link)
                val visible = As7343(link)
                val uvResult = runCatching { uv.initialize() }
                val visibleResult = runCatching { visible.initialize() }
                ensureActive()
                if (epoch != generation) return@launch
                val identity = usbHardwareIdentity(runCatching { found.serialNumber }.getOrNull(), UUID.randomUUID().toString())
                val persistent = !identity.startsWith("FT232H-TEMP:")
                mutable.update { it.copy(identity = identity, persistentIdentity = persistent,
                    uvAvailable = uvResult.isSuccess, visibleAvailable = visibleResult.isSuccess) }
                if (uvResult.isFailure || visibleResult.isFailure) throw InvalidSensorReading(
                    listOfNotNull(uvResult.exceptionOrNull()?.message, visibleResult.exceptionOrNull()?.message).joinToString("; "))
                database.ensureSensorProfile(identity)
                database.renameSensor(identity, context.getString(R.string.usb_sensor_name))
                mutable.update { it.copy(status = UsbPhoneStatus.READY) }
                while (isActive && epoch == generation) {
                    val config = mutable.value
                    val configRevision = revision
                    try {
                    val frames = mutableListOf<DoubleArray>()
                    repeat(config.samples) { index ->
                        ensureActive()
                        val lockGain = mutable.value.measuringReference
                        val u = uv.capture(lockGain)
                        val v = visible.capture(lockGain)
                        val frame = u.wm2 + v.wm2
                        if (manualRequest != null) manualRequestedAt = SystemClock.elapsedRealtime()
                        rawFrames.emit(epoch to frame)
                        frames.add(frame)
                        yield()
                        mutable.update { it.copy(uvGain = u.gain, visibleGain = v.gain) }
                        if (index + 1 < config.samples) delay(config.spacingMs)
                    }
                    ensureActive()
                    if (epoch != generation) break
                    if (configRevision != revision) continue
                    publish(processPhoneMeasurement(frames, config.calibration, config.trim), config.identity)
                    } catch (error: InvalidSensorReading) {
                        if (!error.retryable) throw error
                        if (mutable.value.error != error.message) UvirErrorLog.record(context, "usb_optical_reading", error)
                        referenceJob?.cancel(CancellationException("Invalid reference reading"))
                        mutable.update { it.copy(sample = null, error = error.message) }
                    }
                    delay(100)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                UvirErrorLog.record(context, "usb_i2c", error)
                if (epoch == generation) {
                    finishActivities()
                    mutable.update { it.copy(status = UsbPhoneStatus.ERROR, sample = null, error = error.message) }
                }
            } finally {
                localBus?.close()
                if (epoch == generation) { bus = null; device = null }
            }
        }
    }

    fun disconnect() {
        generationCounter.incrementAndGet()
        referenceJob?.cancel(CancellationException("USB disconnected"))
        loop?.cancel(); loop = null
        bus?.close(); bus = null; device = null
        scope.launch { finishActivities() }
        mutable.update { it.copy(status = UsbPhoneStatus.DETACHED, sample = null, identity = "", uvGain = null,
            visibleGain = null, uvAvailable = false, visibleAvailable = false, manualSession = null, manualCount = 0) }
    }
    fun retry() { permissionRequests.clear(); disconnect(); scope.launch { delay(150); withContext(Dispatchers.Main) { scan() } } }

    fun setSimulation(enabled: Boolean) {
        if (mutable.value.automaticSession != null || mutable.value.alertSession != null || mutable.value.busy) return
        disconnect()
        scope.launch {
            finishActivities()
            mutable.update { it.copy(simulation = enabled) }
            if (enabled) {
                val epoch = generationCounter.incrementAndGet()
                loop = scope.launch {
                    while (isActive && epoch == generation) {
                        val t = SystemClock.elapsedRealtime() / 3000.0
                        publish(processPhoneMeasurement(listOf(DoubleArray(11) { i ->
                            (1 + kotlin.math.sin(t + i)) * (if (i < 3) .002 else .3) }), mutable.value.calibration)
                            .copy(qualityFlags = if (mutable.value.outOfRangeSimulation && (t.toLong() % 5L == 0L)) 3 else 0), "")
                        delay(350)
                    }
                }
            } else withContext(Dispatchers.Main) { scan() }
        }
    }

    private suspend fun publish(sample: SensorSample, origin: String) {
        if (origin.isNotBlank() && database.findSensorProfile(origin) == null)
            database.renameSensor(origin, context.getString(R.string.usb_sensor_name))
        mutable.update { it.copy(sample = sample, sequence = it.sequence + 1, error = null) }
        val now = SystemClock.elapsedRealtime()
        manualRequest?.let { (note, mode) ->
            manualRequest = null
            val manualId = when (mode) {
                ManualSaveMode.SINGLE -> null
                ManualSaveMode.NEW_MANUAL_SESSION -> database.nextSessionId()
                ManualSaveMode.LAST_MANUAL_SESSION -> mutable.value.manualSession ?: database.nextSessionId()
            }
            val count = if (manualId == mutable.value.manualSession) mutable.value.manualCount + 1 else 1
            val sessionNote = if (manualId != null && manualId == mutable.value.manualSession) database.readAcquisitionSessionNote(manualId) else note
            if (manualId != null) database.ensureAcquisitionSession(manualId, sessionNote, System.currentTimeMillis(), origin)
            check(database.saveAcquisition(sample, sessionNote, sessionId = manualId,
                sessionSequence = manualId?.let { count }, sensorDeviceId = origin) > 0)
            if (manualId != null) {
                database.finishAcquisitionSession(manualId)
                mutable.update { it.copy(manualSession = manualId, manualCount = count) }
            }
            acquiredFeedback()
            mutable.update { it.copy(busy = false) }
        }
        auto?.let { request ->
            val step = simulatedConditionalAutomaticAcquisitionStep(now, autoFirst, autoNextAt, request.intervalSeconds,
                autoEndAt, request.maxAcquisitions.takeIf { request.limitEnabled }, mutable.value.automaticCount, true,
                request.conditionalPlan, autoConditionStarted) {
                request.conditionalPlan?.let { plan ->
                    if (plan.rules.any { sample.isOutOfRange(it.metric) }) null else evaluateAcquisitionCondition(plan, sample)
                }
            }
            if (step == SimulatedAutomaticAcquisitionStep.Stop) stopAutomaticNow()
            else if (step is SimulatedAutomaticAcquisitionStep.Acquire) {
                autoNextAt = step.nextAtMs
                if (step.startsSession) {
                    autoConditionStarted = true
                    if (request.useDuration) autoEndAt = now + request.durationSeconds * 1000
                }
                val id = mutable.value.automaticSession ?: return@let
                check(database.saveAcquisition(sample, request.note, true, id, mutable.value.automaticCount + 1,
                    origin) > 0)
                mutable.update { it.copy(automaticCount = it.automaticCount + 1) }
                acquiredFeedback()
                if (request.limitEnabled && mutable.value.automaticCount >= request.maxAcquisitions) stopAutomaticNow()
            }
        }
        val id = mutable.value.alertSession
        if (id != null) {
            if ((alertEndAt != null && now >= alertEndAt!!) || (alerts.maxRegistrations > 0 && mutable.value.alertCount >= alerts.maxRegistrations)) {
                stopAlertsNow(); return
            }
            if (now < alertFirstAt) return
            val violations = thresholdAlertViolations(sample, alerts.copy(enabled = true))
            if (violations.isNotEmpty() && (lastAlertAt == Long.MIN_VALUE || now - lastAlertAt >= alerts.repeatSeconds * 1000)) {
                if (alerts.recordEvents) {
                    database.insertThresholdAlertLog(violations, sessionId = id, sensorDeviceId = origin,
                        qualityFlags = sample.qualityFlags, opticalWindow = sample.opticalWindow)
                    UvirUnreadCounters.increment(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), KEY_UNREAD_ALERT_COUNT)
                }
                lastAlertAt = now
                mutable.update { it.copy(alertCount = it.alertCount + 1) }
                scope.launch { playThresholdAlertTone(context, alerts.sound, alerts.volume) }
                if (alerts.maxRegistrations > 0 && mutable.value.alertCount >= alerts.maxRegistrations) stopAlertsNow()
            }
        }
    }
    private fun recordIdentity(): String {
        if (mutable.value.simulation) return ""
        val id = mutable.value.identity
        if (database.findSensorProfile(id) == null) database.renameSensor(id, context.getString(R.string.usb_sensor_name))
        return id
    }
    fun acquire(note: String = "", mode: ManualSaveMode = ManualSaveMode.SINGLE) = scope.launch {
        if (mutable.value.ready && manualRequest == null) {
            manualRequest = note to mode; manualRequestedAt = SystemClock.elapsedRealtime()
            mutable.update { it.copy(busy = true) }; ensureActivityTimer()
        }
    }
    fun startAutomatic(request: AutomaticAcquisitionRequest) = scope.launch {
        if (!mutable.value.ready || auto != null || request.externalCommand) return@launch
        require(request.intervalSeconds in 1..86400 && (request.conditionalPlan == null || request.conditionalPlan.isValid()))
        val id = database.nextSessionId()
        database.startAcquisitionSession(id, request.note, sensorDeviceId = recordIdentity())
        preferences.edit().putLong("open_acquisition", id).commit()
        auto = request
        autoStarted = SystemClock.elapsedRealtime()
        autoFirst = autoStarted + if (request.useStartDelay) request.startDelaySeconds * 1000 else 0
        autoNextAt = autoFirst
        autoConditionStarted = false
        autoEndAt = if (request.useDuration && request.conditionalPlan?.action != AcquisitionConditionAction.START)
            autoFirst + request.durationSeconds * 1000 else null
        mutable.update { it.copy(automaticSession = id, automaticCount = 0) }
        keepAlive(true)
        ensureActivityTimer()
    }
    fun stopAutomatic() = scope.launch { stopAutomaticNow() }
    private fun stopAutomaticNow() {
        mutable.value.automaticSession?.let(database::finishAcquisitionSession)
        preferences.edit().remove("open_acquisition").apply()
        auto = null
        mutable.update { it.copy(automaticSession = null) }
        keepAlive(mutable.value.alertSession != null)
    }
    fun startAlerts(settings: ThresholdAlertSettings, note: String = "") = scope.launch {
        if (!mutable.value.ready || mutable.value.alertSession != null || settings.rules.none { it.enabled }) return@launch
        alerts = settings.copy(enabled = true)
        val id = database.nextSessionId()
        database.startAlertSession(id, note, sensorDeviceId = recordIdentity())
        preferences.edit().putLong("open_alert", id).commit()
        lastAlertAt = Long.MIN_VALUE
        alertFirstAt = SystemClock.elapsedRealtime() + alerts.startDelaySeconds * 1000
        alertEndAt = if (alerts.durationSeconds > 0) alertFirstAt + alerts.durationSeconds * 1000 else null
        mutable.update { it.copy(alertSession = id, alertCount = 0) }
        keepAlive(true)
        ensureActivityTimer()
    }
    fun stopAlerts() = scope.launch { stopAlertsNow() }
    private fun stopAlertsNow() {
        mutable.value.alertSession?.let(database::finishAlertSession)
        preferences.edit().remove("open_alert").apply()
        mutable.update { it.copy(alertSession = null) }
        keepAlive(mutable.value.automaticSession != null)
    }
    private fun finishActivities() { manualRequest = null; stopAutomaticNow(); stopAlertsNow(); mutable.update { it.copy(busy = false) } }
    private fun ensureActivityTimer() {
        if (activityTimer?.isActive == true) return
        activityTimer = scope.launch {
            while (isActive && (auto != null || mutable.value.alertSession != null || manualRequest != null)) {
                val now = SystemClock.elapsedRealtime()
                if (autoEndAt?.let { now >= it } == true && auto != null) stopAutomaticNow()
                if (alertEndAt?.let { now >= it } == true && mutable.value.alertSession != null) stopAlertsNow()
                if (manualRequest != null && now - manualRequestedAt >= maxOf(10000L, mutable.value.spacingMs + 3000L)) {
                    manualRequest = null
                    mutable.update { it.copy(busy = false) }
                    UvirErrorLog.record(context, "usb_manual_acquisition", "Fresh measurement timed out")
                }
                delay(100)
            }
        }
    }
    private fun keepAlive(active: Boolean) {
        // connectedDevice FGS requires a real granted USB device on Android 14+.
        // Debug-only generation is intentionally foreground-only.
        if (!mutable.value.simulation || !active) UsbPhoneService.update(context, active)
    }
    fun onBackground() {
        if (mutable.value.simulation) { loop?.cancel(); scope.launch { finishActivities() } }
    }
    fun onForeground() {
        if (mutable.value.simulation && loop?.isActive != true) setSimulation(true) else scan()
    }

    fun setSampling(samples: Int, spacing: Long, trim: Boolean) {
        if (mutable.value.measuringReference) return
        require(samples in 1..100 && spacing in 0..60000)
        revisionCounter.incrementAndGet()
        preferences.edit().putInt("samples", samples).putLong("spacing", spacing).putBoolean("trim", trim).apply()
        mutable.update { it.copy(samples = samples, spacingMs = spacing, trim = trim) }
    }
    @Synchronized fun setCalibration(value: PhoneCalibration) {
        if (mutable.value.measuringReference) return
        revisionCounter.incrementAndGet()
        preferences.edit().putString(USB_PHONE_CALIBRATION, encodeUsbPhoneCalibration(value)).apply()
        mutable.update { it.copy(calibration = value) }
    }
    @Synchronized fun updateCalibration(transform: (PhoneCalibration) -> PhoneCalibration) {
        setCalibration(transform(mutable.value.calibration))
    }
    private fun loadCalibration(): PhoneCalibration = runCatching {
        decodeUsbPhoneCalibration(preferences.getString(USB_PHONE_CALIBRATION, null) ?: return PhoneCalibration())
    }.getOrElse { PhoneCalibration() }

    suspend fun measureOffsets(group: CalibrationGroup, progress: (Int) -> Unit): DarkOffsetReading {
        val readings = collectReference(progress)
        return DarkOffsetReading(group, group.indices.map { i -> readings.map { it[i] }.average().toFloat() }, readings.size)
    }
    suspend fun measureWindow(expected: OpticalWindowMeasurementContext?, progress: (Int) -> Unit): OpticalWindowReading {
        val config = mutable.value
        val version = revision
        val epoch = generation
        val window = OpticalWindowSettings(true, config.calibration.window.map(Double::toFloat), config.calibration.windowMaster.toFloat())
        val measurementContext = OpticalWindowMeasurementContext(config.identity, SensorConnectionMode.USB, epoch,
            "phone-usb-v1", version, 1.0, 1.0, CalibrationOffsets(config.calibration.offsets.map(Double::toFloat)),
            CalibrationChannelFactors(config.calibration.factors.map(Double::toFloat)), window,
            config.samples, config.spacingMs, config.trim, config.visibleAvailable, config.uvAvailable)
        check(expected == null || expected == measurementContext) { "window_measurement_changed" }
        val rows = collectReference(progress).map { raw -> DoubleArray(11) { i ->
            maxOf(0.0, raw[i] - config.calibration.offsets[i]) * config.calibration.factors[i] } }
        return OpticalWindowReading(measurementContext, List(11) { i ->
            val values = rows.map { it[i] }; val mean = values.average()
            OpticalWindowChannelReading(mean, kotlin.math.sqrt(values.map { (it - mean) * (it - mean) }.average()), values.size)
        }, isCurrent = { revision == version && generation == epoch && mutable.value.ready })
    }
    private suspend fun collectReference(progress: (Int) -> Unit): List<DoubleArray> {
        check(mutable.value.ready && !mutable.value.simulation && !mutable.value.measuringReference)
        val epoch = generation
        val version = revision
        val rows = mutableListOf<DoubleArray>()
        val start = SystemClock.elapsedRealtime()
        referenceJob = currentCoroutineContext().job
        mutable.update { it.copy(measuringReference = true) }
        try {
            withTimeout(40000) {
                rawFrames.takeWhile { (id, row) ->
                    check(id == epoch && revision == version && generation == epoch) { "window_measurement_changed" }
                    rows.add(row)
                    val elapsed = (SystemClock.elapsedRealtime() - start).toInt()
                    withContext(Dispatchers.Main) { progress(elapsed.coerceAtMost(30000)) }
                    elapsed < 30000
                }.collect()
            }
            check(rows.size >= 20) { "Not enough fresh readings" }
            return rows
        } finally { referenceJob = null; mutable.update { it.copy(measuringReference = false) } }
    }
    fun close() {
        disconnect()
        runCatching { context.unregisterReceiver(receiver) }
        running = false
    }
    fun setOutOfRangeSimulation(value: Boolean) { mutable.update { it.copy(outOfRangeSimulation = value) } }
    fun reloadPhoneSettings() {
        revisionCounter.incrementAndGet()
        mutable.update { current -> current.copy(
            calibration = loadCalibration(),
            samples = preferences.getInt("samples", 5).coerceIn(1, 100),
            spacingMs = preferences.getLong("spacing", 100).coerceIn(0, 60000), trim = preferences.getBoolean("trim", false), sample = null) }
    }
    private fun acquiredFeedback() {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        UvirUnreadCounters.increment(preferences, KEY_UNREAD_ACQUISITION_COUNT, 1)
        val feedback = loadAcquisitionFeedbackSettings(preferences)
        scope.launch { playThresholdAlertTone(context, feedback.sound, feedback.volume, toneProfile = UvirFeedbackToneProfile.ACQUISITION) }
    }
    private fun interruptedAt(sessions: String, records: String, id: Long): Long = database.readableDatabase.rawQuery(
        "SELECT COALESCE((SELECT MAX(timestamp) FROM $records WHERE session_id = ?), started_at) FROM $sessions WHERE session_id = ?",
        arrayOf(id.toString(), id.toString())).use { if (it.moveToFirst()) it.getLong(0) else System.currentTimeMillis() }
    private fun handleFailure(error: Throwable) {
        UvirErrorLog.record(context, "usb_phone_recording", error)
        if (mutable.value.status != UsbPhoneStatus.ERROR) disconnect()
        mutable.update { it.copy(status = UsbPhoneStatus.ERROR, sample = null, busy = false, error = error.message) }
    }
}

internal object UsbPhoneRuntimeStore {
    private var instance: UvirUsbPhoneRuntime? = null
    @Synchronized fun get(context: Context): UvirUsbPhoneRuntime =
        (instance ?: UvirUsbPhoneRuntime(context.applicationContext).also { instance = it }).also { it.start() }
    @Synchronized fun close() { instance?.close() }
    @Synchronized fun onRecordingServiceStopped() {
        val state = instance?.state?.value ?: return
        if (state.automaticSession != null || state.alertSession != null) instance?.close()
    }
}
