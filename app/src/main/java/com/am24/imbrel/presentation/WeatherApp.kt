package com.am24.imbrel.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import com.am24.imbrel.presentation.screens.CitySearchDialog
import com.am24.imbrel.presentation.screens.MainScreen
import com.am24.imbrel.presentation.screens.PlacesScreen

@Composable
fun WeatherApp(
    viewModel: MainViewModel,
    onLocationRequest: () -> Unit,
    placesViewModel: PlacesViewModel,
) {
    val navController = rememberNavController()
    var showCitySearch by rememberSaveable { mutableStateOf(false) }
    val navigateHome = {
        navController.navigate("main") {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val openSearch = {
        viewModel.openCitySearch()
        showCitySearch = true
    }

    WeatherNavigation(
        navController = navController,
        placesContent = {
            PlacesScreen(placesViewModel, onSearchClick = {
                navigateHome()
                openSearch()
            }, onCachedSelected = { entry ->
                viewModel.selectCachedForecast(entry)
                navigateHome()
            }) { city ->
                viewModel.selectCity(city)
                navigateHome()
            }
        },
    ) {
        MainScreen(
            viewModel = viewModel,
            onLocationRequest = onLocationRequest,
            onSearchClick = openSearch,
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
