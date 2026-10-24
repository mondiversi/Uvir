package me.mondiversi.uvir

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.UUID

class UvirRecordArchiveUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private lateinit var database: UvirDatabaseHelper
    private val night = mutableStateOf(false)
    private val rtl = mutableStateOf(false)

    @Before fun setup() {
        root = File(context.cacheDir, "archive-ui-${UUID.randomUUID()}").apply { check(mkdir()) }
        database = UvirDatabaseHelper(object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String) = File(root, name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?) = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory, errorHandler)
        })
    }
    @After fun cleanup() { database.close(); root.deleteRecursively() }

    @Test fun islandUsesStandardActionsAndUpdatesOnlyItsOwnExportAvailability() {
        fixture {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    UvirRecordArchiveIsland(database, MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        compose.waitUntil { compose.onAllNodesWithTag("archive_import_ALERTS").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("archive_export_ACQUISITIONS").assertIsNotEnabled()
        compose.onNodeWithTag("archive_export_ALERTS").assertIsNotEnabled()
        compose.onNodeWithTag("archive_import_ACQUISITIONS").assertIsEnabled()
        compose.runOnIdle { database.saveAcquisition(SensorSample(), "private fixture", timestamp = 1000) }
        compose.waitUntil { runCatching { compose.onNodeWithTag("archive_export_ACQUISITIONS").assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag("archive_export_ALERTS").assertIsNotEnabled()
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            compose.onNodeWithTag("archive_export_ACQUISITIONS").performScrollTo()
            saveScreenshot("record-archive-island-${if (dark) "night" else "day"}")
        }
        compose.onNodeWithTag("archive_export_ACQUISITIONS").performClick()
        compose.onNodeWithText(context.getString(R.string.record_archive_export_description)).assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_encryption_password)).assertDoesNotExist()
    }

    @Test fun importPreviewHasUppercaseActionsAndBlocksThemWhileWorkingInBothThemesAndDirections() {
        val busy = mutableStateOf(false)
        var imported = 0
        fixture {
            UvirRecordArchiveImportDialog(123, 4, busy.value, MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant,
                onDismiss = {}, onImport = { imported++ })
        }
        for (dark in listOf(false, true)) for (direction in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; rtl.value = direction; busy.value = false }
            // The shared accessibility action requires a second activation, like deletion.
            val action = compose.onNodeWithTag("archive_import_confirm").assertIsEnabled()
            action.performSemanticsAction(SemanticsActions.OnClick) { it() }
            action.performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.onNodeWithText(context.getString(R.string.data_management_import_button).uppercase(Locale.getDefault()), useUnmergedTree = true).assertExists()
            compose.onNodeWithText(context.getString(R.string.settings_encryption_password)).assertDoesNotExist()
            compose.runOnIdle { busy.value = true }
            compose.onNodeWithTag("archive_import_confirm").assertIsNotEnabled()
            compose.runOnIdle { busy.value = false }
            if (!direction) saveScreenshot("record-archive-preview-${if (dark) "night" else "day"}")
        }
        assertEquals(4, imported)
    }

    private fun fixture(content: @Composable () -> Unit) {
        compose.setContent {
            val view = LocalView.current
            DisposableEffect(view) {
                val previous = view.keepScreenOn
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = previous }
            }
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                MaterialTheme(colorScheme = if (night.value) darkColorScheme() else lightColorScheme()) {
                    UvirActionTheme(content)
                }
            }
        }
    }
    private fun saveScreenshot(name: String) {
        val node = if (name.contains("-preview-")) compose.onNodeWithTag("record_archive_import_dialog") else compose.onRoot()
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            assertTrue(node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
