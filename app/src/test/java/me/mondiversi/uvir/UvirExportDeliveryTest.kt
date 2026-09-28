package me.mondiversi.uvir

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class UvirExportDeliveryTest {
    @Test
    fun formatRegistryOwnsExtensionsMimeTypesAndNames() {
        assertEquals(
            UvirExportFileFormat.CSV,
            UvirExportFileFormat.fromExtension(".CSV")
        )
        assertEquals(
            "text/csv",
            UvirExportFileFormat.CSV.mimeType
        )
        assertEquals(
            "Uvir_Acquisition.csv",
            UvirExportFileFormat.CSV.fileName("Uvir_Acquisition")
        )
        assertEquals(
            UvirExportFileFormat.OTHER,
            UvirExportFileFormat.fromExtension("unknown")
        )
    }

    @Test
    fun preparedExportDerivesCountsFromTheActualFiles() {
        val directory = Files.createTempDirectory("uvir-export-test").toFile()
        try {
            val files =
                listOf(
                    File(directory, "a.csv").apply { writeText("a") },
                    File(directory, "b.txt").apply { writeText("b") },
                    File(directory, "c.txt").apply { writeText("c") },
                    File(directory, "d.png").apply { writeBytes(byteArrayOf(1)) }
                )

            assertEquals(
                listOf(
                    UvirExportFileSummary(UvirExportFileFormat.CSV, 1),
                    UvirExportFileSummary(UvirExportFileFormat.TXT, 2),
                    UvirExportFileSummary(UvirExportFileFormat.PNG, 1)
                ),
                UvirPreparedExport(files).summaries
            )
        } finally {
            directory.deleteRecursively()
        }
    }
}
