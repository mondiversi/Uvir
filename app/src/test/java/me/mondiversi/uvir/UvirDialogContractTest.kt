package me.mondiversi.uvir

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Keep new popups on the shared scrolling contract instead of per-dialog fixes. */
class UvirDialogContractTest {
    private fun sources(): List<File> {
        val root = listOf(File("src/main/java/me/mondiversi/uvir"),
            File("app/src/main/java/me/mondiversi/uvir")).firstOrNull { it.isDirectory }
        requireNotNull(root) { "Application sources unavailable" }
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test fun allPopupsUseTheSharedDialogContainer() {
        val nativeDialog = Regex("\\b(?:AlertDialog|BasicAlertDialog|MaterialAlertDialog|Dialog)\\s*\\(")
        val callers = sources().filter { nativeDialog.containsMatchIn(it.readText()) }
        assertEquals(listOf("UvirDialogTitle.kt"), callers.map { it.name })
        // Action-free and confirmation dialogs share the same scrolling owner.
        assertEquals(2, nativeDialog.findAll(callers.single().readText()).count())
    }

    @Test fun scrollbarSetupIsOnlyOwnedByTheSharedContainer() {
        val owners = sources().filter { it.readText().contains("rememberUvirDialogScrollbar(") }
        assertEquals(setOf("UvirDialogTitle.kt", "UvirScrollAndPage.kt"), owners.map { it.name }.toSet())
        val container = owners.single { it.name == "UvirDialogTitle.kt" }.readText()
        assertTrue(container.contains("modifier.then(scrollbar.dialogModifier)"))
        assertTrue(container.contains(".verticalScroll(scrollbar.scrollState)"))
        assertTrue(container.contains(".then(scrollbar.viewportModifier)"))
    }

    @Test fun popupBodiesDoNotIntroduceNestedVerticalScrollers() {
        var calls = 0
        // Mask comments and strings before balancing parentheses, preserving offsets.
        val nonCode = Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/|\"(?:\\\\.|[^\"\\\\])*\"")
        for (file in sources().filter { it.name != "UvirDialogTitle.kt" }) {
            val code = nonCode.replace(file.readText()) { " ".repeat(it.value.length) }
            for (match in Regex("\\bUvirAlertDialog\\s*\\(").findAll(code)) {
                calls++
                var depth = 1
                var end = match.range.last + 1
                while (end < code.length && depth > 0) {
                    when (code[end++]) { '(' -> depth++; ')' -> depth-- }
                }
                val arguments = code.substring(match.range.last + 1, end)
                assertFalse("Nested popup scrolling in ${file.name}",
                    Regex("\\b(?:verticalScroll|LazyColumn|scrollbarOverlay)\\s*\\(").containsMatchIn(arguments))
            }
        }
        assertTrue("No popup callers were inspected", calls > 30)
    }
}
