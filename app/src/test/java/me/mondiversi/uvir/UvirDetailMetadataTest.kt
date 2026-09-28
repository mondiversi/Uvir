package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirDetailMetadataTest {
    private fun profile(name: String, hardwareUid: String = "UVIR-OLD") =
        UvirSensorProfile(7L, hardwareUid, name, 1L, 2L)

    @Test
    fun bothMetadataCardsShareFortySixtyColumns() {
        assertEquals(0.40f, UvirDetailLeadingColumnWeight, 0f)
        assertEquals(0.60f, UvirDetailTrailingColumnWeight, 0f)
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
    fun listDescriptionShowsTheNoteWithoutChangingStoredText() {
        val storedNote = "Balcony reading"
        assertEquals("Balcony reading", listNoteDisplayText(storedNote, "No note"))
        assertEquals("Balcony reading", storedNote)
    }

    @Test
    fun listDescriptionKeepsSessionSequenceImmediatelyBeforeNote() {
        assertEquals(
            "#3 · Morning",
            listNoteDisplayText("Morning", "No note", sessionSequence = 3)
        )
    }

    @Test
    fun standaloneManualListDescriptionDoesNotInventASequence() {
        val note = acquisitionDisplayNote("Manual", false, null, "No note")
        assertEquals("Manual", listNoteDisplayText(note, "No note"))
    }

    @Test
    fun emptyListNoteUsesTheLocalizedPlaceholder() {
        assertEquals("—", listNoteDisplayText("", "—"))
    }

    @Test
    fun emptySessionNoteStillKeepsSequence() {
        assertEquals(
            "#3 · —",
            listNoteDisplayText("", "—", sessionSequence = 3)
        )
    }

    @Test
    fun noteIncludingItsSequenceKeepsTheExpectedText() {
        assertEquals("#3 · Morning", listNoteDisplayText("Morning", "No note", 3))
    }
}
