package me.mondiversi.uvir

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class UvirSelectionControlContractTest {
    private fun sources(): List<File> {
        val root = listOf(File("src/main/java/me/mondiversi/uvir"),
            File("app/src/main/java/me/mondiversi/uvir")).first { it.isDirectory }
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test fun everySelectionControlExplicitlyUsesTheSharedPalette() {
        val palettes = mapOf("Checkbox" to "uvirCheckboxColors", "TriStateCheckbox" to "uvirCheckboxColors",
            "RadioButton" to "uvirRadioButtonColors", "Slider" to "uvirSliderColors",
            "FilterChip" to "uvirSelectionChipColors")
        var inspected = 0
        // Mask comments and strings, then balance each actual call's arguments.
        val nonCode = Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/|\"(?:\\\\.|[^\"\\\\])*+\"")
        for (file in sources()) {
            val code = nonCode.replace(file.readText()) { " ".repeat(it.value.length) }
            for ((control, palette) in palettes) {
                for (call in Regex("\\b$control\\s*\\(").findAll(code)) {
                    inspected++
                    var depth = 1
                    var end = call.range.last + 1
                    while (end < code.length && depth > 0) {
                        when (code[end++]) { '(' -> depth++; ')' -> depth-- }
                    }
                    val arguments = code.substring(call.range.last + 1, end)
                    assertTrue("$control in ${file.name} must use $palette",
                        Regex("\\bcolors\\s*=\\s*$palette\\s*\\(").containsMatchIn(arguments))
                }
            }
        }
        assertTrue("All existing selection controls must be inspected", inspected >= 28)
    }

    @Test fun materialSelectionDefaultsAreOnlyOwnedByTheSharedPalette() {
        val factory = Regex("\\b(?:CheckboxDefaults|RadioButtonDefaults|SliderDefaults)\\s*\\.\\s*colors\\s*\\(|" +
            "\\bFilterChipDefaults\\s*\\.\\s*filterChipColors\\s*\\(")
        assertEquals(listOf("UvirSelectionControlColors.kt"),
            sources().filter { factory.containsMatchIn(it.readText()) }.map { it.name })
    }

    @Test fun titlePressCircleGrowsInsideTheUnchangedFortyDpTarget() {
        val source = sources().single { it.name == "UvirNavigationChrome.kt" }.readText()
        assertTrue(source.contains("UvirTitleActionButtonSize = 40.dp"))
        assertTrue(source.contains("UvirTitleActionIconSize = 24.dp"))
        assertTrue(source.contains("UvirTitleActionPressedVisualSize = 38.dp"))
        for (name in listOf("UvirNavigationChrome.kt", "UvirRecordListFilterPanel.kt", "UvirSessionSequenceFilter.kt")) {
            assertTrue("$name must share the expanded press circle",
                sources().single { it.name == name }.readText().contains(
                    "pressedVisualSize = UvirTitleActionPressedVisualSize"))
        }
    }
}
