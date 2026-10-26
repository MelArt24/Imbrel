package com.am24.imbrel

import com.am24.imbrel.data.cache.TimeProvider
import com.am24.imbrel.data.cache.WeatherCachePolicy
import com.am24.imbrel.data.local.CachedWeather
import com.am24.imbrel.data.local.WeatherLocalDataSource
import com.am24.imbrel.data.remote.CurrentWeatherDto
import com.am24.imbrel.data.remote.DailyForecastDto
import com.am24.imbrel.data.remote.HourlyForecastDto
import com.am24.imbrel.data.remote.WeatherApiService
import com.am24.imbrel.data.remote.WeatherResponseDto
import com.am24.imbrel.data.repository.WeatherRepositoryImpl
import com.am24.imbrel.domain.error.DomainError
import com.am24.imbrel.domain.error.DomainFailureException
import com.am24.imbrel.domain.error.NetworkErrorReason
import com.am24.imbrel.domain.model.CurrentWeather
import com.am24.imbrel.domain.model.WeatherForecast
import com.am24.imbrel.domain.model.weatherConditionFromIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.IOException

class WeatherRepositoryImplTest {
    private val dayMillis = 24 * 60 * 60 * 1_000L
    private val now = 10 * dayMillis
    private val remoteForecast = forecast("remote", 20.0)
    private val cachedForecast = forecast("cached", 10.0)

    @Test
    fun remoteSuccess_returnsFreshDataAndUpdatesCorrectNormalizedKey() =
        runTest {
            val local = FakeLocalDataSource()
            val repository = repository(FakeApi(dto("remote", 20.0)), local)

            val result = repository.getWeatherData("50.45001", "30.52340", null)

            assertEquals(remoteForecast, result)
            assertEquals("lat:50.4500|lon:30.5234", local.savedKey)
            assertEquals(remoteForecast, local.savedForecast)
            assertEquals(now, local.savedAt)
        }

    @Test
    fun freshCache_isReturnedWithoutRemoteRequest() =
        runTest {
            val api = FakeApi(dto("remote", 20.0))
            val local = FakeLocalDataSource(CachedWeather(cachedForecast, now - 1_000))

            val result = repository(api, local).getWeatherData("50.45", "30.5234", null)

            assertEquals(cachedForecast, result)
            assertEquals(0, api.calls)
            assertEquals("lat:50.4500|lon:30.5234", local.readKey)
        }

    @Test
    fun offlineWithStaleCache_returnsCacheWithoutRemoteRequest() =
        runTest {
            val api = FakeApi(dto("remote", 20.0))
            val local = FakeLocalDataSource(CachedWeather(cachedForecast, now - dayMillis))

            val result = repository(api, local, online = false).getWeatherData("1", "2", null)

            assertEquals(cachedForecast, result)
            assertEquals(0, api.calls)
        }

    @Test
    fun offlineWithSixDayOldCache_rejectsCacheWithoutRemoteRequest() =
        runTest {
            val api = FakeApi(dto("remote", 20.0))
            val local = FakeLocalDataSource(CachedWeather(cachedForecast, now - 6 * dayMillis))

            val thrown =
                captureFailure {
                    repository(api, local, online = false).getWeatherData("1", "2", null)
                }

            assertEquals(0, api.calls)
            assertEquals(
                DomainError.Network(NetworkErrorReason.Offline),
                (thrown as DomainFailureException).error,
            )
        }

    @Test
    fun offlineWithFutureCache_rejectsCacheWithoutRemoteRequest() =
        runTest {
            val api = FakeApi(dto("remote", 20.0))
            val local = FakeLocalDataSource(CachedWeather(cachedForecast, now + 1))

            val thrown =
                captureFailure {
                    repository(api, local, online = false).getWeatherData("1", "2", null)
                }

            assertEquals(0, api.calls)
            assertEquals(
                DomainError.Network(NetworkErrorReason.Offline),
                (thrown as DomainFailureException).error,
            )
        }

    @Test
    fun offlineWithoutCache_failsWithoutRemoteRequest() =
        runTest {
            val api = FakeApi(dto("remote", 20.0))

            val thrown =
                captureFailure {
                    repository(api, FakeLocalDataSource(), online = false).getWeatherData("1", "2", null)
                }

            assertEquals(0, api.calls)
            assertEquals(
                DomainError.Network(NetworkErrorReason.Offline),
                (thrown as DomainFailureException).error,
            )
        }

    @Test
    fun staleCache_triggersRemoteRefresh() =
        runTest {
            val api = FakeApi(dto("remote", 20.0))
            val local = FakeLocalDataSource(CachedWeather(cachedForecast, now - 20_001))

            val result = repository(api, local, ttl = 20_000).getWeatherData("1", "2", null)

            assertEquals(remoteForecast, result)
            assertEquals(1, api.calls)
            assertEquals(remoteForecast, local.savedForecast)
        }

    @Test
    fun remoteFailure_returnsMatchingStaleCache() =
        runTest {
            val local = FakeLocalDataSource(CachedWeather(cachedForecast, now - dayMillis))

            val result =
                repository(FakeApi(failure = IOException("offline")), local)
                    .getWeatherData("1", "2", null)

            assertEquals(cachedForecast, result)
            assertEquals("lat:1.0000|lon:2.0000", local.readKey)
        }

    @Test
    fun remoteFailureWithSixDayOldCache_mapsFailure() =
        runTest {
            val local = FakeLocalDataSource(CachedWeather(cachedForecast, now - 6 * dayMillis))

            val thrown =
                captureFailure {
                    repository(FakeApi(failure = IOException("offline")), local)
                        .getWeatherData("1", "2", null)
                }

            assertEquals(
                DomainError.Network(NetworkErrorReason.ConnectionFailed),
                (thrown as DomainFailureException).error,
            )
        }

    @Test
    fun remoteFailureWithFutureCache_mapsFailure() =
        runTest {
            val local = FakeLocalDataSource(CachedWeather(cachedForecast, now + 1))

            val thrown =
                captureFailure {
                    repository(FakeApi(failure = IOException("offline")), local)
                        .getWeatherData("1", "2", null)
                }

            assertEquals(
                DomainError.Network(NetworkErrorReason.ConnectionFailed),
                (thrown as DomainFailureException).error,
            )
        }

    @Test
    fun remoteFailureWithoutCache_mapsFailure() =
        runTest {
            val failure = IOException("offline")
            var thrown: Throwable? = null
            try {
                repository(FakeApi(failure = failure), FakeLocalDataSource())
                    .getWeatherData("1", "2", null)
            } catch (error: Throwable) {
                thrown = error
            }

            assertEquals(
                DomainError.Network(NetworkErrorReason.ConnectionFailed),
                (thrown as DomainFailureException).error,
            )
        }

    @Test
    fun cacheWriteFailure_doesNotHideRemoteSuccess() =
        runTest {
            val local = FakeLocalDataSource().apply { writeFailure = IllegalStateException("db") }

            val result =
                repository(FakeApi(dto("remote", 20.0)), local)
                    .getWeatherData("1", "2", null)

            assertEquals(remoteForecast, result)
        }

    @Test
    fun cacheReadFailure_doesNotPreventRemoteSuccess() =
        runTest {
            val local = FakeLocalDataSource().apply { readFailure = IllegalStateException("db") }

            val result =
                repository(FakeApi(dto("remote", 20.0)), local)
                    .getWeatherData("1", "2", null)

            assertEquals(remoteForecast, result)
        }

    @Test
    fun cancellationFromRemote_isPropagatedWithoutFallback() =
        runTest {
            val cancellation = CancellationException("cancel")
            val local = FakeLocalDataSource(CachedWeather(cachedForecast, 0))

            var thrown: Throwable? = null
            try {
                repository(FakeApi(failure = cancellation), local)
                    .getWeatherData("1", "2", null)
            } catch (error: Throwable) {
                thrown = error
            }

            assertSame(cancellation, thrown)
        }

    @Test
    fun openCachedOffline_neverCallsApiAndUsesExactKey() =
        runTest {
            val api = FakeApi()
            val local = FakeLocalDataSource(CachedWeather(cachedForecast, now - dayMillis))
            val result = repository(api, local, online = false).openCachedForecast("lat:1.0000|lon:2.0000")
            assertEquals(cachedForecast, result.forecast)
            assertEquals(com.am24.imbrel.domain.model.CacheStatus.Offline, result.status)
            assertEquals("lat:1.0000|lon:2.0000", local.readKey)
            assertEquals(0, api.calls)
        }

    @Test
    fun openExpiredCache_rejectsWithoutApiEvenOnline() =
        runTest {
            val api = FakeApi()
            val local = FakeLocalDataSource(CachedWeather(cachedForecast, now - dayMillis - 1))
            val failure = captureFailure { repository(api, local).openCachedForecast("city:test") }
            org.junit.Assert.assertTrue(failure is DomainFailureException)
            assertEquals(0, api.calls)
        }

    @Test
    fun cachedList_observesLocalDataAndRefreshesTtlWithoutApi() =
        runTest {
            val api = FakeApi()
            val local = FakeLocalDataSource(CachedWeather(cachedForecast, now - dayMillis))
            val result = repository(api, local).observeCachedForecasts().first()
            assertEquals(1, result.size)
            assertEquals(com.am24.imbrel.domain.model.CacheStatus.Offline, result.single().status)
            assertEquals(0, api.calls)
        }

    @Test
    fun clearWeatherCache_removesLocalForecastsWithoutApiAndPropagatesFailures() =
        runTest {
            val api = FakeApi()
            val local = FakeLocalDataSource(CachedWeather(cachedForecast, now))
            val repository = repository(api, local)
            repository.clearWeatherCache()
            assertEquals(null, local.cached)
            assertEquals(emptyList<Any>(), repository.observeCachedForecasts().first())
            assertEquals(0, api.calls)
            val failure = IOException("Clear failed")
            local.writeFailure = failure
            assertSame(failure, captureFailure { repository.clearWeatherCache() })
        }

    private fun repository(
        api: FakeApi,
        local: FakeLocalDataSource,
        ttl: Long = 20_000,
        online: Boolean = true,
    ) = WeatherRepositoryImpl(
        apiService = api,
        localDataSource = local,
        timeProvider = TimeProvider { now },
        cachePolicy = WeatherCachePolicy(ttl),
        networkMonitor = FakeNetworkMonitor(online),
        timezone = "UTC",
    )

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable? =
        try {
            block()
            null
        } catch (failure: Throwable) {
            failure
        }

    private class FakeLocalDataSource(
        var cached: CachedWeather? = null,
    ) : WeatherLocalDataSource {
        var readKey: String? = null
        var savedKey: String? = null
        var savedForecast: WeatherForecast? = null
        var savedAt: Long? = null
        var readFailure: Exception? = null
        var writeFailure: Exception? = null

        override suspend fun clearWeather() {
            writeFailure?.let { throw it }
            cached = null
        }

        override fun observeWeather() =
            kotlinx.coroutines.flow.flowOf(
                cached?.let { mapOf("city:test" to it) } ?: emptyMap(),
            )

        override suspend fun getWeather(cacheKey: String): CachedWeather? {
            readKey = cacheKey
            readFailure?.let { throw it }
            return cached
        }

        override suspend fun saveWeather(
            cacheKey: String,
            forecast: WeatherForecast,
            cachedAtMillis: Long,
        ) {
            writeFailure?.let { throw it }
            savedKey = cacheKey
            savedForecast = forecast
            savedAt = cachedAtMillis
        }
    }

    private class FakeApi(
        private val response: WeatherResponseDto? = null,
        private val failure: Exception? = null,
    ) : WeatherApiService {
        var calls = 0

        override suspend fun getWeatherData(
            lat: String?,
            lon: String?,
            placeId: String?,
            sections: String,
            timezone: String,
            language: String,
            units: String,
        ): WeatherResponseDto {
            calls++
            failure?.let { throw it }
            return requireNotNull(response)
        }
    }

    private fun dto(
        summary: String,
        temperature: Double,
    ) = WeatherResponseDto(
        lat = "1",
        lon = "2",
        timezone = "UTC",
        units = "metric",
        placeId = null,
        current = CurrentWeatherDto("", 2, summary, temperature),
        hourly = HourlyForecastDto(emptyList()),
        daily = DailyForecastDto(emptyList()),
    )

    private fun forecast(
        summary: String,
        temperature: Double,
    ) = WeatherForecast(
        cityName = null,
        current = CurrentWeather(summary, temperature, 2, weatherConditionFromIcon(2)),
        hourly = emptyList(),
        daily = emptyList(),
    )
}
