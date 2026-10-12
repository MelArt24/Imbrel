package com.am24.imbrel.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.am24.imbrel.presentation.screens.CitySearchDialog
import com.am24.imbrel.presentation.screens.MainScreen

@Composable
fun WeatherApp(
    viewModel: MainViewModel,
    onLocationRequest: () -> Unit,
) {
    var showCitySearch by rememberSaveable { mutableStateOf(false) }

    WeatherNavigation {
        MainScreen(
            viewModel = viewModel,
            onLocationRequest = onLocationRequest,
            onSearchClick = {
                viewModel.openCitySearch()
                showCitySearch = true
            },
        )
    }

    if (showCitySearch) {
        CitySearchDialog(
            viewModel = viewModel,
            onDismiss = {
                viewModel.closeCitySearch()
                showCitySearch = false
            },
            onSearchStarted = { showCitySearch = false },
        )
    }
}
