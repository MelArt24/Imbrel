package com.am24.imbrel

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.am24.imbrel.ads.AdsUiState
import com.am24.imbrel.ads.BannerPlacement
import com.am24.imbrel.presentation.WeatherNavigation
import com.am24.imbrel.presentation.theme.WeatherForecastAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdBannerPlacementTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val ads = mutableStateOf(AdsUiState())
    private var bottomInset = 0

    @Test
    fun banner_isBelowNavigationSeparatedFromControlsAndAboveSystemInsets() {
        ads.value = AdsUiState(canRequestAds = true, sdkReady = true)
        showNavigation()
        val navigation = composeRule.onNodeWithTag("weather_navigation_bar").fetchSemanticsNode().boundsInRoot
        val separator =
            composeRule
                .onNodeWithTag("ad_separator")
                .assertHasNoClickAction()
                .fetchSemanticsNode()
                .boundsInRoot
        val banner =
            composeRule
                .onNodeWithTag("fake_banner")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val home = composeRule.onNodeWithTag("home_content").fetchSemanticsNode().boundsInRoot
        val root = composeRule.onNodeWithTag("app_root").fetchSemanticsNode().boundsInRoot
        assertTrue(home.bottom <= navigation.top)
        assertTrue(navigation.bottom <= separator.top)
        assertTrue(separator.bottom < banner.top)
        assertEquals(bottomInset.toFloat(), root.bottom - banner.bottom, 1f)
    }

    @Test
    fun homeReservation_isStableWhileOtherTabsHaveNoAdvertisingGap() {
        showNavigation()
        val originalBounds = composeRule.onNodeWithTag("navigation_main").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("fake_banner").assertDoesNotExist()
        composeRule.runOnIdle { ads.value = AdsUiState(canRequestAds = true, sdkReady = true) }
        composeRule.onNodeWithTag("fake_banner").assertIsDisplayed()
        assertEquals(originalBounds, composeRule.onNodeWithTag("navigation_main").fetchSemanticsNode().boundsInRoot)
        for (route in listOf("places", "settings")) {
            composeRule.onNodeWithTag("navigation_$route").performClick()
            composeRule.onNodeWithTag("fake_banner").assertDoesNotExist()
            val compactBounds = composeRule.onNodeWithTag("navigation_main").fetchSemanticsNode().boundsInRoot
            assertTrue(compactBounds.top > originalBounds.top)
            val root = composeRule.onNodeWithTag("app_root").fetchSemanticsNode().boundsInRoot
            val navigation = composeRule.onNodeWithTag("weather_navigation_bar").fetchSemanticsNode().boundsInRoot
            val margin = with(composeRule.density) { 8.dp.toPx() }
            assertEquals(bottomInset.toFloat() + margin, root.bottom - navigation.bottom, 1f)
        }
        composeRule.onNodeWithTag("navigation_main").performClick()
        composeRule.onNodeWithTag("fake_banner").assertIsDisplayed()
        composeRule.runOnIdle { ads.value = ads.value.copy(formInProgress = true) }
        composeRule.onNodeWithTag("fake_banner").assertDoesNotExist()
        assertEquals(originalBounds, composeRule.onNodeWithTag("navigation_main").fetchSemanticsNode().boundsInRoot)
    }

    private fun showNavigation() {
        composeRule.setContent {
            bottomInset = WindowInsets.navigationBars.getBottom(LocalDensity.current)
            WeatherForecastAppTheme {
                Box(Modifier.fillMaxSize().testTag("app_root")) {
                    WeatherNavigation(
                        placesContent = { Text("Places") },
                        settingsContent = { Text("Settings") },
                        bannerContent = { isHome ->
                            BannerPlacement(50.dp, isHome = isHome) {
                                if (ads.value.showBanner(isHome)) Box(Modifier.fillMaxWidth().height(50.dp).testTag("fake_banner"))
                            }
                        },
                    ) { Box(Modifier.fillMaxSize().testTag("home_content")) }
                }
            }
        }
    }
}
