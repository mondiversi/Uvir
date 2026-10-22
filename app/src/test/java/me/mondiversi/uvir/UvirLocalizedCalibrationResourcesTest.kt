package me.mondiversi.uvir

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class UvirLocalizedCalibrationResourcesTest {
    @Test fun allLocalesPreserveTheMeasuredChannelReferencesInOpticalNotes() {
        val resourceRoot = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val parser = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder()
        val directories = resourceRoot.listFiles()!!.filter { File(it, "optical_window.xml").isFile }
        assertEquals(20, directories.size)
        for (directory in directories) {
            val nodes = parser.parse(File(directory, "optical_window.xml")).getElementsByTagName("string")
            val strings = buildMap<String, String> {
                for (index in 0 until nodes.length) {
                    val node = nodes.item(index)
                    put(node.attributes.getNamedItem("name").nodeValue, node.textContent)
                }
            }
            assertTrue(directory.name, strings.getValue("measured_uv_bands_note").contains("AS7331"))
            for (key in listOf("calibration_factor_short", "calibration_offset_short",
                "calibration_channel_factors_title", "calibration_channels_firmware_required"))
                assertTrue("${directory.name}: $key", strings.getValue(key).isNotBlank())
            assertTrue(directory.name, strings.getValue("calibration_offset_short").contains("%1\$s"))
            val visibleNote = strings.getValue("measured_visible_bands_note")
            assertTrue(directory.name, visibleNote.contains("AS7343") && visibleNote.contains("HEV"))
            val infraredNote = strings.getValue("measured_infrared_bands_note")
            for (reference in listOf("690", "745", "855", "NIR")) {
                assertTrue("${directory.name}: $reference", infraredNote.contains(reference))
            }
        }
    }

    @Test fun allLocalesFormatCalibrationHintsWithUserSelectedNumbers() {
        val resourceRoot = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val parser = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder()
        val directories = resourceRoot.listFiles()!!.filter { File(it, "optical_window.xml").isFile }
        assertEquals(20, directories.size)
        for (directory in directories) {
            val strings = buildMap<String, String> {
                for (file in listOf("strings.xml", "optical_window.xml")) {
                    val nodes = parser.parse(File(directory, file)).getElementsByTagName("string")
                    for (index in 0 until nodes.length) {
                        val node = nodes.item(index)
                        put(node.attributes.getNamedItem("name").nodeValue, node.textContent)
                    }
                }
            }
            for (format in listOf(UvirNumericFormat.EUROPEAN, UvirNumericFormat.INTERNATIONAL)) {
                assertFalse(directory.name, strings.containsKey("sensor_calibration_uv_unavailable"))
                val neutral = formatUvirNumber(1.0, 1, format, grouping = false)
                val example = formatUvirNumber(1.0 / .9, 3, format, grouping = false)
                val hint = String.format(Locale.US, strings.getValue("optical_window_hint"), neutral, example)
                assertTrue(directory.name, hint.contains(neutral) && hint.contains(example))
                assertFalse(directory.name, hint.contains("%%"))
                val windowMinimum = formatUvirNumber(1.0, 1, format, grouping = false)
                val windowMaximum = formatUvirNumber(10.0, 1, format, grouping = false)
                val windowLimits = String.format(Locale.US, strings.getValue("optical_window_factor_limits"),
                    windowMinimum, windowMaximum)
                assertTrue(directory.name, windowLimits.contains(windowMinimum) && windowLimits.contains(windowMaximum))
                assertFalse(directory.name, windowLimits.contains("%"))
                val description = strings.getValue("sensor_calibration_description")
                assertTrue(directory.name, description.isNotBlank())
                assertFalse(directory.name, description.contains("%"))
                val minimum = formatUvirNumber(.1, 1, format, grouping = false)
                val maximum = formatUvirNumber(10.0, 1, format, grouping = false)
                val limits = String.format(Locale.US, strings.getValue("sensor_calibration_factor_hint"), minimum, maximum, neutral)
                assertTrue(directory.name, limits.contains(minimum) && limits.contains(maximum) && limits.contains(neutral))
                assertFalse(directory.name, limits.contains("%"))
                for (unit in UvirIrradianceUnit.entries) {
                    val zero = formatUvirNumber(0.0, 0, format)
                    val offsetMaximum = formatUvirNumber(
                        unit.fromCanonicalWm2(MAX_CALIBRATION_OFFSET_W_M2.toDouble()), 0, format)
                    val offsetLimits = String.format(Locale.US, strings.getValue("calibration_offsets_description"),
                        zero, offsetMaximum, unit.symbol)
                    assertTrue("${directory.name}: $unit", offsetLimits.contains(zero) &&
                        offsetLimits.contains(offsetMaximum) && offsetLimits.contains(unit.symbol))
                    assertFalse(directory.name, offsetLimits.contains("%"))
                    assertEquals(MAX_CALIBRATION_OFFSET_W_M2.toDouble(),
                        unit.toCanonicalWm2(unit.fromCanonicalWm2(MAX_CALIBRATION_OFFSET_W_M2.toDouble())), 0.0)
                }
            }
        }
    }
}
