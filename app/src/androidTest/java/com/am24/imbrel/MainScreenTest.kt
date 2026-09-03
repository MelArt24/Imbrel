package com.am24.imbrel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.am24.imbrel.domain.error.DomainError
import com.am24.imbrel.domain.error.NetworkErrorReason
import com.am24.imbrel.presentation.WeatherUiError
import com.am24.imbrel.presentation.WeatherUiState
import com.am24.imbrel.presentation.WeatherUiStatus
import com.am24.imbrel.presentation.model.WeatherModel
import com.am24.imbrel.presentation.screens.MainScreenContent
import com.am24.imbrel.presentation.theme.WeatherForecastAppTheme
import org.junit.Rule
import org.junit.Test

class MainScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun banner_isVisibleOffline() {
        showScreen(isOnline = false)

        composeRule.onNodeWithText("No internet connection").assertIsDisplayed()
    }

    @Test
    fun banner_isHiddenOnline() {
        showScreen(isOnline = true)

        composeRule.onNodeWithText("No internet connection").assertDoesNotExist()
    }

    @Test
    fun weatherContentAndBanner_areVisibleTogether() {
        showScreen(
            isOnline = false,
            uiState =
                WeatherUiState(
                    status = WeatherUiStatus.Success,
                    currentWeather = weather("Kyiv"),
                ),
        )

        composeRule.onNodeWithText("No internet connection").assertIsDisplayed()
        composeRule.onNodeWithText("Kyiv").assertIsDisplayed()
    }

    @Test
    fun banner_isVisibleOnInitialErrorScreen() {
        showScreen(
            isOnline = false,
            uiState =
                WeatherUiState(
                    status = WeatherUiStatus.Error,
                    error =
                        WeatherUiError.Weather(
                            DomainError.Network(NetworkErrorReason.Offline),
                        ),
                ),
        )

        composeRule.onNodeWithText("No internet connection").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "No internet connection and no cached weather is available",
            ).assertIsDisplayed()
    }

    @Test
    fun loadingOverlay_isVisibleOverExistingWeatherDuringRefresh() {
        showScreen(
            isOnline = true,
            uiState =
                WeatherUiState(
                    status = WeatherUiStatus.Success,
                    currentWeather = weather("Kyiv"),
                    isRefreshing = true,
                ),
        )

        composeRule.onNodeWithText("Kyiv").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Loading weather").assertIsDisplayed()
    }

    @Test
    fun daysTab_showsDailyEmptyState() {
        showScreen(isOnline = true)

        composeRule.onNodeWithText("DAYS").performClick()
        composeRule.onNodeWithText("No daily forecast available").assertIsDisplayed()
    }

    private fun showScreen(
        isOnline: Boolean,
        uiState: WeatherUiState = WeatherUiState(),
    ) {
        composeRule.setContent {
            WeatherForecastAppTheme {
                MainScreenContent(
                    uiState = uiState,
                    isOnline = isOnline,
                    onRetry = {},
                    onSearchClick = {},
                    onLocationClick = {},
                    onDayClick = {},
                )
            }
        }
    }

    private fun weather(city: String) =
        WeatherModel(
            city = city,
            time = "Now",
            condition = "Clear",
            currentTemperature = "20",
            minimumTemperature = "10",
            maximumTemperature = "22",
            imageURL = "1",
            hourlyWeather = emptyList(),
        )
}
