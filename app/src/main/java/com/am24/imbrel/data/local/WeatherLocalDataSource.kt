package com.am24.imbrel.data.local

import com.am24.imbrel.domain.model.WeatherForecast
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class CachedWeather(
    val forecast: WeatherForecast,
    val cachedAtMillis: Long,
)

interface WeatherLocalDataSource {
    suspend fun clearWeather() {
        error("Cache clearing unavailable")
    }

    fun observeWeather(): Flow<Map<String, CachedWeather>> = flowOf(emptyMap())

    suspend fun getWeather(cacheKey: String): CachedWeather?

    suspend fun saveWeather(
        cacheKey: String,
        forecast: WeatherForecast,
        cachedAtMillis: Long,
    )
}

class RoomWeatherLocalDataSource(
    private val weatherDao: WeatherDao,
) : WeatherLocalDataSource {
    override suspend fun clearWeather() = weatherDao.clearWeather()

    override fun observeWeather() =
        weatherDao.observeWeather().map { rows ->
            rows.associate { it.weather.cacheKey to it.toCachedWeather() }
        }

    override suspend fun getWeather(cacheKey: String): CachedWeather? = weatherDao.getWeather(cacheKey)?.toCachedWeather()

    override suspend fun saveWeather(
        cacheKey: String,
        forecast: WeatherForecast,
        cachedAtMillis: Long,
    ) {
        weatherDao.replaceWeather(forecast.toCachedAggregate(cacheKey, cachedAtMillis))
    }
}
