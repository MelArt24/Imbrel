package com.am24.imbrel.domain.usecase

import com.am24.imbrel.domain.error.ApiErrorReason
import com.am24.imbrel.domain.error.DomainError
import com.am24.imbrel.domain.error.DomainFailureException
import com.am24.imbrel.domain.model.CityWeatherResult
import com.am24.imbrel.domain.model.LocationCoordinates
import com.am24.imbrel.domain.model.SelectedPlace
import com.am24.imbrel.domain.network.NetworkMonitor
import com.am24.imbrel.domain.network.isOnlineOrDomainFailure
import com.am24.imbrel.domain.repository.GeocodingRepository
import com.am24.imbrel.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SearchCityWeatherUseCase(
    private val weatherRepository: WeatherRepository,
    private val geocodingRepository: GeocodingRepository,
    private val networkMonitor: NetworkMonitor =
        object : NetworkMonitor {
            override fun isOnline(): Boolean = true

            override fun observeConnectivity(): Flow<Boolean> = flowOf(true)
        },
) {
    suspend operator fun invoke(place: SelectedPlace.City): CityWeatherResult {
        val coordinates = place.coordinates ?: return invoke(place.normalizedQuery)
        return CityWeatherResult(
            forecast =
                weatherRepository.getWeatherData(
                    lat = coordinates.latitude.toString(),
                    lon = coordinates.longitude.toString(),
                    city = place.normalizedQuery,
                ),
            city = place.displayName,
            normalizedQuery = place.normalizedQuery,
            coordinates = coordinates,
        )
    }

    suspend operator fun invoke(city: String): CityWeatherResult {
        val normalizedQuery = SelectedPlace.City.normalizeQuery(city)
        if (normalizedQuery.isEmpty()) {
            throw DomainFailureException(
                DomainError.Api(ApiErrorReason.RequestFailed),
            )
        }

        if (!networkMonitor.isOnlineOrDomainFailure()) {
            return CityWeatherResult(
                forecast = weatherRepository.getWeatherData(city = normalizedQuery),
                city = normalizedQuery,
                normalizedQuery = normalizedQuery,
            )
        }

        val location =
            geocodingRepository.searchLocation(normalizedQuery)
                ?: throw DomainFailureException(DomainError.Api(ApiErrorReason.NotFound))
        val forecast =
            weatherRepository.getWeatherData(
                lat = location.latitude.toString(),
                lon = location.longitude.toString(),
                city = normalizedQuery,
            )
        return CityWeatherResult(
            forecast = forecast,
            normalizedQuery = normalizedQuery,
            coordinates = LocationCoordinates(location.latitude, location.longitude),
            city =
                location.localizedName?.takeIf { it.isNotBlank() }
                    ?: normalizedQuery,
        )
    }
}
