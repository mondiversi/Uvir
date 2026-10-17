package me.mondiversi.uvir

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class UvirPlanetCompassExtractionTest {
    private val main = File("src/main").takeIf { it.isDirectory } ?: File("app/src/main")
    @Test fun logoIsAnOrdinaryInfoActionWithoutTheCelestialHold() {
        val logo = File(main, "java/me/mondiversi/uvir/UvirHomeInfoLogo.kt").readText()
        assertTrue(logo.contains("onClick = onOpenInfo"))
        assertFalse(logo.contains("onLongClick"))
        assertFalse(logo.contains("SunFinderEntry"))
    }
    @Test fun noCelestialCodeMapsOrResourcesRemainInTheMeasurementApplication() {
        val files = File(main, "java/me/mondiversi/uvir").listFiles().orEmpty()
        assertFalse(files.any { Regex("^Uvir(Celestial|SunFinder|Moon|IssOrbit|Starlink|Horizons)").containsMatchIn(it.name) })
        assertFalse(File(main, "assets/celestial").walkTopDown().any { it.isFile })
        assertFalse(File(main, "assets/licenses/celestial-textures.txt").exists())
        assertFalse(File(main, "assets/licenses/astronomy-engine.txt").exists())
        assertFalse(File(main, "res").walkTopDown().any { it.name == "celestial.xml" })
        val live = File(main, "java/me/mondiversi/uvir/UvirLiveScreen.kt").readText()
        assertFalse(live.contains("showSunFinderScreen"))
        assertFalse(live.contains("onFindSun"))
    }
}
