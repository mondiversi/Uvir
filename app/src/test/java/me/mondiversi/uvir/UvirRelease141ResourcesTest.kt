package me.mondiversi.uvir

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

/** The release page must describe this release in every shipped language. */
class UvirRelease141ResourcesTest {
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
            assertTrue(folder.name, highlights[0].contains(".uvirrecords"))
            for (format in listOf("CSV", "TXT", "PNG")) assertTrue(folder.name, highlights[4].contains(format))
            assertFalse(folder.name, highlights.any { it.contains("1.3.0") || it.contains("1.4.0") })
        }
    }

    @Test fun releaseIdentityAndPublicDocumentationAgree() {
        val root = listOf(File("."), File("..")).first { File(it, "app/build.gradle.kts").isFile }
        val gradle = File(root, "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("versionCode = 6"))
        assertTrue(gradle.contains("versionName = \"1.4.1\""))
        for (file in listOf("RELEASE.md", "docs/UPDATES.md", "CREA_APK_RELEASE.bat",
            "docs/RELEASE_NOTES_1.4.1.md")) assertTrue(file, File(root, file).readText().contains("1.4.1"))
        assertTrue(File(root, "firmware/esp32/UvirSensor/UvirSensor.ino")
            .readText().contains("kFirmwareVersion[] = \"0.5.112\""))
    }
}
