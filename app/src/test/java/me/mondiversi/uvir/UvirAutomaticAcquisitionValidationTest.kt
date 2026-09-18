package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UvirAutomaticAcquisitionValidationTest {
    @Test
    fun validInputBuildsEquivalentRequest() {
        val result =
            validateAutomaticAcquisitionInput(
                validInput(
                    useStartDelay = true,
                    useDuration = true,
                    limitEnabled = true
                )
            )

        assertTrue(result is AutomaticAcquisitionValidation.Valid)
        val request =
            (result as AutomaticAcquisitionValidation.Valid).request
        assertEquals(90L, request.intervalSeconds)
        assertEquals(120L, request.startDelaySeconds)
        assertEquals(3_600L, request.durationSeconds)
        assertEquals(12, request.maxAcquisitions)
        assertEquals("test", request.note)
    }

    @Test
    fun sixtyMinutesAndSecondsAreAccepted() {
        val result =
            validateAutomaticAcquisitionInput(
                validInput().copy(
                    intervalMinutes = "60",
                    intervalSeconds = "60"
                )
            )

        assertTrue(result is AutomaticAcquisitionValidation.Valid)
        assertEquals(
            3_660L,
            (result as AutomaticAcquisitionValidation.Valid)
                .request.intervalSeconds
        )
    }

    @Test
    fun intervalAboveSixtyIsRejectedOutsideTheUi() {
        val result =
            validateAutomaticAcquisitionInput(
                validInput().copy(intervalMinutes = "61")
            )

        assertEquals(AutomaticAcquisitionValidation.InvalidInterval, result)
    }

    @Test
    fun invalidOptionalScheduleIsReported() {
        val result =
            validateAutomaticAcquisitionInput(
                validInput(useDuration = true)
                    .copy(
                        durationHours = "0",
                        durationMinutes = "0",
                        durationSeconds = "0"
                    )
            )

        assertEquals(
            AutomaticAcquisitionValidation.InvalidSchedule,
            result
        )
    }

    @Test
    fun externalCommandIgnoresScheduleButKeepsNote() {
        val result = validateAutomaticAcquisitionInput(
            validInput(useStartDelay = true, useDuration = true, limitEnabled = true)
                .copy(
                    intervalMinutes = "99",
                    startDelayMinutes = "99",
                    durationMinutes = "99",
                    maxAcquisitions = "0",
                    externalCommand = true
                )
        )

        assertTrue(result is AutomaticAcquisitionValidation.Valid)
        val request = (result as AutomaticAcquisitionValidation.Valid).request
        assertTrue(request.externalCommand)
        assertEquals("test", request.note)
        assertEquals(true, request.useStartDelay)
        assertEquals(true, request.useDuration)
        assertEquals(true, request.limitEnabled)
    }

    private fun validInput(
        useStartDelay: Boolean = false,
        useDuration: Boolean = false,
        limitEnabled: Boolean = false
    ) =
        AutomaticAcquisitionInput(
            intervalHours = "0",
            intervalMinutes = "1",
            intervalSeconds = "30",
            note = " test ",
            useStartDelay = useStartDelay,
            startDelayHours = "0",
            startDelayMinutes = "2",
            startDelaySeconds = "0",
            useDuration = useDuration,
            durationHours = "1",
            durationMinutes = "0",
            durationSeconds = "0",
            limitEnabled = limitEnabled,
            maxAcquisitions = "12"
        )
}
