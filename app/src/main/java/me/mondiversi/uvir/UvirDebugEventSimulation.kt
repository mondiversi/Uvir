package me.mondiversi.uvir

internal data class UvirDebugSimulationResult(
    val acquisitions: Int,
    val alerts: Int
)

/**
 * Adds a compact but representative history without changing live jobs or
 * sensor settings. IDs still come from the normal global counters.
 */
internal fun UvirDatabaseHelper.insertDebugSimulationEvents(
    selectedSensorDeviceId: String,
    note: String,
    now: Long = System.currentTimeMillis()
): UvirDebugSimulationResult {
    val sensorDeviceId = selectedSensorDeviceId.trim()
    require(sensorDeviceId.isNotBlank()) { "Debug events require an explicitly selected sensor" }
    val base = now - 6L * 60L * 60L * 1_000L
    var acquisitionCount = 0
    var alertCount = 0

    fun sample(index: Int, qualityFlags: Int = 0): SensorSample {
        val factor = 1.0 + index * 0.12
        return SensorSample(
            uvc = 0.018 * factor,
            uvb = 0.065 * factor,
            uva = 0.84 * factor,
            violetto = 12.4 * factor,
            blu = 21.8 * factor,
            verde = 34.2 * factor,
            giallo = 26.7 * factor,
            arancione = 18.3 * factor,
            rosso = 15.6 * factor,
            f8 = 8.9 * factor,
            nir = 17.5 * factor,
            qualityFlags = qualityFlags
        )
    }

    fun save(
        timestamp: Long,
        index: Int,
        automatic: Boolean,
        sessionId: Long? = null,
        sequence: Int? = null,
        external: Boolean = false,
        qualityFlags: Int = 0
    ) {
        check(
            saveAcquisition(
                sample = sample(index, qualityFlags),
                note = note,
                automatic = automatic,
                sessionId = sessionId,
                sessionSequence = sequence,
                sensorDeviceId = sensorDeviceId,
                externalCommand = external,
                timestamp = timestamp
            ) != -1L
        )
        acquisitionCount += 1
    }

    // Standalone manual and external acquisitions.
    save(base + 5L * 60L * 1_000L, 1, automatic = false)
    save(base + 12L * 60L * 1_000L, 2, automatic = false, external = true)

    // Manual session.
    val manualSession = nextSessionId()
    val manualStart = base + 30L * 60L * 1_000L
    startAcquisitionSession(manualSession, note, manualStart, sensorDeviceId)
    repeat(3) { index ->
        save(
            timestamp = manualStart + index * 18_000L,
            index = index + 3,
            automatic = false,
            sessionId = manualSession,
            sequence = index + 1
        )
    }
    finishAcquisitionSession(manualSession, manualStart + 36_000L)

    // Automatic session with two variants for each acquisition position.
    val automaticSession = nextSessionId()
    val automaticStart = base + 70L * 60L * 1_000L
    startAcquisitionSession(automaticSession, note, automaticStart, sensorDeviceId)
    repeat(6) { index ->
        save(
            timestamp = automaticStart + index * 12_000L,
            index = index + 6,
            automatic = true,
            sessionId = automaticSession,
            sequence = index + 1,
            qualityFlags =
                if (index == 4) UVIR_QUALITY_VISIBLE_NIR_OUT_OF_RANGE else 0
        )
    }
    check(updateAcquisitionSessionVariantsPerPosition(automaticSession, 2))
    finishAcquisitionSession(automaticSession, automaticStart + 60_000L)

    // Session controlled by the external input.
    val externalSession = nextSessionId()
    val externalStart = base + 110L * 60L * 1_000L
    startAcquisitionSession(
        externalSession,
        note,
        externalStart,
        sensorDeviceId,
        externalCommand = true
    )
    repeat(4) { index ->
        save(
            timestamp = externalStart + index * 9_000L,
            index = index + 12,
            automatic = true,
            sessionId = externalSession,
            sequence = index + 1,
            external = true
        )
    }
    finishAcquisitionSession(externalSession, externalStart + 27_000L)

    fun violation(
        metric: ThresholdAlertMetric,
        value: Double,
        threshold: Float,
        direction: ThresholdAlertDirection = ThresholdAlertDirection.ABOVE
    ): ThresholdAlertViolation =
        ThresholdAlertViolation(
            rule =
                ThresholdAlertRule(
                    metric = metric,
                    enabled = true,
                    direction = direction,
                    threshold = threshold
                ),
            value = value
        )

    fun addAlert(
        sessionId: Long,
        timestamp: Long,
        violations: List<ThresholdAlertViolation>,
        qualityFlags: Int = 0
    ) {
        check(
            insertThresholdAlertLog(
                violations = violations,
                timestamp = timestamp,
                sessionId = sessionId,
                sensorDeviceId = sensorDeviceId,
                qualityFlags = qualityFlags
            ) != -1L
        )
        alertCount += 1
    }

    val alertSessionOne = nextSessionId()
    val alertStartOne = base + 155L * 60L * 1_000L
    startAlertSession(alertSessionOne, note, alertStartOne, sensorDeviceId)
    addAlert(alertSessionOne, alertStartOne, listOf(violation(ThresholdAlertMetric.UVA, 1.42, 1.0f)))
    addAlert(
        alertSessionOne,
        alertStartOne + 15_000L,
        listOf(
            violation(ThresholdAlertMetric.UVB, 0.19, 0.12f),
            violation(ThresholdAlertMetric.HEV, 42.0, 30f)
        )
    )
    addAlert(
        alertSessionOne,
        alertStartOne + 30_000L,
        listOf(violation(ThresholdAlertMetric.NIR, 8.0, 12f, ThresholdAlertDirection.BELOW))
    )
    finishAlertSession(alertSessionOne, alertStartOne + 30_000L)

    val alertSessionTwo = nextSessionId()
    val alertStartTwo = base + 190L * 60L * 1_000L
    startAlertSession(alertSessionTwo, note, alertStartTwo, sensorDeviceId)
    addAlert(
        alertSessionTwo,
        alertStartTwo,
        listOf(violation(ThresholdAlertMetric.BIO_DNA_UV, 2.6, 1.5f))
    )
    addAlert(
        alertSessionTwo,
        alertStartTwo + 20_000L,
        listOf(violation(ThresholdAlertMetric.BIO_UVA_PHOTOAGING, 3.4, 2f))
    )
    addAlert(
        alertSessionTwo,
        alertStartTwo + 40_000L,
        listOf(violation(ThresholdAlertMetric.BIO_HEV_OXIDATIVE, 4.8, 3f))
    )
    finishAlertSession(alertSessionTwo, alertStartTwo + 40_000L)

    return UvirDebugSimulationResult(acquisitionCount, alertCount)
}
