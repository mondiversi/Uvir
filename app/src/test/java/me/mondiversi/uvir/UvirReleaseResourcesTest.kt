package me.mondiversi.uvir

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

/** The release page must describe this release in every shipped language. */
class UvirReleaseResourcesTest {
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

    @Test fun releaseIdentityAndPublicDocumentationAgree() {
        val root = listOf(File("."), File("..")).first { File(it, "app/build.gradle.kts").isFile }
        val gradle = File(root, "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("versionCode = 10"))
        assertTrue(gradle.contains("versionName = \"0.1.1\""))
        assertTrue(File(root, "README.md").readText().contains("`0.1.1`"))
        for (name in listOf("RELEASE.md", "docs/UPDATES.md", "CREA_APK_RELEASE.bat")) {
            assertTrue(name, File(root, name).readText().contains("0.1.1"))
        }
    }
}
