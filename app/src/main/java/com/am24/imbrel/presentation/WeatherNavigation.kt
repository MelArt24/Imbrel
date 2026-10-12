package com.am24.imbrel.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.am24.imbrel.R
import com.am24.imbrel.presentation.screens.PlacesScreen
import com.am24.imbrel.presentation.screens.SettingsScreen
import com.am24.imbrel.presentation.theme.ScreenGradientBottomColor

private enum class WeatherDestination(
    val route: String,
    val label: Int,
    val icon: Int,
) {
    Home("main", R.string.navigation_home, R.drawable.ic_home),
    Places("places", R.string.navigation_places, R.drawable.ic_places),
    Settings("settings", R.string.navigation_settings, R.drawable.ic_settings),
}

@Composable
fun WeatherNavigation(
    navController: NavHostController = rememberNavController(),
    homeContent: @Composable () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { FloatingWeatherNavigationBar(navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = WeatherDestination.Home.route,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            composable(WeatherDestination.Home.route) { homeContent() }
            composable(WeatherDestination.Places.route) { PlacesScreen() }
            composable(WeatherDestination.Settings.route) { SettingsScreen() }
        }
    }
}

@Composable
private fun FloatingWeatherNavigationBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val background =
        if (backStackEntry?.destination?.route == WeatherDestination.Home.route) {
            ScreenGradientBottomColor
        } else {
            MaterialTheme.colorScheme.surface
        }
    Surface(color = background) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 8.dp,
                color = colorResource(R.color.navigation_container),
            ) {
                WeatherNavigationBar(navController)
            }
        }
    }
}

@Composable
private fun WeatherNavigationBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()

    NavigationBar(
        containerColor = colorResource(R.color.navigation_container),
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        WeatherDestination.entries.forEach { destination ->
            NavigationBarItem(
                modifier = Modifier.testTag("navigation_${destination.route}"),
                selected = backStackEntry?.destination?.hierarchy?.any { it.route == destination.route } == true,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                label = { Text(stringResource(destination.label)) },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = colorResource(R.color.navigation_selected),
                        selectedTextColor = colorResource(R.color.navigation_selected),
                        indicatorColor = colorResource(R.color.navigation_indicator),
                        unselectedIconColor = colorResource(R.color.navigation_unselected),
                        unselectedTextColor = colorResource(R.color.navigation_unselected),
                    ),
            )
        }
    }
}
