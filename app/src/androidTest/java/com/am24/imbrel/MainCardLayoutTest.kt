package com.am24.imbrel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.am24.imbrel.domain.model.CacheStatus
import com.am24.imbrel.domain.model.CachedForecast
import com.am24.imbrel.domain.model.CurrentWeather
import com.am24.imbrel.domain.model.WeatherForecast
import com.am24.imbrel.presentation.model.WeatherModel
import com.am24.imbrel.presentation.screens.MainCard
import com.am24.imbrel.presentation.theme.WeatherForecastAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainCardLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun bothButtonsRemainVisibleAndClickableWithCacheNotice() {
        var searches = 0
        var syncs = 0
        val weather = WeatherModel("Kyiv", "Now", "Clear", "20\u00B0C", "10", "22", "1", emptyList())
        val cache =
            CachedForecast(
                "city:kyiv",
                WeatherForecast(
                    null,
                    CurrentWeather("Clear", 20.0, 1),
                    emptyList(),
                    emptyList(),
                ),
                0,
                CacheStatus.Offline,
            )
        composeRule.setContent {
            WeatherForecastAppTheme {
                Box(Modifier.width(320.dp)) {
                    MainCard(weather, { syncs++ }, { searches++ }, cache)
                }
            }
        }
        val search = composeRule.onNodeWithContentDescription("Search your city").assertIsDisplayed()
        val sync = composeRule.onNodeWithContentDescription("Temperature synchronization").assertIsDisplayed()
        search.performClick()
        sync.performClick()
        assertEquals(1, searches)
        assertEquals(1, syncs)
        composeRule.onNodeWithText("Now").assertDoesNotExist()
        val searchBounds = search.fetchSemanticsNode().boundsInRoot
        val syncBounds = sync.fetchSemanticsNode().boundsInRoot
        val cityBounds =
            composeRule
                .onNodeWithText("Kyiv")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val noticeBounds =
            composeRule
                .onNodeWithText("Available offline", substring = true)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(searchBounds.right <= cityBounds.left)
        assertTrue(cityBounds.right <= syncBounds.left)
        assertTrue(noticeBounds.top >= syncBounds.bottom)
    }
}
