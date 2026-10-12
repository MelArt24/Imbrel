package com.am24.imbrel.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.am24.imbrel.domain.error.DomainError
import com.am24.imbrel.domain.error.DomainFailureException
import com.am24.imbrel.domain.network.NetworkMonitor
import com.am24.imbrel.domain.usecase.GetCurrentLocationUseCase
import com.am24.imbrel.domain.usecase.GetCurrentWeatherUseCase
import com.am24.imbrel.domain.usecase.SearchCityWeatherUseCase
import com.am24.imbrel.presentation.mapper.WeatherPresentationMapper
import com.am24.imbrel.presentation.model.WeatherModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val getCurrentWeatherUseCase: GetCurrentWeatherUseCase,
    private val getCurrentLocationUseCase: GetCurrentLocationUseCase,
    private val searchCityWeatherUseCase: SearchCityWeatherUseCase,
    private val weatherPresentationMapper: WeatherPresentationMapper,
    networkMonitor: NetworkMonitor,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _isOnline = MutableStateFlow(networkMonitor.isOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _events = Channel<WeatherUiEvent>(Channel.BUFFERED)
    val events: Flow<WeatherUiEvent> = _events.receiveAsFlow()

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

    fun requestCityWeather(city: String) {
        lastRequest = Request.City(city)
        val id = beginRequest(Operation.CitySearch)
        weatherRequestJob =
            viewModelScope.launch {
                try {
                    val result = searchCityWeatherUseCase(city)
                    val weather = weatherPresentationMapper(result.forecast, result.city)
                    showWeather(id, weather.current, weather.daily)
                } catch (cancellation: CancellationException) {
                    clearCancelledRequest(id)
                    throw cancellation
                } catch (failure: DomainFailureException) {
                    showError(id, WeatherUiError.CitySearch(failure.error))
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException")
                    e: Exception,
                ) {
                    // The presentation boundary keeps unexpected failures from escaping without a
                    // user-visible state; known domain failures are handled above.
                    showError(id, WeatherUiError.CitySearch(DomainError.Unknown))
                } finally {
                    finishRequest(id)
                }
            }
    }

    fun requestCurrentLocationWeather() {
        lastRequest = Request.CurrentLocation
        val id = beginRequest(Operation.Location)
        weatherRequestJob =
            viewModelScope.launch {
                val location =
                    try {
                        getCurrentLocationUseCase()
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
            is Request.City -> requestCityWeather(request.city)
            Request.CurrentLocation -> requestCurrentLocationWeather()
            null -> Unit
        }
    }

    private fun beginRequest(operation: Operation): Long {
        weatherRequestJob?.cancel()
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
        data class City(
            val city: String,
        ) : Request

        data object CurrentLocation : Request
    }
}
