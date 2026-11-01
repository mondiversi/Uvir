package me.mondiversi.uvir

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

/** The release page must describe this release in every shipped language. */
class UvirRelease142ResourcesTest {
    @Test fun currentUsbHighlightsArePresentInEveryLanguage() {
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val folders = root.listFiles()!!.filter { File(it, "strings.xml").isFile }
        assertEquals(20, folders.size)
        val parser = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder()
        for (folder in folders) {
            val nodes = parser.parse(File(folder, "release_011.xml")).getElementsByTagName("string")
            val entries = (0 until nodes.length).associate { index ->
                val node = nodes.item(index)
                node.attributes.getNamedItem("name").nodeValue to node.textContent
            }
            assertEquals(folder.name, setOf("release_011_status", "release_011_case", "release_011_improvements"), entries.keys)
            assertEquals(folder.name, 3, entries.values.toSet().size)
            assertTrue(folder.name, entries.values.all { it.isNotBlank() && !it.contains("0.5.119") })
        }
        val project = listOf(File("."), File("..")).first { File(it, "app/build.gradle.kts").isFile }
        val screen = File(project, "app/src/main/java/me/mondiversi/uvir/UvirVersionInfoScreen.kt").readText()
        assertTrue(screen.contains("R.string.release_011_status"))
        val notes = File(project, "docs/RELEASE_NOTES_0.1.1.md").readText()
        assertTrue(notes.contains("Development is suspended"))
        assertTrue(notes.contains("5 mm"))
    }

    @Test fun everyLanguageHasSixDistinctCurrentReleaseHighlights() {
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val folders = root.listFiles()!!.filter { File(it, "strings.xml").isFile }
        assertEquals(20, folders.size)
        val parser = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder()
        val keys = listOf("about_whats_new_connectivity", "about_whats_new_connections",
            "about_whats_new_measurements", "about_whats_new_interface",
            "update_whats_new", "about_whats_new_github")
        for (folder in folders) {
            val strings = buildMap<String, String> {
                for (filename in listOf("strings.xml", "updates.xml")) {
                    val nodes = parser.parse(File(folder, filename)).getElementsByTagName("string")
                    for (index in 0 until nodes.length) {
                        val node = nodes.item(index)
                        put(node.attributes.getNamedItem("name").nodeValue, node.textContent)
                    }
                }
            }
            val highlights = keys.map { strings.getValue(it) }
            assertEquals(folder.name, 6, highlights.toSet().size)
            assertTrue(folder.name, highlights.all { it.isNotBlank() && !it.contains("%") })
            assertTrue(folder.name, strings.getValue("about_whats_new_title").contains("%1\$s"))
            assertTrue(folder.name, highlights[4].contains("0.5.119"))
            assertFalse(folder.name, highlights.any {
                it.contains("1.3.0") || it.contains("1.4.0") || it.contains("1.4.1")
            })
        }
    }

    @Test fun releaseIdentityAndPublicDocumentationAgree() {
        val root = listOf(File("."), File("..")).first { File(it, "app/build.gradle.kts").isFile }
        val gradle = File(root, "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("versionCode = 10"))
        assertTrue(gradle.contains("versionName = \"0.1.1\""))
        assertTrue(File(root, "README.md").readText().contains("`0.1.1`"))
        for (file in listOf("RELEASE.md", "docs/UPDATES.md", "CREA_APK_RELEASE.bat",
            "docs/RELEASE_NOTES_1.4.2.md")) assertTrue(file, File(root, file).readText().contains("1.4.2"))
        val sourceVersion = Regex("kFirmwareVersion\\[\\] = \"([^\"]+)\"")
            .find(File(root, "firmware/esp32/UvirSensor/UvirSensor.ino").readText())!!.groupValues[1]
        assertEquals("0.5.119", sourceVersion)
        val readme = File(root, "docs/LEGACY_ESP32_README.md").readText()
        assertTrue(readme.contains("## Project on hold"))
        assertTrue(readme.contains("theoretical prototypes"))
        assertTrue(readme.contains("**1.4.2**"))
        assertTrue(readme.contains("**$sourceVersion**"))
    }
}
