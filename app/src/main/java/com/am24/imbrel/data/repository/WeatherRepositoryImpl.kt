package com.am24.imbrel.data.repository

import com.am24.imbrel.data.cache.CachedForecastIndex
import com.am24.imbrel.data.cache.TimeProvider
import com.am24.imbrel.data.cache.WeatherCacheKeyFactory
import com.am24.imbrel.data.cache.WeatherCachePolicy
import com.am24.imbrel.data.error.toWeatherDomainError
import com.am24.imbrel.data.local.PlacesDao
import com.am24.imbrel.data.local.WeatherLocalDataSource
import com.am24.imbrel.data.mapper.toDomain
import com.am24.imbrel.data.remote.WeatherApiService
import com.am24.imbrel.domain.error.ApiErrorReason
import com.am24.imbrel.domain.error.DomainError
import com.am24.imbrel.domain.error.DomainFailureException
import com.am24.imbrel.domain.error.NetworkErrorReason
import com.am24.imbrel.domain.model.CachedForecast
import com.am24.imbrel.domain.model.WeatherForecast
import com.am24.imbrel.domain.network.NetworkMonitor
import com.am24.imbrel.domain.network.isOnlineOrDomainFailure
import com.am24.imbrel.domain.repository.WeatherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

class WeatherRepositoryImpl(
    private val apiService: WeatherApiService,
    private val localDataSource: WeatherLocalDataSource,
    private val timeProvider: TimeProvider,
    private val cachePolicy: WeatherCachePolicy,
    private val networkMonitor: NetworkMonitor =
        object : NetworkMonitor {
            override fun isOnline(): Boolean = true

            override fun observeConnectivity(): Flow<Boolean> = flowOf(true)
        },
    private val timezone: String = ZoneId.systemDefault().id,
    private val placesDao: PlacesDao? = null,
) : WeatherRepository {
    override suspend fun clearWeatherCache() = localDataSource.clearWeather()

    override fun observeCachedForecasts() =
        combine(
            localDataSource.observeWeather(),
            placesDao?.observeAll() ?: flowOf(emptyList()),
            flow {
                while (true) {
                    emit(timeProvider.currentTimeMillis())
                    delay(CACHE_STATUS_INTERVAL_MILLIS)
                }
            },
        ) { cache, places, now -> CachedForecastIndex(cachePolicy).map(cache, places, now) }

    override suspend fun openCachedForecast(key: String): CachedForecast =
        mutexFor(key).withLock {
            val cached = readCache(key)
            val now = timeProvider.currentTimeMillis()
            if (cached == null || !cachePolicy.isUsableOffline(cached.cachedAtMillis, now)) {
                throw DomainFailureException(DomainError.Network(NetworkErrorReason.Offline))
            }
            CachedForecast(key, cached.forecast, cached.cachedAtMillis, CachedForecastIndex(cachePolicy).status(cached.cachedAtMillis, now))
        }

    override suspend fun getWeatherData(
        lat: String?,
        lon: String?,
        city: String?,
    ): WeatherForecast {
        val cacheKey =
            try {
                WeatherCacheKeyFactory.create(lat, lon, city)
            } catch (_: Exception) {
                throw DomainFailureException(DomainError.Api(ApiErrorReason.RequestFailed))
            }

        return mutexFor(cacheKey).withLock {
            val cached = readCache(cacheKey)
            val now = timeProvider.currentTimeMillis()

            if (!networkMonitor.isOnlineOrDomainFailure()) {
                return@withLock cached
                    ?.takeIf { cachePolicy.isUsableOffline(it.cachedAtMillis, now) }
                    ?.forecast ?: throw DomainFailureException(
                    DomainError.Network(NetworkErrorReason.Offline),
                )
            }

            if (cached != null && cachePolicy.isFresh(cached.cachedAtMillis, now)) {
                return@withLock cached.forecast
            }

            try {
                val forecast =
                    apiService
                        .getWeatherData(
                            lat = lat,
                            lon = lon,
                            placeId = city?.takeIf { lat == null && lon == null },
                            timezone = timezone,
                        ).toDomain()

                cacheKeysFor(lat, lon, city).forEach { key -> writeCache(key, forecast, now) }

                forecast
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException")
                remoteFailure: Exception,
            ) {
                // This boundary includes transport, decoding, and mapping failures so each can use
                // the same stale-cache fallback before being translated to a domain error.
                cached
                    ?.takeIf { cachePolicy.isUsableOffline(it.cachedAtMillis, now) }
                    ?.forecast ?: throw DomainFailureException(remoteFailure.toWeatherDomainError())
            }
        }
    }

    private suspend fun readCache(cacheKey: String) =
        try {
            localDataSource.getWeather(cacheKey)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }

    private suspend fun writeCache(
        cacheKey: String,
        forecast: WeatherForecast,
        now: Long,
    ) {
        try {
            localDataSource.saveWeather(cacheKey, forecast, now)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The remote response remains usable even if persistence is unavailable.
        }
    }

    private fun mutexFor(cacheKey: String): Mutex = requestMutexes.computeIfAbsent(cacheKey) { Mutex() }

    private fun cacheKeysFor(
        lat: String?,
        lon: String?,
        city: String?,
    ): Set<String> =
        buildSet {
            add(WeatherCacheKeyFactory.create(lat, lon, city))
            if (city != null && lat != null && lon != null) {
                add(WeatherCacheKeyFactory.create(lat = null, lon = null, city = city))
            }
        }

    private companion object {
        const val CACHE_STATUS_INTERVAL_MILLIS = 1_000L
        val requestMutexes = ConcurrentHashMap<String, Mutex>()
    }
}
