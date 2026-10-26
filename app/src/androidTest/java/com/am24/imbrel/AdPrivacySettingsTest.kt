package com.am24.imbrel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.am24.imbrel.ads.AdsUiState
import com.am24.imbrel.presentation.SettingsUiState
import com.am24.imbrel.presentation.screens.SettingsScreenContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AdPrivacySettingsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun privacyEntry_isAbsentUnlessRequiredByUmp() {
        composeRule.setContent { SettingsScreenContent(SettingsUiState(isLoading = false)) }
        composeRule.onNodeWithText("Manage privacy choices").assertDoesNotExist()
    }

    @Test
    fun requiredPrivacyEntry_invokesOfficialFormCallback() {
        var shown = 0
        composeRule.setContent {
            SettingsScreenContent(
                SettingsUiState(isLoading = false),
                adsState = AdsUiState(privacyOptionsRequired = true),
                onPrivacyOptions = { shown++ },
            )
        }
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Manage privacy choices"))
        composeRule
            .onNodeWithText("Manage privacy choices")
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, shown)
    }

    @Test
    fun privacyFormInProgress_disablesDuplicateClicks() {
        composeRule.setContent {
            SettingsScreenContent(
                SettingsUiState(isLoading = false),
                adsState = AdsUiState(privacyOptionsRequired = true, formInProgress = true),
            )
        }
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Manage privacy choices"))
        composeRule.onNodeWithText("Manage privacy choices").assertIsNotEnabled()
    }
}
