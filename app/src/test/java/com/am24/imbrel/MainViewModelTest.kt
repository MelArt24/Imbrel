package com.am24.imbrel

import com.am24.imbrel.domain.error.ApiErrorReason
import com.am24.imbrel.domain.error.DomainError
import com.am24.imbrel.domain.error.DomainFailureException
import com.am24.imbrel.domain.error.LocationErrorReason
import com.am24.imbrel.domain.error.NetworkErrorReason
import com.am24.imbrel.domain.model.CurrentWeather
import com.am24.imbrel.domain.model.DailyWeather
import com.am24.imbrel.domain.model.GeocodedLocation
import com.am24.imbrel.domain.model.HourlyWeather
import com.am24.imbrel.domain.model.LocationCoordinates
import com.am24.imbrel.domain.model.SavedCity
import com.am24.imbrel.domain.model.SelectedPlace
import com.am24.imbrel.domain.model.UserLocation
import com.am24.imbrel.domain.model.WeatherForecast
import com.am24.imbrel.domain.repository.GeocodingRepository
import com.am24.imbrel.domain.repository.LocationRepository
import com.am24.imbrel.domain.repository.SelectedPlaceRepository
import com.am24.imbrel.domain.repository.WeatherRepository
import com.am24.imbrel.domain.usecase.GetCurrentLocationUseCase
import com.am24.imbrel.domain.usecase.GetCurrentWeatherUseCase
import com.am24.imbrel.domain.usecase.RecordCitySearchUseCase
import com.am24.imbrel.domain.usecase.SearchCityWeatherUseCase
import com.am24.imbrel.presentation.MainViewModel
import com.am24.imbrel.presentation.WeatherUiError
import com.am24.imbrel.presentation.WeatherUiEvent
import com.am24.imbrel.presentation.WeatherUiState
import com.am24.imbrel.presentation.WeatherUiStatus
import com.am24.imbrel.presentation.mapper.WeatherPresentationMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun initialState_isExplicitAndEmpty() {
        val viewModel = viewModel(FakeWeatherRepository())

        assertEquals(WeatherUiState(), viewModel.uiState.value)
        assertEquals(WeatherUiStatus.Initial, viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun initialConnectivity_matchesOnlineSnapshot() {
        val viewModel = viewModel(FakeWeatherRepository(), networkMonitor = FakeNetworkMonitor(true))

        assertTrue(viewModel.isOnline.value)
    }

    @Test
    fun initialConnectivity_matchesOfflineSnapshot() {
        val viewModel = viewModel(FakeWeatherRepository(), networkMonitor = FakeNetworkMonitor(false))

        assertFalse(viewModel.isOnline.value)
    }

    @Test
    fun connectivityChanges_doNotChangeWeatherState() =
        runTest(testDispatcher) {
            val networkMonitor = FakeNetworkMonitor(true)
            val viewModel = viewModel(FakeWeatherRepository(), networkMonitor = networkMonitor)
            val weatherState = viewModel.uiState.value

            networkMonitor.setOnline(false)
            advanceUntilIdle()

            assertFalse(viewModel.isOnline.value)
            assertEquals(weatherState, viewModel.uiState.value)

            networkMonitor.setOnline(true)
            advanceUntilIdle()

            assertTrue(viewModel.isOnline.value)
            assertEquals(weatherState, viewModel.uiState.value)
        }

    @Test
    fun connectivitySurvivesWeatherLoadingTransitions() =
        runTest(testDispatcher) {
            val response = CompletableDeferred<WeatherForecast>()
            val networkMonitor = FakeNetworkMonitor(false)
            val viewModel =
                viewModel(
                    repository = FakeWeatherRepository { response.await() },
                    networkMonitor = networkMonitor,
                )

            viewModel.requestCityWeather("Kyiv")
            runCurrent()

            assertFalse(viewModel.isOnline.value)
            assertTrue(viewModel.uiState.value.isLoading)

            response.complete(successForecast())
            advanceUntilIdle()

            assertFalse(viewModel.isOnline.value)
            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
        }

    @Test
    fun currentLocationRequest_isLoadingThenSucceeds() =
        runTest(testDispatcher) {
            val response = CompletableDeferred<WeatherForecast>()
            val repository = FakeWeatherRepository(response = { response.await() })
            val viewModel = viewModel(repository, FakeLocationRepository())

            viewModel.requestCurrentLocationWeather()
            runCurrent()

            assertTrue(viewModel.uiState.value.isLoading)
            assertNull(viewModel.uiState.value.error)

            response.complete(successForecast())
            advanceUntilIdle()

            with(viewModel.uiState.value) {
                assertEquals(WeatherUiStatus.Success, status)
                assertFalse(isLoading)
                assertEquals("Kyiv", currentWeather?.city)
                assertEquals(1, dailyWeather.size)
            }
            assertEquals("50.45", repository.lastLat)
            assertEquals("30.52", repository.lastLon)
        }

    @Test
    fun currentLocationRequest_usesResolvedPlaceName() =
        runTest(testDispatcher) {
            val response = CompletableDeferred<WeatherForecast>()
            val repository =
                FakeWeatherRepository(
                    response = { response.await() },
                )
            val locationRepository =
                FakeLocationRepository(
                    coordinates =
                        UserLocation(
                            latitude = 50.45,
                            longitude = 30.52,
                            placeName = "Brovary",
                        ),
                )
            val viewModel = viewModel(repository, locationRepository)

            viewModel.requestCurrentLocationWeather()
            runCurrent()

            assertTrue(viewModel.uiState.value.isLoading)

            response.complete(successForecast())
            advanceUntilIdle()

            with(viewModel.uiState.value) {
                assertEquals(WeatherUiStatus.Success, status)
                assertEquals("Brovary", currentWeather?.city)
                assertFalse(isLoading)
            }

            assertEquals("50.45", repository.lastLat)
            assertEquals("30.52", repository.lastLon)
        }

    @Test
    fun currentLocationRequest_fallsBackWhenPlaceNameIsUnavailable() =
        runTest(testDispatcher) {
            val locationRepository =
                FakeLocationRepository(
                    coordinates =
                        UserLocation(
                            latitude = 50.45,
                            longitude = 30.52,
                            placeName = null,
                        ),
                )

            val viewModel =
                viewModel(
                    repository =
                        FakeWeatherRepository(
                            response = { successForecast() },
                        ),
                    locationRepository = locationRepository,
                )

            viewModel.requestCurrentLocationWeather()
            advanceUntilIdle()

            assertEquals(
                "Kyiv",
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
        }

    @Test
    fun cityRequest_mapsRequestedCityAndSucceeds() =
        runTest(testDispatcher) {
            val repository = FakeWeatherRepository(response = { successForecast() })
            val viewModel = viewModel(repository)

            viewModel.requestCityWeather("Kyiv")
            advanceUntilIdle()

            assertEquals("Kyiv", repository.lastCity)
            assertEquals("50.45", repository.lastLat)
            assertEquals("30.52", repository.lastLon)
            assertEquals(
                "Kyiv",
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
            assertEquals(
                "12:34",
                viewModel.uiState.value.currentWeather
                    ?.time,
            )
            assertEquals(
                "Clear",
                viewModel.uiState.value.currentWeather
                    ?.condition,
            )
            assertEquals(
                "21\u00B0C",
                viewModel.uiState.value.currentWeather
                    ?.currentTemperature,
            )
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun cityRequest_displaysLocalizedGeocoderName() =
        runTest(testDispatcher) {
            val viewModel =
                viewModel(
                    repository = FakeWeatherRepository(response = { successForecast() }),
                    geocodingRepository = FakeGeocodingRepository(localizedName = "Пекін"),
                )

            viewModel.requestCityWeather("Пекін")
            advanceUntilIdle()

            assertEquals(
                "Пекін",
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
        }

    @Test
    fun citySearchWithoutContent_storesCityNotFoundWithoutTransientDuplicate() =
        runTest(testDispatcher) {
            val viewModel =
                viewModel(
                    repository = FakeWeatherRepository(),
                    geocodingRepository = FakeGeocodingRepository(found = false),
                )
            viewModel.requestCityWeather("Unknown place")
            advanceUntilIdle()

            val event = async { viewModel.events.first() }
            runCurrent()

            val expectedError =
                WeatherUiError.CitySearch(
                    DomainError.Api(ApiErrorReason.NotFound),
                )
            assertEquals(expectedError, viewModel.uiState.value.error)
            assertEquals(WeatherUiStatus.Error, viewModel.uiState.value.status)
            assertFalse(event.isCompleted)
            event.cancel()
        }

    @Test
    fun emptyForecast_hasExplicitEmptyState() =
        runTest(testDispatcher) {
            val viewModel = viewModel(FakeWeatherRepository(response = { emptyForecast() }), FakeLocationRepository())

            viewModel.requestCurrentLocationWeather()
            advanceUntilIdle()

            assertEquals(WeatherUiStatus.Empty, viewModel.uiState.value.status)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun failedInitialRequest_setsErrorStopsLoadingWithoutTransientDuplicate() =
        runTest(testDispatcher) {
            val viewModel = viewModel(FakeWeatherRepository(response = { throw IllegalStateException() }))

            viewModel.requestCurrentLocationWeather()
            advanceUntilIdle()

            val event = async { viewModel.events.first() }
            runCurrent()

            assertEquals(WeatherUiStatus.Error, viewModel.uiState.value.status)
            val expectedError = WeatherUiError.Weather(DomainError.Unknown)
            assertEquals(expectedError, viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(event.isCompleted)
            event.cancel()
        }

    @Test
    fun failedRefresh_keepsExistingWeatherAndUsesTransientError() =
        runTest(testDispatcher) {
            var requestCount = 0
            val viewModel =
                viewModel(
                    FakeWeatherRepository(response = {
                        if (requestCount++ == 0) {
                            successForecast()
                        } else {
                            throw DomainFailureException(
                                DomainError.Network(NetworkErrorReason.Offline),
                            )
                        }
                    }),
                )

            viewModel.requestCurrentLocationWeather()
            advanceUntilIdle()
            val existingWeather = viewModel.uiState.value.currentWeather
            val event = async { viewModel.events.first() }

            viewModel.requestCurrentLocationWeather()
            runCurrent()

            assertSame(existingWeather, viewModel.uiState.value.currentWeather)
            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
            assertFalse(viewModel.uiState.value.isLoading)

            advanceUntilIdle()

            assertSame(existingWeather, viewModel.uiState.value.currentWeather)
            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
            assertNull(viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertEquals(
                WeatherUiEvent.ShowError(
                    WeatherUiError.Weather(DomainError.Network(NetworkErrorReason.Offline)),
                ),
                event.await(),
            )
        }

    @Test
    fun initialDomainFailure_hasPersistentErrorAndClearsAllLoading() =
        runTest(testDispatcher) {
            val failure = DomainError.Api(ApiErrorReason.RequestFailed)
            val viewModel =
                viewModel(
                    FakeWeatherRepository(response = { throw DomainFailureException(failure) }),
                )

            viewModel.requestCurrentLocationWeather()
            advanceUntilIdle()

            with(viewModel.uiState.value) {
                assertEquals(WeatherUiStatus.Error, status)
                assertEquals(WeatherUiError.Weather(failure), error)
                assertFalse(isLoading)
                assertFalse(isRefreshing)
                assertFalse(isLocationLoading)
                assertFalse(isWeatherLoading)
                assertFalse(isCitySearchLoading)
            }
        }

    @Test
    fun cachedFallback_isPresentedAsSuccessfulContent() =
        runTest(testDispatcher) {
            val cachedForecast = successForecast().copy(cityName = "Cached Kyiv")
            val viewModel = viewModel(FakeWeatherRepository(response = { cachedForecast }))

            viewModel.requestCurrentLocationWeather()
            advanceUntilIdle()

            with(viewModel.uiState.value) {
                assertEquals(WeatherUiStatus.Success, status)
                assertEquals("Kyiv", currentWeather?.city)
                assertFalse(isLoading)
                assertFalse(isRefreshing)
                assertNull(error)
            }
        }

    @Test
    fun newerRequest_cannotBeOverwrittenByOlderNonCooperativeRequest() =
        runTest(testDispatcher) {
            val olderResponse = CompletableDeferred<WeatherForecast>()
            var requestCount = 0
            val viewModel =
                viewModel(
                    FakeWeatherRepository(response = {
                        if (requestCount++ == 0) {
                            withContext(NonCancellable) { olderResponse.await() }
                        } else {
                            successForecast().copy(cityName = "Newer")
                        }
                    }),
                )

            viewModel.requestCityWeather("Older")
            runCurrent()
            viewModel.requestCityWeather("Newer")
            runCurrent()

            assertEquals(
                "Newer",
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
            assertFalse(viewModel.uiState.value.isCitySearchLoading)

            olderResponse.complete(successForecast().copy(cityName = "Older"))
            advanceUntilIdle()

            assertEquals(
                "Newer",
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isCitySearchLoading)
        }

    @Test
    fun operationLoadingFlags_changeIndependently() =
        runTest(testDispatcher) {
            val location = CompletableDeferred<UserLocation>()
            val weather = CompletableDeferred<WeatherForecast>()
            val locationRepository =
                object : LocationRepository {
                    override suspend fun getCurrentLocation(): UserLocation = location.await()

                    override suspend fun getLastSavedLocation(): UserLocation? = null

                    override suspend fun saveLastLocation(location: UserLocation) = Unit
                }
            val viewModel =
                viewModel(
                    FakeWeatherRepository(response = { weather.await() }),
                    locationRepository,
                )

            viewModel.requestCurrentLocationWeather()
            runCurrent()
            with(viewModel.uiState.value) {
                assertTrue(isLocationLoading)
                assertFalse(isWeatherLoading)
                assertFalse(isCitySearchLoading)
            }

            location.complete(UserLocation(50.45, 30.52, "Kyiv"))
            runCurrent()
            with(viewModel.uiState.value) {
                assertFalse(isLocationLoading)
                assertTrue(isWeatherLoading)
                assertFalse(isCitySearchLoading)
            }

            viewModel.requestCityWeather("Lviv")
            runCurrent()
            with(viewModel.uiState.value) {
                assertFalse(isLocationLoading)
                assertFalse(isWeatherLoading)
                assertTrue(isCitySearchLoading)
            }
        }

    @Test
    fun cancellation_isNotRenderedAsErrorAndClearsLoading() =
        runTest(testDispatcher) {
            val viewModel =
                viewModel(
                    FakeWeatherRepository(response = { throw CancellationException("cancelled") }),
                )
            val event = async { viewModel.events.first() }

            viewModel.requestCityWeather("Kyiv")
            advanceUntilIdle()

            with(viewModel.uiState.value) {
                assertEquals(WeatherUiStatus.Initial, status)
                assertNull(error)
                assertFalse(isLoading)
                assertFalse(isCitySearchLoading)
            }
            assertFalse(event.isCompleted)
            event.cancel()
        }

    @Test
    fun locationCancellation_clearsLocationLoadingWithoutError() =
        runTest(testDispatcher) {
            val locationRepository =
                object : LocationRepository {
                    override suspend fun getCurrentLocation(): UserLocation = throw CancellationException("cancelled")

                    override suspend fun getLastSavedLocation(): UserLocation? = null

                    override suspend fun saveLastLocation(location: UserLocation) = Unit
                }
            val viewModel = viewModel(FakeWeatherRepository(), locationRepository)
            viewModel.requestCurrentLocationWeather()
            advanceUntilIdle()

            val event = async { viewModel.events.first() }
            runCurrent()

            with(viewModel.uiState.value) {
                assertEquals(WeatherUiStatus.Initial, status)
                assertNull(error)
                assertFalse(isLoading)
                assertFalse(isLocationLoading)
                assertFalse(isWeatherLoading)
            }
            assertFalse(event.isCompleted)
            event.cancel()
        }

    @Test
    fun offlineRequestWithoutCache_storesOfflineErrorWithoutTransientDuplicate() =
        runTest(testDispatcher) {
            val viewModel =
                viewModel(
                    FakeWeatherRepository(response = {
                        throw DomainFailureException(DomainError.Network(NetworkErrorReason.Offline))
                    }),
                )
            val event = async { viewModel.events.first() }

            viewModel.requestCurrentLocationWeather()
            advanceUntilIdle()

            val expectedError =
                WeatherUiError.Weather(
                    DomainError.Network(NetworkErrorReason.Offline),
                )
            assertEquals(expectedError, viewModel.uiState.value.error)
            assertFalse(event.isCompleted)
            event.cancel()
        }

    @Test
    fun missingLocationPermission_setsPersistentPermissionErrorOnly() =
        runTest(testDispatcher) {
            val locationRepository =
                object : LocationRepository {
                    override suspend fun getCurrentLocation(): UserLocation =
                        throw DomainFailureException(
                            DomainError.Location(LocationErrorReason.PermissionDenied),
                        )

                    override suspend fun getLastSavedLocation(): UserLocation? = null

                    override suspend fun saveLastLocation(location: UserLocation) = Unit
                }
            val viewModel = viewModel(FakeWeatherRepository(), locationRepository)

            viewModel.requestCurrentLocationWeather()
            advanceUntilIdle()

            val event = async { viewModel.events.first() }
            runCurrent()

            assertEquals(WeatherUiStatus.Error, viewModel.uiState.value.status)
            val expectedError =
                WeatherUiError.Location(
                    DomainError.Location(LocationErrorReason.PermissionDenied),
                )
            assertEquals(expectedError, viewModel.uiState.value.error)
            assertFalse(event.isCompleted)
            event.cancel()
        }

    @Test
    fun deniedPermission_withSavedLocation_loadsWeatherWithoutPermissionError() =
        runTest(testDispatcher) {
            val weatherRepository = FakeWeatherRepository(response = { successForecast() })
            val locationRepository =
                FakeLocationRepository(
                    currentFailure = SecurityException("Permission denied"),
                    savedLocation = UserLocation(49.84, 24.03, "Lviv"),
                )
            val viewModel = viewModel(weatherRepository, locationRepository)

            viewModel.requestCurrentLocationWeather()
            advanceUntilIdle()

            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
            assertNull(viewModel.uiState.value.error)
            assertEquals(
                "Lviv",
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
            assertEquals("49.84", weatherRepository.lastLat)
            assertEquals("24.03", weatherRepository.lastLon)
        }

    @Test
    fun locationFailure_withoutSavedLocation_setsLocationError() =
        runTest(testDispatcher) {
            val locationRepository =
                FakeLocationRepository(
                    currentFailure = IllegalStateException("Unavailable"),
                )
            val viewModel = viewModel(FakeWeatherRepository(), locationRepository)

            viewModel.requestCurrentLocationWeather()
            advanceUntilIdle()

            val event = async { viewModel.events.first() }
            runCurrent()

            val expectedError = WeatherUiError.Location(DomainError.Unknown)
            assertEquals(expectedError, viewModel.uiState.value.error)
            assertFalse(event.isCompleted)
            event.cancel()
        }

    @Test
    fun errorEvent_isConsumedOnlyOncePerFailure() =
        runTest(testDispatcher) {
            var requestCount = 0
            val viewModel =
                viewModel(
                    FakeWeatherRepository(response = {
                        if (requestCount++ == 0) successForecast() else error("Unexpected additional request")
                    }),
                )

            viewModel.requestCurrentLocationWeather()
            advanceUntilIdle()

            viewModel.requestCurrentLocationWeather()
            advanceUntilIdle()

            assertEquals(
                WeatherUiEvent.ShowError(WeatherUiError.Weather(DomainError.Unknown)),
                viewModel.events.first(),
            )
            val secondCollector = async { viewModel.events.first() }
            runCurrent()
            assertFalse(secondCollector.isCompleted)
            secondCollector.cancel()
        }

    @Test
    fun retryLastRequest_repeatsFailedCitySearch() =
        runTest(testDispatcher) {
            var requestCount = 0
            val repository =
                FakeWeatherRepository(response = {
                    requestCount++
                    if (requestCount == 1) {
                        throw DomainFailureException(DomainError.Api(ApiErrorReason.ServerError))
                    }
                    successForecast()
                })
            val viewModel = viewModel(repository)

            viewModel.requestCityWeather("Kyiv")
            advanceUntilIdle()
            assertEquals(WeatherUiStatus.Error, viewModel.uiState.value.status)

            viewModel.retryLastRequest()
            advanceUntilIdle()

            assertEquals(2, requestCount)
            assertEquals("Kyiv", repository.lastCity)
            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun retryLastRequest_repeatsFailedCurrentLocationRequest() =
        runTest(testDispatcher) {
            var locationRequests = 0
            val locationError = DomainError.Location(LocationErrorReason.Unavailable)
            val locationRepository =
                object : LocationRepository {
                    override suspend fun getCurrentLocation(): UserLocation {
                        locationRequests++
                        if (locationRequests == 1) throw DomainFailureException(locationError)
                        return UserLocation(50.45, 30.52, "Kyiv")
                    }

                    override suspend fun getLastSavedLocation(): UserLocation? = null

                    override suspend fun saveLastLocation(location: UserLocation) = Unit
                }
            val viewModel =
                viewModel(
                    FakeWeatherRepository(response = { successForecast() }),
                    locationRepository,
                )

            viewModel.requestCurrentLocationWeather()
            advanceUntilIdle()
            assertEquals(WeatherUiError.Location(locationError), viewModel.uiState.value.error)

            viewModel.retryLastRequest()
            advanceUntilIdle()

            assertEquals(2, locationRequests)
            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
            assertEquals(
                "Kyiv",
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
            assertNull(viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun staleOlderFailure_doesNotReplaceSuccessEmitEventOrChangeLoading() =
        runTest(testDispatcher) {
            val olderRequest = CompletableDeferred<Unit>()
            var requestCount = 0
            val viewModel =
                viewModel(
                    FakeWeatherRepository(response = {
                        if (requestCount++ == 0) {
                            withContext(NonCancellable) { olderRequest.await() }
                            throw DomainFailureException(
                                DomainError.Api(ApiErrorReason.ServerError, statusCode = 503),
                            )
                        }
                        successForecast().copy(cityName = "Newer")
                    }),
                )

            viewModel.requestCityWeather("Older")
            runCurrent()
            viewModel.requestCityWeather("Newer")
            runCurrent()

            assertEquals(
                "Newer",
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
            assertFalse(viewModel.uiState.value.isCitySearchLoading)

            val event = async { viewModel.events.first() }
            runCurrent()
            olderRequest.complete(Unit)
            runCurrent()

            assertEquals(
                "Newer",
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
            assertNull(viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertFalse(viewModel.uiState.value.isCitySearchLoading)
            assertFalse(event.isCompleted)
            event.cancel()
        }

    @Test
    fun cityForecast_keepsCurrentWeatherAndHourlyForecastSeparateFromDays() =
        runTest(testDispatcher) {
            val forecast = successForecast()
            val viewModel = viewModel(FakeWeatherRepository(response = { forecast }))

            viewModel.requestCityWeather("Kyiv")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("21\u00B0C", state.currentWeather?.currentTemperature)
            assertEquals("12:34", state.currentWeather?.time)
            val currentHour = state.currentWeather?.hourlyWeather?.single()
            val day = state.dailyWeather.single()
            assertEquals("12:00", currentHour?.time)
            assertEquals("21\u00B0C", currentHour?.currentTemperature)
            assertEquals("2026-07-05", day.time)
            assertTrue(day.currentTemperature.isEmpty())
        }

    @Test
    fun firstLaunch_initializesOnceAndRequestsLocation() =
        runTest(testDispatcher) {
            val selected = FakeSelectedPlaceRepository()
            val viewModel = viewModel(FakeWeatherRepository(), selectedPlaceRepository = selected)
            viewModel.initializeStartup()
            viewModel.initializeStartup()
            val request = viewModel.locationRequests.first()
            assertFalse(request.requestPermission)
            assertTrue(viewModel.isCurrentRequest(request.id))
            assertEquals(1, selected.readCount)
            assertTrue(selected.writes.isEmpty())
        }

    @Test
    fun startupCity_withCoordinatesBypassesGeocodingAndLocation() =
        runTest(testDispatcher) {
            val city = SelectedPlace.City("London", "Saved London", LocationCoordinates(51.5, -0.12))
            val selected = FakeSelectedPlaceRepository(city)
            val weather = FakeWeatherRepository()
            val viewModel =
                viewModel(
                    weather,
                    FakeLocationRepository(currentFailure = IllegalStateException("GPS must not be used")),
                    FakeGeocodingRepository(found = false),
                    selectedPlaceRepository = selected,
                )
            viewModel.initializeStartup()
            advanceUntilIdle()
            viewModel.initializeStartup()
            advanceUntilIdle()

            assertEquals("51.5", weather.lastLat)
            assertEquals("-0.12", weather.lastLon)
            assertEquals("London", weather.lastCity)
            assertEquals(
                "Saved London",
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
            assertEquals(1, selected.readCount)
            assertTrue(selected.writes.isEmpty())
        }

    @Test
    fun startupCity_withoutCoordinatesUsesNormalizedQuery() =
        runTest(testDispatcher) {
            val selected = FakeSelectedPlaceRepository(SelectedPlace.City("Kyiv", "Kyiv"))
            val weather = FakeWeatherRepository()
            val viewModel = viewModel(weather, selectedPlaceRepository = selected)
            viewModel.initializeStartup()
            advanceUntilIdle()

            assertEquals("Kyiv", weather.lastCity)
            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
        }

    @Test
    fun startupCity_offlineUsesCityCacheWithoutGeocoding() =
        runTest(testDispatcher) {
            val weather = FakeWeatherRepository()
            val viewModel =
                viewModel(
                    weather,
                    geocodingRepository = FakeGeocodingRepository(found = false),
                    networkMonitor = FakeNetworkMonitor(false),
                    selectedPlaceRepository = FakeSelectedPlaceRepository(SelectedPlace.City("Kyiv", "Kyiv")),
                )
            viewModel.initializeStartup()
            advanceUntilIdle()

            assertEquals("Kyiv", weather.lastCity)
            assertNull(weather.lastLat)
            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
        }

    @Test
    fun startupCurrentLocation_usesPermissionFlowAndPersistsAfterSuccess() =
        runTest(testDispatcher) {
            val selected = FakeSelectedPlaceRepository(SelectedPlace.CurrentLocation)
            val viewModel = viewModel(FakeWeatherRepository(), selectedPlaceRepository = selected)
            viewModel.initializeStartup()
            val request = viewModel.locationRequests.first()
            viewModel.requestCurrentLocationWeather(expectedRequestId = request.id)
            advanceUntilIdle()

            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
            assertEquals(listOf(SelectedPlace.CurrentLocation), selected.writes)
        }

    @Test
    fun deniedPermission_usesSavedCoordinatesWithoutNewLocation() =
        runTest(testDispatcher) {
            val weather = FakeWeatherRepository()
            val viewModel =
                viewModel(
                    weather,
                    FakeLocationRepository(
                        currentFailure = IllegalStateException("GPS must not be used"),
                        savedLocation = UserLocation(1.0, 2.0, "Saved"),
                    ),
                )
            viewModel.requestLocation()
            val request = viewModel.locationRequests.first()
            viewModel.requestCurrentLocationWeather(allowCurrentLocation = false, expectedRequestId = request.id)
            advanceUntilIdle()

            assertEquals("1.0", weather.lastLat)
            assertEquals(
                "Saved",
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
        }

    @Test
    fun noSavedCoordinates_requestsPermissionAndAllowsSearchAfterDenial() =
        runTest(testDispatcher) {
            val selected = FakeSelectedPlaceRepository()
            val viewModel = viewModel(FakeWeatherRepository(), selectedPlaceRepository = selected)
            viewModel.requestLocation()
            val request = viewModel.locationRequests.first()
            viewModel.requestSavedLocationOrPermission(request.id)
            val permission = viewModel.locationRequests.first()
            assertTrue(permission.requestPermission)
            viewModel.requestCurrentLocationWeather(allowCurrentLocation = false, expectedRequestId = permission.id)
            advanceUntilIdle()
            assertEquals(
                WeatherUiError.Location(DomainError.Location(LocationErrorReason.PermissionDenied)),
                viewModel.uiState.value.error,
            )
            assertTrue(selected.writes.isEmpty())

            viewModel.requestCityWeather("Kyiv")
            advanceUntilIdle()
            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
            assertTrue(selected.value is SelectedPlace.City)
        }

    @Test
    fun startupReadFailure_fallsBackToLocation() =
        runTest(testDispatcher) {
            val selected = FakeSelectedPlaceRepository(read = { throw java.io.IOException("read") })
            val viewModel = viewModel(FakeWeatherRepository(), selectedPlaceRepository = selected)
            viewModel.initializeStartup()

            assertTrue(viewModel.isCurrentRequest(viewModel.locationRequests.first().id))
            assertTrue(selected.writes.isEmpty())
        }

    @Test
    fun persistenceFailure_doesNotHideSuccessfulForecast() =
        runTest(testDispatcher) {
            val selected = FakeSelectedPlaceRepository(writeFailure = java.io.IOException("write"))
            val viewModel = viewModel(FakeWeatherRepository(), selectedPlaceRepository = selected)
            viewModel.requestCityWeather("Kyiv")
            advanceUntilIdle()

            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun failedSearch_doesNotOverwriteSelectedPlace() =
        runTest(testDispatcher) {
            val selected = FakeSelectedPlaceRepository(SelectedPlace.CurrentLocation)
            val viewModel =
                viewModel(
                    FakeWeatherRepository(response = { throw DomainFailureException(DomainError.Unknown) }),
                    selectedPlaceRepository = selected,
                )
            viewModel.requestCityWeather("Kyiv")
            advanceUntilIdle()

            assertSame(SelectedPlace.CurrentLocation, selected.value)
            assertTrue(selected.writes.isEmpty())
        }

    @Test
    fun slowStartupRead_doesNotOverrideManualCitySearch() =
        runTest(testDispatcher) {
            val stored = CompletableDeferred<SelectedPlace?>()
            val selected = FakeSelectedPlaceRepository(read = { stored.await() })
            val viewModel = viewModel(FakeWeatherRepository(), selectedPlaceRepository = selected)
            viewModel.initializeStartup()
            runCurrent()
            viewModel.requestCityWeather("Manual")
            advanceUntilIdle()
            stored.complete(SelectedPlace.City("Stored", "Stored"))
            advanceUntilIdle()

            assertEquals(
                "Manual",
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
            assertEquals("Manual", (selected.value as SelectedPlace.City).normalizedQuery)
        }

    @Test
    fun latePermissionResult_doesNotOverrideManualCity() =
        runTest(testDispatcher) {
            val selected = FakeSelectedPlaceRepository()
            val viewModel = viewModel(FakeWeatherRepository(), selectedPlaceRepository = selected)
            viewModel.requestLocation()
            val request = viewModel.locationRequests.first()
            viewModel.requestCityWeather("Manual")
            advanceUntilIdle()
            viewModel.requestCurrentLocationWeather(expectedRequestId = request.id)
            advanceUntilIdle()

            assertEquals(
                "Manual",
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
            assertEquals(1, selected.writes.size)
        }

    @Test
    fun nonCooperativeOlderRequest_cannotPersistOverNewerSelection() =
        runTest(testDispatcher) {
            val older = CompletableDeferred<Unit>()
            var calls = 0
            val weather =
                FakeWeatherRepository {
                    if (calls++ == 0) withContext(NonCancellable) { older.await() }
                    successForecast()
                }
            val selected = FakeSelectedPlaceRepository()
            val viewModel = viewModel(weather, selectedPlaceRepository = selected)
            viewModel.requestCityWeather("Older")
            runCurrent()
            viewModel.requestCityWeather("Newer")
            runCurrent()
            older.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                "Newer",
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
            assertEquals("Newer", (selected.value as SelectedPlace.City).normalizedQuery)
            assertEquals(1, selected.writes.size)
        }

    @Test
    fun successfulManualSearch_persistsNormalizedQueryDisplayNameAndCoordinates() =
        runTest(testDispatcher) {
            val selected = FakeSelectedPlaceRepository()
            val viewModel =
                viewModel(
                    FakeWeatherRepository(),
                    geocodingRepository = FakeGeocodingRepository(localizedName = "Localized Kyiv"),
                    selectedPlaceRepository = selected,
                )
            viewModel.requestCityWeather("  Kyiv   City ")
            advanceUntilIdle()

            assertEquals(
                SelectedPlace.City("Kyiv City", "Localized Kyiv", LocationCoordinates(50.45, 30.52)),
                selected.value,
            )
        }

    @Test
    fun offlineCurrentLocation_usesSavedLocationAndCachedForecast() =
        runTest(testDispatcher) {
            val weather = FakeWeatherRepository()
            val selected = FakeSelectedPlaceRepository(SelectedPlace.CurrentLocation)
            val viewModel =
                viewModel(
                    weather,
                    FakeLocationRepository(
                        currentFailure = IllegalStateException("GPS must not be used"),
                        savedLocation = UserLocation(1.0, 2.0, "Saved"),
                    ),
                    networkMonitor = FakeNetworkMonitor(false),
                    selectedPlaceRepository = selected,
                )
            viewModel.initializeStartup()
            val request = viewModel.locationRequests.first()
            viewModel.requestCurrentLocationWeather(expectedRequestId = request.id)
            advanceUntilIdle()

            assertEquals("1.0", weather.lastLat)
            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
        }

    @Test
    fun cancelledSearch_doesNotPersistSelection() =
        runTest(testDispatcher) {
            val selected = FakeSelectedPlaceRepository(SelectedPlace.CurrentLocation)
            val viewModel =
                viewModel(
                    FakeWeatherRepository { throw CancellationException("cancel") },
                    selectedPlaceRepository = selected,
                )
            viewModel.requestCityWeather("Kyiv")
            advanceUntilIdle()

            assertSame(SelectedPlace.CurrentLocation, selected.value)
            assertTrue(selected.writes.isEmpty())
        }

    @Test
    fun failedLocationForecast_doesNotReplaceSelectedCity() =
        runTest(testDispatcher) {
            val city = SelectedPlace.City("Kyiv", "Kyiv")
            val selected = FakeSelectedPlaceRepository(city)
            val viewModel =
                viewModel(
                    FakeWeatherRepository { throw DomainFailureException(DomainError.Unknown) },
                    selectedPlaceRepository = selected,
                )
            viewModel.requestCurrentLocationWeather()
            advanceUntilIdle()

            assertEquals(city, selected.value)
            assertTrue(selected.writes.isEmpty())
        }

    @Test
    fun supersededSearch_doesNotPersistWhenNewerSearchFails() =
        runTest(testDispatcher) {
            val older = CompletableDeferred<Unit>()
            var calls = 0
            val weather =
                FakeWeatherRepository {
                    if (calls++ == 0) {
                        withContext(NonCancellable) { older.await() }
                        successForecast()
                    } else {
                        throw DomainFailureException(DomainError.Unknown)
                    }
                }
            val selected = FakeSelectedPlaceRepository(SelectedPlace.CurrentLocation)
            val viewModel = viewModel(weather, selectedPlaceRepository = selected)
            viewModel.requestCityWeather("Older")
            runCurrent()
            viewModel.requestCityWeather("Newer")
            runCurrent()
            older.complete(Unit)
            advanceUntilIdle()

            assertSame(SelectedPlace.CurrentLocation, selected.value)
            assertTrue(selected.writes.isEmpty())
        }

    @Test
    fun manualRequestBeforeStartup_isNotReplacedByStoredSelection() =
        runTest(testDispatcher) {
            val selected = FakeSelectedPlaceRepository(SelectedPlace.City("Stored", "Stored"))
            val viewModel = viewModel(FakeWeatherRepository(), selectedPlaceRepository = selected)
            viewModel.requestCityWeather("Manual")
            viewModel.initializeStartup()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Manual", state.currentWeather?.city)
            assertEquals(0, selected.readCount)
        }

    @Test
    fun searchScreen_blankQueryDoesNotStartRequest() =
        runTest(testDispatcher) {
            val selected = FakeSelectedPlaceRepository()
            val viewModel = viewModel(FakeWeatherRepository(), selectedPlaceRepository = selected)
            viewModel.openCitySearch()
            viewModel.updateCitySearchQuery("  ")
            viewModel.submitCitySearch()
            advanceUntilIdle()

            assertTrue(viewModel.citySearchState.value.isQueryBlank)
            assertFalse(viewModel.citySearchState.value.isLoading)
            assertEquals(WeatherUiStatus.Initial, viewModel.uiState.value.status)
            assertTrue(selected.writes.isEmpty())
        }

    @Test
    fun searchScreen_existingForecastIsNotSearchSuccess() =
        runTest(testDispatcher) {
            val viewModel = viewModel(FakeWeatherRepository())
            viewModel.requestCityWeather("Existing")
            advanceUntilIdle()
            viewModel.openCitySearch()

            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
            assertNull(viewModel.citySearchState.value.successId)
        }

    @Test
    fun searchScreen_successIsRetainedUntilAcknowledgedExactlyOnce() =
        runTest(testDispatcher) {
            val selected = FakeSelectedPlaceRepository()
            val viewModel = viewModel(FakeWeatherRepository(), selectedPlaceRepository = selected)
            viewModel.openCitySearch()
            viewModel.updateCitySearchQuery("  Kyiv ")
            viewModel.submitCitySearch()
            advanceUntilIdle()

            val id = requireNotNull(viewModel.citySearchState.value.successId)
            assertEquals(
                "Kyiv",
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
            assertTrue(selected.value is SelectedPlace.City)
            assertFalse(viewModel.consumeCitySearchSuccess(id + 1))
            assertTrue(viewModel.consumeCitySearchSuccess(id))
            assertFalse(viewModel.consumeCitySearchSuccess(id))
            viewModel.openCitySearch()
            assertNull(viewModel.citySearchState.value.successId)
        }

    @Test
    fun searchScreen_errorIsLocalAndCanBeRetriedWithoutOldSuccess() =
        runTest(testDispatcher) {
            var calls = 0
            val viewModel =
                viewModel(
                    FakeWeatherRepository {
                        if (calls++ == 1) throw DomainFailureException(DomainError.Api(ApiErrorReason.NotFound))
                        successForecast()
                    },
                )
            viewModel.requestCityWeather("Existing")
            advanceUntilIdle()
            val previous = viewModel.uiState.value
            viewModel.openCitySearch()
            viewModel.updateCitySearchQuery("Unknown")
            viewModel.submitCitySearch()
            advanceUntilIdle()

            assertEquals(previous, viewModel.uiState.value)
            assertEquals(
                WeatherUiError.CitySearch(DomainError.Api(ApiErrorReason.NotFound)),
                viewModel.citySearchState.value.error,
            )
            assertNull(viewModel.citySearchState.value.successId)
            viewModel.updateCitySearchQuery("Kyiv")
            viewModel.submitCitySearch()
            advanceUntilIdle()
            assertNotNull(viewModel.citySearchState.value.successId)
        }

    @Test
    fun searchScreen_backCancelsNonCooperativeRequestAndPreservesSelection() =
        runTest(testDispatcher) {
            val pending = CompletableDeferred<Unit>()
            var calls = 0
            val selected = FakeSelectedPlaceRepository()
            val viewModel =
                viewModel(
                    FakeWeatherRepository {
                        if (calls++ > 0) withContext(NonCancellable) { pending.await() }
                        successForecast()
                    },
                    selectedPlaceRepository = selected,
                )
            viewModel.requestCityWeather("Existing")
            advanceUntilIdle()
            val previous = viewModel.uiState.value
            val previousPlace = selected.value
            viewModel.openCitySearch()
            viewModel.updateCitySearchQuery("Late city")
            viewModel.submitCitySearch()
            runCurrent()
            viewModel.closeCitySearch()
            pending.complete(Unit)
            advanceUntilIdle()

            assertEquals(previous, viewModel.uiState.value)
            assertEquals(previousPlace, selected.value)
            assertEquals(1, selected.writes.size)
            assertNull(viewModel.citySearchState.value.successId)
        }

    @Test
    fun searchScreen_duplicateSubmitStartsOnlyOneRequest() =
        runTest(testDispatcher) {
            val pending = CompletableDeferred<WeatherForecast>()
            var calls = 0
            val viewModel =
                viewModel(
                    FakeWeatherRepository {
                        calls++
                        pending.await()
                    },
                )
            viewModel.openCitySearch()
            viewModel.updateCitySearchQuery("Kyiv")
            viewModel.submitCitySearch()
            viewModel.submitCitySearch()
            runCurrent()
            assertEquals(1, calls)
            pending.complete(successForecast())
            advanceUntilIdle()
        }

    @Test
    fun searchScreen_emptyForecastReportsErrorWithoutPersistingOrNavigating() =
        runTest(testDispatcher) {
            val selected = FakeSelectedPlaceRepository()
            val viewModel = viewModel(FakeWeatherRepository { emptyForecast() }, selectedPlaceRepository = selected)
            viewModel.openCitySearch()
            viewModel.updateCitySearchQuery("Kyiv")
            viewModel.submitCitySearch()
            advanceUntilIdle()

            assertNull(viewModel.citySearchState.value.successId)
            assertNotNull(viewModel.citySearchState.value.error)
            assertTrue(selected.writes.isEmpty())
        }

    @Test
    fun searchScreen_backPreservesMainScreenRetryTarget() =
        runTest(testDispatcher) {
            val pending = CompletableDeferred<WeatherForecast>()
            var calls = 0
            val repository =
                FakeWeatherRepository {
                    when (calls++) {
                        0 -> throw DomainFailureException(DomainError.Unknown)
                        1 -> pending.await()
                        else -> successForecast()
                    }
                }
            val viewModel = viewModel(repository)
            viewModel.requestCityWeather("Original")
            advanceUntilIdle()
            viewModel.openCitySearch()
            viewModel.updateCitySearchQuery("Cancelled")
            viewModel.submitCitySearch()
            runCurrent()
            viewModel.closeCitySearch()
            viewModel.retryLastRequest()
            advanceUntilIdle()

            assertEquals("Original", repository.lastCity)
            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
        }

    @Test
    fun immediateDialogSubmit_runsInBackgroundAndPublishesForecast() =
        runTest(testDispatcher) {
            val pending = CompletableDeferred<WeatherForecast>()
            val selected = FakeSelectedPlaceRepository()
            val viewModel = viewModel(FakeWeatherRepository { pending.await() }, selectedPlaceRepository = selected)
            viewModel.openCitySearch()
            viewModel.updateCitySearchQuery("Kyiv")

            assertTrue(viewModel.submitCitySearch(closeOnSubmit = true))
            assertTrue(viewModel.uiState.value.isCitySearchLoading)
            assertFalse(viewModel.citySearchState.value.isLoading)
            assertEquals("", viewModel.citySearchState.value.query)
            pending.complete(successForecast())
            advanceUntilIdle()

            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
            assertTrue(selected.value is SelectedPlace.City)
            assertFalse(viewModel.uiState.value.isCitySearchLoading)
        }

    @Test
    fun immediateDialogSubmit_blankQueryKeepsDialogAndDoesNotLoad() =
        runTest(testDispatcher) {
            val viewModel = viewModel(FakeWeatherRepository())
            viewModel.openCitySearch()
            assertFalse(viewModel.submitCitySearch(closeOnSubmit = true))

            assertTrue(viewModel.citySearchState.value.isQueryBlank)
            assertFalse(viewModel.uiState.value.isCitySearchLoading)
        }

    @Test
    fun immediateDialogSubmit_failureIsReportedOnMain() =
        runTest(testDispatcher) {
            val selected = FakeSelectedPlaceRepository(SelectedPlace.CurrentLocation)
            val viewModel =
                viewModel(
                    FakeWeatherRepository { throw DomainFailureException(DomainError.Api(ApiErrorReason.NotFound)) },
                    selectedPlaceRepository = selected,
                )
            viewModel.openCitySearch()
            viewModel.updateCitySearchQuery("Unknown")
            viewModel.submitCitySearch(closeOnSubmit = true)
            advanceUntilIdle()

            assertEquals(WeatherUiStatus.Error, viewModel.uiState.value.status)
            assertFalse(viewModel.uiState.value.isCitySearchLoading)
            assertSame(SelectedPlace.CurrentLocation, selected.value)
        }

    @Test
    fun successfulManualSearch_recordsHistoryWithResolvedMetadata() =
        runTest(testDispatcher) {
            val places = FakePlacesRepository()
            val viewModel =
                viewModel(
                    FakeWeatherRepository(),
                    geocodingRepository = FakeGeocodingRepository(localizedName = "Resolved Kyiv"),
                    placesRepository = places,
                )
            viewModel.openCitySearch()
            viewModel.updateCitySearchQuery("  Kyiv   City ")
            viewModel.submitCitySearch(closeOnSubmit = true)
            advanceUntilIdle()
            assertEquals(
                listOf(SelectedPlace.City("Kyiv City", "Resolved Kyiv", LocationCoordinates(50.45, 30.52))),
                places.searches,
            )
        }

    @Test
    fun startupAndGps_doNotRecordSearchHistory() =
        runTest(testDispatcher) {
            val places = FakePlacesRepository()
            val selected = FakeSelectedPlaceRepository(SelectedPlace.City("Kyiv", "Kyiv", LocationCoordinates(50.45, 30.52)))
            val viewModel = viewModel(FakeWeatherRepository(), selectedPlaceRepository = selected, placesRepository = places)
            viewModel.initializeStartup()
            advanceUntilIdle()
            viewModel.requestCurrentLocationWeather()
            advanceUntilIdle()
            assertTrue(places.searches.isEmpty())
        }

    @Test
    fun failedCancelledAndEmptySearches_doNotRecordHistory() =
        runTest(testDispatcher) {
            val places = FakePlacesRepository()
            val responses =
                listOf<suspend () -> WeatherForecast>(
                    { throw DomainFailureException(DomainError.Api(ApiErrorReason.NotFound)) },
                    { throw CancellationException() },
                    { emptyForecast() },
                )
            responses.forEach { response ->
                viewModel(FakeWeatherRepository(response), placesRepository = places).requestCityWeather("Kyiv")
                advanceUntilIdle()
            }
            assertTrue(places.searches.isEmpty())
        }

    @Test
    fun cancelledDialogSearch_doesNotRecordHistoryEvenAfterLateResponse() =
        runTest(testDispatcher) {
            val pending = CompletableDeferred<Unit>()
            val places = FakePlacesRepository()
            val viewModel =
                viewModel(
                    FakeWeatherRepository {
                        withContext(NonCancellable) { pending.await() }
                        successForecast()
                    },
                    placesRepository = places,
                )
            viewModel.openCitySearch()
            viewModel.updateCitySearchQuery("Kyiv")
            viewModel.submitCitySearch()
            runCurrent()
            viewModel.closeCitySearch()
            pending.complete(Unit)
            advanceUntilIdle()
            assertTrue(places.searches.isEmpty())
        }

    @Test
    fun supersededSearch_recordsOnlyLatestCity() =
        runTest(testDispatcher) {
            val pending = CompletableDeferred<Unit>()
            val places = FakePlacesRepository()
            var calls = 0
            val viewModel =
                viewModel(
                    FakeWeatherRepository {
                        if (calls++ == 0) withContext(NonCancellable) { pending.await() }
                        successForecast()
                    },
                    placesRepository = places,
                )
            viewModel.requestCityWeather("Old")
            runCurrent()
            viewModel.requestCityWeather("Latest")
            runCurrent()
            pending.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf("Latest"), places.searches.map { it.normalizedQuery })
            assertEquals(
                "Latest",
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
        }

    @Test
    fun selectingSavedCityOffline_usesCoordinatesPersistsSelectionAndDoesNotRecordHistory() =
        runTest(testDispatcher) {
            val places = FakePlacesRepository()
            val selected = FakeSelectedPlaceRepository()
            val weather = FakeWeatherRepository()
            val viewModel =
                viewModel(
                    weather,
                    geocodingRepository = FakeGeocodingRepository(found = false),
                    networkMonitor = FakeNetworkMonitor(false),
                    selectedPlaceRepository = selected,
                    placesRepository = places,
                )
            val city = SavedCity(1L, "Kyiv", "Saved Kyiv", LocationCoordinates(50.45, 30.52), 100L, true)
            viewModel.selectCity(city)
            advanceUntilIdle()
            assertEquals("50.45", weather.lastLat)
            assertEquals("30.52", weather.lastLon)
            assertEquals(city.toSelectedPlace(), selected.value)
            assertEquals(
                "Saved Kyiv",
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
            assertTrue(places.searches.isEmpty())
        }

    @Test
    fun failedSavedCitySelection_preservesPreviousSelectedPlaceAndRetryDoesNotRecordHistory() =
        runTest(testDispatcher) {
            val places = FakePlacesRepository()
            val selected = FakeSelectedPlaceRepository(SelectedPlace.CurrentLocation)
            var calls = 0
            val viewModel =
                viewModel(
                    FakeWeatherRepository {
                        if (calls++ == 0) throw DomainFailureException(DomainError.Network(NetworkErrorReason.Offline))
                        successForecast()
                    },
                    selectedPlaceRepository = selected,
                    placesRepository = places,
                )
            val city = SavedCity(1L, "Kyiv", "Kyiv", LocationCoordinates(50.45, 30.52), 100L, true)
            viewModel.selectCity(city)
            advanceUntilIdle()
            assertEquals(SelectedPlace.CurrentLocation, selected.value)
            assertNotNull(viewModel.uiState.value.error)
            viewModel.retryLastRequest()
            advanceUntilIdle()
            assertEquals(city.toSelectedPlace(), selected.value)
            assertTrue(places.searches.isEmpty())
        }

    @Test
    fun historyStorageFailure_preservesLoadedWeather() =
        runTest(testDispatcher) {
            val places = FakePlacesRepository().apply { mutationFailure = IllegalStateException("Storage unavailable") }
            val viewModel = viewModel(FakeWeatherRepository(), placesRepository = places)
            viewModel.requestCityWeather("Kyiv")
            advanceUntilIdle()
            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
            assertNotNull(viewModel.uiState.value.currentWeather)
            assertTrue(places.searches.isEmpty())
        }

    @Test
    fun cachedCoordinates_openOfflineWithoutPermissionGeocodingOrHistory() =
        runTest(testDispatcher) {
            val selected = FakeSelectedPlaceRepository(SelectedPlace.CurrentLocation)
            val places = FakePlacesRepository()
            val viewModel =
                viewModel(
                    FakeWeatherRepository { successForecast().copy(cityName = null) },
                    locationRepository = FakeLocationRepository(currentFailure = IllegalStateException("No GPS permission")),
                    geocodingRepository = FakeGeocodingRepository(found = false),
                    networkMonitor = FakeNetworkMonitor(false),
                    selectedPlaceRepository = selected,
                    placesRepository = places,
                )
            val entry =
                com.am24.imbrel.domain.model.CachedForecast(
                    "lat:1.0000|lon:2.0000",
                    successForecast().copy(cityName = null),
                    0,
                    com.am24.imbrel.domain.model.CacheStatus.Offline,
                )
            viewModel.selectCachedForecast(entry)
            advanceUntilIdle()
            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
            assertEquals(
                entry.key,
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
            assertTrue(selected.writes.isEmpty())
            assertTrue(places.searches.isEmpty())
        }

    @Test
    fun cachedKnownCity_persistsOnlySuccessfulSelectionWithoutHistory() =
        runTest(testDispatcher) {
            val selected = FakeSelectedPlaceRepository()
            val places = FakePlacesRepository()
            var fail = true
            val viewModel =
                viewModel(
                    FakeWeatherRepository {
                        if (fail) throw DomainFailureException(DomainError.Network(NetworkErrorReason.Offline))
                        successForecast()
                    },
                    selectedPlaceRepository = selected,
                    placesRepository = places,
                )
            val place = SelectedPlace.City("Kyiv", "Kyiv", LocationCoordinates(50.45, 30.52))
            val entry =
                com.am24.imbrel.domain.model.CachedForecast(
                    "city:kyiv",
                    successForecast(),
                    0,
                    com.am24.imbrel.domain.model.CacheStatus.Offline,
                    place,
                )
            viewModel.selectCachedForecast(entry)
            advanceUntilIdle()
            assertTrue(selected.writes.isEmpty())
            assertTrue(viewModel.uiState.value.error is WeatherUiError.Cached)
            fail = false
            viewModel.retryLastRequest()
            advanceUntilIdle()
            assertEquals(listOf(place), selected.writes)
            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
            assertTrue(places.searches.isEmpty())
        }

    @Test
    fun cacheWithOnlyCurrentWeather_opensSuccessfully() =
        runTest(testDispatcher) {
            val viewModel = viewModel(FakeWeatherRepository { emptyForecast() })
            viewModel.selectCachedForecast(
                com.am24.imbrel.domain.model.CachedForecast(
                    "city:test",
                    emptyForecast(),
                    0,
                    com.am24.imbrel.domain.model.CacheStatus.Fresh,
                ),
            )
            advanceUntilIdle()
            assertEquals(WeatherUiStatus.Success, viewModel.uiState.value.status)
            assertNotNull(viewModel.uiState.value.currentWeather)
            assertNotNull(viewModel.uiState.value.cachedForecast)
        }

    @Test
    fun cachedRequest_lateCompletionCannotOverwriteNewerSelection() =
        runTest(testDispatcher) {
            val pending = CompletableDeferred<WeatherForecast>()
            var count = 0
            val viewModel =
                viewModel(
                    FakeWeatherRepository {
                        if (count++ == 0) withContext(NonCancellable) { pending.await() } else successForecast().copy(cityName = "New")
                    },
                )
            val first =
                com.am24.imbrel.domain.model.CachedForecast(
                    "city:first",
                    successForecast(),
                    0,
                    com.am24.imbrel.domain.model.CacheStatus.Fresh,
                )
            viewModel.selectCachedForecast(first)
            runCurrent()
            viewModel.selectCachedForecast(first.copy(key = "city:second"))
            runCurrent()
            pending.complete(successForecast())
            advanceUntilIdle()
            assertEquals(
                "second",
                viewModel.uiState.value.currentWeather
                    ?.city,
            )
        }

    private class FakeSelectedPlaceRepository(
        var value: SelectedPlace? = null,
        private val read: (suspend () -> SelectedPlace?)? = null,
        private val writeFailure: Exception? = null,
    ) : SelectedPlaceRepository {
        var readCount = 0
        val writes = mutableListOf<SelectedPlace>()

        override suspend fun getSelectedPlace(): SelectedPlace? {
            readCount++
            return read?.invoke() ?: value
        }

        override suspend fun saveSelectedPlace(place: SelectedPlace) {
            writeFailure?.let { throw it }
            writes.add(place)
            value = place
        }

        override suspend fun clearSelectedPlace() {
            value = null
        }
    }

    private fun viewModel(
        repository: WeatherRepository,
        locationRepository: LocationRepository = FakeLocationRepository(),
        geocodingRepository: GeocodingRepository = FakeGeocodingRepository(),
        networkMonitor: FakeNetworkMonitor = FakeNetworkMonitor(),
        selectedPlaceRepository: SelectedPlaceRepository = FakeSelectedPlaceRepository(),
        placesRepository: FakePlacesRepository = FakePlacesRepository(),
    ) = MainViewModel(
        GetCurrentWeatherUseCase(repository),
        GetCurrentLocationUseCase(locationRepository, networkMonitor),
        SearchCityWeatherUseCase(repository, geocodingRepository, networkMonitor),
        WeatherPresentationMapper(
            conditionLocalizer = { _, fallback -> fallback },
            clock = Clock.fixed(Instant.parse("2026-07-05T12:34:00Z"), ZoneOffset.UTC),
        ),
        networkMonitor,
        selectedPlaceRepository,
        RecordCitySearchUseCase(placesRepository),
        com.am24.imbrel.domain.usecase
            .OpenCachedForecastUseCase(repository),
    )

    private class FakeLocationRepository(
        private val coordinates: UserLocation = UserLocation(50.45, 30.52, "Kyiv"),
        private val currentFailure: Exception? = null,
        private val savedLocation: UserLocation? = null,
    ) : LocationRepository {
        override suspend fun getCurrentLocation(): UserLocation {
            currentFailure?.let { throw it }
            return coordinates
        }

        override suspend fun getLastSavedLocation(): UserLocation? = savedLocation

        override suspend fun saveLastLocation(location: UserLocation) = Unit
    }

    private class FakeGeocodingRepository(
        private val localizedName: String? = null,
        private val found: Boolean = true,
    ) : GeocodingRepository {
        override suspend fun searchLocation(query: String): GeocodedLocation? =
            if (found) GeocodedLocation(50.45, 30.52, localizedName ?: query) else null

        override suspend fun resolvePlaceName(
            latitude: Double,
            longitude: Double,
        ): String? = null
    }

    private class FakeWeatherRepository(
        private val response: suspend () -> WeatherForecast = { successForecast() },
    ) : WeatherRepository {
        var lastCity: String? = null
            private set
        var lastLat: String? = null
            private set
        var lastLon: String? = null
            private set

        override suspend fun openCachedForecast(key: String) =
            com.am24.imbrel.domain.model.CachedForecast(
                key,
                response(),
                0,
                com.am24.imbrel.domain.model.CacheStatus.Offline,
            )

        override suspend fun getWeatherData(
            lat: String?,
            lon: String?,
            city: String?,
        ): WeatherForecast {
            lastLat = lat
            lastLon = lon
            lastCity = city
            return response()
        }
    }

    companion object {
        private fun successForecast() =
            WeatherForecast(
                cityName = "Kyiv",
                current = CurrentWeather("Clear", 21.4, 1),
                hourly = listOf(HourlyWeather("2026-07-05T12:00:00", "Clear", 21.4, 1)),
                daily = listOf(DailyWeather("2026-07-05", "Clear", 1, 18.0, 24.0)),
            )

        private fun emptyForecast() =
            WeatherForecast(
                cityName = "Kyiv",
                current = CurrentWeather("Clear", 21.4, 1),
                hourly = emptyList(),
                daily = emptyList(),
            )
    }
}
