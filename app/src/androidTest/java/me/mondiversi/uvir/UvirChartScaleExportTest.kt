package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Exports synthetic fixtures to a private cache directory; never opens the user's database. */
class UvirChartScaleExportTest {
    private lateinit var context: Context
    private lateinit var directory: File
    private val namespace = "chart-scale-test-${UUID.randomUUID()}"
    private val preferences = mutableSetOf<String>()

    private inner class ScopedContext(base: Context) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getCacheDir(): File = directory
        override fun createConfigurationContext(configuration: Configuration): Context =
            ScopedContext(super.createConfigurationContext(configuration))
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolated = "$namespace-$name"
            preferences.add(isolated)
            return super.getSharedPreferences(isolated, mode)
        }
    }

    @Before fun prepare() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(base.cacheDir, namespace).also { assertTrue(it.mkdir()) }
        context = ScopedContext(base)
        saveUvirExportMode(context, UvirExportMode.SELECTED)
    }

    @After fun cleanUp() {
        assertTrue(directory.deleteRecursively())
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        preferences.forEach { base.deleteSharedPreferences(it) }
    }

    private fun record(sequence: Int, uva: Double, flags: Int = 0) = SavedRecordDetail(
        id = sequence.toLong(), timestamp = 1_700_000_000_000L + sequence * 1_000L,
        note = "", automatic = true, sessionId = 12L, sessionSequence = sequence,
        sample = SensorSample(uva = uva, uvb = 5.0, uvc = 1.0, qualityFlags = flags),
        sensorDisplayName = "Chart test")

    private fun bitmap(file: File): Bitmap {
        assertTrue(file.absolutePath.startsWith(directory.absolutePath + File.separator))
        return requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
    }

    private fun colorBounds(bitmap: Bitmap, color: Int, left: Int, right: Int, bottom: Int): Pair<Int, Int> {
        var first = Int.MAX_VALUE
        var last = -1
        for (y in 0 until bottom) for (x in left until right) {
            if (bitmap.getPixel(x, y) == color) { first = minOf(first, y); last = maxOf(last, y) }
        }
        assertTrue("No trace/bar pixels found", last >= first)
        return first to last
    }

    @Test fun acquisitionPngBarHasTheSameHeightInAllUnits() {
        var baseline: Pair<Int, Int>? = null
        for (unit in UvirIrradianceUnit.entries) {
            saveUvirIrradianceUnit(context, unit)
            val file = createAcquisitionChartFiles(context, record(1, 20.0), AcquisitionChartGroup.IRRADIANCE).first()
            val image = bitmap(file)
            val bounds = colorBounds(image, 0xFF3F51B5.toInt(), 1335, 1340, image.height)
            assertTrue("The UVA bar must not be flattened in $unit", bounds.second - bounds.first > 150)
            baseline?.let { assertEquals(it, bounds) }
            baseline = bounds
            image.recycle()
        }
    }

    @Test fun sessionPngUsesVisibleValidSamplesAndPreservesShapeAcrossUnits() {
        val records = listOf(record(1, 10.0), record(2, 20.0),
            record(3, 1e6, UVIR_QUALITY_UV_OUT_OF_RANGE), record(4, 15.0), record(5, 5.0))
        var baseline: Pair<Int, Int>? = null
        for (unit in UvirIrradianceUnit.entries) {
            saveUvirIrradianceUnit(context, unit)
            val image = bitmap(createSessionChartFile(context, 12L, records, SessionChartGroup.UV))
            // Stop before the legend; inspect the actual UVA curve.
            val bounds = colorBounds(image, 0xFF42A5F5.toInt(), 400, 1500, image.height - 200)
            assertTrue("OL or subunit values flattened the session in $unit", bounds.second - bounds.first > 200)
            baseline?.let { assertEquals(it, bounds) }
            baseline = bounds
            image.recycle()
        }
    }

    @Test fun emptyOlChartsAndVariantExportModesRemainExportable() {
        val records = listOf(record(1, 0.0, UVIR_QUALITY_UV_OUT_OF_RANGE).copy(positionIndex = 1, variantIndex = 1),
            record(2, 0.0, UVIR_QUALITY_UV_OUT_OF_RANGE).copy(positionIndex = 1, variantIndex = 2))
        for (grouping in UvirVariantChartGrouping.entries) {
            val files = createSessionVariantChartFiles(context, 12L, records,
                UvirChartExportMode.COMBINED, grouping)
            assertTrue(files.isNotEmpty())
            files.forEach { bitmap(it).recycle() }
        }
    }

    @Test fun alertPercentagePngKeepsItsThresholdReferenceWhenUnitChanges() {
        val entries = listOf(ThresholdAlertLogEntry(1L, 1_700_000_000_000L, "UVA|20.0|ABOVE|10.0", 13L),
            ThresholdAlertLogEntry(2L, 1_700_000_001_000L, "UVA|30.0|ABOVE|10.0", 13L))
        var first: Bitmap? = null
        try {
            for (unit in UvirIrradianceUnit.entries) {
                saveUvirIrradianceUnit(context, unit)
                val image = bitmap(createAlertSessionCombinedChartFile(context, 13L, entries))
                if (first == null) first = image else {
                    assertTrue("Percentage chart changed when selecting $unit", first!!.sameAs(image))
                    image.recycle()
                }
            }
        } finally { first?.recycle() }
    }
}
