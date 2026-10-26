package com.am24.imbrel.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.am24.imbrel.R
import com.am24.imbrel.ads.AdPrivacySettings
import com.am24.imbrel.ads.AdsUiState
import com.am24.imbrel.domain.model.SavedCity
import com.am24.imbrel.domain.model.StartupMode
import com.am24.imbrel.presentation.SettingsUiState
import com.am24.imbrel.presentation.SettingsViewModel
import com.am24.imbrel.presentation.theme.ScreenGradientBottomColor
import com.am24.imbrel.presentation.theme.ScreenGradientTopColor
import com.am24.imbrel.presentation.theme.SettingsAccentColor
import com.am24.imbrel.presentation.theme.SettingsDialogColor
import com.am24.imbrel.presentation.theme.SettingsSecondaryTextColor

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel? = null,
    adsState: AdsUiState = AdsUiState(),
    onPrivacyOptions: () -> Unit = {},
) {
    val state = viewModel?.uiState?.collectAsStateWithLifecycle()?.value ?: SettingsUiState()
    SettingsScreenContent(
        state,
        SettingsActions(
            { viewModel?.setStartupMode(it) },
            { viewModel?.setDefaultCity(it) },
            { viewModel?.resetDefaultCity() },
            { viewModel?.clearCache() },
            { viewModel?.retry() },
        ),
        adsState,
        onPrivacyOptions,
    )
}

class SettingsActions(
    val onModeSelected: (StartupMode) -> Unit = {},
    val onCitySelected: (SavedCity) -> Unit = {},
    val onResetCity: () -> Unit = {},
    val onClearCache: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    actions: SettingsActions = SettingsActions(),
    adsState: AdsUiState = AdsUiState(),
    onPrivacyOptions: () -> Unit = {},
) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = Color.White,
                onPrimary = ScreenGradientTopColor,
                secondary = SettingsAccentColor,
                surface = SettingsDialogColor,
                onSurface = Color.White,
                onSurfaceVariant = SettingsSecondaryTextColor,
            ),
    ) { SettingsContent(state, actions, adsState, onPrivacyOptions) }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    adsState: AdsUiState,
    onPrivacyOptions: () -> Unit,
) {
    var chooseCity by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val enabled = !state.isLoading && !state.hasReadError && !state.isSaving && !state.isClearingCache
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ScreenGradientTopColor, ScreenGradientBottomColor)))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("settings_list"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            item { SettingsHeader() }
            if (state.isLoading) item { CircularProgressIndicator() }
            if (state.hasReadError || state.hasOperationError) {
                item {
                    Column {
                        Text(stringResource(R.string.settings_error), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = actions.onRetry) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
            item { StartupSettingsCard(state, enabled, actions.onModeSelected) }
            item { DefaultCityCard(state, enabled, { chooseCity = true }, actions.onResetCity) }
            item { CacheSettingsCard(state, enabled) { confirmClear = true } }
            if (adsState.privacyOptionsRequired) {
                item {
                    AdPrivacySettings(adsState, onPrivacyOptions)
                }
            }
        }
    }
    if (chooseCity) {
        DefaultCityDialog(state, { chooseCity = false }) {
            actions.onCitySelected(it)
            chooseCity = false
        }
    }
    if (confirmClear) {
        ClearCacheDialog(enabled, { confirmClear = false }) {
            confirmClear = false
            actions.onClearCache()
        }
    }
}

@Composable
private fun StartupSettingsCard(
    state: SettingsUiState,
    enabled: Boolean,
    onModeSelected: (StartupMode) -> Unit,
) {
    SettingsSection(R.string.startup_mode, R.drawable.ic_home) {
        StartupMode.entries.forEach { mode ->
            StartupModeRow(
                mode,
                state.settings.startupMode == mode,
                enabled && (mode != StartupMode.DefaultCity || state.settings.defaultCity != null),
                onModeSelected,
            )
        }
        if (state.settings.defaultCity == null) SettingsSupportingText(stringResource(R.string.settings_default_city_hint))
        if (state.isSaving) CircularProgressIndicator()
    }
}

@Composable
private fun DefaultCityCard(
    state: SettingsUiState,
    enabled: Boolean,
    onChoose: () -> Unit,
    onReset: () -> Unit,
) {
    SettingsSection(R.string.default_city, R.drawable.ic_places) {
        val city = state.settings.defaultCity
        Text(city?.displayName ?: stringResource(R.string.default_city_none), style = MaterialTheme.typography.titleLarge)
        if (city != null && city.normalizedQuery != city.displayName) SettingsSupportingText(city.normalizedQuery)
        SettingsAction(R.string.choose_default_city, enabled, onChoose)
        if (city != null) {
            TextButton(onClick = onReset, enabled = enabled, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.reset_default_city))
            }
        }
    }
}

@Composable
private fun CacheSettingsCard(
    state: SettingsUiState,
    enabled: Boolean,
    onClear: () -> Unit,
) {
    SettingsSection(R.string.settings_cache, R.drawable.ic_history) {
        Text(stringResource(R.string.settings_cache_count, state.cacheCount), style = MaterialTheme.typography.titleLarge)
        SettingsSupportingText(stringResource(R.string.settings_cache_description))
        SettingsAction(
            R.string.clear_weather_cache,
            enabled && state.cacheCount > 0,
            onClear,
            Modifier.testTag("clear_weather_cache"),
        )
        if (state.isClearingCache) CircularProgressIndicator()
    }
}

@Composable
private fun ClearCacheDialog(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_weather_cache)) },
        text = { Text(stringResource(R.string.clear_cache_confirmation)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = enabled, modifier = Modifier.testTag("confirm_clear_weather_cache")) {
                Text(stringResource(R.string.clear_weather_cache))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun StartupModeRow(
    mode: StartupMode,
    selected: Boolean,
    enabled: Boolean,
    onSelect: (StartupMode) -> Unit,
) {
    val label =
        when (mode) {
            StartupMode.LastSelected -> R.string.startup_last_selected
            StartupMode.DefaultCity -> R.string.startup_default_city
            StartupMode.CurrentLocation -> R.string.startup_current_location
        }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = if (selected) 0.14f else 0f))
            .testTag("startup_mode_${mode.name}")
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(mode) })
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(stringResource(label), color = Color.White.copy(alpha = if (enabled) 1f else 0.45f))
    }
}

@Composable
private fun DefaultCityDialog(
    state: SettingsUiState,
    onDismiss: () -> Unit,
    onSelect: (SavedCity) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_default_city)) },
        text = {
            if (state.cities.isEmpty()) {
                Text(stringResource(R.string.default_city_candidates_empty))
            } else {
                LazyColumn {
                    items(state.cities, key = { it.id }) { city ->
                        TextButton(onClick = { onSelect(city) }, enabled = !state.isSaving && !state.isClearingCache) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(city.displayName)
                                if (city.normalizedQuery !=
                                    city.displayName
                                ) {
                                    Text(city.normalizedQuery, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
