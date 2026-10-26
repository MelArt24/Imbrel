package com.am24.imbrel.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.am24.imbrel.domain.error.ApiErrorReason
import com.am24.imbrel.domain.error.DomainError
import com.am24.imbrel.domain.error.DomainFailureException
import com.am24.imbrel.domain.error.LocationErrorReason
import com.am24.imbrel.domain.model.CachedForecast
import com.am24.imbrel.domain.model.CityWeatherResult
import com.am24.imbrel.domain.model.SavedCity
import com.am24.imbrel.domain.model.SelectedPlace
import com.am24.imbrel.domain.network.NetworkMonitor
import com.am24.imbrel.domain.repository.SelectedPlaceRepository
import com.am24.imbrel.domain.usecase.GetCurrentLocationUseCase
import com.am24.imbrel.domain.usecase.GetCurrentWeatherUseCase
import com.am24.imbrel.domain.usecase.OpenCachedForecastUseCase
import com.am24.imbrel.domain.usecase.RecordCitySearchUseCase
import com.am24.imbrel.domain.usecase.SearchCityWeatherUseCase
import com.am24.imbrel.presentation.mapper.WeatherPresentationMapper
import com.am24.imbrel.presentation.model.WeatherModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Weather and permission commands share one screen state and request generation.
@Suppress("TooManyFunctions", "LongParameterList")
class MainViewModel(
    private val getCurrentWeatherUseCase: GetCurrentWeatherUseCase,
    private val getCurrentLocationUseCase: GetCurrentLocationUseCase,
    private val searchCityWeatherUseCase: SearchCityWeatherUseCase,
    private val weatherPresentationMapper: WeatherPresentationMapper,
    networkMonitor: NetworkMonitor,
    private val selectedPlaceRepository: SelectedPlaceRepository,
    private val recordCitySearchUseCase: RecordCitySearchUseCase,
    private val openCachedForecast: OpenCachedForecastUseCase? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _citySearchState = MutableStateFlow(CitySearchUiState())
    val citySearchState: StateFlow<CitySearchUiState> = _citySearchState.asStateFlow()
    private var weatherBeforeCitySearch: WeatherUiState? = null
    private var requestBeforeCitySearch: Request? = null

    private val _isOnline = MutableStateFlow(networkMonitor.isOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _events = Channel<WeatherUiEvent>(Channel.BUFFERED)
    val events: Flow<WeatherUiEvent> = _events.receiveAsFlow()

    private val _locationRequests = Channel<LocationRequest>(Channel.BUFFERED)
    val locationRequests: Flow<LocationRequest> = _locationRequests.receiveAsFlow()
    private val selectionMutex = Mutex()
    private var startupInitialized = false
    private var weatherRequestJob: Job? = null

    // Cancellation is cooperative, so every state mutation also checks this generation. A late
    // completion from an older request therefore cannot overwrite the latest request's UI state.
    private var requestId = 0L
    private var lastRequest: Request? = null

    init {
        viewModelScope.launch {
            networkMonitor.observeConnectivity().collect(_isOnline)
        }
    }

    fun initializeStartup() {
        if (startupInitialized) return
        startupInitialized = true
        if (requestId != 0L) return
        val startupRequestId = requestId
        viewModelScope.launch {
            val place =
                try {
                    selectedPlaceRepository.getSelectedPlace()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // A storage failure falls back to the first-launch location flow.
                    null
                }
            currentCoroutineContext().ensureActive()
            if (requestId != startupRequestId) return@launch
            when (place) {
                is SelectedPlace.City -> requestCityWeather(place.normalizedQuery, place)
                SelectedPlace.CurrentLocation, null -> requestLocation()
            }
        }
    }

    fun isCurrentRequest(id: Long): Boolean = id == requestId

    fun requestLocation() {
        lastRequest = Request.CurrentLocation(true, LocationErrorReason.PermissionDenied)
        val id = beginRequest(Operation.Location)
        _locationRequests.trySend(LocationRequest(id))
    }

    fun requestSavedLocationOrPermission(id: Long) {
        if (!isCurrentRequest(id)) return
        weatherRequestJob =
            viewModelScope.launch {
                val saved = getCurrentLocationUseCase.getSavedLocation()
                currentCoroutineContext().ensureActive()
                if (!isCurrentRequest(id)) return@launch
                if (saved == null) {
                    _locationRequests.send(LocationRequest(id, requestPermission = true))
                } else {
                    requestCurrentLocationWeather(allowCurrentLocation = false, expectedRequestId = id)
                }
            }
    }

    fun openCitySearch() {
        _citySearchState.value = CitySearchUiState()
    }

    fun updateCitySearchQuery(query: String) {
        if (_citySearchState.value.isLoading) return
        _citySearchState.update { it.copy(query = query, isQueryBlank = false, error = null) }
    }

    fun submitCitySearch(closeOnSubmit: Boolean = false): Boolean {
        val state = _citySearchState.value
        if (state.isLoading || state.successId != null) return false
        return if (state.query.isBlank()) {
            _citySearchState.update { it.copy(isQueryBlank = true, error = null) }
            false
        } else {
            if (closeOnSubmit) {
                closeCitySearch()
                requestCityWeather(state.query)
            } else {
                weatherBeforeCitySearch = _uiState.value
                requestBeforeCitySearch = lastRequest
                requestCityWeather(state.query, restoredPlace = null, fromSearchScreen = true)
            }
            true
        }
    }

    fun closeCitySearch() {
        val state = _citySearchState.value
        if (state.isLoading && state.requestId == requestId) {
            weatherRequestJob?.cancel()
            restoreWeatherBeforeSearch()
            ++requestId
        }
        _citySearchState.value = CitySearchUiState()
        weatherBeforeCitySearch = null
        requestBeforeCitySearch = null
    }

    fun consumeCitySearchSuccess(id: Long): Boolean {
        if (_citySearchState.value.successId != id || !isCurrentRequest(id)) return false
        closeCitySearch()
        return true
    }

    private fun restoreWeatherBeforeSearch() {
        lastRequest = requestBeforeCitySearch
        weatherBeforeCitySearch?.let { previous ->
            _uiState.value =
                previous.copy(
                    status = if (previous.status == WeatherUiStatus.Loading) WeatherUiStatus.Initial else previous.status,
                    isRefreshing = false,
                    isCitySearchLoading = false,
                    isLocationLoading = false,
                    isWeatherLoading = false,
                )
        }
    }

    fun requestCityWeather(city: String) = requestCityWeather(city, restoredPlace = null)

    fun selectCachedForecast(entry: CachedForecast) {
        lastRequest = Request.Cached(entry)
        val id = beginRequest(Operation.CitySearch)
        weatherRequestJob =
            viewModelScope.launch {
                try {
                    val cached = requireNotNull(openCachedForecast)(entry.key)
                    val weather = weatherPresentationMapper(cached.forecast, entry.label, includeCurrentWithoutDaily = true)
                    currentCoroutineContext().ensureActive()
                    if (!isCurrentRequest(id)) return@launch
                    if (weather.current != null || weather.daily.isNotEmpty()) {
                        entry.place?.let { saveSelection(id, it) }
                    }
                    showWeather(id, weather.current, weather.daily)
                    if (isCurrentRequest(id)) _uiState.update { it.copy(cachedForecast = cached) }
                } catch (cancellation: CancellationException) {
                    clearCancelledRequest(id)
                    throw cancellation
                } catch (_: Exception) {
                    showError(id, WeatherUiError.Cached())
                } finally {
                    finishRequest(id)
                }
            }
    }

    fun selectCity(city: SavedCity) {
        requestCityWeather(city.normalizedQuery, city.toSelectedPlace(), persistSelection = true)
    }

    private fun requestCityWeather(
        city: String,
        restoredPlace: SelectedPlace.City?,
        fromSearchScreen: Boolean = false,
        recordHistory: Boolean = restoredPlace == null,
        persistSelection: Boolean = restoredPlace == null,
    ) {
        lastRequest = Request.City(city, restoredPlace, recordHistory, persistSelection)
        val id = beginRequest(Operation.CitySearch)
        if (fromSearchScreen) {
            _citySearchState.update { it.copy(isLoading = true, isQueryBlank = false, error = null, requestId = id, successId = null) }
        }
        weatherRequestJob =
            viewModelScope.launch {
                try {
                    val result =
                        if (restoredPlace == null) searchCityWeatherUseCase(city) else searchCityWeatherUseCase(restoredPlace)
                    completeCitySearch(id, result, persistSelection, fromSearchScreen, recordHistory)
                } catch (cancellation: CancellationException) {
                    clearCancelledRequest(id)
                    throw cancellation
                } catch (failure: DomainFailureException) {
                    showCitySearchError(id, WeatherUiError.CitySearch(failure.error), fromSearchScreen)
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException")
                    e: Exception,
                ) {
                    // The presentation boundary keeps unexpected failures from escaping without a
                    // user-visible state; known domain failures are handled above.
                    showCitySearchError(id, WeatherUiError.CitySearch(DomainError.Unknown), fromSearchScreen)
                } finally {
                    if (fromSearchScreen && isCurrentRequest(id)) {
                        _citySearchState.update { it.copy(isLoading = false) }
                    }
                    finishRequest(id)
                }
            }
    }

    private suspend fun completeCitySearch(
        id: Long,
        result: CityWeatherResult,
        persistSelection: Boolean,
        fromSearchScreen: Boolean,
        recordHistory: Boolean,
    ) {
        val weather = weatherPresentationMapper(result.forecast, result.city)
        currentCoroutineContext().ensureActive()
        if (!isCurrentRequest(id)) return
        val hasForecast = weather.current != null || weather.daily.isNotEmpty()
        if (fromSearchScreen && !hasForecast) {
            throw DomainFailureException(DomainError.Api(ApiErrorReason.NotFound))
        }
        if (persistSelection && hasForecast) {
            saveSelection(id, result.toSelectedPlace())
        }
        if (recordHistory && hasForecast) {
            saveSearchHistory(id, result.toSelectedPlace())
        }
        showWeather(id, weather.current, weather.daily)
        if (fromSearchScreen && isCurrentRequest(id)) {
            _citySearchState.update { it.copy(isLoading = false, successId = id) }
        }
    }

    private suspend fun showCitySearchError(
        id: Long,
        error: WeatherUiError.CitySearch,
        fromSearchScreen: Boolean,
    ) {
        if (!isCurrentRequest(id)) return
        if (fromSearchScreen) {
            restoreWeatherBeforeSearch()
            _citySearchState.update { it.copy(isLoading = false, error = error) }
        } else {
            showError(id, error)
        }
    }

    fun requestCurrentLocationWeather(
        allowCurrentLocation: Boolean = true,
        expectedRequestId: Long? = null,
        missingLocationReason: LocationErrorReason = LocationErrorReason.PermissionDenied,
    ) {
        if (expectedRequestId != null && !isCurrentRequest(expectedRequestId)) return
        lastRequest = Request.CurrentLocation(allowCurrentLocation, missingLocationReason)
        val id = expectedRequestId ?: beginRequest(Operation.Location)
        weatherRequestJob =
            viewModelScope.launch {
                val location =
                    try {
                        getCurrentLocationUseCase(allowCurrentLocation, missingLocationReason)
                    } catch (cancellation: CancellationException) {
                        clearCancelledRequest(id)
                        throw cancellation
                    } catch (failure: DomainFailureException) {
                        showError(id, WeatherUiError.Location(failure.error))
                        finishRequest(id)
                        return@launch
                    } catch (
                        @Suppress("TooGenericExceptionCaught", "SwallowedException")
                        locationFailure: Exception,
                    ) {
                        // The presentation boundary maps unexpected failures after known domain and
                        // cancellation cases have been handled above.
                        showError(id, WeatherUiError.Location(DomainError.Unknown))
                        finishRequest(id)
                        return@launch
                    }

                try {
                    startWeatherLoading(id)
                    val response =
                        getCurrentWeatherUseCase(
                            location.latitude.toString(),
                            location.longitude.toString(),
                        )
                    val weather = weatherPresentationMapper(response, location.placeName)
                    currentCoroutineContext().ensureActive()
                    if (weather.current != null || weather.daily.isNotEmpty()) {
                        saveSelection(id, SelectedPlace.CurrentLocation)
                    }
                    showWeather(id, weather.current, weather.daily)
                } catch (cancellation: CancellationException) {
                    clearCancelledRequest(id)
                    throw cancellation
                } catch (failure: DomainFailureException) {
                    showError(id, WeatherUiError.Weather(failure.error))
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException")
                    weatherFailure: Exception,
                ) {
                    // The presentation boundary maps unexpected failures after known domain and
                    // cancellation cases have been handled above.
                    showError(id, WeatherUiError.Weather(DomainError.Unknown))
                } finally {
                    finishRequest(id)
                }
            }
    }

    fun retryLastRequest() {
        when (val request = lastRequest) {
            is Request.Cached -> selectCachedForecast(request.entry)
            is Request.City ->
                requestCityWeather(
                    request.city,
                    request.restoredPlace,
                    recordHistory = request.recordHistory,
                    persistSelection = request.persistSelection,
                )
            is Request.CurrentLocation ->
                requestCurrentLocationWeather(
                    allowCurrentLocation = request.allowCurrentLocation,
                    missingLocationReason = request.missingLocationReason,
                )
            null -> Unit
        }
    }

    private suspend fun saveSelection(
        id: Long,
        place: SelectedPlace,
    ) {
        selectionMutex.withLock {
            currentCoroutineContext().ensureActive()
            if (!isCurrentRequest(id)) return
            try {
                selectedPlaceRepository.saveSelectedPlace(place)
                currentCoroutineContext().ensureActive()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Persistence is best-effort; a valid forecast remains visible.
            }
        }
    }

    private suspend fun saveSearchHistory(
        id: Long,
        city: SelectedPlace.City,
    ) {
        selectionMutex.withLock {
            currentCoroutineContext().ensureActive()
            if (!isCurrentRequest(id)) return
            try {
                recordCitySearchUseCase(city)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // A storage failure must not discard a successfully loaded forecast.
                if (isCurrentRequest(id)) {
                    _events.trySend(WeatherUiEvent.ShowError(WeatherUiError.CitySearch(DomainError.Unknown)))
                }
            }
        }
    }

    private fun beginRequest(operation: Operation): Long {
        weatherRequestJob?.cancel()
        if (_citySearchState.value.requestId != null) {
            _citySearchState.update { it.copy(isLoading = false, requestId = null, successId = null) }
        }
        val id = ++requestId
        _uiState.update { state ->
            state.copy(
                status = if (state.hasWeather) state.status else WeatherUiStatus.Loading,
                error = null,
                isRefreshing = state.hasWeather,
                isCitySearchLoading = operation == Operation.CitySearch,
                isLocationLoading = operation == Operation.Location,
                isWeatherLoading = false,
            )
        }
        return id
    }

    private fun startWeatherLoading(id: Long) {
        if (id != requestId) return
        _uiState.update {
            it.copy(isLocationLoading = false, isWeatherLoading = true)
        }
    }

    private fun showWeather(
        id: Long,
        current: WeatherModel?,
        daily: List<WeatherModel>,
    ) {
        if (id != requestId) return
        val status =
            if (current == null && daily.isEmpty()) {
                WeatherUiStatus.Empty
            } else {
                WeatherUiStatus.Success
            }
        _uiState.update {
            it.copy(
                status = status,
                cachedForecast = null,
                currentWeather = current,
                dailyWeather = daily,
                error = null,
                isRefreshing = false,
                isCitySearchLoading = false,
                isLocationLoading = false,
                isWeatherLoading = false,
            )
        }
    }

    private suspend fun showError(
        id: Long,
        error: WeatherUiError,
    ) {
        if (id != requestId) return
        val hasWeather = _uiState.value.hasWeather
        _uiState.update { state ->
            if (state.hasWeather) {
                state.copy(error = null, isRefreshing = false)
            } else {
                state.copy(
                    status = WeatherUiStatus.Error,
                    error = error,
                    isRefreshing = false,
                )
            }
        }
        if (hasWeather) {
            _events.send(WeatherUiEvent.ShowError(error))
        }
    }

    private fun clearCancelledRequest(id: Long) {
        if (id != requestId) return
        _uiState.update { state ->
            state.copy(
                status =
                    if (state.status == WeatherUiStatus.Loading) {
                        WeatherUiStatus.Initial
                    } else {
                        state.status
                    },
                error = null,
                isRefreshing = false,
                isCitySearchLoading = false,
                isLocationLoading = false,
                isWeatherLoading = false,
            )
        }
    }

    private fun finishRequest(id: Long) {
        if (id != requestId) return
        _uiState.update {
            it.copy(
                isRefreshing = false,
                isCitySearchLoading = false,
                isLocationLoading = false,
                isWeatherLoading = false,
            )
        }
    }

    private enum class Operation {
        CitySearch,
        Location,
    }

    private sealed interface Request {
        data class Cached(
            val entry: CachedForecast,
        ) : Request

        data class City(
            val city: String,
            val restoredPlace: SelectedPlace.City?,
            val recordHistory: Boolean,
            val persistSelection: Boolean,
        ) : Request

        data class CurrentLocation(
            val allowCurrentLocation: Boolean,
            val missingLocationReason: LocationErrorReason,
        ) : Request
    }
}

data class LocationRequest(
    val id: Long,
    val requestPermission: Boolean = false,
)
