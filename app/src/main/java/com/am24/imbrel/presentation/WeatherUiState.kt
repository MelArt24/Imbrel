package com.am24.imbrel.presentation

import com.am24.imbrel.R
import com.am24.imbrel.domain.error.ApiErrorReason
import com.am24.imbrel.domain.error.DomainError
import com.am24.imbrel.domain.error.LocationErrorReason
import com.am24.imbrel.domain.error.NetworkErrorReason
import com.am24.imbrel.presentation.model.WeatherModel

data class WeatherUiState(
    val status: WeatherUiStatus = WeatherUiStatus.Initial,
    val currentWeather: WeatherModel? = null,
    val dailyWeather: List<WeatherModel> = emptyList(),
    val error: WeatherUiError? = null,
    val isRefreshing: Boolean = false,
    val isCitySearchLoading: Boolean = false,
    val isLocationLoading: Boolean = false,
    val isWeatherLoading: Boolean = false,
) {
    val isLoading: Boolean
        get() = status == WeatherUiStatus.Loading

    val hasWeather: Boolean
        get() = currentWeather != null || dailyWeather.isNotEmpty()
}

enum class WeatherUiStatus {
    Initial,
    Loading,
    Success,
    Empty,
    Error,
}

sealed interface WeatherUiError {
    fun messageResource(): Int =
        when (val error = cause) {
            is DomainError.Network ->
                when (error.reason) {
                    NetworkErrorReason.Offline -> R.string.offline_error
                    NetworkErrorReason.Timeout -> R.string.timeout_error
                    NetworkErrorReason.ConnectionFailed -> R.string.connection_error
                }
            is DomainError.Api ->
                when (error.reason) {
                    ApiErrorReason.Unauthorized -> R.string.api_access_error
                    ApiErrorReason.NotFound ->
                        if (this is WeatherUiError.CitySearch) {
                            R.string.city_not_found
                        } else {
                            R.string.weather_not_found
                        }
                    ApiErrorReason.RateLimited -> R.string.rate_limit_error
                    ApiErrorReason.InvalidResponse -> R.string.invalid_response_error
                    ApiErrorReason.ServerError -> R.string.server_error
                    ApiErrorReason.RequestFailed -> R.string.weather_error
                }
            is DomainError.Location ->
                when (error.reason) {
                    LocationErrorReason.PermissionDenied -> R.string.location_permission_denied
                    LocationErrorReason.Unavailable -> R.string.location_unavailable_error
                    LocationErrorReason.ResolutionFailed -> R.string.location_resolution_error
                }
            DomainError.Unknown -> R.string.unknown_error
        }

    val cause: DomainError

    data class CitySearch(
        override val cause: DomainError,
    ) : WeatherUiError

    data class Location(
        override val cause: DomainError,
    ) : WeatherUiError

    data class Weather(
        override val cause: DomainError,
    ) : WeatherUiError
}

sealed interface WeatherUiEvent {
    data class ShowError(
        val error: WeatherUiError,
    ) : WeatherUiEvent
}
