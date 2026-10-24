package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class UvirLiveIrradianceDecimalsTest {
    @Test fun everyLivePathUsesOnlyTheNewChoiceOrItsUnitDefault() {
        for (unit in UvirIrradianceUnit.entries) {
            assertEquals(defaultUvirLiveIrradianceDecimals(unit), resolveUvirLiveIrradianceDecimals(null, unit))
            for (digits in 0..6) assertEquals(digits, resolveUvirLiveIrradianceDecimals(digits, unit))
        }
        val source = File("src/main/java/me/mondiversi/uvir/UvirLiveIrradianceDecimals.kt").readText()
        assertFalse(source.contains("formatUvirIrradianceNumber("))
        val chart = File("src/main/java/me/mondiversi/uvir/UvirLiveCharts.kt").readText()
        assertFalse(chart.contains("fractionDigits = 1,"))
        assertTrue(chart.contains("val liveDigits = currentUvirLiveIrradianceDecimals()"))
    }

    @Test fun defaultsUseRequestedPerUnitPrecisionAndIndependentKeys() {
        assertEquals(2, defaultUvirLiveIrradianceDecimals(UvirIrradianceUnit.W_M2))
        assertEquals(3, defaultUvirLiveIrradianceDecimals(UvirIrradianceUnit.MW_CM2))
        assertEquals(1, defaultUvirLiveIrradianceDecimals(UvirIrradianceUnit.UW_CM2))
        assertEquals(3, UvirIrradianceUnit.entries.map(::uvirLiveIrradianceDecimalsKey).toSet().size)
        for (unit in UvirIrradianceUnit.entries) {
            val displayStep = 1.0 / Math.pow(10.0, defaultUvirLiveIrradianceDecimals(unit).toDouble())
            assertEquals(if (unit == UvirIrradianceUnit.UW_CM2) .001 else .01,
                unit.toCanonicalWm2(displayStep), 1e-12)
        }
    }

    @Test fun roundsHalvesUpWithoutDroppingTrailingZeros() {
        val format = UvirNumericFormat.EUROPEAN
        val unit = UvirIrradianceUnit.UW_CM2
        assertEquals("0", formatUvirLiveIrradianceNumber(.0049, 0, format, unit))
        assertEquals("1", formatUvirLiveIrradianceNumber(.005, 0, format, unit))
        assertEquals("0,15", formatUvirLiveIrradianceNumber(.00145, 2, format, unit))
        assertEquals("1,01", formatUvirLiveIrradianceNumber(.01005, 2, format, unit))
        assertEquals("12,30", formatUvirLiveIrradianceNumber(.123, 2, format, unit))
        assertEquals("0,000000", formatUvirLiveIrradianceNumber(0.0, 6, format, unit))
        assertEquals("0,00", formatUvirLiveIrradianceNumber(.00001, 2, format, unit))
    }

    @Test fun appliesChosenDecimalsAfterUnitConversionAndRespectsNumericFormats() {
        assertEquals("0.12", formatUvirLiveIrradianceNumber(.123456, 2,
            UvirNumericFormat.INTERNATIONAL, UvirIrradianceUnit.W_M2))
        assertEquals("0,012", formatUvirLiveIrradianceNumber(.123456, 3,
            UvirNumericFormat.EUROPEAN, UvirIrradianceUnit.MW_CM2))
        assertEquals("12", formatUvirLiveIrradianceNumber(.123456, 0,
            UvirNumericFormat.AMERICAN, UvirIrradianceUnit.UW_CM2))
        assertEquals("1.234,00", formatUvirLiveIrradianceNumber(12.34, 2,
            UvirNumericFormat.EUROPEAN, UvirIrradianceUnit.UW_CM2))
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ITALY)
            assertEquals("1,20", formatUvirLiveIrradianceNumber(.012, 2,
                UvirNumericFormat.SYSTEM, UvirIrradianceUnit.UW_CM2))
        } finally { Locale.setDefault(previous) }
    }

    @Test fun savedFormattingExportsAndEditorsKeepTheirExistingPrecision() {
        val value = .123456
        formatUvirLiveIrradianceNumber(value, 0, UvirNumericFormat.EUROPEAN, UvirIrradianceUnit.W_M2)
        assertEquals("0,12346", formatUvirIrradianceNumber(value, 3,
            UvirNumericFormat.EUROPEAN, UvirIrradianceUnit.W_M2))
        assertEquals("0,123456", formatUvirIrradianceExportNumber(value,
            UvirNumericFormat.EUROPEAN, UvirIrradianceUnit.W_M2))
        assertEquals("12,345600", formatUvirDecimalInputText("12.345600", UvirNumericFormat.EUROPEAN))
        assertEquals(.123456, value, 0.0)
    }

    @Test fun invalidDigitCountsAreBoundedAndNonFiniteValuesStayExplicit() {
        assertEquals("2", formatUvirLiveDisplayNumber(1.5, -1, UvirNumericFormat.INTERNATIONAL))
        assertEquals("1.500000", formatUvirLiveDisplayNumber(1.5, 20, UvirNumericFormat.INTERNATIONAL))
        assertEquals("NaN", formatUvirLiveDisplayNumber(Double.NaN, 2, UvirNumericFormat.EUROPEAN))
    }

    @Test fun allTwentyLocalesProvideBothLabels() {
        val resources = File("src/main/res")
        val folders = resources.listFiles()!!.filter { File(it, "strings.xml").isFile }
        assertEquals(20, folders.size)
        for (folder in folders) {
            val file = File(folder, "live_irradiance_decimals.xml")
            assertTrue(file.toString(), file.isFile)
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            assertEquals(2, nodes.length)
            val keys = (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }.toSet()
            assertEquals(setOf("live_irradiance_decimals", "live_irradiance_decimals_description"), keys)
        }
    }
}
