package com.am24.imbrel

import com.am24.imbrel.domain.model.AppSettings
import com.am24.imbrel.domain.model.CacheStatus
import com.am24.imbrel.domain.model.CachedForecast
import com.am24.imbrel.domain.model.CurrentWeather
import com.am24.imbrel.domain.model.LocationCoordinates
import com.am24.imbrel.domain.model.SavedCity
import com.am24.imbrel.domain.model.StartupMode
import com.am24.imbrel.domain.model.WeatherForecast
import com.am24.imbrel.domain.repository.WeatherRepository
import com.am24.imbrel.presentation.SettingsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeAppSettingsRepository()
    private val places = FakePlacesRepository()
    private val weather = CacheRepository()
    private val city = SavedCity(1, "Kyiv", "Kyiv", LocationCoordinates(50.45, 30.52), 100, true)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadingAndReactiveLists_deduplicateFavoritesAndHistory() =
        runTest(dispatcher) {
            val vm = SettingsViewModel(settings, places, weather)
            assertTrue(vm.uiState.value.isLoading)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            places.favorites.value = listOf(city)
            places.history.value = listOf(city, city.copy(id = 2, normalizedQuery = "Lviv", displayName = "Lviv"))
            runCurrent()
            assertFalse(vm.uiState.value.isLoading)
            assertEquals(2, vm.uiState.value.cities.size)
            assertEquals(1, vm.uiState.value.cacheCount)
            weather.rows.value = emptyList()
            runCurrent()
            assertEquals(0, vm.uiState.value.cacheCount)
        }

    @Test
    fun defaultCity_survivesHistoryRemovalAndResetDisablesDefaultMode() =
        runTest(dispatcher) {
            val vm = SettingsViewModel(settings, places, weather)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            vm.setDefaultCity(city)
            vm.setStartupMode(StartupMode.DefaultCity)
            runCurrent()
            places.history.value = emptyList()
            places.favorites.value = emptyList()
            runCurrent()
            assertEquals(AppSettings(StartupMode.DefaultCity, city.toSelectedPlace()), vm.uiState.value.settings)
            assertTrue(places.searches.isEmpty())
            vm.resetDefaultCity()
            runCurrent()
            assertEquals(AppSettings(), vm.uiState.value.settings)
        }

    @Test
    fun readAndWriteErrors_areVisibleAndRecoverOnRetry() =
        runTest(dispatcher) {
            settings.readFailure = IOException("Read failed")
            val vm = SettingsViewModel(settings, places, weather)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            runCurrent()
            assertTrue(vm.uiState.value.hasReadError)
            settings.readFailure = null
            vm.retry()
            runCurrent()
            assertFalse(vm.uiState.value.hasReadError)
            settings.writeFailure = IOException("Write failed")
            vm.setDefaultCity(city)
            runCurrent()
            assertTrue(vm.uiState.value.hasOperationError)
            assertFalse(vm.uiState.value.isSaving)
            assertEquals(AppSettings(), vm.uiState.value.settings)
            settings.writeFailure = null
            vm.setDefaultCity(city)
            runCurrent()
            assertFalse(vm.uiState.value.hasOperationError)
            assertEquals(city.toSelectedPlace(), vm.uiState.value.settings.defaultCity)
        }

    @Test
    fun clearingCache_showsProgressAndPreservesPlacesAndSettings() =
        runTest(dispatcher) {
            settings.settings.value = AppSettings(StartupMode.DefaultCity, city.toSelectedPlace())
            places.history.value = listOf(city)
            places.favorites.value = listOf(city)
            val vm = SettingsViewModel(settings, places, weather)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            weather.pendingClear = CompletableDeferred()
            vm.clearCache()
            runCurrent()
            assertTrue(vm.uiState.value.isClearingCache)
            weather.pendingClear?.complete(Unit)
            runCurrent()
            assertFalse(vm.uiState.value.isClearingCache)
            assertEquals(0, vm.uiState.value.cacheCount)
            assertEquals(listOf(city), places.history.value)
            assertEquals(listOf(city), places.favorites.value)
            assertEquals(AppSettings(StartupMode.DefaultCity, city.toSelectedPlace()), settings.settings.value)
            assertEquals(0, places.clearCount)
        }

    @Test
    fun failedClear_keepsCountAndCanBeRetried() =
        runTest(dispatcher) {
            weather.clearFailure = IOException("Clear failed")
            val vm = SettingsViewModel(settings, places, weather)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            vm.clearCache()
            runCurrent()
            assertTrue(vm.uiState.value.hasOperationError)
            assertFalse(vm.uiState.value.isClearingCache)
            assertEquals(1, vm.uiState.value.cacheCount)
            weather.clearFailure = null
            vm.clearCache()
            runCurrent()
            assertFalse(vm.uiState.value.hasOperationError)
            assertEquals(0, vm.uiState.value.cacheCount)
        }

    private class CacheRepository : WeatherRepository {
        val rows =
            MutableStateFlow(
                listOf(
                    CachedForecast(
                        "city:kyiv",
                        WeatherForecast(null, CurrentWeather("Clear", 20.0, 1), emptyList(), emptyList()),
                        0,
                        CacheStatus.Fresh,
                    ),
                ),
            )
        var pendingClear: CompletableDeferred<Unit>? = null
        var clearFailure: IOException? = null

        override fun observeCachedForecasts() = rows

        override suspend fun clearWeatherCache() {
            pendingClear?.await()
            clearFailure?.let { throw it }
            rows.value = emptyList()
        }

        override suspend fun getWeatherData(
            lat: String?,
            lon: String?,
            city: String?,
        ): WeatherForecast = error("API must not be called")
    }
}
