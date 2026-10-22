package me.mondiversi.uvir

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

/** Resource-only checks: no application preferences, records or sensor state changed. */
class UvirLocalizedResourcesTest {
    private data class Entry(
        val type: String,
        val text: String,
        val translatable: Boolean,
        val formatted: Boolean,
        val quantities: Map<String, String>,
        val itemCount: Int,
        val sourceFile: String
    )

    private val catalogs by lazy {
        val root = listOf(File("src/main/res"), File("app/src/main/res"))
            .firstOrNull { it.isDirectory }
        requireNotNull(root) { "Application resources unavailable" }
        root.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "strings.xml").isFile }
            .sortedBy { it.name }
            .associate { it.name to loadCatalog(it) }
    }

    private val formatArgument =
        Regex("(?<!%)%(?:(\\d+)\\$)?[-#+ 0,(]*(?:\\d+)?(?:\\.\\d+)?([a-zA-Z])")

    private fun arguments(text: String): List<Pair<Int, Char>> {
        var implicitIndex = 1
        return formatArgument.findAll(text)
            .filter { it.groupValues[2] != "n" }
            .map { match ->
                val index = match.groupValues[1].toIntOrNull() ?: implicitIndex++
                index to match.groupValues[2].single().lowercaseChar()
            }
            .sortedWith(compareBy<Pair<Int, Char>> { it.first }.thenBy { it.second })
            .toList()
    }

    private fun loadCatalog(directory: File): Map<String, Entry> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val result = linkedMapOf<String, Entry>()
        for (file in directory.listFiles().orEmpty().filter { it.extension == "xml" }) {
            val nodes = factory.newDocumentBuilder().parse(file).documentElement.childNodes
            for (index in 0 until nodes.length) {
                val node = nodes.item(index) as? Element ?: continue
                val type = node.tagName
                if (type !in setOf("string", "plurals", "string-array")) continue
                val key = type + ":" + node.getAttribute("name")
                assertFalse("Duplicate resource in " + directory.name + "/" + key, result.containsKey(key))
                val itemNodes = node.getElementsByTagName("item")
                val quantities = linkedMapOf<String, String>()
                if (type == "plurals") {
                    for (itemIndex in 0 until itemNodes.length) {
                        val item = itemNodes.item(itemIndex) as Element
                        val quantity = item.getAttribute("quantity")
                        assertFalse("Duplicate plural form: " + directory.name + "/" + key,
                            quantities.containsKey(quantity))
                        quantities[quantity] = item.textContent
                    }
                    assertTrue("Missing other plural form: " + directory.name + "/" + key,
                        quantities.containsKey("other"))
                }
                assertTrue("Empty resource: " + directory.name + "/" + key, node.textContent.isNotBlank())
                result[key] = Entry(type, node.textContent,
                    node.getAttribute("translatable") != "false",
                    node.getAttribute("formatted") != "false", quantities, itemNodes.length, file.name)
            }
        }
        return result
    }

    @Test fun bothVolumeSlidersUseTheSameConciseAlarmVolumeHintInEveryLanguage() {
        for ((locale, catalog) in catalogs) {
            val acquisition = catalog.getValue("string:acquisition_feedback_preview_hint").text
            val alert = catalog.getValue("string:threshold_sound_preview_hint").text
            assertEquals("Volume hint mismatch: $locale", acquisition, alert)
            assertFalse("Old preview/recording explanation: $locale", acquisition.contains(". "))
        }
        assertEquals("Il volume finale dipende anche dal volume allarmi del telefono.",
            catalogs.getValue("values-it").getValue("string:threshold_sound_preview_hint").text)
    }

    @Test fun allLanguagesHaveCompleteResourceCatalogs() {
        val source = catalogs.getValue("values")
        val required = source.filterValues { it.translatable }.keys
        assertTrue("No localized catalogs found", catalogs.size > 1)
        for ((locale, catalog) in catalogs) {
            assertEquals("Missing translations: $locale", emptySet<String>(), required - catalog.keys)
            assertEquals("Unknown translations: $locale", emptySet<String>(), catalog.keys - source.keys)
            for ((key, base) in source.filterValues { it.translatable && it.type == "string-array" }) {
                assertEquals("Array length: $locale/$key", base.itemCount, catalog.getValue(key).itemCount)
            }
        }
    }

    @Test fun removedProjectNumbersLeaveNoUnusedTranslations() {
        for ((locale, catalog) in catalogs) {
            assertTrue("Obsolete project counters: $locale",
                catalog.keys.none { it.startsWith("string:project_numbers_") })
        }
    }

    @Test fun appearanceThemeAndSeparateUnitsAreLocalizedInEveryLanguage() {
        for ((locale, catalog) in catalogs) {
            assertTrue("Missing appearance title: $locale",
                catalog.getValue("string:settings_section_appearance").text.isNotBlank())
            assertTrue("Missing units title: $locale",
                catalog.getValue("string:settings_section_language_formats").text.isNotBlank())
            for (key in listOf("settings_section_display", "settings_display_description", "settings_display_unavailable",
                "settings_mode_small_screen", "settings_mode_large_screen")) {
                assertTrue("Missing display label: $locale/$key",
                    catalog.getValue("string:$key").text.isNotBlank())
            }
            assertNotEquals("Appearance and units must be distinct: $locale",
                catalog.getValue("string:settings_section_appearance").text,
                catalog.getValue("string:settings_section_language_formats").text)
            for (suffix in listOf("title", "description", "default", "light", "dark")) {
                assertTrue("Missing theme label: $locale/$suffix",
                    catalog.getValue("string:settings_theme_$suffix").text.isNotBlank())
            }
        }
        val italian = catalogs.getValue("values-it")
        assertEquals("Aspetto", italian.getValue("string:settings_section_appearance").text)
        assertEquals("Appearance", catalogs.getValue("values").getValue("string:settings_section_appearance").text)
        assertEquals("Unità", italian.getValue("string:settings_section_language_formats").text)
        assertEquals("Questa funzionalità non è ancora disponibile.",
            italian.getValue("string:settings_display_unavailable").text)
        assertEquals("Units", catalogs.getValue("values").getValue("string:settings_section_language_formats").text)
        assertEquals("Tema", italian.getValue("string:settings_theme_title").text)
        assertEquals("Scegli il tema dell’app per personalizzare l’aspetto dei contenuti.",
            italian.getValue("string:settings_theme_description").text)
    }

    @Test fun newLanguagesHaveCompletePluralFormsAndNativeMenuLabels() {
        for ((locale, forms) in mapOf(
            "values-pl" to setOf("one", "few", "many", "other"),
            "values-nl" to setOf("one", "other"),
            "values-in" to setOf("other")
        )) {
            for ((key, entry) in catalogs.getValue(locale).filterValues { it.type == "plurals" }) {
                assertEquals("Incomplete plural forms: $locale/$key", forms, entry.quantities.keys)
            }
        }
        assertEquals(AppLanguage.entries.count { it != AppLanguage.SYSTEM }, catalogs.size)
        for ((locale, catalog) in catalogs) {
            assertEquals("Polish menu label: $locale", "Polski", catalog.getValue("string:language_polish").text)
            assertEquals("Dutch menu label: $locale", "Nederlands", catalog.getValue("string:language_dutch").text)
            assertEquals("Indonesian menu label: $locale", "Bahasa Indonesia", catalog.getValue("string:language_indonesian").text)
        }
    }

    @Test fun allLanguagesKeepTheSameDomainResourceFiles() {
        val domains = setOf("sensor_information.xml", "diagnostics.xml", "data_export.xml", "optical_window.xml")
        for ((key, base) in catalogs.getValue("values").filterValues { it.sourceFile in domains }) {
            for ((locale, catalog) in catalogs) {
                assertEquals("Resource domain changed: $locale/$key", base.sourceFile,
                    catalog.getValue(key).sourceFile)
            }
        }
    }

    @Test fun pluralFormsDoNotIncludeCategoriesUnusedByTheirLanguage() {
        val allowed = mapOf(
            "values-ja" to setOf("other"),
            "values-ko-rKR" to setOf("other"),
            "values-zh-rCN" to setOf("other"),
            "values-iw" to setOf("one", "two", "other"),
            "values-pl" to setOf("one", "few", "many", "other"),
            "values-nl" to setOf("one", "other"),
            "values-in" to setOf("other")
        )
        for ((locale, forms) in allowed) {
            for ((key, entry) in catalogs.getValue(locale).filterValues { it.type == "plurals" }) {
                assertTrue("Unused plural forms: $locale/$key", forms.containsAll(entry.quantities.keys))
            }
        }
    }

    @Test fun localizedStringsAndPluralFormsPreserveFormatArguments() {
        val source = catalogs.getValue("values")
        val issues = mutableListOf<String>()
        for ((locale, catalog) in catalogs) {
            for ((key, base) in source.filterValues { it.translatable && it.formatted }) {
                val translated = catalog.getValue(key)
                if (base.type == "string") {
                    if (arguments(base.text) != arguments(translated.text)) {
                        issues += "Format arguments: $locale/$key"
                    }
                } else if (base.type == "plurals") {
                    for ((quantity, text) in translated.quantities) {
                        // The other form carries the full argument contract;
                        // singular forms may already express the count in words.
                        val original = base.quantities.getValue("other")
                        var expected = arguments(original)
                        var actual = arguments(text)
                        // Zero, one and two may use words or noun forms instead
                        // of a digit. All remaining arguments must still match.
                        if (quantity in setOf("zero", "one", "two")) {
                            expected = expected.filterNot { it.first == 1 && it.second == 'd' }
                            actual = actual.filterNot { it.first == 1 && it.second == 'd' }
                        }
                        if (expected != actual) {
                            issues += "Plural format arguments: $locale/$key/$quantity: $expected -> $actual"
                        }
                    }
                }
            }
        }
        assertEquals("Localized format issues", emptyList<String>(), issues)
    }

    @Test fun sourceAndItalianUseGenericSensorWording() {
        for ((locale, pattern) in mapOf(
            "values" to Regex("\\bthis\\s+sensor\\b", RegexOption.IGNORE_CASE),
            "values-it" to Regex("\\bquesto\\s+sensore\\b", RegexOption.IGNORE_CASE)
        )) {
            for ((key, entry) in catalogs.getValue(locale)) {
                assertFalse("Non-generic sensor wording: $locale/$key", pattern.containsMatchIn(entry.text))
            }
        }
    }

    @Test fun disconnectedHintRefersToASensorRatherThanTheOnlySensor() {
        val key = "string:home_sensor_disconnected_description"
        assertEquals(
            "Connetti un sensore per effettuare acquisizioni, registrare allerte o modificarne le impostazioni.",
            catalogs.getValue("values-it").getValue(key).text
        )
        assertEquals(
            "Connect a sensor to acquire data, record alerts, or change its settings.",
            catalogs.getValue("values").getValue(key).text
        )
        assertEquals(1, Regex("\\bsensore\\b").findAll(catalogs.getValue("values-it").getValue(key).text).count())
        assertEquals("Nessun sensore associato", catalogs.getValue("values-it")
            .getValue("string:sensor_status_no_associated_sensor").text)
        assertEquals("Non connesso", catalogs.getValue("values-it")
            .getValue("string:sensor_status_no_sensor").text)
        for ((locale, fragment) in mapOf(
            "values-de" to "einen Sensor", "values-el" to "έναν αισθητήρα",
            "values-pt" to "um sensor", "values-fa" to "یک حسگر",
            "values-es" to "un sensor", "values-ar" to "مستشعرًا",
            "values-hi" to "एक सेंसर", "values-fr" to "un capteur",
            "values-tr" to "bir sensör", "values-iw" to "חבר חיישן",
            "values-nl" to "een sensor"
        )) {
            assertTrue("Indefinite sensor wording: $locale", catalogs.getValue(locale).getValue(key).text.contains(fragment))
        }
    }

    @Test fun unassociatedAndDisconnectedStatesHaveSeparateTranslationsInEveryLanguage() {
        assertEquals(AppLanguage.entries.count { it != AppLanguage.SYSTEM }, catalogs.size)
        for ((locale, catalog) in catalogs) {
            val unassociated = catalog.getValue("string:sensor_status_no_associated_sensor").text
            val disconnected = catalog.getValue("string:sensor_status_no_sensor").text
            assertTrue("Missing unassociated status: $locale", unassociated.isNotBlank())
            assertNotEquals("Association and connection are separate states: $locale", disconnected, unassociated)
            assertTrue("Unexpected format arguments: $locale", arguments(unassociated).isEmpty())
        }
    }

    @Test fun mockSensorSelectionHintExplainsSelectionBeforeTheSettingsPath() {
        assertEquals(
            "Dati fittizi attivi: per selezionare un sensore, disattivali in Impostazioni → Sensore → Diagnostica.",
            catalogs.getValue("values-it").getValue("string:sensor_source_debug_hint").text
        )
        assertEquals(
            "Mock data is active: to select a sensor, disable it in Settings → Sensor → Diagnostics.",
            catalogs.getValue("values").getValue("string:sensor_source_debug_hint").text
        )
        for ((locale, catalog) in catalogs) {
            val text = catalog.getValue("string:sensor_source_debug_hint").text
            assertTrue("Missing explanation separator: $locale", text.contains(':') || text.contains('：'))
            assertEquals("Settings path: $locale", 2, text.count { it == '→' })
        }
    }

    @Test fun mockSettingsShortcutUsesTheSettingsLabelInEveryLanguage() {
        assertEquals("Impostazioni",
            catalogs.getValue("values-it").getValue("string:sensor_simulation_open_settings").text)
        assertEquals("Settings",
            catalogs.getValue("values").getValue("string:sensor_simulation_open_settings").text)
        for ((locale, catalog) in catalogs) {
            assertEquals(locale, catalog.getValue("string:acquisition_parameters").text,
                catalog.getValue("string:sensor_simulation_open_settings").text)
        }
    }

    @Test fun simulatedEventsUseTheCompactDescriptionWithoutSensorIdentity() {
        assertEquals("Aggiungi acquisizioni, allerte simulate.",
            catalogs.getValue("values-it").getValue("string:debug_simulate_events_description").text)
        assertEquals("Add simulated acquisitions and alerts.",
            catalogs.getValue("values").getValue("string:debug_simulate_events_description").text)
        for ((locale, catalog) in catalogs) {
            assertFalse("The compact event description must not include the sensor identity: $locale",
                catalog.getValue("string:debug_simulate_events_description").text.contains('—'))
        }
    }

    @Test fun hebrewRemainsUnvocalized() {
        val points = Regex("[\u0591-\u05BD\u05BF\u05C1\u05C2\u05C4\u05C5\u05C7]")
        for ((key, entry) in catalogs.getValue("values-iw")) {
            assertFalse("Hebrew vocalization marks: $key", points.containsMatchIn(entry.text))
        }
    }
}
