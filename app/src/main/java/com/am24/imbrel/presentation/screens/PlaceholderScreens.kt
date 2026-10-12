package com.am24.imbrel.presentation.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.am24.imbrel.R

@Composable
fun PlacesScreen() {
    PlaceholderScreen(R.string.navigation_places, R.string.places_placeholder)
}

@Composable
fun SettingsScreen() {
    PlaceholderScreen(R.string.navigation_settings, R.string.settings_placeholder)
}

@Composable
private fun PlaceholderScreen(
    @StringRes title: Int,
    @StringRes message: Int,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
