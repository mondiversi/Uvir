package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.hoho.android.usbserial.driver.UsbSerialPort
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.File
import java.lang.reflect.Proxy
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Fake transports and isolated storage: no hardware commands or user records. */
class UvirConnectionHandoverProtocolTest {
    private val deviceId = "HANDOVER-TEST"
    private lateinit var context: Context
    private lateinit var directory: File
    private val preferenceNames = mutableSetOf<String>()

    @Before fun isolateStorage() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "handover_test_${System.nanoTime()}"
        directory = File(base.cacheDir, prefix).apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val isolated = "${prefix}_$name"
                preferenceNames.add(isolated)
                return base.getSharedPreferences(isolated, mode)
            }
        }
    }

    @After fun removeOnlyFixtureStorage() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        preferenceNames.forEach { base.deleteSharedPreferences(it) }
        check(requireNotNull(directory.parentFile).canonicalFile == base.cacheDir.canonicalFile)
        check(directory.name.startsWith("handover_test_"))
        directory.deleteRecursively()
    }

    private fun setField(instance: Any, name: String, value: Any?) {
        instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(instance, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> setState(instance: Any, value: T) {
        val field = instance.javaClass.getDeclaredField("mutableState").apply { isAccessible = true }
        (field.get(instance) as MutableStateFlow<T>).value = value
    }

    private fun wireless(output: ByteArrayOutputStream): UvirWirelessSensorManager =
        UvirWirelessSensorManager(context, deviceId).also {
            setField(it, "desiredMode", SensorConnectionMode.WIFI)
            setField(it, "desiredStreaming", true)
            setField(it, "desiredIntervalMs", 1_000L)
            setField(it, "activeProtocolOutput", output)
            setState(it, UvirWirelessSensorState(status = WirelessSensorConnectionStatus.CONNECTED,
                mode = SensorConnectionMode.WIFI, deviceId = deviceId, appConnectionConfirmed = true))
        }

    private fun receive(manager: UvirWirelessSensorManager, frame: String) {
        manager.javaClass.getDeclaredMethod("parseLine", String::class.java,
            SensorConnectionMode::class.java, UvirSensorCredentials::class.java)
            .apply { isAccessible = true }
            .invoke(manager, frame, SensorConnectionMode.WIFI, UvirSensorCredentials(deviceId = deviceId))
    }

    private val accepted = "{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"wireless_mode\":\"bluetooth\"}"

    private fun hello(boot: String, connected: Boolean = true) = JSONObject()
        .put("type", "hello").put("protocol", "uvir-sensor-v1").put("device_id", deviceId)
        .put("boot_id", boot).put("app_connected", connected).put("sensor_available", true)
        .put("firmware", "0.5.115").toString()

    private fun sample(boot: String, age: Int = 0, valid: Boolean = true) = JSONObject()
        .put("type", "sample").put("protocol", "uvir-sensor-v1").put("boot_id", boot)
        .put("unit", "W/m2").put("seq", 7).put("sample_seq", 3)
        .put("sample_valid", valid).put("sample_age_ms", age).put("sample_max_age_ms", 500)
        .put("bands", JSONObject().put("uva", 1.25).put("blue", 2.5)).toString()

    @Test fun restartedSensorClearsOldReadingsAndReopensTheLogicalSessionExactlyOnce() {
        val output = ByteArrayOutputStream()
        val manager = wireless(output)
        try {
            receive(manager, hello("A")); receive(manager, sample("A"))
            assertNotNull(manager.state.value.sample)
            output.reset()
            receive(manager, hello("B", connected = false))
            assertNull(manager.state.value.sample)
            assertFalse(manager.state.value.appConnectionConfirmed)
            assertEquals(0L, manager.state.value.sampleSequence)
            assertEquals(1, output.toString().lines().count { it == "APP_CONNECT" })
            receive(manager, hello("B", connected = false))
            assertEquals(1, output.toString().lines().count { it == "APP_CONNECT" })
            receive(manager, sample("A")); receive(manager, hello("A"))
            assertNull(manager.state.value.sample)
            receive(manager, sample("B"))
            assertEquals(1.25, requireNotNull(manager.state.value.sample).uva, 0.0)
        } finally { manager.stop() }
    }

    @Test fun staleAndTemporarilyMissingSamplesClearNumbersWithoutInventingZeros() {
        val manager = wireless(ByteArrayOutputStream())
        try {
            receive(manager, hello("A")); receive(manager, sample("A"))
            receive(manager, sample("A", age = 501))
            assertNull(manager.state.value.sample)
            receive(manager, sample("A")); assertNotNull(manager.state.value.sample)
            receive(manager, """{"type":"sample_status","protocol":"uvir-sensor-v1","boot_id":"A","sample_valid":false}""")
            assertNull(manager.state.value.sample)
            receive(manager, sample("A")); assertNotNull(manager.state.value.sample)
        } finally { manager.stop() }
    }

    @Test fun expiredAppSessionWithTheSameBootIsReestablished() {
        val output = ByteArrayOutputStream()
        val manager = wireless(output)
        try {
            receive(manager, hello("A")); receive(manager, sample("A")); output.reset()
            receive(manager, hello("A", connected = false))
            assertNull(manager.state.value.sample)
            assertFalse(manager.state.value.appConnectionConfirmed)
            assertTrue(output.toString().contains("APP_CONNECT\n"))
        } finally { manager.stop() }
    }

    @Test fun closedWifiSocketIsDetectedImmediatelyEvenWhenAvailableReturnsZero() {
        val manager = wireless(ByteArrayOutputStream())
        try {
            val generation = manager.javaClass.getDeclaredField("generation").apply { isAccessible = true }
                .get(manager) as AtomicInteger
            val method = manager.javaClass.getDeclaredMethod("runProtocol", java.io.InputStream::class.java,
                java.io.OutputStream::class.java, UvirSensorCredentials::class.java,
                SensorConnectionMode::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
            val started = System.nanoTime()
            try {
                method.invoke(manager, ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(),
                    UvirSensorCredentials(deviceId = deviceId), SensorConnectionMode.WIFI, generation.get())
                fail("TCP EOF must close the session")
            } catch (error: java.lang.reflect.InvocationTargetException) {
                assertTrue(error.cause is IOException)
                assertEquals("wireless_connection_closed", error.cause?.message)
            }
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1_000)
        } finally { manager.stop() }
    }

    @Test fun malformedAndForeignFramesCannotKeepAConnectionAlive() {
        assertFalse(uvirValidLiveProtocolFrame("garbage", deviceId))
        assertFalse(uvirValidLiveProtocolFrame("{}", deviceId))
        assertFalse(uvirValidLiveProtocolFrame(hello("A").replace(deviceId, "OTHER"), deviceId))
        assertTrue(uvirValidLiveProtocolFrame(hello("A"), deviceId))
        assertTrue(uvirValidLiveProtocolFrame(sample("A"), deviceId))
    }

    @Test fun wirelessStatusConfirmsSwitchWhileBackgroundRefreshRemainsResponsive() {
        val reader = Executors.newSingleThreadExecutor()
        lateinit var manager: UvirWirelessSensorManager
        val output = object : ByteArrayOutputStream() {
            override fun flush() {
                reader.submit {
                    manager.setStreaming(SensorConnectionMode.WIFI, true, 1_000L)
                    receive(manager, accepted)
                }.get(1, TimeUnit.SECONDS)
            }
        }
        manager = wireless(output)
        try {
            assertTrue(manager.requestWirelessModeAndAwait(SensorConnectionMode.BLUETOOTH,
                deviceId = deviceId))
            assertEquals("WIRELESS BLUETOOTH\n", output.toString(StandardCharsets.US_ASCII.name()))
        } finally { manager.stop(); reader.shutdownNow() }
    }

    @Test fun wirelessRejectionReturnsFailureWithoutDroppingTheOldConnection() {
        lateinit var manager: UvirWirelessSensorManager
        val output = object : ByteArrayOutputStream() {
            override fun flush() {
                receive(manager, "{\"type\":\"error\",\"protocol\":\"uvir-sensor-v1\",\"code\":\"bluetooth_disabled\"}")
            }
        }
        manager = wireless(output)
        try {
            assertFalse(manager.requestWirelessModeAndAwait(SensorConnectionMode.BLUETOOTH,
                deviceId = deviceId))
            assertEquals(WirelessSensorConnectionStatus.CONNECTED, manager.state.value.status)
            assertEquals(SensorConnectionMode.WIFI, manager.state.value.mode)
        } finally { manager.stop() }
    }

    @Test fun wirelessHandoverNeverWritesToAnotherDevice() {
        val output = ByteArrayOutputStream()
        val manager = wireless(output)
        try {
            assertFalse(manager.requestWirelessModeAndAwait(SensorConnectionMode.BLUETOOTH,
                deviceId = "OTHER-SENSOR"))
            assertEquals(0, output.size())
        } finally { manager.stop() }
    }

    @Test fun alreadyConnectedRequestedRadioNeedsNoSecondSwitchCommand() {
        val output = ByteArrayOutputStream()
        val manager = wireless(output)
        try {
            assertTrue(manager.requestWirelessModeAndAwait(SensorConnectionMode.WIFI,
                deviceId = deviceId))
            assertEquals(0, output.size())
        } finally { manager.stop() }
    }

    private fun usb(onWrite: (ByteArray) -> Unit): UvirUsbSensorManager {
        val port = Proxy.newProxyInstance(UsbSerialPort::class.java.classLoader,
            arrayOf(UsbSerialPort::class.java)) { _, method, arguments ->
            if (method.name == "write") onWrite(arguments!![0] as ByteArray)
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                else -> null
            }
        } as UsbSerialPort
        return UvirUsbSensorManager(context).also {
            setField(it, "protocolAccepted", true)
            setField(it, "serialPort", port)
            setState(it, UvirUsbSensorState(status = UsbSensorConnectionStatus.CONNECTED,
                deviceId = deviceId, appConnectionConfirmed = true))
        }
    }

    @Test fun usbRebootClearsOldNumbersAndRejectsPreviousBootSamples() {
        UvirSensorCredentialStore.save(context, UvirSensorCredentials(deviceId = deviceId,
            authToken = "isolated-test-token", wifiSsid = "fixture"))
        val manager = usb { }
        val parse = manager.javaClass.getDeclaredMethod("parseLine", String::class.java)
            .apply { isAccessible = true }
        try {
            parse.invoke(manager, hello("A")); parse.invoke(manager, sample("A"))
            assertNotNull(manager.state.value.sample)
            parse.invoke(manager, hello("B", connected = false))
            assertNull(manager.state.value.sample)
            assertFalse(manager.state.value.appConnectionConfirmed)
            parse.invoke(manager, sample("A")); assertNull(manager.state.value.sample)
            parse.invoke(manager, sample("B")); assertNotNull(manager.state.value.sample)
            parse.invoke(manager, sample("B", valid = false)); assertNull(manager.state.value.sample)
        } finally { manager.stop() }
    }

    @Test fun usbStatusConfirmsTheSelectedSensorsWirelessMode() {
        lateinit var manager: UvirUsbSensorManager
        var written = ""
        manager = usb {
            written = String(it, StandardCharsets.US_ASCII)
            manager.javaClass.getDeclaredMethod("parseLine", String::class.java)
                .apply { isAccessible = true }.invoke(manager, accepted)
        }
        try {
            assertTrue(manager.requestWirelessModeAndAwait(deviceId, SensorConnectionMode.BLUETOOTH))
            assertEquals("WIRELESS BLUETOOTH\n", written)
        } finally { manager.stop() }
    }

    @Test fun usbHandoverCannotReachAnotherSensorsCable() {
        var writes = 0
        val manager = usb { writes++ }
        try {
            assertFalse(manager.requestWirelessModeAndAwait("OTHER-SENSOR", SensorConnectionMode.BLUETOOTH))
            assertEquals(0, writes)
        } finally { manager.stop() }
    }
}
