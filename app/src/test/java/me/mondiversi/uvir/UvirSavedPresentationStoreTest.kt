package me.mondiversi.uvir

import androidx.lifecycle.ViewModelStore
import org.junit.Assert.*
import org.junit.Test

class UvirSavedPresentationStoreTest {
    @Test fun recordsSessionsAndAlertsKeepIndependentChoicesUntilTheAppSessionEnds() {
        val store = UvirSavedPresentationStore()
        val session = store.screen("acquisition-session:9")
        session.valueDisplay.value = session.valueDisplay.value.toggleAcquisition(ThresholdAlertMetric.UVB)
            .let { it.copy(spectrum = it.spectrum.togglePercentageBase(SensorGroup.UV)) }
        session.expandedGroups[SessionChartGroup.VISIBLE] = false
        session.showChart.value = true
        session.sequencePosition.intValue = 2

        val record = store.screen("acquisition:9")
        record.valueDisplay.value = record.valueDisplay.value.toggleAcquisition(ThresholdAlertMetric.UVA)
        val alert = store.screen("alert:9", ViewMode.BIOLOGICAL_EFFECTS)
        alert.dataExpanded.value = false
        val alertSession = store.screen("alert-session:9")
        alertSession.expandedChartViews[ViewMode.IRRADIANCE] = false

        assertFalse(store.screen("acquisition-session:9").valueDisplay.value.acquisitionVisible(ThresholdAlertMetric.UVB))
        assertTrue(session.valueDisplay.value.acquisitionVisible(ThresholdAlertMetric.UVA))
        assertTrue(SensorGroup.UV in session.valueDisplay.value.spectrum.groupPercentages)
        assertEquals(false, store.screen("acquisition-session:9").expandedGroups[SessionChartGroup.VISIBLE])
        assertTrue(store.screen("acquisition-session:9").showChart.value)
        assertEquals(2, store.screen("acquisition-session:9").sequencePosition.intValue)
        assertTrue(record.valueDisplay.value.acquisitionVisible(ThresholdAlertMetric.UVB))
        assertFalse(record.valueDisplay.value.acquisitionVisible(ThresholdAlertMetric.UVA))
        assertEquals(ViewMode.BIOLOGICAL_EFFECTS, store.screen("alert:9").viewMode.value)
        assertFalse(store.screen("alert:9").dataExpanded.value)
        assertEquals(false, store.screen("alert-session:9").expandedChartViews[ViewMode.IRRADIANCE])
        assertEquals(UvirSavedValueDisplay(), store.screen("acquisition:10").valueDisplay.value)
        assertEquals(UvirSavedValueDisplay(), UvirSavedPresentationStore().screen("acquisition-session:9").valueDisplay.value)
    }

    @Test fun finishingTheActivityDiscardsMemoryRatherThanRestoringItToTheNextAppSession() {
        val lifetime = ViewModelStore()
        val store = UvirSavedPresentationStore()
        lifetime.put("saved-presentation", store)
        store.screen("acquisition:1").valueDisplay.value = UvirSavedValueDisplay()
            .toggleAcquisition(ThresholdAlertMetric.UV_TOTAL)
        lifetime.clear()
        assertEquals(UvirSavedValueDisplay(), store.screen("acquisition:1").valueDisplay.value)
    }
}
