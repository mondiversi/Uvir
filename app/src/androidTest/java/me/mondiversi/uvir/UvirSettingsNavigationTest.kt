package me.mondiversi.uvir

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UvirSettingsNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settingsListShowsOnlyPageRowsAndOpensTheSelectedContent() {
        compose.setContent {
            var selected by remember { mutableStateOf<UvirSettingsPage?>(null) }
            MaterialTheme {
                CompositionLocalProvider(
                    LocalUvirSettingsNavigation provides UvirSettingsNavigation(
                        selectedPage = selected,
                        onOpenPage = { selected = it }
                    )
                ) {
                    Column {
                        TestSettingsPage(
                            page = UvirSettingsPage.SAMPLING,
                            title = "Sampling",
                            content = "Sampling content"
                        )
                        TestSettingsPage(
                            page = UvirSettingsPage.LANGUAGE_AND_FORMATS,
                            title = "Language and formats",
                            content = "Language and formats content"
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("Sampling").assertIsDisplayed()
        compose.onNodeWithText("Language and formats").assertIsDisplayed()
        compose.onNodeWithText("Sampling content").assertDoesNotExist()

        compose.onNodeWithText("Sampling").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Sampling content").assertIsDisplayed()
        compose.onNodeWithText("Language and formats").assertDoesNotExist()
        compose.onNodeWithText("Language and formats content").assertDoesNotExist()
    }

    @Test
    fun ordinarySettingsSectionsKeepTheirExpandableBehaviorOutsideTheSettingsNavigator() {
        compose.setContent {
            var expanded by remember { mutableStateOf(false) }
            MaterialTheme {
                SettingsSection(
                    title = "Expandable",
                    containerColor = Color.DarkGray,
                    titleColor = Color.White,
                    dividerColor = Color.Gray,
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    androidx.compose.material3.Text("Expandable content")
                }
            }
        }

        compose.onNodeWithText("Expandable content").assertDoesNotExist()
        compose.onNodeWithText("Expandable").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Expandable content").assertIsDisplayed()
    }

    @androidx.compose.runtime.Composable
    private fun TestSettingsPage(
        page: UvirSettingsPage,
        title: String,
        content: String
    ) {
        SettingsSection(
            title = title,
            containerColor = Color.DarkGray,
            titleColor = Color.White,
            dividerColor = Color.Gray,
            settingsPage = page
        ) {
            androidx.compose.material3.Text(content)
        }
    }
}
