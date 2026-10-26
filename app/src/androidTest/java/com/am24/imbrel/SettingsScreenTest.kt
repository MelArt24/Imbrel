package com.am24.imbrel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.am24.imbrel.presentation.SettingsUiState
import com.am24.imbrel.presentation.screens.SettingsActions
import com.am24.imbrel.presentation.screens.SettingsScreenContent
import com.am24.imbrel.presentation.theme.WeatherForecastAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun defaultCityMode_isDisabledWithoutCity() {
        composeRule.setContent {
            WeatherForecastAppTheme { SettingsScreenContent(SettingsUiState(isLoading = false)) }
        }
        composeRule.onNodeWithTag("startup_mode_DefaultCity").assertIsNotEnabled()
    }

    @Test
    fun clearCache_requiresConfirmationAndCancelPreservesCache() {
        var clears = 0
        composeRule.setContent {
            WeatherForecastAppTheme {
                SettingsScreenContent(
                    SettingsUiState(isLoading = false, cacheCount = 2),
                    SettingsActions(onClearCache = { clears++ }),
                )
            }
        }
        composeRule.onNodeWithTag("clear_weather_cache").performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(0, clears)
        composeRule.onNodeWithTag("clear_weather_cache").performScrollTo().performClick()
        composeRule
            .onNodeWithText(
                "Delete all cached forecasts? Favorites, search history and settings will be kept. Offline forecasts will be unavailable until weather is loaded again.",
            ).assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_clear_weather_cache").performClick()
        assertEquals(1, clears)
    }
}
