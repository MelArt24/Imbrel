package com.am24.imbrel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.am24.imbrel.domain.model.SavedCity
import com.am24.imbrel.presentation.PlacesUiState
import com.am24.imbrel.presentation.screens.PlacesScreenContent
import com.am24.imbrel.presentation.theme.WeatherForecastAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlacesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val city = SavedCity(1, "Kyiv", "Kyiv", null, 100, false)

    @Test
    fun emptyStates_explainHowToAddCities() {
        showScreen(PlacesUiState(isLoading = false))
        composeRule.onNodeWithText("No favorite cities yet. Tap a star next to a city to save it.").assertIsDisplayed()
        composeRule.onNodeWithText("No recent searches. Search for a city on Home to add it here.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun star_setsFavoriteWithoutSelectingCity() {
        var favoriteChange: Pair<SavedCity, Boolean>? = null
        var selected: SavedCity? = null
        showScreen(
            PlacesUiState(recentSearches = listOf(city), isLoading = false),
            onSelect = { selected = it },
            onFavorite = { city, favorite -> favoriteChange = city to favorite },
        )
        composeRule.onNodeWithContentDescription("Add Kyiv to favorites").performClick()
        assertEquals(city to true, favoriteChange)
        assertNull(selected)
        composeRule.onNodeWithText("Kyiv").performClick()
        assertEquals(city, selected)
    }

    @Test
    fun favoriteStar_allowsRemovingFavorite() {
        var favoriteChange: Boolean? = null
        showScreen(
            PlacesUiState(favorites = listOf(city.copy(isFavorite = true)), isLoading = false),
            onFavorite = { _, favorite -> favoriteChange = favorite },
        )
        composeRule.onNodeWithContentDescription("Remove Kyiv from favorites").performClick()
        assertEquals(false, favoriteChange)
    }

    @Test
    fun clearingHistory_requiresConfirmationAndCanBeCancelled() {
        var cleared = false
        showScreen(PlacesUiState(recentSearches = listOf(city), isLoading = false), onClear = { cleared = true })
        composeRule.onNodeWithContentDescription("Clear history").performClick()
        composeRule.onNodeWithText("Clear search history?").assertIsDisplayed()
        assertFalse(cleared)
        composeRule.onNodeWithText("Cancel").performClick()
        assertFalse(cleared)
        composeRule.onNodeWithContentDescription("Clear history").performClick()
        composeRule.onNodeWithText("Clear history").performClick()
        assertTrue(cleared)
    }

    private fun showScreen(
        state: PlacesUiState,
        onSelect: (SavedCity) -> Unit = {},
        onFavorite: (SavedCity, Boolean) -> Unit = { _, _ -> },
        onClear: () -> Unit = {},
    ) {
        composeRule.setContent {
            WeatherForecastAppTheme { PlacesScreenContent(state, onSelect, onFavorite, onClear) }
        }
    }
}
