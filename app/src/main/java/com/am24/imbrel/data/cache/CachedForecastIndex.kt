package com.am24.imbrel.data.cache

import com.am24.imbrel.data.local.CachedWeather
import com.am24.imbrel.data.local.SavedCityEntity
import com.am24.imbrel.domain.model.CacheStatus
import com.am24.imbrel.domain.model.CachedForecast
import com.am24.imbrel.domain.model.SelectedPlace

internal class CachedForecastIndex(
    private val cachePolicy: WeatherCachePolicy,
) {
    fun status(
        at: Long,
        now: Long,
    ) = when {
        !cachePolicy.isUsableOffline(at, now) -> CacheStatus.Expired
        cachePolicy.isFresh(at, now) -> CacheStatus.Fresh
        else -> CacheStatus.Offline
    }

    fun map(
        cache: Map<String, CachedWeather>,
        places: List<SavedCityEntity>,
        now: Long,
    ): List<CachedForecast> {
        val aliases = mutableMapOf<String, String>()
        val known = mutableMapOf<String, SelectedPlace.City>()
        places.forEach { city ->
            val cityKey = WeatherCacheKeyFactory.create(null, null, city.normalizedQuery)
            val place = SelectedPlace.City(city.normalizedQuery, city.displayName, city.toDomain().coordinates)
            known[cityKey] = place
            if (city.latitude != null && city.longitude != null) {
                val coordinateKey = WeatherCacheKeyFactory.create(city.latitude.toString(), city.longitude.toString(), null)
                aliases[cityKey] = coordinateKey
                known[coordinateKey] = place
            }
        }
        return cache.entries
            .groupBy { aliases[it.key] ?: it.key }
            .values
            .map { entries ->
                val entry = entries.maxBy { it.value.cachedAtMillis }
                CachedForecast(
                    entry.key,
                    entry.value.forecast,
                    entry.value.cachedAtMillis,
                    status(entry.value.cachedAtMillis, now),
                    known[entry.key],
                )
            }.sortedWith(compareByDescending<CachedForecast> { it.updatedAtMillis }.thenBy { it.key })
    }
}
