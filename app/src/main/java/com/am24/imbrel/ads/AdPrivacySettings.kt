package com.am24.imbrel.ads

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.am24.imbrel.R
import com.am24.imbrel.presentation.screens.SettingsAction
import com.am24.imbrel.presentation.screens.SettingsSection

@Composable
fun AdPrivacySettings(
    state: AdsUiState,
    onPrivacyOptions: () -> Unit,
) {
    SettingsSection(R.string.ad_privacy_title, R.drawable.ic_settings) {
        SettingsAction(R.string.ad_privacy_options, !state.formInProgress, onPrivacyOptions)
        if (state.formInProgress) CircularProgressIndicator()
        if (state.hasPrivacyError) Text(stringResource(R.string.ad_privacy_error), color = MaterialTheme.colorScheme.error)
    }
}
