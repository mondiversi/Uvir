package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UvirMultiSensorPersistenceTest {
    private lateinit var context: Context
    private lateinit var database: UvirDatabaseHelper
    private lateinit var directory: File
    private lateinit var persistence: UvirMultiSensorPersistence
    private val acknowledgements = mutableListOf<Triple<String, SensorSyncSource, List<String>>>()
    private val completed = mutableListOf<String>()
    private val preferenceNames = mutableSetOf<String>()
    private val namespace = "multisensor-test-${UUID.randomUUID()}"
    private val a = UvirSensorCredentials(deviceId = "TEST-A", authToken = "secret-a")
    private val b = UvirSensorCredentials(deviceId = "TEST-B", authToken = "secret-b")

    @Before fun setUp() {
        val original = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(original.cacheDir, namespace).also { assertTrue(it.mkdir()) }
        context = object : ContextWrapper(original) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir(): File = directory
            override fun getFilesDir(): File = File(directory, "files").also { it.mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val isolated = "$namespace-$name"
                preferenceNames.add(isolated)
                return super.getSharedPreferences(isolated, mode)
            }
            override fun getDatabasePath(name: String) = File(directory, name)
            override fun openOrCreateDatabase(name: String, mode: Int,
                factory: SQLiteDatabase.CursorFactory?) = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int,
                factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?) =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory, handler)
        }
        database = UvirDatabaseHelper(context).also { it.writableDatabase }
        assertTrue(UvirSensorCredentialStore.save(context, a))
        assertTrue(UvirSensorCredentialStore.restoreAssociatedSensor(context, b))
        persistence = UvirMultiSensorPersistence(context, database, { id, source, commands ->
            acknowledgements.add(Triple(id, source, commands)); true
        }, { id, _ -> completed.add(id) })
    }

    @After fun tearDown() {
        database.close()
        directory.deleteRecursively()
        val original = InstrumentationRegistry.getInstrumentation().targetContext
        preferenceNames.forEach { original.deleteSharedPreferences(it) }
    }

    private fun acquisition(id: String, recordId: Long = 1L, sessionId: Long = 0L) =
        UvirDeviceFrame.Runtime(id, SensorRuntimeEvent.Acquisition(SensorSyncSource.WIRELESS,
            recordId, 1_700_000_000_000L + recordId, sessionId, recordId.toInt(), "", 1, false, 0L,
            SensorSample(uva = if (id == a.deviceId) 10.0 else 20.0), externalCommand = true))

    private fun count(table: String): Int = database.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null)
        .use { it.moveToFirst(); it.getInt(0) }

    @Test fun backgroundAlertCompletionClosesOnlyItsOwnSessionWithoutChangingSelection() {
        fun snapshot(session: Long, active: Boolean) = UvirSensorSettingsSnapshot(
            schemaVersion = 2, firmwareVersion = SENSOR_SETTINGS_SYNC_MIN_FIRMWARE,
            sensorParameters = SensorParameters(), acquisitionParameters = AcquisitionParameters(5, 150, true),
            calibrationSettings = SensorCalibrationSettings(), alertMonitoringEnabled = active,
            alertRepeatSeconds = 30, alertSessionId = session, alertRules = emptyList(),
            wifiEnabled = true, bluetoothEnabled = true, internetEnabled = false,
            internetUsePrimaryWifi = true, wifiSsid = "", internetRelayHost = "",
            internetRelayPort = 8883, lastSyncedAt = 50, updatedAtMs = 100)
        val first = database.nextSessionId()
        val second = database.nextSessionId()
        database.startAlertSession(first, "first", sensorDeviceId = a.deviceId)
        database.upsertSensorSettings(a.deviceId, snapshot(first, true))
        assertNull(reconcileBackgroundSensorSnapshot(context, database, b.deviceId, snapshot(second, true)))
        assertNull(reconcileBackgroundSensorSnapshot(context, database, b.deviceId, snapshot(second, false)))
        database.readableDatabase.rawQuery("SELECT session_id, ended_at FROM alert_sessions ORDER BY session_id", null)
            .use { assertTrue(it.moveToFirst()); assertEquals(first, it.getLong(0)); assertTrue(it.isNull(1))
                assertTrue(it.moveToNext()); assertEquals(second, it.getLong(0)); assertFalse(it.isNull(1)) }
        assertEquals(a.deviceId, UvirSensorCredentialStore.load(context).deviceId)
    }

    @Test fun sameHardwareRecordIdIsIndependentForEachDeviceAndAcknowledgedToItsOwner() = runBlocking {
        persistence.handle(acquisition(a.deviceId))
        assertTrue(UvirSensorCredentialStore.activateAssociatedSensor(context, b.deviceId))
        persistence.handle(acquisition(b.deviceId))
        persistence.handle(acquisition(a.deviceId))
        assertEquals(2, count("acquisitions"))
        assertEquals(listOf(a.deviceId, b.deviceId, a.deviceId), acknowledgements.map { it.first })
        assertEquals(2, context.getSharedPreferences(PREFS_NAME, 0).getInt(KEY_UNREAD_ACQUISITION_COUNT, 0))
        database.readableDatabase.rawQuery(
            "SELECT s.hardware_uid, a.uva FROM acquisitions a JOIN sensors s ON s.id = a.sensor_id ORDER BY s.hardware_uid", null
        ).use { assertTrue(it.moveToFirst()); assertEquals(a.deviceId, it.getString(0)); assertEquals(10.0, it.getDouble(1), 0.0)
            assertTrue(it.moveToNext()); assertEquals(b.deviceId, it.getString(0)); assertEquals(20.0, it.getDouble(1), 0.0) }
    }

    @Test fun backgroundCredentialUpdateDoesNotActivateAnotherDeviceOrUndoNoSensorSelection() {
        assertTrue(UvirSensorCredentialStore.activateAssociatedSensor(context, b.deviceId))
        assertTrue(UvirSensorCredentialStore.updateAssociatedSensor(context, a.copy(firmwareVersion = "new")))
        assertEquals(b.deviceId, UvirSensorCredentialStore.load(context).deviceId)
        assertEquals("new", UvirSensorCredentialStore.loadForDevice(context, a.deviceId)?.firmwareVersion)
        assertTrue(UvirSensorCredentialStore.deactivateSensorSelection(context))
        assertTrue(UvirSensorCredentialStore.updateAssociatedSensor(context, a.copy(firmwareVersion = "newer")))
        assertTrue(UvirSensorCredentialStore.isSensorSelectionDisabled(context))
        assertTrue(UvirSensorCredentialStore.load(context).deviceId.isBlank())
    }

    @Test fun forgottenAssociationCannotBeRestoredByLateTransportReadback() = runBlocking {
        assertTrue(UvirSensorCredentialStore.disassociate(context))
        assertFalse(UvirSensorCredentialStore.updateAssociatedSensor(context, a.copy(firmwareVersion = "late")))
        persistence.handle(acquisition(a.deviceId))
        assertEquals(0, count("acquisitions"))
        assertTrue(acknowledgements.isEmpty())
    }

    @Test fun sensorOriginatedSessionIdsAreMappedSeparatelyForEachUid() = runBlocking {
        val origin = (1L shl 62) + 7L
        persistence.handle(acquisition(a.deviceId, sessionId = origin))
        persistence.handle(acquisition(b.deviceId, sessionId = origin))
        assertEquals(2, count("acquisition_sessions"))
        database.readableDatabase.rawQuery("SELECT COUNT(DISTINCT session_id) FROM acquisitions", null)
            .use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
    }

    @Test fun backgroundExternalRecordJoinsItsOwnManualSessionNotSelectedDevicesSession() = runBlocking {
        database.saveAcquisition(SensorSample(), "manual-a", false, sessionId = 21L,
            sessionSequence = 1, sensorDeviceId = a.deviceId, timestamp = 1_000L)
        context.getSharedPreferences(PREFS_NAME, 0).edit()
            .putLong(sensorContextPreferenceKey(a.deviceId, KEY_LAST_MANUAL_SESSION_ID), 21L).commit()
        assertTrue(UvirSensorCredentialStore.activateAssociatedSensor(context, b.deviceId))
        persistence.handle(acquisition(a.deviceId))
        assertEquals(2, database.acquisitionCountForSession(21L))
        assertEquals("manual-a", database.manualSessionNote(21L))
    }

    @Test fun liveAlertAndRecoveredCopyProduceOneRowAndOneUnreadEvent() = runBlocking {
        val details = "UVA|12.0|ABOVE|10.0"
        val event = SensorLiveAlertEvent(1L, 1_700_000_000_000L, details, recorded = true)
        persistence.handle(UvirDeviceFrame.Alert(a.deviceId, SensorSyncSource.WIRELESS, event, 0))
        persistence.handle(UvirDeviceFrame.Sync(a.deviceId, SensorSyncEvent.Started(SensorSyncSource.WIRELESS, 0, 1, 0, false)))
        persistence.handle(UvirDeviceFrame.Sync(a.deviceId,
            SensorSyncEvent.Alert(SensorSyncSource.WIRELESS, 44L, event.timestampMs, details, 0L)))
        assertEquals(1, count("alerts"))
        assertEquals(1, context.getSharedPreferences(PREFS_NAME, 0).getInt(KEY_UNREAD_ALERT_COUNT, 0))
        database.readableDatabase.rawQuery("SELECT sensor_record_id FROM alerts", null)
            .use { assertTrue(it.moveToFirst()); assertEquals(44L, it.getLong(0)) }
    }

    @Test fun failedPersistenceDoesNotAcknowledgeOrCompleteSync() = runBlocking {
        persistence.handle(UvirDeviceFrame.Sync(a.deviceId, SensorSyncEvent.Started(SensorSyncSource.WIRELESS, 0, 1, 0, false)))
        persistence.handle(UvirDeviceFrame.Sync(a.deviceId, SensorSyncEvent.Alert(SensorSyncSource.WIRELESS, 44L, 1_700L, "", 0L)))
        persistence.handle(UvirDeviceFrame.Sync(a.deviceId, SensorSyncEvent.Complete(SensorSyncSource.WIRELESS,
            0, 1, 0, false, false, 0L, 0, 0L)))
        assertTrue(acknowledgements.isEmpty())
        assertTrue(completed.isEmpty())
    }

    @Test fun twoAuthenticatedConnectionsSurviveSelectionAndContinueSaving() = runBlocking {
        FakeSensor(a).use { first ->
            FakeSensor(b).use { second ->
                assertTrue(UvirSensorCredentialStore.updateAssociatedSensor(context, first.credentials))
                assertTrue(UvirSensorCredentialStore.updateAssociatedSensor(context, second.credentials))
                val prefs = context.getSharedPreferences(PREFS_NAME, 0)
                prefs.edit().putString(KEY_SENSOR_CONNECTION_MODE, SensorConnectionMode.WIFI.name)
                    .putString(sensorContextPreferenceKey(b.deviceId, KEY_SENSOR_CONNECTION_MODE),
                        SensorConnectionMode.WIFI.name).commit()
                val runtime = UvirMultiSensorRuntime(context, UvirUsbSensorManager(context))
                try {
                    runtime.start()
                    withTimeout(15_000L) {
                        while (runtime.indicators.value.values.count { it.dot == UvirStatusDot.GREEN } != 2) delay(30L)
                    }
                    first.emitAcquisition(101L)
                    second.emitAcquisition(101L)
                    withTimeout(5_000L) { while (count("acquisitions") < 2) delay(30L) }
                    assertTrue(saveSelectedSensorContext(prefs, a.deviceId))
                    assertTrue(UvirSensorCredentialStore.activateAssociatedSensor(context, b.deviceId))
                    assertTrue(restoreSelectedSensorContext(prefs, b.deviceId))
                    assertTrue(runtime.wirelessFor(b.deviceId).sendSensorControlCommands(listOf("PING_SELECTED_B")))
                    first.emitAcquisition(102L)
                    second.emitAcquisition(102L)
                    withTimeout(5_000L) { while (count("acquisitions") < 4) delay(30L) }
                    assertEquals(2, runtime.indicators.value.values.count { it.dot == UvirStatusDot.GREEN })
                    withTimeout(5_000L) {
                        while ("ACQUISITION_ACK 102" !in first.commands ||
                            "ACQUISITION_ACK 102" !in second.commands ||
                            "PING_SELECTED_B" !in second.commands) delay(30L)
                    }
                    assertFalse("PING_SELECTED_B" in first.commands)
                    assertEquals(1, first.connections.get())
                    assertEquals(1, second.connections.get())
                    assertEquals(b.deviceId, UvirSensorCredentialStore.load(context).deviceId)
                } finally { runtime.stop() }
            }
        }
    }

    @Test fun simulationOnSelectedADoesNotDisconnectBOrChangeItsRealSession() = runBlocking {
        FakeSensor(a).use { first ->
            FakeSensor(b).use { second ->
                assertTrue(UvirSensorCredentialStore.updateAssociatedSensor(context, first.credentials))
                assertTrue(UvirSensorCredentialStore.updateAssociatedSensor(context, second.credentials))
                val prefs = context.getSharedPreferences(PREFS_NAME, 0)
                prefs.edit().putString(KEY_SENSOR_CONNECTION_MODE, SensorConnectionMode.WIFI.name)
                    .putString(sensorContextPreferenceKey(b.deviceId, KEY_SENSOR_CONNECTION_MODE), SensorConnectionMode.WIFI.name)
                    .putBoolean(sensorContextPreferenceKey(b.deviceId, KEY_AUTO_ENABLED), true)
                    .putLong(sensorContextPreferenceKey(b.deviceId, KEY_AUTO_SESSION_ID), 701L).commit()
                database.startAcquisitionSession(701L, "real B", 1_700_000_000_000L, b.deviceId)
                val runtime = UvirMultiSensorRuntime(context, UvirUsbSensorManager(context))
                try {
                    runtime.start()
                    withTimeout(15_000L) {
                        while (runtime.indicators.value.values.count { it.dot == UvirStatusDot.GREEN } != 2) delay(30L)
                    }
                    second.emitAcquisition(201L, sessionId = 701L)
                    withTimeout(5_000L) { while (database.acquisitionCountForSession(701L) != 1) delay(30L) }
                    UvirSensorSimulationSettingsStore.setEnabled(prefs, a.deviceId, true)
                    UvirSensorSimulationSettingsStore.setOutOfRange(prefs, a.deviceId, true)
                    withTimeout(5_000L) {
                        while (runtime.indicators.value[normalizeSensorDeviceId(a.deviceId)]?.dot != UvirStatusDot.DEBUG) delay(30L)
                    }
                    second.emitAcquisition(202L, sessionId = 701L)
                    withTimeout(5_000L) {
                        while (database.acquisitionCountForSession(701L) != 2 ||
                            "ACQUISITION_ACK 202" !in second.commands) delay(30L)
                    }
                    assertEquals(UvirStatusDot.GREEN, runtime.indicators.value[normalizeSensorDeviceId(b.deviceId)]?.dot)
                    assertEquals(1, second.connections.get())
                    assertTrue(runtime.resources.value.active)
                    assertTrue(runtime.resources.value.wifiRequired)
                    assertFalse(UvirSensorSimulationSettingsStore.load(prefs, b.deviceId).enabled)
                    assertTrue(prefs.getBoolean(sensorContextPreferenceKey(b.deviceId, KEY_AUTO_ENABLED), false))
                    assertEquals(701L, prefs.getLong(sensorContextPreferenceKey(b.deviceId, KEY_AUTO_SESSION_ID), 0L))
                    assertTrue(saveSelectedSensorContext(prefs, a.deviceId))
                    assertTrue(UvirSensorCredentialStore.activateAssociatedSensor(context, b.deviceId))
                    assertTrue(restoreSelectedSensorContext(prefs, b.deviceId))
                    assertFalse(UvirSensorSimulationSettingsStore.load(prefs, b.deviceId).enabled)
                    assertTrue(prefs.getBoolean(KEY_AUTO_ENABLED, false))
                    assertEquals(701L, prefs.getLong(KEY_AUTO_SESSION_ID, 0L))
                    second.emitAcquisition(203L, sessionId = 701L)
                    withTimeout(5_000L) { while (database.acquisitionCountForSession(701L) != 3) delay(30L) }
                    assertEquals(1, second.connections.get())
                    database.readableDatabase.rawQuery("SELECT uva FROM acquisitions WHERE session_id = 701", null).use {
                        while (it.moveToNext()) assertEquals(7.0, it.getDouble(0), 0.0)
                    }
                } finally { runtime.stop() }
            }
        }
    }

    @Test fun encryptedAppBackupRestoresIndependentSimulationChoices() {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        UvirSensorSimulationSettingsStore.setEnabled(prefs, a.deviceId, true)
        UvirSensorSimulationSettingsStore.setOutOfRange(prefs, a.deviceId, true)
        UvirSensorSimulationSettingsStore.setEnabled(prefs, b.deviceId, false)
        val password = "simulation-backup-test".toCharArray()
        val file = createUvirAppSettingsBackup(context, password)
        UvirSensorSimulationSettingsStore.setEnabled(prefs, a.deviceId, false)
        UvirSensorSimulationSettingsStore.setOutOfRange(prefs, a.deviceId, false)
        UvirSensorSimulationSettingsStore.setEnabled(prefs, b.deviceId, true)
        val imported = importUvirSettingsFiles(context, database, listOf(android.net.Uri.fromFile(file)), password)
        assertEquals(1, imported.importedAppFiles)
        assertEquals(UvirSensorSimulationSettings(true, true), UvirSensorSimulationSettingsStore.load(prefs, a.deviceId))
        assertEquals(UvirSensorSimulationSettings(), UvirSensorSimulationSettingsStore.load(prefs, b.deviceId))
        assertEquals(a.deviceId, UvirSensorCredentialStore.load(context).deviceId)
    }

    /** Real UDP discovery + authenticated TCP, never touches the user's sensor. */
    private class FakeSensor(original: UvirSensorCredentials) : java.io.Closeable {
        private val udp = java.net.DatagramSocket(0)
        private val tcp = java.net.ServerSocket(0)
        private val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        private var socket: java.net.Socket? = null
        private var writer: java.io.BufferedWriter? = null
        private val outputLock = Any()
        val commands = java.util.concurrent.CopyOnWriteArrayList<String>()
        val connections = java.util.concurrent.atomic.AtomicInteger()
        val credentials = original.copy(wifiSsid = "isolated-test", wifiPort = tcp.localPort,
            wifiDiscoveryPort = udp.localPort)
        init {
            executor.submit {
                runCatching {
                    while (!udp.isClosed) {
                        val packet = java.net.DatagramPacket(ByteArray(512), 512)
                        udp.receive(packet)
                        val parts = String(packet.data, 0, packet.length, Charsets.US_ASCII).split(' ')
                        if (parts.size != 3 || parts[1] != credentials.deviceId) continue
                        val nonce = parts[2]
                        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
                        mac.init(javax.crypto.spec.SecretKeySpec(credentials.authToken.toByteArray(), "HmacSHA256"))
                        val proof = mac.doFinal(("DISCOVERY:" + credentials.deviceId + ":" + nonce).toByteArray())
                            .joinToString("") { "%02x".format(it.toInt() and 255) }
                        val response = org.json.JSONObject().put("protocol", UVIR_SENSOR_PROTOCOL)
                            .put("type", "discovery").put("device_id", credentials.deviceId)
                            .put("nonce", nonce).put("proof", proof).toString().toByteArray()
                        udp.send(java.net.DatagramPacket(response, response.size, packet.address, packet.port))
                    }
                }
            }
            executor.submit {
                runCatching {
                    socket = tcp.accept()
                    connections.incrementAndGet()
                    writer = socket!!.getOutputStream().bufferedWriter()
                    val input = socket!!.getInputStream().bufferedReader()
                    while (!socket!!.isClosed) {
                        val line = input.readLine() ?: break
                        commands.add(line)
                        when {
                            line == "AUTH " + credentials.authToken || line == "HELLO" -> emit("hello")
                            line == "APP_CONNECT" || line.startsWith("STREAM ") -> emit("status")
                            line == "SYNC_BEGIN" -> { emit("sync_start"); emit("sync_complete") }
                            line == "PING" -> emit("status")
                        }
                    }
                }
            }
        }
        private fun emit(type: String, more: (org.json.JSONObject) -> Unit = {}) {
            val frame = org.json.JSONObject().put("protocol", UVIR_SENSOR_PROTOCOL).put("type", type)
                .put("device_id", credentials.deviceId).put("firmware", "0.5.101")
                .put("app_connected", true).put("streaming", true).put("sensor_available", true)
            more(frame)
            synchronized(outputLock) { writer!!.write(frame.toString()); writer!!.newLine(); writer!!.flush() }
        }
        fun emitAcquisition(id: Long, sessionId: Long = 0L) = emit("acquisition_event") {
            it.put("record_id", id).put("timestamp_ms", 1_700_000_000_000L + id)
                .put("session_id", sessionId).put("sequence", id.toInt()).put("external_command", true)
                .put("bands", org.json.JSONObject().put("uva", 7.0))
        }
        override fun close() {
            runCatching { udp.close() }; runCatching { tcp.close() }; runCatching { socket?.close() }
            executor.shutdownNow()
            executor.awaitTermination(3L, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    @Test fun lateReadbackIsNotOverwrittenWhenTheOldUiContextIsSaved() {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        prefs.edit().putBoolean(KEY_AUTO_ENABLED, false).putInt(KEY_AUTO_COMPLETED_COUNT, 2)
            .putBoolean(sensorContextPreferenceKey(a.deviceId, KEY_AUTO_ENABLED), true)
            .putInt(sensorContextPreferenceKey(a.deviceId, KEY_AUTO_COMPLETED_COUNT), 3)
            .putString(KEY_AUTO_NOTE, "saved draft").commit()
        assertTrue(saveSelectedSensorContext(prefs, a.deviceId, preserveSensorReadback = true))
        assertTrue(restoreSelectedSensorContext(prefs, a.deviceId))
        assertTrue(prefs.getBoolean(KEY_AUTO_ENABLED, false))
        assertEquals(3, prefs.getInt(KEY_AUTO_COMPLETED_COUNT, 0))
        assertEquals("saved draft", prefs.getString(KEY_AUTO_NOTE, ""))
    }

    @Test fun backgroundDiagnosticSnapshotDoesNotReplaceSelectedDevice() {
        UvirSensorRuntimeInfoStore.save(context, SensorConnectionMode.WIFI, UvirSensorRuntimeInfo(deviceId = a.deviceId))
        UvirSensorRuntimeInfoStore.saveForDevice(context, SensorConnectionMode.BLUETOOTH, UvirSensorRuntimeInfo(deviceId = b.deviceId))
        assertEquals(a.deviceId, UvirSensorRuntimeInfoStore.load(context)?.info?.deviceId)
        assertTrue(UvirSensorRuntimeInfoStore.activate(context, b.deviceId))
        assertEquals(b.deviceId, UvirSensorRuntimeInfoStore.load(context)?.info?.deviceId)
    }
}
