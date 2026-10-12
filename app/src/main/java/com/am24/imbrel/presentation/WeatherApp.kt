package com.am24.imbrel.presentation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.am24.imbrel.presentation.screens.CitySearchDialog
import com.am24.imbrel.presentation.screens.MainScreen

@Composable
fun WeatherApp(
    viewModel: MainViewModel,
    onLocationRequest: () -> Unit,
) {
    val navController = rememberNavController()
    var showCitySearch by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "main",
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            composable("main") {
                MainScreen(
                    viewModel = viewModel,
                    onLocationRequest = onLocationRequest,
                    onSearchClick = {
                        viewModel.openCitySearch()
                        showCitySearch = true
                    },
                )
            }
        }
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
