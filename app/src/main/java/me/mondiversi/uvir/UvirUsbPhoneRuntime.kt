package me.mondiversi.uvir

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.*
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val overloadSample: SensorSample? = null,
    val overloadAtMs: Long = 0,
    val sampleAtMs: Long = 0,
    val freshnessMs: Long = 500,
    val captureDeadlineMs: Long = 0,
    val sequence: Long = 0,
    val uvGain: Double? = null,
    val visibleGain: Double? = null,
    val error: String? = null,
    val simulation: Boolean = false,
    val automaticSession: Long? = null,
    val automaticCount: Int = 0,
    val automaticWaitingForCondition: Boolean = false,
    val alertSession: Long? = null,
    val alertCount: Int = 0,
    val measuringReference: Boolean = false,
    val busy: Boolean = false,
    val manualSession: Long? = null,
    val manualCount: Int = 0,
    val saturationSimulation: Boolean = false,
    val calibration: PhoneCalibration = PhoneCalibration(),
    val samples: Int = 1,
    val spacingMs: Long = 100,
    val trim: Boolean = false,
    val adapterName: String = "",
    val usbPermission: Boolean = false,
    val diagnosticRunning: Boolean = false,
    val hardwareChecks: UsbHeadChecks? = null
) {
    val ready: Boolean get() = (status == UsbPhoneStatus.READY || simulation) && sample != null && overloadSample == null
    val liveSample: SensorSample? get() = overloadSample ?: sample
    val liveReady: Boolean get() = (status == UsbPhoneStatus.READY || simulation) && liveSample != null
    val liveAtMs: Long get() = if (overloadSample != null) overloadAtMs else sampleAtMs
}

internal fun UsbPhoneState.expireOpticalReading(nowMs: Long): UsbPhoneState =
    if (!simulation && liveSample != null && nowMs - liveAtMs > freshnessMs &&
        (captureDeadlineMs == 0L || nowMs > captureDeadlineMs))
        copy(sample = null, overloadSample = null, captureDeadlineMs = 0) else this

internal fun UsbPhoneState.beginOpticalCapture(nowMs: Long): UsbPhoneState =
    copy(captureDeadlineMs = if (!simulation && status == UsbPhoneStatus.READY && liveSample != null &&
        (error == null || overloadSample != null) && nowMs - liveAtMs in 0..freshnessMs)
        nowMs + OPTICAL_CAPTURE_GRACE_MS else 0)

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
    private val referenceFrames = MutableSharedFlow<PhoneOpticalFrame>(extraBufferCapacity = 2)
    private class ReferenceCapture(val group: CalibrationGroup?) {
        var lockUv = false
        var lockVisible = false
    }
    @Volatile private var activeReference: ReferenceCapture? = null
    private var freshnessJob: Job? = null
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
    private val diagnosticMutex = Mutex()

    private val permissionAction = "${context.packageName}.FT232H_PERMISSION"
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    @Suppress("DEPRECATION") val detached = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (detached != null && (detached.deviceName == device?.deviceName || detached.deviceName == permissionRequests.requestedDeviceName ||
                            detached.deviceName == mutable.value.adapterName)) {
                        permissionRequests.clear()
                        Log.i("UvirUsb", "USB detached by Android")
                        disconnect()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    @Suppress("DEPRECATION") val attached = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (attached == null || !isFt232h(attached)) return
                    Log.i("UvirUsb", "USB attached by Android")
                    // Android is also resolving/opening the matching app. Do not put a
                    // second access dialog over that system confirmation.
                    scan(mayRequestPermission = false)
                }
                permissionAction -> {
                    @Suppress("DEPRECATION") val granted = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (!permissionRequests.acceptsReply(granted?.deviceName,
                            granted != null && usb.deviceList.containsKey(granted.deviceName)) || granted == null || !isFt232h(granted)) return
                    // The system chooser may already have granted access while an older
                    // callback was pending. A stale denial cannot tear down that connection.
                    if (usb.hasPermission(granted)) connect(granted)
                    else mutable.update { it.copy(status = UsbPhoneStatus.ERROR, sample = null, overloadSample = null,
                        usbPermission = false, error = "USB permission denied") }
                }
            }
        }
    }

    fun start() {
        if (running) return
        running = true
        // Independent of blocking USB calls: a stalled reader cannot leave old values live.
        freshnessJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                val before = mutable.value
                val expired = before.expireOpticalReading(now)
                if (expired !== before && mutable.compareAndSet(before, expired)) {
                    Log.w("UvirUsb", "Live sample expired; age=${now - before.liveAtMs}ms; " +
                        "budget=${before.freshnessMs}ms; sequence=${before.sequence}")
                }
                delay(100)
            }
        }
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
            mutable.update { it.copy(status = UsbPhoneStatus.ERROR, adapterName = "", usbPermission = false,
                hardwareChecks = null, sample = null, overloadSample = null, error = "Connect one FT232H at a time") }; return
        }
        val found = candidates.singleOrNull() ?: run {
            mutable.update { it.copy(status = UsbPhoneStatus.DETACHED, adapterName = "", usbPermission = false,
                hardwareChecks = null, identity = "", uvAvailable = false, visibleAvailable = false,
                sample = null, overloadSample = null) }
            return
        }
        mutable.update { it.copy(adapterName = found.deviceName, usbPermission = usb.hasPermission(found)) }
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
        Log.i("UvirUsb", "Opening FT232H; generation=$epoch")
        mutable.update { it.copy(status = UsbPhoneStatus.INITIALIZING, sample = null, overloadSample = null, error = null,
            adapterName = found.deviceName, usbPermission = true, uvAvailable = false, visibleAvailable = false,
            hardwareChecks = UsbHeadChecks(epoch, found.deviceName)) }
        loop = scope.launch {
            var localBus: Ft232hMpsse? = null
            try {
                if (epoch != generation || mutable.value.simulation) return@launch
                val link = Ft232hMpsse(AndroidFt232h(usb, found))
                localBus = link
                bus = link
                mutable.update { if(epoch == generation) it.copy(hardwareChecks = it.hardwareChecks?.copy(usbOpened = true)) else it }
                link.initialize()
                mutable.update { if(epoch == generation) it.copy(hardwareChecks = it.hardwareChecks?.copy(mpsseReady = true, i2cReady = true)) else it }
                val uv = As7331(link)
                val visible = As7343(link)
                var visibleTraceCount = 0
                visible.onCaptureTrace = { gain, next, peak, saturated, elapsed ->
                    if (visibleTraceCount++ < 30) Log.d("UvirUsb", "VIS conversion: gainIndex=$gain; " +
                        "next=$next; ADCpeak=$peak; saturated=$saturated; duration=${elapsed}ms")
                }
                val (uvResult, visibleResult) = checkUsbOpticalModules(uv, visible,
                    isCurrent = { epoch == generation && usb.deviceList.containsKey(found.deviceName) },
                    onError = { action, error -> UvirErrorLog.record(context, action, error) })
                ensureActive()
                if (epoch != generation) return@launch
                val identity = usbHardwareIdentity(runCatching { found.serialNumber }.getOrNull(), UUID.randomUUID().toString())
                val persistent = !identity.startsWith("FT232H-TEMP:")
                mutable.update { it.copy(identity = identity, persistentIdentity = persistent,
                    uvAvailable = uvResult.initialized == true, visibleAvailable = visibleResult.initialized == true,
                    hardwareChecks = it.hardwareChecks?.copy(uv = uvResult, visible = visibleResult, complete = true)) }
                if (uvResult.initialized != true || visibleResult.initialized != true) throw InvalidSensorReading(
                    listOfNotNull(uvResult.error, visibleResult.error).joinToString("; "))
                mutable.update { it.copy(status = UsbPhoneStatus.READY) }
                Log.i("UvirUsb", "FT232H initialized; UV=${uvResult.passed}; VIS_IR=${visibleResult.passed}")
                var firstReading = true
                val window = PhoneSamplingWindow()
                val overloadPresentation = UsbOverloadPresentation()
                var captureDuration = 500L
                var captureCount = 0L
                while (isActive && epoch == generation) {
                    val (config, configRevision) = synchronized(this@UvirUsbPhoneRuntime) { mutable.value to revision }
                    val reference = activeReference
                    val started = SystemClock.elapsedRealtime()
                    mutable.update { it.beginOpticalCapture(started) }
                    val reading = readPhoneOpticalFrame(uv, visible, epoch, configRevision,
                        reference?.lockUv == true, reference?.lockVisible == true, SystemClock::elapsedRealtime)
                    ensureActive()
                    if (epoch != generation) break
                    if (configRevision != revision) {
                        mutable.update { it.copy(captureDeadlineMs = 0).expireOpticalReading(SystemClock.elapsedRealtime()) }
                        continue
                    }
                    val duration = (reading.capturedAtMs - started).coerceAtLeast(1)
                    captureCount++
                    if (captureCount % 20L == 0L || duration > mutable.value.freshnessMs) {
                        Log.d("UvirUsb", "Optical cycle=$captureCount; duration=${duration}ms; " +
                            "complete=${reading.complete != null}; UVgain=${reading.uv?.gain}; VISgain=${reading.visible?.gain}")
                    }
                    // Grow promptly when real I/O is slower, settle gradually afterwards.
                    if (reading.complete != null) captureDuration = maxOf(duration, (captureDuration * 3 + duration) / 4)
                    val freshness = opticalFreshnessMs(config.spacingMs, captureDuration)
                    val overload = overloadPresentation.add(reading, config.calibration,
                        config.samples, config.trim, freshness, started,
                        recoveringFlags = config.overloadSample?.qualityFlags ?: 0)
                    updateOpticalCheck(epoch, true, reading.uv != null, reading.uvError?.message)
                    updateOpticalCheck(epoch, false, reading.visible != null, reading.visibleError?.message)
                    mutable.update { it.expireOpticalReading(reading.capturedAtMs).copy(uvGain = reading.uv?.gain ?: it.uvGain,
                        visibleGain = reading.visible?.gain ?: it.visibleGain, freshnessMs = freshness) }
                    if (reference != null && activeReference === reference) {
                        // First valid conversion auto-ranges; subsequent references keep its gain.
                        if (reference.group == null || reference.group == CalibrationGroup.UV)
                            if (reading.uv != null) reference.lockUv = true
                        if (reference.group == null || reference.group != CalibrationGroup.UV)
                            if (reading.visible != null) reference.lockVisible = true
                        referenceFrames.emit(reading)
                    }
                    val frame = reading.complete
                    if (frame != null) {
                        // Keep the last explicit OL display during recovery until the
                        // configured full average is ready; never flash a disconnected card.
                        mutable.update { if (it.overloadSample != null && overload != null)
                            it.copy(overloadSample = overload, overloadAtMs = reading.capturedAtMs) else it }
                        rawFrames.emit(epoch to frame)
                        val frames = window.add(frame, configRevision, config.samples, reading.capturedAtMs, freshness, started)
                        if (frames != null) {
                            if (firstReading || mutable.value.error != null) {
                                Log.i("UvirUsb", "Fresh optical readings available; generation=$epoch")
                                firstReading = false
                            }
                            publish(processPhoneMeasurement(frames, config.calibration, config.trim), reading.capturedAtMs)
                        }
                    } else {
                        val error = requireNotNull(reading.uvError ?: reading.visibleError)
                        if (mutable.value.error != error.message) {
                            UvirErrorLog.record(context, "usb_optical_reading", error)
                            Log.w("UvirUsb", "Optical reading temporarily unavailable; USB remains attached: ${error.message}")
                        }
                        // Saturation is a connected-head diagnostic, not a disconnect. Only
                        // the separate display path can expose it; recording still needs a full pair.
                        mutable.update { it.copy(error = error.message, captureDeadlineMs = 0,
                            overloadSample = overload, overloadAtMs = reading.capturedAtMs)
                            .expireOpticalReading(reading.capturedAtMs) }
                        window.expire(reading.capturedAtMs)
                    }
                    mutable.update { it.copy(captureDeadlineMs = 0).expireOpticalReading(SystemClock.elapsedRealtime()) }
                    yield()
                    delay((config.spacingMs - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(1))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                UvirErrorLog.record(context, "usb_i2c", error)
                if (epoch == generation) {
                    Log.e("UvirUsb", "USB/I2C reading stopped; physically attached=${usb.deviceList.containsKey(found.deviceName)}", error)
                    finishActivities()
                    mutable.update {
                        val check = it.hardwareChecks
                        it.copy(status = UsbPhoneStatus.ERROR, sample = null, overloadSample = null, error = error.message,
                            hardwareChecks = if (check?.complete == true) {
                                if(error is I2cNack || error is InvalidSensorReading) check
                                else check.copy(usbOpened = false, mpsseReady = null, i2cReady = null,
                                    uv = check.uv.copy(readable = null), visible = check.visible.copy(readable = null),
                                    error = error.message ?: error.javaClass.simpleName)
                            } else check?.copy(
                                mpsseReady = if(error is I2cLinesHeldLow) true else false.takeIf { check.usbOpened == true },
                                i2cReady = false.takeIf { error is I2cLinesHeldLow }, complete = true,
                                error = error.message ?: error.javaClass.simpleName))
                    }
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
        mutable.update { it.copy(status = UsbPhoneStatus.DETACHED, sample = null, overloadSample = null, identity = "", uvGain = null,
            visibleGain = null, uvAvailable = false, visibleAvailable = false, manualSession = null, manualCount = 0,
            adapterName = "", usbPermission = false, hardwareChecks = null) }
    }
    fun retry() { permissionRequests.clear(); disconnect(); scope.launch { delay(150); withContext(Dispatchers.Main) { scan() } } }

    suspend fun diagnosticProbe(hardwareId: String, mode: SensorConnectionMode): UvirDiagnosticProbe =
        if (mode != SensorConnectionMode.USB) UvirDiagnosticProbe(UvirDiagnosticOutcome.CONNECTION_CHANGED)
        else probeUsbPhoneDiagnostic(hardwareId, state, rawFrames, { generation },
            timeoutMs = maxOf(5_000L, mutable.value.spacingMs + 3_000L))

    private fun updateOpticalCheck(epoch: Long, ultraviolet: Boolean, readable: Boolean, error: String? = null) {
        mutable.update { state ->
            val checks = state.hardwareChecks
            if(epoch != generation || checks == null || checks.generation != epoch) state else {
                val updated = (if(ultraviolet) checks.uv else checks.visible).copy(readable = readable, error = error)
                state.copy(hardwareChecks = if(ultraviolet) checks.copy(uv = updated) else checks.copy(visible = updated))
            }
        }
    }

    /** Reuses the single USB owner. Healthy sessions are only observed; a failed idle
     * connection gets one explicit fresh initialization attempt, never a parallel bus owner. */
    suspend fun runHardwareDiagnostics(progress: (Int) -> Unit): UvirSensorDiagnosticReport = diagnosticMutex.withLock {
        check(usbHeadDiagnosticAvailable(mutable.value))
        val started = System.currentTimeMillis()
        val startNanos = System.nanoTime()
        val expectedName = mutable.value.adapterName
        mutable.update { it.copy(diagnosticRunning = true) }
        try {
            if(mutable.value.status != UsbPhoneStatus.READY) {
                check(mutable.value.automaticSession == null && mutable.value.alertSession == null && !mutable.value.busy)
                val before = generation
                retry()
                withTimeoutOrNull(20_000) {
                    state.first { snapshot ->
                        snapshot.hardwareChecks?.let { it.generation > before && it.complete } == true ||
                            snapshot.adapterName.isNotBlank() && snapshot.adapterName != expectedName ||
                            generation > before && (!usb.deviceList.containsKey(expectedName) ||
                                snapshot.status == UsbPhoneStatus.ERROR && !snapshot.usbPermission)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            val initial = mutable.value
            val epoch = generation
            val sameHead = { generation == epoch && mutable.value.adapterName == expectedName &&
                usb.deviceList.containsKey(expectedName) && !mutable.value.simulation }
            var checks = initial.hardwareChecks?.takeIf { it.adapterName == expectedName && it.generation == epoch }
                ?: UsbHeadChecks(epoch,expectedName,complete = true,error = "USB initialization did not complete")
            progress(1)
            val report = if(sameHead() && usbPhoneDiagnosticAvailable(initial)) {
                runUvirSensorDiagnostics(context.getString(R.string.app_name),SensorConnectionMode.USB,
                    usbPhoneDiagnosticInfo(initial),
                    onProgress = { count -> progress(if(count == UVIR_DIAGNOSTIC_REQUESTS) 3 else 2) },
                    probe = { diagnosticProbe(initial.identity,SensorConnectionMode.USB) })
            } else UvirSensorDiagnosticReport(started,0,context.getString(R.string.app_name),SensorConnectionMode.USB,
                usbPhoneDiagnosticInfo(initial),emptyList())
            if(!sameHead() || report.probes.any { it.outcome == UvirDiagnosticOutcome.CONNECTION_CHANGED }) {
                checks = UsbHeadChecks(epoch,expectedName,usbOpened = false,complete = true,error = "USB connection changed")
            } else if(report.probes.isNotEmpty()) {
                checks = mutable.value.hardwareChecks ?: checks
                if(report.responses != UVIR_DIAGNOSTIC_REQUESTS) {
                    val latest = checks
                    checks = latest.copy(
                        uv = latest.uv.copy(readable = false.takeIf { latest.uv.readable == false }),
                        visible = latest.visible.copy(readable = false.takeIf { latest.visible.readable == false }))
                    UvirErrorLog.record(context,"usb_diagnostic_fresh_readings",IllegalStateException("Incomplete fresh optical readings"))
                }
            }
            progress(3)
            report.copy(startedAtMs = started,durationMs = (System.nanoTime()-startNanos)/1_000_000,usbChecks = checks).also {
                if(!it.successful) UvirErrorLog.record(context,"usb_head_diagnostic",IllegalStateException(
                    "USB=${checks.usbOpened}; MPSSE=${checks.mpsseReady}; I2C=${checks.i2cReady}; " +
                        "UV=${checks.uv}; VIS_IR=${checks.visible}; ${checks.error.orEmpty()}"))
            }
        } finally { mutable.update { it.copy(diagnosticRunning = false) } }
    }

    fun setSimulation(enabled: Boolean) {
        if (mutable.value.automaticSession != null || mutable.value.alertSession != null || mutable.value.busy || mutable.value.diagnosticRunning) return
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
                            .copy(qualityFlags = simulatedUsbQualityFlags(mutable.value, t)))
                        delay(350)
                    }
                }
            } else withContext(Dispatchers.Main) { scan() }
        }
    }

    private suspend fun publish(sample: SensorSample, capturedAtMs: Long = SystemClock.elapsedRealtime()) {
        mutable.update { it.copy(sample = sample, overloadSample = null, sampleAtMs = capturedAtMs, captureDeadlineMs = 0,
            sequence = it.sequence + 1, error = null) }
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
            if (manualId != null) database.ensureAcquisitionSession(manualId, sessionNote, System.currentTimeMillis())
            check(database.saveAcquisition(sample, sessionNote, sessionId = manualId,
                sessionSequence = manualId?.let { count }) > 0)
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
                    mutable.update { it.copy(automaticWaitingForCondition = false) }
                    if (request.useDuration) autoEndAt = now + request.durationSeconds * 1000
                }
                val id = mutable.value.automaticSession ?: return@let
                check(database.saveAcquisition(sample, request.note, true, id, mutable.value.automaticCount + 1) > 0)
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
                    database.insertThresholdAlertLog(violations, sessionId = id,
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
        database.startAcquisitionSession(id, request.note)
        preferences.edit().putLong("open_acquisition", id).commit()
        auto = request
        autoStarted = SystemClock.elapsedRealtime()
        autoFirst = autoStarted + if (request.useStartDelay) request.startDelaySeconds * 1000 else 0
        autoNextAt = autoFirst
        autoConditionStarted = false
        autoEndAt = if (request.useDuration && request.conditionalPlan?.action != AcquisitionConditionAction.START)
            autoFirst + request.durationSeconds * 1000 else null
        mutable.update { it.copy(automaticSession = id, automaticCount = 0,
            automaticWaitingForCondition = request.conditionalPlan?.action == AcquisitionConditionAction.START) }
        keepAlive(true)
        ensureActivityTimer()
    }
    fun stopAutomatic() = scope.launch { stopAutomaticNow() }
    private fun stopAutomaticNow() {
        mutable.value.automaticSession?.let(database::finishAcquisitionSession)
        preferences.edit().remove("open_acquisition").apply()
        auto = null
        mutable.update { it.copy(automaticSession = null, automaticWaitingForCondition = false) }
        keepAlive(mutable.value.alertSession != null)
    }
    fun startAlerts(settings: ThresholdAlertSettings, note: String = "") = scope.launch {
        if (!mutable.value.ready || mutable.value.alertSession != null || settings.rules.none { it.enabled }) return@launch
        alerts = settings.copy(enabled = true)
        val id = database.nextSessionId()
        database.startAlertSession(id, note)
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

    @Synchronized fun setSampling(samples: Int, spacing: Long, trim: Boolean) {
        if (mutable.value.measuringReference) return
        require(samples in 1..100 && spacing in 0..60000)
        revisionCounter.incrementAndGet()
        preferences.edit().putInt("samples", samples).putLong("spacing", spacing).putBoolean("trim", trim).apply()
        mutable.update { it.copy(samples = samples, spacingMs = spacing, trim = trim, sample = null, overloadSample = null) }
    }
    @Synchronized fun setCalibration(value: PhoneCalibration) {
        if (mutable.value.measuringReference) return
        revisionCounter.incrementAndGet()
        preferences.edit().putString(USB_PHONE_CALIBRATION, encodeUsbPhoneCalibration(value)).apply()
        mutable.update { it.copy(calibration = value, sample = null, overloadSample = null) }
    }
    @Synchronized fun updateCalibration(transform: (PhoneCalibration) -> PhoneCalibration) {
        setCalibration(transform(mutable.value.calibration))
    }
    private fun loadCalibration(): PhoneCalibration = runCatching {
        decodeUsbPhoneCalibration(preferences.getString(USB_PHONE_CALIBRATION, null) ?: return PhoneCalibration())
    }.getOrElse { PhoneCalibration() }

    suspend fun measureOffsets(group: CalibrationGroup, progress: (Int) -> Unit): DarkOffsetReading {
        val readings = collectReference(group, progress).map { it.referenceValues() }
            .filter { row -> group.indices.all { row[it].isFinite() } }
        check(readings.size >= OPTICAL_WINDOW_MIN_SAMPLES) { "Not enough fresh readings" }
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
        val frames = collectReference(null, progress)
        val rows = frames.map { it.referenceValues() }.map { raw -> DoubleArray(11) { i ->
            maxOf(0.0, raw[i] - config.calibration.offsets[i]) * config.calibration.factors[i] } }
        return OpticalWindowReading(measurementContext, List(11) { i ->
            opticalWindowChannelStatistics(rows.map { it[i] }.filter(Double::isFinite),
                available = if (i < 3) config.uvAvailable else config.visibleAvailable,
                saturated = frames.any { (if (i < 3) it.uvError else it.visibleError)?.saturated == true })
        }, isCurrent = { revision == version && generation == epoch && mutable.value.ready })
    }
    private suspend fun collectReference(group: CalibrationGroup?, progress: (Int) -> Unit): List<PhoneOpticalFrame> {
        check(mutable.value.ready && !mutable.value.simulation && !mutable.value.measuringReference && !mutable.value.diagnosticRunning)
        val epoch = generation
        val version = revision
        val request = ReferenceCapture(group)
        referenceJob = currentCoroutineContext().job
        activeReference = request
        mutable.update { it.copy(measuringReference = true) }
        try {
            return collectPhoneOpticalReference(referenceFrames, epoch, version,
                isCurrent = { generation == epoch && revision == version && activeReference === request },
                progress = { withContext(Dispatchers.Main) { progress(it) } })
        } finally {
            if (activeReference === request) {
                activeReference = null
                referenceJob = null
                mutable.update { it.copy(measuringReference = false) }
            }
        }
    }
    fun close() {
        disconnect()
        freshnessJob?.cancel(); freshnessJob = null
        runCatching { context.unregisterReceiver(receiver) }
        running = false
    }
    fun setSaturationSimulation(value: Boolean) { mutable.update { it.copy(saturationSimulation = value) } }
    @Synchronized fun reloadPhoneSettings() {
        revisionCounter.incrementAndGet()
        mutable.update { current -> current.copy(
            calibration = loadCalibration(),
            samples = preferences.getInt("samples", 5).coerceIn(1, 100),
            spacingMs = preferences.getLong("spacing", 100).coerceIn(0, 60000), trim = preferences.getBoolean("trim", false),
            sample = null, overloadSample = null) }
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
        mutable.update { it.copy(status = UsbPhoneStatus.ERROR, sample = null, overloadSample = null,
            busy = false, error = error.message) }
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
