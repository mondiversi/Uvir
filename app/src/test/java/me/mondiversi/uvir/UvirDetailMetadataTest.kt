package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirDetailMetadataTest {
    private fun profile(name: String, hardwareUid: String = "UVIR-OLD") =
        UvirSensorProfile(7L, hardwareUid, name, 1L, 2L)

    @Test
    fun bothMetadataCardsShareThirtyFiveSixtyFiveColumns() {
        assertEquals(0.35f, UvirDetailLeadingColumnWeight, 0f)
        assertEquals(0.65f, UvirDetailTrailingColumnWeight, 0f)
        assertEquals(1f, UvirDetailLeadingColumnWeight + UvirDetailTrailingColumnWeight, 0.0001f)
    }

    @Test
    fun allMetadataIconsUseTheSameSizeAndStroke() {
        assertEquals(11f, UvirDetailMetadataIconSize.value, 0f)
        assertEquals(1.25f, UvirDetailMetadataIconStrokeWidth.value, 0f)
    }

    @Test
    fun recordSensorUsesItsStoredNameIncludingUnicode() {
        assertEquals("Balcone – חיישן", detailSensorDisplayName(profile("Balcone – חיישן")))
    }

    @Test
    fun unnamedRecordSensorFallsBackToItsOwnHardwareIdentity() {
        assertEquals("UVIR-OLD", detailSensorDisplayName(profile("")))
    }

    @Test
    fun missingRecordSensorShowsADashWithoutBorrowingTheCurrentAssociation() {
        assertEquals("—", detailSensorDisplayName(null))
        assertEquals("—", detailSensorDisplayName(profile("", "")))
    }

    @Test
    fun listDescriptionPlacesNoteBeforeSensorWithoutChangingStoredText() {
        val storedNote = "Balcony reading"
        assertEquals(
            "Balcony reading · Garden sensor",
            sensorAndNoteDisplayText("Garden sensor", storedNote, "No note")
        )
        assertEquals("Balcony reading", storedNote)
    }

    @Test
    fun listDescriptionKeepsSessionSequenceImmediatelyBeforeNote() {
        assertEquals(
            "#3 · Morning · Garden sensor",
            sensorAndNoteDisplayText("Garden sensor", "Morning", "No note", sessionSequence = 3)
        )
    }

    @Test
    fun standaloneManualListDescriptionDoesNotInventASequence() {
        val note = acquisitionDisplayNote("Manual", false, null, "No note")
        assertEquals("Manual · Garden sensor", sensorAndNoteDisplayText("Garden sensor", note, "No note"))
    }

    @Test
    fun emptyListNoteUsesTheLocalizedPlaceholderBeforeTheSensor() {
        assertEquals("Nessuna nota · Sensore", sensorAndNoteDisplayText("Sensore", "", "Nessuna nota"))
    }

    @Test
    fun emptySessionNoteStillKeepsSequenceBeforeSensor() {
        assertEquals(
            "#3 · Garden sensor",
            sensorAndNoteDisplayText("Garden sensor", "", "No note", sessionSequence = 3)
        )
    }

    @Test
    fun noteIncludingItsSequenceCannotUseMoreThanHalfOfTheListDescriptionWidth() {
        assertEquals(0.5f, UvirListNoteMaximumWidthFraction, 0f)
        assertEquals("#3 · Morning", listNoteDisplayText("Morning", "No note", 3))
    }
}
