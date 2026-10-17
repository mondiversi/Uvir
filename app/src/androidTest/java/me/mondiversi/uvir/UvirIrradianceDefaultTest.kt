package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Isolated preference files only; never modifies the installed app's chosen unit. */
class UvirIrradianceDefaultTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "irradiance-default-test-${UUID.randomUUID()}-"
    private val files = mutableSetOf<String>()
    private val isolated = object : ContextWrapper(base) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolatedName = prefix + name
            files.add(isolatedName)
            return base.getSharedPreferences(isolatedName, mode)
        }
    }

    @After fun cleanup() { files.forEach(base::deleteSharedPreferences) }

    @Test fun cleanInstallationStartsInWattsPerSquareMetreWithoutWritingPreferences() {
        val preferences = isolated.getSharedPreferences(UVIR_PREFERENCES_NAME, Context.MODE_PRIVATE)
        assertFalse(preferences.contains(UVIR_IRRADIANCE_UNIT_KEY))
        assertEquals(UvirIrradianceUnit.W_M2, loadUvirIrradianceUnit(isolated))
        assertFalse(preferences.contains(UVIR_IRRADIANCE_UNIT_KEY))
    }

    @Test fun allExistingUserChoicesRemainUnchangedWhenLoadingAgain() {
        for (unit in UvirIrradianceUnit.entries) {
            saveUvirIrradianceUnit(isolated, unit)
            assertEquals(unit, loadUvirIrradianceUnit(isolated))
            assertEquals(unit.storedValue,
                isolated.getSharedPreferences(UVIR_PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .getString(UVIR_IRRADIANCE_UNIT_KEY, null))
        }
    }
}
