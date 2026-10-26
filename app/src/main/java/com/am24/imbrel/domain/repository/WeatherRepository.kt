package com.am24.imbrel.domain.repository

import com.am24.imbrel.domain.model.CachedForecast
import com.am24.imbrel.domain.model.WeatherForecast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface WeatherRepository {
    fun observeCachedForecasts(): Flow<List<CachedForecast>> = flowOf(emptyList())

    suspend fun openCachedForecast(key: String): CachedForecast = error("Cache unavailable")

    suspend fun getWeatherData(
        lat: String? = null,
        lon: String? = null,
        city: String? = null,
    ): WeatherForecast
}
