package me.mondiversi.uvir

import android.content.Context
import androidx.test.espresso.Espresso.pressBack
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.junit.*
import org.junit.Assert.*

/** Real list composables, but a separately resolved private temporary database. */
class UvirRecordListFilterIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var directory: File
    private lateinit var database: UvirDatabaseHelper
    private fun text(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Before fun preparePrivateDatabase() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(base.cacheDir, "list-filter-test-" + UUID.randomUUID())
        assertTrue(directory.mkdir())
        val isolatedContext = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String) = File(directory, name)
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
                error("Filter tests must never access app preferences")
            override fun openOrCreateDatabase(name: String, mode: Int,
                factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int,
                factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory, errorHandler)
        }
        database = UvirDatabaseHelper(isolatedContext)
        assertTrue(database.writableDatabase.path.startsWith(directory.absolutePath + File.separator))
        assertNotEquals(base.getDatabasePath("uvir.db").absolutePath, database.writableDatabase.path)
    }

    @After fun closePrivateDatabase() {
        database.close()
        directory.listFiles().orEmpty().forEach { assertTrue(it.delete()) }
        assertTrue(directory.delete())
    }

    @Test fun acquisitionListKeepsChoiceFiltersAccessibleAndResetsWithoutChangingRecords() {
        repeat(3) { database.saveAcquisition(SensorSample(), "Garden", automatic = true,
            sessionId = 10, sessionSequence = it + 1, sensorDeviceId = "TEST-A") }
        compose.setContent {
            MaterialTheme {
                HistoryScreen(database, rememberLazyListState(), ListEdgeAnchor.START, {},
                    Color.White, Color.White, Color.Black, Color.Gray, {}, {}, {})
            }
        }
        exerciseFilterAndReset()
        compose.runOnIdle { assertEquals(3, database.readSavedRecords().size) }
    }

    @Test fun alertListKeepsChoiceFiltersAccessibleAndResetsWithoutChangingRecords() {
        val rule = ThresholdAlertRule(ThresholdAlertMetric.UVA, true, ThresholdAlertDirection.ABOVE, 1.0f)
        repeat(3) { database.insertThresholdAlertLog(
            listOf(ThresholdAlertViolation(rule, 2.0)), timestamp = 1000L + it,
            sessionId = 10, sensorDeviceId = "TEST-A") }
        compose.setContent {
            MaterialTheme {
                ThresholdAlertLogScreen(database, rememberLazyListState(),
                    Color.White, Color.White, Color.Black, Color.Gray, {})
            }
        }
        exerciseFilterAndReset()
        compose.runOnIdle { assertEquals(3, database.readThresholdAlertLog().size) }
    }

    @Test fun acquisitionSelectionStillWorksWithoutTheTitleSelectionIcon() {
        repeat(3) { database.saveAcquisition(SensorSample(), "Garden", automatic = true,
            sessionId = 10, sessionSequence = it + 1, sensorDeviceId = "TEST-A") }
        compose.setContent {
            MaterialTheme {
                HistoryScreen(database, rememberLazyListState(), ListEdgeAnchor.START, {},
                    Color.White, Color.White, Color.Black, Color.Gray, {}, {}, {})
            }
        }
        exerciseLongPressSelection(R.string.select_measurements)
        compose.runOnIdle { assertEquals(3, database.readSavedRecords().size) }
    }

    @Test fun alertSelectionStillWorksWithoutTheTitleSelectionIcon() {
        val rule = ThresholdAlertRule(ThresholdAlertMetric.UVA, true, ThresholdAlertDirection.ABOVE, 1f)
        repeat(3) { database.insertThresholdAlertLog(
            listOf(ThresholdAlertViolation(rule, 2.0)), timestamp = 1000L + it,
            sessionId = 10, sensorDeviceId = "TEST-A") }
        compose.setContent {
            MaterialTheme {
                ThresholdAlertLogScreen(database, rememberLazyListState(),
                    Color.White, Color.White, Color.Black, Color.Gray, {})
            }
        }
        exerciseLongPressSelection(R.string.select_alerts)
        compose.runOnIdle { assertEquals(3, database.readThresholdAlertLog().size) }
    }

    private fun exerciseLongPressSelection(selectionIconLabel: Int) {
        compose.onNodeWithContentDescription(text(selectionIconLabel)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.select_all)).assertDoesNotExist()
        val headerLabel = if (selectionIconLabel == R.string.select_alerts)
            R.string.alert_session_chart_open else R.string.session_chart_open
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)
            .and(!hasContentDescription(text(headerLabel))))
            .onFirst().performTouchInput { longClick() }
        compose.onNodeWithText(text(R.string.select_all)).assertIsDisplayed().performClick()
        compose.onNodeWithText(text(R.string.deselect_all)).assertIsDisplayed().performClick()
        compose.onNodeWithText(text(R.string.select_all)).assertIsDisplayed()
        compose.onNodeWithTag("list-filter-toggle").assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.navigate_back)).performClick()
        compose.onNodeWithText(text(R.string.select_all)).assertDoesNotExist()
    }

    @Test fun acquisitionSessionLongPressSelectsItsRecordsWithoutOpeningTheDetail() {
        renderAcquisitions()
        exerciseSessionHeaderSelection(R.string.session_chart_open)
        compose.runOnIdle { assertEquals(3, database.readSavedRecords().size) }
    }

    @Test fun alertSessionLongPressSelectsItsRecordsWithoutOpeningTheDetail() {
        renderAlerts()
        exerciseSessionHeaderSelection(R.string.alert_session_chart_open)
        compose.runOnIdle { assertEquals(3, database.readThresholdAlertLog().size) }
    }

    @Test fun acquisitionBackClosesFiltersBeforeSelectionOrNavigation() {
        val exits = AtomicInteger()
        renderAcquisitions { exits.incrementAndGet() }
        exerciseFilterBack(exits, R.string.session_chart_open)
        compose.runOnIdle { assertEquals(3, database.readSavedRecords().size) }
    }

    @Test fun alertBackClosesFiltersBeforeSelectionOrNavigation() {
        val exits = AtomicInteger()
        renderAlerts { exits.incrementAndGet() }
        exerciseFilterBack(exits, R.string.alert_session_chart_open)
        compose.runOnIdle { assertEquals(3, database.readThresholdAlertLog().size) }
    }

    @Test fun selectingAcquisitionNoteAndIdFiltersDoesNotCrashOrChangeRecords() {
        repeat(30) { database.saveAcquisition(SensorSample(), "Garden", automatic = true,
            sessionId = 10, sessionSequence = it + 1, sensorDeviceId = "TEST-A") }
        compose.setContent {
            MaterialTheme {
                HistoryScreen(database, rememberLazyListState(), ListEdgeAnchor.START, {},
                    Color.White, Color.White, Color.Black, Color.Gray, {}, {}, {})
            }
        }
        exerciseNoteAndIdChoices()
        compose.runOnIdle { assertEquals(30, database.readSavedRecords().size) }
    }

    @Test fun selectingAlertNoteAndIdFiltersDoesNotCrashOrChangeRecords() {
        val rule = ThresholdAlertRule(ThresholdAlertMetric.UVA, true, ThresholdAlertDirection.ABOVE, 1f)
        database.startAlertSession(10, "Garden", 1000L, "TEST-A")
        repeat(30) { database.insertThresholdAlertLog(listOf(ThresholdAlertViolation(rule, 2.0)),
            timestamp = 1000L + it, sessionId = 10, sensorDeviceId = "TEST-A") }
        compose.setContent {
            MaterialTheme {
                ThresholdAlertLogScreen(database, rememberLazyListState(),
                    Color.White, Color.White, Color.Black, Color.Gray, {})
            }
        }
        exerciseNoteAndIdChoices()
        compose.runOnIdle { assertEquals(30, database.readThresholdAlertLog().size) }
    }

    private fun exerciseNoteAndIdChoices() {
        compose.onNodeWithTag("list-filter-toggle").performClick()
        selectFilterChoice("filter-note", "Garden")
        selectFilterChoice("filter-id", "2")
        assertFilterCount(1, 30)
        selectFilterChoice("filter-session-id", "10")
        assertFilterCount(1, 30)
        compose.onNodeWithContentDescription(text(R.string.navigate_back)).performClick()
        compose.onNodeWithTag("filter-note").assertDoesNotExist()
    }

    private fun renderAcquisitions(onBack: () -> Unit = {}) {
        repeat(3) { database.saveAcquisition(SensorSample(), "Garden", automatic = true,
            sessionId = 10, sessionSequence = it + 1, sensorDeviceId = "TEST-A") }
        compose.setContent {
            MaterialTheme {
                HistoryScreen(database, rememberLazyListState(), ListEdgeAnchor.START, {},
                    Color.White, Color.White, Color.Black, Color.Gray, onBack, {}, {})
            }
        }
    }

    private fun renderAlerts(onBack: () -> Unit = {}) {
        val rule = ThresholdAlertRule(ThresholdAlertMetric.UVA, true, ThresholdAlertDirection.ABOVE, 1f)
        repeat(3) { database.insertThresholdAlertLog(listOf(ThresholdAlertViolation(rule, 2.0)),
            timestamp = 1000L + it, sessionId = 10, sensorDeviceId = "TEST-A") }
        compose.setContent {
            MaterialTheme {
                ThresholdAlertLogScreen(database, rememberLazyListState(),
                    Color.White, Color.White, Color.Black, Color.Gray, onBack)
            }
        }
    }

    private fun exerciseSessionHeaderSelection(headerLabel: Int) {
        compose.onNodeWithContentDescription(text(headerLabel))
            .performTouchInput { longClick() }
        compose.onNodeWithText(text(R.string.deselect_all)).assertIsDisplayed().performClick()
        compose.onNodeWithText(text(R.string.select_all)).assertIsDisplayed().performClick()
        compose.onNodeWithText(text(R.string.deselect_all)).assertIsDisplayed()
    }

    private fun exerciseFilterBack(exits: AtomicInteger, headerLabel: Int) {
        fun openFilters() = compose.onNodeWithTag("list-filter-toggle").performClick()
        fun titleBack() = compose.onNodeWithContentDescription(text(R.string.navigate_back)).performClick()
        fun filtersClosed() {
            compose.onNodeWithTag("filter-id").assertDoesNotExist()
            assertEquals(0, exits.get())
        }

        openFilters()
        titleBack()
        filtersClosed()
        openFilters()
        pressBack()
        filtersClosed()

        openFilters()
        selectFilterChoice("filter-id", "2")
        titleBack()
        filtersClosed()
        assertFilterCount(1, 3)
        openFilters()
        compose.onNodeWithTag("filter-id")
            .assertContentDescriptionContains("2", substring = true)
        compose.onNodeWithTag("filter-reset").performScrollTo().performClick()
        titleBack()
        filtersClosed()

        compose.onNodeWithContentDescription(text(headerLabel)).performTouchInput { longClick() }
        compose.onNodeWithText(text(R.string.deselect_all)).assertIsDisplayed()
        openFilters()
        pressBack()
        filtersClosed()
        compose.onNodeWithText(text(R.string.deselect_all)).assertIsDisplayed()
        pressBack()
        compose.onNodeWithText(text(R.string.deselect_all)).assertDoesNotExist()
        assertEquals(0, exits.get())
        titleBack()
        assertEquals(1, exits.get())
    }

    private fun exerciseFilterAndReset() {
        compose.onNodeWithTag("list-filter-toggle").performClick()
        selectFilterChoice("filter-id", "2")
        assertFilterCount(1, 3)
        compose.onNodeWithTag("list-filter-toggle").performClick()
        compose.onNodeWithTag("filter-id").assertDoesNotExist()
        assertFilterCount(1, 3)
        compose.onNodeWithTag("list-filter-toggle").performClick()
        compose.onNodeWithTag("filter-reset").performScrollTo().performClick()
        compose.onNodeWithTag("list-filter-toggle").performClick()
        compose.onNodeWithText(text(R.string.list_filter_count, 1, 3)).assertDoesNotExist()
    }

    private fun selectFilterChoice(fieldTag: String, key: String) {
        compose.onNodeWithTag(fieldTag).performScrollTo().performTouchInput { click(center) }
        compose.onNodeWithTag("filter-choice-option-$key").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(fieldTag)
            .assertContentDescriptionContains(key, substring = true)
    }

    private fun assertFilterCount(visible: Int, total: Int) {
        compose.onNodeWithTag("list-filter-count")
            .assertTextContains(visible.toString(), substring = true)
            .assertTextContains(total.toString(), substring = true)
    }
}
