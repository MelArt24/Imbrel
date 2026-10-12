package com.am24.imbrel

import com.am24.imbrel.domain.error.ApiErrorReason
import com.am24.imbrel.domain.error.DomainError
import com.am24.imbrel.domain.error.DomainFailureException
import com.am24.imbrel.domain.error.NetworkErrorReason
import com.am24.imbrel.domain.model.CurrentWeather
import com.am24.imbrel.domain.model.GeocodedLocation
import com.am24.imbrel.domain.model.LocationCoordinates
import com.am24.imbrel.domain.model.SelectedPlace
import com.am24.imbrel.domain.model.WeatherForecast
import com.am24.imbrel.domain.repository.GeocodingRepository
import com.am24.imbrel.domain.repository.WeatherRepository
import com.am24.imbrel.domain.usecase.SearchCityWeatherUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SearchCityWeatherUseCaseTest {
    @Test
    fun localizedQuery_resolvesCoordinatesAndPreservesLocalizedName() =
        runTest {
            val weatherRepository = FakeWeatherRepository()
            val geocodingRepository =
                FakeGeocodingRepository(
                    GeocodedLocation(39.9042, 116.4074, "Пекін"),
                )

            val result =
                SearchCityWeatherUseCase(
                    weatherRepository,
                    geocodingRepository,
                )("Пекін")

            assertEquals("Пекін", geocodingRepository.query)
            assertEquals("39.9042", weatherRepository.lat)
            assertEquals("116.4074", weatherRepository.lon)
            assertEquals(geocodingRepository.query, result.normalizedQuery)
            assertEquals(LocationCoordinates(39.9042, 116.4074), result.coordinates)
            assertEquals(
                SelectedPlace.City(result.normalizedQuery, result.city, result.coordinates),
                result.toSelectedPlace(),
            )
            assertEquals(geocodingRepository.query, weatherRepository.city)
            assertEquals("Пекін", result.city)
        }

    @Test
    fun missingLocalizedName_usesNormalizedOriginalQuery() =
        runTest {
            val useCase =
                SearchCityWeatherUseCase(
                    FakeWeatherRepository(),
                    FakeGeocodingRepository(GeocodedLocation(50.45, 30.52, null)),
                )

            val result = useCase("  Київ   Місто ")

            assertEquals("Київ Місто", result.city)
        }

    @Test
    fun localizedDisplayName_doesNotReplaceNormalizedSearchQuery() =
        runTest {
            val result =
                SearchCityWeatherUseCase(
                    FakeWeatherRepository(),
                    FakeGeocodingRepository(GeocodedLocation(50.45, 30.52, "Localized name")),
                )("  Kyiv   City  ")

            assertEquals("Kyiv City", result.normalizedQuery)
            assertEquals("Localized name", result.city)
            assertEquals(
                SelectedPlace.City("Kyiv City", "Localized name", LocationCoordinates(50.45, 30.52)),
                result.toSelectedPlace(),
            )
        }

    @Test
    fun missingGeocodingResult_producesExactCityNotFoundError() =
        runTest {
            val failure =
                captureFailure {
                    SearchCityWeatherUseCase(
                        FakeWeatherRepository(),
                        FakeGeocodingRepository(null),
                    )("Unknown city")
                }

            assertEquals(
                DomainError.Api(ApiErrorReason.NotFound),
                (failure as DomainFailureException).error,
            )
        }

    @Test
    fun blankQuery_producesExactRequestFailedError() =
        runTest {
            val failure =
                captureFailure {
                    SearchCityWeatherUseCase(
                        FakeWeatherRepository(),
                        FakeGeocodingRepository(null),
                    )("   ")
                }

            assertEquals(
                DomainError.Api(ApiErrorReason.RequestFailed),
                (failure as DomainFailureException).error,
            )
        }

    @Test
    fun offlineSearch_usesCachedCityWithoutGeocoding() =
        runTest {
            val weatherRepository = FakeWeatherRepository()
            val geocodingRepository = FakeGeocodingRepository(null)

            val result =
                SearchCityWeatherUseCase(
                    weatherRepository,
                    geocodingRepository,
                    FakeNetworkMonitor(false),
                )("  Kyiv ")

            assertEquals("Kyiv", result.city)
            assertEquals("Kyiv", result.normalizedQuery)
            assertNull(result.coordinates)
            assertEquals(SelectedPlace.City("Kyiv", "Kyiv"), result.toSelectedPlace())
            assertNull(weatherRepository.lat)
            assertNull(weatherRepository.lon)
            assertEquals("Kyiv", weatherRepository.city)
            assertNull(geocodingRepository.query)
        }

    @Test
    fun geocodingCancellation_isPropagated() =
        runTest {
            val cancellation = CancellationException("cancel geocoding")
            val useCase =
                SearchCityWeatherUseCase(
                    FakeWeatherRepository(),
                    FakeGeocodingRepository(result = null, failure = cancellation),
                )

            assertSame(cancellation, captureFailure { useCase("Kyiv") })
        }

    @Test
    fun weatherCancellation_isPropagated() =
        runTest {
            val cancellation = CancellationException("cancel weather")
            val useCase =
                SearchCityWeatherUseCase(
                    FakeWeatherRepository(failure = cancellation),
                    FakeGeocodingRepository(GeocodedLocation(50.45, 30.52, "Kyiv")),
                )

            assertSame(cancellation, captureFailure { useCase("Kyiv") })
        }

    @Test
    fun mappedGeocodingFailure_isPreserved() =
        runTest {
            val error = DomainError.Network(NetworkErrorReason.ConnectionFailed)
            val useCase =
                SearchCityWeatherUseCase(
                    FakeWeatherRepository(),
                    FakeGeocodingRepository(
                        result = null,
                        failure = DomainFailureException(error),
                    ),
                )

            val failure = captureFailure { useCase("Kyiv") }

            assertSame(error, (failure as DomainFailureException).error)
        }

    @Test
    fun mappedWeatherFailure_isPreserved() =
        runTest {
            val error = DomainError.Api(ApiErrorReason.ServerError, statusCode = 503)
            val useCase =
                SearchCityWeatherUseCase(
                    FakeWeatherRepository(failure = DomainFailureException(error)),
                    FakeGeocodingRepository(GeocodedLocation(50.45, 30.52, "Kyiv")),
                )

            val failure = captureFailure { useCase("Kyiv") }

            assertSame(error, (failure as DomainFailureException).error)
        }

    @Test
    fun restoredCoordinates_useWeatherRepositoryWithoutGeocodingEvenOffline() =
        runTest {
            val weather = FakeWeatherRepository()
            val geocoding = FakeGeocodingRepository(null)
            val city = SelectedPlace.City("Kyiv", "Saved name", LocationCoordinates(50.45, 30.52))
            val result = SearchCityWeatherUseCase(weather, geocoding, FakeNetworkMonitor(false))(city)

            assertEquals("50.45", weather.lat)
            assertEquals("30.52", weather.lon)
            assertEquals("Kyiv", weather.city)
            assertNull(geocoding.query)
            assertEquals(city, result.toSelectedPlace())
        }

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable? =
        try {
            block()
            null
        } catch (failure: Throwable) {
            failure
        }

    private class FakeGeocodingRepository(
        private val result: GeocodedLocation?,
        private val failure: Throwable? = null,
    ) : GeocodingRepository {
        var query: String? = null

        override suspend fun searchLocation(query: String): GeocodedLocation? {
            this.query = query
            failure?.let { throw it }
            return result
        }

        override suspend fun resolvePlaceName(
            latitude: Double,
            longitude: Double,
        ): String? = null
    }

    private class FakeWeatherRepository(
        private val failure: Throwable? = null,
    ) : WeatherRepository {
        var lat: String? = null
        var lon: String? = null
        var city: String? = null

        override suspend fun getWeatherData(
            lat: String?,
            lon: String?,
            city: String?,
        ): WeatherForecast {
            failure?.let { throw it }
            this.lat = lat
            this.lon = lon
            this.city = city
            return WeatherForecast(
                cityName = "Beijing",
                current = CurrentWeather("Clear", 21.4, 2),
                hourly = emptyList(),
                daily = emptyList(),
            )
        }
    }
}
