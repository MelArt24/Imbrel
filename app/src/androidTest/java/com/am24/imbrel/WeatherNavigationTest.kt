package com.am24.imbrel

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.am24.imbrel.presentation.PlacesUiState
import com.am24.imbrel.presentation.WeatherNavigation
import com.am24.imbrel.presentation.WeatherUiState
import com.am24.imbrel.presentation.screens.MainScreenContent
import com.am24.imbrel.presentation.screens.PlacesScreenContent
import com.am24.imbrel.presentation.theme.WeatherForecastAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class WeatherNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController

    @Test
    fun tabs_selectMatchingRoutesAndBackReturnsHomeWithoutDuplicates() {
        showScreen()
        assertRoute("main")
        composeRule.onNodeWithTag("navigation_main").assertIsSelected()
        repeat(3) {
            composeRule.onNodeWithTag("navigation_places").performClick()
            assertRoute("places")
            composeRule.onNodeWithTag("navigation_places").assertIsSelected()
            composeRule.onNodeWithTag("navigation_main").assertIsNotSelected()
            composeRule.onNodeWithTag("navigation_settings").performClick()
        }
        composeRule.onNodeWithTag("navigation_settings").performClick()
        assertRoute("settings")
        composeRule.onNodeWithTag("navigation_settings").assertIsSelected()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        assertRoute("main")
        composeRule.onNodeWithTag("navigation_main").assertIsSelected()
        composeRule.runOnIdle { assertFalse(navController.popBackStack()) }
    }

    @Test
    fun home_preservesForecastTabAfterNavigationAndStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { NavigationContent() }
        composeRule.onNodeWithText("DAYS").performClick()
        composeRule.onNodeWithText("No daily forecast available").assertIsDisplayed()
        composeRule.onNodeWithTag("navigation_places").performClick()
        restorationTester.emulateSavedInstanceStateRestore()
        assertRoute("places")
        composeRule.onNodeWithTag("navigation_places").assertIsSelected()
        composeRule.onNodeWithTag("navigation_main").performClick()
        assertRoute("main")
        composeRule.onNodeWithText("DAYS").assertIsSelected()
        composeRule.onNodeWithText("No daily forecast available").assertIsDisplayed()
        composeRule.onNodeWithText("Your city").assertIsDisplayed()
    }

    private fun showScreen() {
        composeRule.setContent { NavigationContent() }
    }

    @androidx.compose.runtime.Composable
    private fun NavigationContent() {
        navController = rememberNavController()
        WeatherForecastAppTheme {
            WeatherNavigation(
                navController,
                placesContent = { PlacesScreenContent(PlacesUiState(isLoading = false), {}, { _, _ -> }, {}) },
            ) {
                MainScreenContent(
                    uiState = WeatherUiState(),
                    isOnline = true,
                    onRetry = {},
                    onSearchClick = {},
                    onLocationClick = {},
                )
            }
        }
    }

    private fun assertRoute(route: String) {
        composeRule.runOnIdle { assertEquals(route, navController.currentDestination?.route) }
    }
}
