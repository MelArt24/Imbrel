package com.am24.imbrel

import com.am24.imbrel.data.cache.CachedForecastIndex
import com.am24.imbrel.data.cache.WeatherCachePolicy
import com.am24.imbrel.data.local.CachedWeather
import com.am24.imbrel.data.local.SavedCityEntity
import com.am24.imbrel.domain.model.CacheStatus
import com.am24.imbrel.domain.model.CurrentWeather
import com.am24.imbrel.domain.model.WeatherForecast
import org.junit.Assert.assertEquals
import org.junit.Test

class CachedForecastIndexTest {
    private val policy = WeatherCachePolicy()
    private val index = CachedForecastIndex(policy)
    private val now = 100_000_000L
    private val forecast = WeatherForecast(null, CurrentWeather("Clear", 20.0, 1), emptyList(), emptyList())

    @Test
    fun ttlBoundaries_areInclusiveAndFutureDatesExpired() {
        assertEquals(CacheStatus.Fresh, index.status(now - policy.ttlMillis, now))
        assertEquals(CacheStatus.Offline, index.status(now - policy.ttlMillis - 1, now))
        assertEquals(CacheStatus.Offline, index.status(now - policy.maxOfflineAgeMillis, now))
        assertEquals(CacheStatus.Expired, index.status(now - policy.maxOfflineAgeMillis - 1, now))
        assertEquals(CacheStatus.Expired, index.status(now + 1, now))
    }

    @Test
    fun cityAndCoordinateAliases_useNewestForecastAndKnownPlace() {
        val result =
            index.map(
                mapOf(
                    "city:kyiv" to CachedWeather(forecast, now - 1),
                    "lat:50.4500|lon:30.5200" to CachedWeather(forecast, now),
                ),
                listOf(city("Kyiv", "Kyiv", 50.45, 30.52)),
                now,
            )
        assertEquals(1, result.size)
        assertEquals(now, result.single().updatedAtMillis)
        assertEquals("Kyiv", result.single().label)
    }

    @Test
    fun identicalNamesAndPayloads_atDifferentCoordinatesRemainSeparate() {
        val result =
            index.map(
                mapOf(
                    "lat:1.0000|lon:2.0000" to CachedWeather(forecast, now),
                    "lat:3.0000|lon:4.0000" to CachedWeather(forecast, now),
                ),
                listOf(city("Town A", "Town", 1.0, 2.0), city("Town B", "Town", 3.0, 4.0)),
                now,
            )
        assertEquals(2, result.size)
        assertEquals(listOf("Town", "Town"), result.map { it.label })
    }

    @Test
    fun missingIdentity_preservesCoordinatesAndExpiredEntries() {
        val key = "lat:1.0000|lon:2.0000"
        val result = index.map(mapOf(key to CachedWeather(forecast, 0)), emptyList(), now)
        assertEquals(key, result.single().label)
        assertEquals(null, result.single().place)
        assertEquals(CacheStatus.Expired, result.single().status)
        assertEquals(emptyList<Any>(), index.map(emptyMap(), emptyList(), now))
    }

    private fun city(
        query: String,
        name: String,
        lat: Double,
        lon: Double,
    ) = SavedCityEntity(
        queryKey = query.lowercase(),
        normalizedQuery = query,
        displayName = name,
        latitude = lat,
        longitude = lon,
        lastSearchedAtMillis = now,
    )
}
