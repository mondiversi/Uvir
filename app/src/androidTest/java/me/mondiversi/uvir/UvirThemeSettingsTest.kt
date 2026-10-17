package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import java.util.UUID
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Test-only preferences and databases; no records or settings in the user's archive. */
class UvirThemeSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val original = InstrumentationRegistry.getInstrumentation().targetContext
    private val namespace = "theme-test-${UUID.randomUUID()}"
    private val names = mutableSetOf<String>()
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var prefs: SharedPreferences

    @Before fun setUp() {
        directory = File(original.cacheDir, namespace).also { assertTrue(it.mkdir()) }
        context = object : ContextWrapper(original) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val isolated = "$namespace-$name"
                names += isolated
                return super.getSharedPreferences(isolated, mode)
            }
            override fun getDatabasePath(name: String) = File(directory, name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?,
                handler: DatabaseErrorHandler?) = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory, handler)
        }
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @After fun tearDown() {
        directory.deleteRecursively()
        names.forEach { original.deleteSharedPreferences(it) }
    }

    @Test fun themeSwitchesImmediatelyWithoutReplacingContextOrRememberedState() {
        val system = mutableStateOf(Configuration(original.resources.configuration).apply {
            setLocale(Locale.ITALIAN)
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        })
        val rtl = mutableStateOf(false)
        val rememberedIdentities = mutableSetOf<Any>()
        var observedDark = false
        var observedBackground = Color.Unspecified
        var actualContext: Context? = null
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val old = view.keepScreenOn; view.keepScreenOn = true
                onDispose { view.keepScreenOn = old }
            }
            CompositionLocalProvider(LocalContext provides context,
                LocalConfiguration provides system.value,
                LocalResources provides original.createConfigurationContext(system.value).resources,
                LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                UvirThemeOverride(prefs) {
                    UvirActionTheme {
                        val identity = remember { Any() }
                        val dark = isSystemInDarkTheme()
                        val currentContext = LocalContext.current
                        val background = MaterialTheme.colorScheme.background
                        SideEffect {
                            rememberedIdentities += identity
                            observedDark = dark
                            observedBackground = background
                            actualContext = currentContext
                        }
                        UvirAdaptiveDisplay {
                            val card = if (dark) Color(0xFF1C242B) else Color.White
                            val primary = if (dark) Color.White else Color(0xFF101418)
                            val secondary = if (dark) Color(0xFF90A4AE) else Color(0xFF546E7A)
                            Column(Modifier.fillMaxSize().background(background)
                                .verticalScroll(rememberScrollState()).padding(8.dp).testTag("theme-fixture")) {
                                Text(LocalResources.current.getString(R.string.settings_section_appearance), color = primary)
                                UvirThemeSettingsIsland(card, primary, secondary)
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Predefinito").assertIsSelected()
        compose.runOnIdle { assertFalse(observedDark); assertSame(context, actualContext) }
        val dayBackground = observedBackground
        compose.onNode(hasText("Scuro") and !hasText("Predefinito")).performClick().assertIsSelected()
        compose.runOnIdle {
            assertTrue(observedDark)
            assertEquals(UvirAppTheme.DARK, readUvirAppTheme(context.getSharedPreferences(PREFS_NAME, 0)))
            assertNotEquals(dayBackground, observedBackground)
        }
        capture("night")
        compose.onNode(hasText("Chiaro") and !hasText("Predefinito")).performClick().assertIsSelected()
        compose.runOnIdle {
            assertFalse(observedDark)
            system.value = Configuration(system.value).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
        }
        compose.runOnIdle { assertFalse(observedDark) }
        capture("day")
        compose.onNodeWithText("Predefinito").performClick().assertIsSelected()
        compose.runOnIdle { assertTrue(observedDark); rtl.value = true }
        compose.onNode(hasText("Chiaro") and !hasText("Predefinito")).performClick().assertIsSelected()
        compose.runOnIdle {
            assertFalse(observedDark)
            assertEquals(1, rememberedIdentities.size)
            assertSame(context, actualContext)
            assertEquals(Configuration.UI_MODE_NIGHT_YES,
                system.value.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        }
    }

    private fun capture(suffix: String) {
        File(original.externalCacheDir, "theme-settings-$suffix.png").outputStream().use {
            compose.onNodeWithTag("theme-fixture").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun displayOptionsRemainDisabledAndSmallSelectedInBothThemesAndDirections() {
        val dark = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        val resources = original.createConfigurationContext(Configuration(original.resources.configuration)
            .apply { setLocale(Locale.ITALIAN) }).resources
        val initialPreferences = prefs.all.toMap()
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val old = view.keepScreenOn; view.keepScreenOn = true
                onDispose { view.keepScreenOn = old }
            }
            CompositionLocalProvider(LocalContext provides context, LocalResources provides resources,
                LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalUvirSettingsNeutralIcons provides true) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    UvirAdaptiveDisplay {
                        Column(Modifier.fillMaxSize().background(colors.background)
                            .verticalScroll(rememberScrollState()).padding(8.dp).testTag("display-fixture")) {
                            UvirDisplaySettingsIsland(colors.surface, colors.onSurface, colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        for (night in listOf(false, true)) for (direction in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; rtl.value = direction }
            compose.onNodeWithText("Visualizzazione").assertIsDisplayed()
            val small = compose.onNodeWithText("Schermo piccolo")
            val large = compose.onNodeWithText("Schermo grande")
            small.assertIsDisplayed().assertIsSelected().assertIsNotEnabled()
            large.assertIsDisplayed().assertIsNotSelected().assertIsNotEnabled()
            val unavailable = compose.onNodeWithText("Questa funzionalità non è ancora disponibile.")
            unavailable.assertIsDisplayed()
            assertTrue(unavailable.fetchSemanticsNode().boundsInRoot.top >=
                large.fetchSemanticsNode().boundsInRoot.bottom)
            small.performTouchInput { click() }
            large.performTouchInput { click() }
            small.assertIsSelected().assertIsNotEnabled()
            large.assertIsNotSelected().assertIsNotEnabled()
            compose.runOnIdle { assertEquals(initialPreferences, prefs.all) }
            if (!direction) {
                File(original.externalCacheDir, "display-options-${if (night) "night" else "day"}.png")
                    .outputStream().use {
                        compose.onNodeWithTag("display-fixture").captureToImage().asAndroidBitmap()
                            .compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
            }
        }
    }

    @Test fun encryptedBackupsRestoreEveryThemeAndLegacyBackupsRestoreDefault() {
        assertEquals(UvirAppTheme.DEFAULT, readUvirAppTheme(prefs))
        val database = UvirDatabaseHelper(context).also { it.writableDatabase }
        val password = "theme-test-password".toCharArray()
        try {
            for (theme in UvirAppTheme.entries) {
                if (theme != UvirAppTheme.DEFAULT) setUvirAppTheme(prefs, theme)
                val file = createUvirAppSettingsBackup(context, password)
                assertTrue(file.readText().contains("ciphertext"))
                setUvirAppTheme(prefs, if (theme == UvirAppTheme.DARK) UvirAppTheme.LIGHT else UvirAppTheme.DARK)
                importUvirSettingsFiles(context, database, listOf(Uri.fromFile(file)), password)
                assertEquals(theme, readUvirAppTheme(prefs))
            }
            val legacy = File(directory, "legacy-app-settings.json").apply {
                writeText(JSONObject().put("schema", UVIR_SETTINGS_TRANSFER_SCHEMA).put("version", 1)
                    .put("scope", "app").put("payload", JSONObject()).toString())
            }
            importUvirSettingsFiles(context, database, listOf(Uri.fromFile(legacy)))
            assertEquals(UvirAppTheme.DEFAULT, readUvirAppTheme(prefs))
        } finally { database.close() }
    }

    @Test fun nightOverridePreservesLocaleDimensionsFontScaleAndOtherUiModeFlags() {
        val source = Configuration(original.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ar")); fontScale = 1.3f
            uiMode = Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_YES
        }
        for (theme in UvirAppTheme.entries) {
            val actual = uvirThemeConfiguration(source, theme)
            assertEquals(source.locales, actual.locales)
            assertEquals(source.fontScale, actual.fontScale)
            assertEquals(source.screenWidthDp, actual.screenWidthDp)
            assertEquals(source.smallestScreenWidthDp, actual.smallestScreenWidthDp)
            assertEquals(Configuration.UI_MODE_TYPE_TELEVISION, actual.uiMode and Configuration.UI_MODE_TYPE_MASK)
            assertEquals(if (theme == UvirAppTheme.LIGHT) Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES,
                actual.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        }
        assertEquals(Configuration.UI_MODE_NIGHT_YES, source.uiMode and Configuration.UI_MODE_NIGHT_MASK)
    }
}
