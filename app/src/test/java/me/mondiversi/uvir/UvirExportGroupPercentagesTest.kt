package me.mondiversi.uvir

import org.junit.Assert.*
import org.junit.Test

class UvirExportGroupPercentagesTest {
    private val sample = SensorSample(uvc = 1.0, uvb = 1.0, uva = 2.0,
        violetto = 2.0, blu = 2.0, verde = 2.0, giallo = 2.0, arancione = 2.0, rosso = 2.0,
        f8 = 3.0, nir = 1.0)
    private fun rows(samples: List<SensorSample>, language: String = "en", digits: Int? = 2,
        unit: UvirIrradianceUnit = UvirIrradianceUnit.W_M2): List<Map<String, String>> {
        val lines = measurementCsv(samples.mapIndexed { i, s -> SavedRecordDetail(i + 1L,
            i + 1000L, "Fixture", true, s, sessionId = 12, sessionSequence = i + 1,
            variantIndex = i + 1) }, UvirNumericFormat.INTERNATIONAL,
            language = language, irradianceUnit = unit, irradianceDecimals = digits).trim().lines()
        val headers = lines.first().split(';')
        assertEquals(headers.size, headers.toSet().size)
        return lines.drop(1).map { line ->
            val cells = line.split(';')
            assertEquals(headers.size, cells.size)
            headers.zip(cells).toMap()
        }
    }

    @Test fun csvContainsTwoExplicitPercentagesForEveryIrradianceChannelAndGroupTotal() {
        val row = rows(listOf(sample)).single()
        assertEquals("5.00", row["UVC_Percentages_of_total"])
        assertEquals("25.00", row["UVC_Percentages_within_group"])
        assertEquals("20.00", row["Ultraviolet_total_Percentages_of_total"])
        assertEquals("100.00", row["Ultraviolet_total_Percentages_within_group"])
        assertEquals("20.00", row["HEV_Percentages_of_total"])
        assertEquals("33.33", row["HEV_Percentages_within_group"])
        assertEquals("60.00", row["Visible_total_Percentages_of_total"])
        assertEquals("100.00", row["Visible_total_Percentages_within_group"])
        assertEquals("15.00", row["FarRed_Percentages_of_total"])
        assertEquals("75.00", row["FarRed_Percentages_within_group"])
        assertEquals("100.00", row["Infrared_total_Percentages_within_group"])
        assertEquals(15, row.keys.count { it.endsWith("_Percentages_of_total") })
        assertEquals(15, row.keys.count { it.endsWith("_Percentages_within_group") })
        assertFalse(row.keys.any { it.startsWith("Spectral_index") && it.contains("Percentages") })
    }

    @Test fun everySessionRecordAndVariantUsesItsOwnSpectrumAndNotASessionSum() {
        val result = rows(listOf(sample, sample.copy(uva = 8.0)))
        assertEquals("25.00", result[0]["UVC_Percentages_within_group"])
        assertEquals("10.00", result[1]["UVC_Percentages_within_group"])
        assertEquals("3.85", result[1]["UVC_Percentages_of_total"])
        assertEquals("A", result[0]["Variant"])
        assertEquals("B", result[1]["Variant"])
        assertEquals(2.0, sample.uva, 0.0)
    }

    @Test fun groupPercentagesDoNotDoubleCountTheOverlappingHevAggregate() {
        assertEquals(UvirExportSpectralPercentages(20.0, 100.0), sample.exportSpectralPercentages(4.0, SensorGroup.UV))
        assertEquals(60.0, sample.exportSpectralPercentages(12.0, SensorGroup.VISIBLE).total!!, 0.0)
        assertEquals(100.0 / 3.0, sample.exportSpectralPercentages(4.0, SensorGroup.VISIBLE).group!!, 1e-12)
    }

    @Test fun undefinedZeroDenominatorsUseDashesRatherThanNaNOrInventedZeroOrHundredPercent() {
        val zero = rows(listOf(SensorSample())).single()
        assertTrue(zero.filterKeys { it.contains("Percentages") }.values.all { it == "—" })
        val partial = rows(listOf(SensorSample(blu = 2.0))).single()
        assertEquals("0.00", partial["UVC_Percentages_of_total"])
        assertEquals("—", partial["UVC_Percentages_within_group"])
        assertEquals("100.00", partial["Blue_Percentages_within_group"])
    }

    @Test fun unavailableGroupsSuppressTheirRatiosAndTheGlobalRatioButNotOtherValidGroups() {
        val flagged = sample.copy(qualityFlags = UVIR_QUALITY_UV_OUT_OF_RANGE)
        assertEquals(UvirExportSpectralPercentages(null, null), flagged.exportSpectralPercentages(1.0, SensorGroup.UV))
        assertNull(flagged.exportSpectralPercentages(2.0, SensorGroup.VISIBLE).total)
        assertEquals(100.0 / 6.0, flagged.exportSpectralPercentages(2.0, SensorGroup.VISIBLE).group!!, 1e-12)
        assertEquals(UvirExportSpectralPercentages(null, null), sample.exportSpectralPercentages(1.0, SensorGroup.BIOLOGICAL))
    }

    @Test fun percentagesUseFullDoublePrecisionInScientificExportsAndCustomDigitsOnlyAtFormatting() {
        val raw = sample.exportSpectralPercentages(sample.violetto, SensorGroup.VISIBLE).group!!
        val scientific = rows(listOf(sample), digits = null).single()
        assertEquals(raw.toBits(), scientific.getValue("Violet_Percentages_within_group").toDouble().toBits())
        for (unit in UvirIrradianceUnit.entries) {
            val custom = rows(listOf(sample), digits = 0, unit = unit).single()
            assertEquals("17", custom["Violet_Percentages_within_group"])
        }
    }

    @Test fun italianCsvHasTheRequestedExplicitHeadersAndNoAmbiguousPercentageColumn() {
        val row = rows(listOf(sample), language = "it").single()
        assertEquals("5.00", row["UVC_Percentuali_sul_totale"])
        assertEquals("25.00", row["UVC_Percentuali_per_gruppo"])
        assertFalse("UVC_percentuale" in row)
    }
}
