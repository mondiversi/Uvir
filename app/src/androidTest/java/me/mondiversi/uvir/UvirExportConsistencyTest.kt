package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Synthetic files, two synthetic profiles and isolated preferences/database; no real transport. */
class UvirExportConsistencyTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "export-consistency-${UUID.randomUUID()}-"
    private val directory = File(base.cacheDir, prefix)
    private val preferences = mutableSetOf<String>()
    private lateinit var context: Context
    private lateinit var records: List<SavedRecordDetail>
    private lateinit var alerts: List<ThresholdAlertLogEntry>

    private inner class ScopedContext(base: Context) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getCacheDir() = directory
        override fun getDatabasePath(name: String) = File(directory, name)
        override fun createConfigurationContext(configuration: Configuration): Context = ScopedContext(super.createConfigurationContext(configuration))
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val scoped = prefix + name
            preferences.add(scoped)
            return super.getSharedPreferences(scoped, mode)
        }
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory, errorHandler)
    }

    @Before fun prepare() {
        assertTrue(directory.mkdir())
        context = ScopedContext(base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.ITALY) }))
        val profiles = UvirDatabaseHelper(context).use { db ->
            listOf(db.ensureSensorProfile("SOURCE_A")!!, db.ensureSensorProfile("SOURCE_B")!!)
        }
        profiles.forEachIndexed { i, profile ->
            UvirSensorRuntimeInfoStore.save(context, SensorConnectionMode.WIFI,
                UvirSensorRuntimeInfo(deviceId = profile.hardwareUid, protocol = "uvir-sensor-v1",
                    uvSensorModel = if (i == 0) "AS7331" else "UV-B",
                    visibleSensorModel = if (i == 0) "AS7343" else "VIS-B"))
        }
        records = profiles.mapIndexed { i, profile -> SavedRecordDetail(i + 1L, 1_700_000_000_000L + i * 1000,
            "Fixture", true, SensorSample(uvc = .0000000000123456789, uvb = .0123456789012345,
                uva = .0234567890123456, violetto = .03, blu = .04, verde = .05, giallo = .06,
                arancione = .07, rosso = .08, f8 = .09, nir = .1),
            sessionId = 12, sessionSequence = i + 1, sensorId = profile.id, sensorDisplayName = "Device ${i + 1}") }
        alerts = records.map { ThresholdAlertLogEntry(it.id, it.timestamp,
            "UVA|0.0234567890123456|ABOVE|0.0123456789;BLUE|0.04|ABOVE|0.03", sessionId = 13,
            sensorId = it.sensorId, sensorDisplayName = it.sensorDisplayName) }
    }

    @After fun cleanup() {
        assertTrue(directory.deleteRecursively())
        preferences.forEach(base::deleteSharedPreferences)
    }

    private fun csv(format: UvirExportFormatting) = measurementCsv(records, format.numericFormat,
        format.dateFormat, format.language, format.context.resources.configuration.locales[0], format.context,
        format.timeFormat, format.irradianceUnit)

    @Test fun scientificAndCustomExportsUseTheirOwnPrecisionAndSourceModels() {
        for (unit in UvirIrradianceUnit.entries) {
            saveUvirIrradianceUnit(context, unit)
            saveUvirLiveIrradianceDecimals(context, unit, 2)
            saveUvirNumericFormat(context, UvirNumericFormat.EUROPEAN)
            saveUvirExportMode(context, UvirExportMode.SELECTED)
            val selected = uvirExportFormatting(context)
            assertEquals(2, selected.irradianceDecimals)
            val lines = csv(selected).lines()
            val row = lines[0].split(';').zip(lines[1].split(';')).toMap()
            assertEquals(selected.irradiance(records[0].sample.uva), row["UVA_315_410_nm_${unit.csvSymbol}"])
            assertEquals("AS7331", row["Modello_sensore_UV"])
            assertEquals("AS7343", row["Modello_sensore_VIS_IR"])
            val second = lines[0].split(';').zip(lines[2].split(';')).toMap()
            assertEquals("UV-B", second["Modello_sensore_UV"])
            assertEquals("VIS-B", second["Modello_sensore_VIS_IR"])
            val readable = readableMeasurementTable(selected.context, records, selected.numericFormat,
                irradianceUnit = selected.irradianceUnit)
            assertTrue(readable.contains("UV: AS7331 · VIS/IR: AS7343"))
            assertTrue(readable.contains("405 / 425 nm"))
            assertTrue(readable.contains("240–280 nm"))
            assertTrue(readable.contains(selected.irradiance(records[0].sample.uva) + " " + unit.symbol))
            val alertCsv = thresholdAlertLogCsv(selected.context, alerts, selected.numericFormat, irradianceUnit = unit)
            assertTrue(alertCsv.contains(selected.irradiance(records[0].sample.uva)))
            assertTrue(alertCsv.contains("AS7331;AS7343"))
            val alertText = readableThresholdAlertLog(selected.context, alerts, selected.numericFormat, irradianceUnit = unit)
            assertTrue(alertText.contains(selected.irradiance(records[0].sample.uva)))

            saveUvirExportMode(context, UvirExportMode.INTERNATIONAL)
            val scientific = uvirExportFormatting(context)
            assertEquals(UvirIrradianceUnit.W_M2, scientific.irradianceUnit)
            assertNull(scientific.irradianceDecimals)
            val scientificCsv = csv(scientific)
            assertTrue(scientificCsv.contains("0.0000000000123456789"))
            assertTrue(scientificCsv.contains("0.0234567890123456"))
            assertTrue(scientificCsv.contains("UV_sensor_model"))
            assertFalse(scientificCsv.lineSequence().first().contains("uW_cm2"))
            val scientificText = readableMeasurementTable(scientific.context, records, scientific.numericFormat,
                irradianceUnit = scientific.irradianceUnit)
            assertTrue(scientificText.contains("0.0000000000123456789 W/m²"))
        }
    }

    @Test fun knownProfileCannotBorrowTheLastSelectedProfilesComponentsAndModelsSurviveTheCache() {
        assertEquals("AS7331", loadExportHardwareModels(context, listOf(records[0].sensorId)).models(records[0].sensorId).ultraviolet)
        assertEquals("UV-B", loadExportHardwareModels(context, listOf(records[1].sensorId)).models(records[1].sensorId).ultraviolet)
        assertNull(UvirSensorRuntimeInfoStore.loadForDevice(context, "UNRELATED"))
        assertEquals(UvirExportHardwareModels(), loadExportHardwareModels(context, listOf(null)).models(null))
    }

    @Test fun explicitScientificPolicyIsNotOverriddenBySavedCustomPreferences() {
        saveUvirExportMode(context, UvirExportMode.SELECTED)
        saveUvirIrradianceUnit(context, UvirIrradianceUnit.UW_CM2)
        saveUvirLiveIrradianceDecimals(context, UvirIrradianceUnit.UW_CM2, 0)
        val scientific = uvirExportFormatting(context, UvirExportMode.INTERNATIONAL)
        assertTrue(csv(scientific).contains("0.0000000000123456789"))
        assertTrue(readableMeasurementTable(scientific.context, records, scientific.numericFormat,
            irradianceUnit = scientific.irradianceUnit).contains("0.0000000000123456789 W/m²"))
        assertEquals(1.23456789e-11, scientific.chartValue(1.23456789e-11), 0.0)
        val selected = uvirExportFormatting(context)
        assertEquals(0.0, selected.chartValue(1.23456789e-11), 0.0)
        assertEquals(records[0].sample.uvc, 1.23456789e-11, 0.0)
    }

    @Test fun allChartTypesRenderWithBandLegendsAndHardwareHeadersInBothModes() {
        val output = File(base.getExternalFilesDir(null), "export-consistency").apply { mkdirs() }
        for (mode in UvirExportMode.entries) {
            saveUvirExportMode(context, mode)
            saveUvirLiveIrradianceDecimals(context, UvirIrradianceUnit.W_M2, 3)
            saveUvirIrradianceUnit(context, UvirIrradianceUnit.W_M2)
            val files = createAcquisitionChartFiles(context, records[0], AcquisitionChartGroup.IRRADIANCE) +
                createSessionChartFile(context, 12, records, SessionChartGroup.VISIBLE) +
                createAlertSeparateChartFiles(context, alerts[0]) +
                createAlertSessionChartFile(context, 13, alerts, ThresholdAlertMetric.UVA) +
                createAlertSessionCombinedChartFile(context, 13, alerts)
            files.forEachIndexed { i, file ->
                val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path))
                assertEquals(1600, bitmap.width)
                assertTrue("Export panel $i in $mode: height=${bitmap.height}", bitmap.height in 400..6000)
                bitmap.recycle()
                file.copyTo(File(output, "${mode.name.lowercase()}-$i.png"), overwrite = true)
            }
        }
    }

    @Test fun legendCellWrappingKeepsTranslatedBandsAndLongScientificValuesInsideTheirCells() {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f }
        val text = "Verde-giallo · 550 / 555 nm: 0.00000000001234567890123456789"
        val lines = wrapChartExportText(text, paint, 418f)
        assertTrue(lines.size > 1)
        assertTrue(lines.all { paint.measureText(it) <= 418.1f })
        val legend = UvirChartExportLegend(List(7) { UvirChartLegendEntry(text, android.graphics.Color.BLUE) }, 3, 1380f, paint)
        assertTrue(legend.height >= lines.size * paint.fontSpacing * 3)
    }

    @Test fun customZeroDecimalChartsStayFiniteWhenEveryReadingRoundsToZero() {
        saveUvirExportMode(context, UvirExportMode.SELECTED)
        saveUvirIrradianceUnit(context, UvirIrradianceUnit.MW_CM2)
        saveUvirLiveIrradianceDecimals(context, UvirIrradianceUnit.MW_CM2, 0)
        val formatting = uvirExportFormatting(context)
        assertEquals(0.0, formatting.chartValue(.001), 0.0)
        assertEquals(1.0, formatting.chartZeroMaximum(), 0.0)
        val zero = records.map { it.copy(sample = SensorSample(uva = .001)) }
        val files = createAcquisitionChartFiles(context, zero[0], AcquisitionChartGroup.IRRADIANCE) +
            createSessionChartFile(context, 12, zero, SessionChartGroup.UV)
        files.forEach { file ->
            val image = requireNotNull(BitmapFactory.decodeFile(file.path))
            assertTrue(image.width > 0 && image.height > 0)
            image.recycle()
        }
    }
}
