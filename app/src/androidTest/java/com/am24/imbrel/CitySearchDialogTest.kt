package com.am24.imbrel

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import com.am24.imbrel.data.cache.TimeProvider
import com.am24.imbrel.data.local.WeatherDatabase
import com.am24.imbrel.data.repository.RoomPlacesRepository
import com.am24.imbrel.domain.error.ApiErrorReason
import com.am24.imbrel.domain.error.DomainError
import com.am24.imbrel.domain.error.DomainFailureException
import com.am24.imbrel.domain.model.CurrentWeather
import com.am24.imbrel.domain.model.DailyWeather
import com.am24.imbrel.domain.model.GeocodedLocation
import com.am24.imbrel.domain.model.SelectedPlace
import com.am24.imbrel.domain.model.UserLocation
import com.am24.imbrel.domain.model.WeatherForecast
import com.am24.imbrel.domain.network.NetworkMonitor
import com.am24.imbrel.domain.repository.GeocodingRepository
import com.am24.imbrel.domain.repository.LocationRepository
import com.am24.imbrel.domain.repository.SelectedPlaceRepository
import com.am24.imbrel.domain.repository.WeatherRepository
import com.am24.imbrel.domain.usecase.ClearSearchHistoryUseCase
import com.am24.imbrel.domain.usecase.GetCurrentLocationUseCase
import com.am24.imbrel.domain.usecase.GetCurrentWeatherUseCase
import com.am24.imbrel.domain.usecase.ObserveFavoriteCitiesUseCase
import com.am24.imbrel.domain.usecase.ObserveSearchHistoryUseCase
import com.am24.imbrel.domain.usecase.RecordCitySearchUseCase
import com.am24.imbrel.domain.usecase.SearchCityWeatherUseCase
import com.am24.imbrel.domain.usecase.SetCityFavoriteUseCase
import com.am24.imbrel.presentation.MainViewModel
import com.am24.imbrel.presentation.PlacesViewModel
import com.am24.imbrel.presentation.WeatherApp
import com.am24.imbrel.presentation.mapper.WeatherPresentationMapper
import com.am24.imbrel.presentation.theme.WeatherForecastAppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Rule
import org.junit.Test

class CitySearchDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val response = CompletableDeferred<WeatherForecast>()
    private val selected = MemorySelectedPlaceRepository()
    private lateinit var viewModel: MainViewModel
    private lateinit var database: WeatherDatabase

    @After
    fun closeDatabase() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun blankQuery_showsValidationAndDoesNotLoad() {
        showApp()
        openSearch()
        composeRule.onNodeWithText("OK").performClick()

        composeRule.onNodeWithText("Enter a city name").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Loading weather").assertDoesNotExist()
    }

    @Test
    fun imeSearch_closesDialogAndShowsProgressOnMain() {
        showApp()
        openSearch()
        composeRule.onNodeWithText("Enter your city/town").performTextInput("Kyiv")
        composeRule.onNodeWithText("Enter your city/town").performImeAction()
        composeRule.onNodeWithText("OK").assertDoesNotExist()
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
        composeRule
            .onAllNodes(
                androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
                    androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
                ),
            ).assertCountEquals(1)
        response.complete(forecast())
        composeRule.waitUntil(5_000) { viewModel.uiState.value.hasWeather }

        composeRule.onNodeWithText("Kyiv").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
        openSearch()
        composeRule.onNodeWithText("Enter your city/town").assertIsDisplayed()
    }

    @Test
    fun successfulSearch_appearsInPlacesAndFavoriteOpensHome() {
        showApp()
        openSearch()
        composeRule.onNodeWithText("Enter your city/town").performTextInput("Kyiv")
        composeRule.onNodeWithText("OK").performClick()
        response.complete(forecast())
        composeRule.waitUntil(5_000) { viewModel.uiState.value.hasWeather }
        composeRule.onNodeWithTag("navigation_places").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Kyiv").fetchSemanticsNodes().size == 1 }
        composeRule.onNodeWithContentDescription("Add Kyiv to favorites").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("Remove Kyiv from favorites").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Kyiv")[0].performClick()
        composeRule.onNodeWithTag("navigation_main").assertIsSelected()
        composeRule.onNodeWithText("Kyiv").assertIsDisplayed()
        composeRule.onNodeWithTag("navigation_places").performClick()
        composeRule.onAllNodesWithText("Kyiv").assertCountEquals(1)
    }

    @Test
    fun error_isShownOnMainAndAllowsNewSearch() {
        showApp()
        openSearch()
        composeRule.onNodeWithText("Enter your city/town").performTextInput("Unknown")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
        response.completeExceptionally(DomainFailureException(DomainError.Api(ApiErrorReason.NotFound)))

        composeRule.onNodeWithText("City is not found").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Loading weather").assertDoesNotExist()
        composeRule.onNodeWithText("Search for a city").performClick()
        composeRule.onNodeWithText("Enter your city/town").assertIsDisplayed()
    }

    @Test
    fun cancelButton_closesDialog() {
        showApp()
        openSearch()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithContentDescription("Search your city").assertIsDisplayed()
    }

    @Test
    fun initialErrorScreen_searchButtonOpensCitySearch() {
        showApp()
        composeRule.runOnUiThread { viewModel.requestCityWeather("Unknown") }
        response.completeExceptionally(DomainFailureException(DomainError.Api(ApiErrorReason.NotFound)))
        composeRule.onNodeWithText("Search for a city").performClick()

        composeRule.onNodeWithText("Enter your city/town").assertIsDisplayed()
    }

    private fun openSearch() {
        composeRule.onNodeWithContentDescription("Search your city").performClick()
    }

    private fun showApp() {
        database = Room.inMemoryDatabaseBuilder(composeRule.activity, WeatherDatabase::class.java).build()
        val places = RoomPlacesRepository(database.placesDao(), TimeProvider { 100L })
        val placesViewModel =
            PlacesViewModel(
                ObserveSearchHistoryUseCase(places),
                ObserveFavoriteCitiesUseCase(places),
                SetCityFavoriteUseCase(places),
                ClearSearchHistoryUseCase(places),
            )
        val weather =
            object : WeatherRepository {
                override suspend fun getWeatherData(
                    lat: String?,
                    lon: String?,
                    city: String?,
                ): WeatherForecast = response.await()
            }
        val geocoding =
            object : GeocodingRepository {
                override suspend fun searchLocation(query: String) = GeocodedLocation(50.45, 30.52, query)

                override suspend fun resolvePlaceName(
                    latitude: Double,
                    longitude: Double,
                ): String? = null
            }
        val location =
            object : LocationRepository {
                override suspend fun getCurrentLocation(): UserLocation = error("Search must not request GPS")

                override suspend fun getLastSavedLocation(): UserLocation? = null

                override suspend fun saveLastLocation(location: UserLocation) = Unit
            }
        val network =
            object : NetworkMonitor {
                override fun isOnline() = true

                override fun observeConnectivity() = flowOf(true)
            }
        viewModel =
            MainViewModel(
                GetCurrentWeatherUseCase(weather),
                GetCurrentLocationUseCase(location),
                SearchCityWeatherUseCase(weather, geocoding),
                WeatherPresentationMapper(conditionLocalizer = { _, fallback -> fallback }),
                network,
                selected,
                RecordCitySearchUseCase(places),
            )
        composeRule.setContent {
            WeatherForecastAppTheme { WeatherApp(viewModel, onLocationRequest = {}, placesViewModel = placesViewModel) }
        }
    }

    private fun forecast() =
        WeatherForecast(
            cityName = "Kyiv",
            current = CurrentWeather("Clear", 20.0, 1),
            daily = listOf(DailyWeather("2026-10-12", "Clear", 1, 10.0, 22.0)),
            hourly = emptyList(),
        )

    private class MemorySelectedPlaceRepository : SelectedPlaceRepository {
        var place: SelectedPlace? = null

        override suspend fun getSelectedPlace(): SelectedPlace? = place

        override suspend fun saveSelectedPlace(place: SelectedPlace) {
            this.place = place
        }

        override suspend fun clearSelectedPlace() {
            place = null
        }
    }
}
