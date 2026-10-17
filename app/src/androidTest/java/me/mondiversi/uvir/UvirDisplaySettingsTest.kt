package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
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

/** All preferences, exports and database paths are isolated from the user's app. */
class UvirDisplaySettingsTest {
    @get:Rule val compose = createComposeRule()
    private val original = InstrumentationRegistry.getInstrumentation().targetContext
    private val namespace = "display-test-${UUID.randomUUID()}"
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

    @Test fun encryptedBackupsIncludeTheUntouchedDefaultAndRestoreBothChoices() {
        assertTrue(showUvirRtcBatteryWarning(prefs))
        assertTrue(isTransferableAppPreference(KEY_SHOW_RTC_BATTERY_WARNING))
        assertFalse(KEY_SHOW_RTC_BATTERY_WARNING in sensorConfigurationPreferenceKeys)
        assertFalse(KEY_SHOW_RTC_BATTERY_WARNING in sensorOperationalPreferenceKeys)
        val database = UvirDatabaseHelper(context).also { it.writableDatabase }
        val password = "display-test-password".toCharArray()
        try {
            val defaultFile = createUvirAppSettingsBackup(context, password)
            assertTrue(uvirSettingsFilesRequirePassword(context, listOf(Uri.fromFile(defaultFile))))
            setUvirRtcBatteryWarning(prefs, false)
            assertFalse(showUvirRtcBatteryWarning(prefs))
            assertEquals(1, importUvirSettingsFiles(context, database, listOf(Uri.fromFile(defaultFile)), password).importedAppFiles)
            assertTrue(showUvirRtcBatteryWarning(prefs))
            setUvirRtcBatteryWarning(prefs, false)
            val disabledFile = createUvirAppSettingsBackup(context, password)
            setUvirRtcBatteryWarning(prefs, true)
            importUvirSettingsFiles(context, database, listOf(Uri.fromFile(disabledFile)), password)
            assertFalse(showUvirRtcBatteryWarning(prefs))
            assertFalse(showUvirRtcBatteryWarning(context.getSharedPreferences(PREFS_NAME, 0)))
            // Older backups without the new key must restore its enabled default.
            val legacy = File(directory, "legacy-app-settings.json").apply {
                writeText(JSONObject().put("schema", UVIR_SETTINGS_TRANSFER_SCHEMA).put("version", 1)
                    .put("scope", "app").put("payload", JSONObject()).toString())
            }
            importUvirSettingsFiles(context, database, listOf(Uri.fromFile(legacy)))
            assertTrue(showUvirRtcBatteryWarning(prefs))
        } finally { database.close() }
    }

    @Test fun settingsHeaderOffersDistinctSmallAndLargeScreensInBothThemes() {
        val dark = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        val resources = original.createConfigurationContext(Configuration(original.resources.configuration)
            .apply { setLocale(Locale.ITALIAN) }).resources
        var phoneClicks = 0
        var tabletClicks = 0
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val old = view.keepScreenOn; view.keepScreenOn = true
                onDispose { view.keepScreenOn = old }
            }
            CompositionLocalProvider(LocalContext provides context, LocalResources provides resources,
                LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                    val colors = MaterialTheme.colorScheme
                    Row(Modifier.fillMaxWidth().background(colors.background).padding(8.dp)
                        .testTag("settings-mode-selector-fixture")) {
                        UvirMenuTitle(resources.getString(R.string.acquisition_parameters),
                            modifier = Modifier.weight(1f), color = colors.onSurface)
                        UvirSettingsModeSelector(colors.onSurface, colors.onSurfaceVariant,
                            onPhoneClick = { phoneClicks++ }, onTabletClick = { tabletClicks++ })
                    }
                }
            }
        }
        for (night in listOf(false, true)) for (direction in listOf(false, true)) {
            compose.runOnIdle {
                dark.value = night; rtl.value = direction
            }
            compose.onNodeWithTag("settings_mode_phone").assertIsSelected().assertIsDisplayed()
            compose.onNodeWithTag("settings_mode_tablet").assertIsNotSelected().assertIsDisplayed()
            compose.onNodeWithTag("settings_mode_phone").assertContentDescriptionEquals("Schermo piccolo")
            compose.onNodeWithTag("settings_mode_tablet").assertContentDescriptionEquals(
                "Modalità schermo grande non ancora disponibile.")
            val phoneBounds = compose.onNodeWithTag("settings_mode_phone").getUnclippedBoundsInRoot()
            val tabletBounds = compose.onNodeWithTag("settings_mode_tablet").getUnclippedBoundsInRoot()
            assertTrue(phoneBounds.bottom - phoneBounds.top <= 40.5.dp)
            assertTrue(tabletBounds.bottom - tabletBounds.top <= 40.5.dp)
            compose.onNodeWithTag("settings_mode_phone_icon", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("settings_mode_tablet_icon", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("settings_mode_tablet").performClick().assertIsNotSelected()
            compose.onNodeWithTag("settings_mode_phone").assertIsSelected()
            compose.runOnIdle { assertEquals(1 + (if (night) 2 else 0) + (if (direction) 1 else 0), tabletClicks) }
            if (!direction) {
                File(original.externalCacheDir, "settings-mode-selector-${if (night) "night" else "day"}.png")
                    .outputStream().use { compose.onNodeWithTag("settings-mode-selector-fixture").captureToImage()
                        .asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        compose.onNodeWithTag("settings_mode_phone").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(1, phoneClicks) }
    }

    @Test fun clockBatteryWarningIsInNotificationsAndUsesTheSameAppPreference() {
        val resources = original.createConfigurationContext(Configuration(original.resources.configuration)
            .apply { setLocale(Locale.ITALIAN) }).resources
        val title = resources.getString(R.string.notifications_misc_title)
        val toggle = resources.getString(R.string.rtc_battery_notification_toggle)
        val description = resources.getString(R.string.rtc_battery_notification_description)
        setUvirRtcBatteryWarning(prefs, true)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalResources provides resources,
                LocalUvirSettingsNavigation provides UvirSettingsNavigation(UvirSettingsPage.ALERTS, {})) {
                MaterialTheme {
                    val colors = MaterialTheme.colorScheme
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)
                        .testTag("notification-settings-fixture")) {
                        UvirSamplingAndAlertSettings(
                            context = context,
                            showSamplingSection = false,
                            showAlertsSection = true,
                            sensorSettingsEnabled = false,
                            samplesText = "",
                            onSamplesTextChange = {},
                            spacingText = "",
                            onSpacingTextChange = {},
                            trimEnabled = false,
                            onTrimEnabledChange = {},
                            parametersError = null,
                            alertsSectionExpanded = true,
                            onAlertsSectionExpandedChange = {},
                            alertSoundValue = ThresholdAlertSound.SILENT.name,
                            onAlertSoundValueChange = {},
                            alertVolume = 80f,
                            onAlertVolumeChange = {},
                            onPreviewThresholdAlertSound = { _, _ -> },
                            acquisitionFeedbackSettings = AcquisitionFeedbackSettings(),
                            onAcquisitionFeedbackSettingsChange = {},
                            onPreviewAcquisitionFeedback = { _, _ -> },
                            cardColor = colors.surface,
                            primaryText = colors.onSurface,
                            secondaryText = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
        compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(toggle).performScrollTo().assertIsOn()
        compose.onNodeWithText(description).performScrollTo().assertIsDisplayed()
        File(original.externalCacheDir, "notification-settings.png").outputStream().use {
            compose.onNodeWithTag("notification-settings-fixture").captureToImage()
                .asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText(toggle).performClick().assertIsOff()
        compose.runOnIdle { assertFalse(showUvirRtcBatteryWarning(prefs)) }
        compose.runOnIdle { setUvirRtcBatteryWarning(prefs, true) }
        compose.onNodeWithText(toggle).assertIsOn()
    }

    @Test fun popupCheckboxSharesThePreferenceAndSuppressesNewWarningsUntilReenabled() {
        val ids = mutableStateOf(listOf("TEST-A"))
        val blocked = mutableStateOf(false)
        setUvirRtcBatteryWarning(prefs, false)
        val label = original.getString(R.string.rtc_clock_warning_show_each_launch)
        compose.setContent {
            MaterialTheme {
                val colors = MaterialTheme.colorScheme
                UvirRtcClockWarningHost(ids.value, emptyList(), prefs, colors.surface, colors.onSurface,
                    blocked.value, { ids.value = ids.value - it.toSet() })
            }
        }
        compose.onNodeWithTag("rtc_clock_warning_dialog").assertDoesNotExist()
        compose.runOnIdle { setUvirRtcBatteryWarning(prefs, true) }
        compose.onNodeWithText(label).assertIsOn().performClick().assertIsOff()
        // The current popup stays open, allowing the choice to be undone before closing.
        compose.runOnIdle { assertFalse(showUvirRtcBatteryWarning(prefs)) }
        compose.onNodeWithText(label).performClick().assertIsOn()
        compose.runOnIdle { assertTrue(showUvirRtcBatteryWarning(prefs)) }
        compose.onNodeWithText(label).performClick().assertIsOff()
        compose.runOnIdle { assertFalse(showUvirRtcBatteryWarning(prefs)); ids.value += "TEST-B" }
        compose.onAllNodesWithTag("rtc_clock_warning_dialog").assertCountEquals(1)
        compose.onNodeWithText("• TEST-A").assertIsDisplayed()
        compose.onNodeWithText("• TEST-B").assertIsDisplayed()
        compose.onNodeWithContentDescription(original.getString(R.string.close)).performClick()
        compose.onNodeWithTag("rtc_clock_warning_dialog").assertDoesNotExist()
        compose.runOnIdle { ids.value = listOf("TEST-C") }
        compose.onNodeWithTag("rtc_clock_warning_dialog").assertDoesNotExist()
        compose.runOnIdle { setUvirRtcBatteryWarning(prefs, true) }
        compose.onNodeWithText(label).assertIsOn().assertIsDisplayed()
        compose.onNodeWithContentDescription(original.getString(R.string.close)).performClick()
        // A warning deferred behind another popup respects a new opt-out.
        compose.runOnIdle { blocked.value = true; ids.value = listOf("TEST-D") }
        compose.onNodeWithTag("rtc_clock_warning_dialog").assertDoesNotExist()
        compose.runOnIdle { setUvirRtcBatteryWarning(prefs, false); blocked.value = false }
        compose.onNodeWithTag("rtc_clock_warning_dialog").assertDoesNotExist()
    }

    @Test fun allLanguagesTranslateNotificationsAndModeSelector() {
        for (language in listOf("en", "it", "ar", "de", "el", "es", "fa", "fr", "hi", "he",
            "ja", "ko-KR", "pt", "ru", "sw", "pl", "nl", "id", "tr", "zh-CN")) {
            val resources = original.createConfigurationContext(Configuration(original.resources.configuration)
                .apply { setLocale(Locale.forLanguageTag(language)) }).resources
            for (key in listOf(R.string.settings_section_notifications,
                R.string.notifications_misc_title, R.string.notifications_misc_description,
                R.string.rtc_battery_notification_toggle, R.string.rtc_battery_notification_description,
                R.string.settings_mode_small_screen, R.string.settings_mode_small_screen_active,
                R.string.settings_mode_large_screen,
                R.string.settings_mode_large_screen_unavailable,
                R.string.rtc_clock_warning_show_each_launch)) {
                assertTrue("$language/$key", resources.getString(key).isNotBlank())
            }
        }
    }
}
