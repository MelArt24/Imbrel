package com.am24.imbrel.domain.usecase

import com.am24.imbrel.domain.repository.WeatherRepository

class ObserveCachedForecastsUseCase(
    private val repository: WeatherRepository,
) {
    operator fun invoke() = repository.observeCachedForecasts()
}

class OpenCachedForecastUseCase(
    private val repository: WeatherRepository,
) {
    suspend operator fun invoke(key: String) = repository.openCachedForecast(key)
}
