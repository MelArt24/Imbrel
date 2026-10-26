package com.am24.imbrel

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.am24.imbrel.data.cache.TimeProvider
import com.am24.imbrel.data.cache.WeatherCacheKeyFactory
import com.am24.imbrel.data.cache.WeatherCachePolicy
import com.am24.imbrel.data.local.RoomWeatherLocalDataSource
import com.am24.imbrel.data.local.WeatherDatabase
import com.am24.imbrel.data.remote.WeatherApiService
import com.am24.imbrel.data.repository.RoomPlacesRepository
import com.am24.imbrel.data.repository.WeatherRepositoryImpl
import com.am24.imbrel.domain.model.CurrentWeather
import com.am24.imbrel.domain.model.LocationCoordinates
import com.am24.imbrel.domain.model.SelectedPlace
import com.am24.imbrel.domain.model.WeatherForecast
import com.am24.imbrel.domain.network.NetworkMonitor
import com.am24.imbrel.domain.repository.GeocodingRepository
import com.am24.imbrel.domain.usecase.SearchCityWeatherUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import java.util.UUID

class PlacesPersistenceTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WeatherDatabase::class.java)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "places-test-${UUID.randomUUID()}.db"
    private var database: WeatherDatabase? = null
    private var now = 100L

    @After
    fun closeDatabase() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase(): WeatherDatabase =
        Room
            .databaseBuilder(context, WeatherDatabase::class.java, databaseName)
            .addMigrations(WeatherDatabase.MIGRATION_1_2)
            .build()
            .also { database = it }

    private fun repository(db: WeatherDatabase) = RoomPlacesRepository(db.placesDao(), TimeProvider { now })

    @Test
    fun migration_preservesCurrentHourlyAndDailyCacheAndAcceptsCities() =
        runBlocking {
            migrationHelper.createDatabase(databaseName, 1).apply {
                execSQL("INSERT INTO cached_weather VALUES ('city:kyiv', 'Kyiv', 'Clear', 20.0, 1, 123)")
                execSQL("INSERT INTO cached_hourly_weather VALUES ('city:kyiv', 0, '12:00', 'Clear', 21.0, 1)")
                execSQL("INSERT INTO cached_daily_weather VALUES ('city:kyiv', 0, '2026-10-12', 'Clear', 1, 10.0, 22.0)")
                close()
            }
            migrationHelper.runMigrationsAndValidate(databaseName, 2, true, WeatherDatabase.MIGRATION_1_2).close()
            val db = openDatabase()
            val cached = db.weatherDao().getWeather("city:kyiv")!!
            assertEquals(123L, cached.weather.cachedAtMillis)
            assertEquals(20.0, cached.weather.currentTemperature, 0.0)
            assertEquals("12:00", cached.hourly.single().date)
            assertEquals("2026-10-12", cached.daily.single().day)
            val places = repository(db)
            places.recordSuccessfulSearch(city())
            assertEquals(
                "Kyiv",
                places
                    .observeHistory()
                    .first()
                    .single()
                    .displayName,
            )
        }

    @Test
    fun duplicateQueries_preserveIdentityCoordinatesDisplayNameAndFavorite() =
        runBlocking {
            val places = repository(openDatabase())
            places.recordSuccessfulSearch(city())
            val original = places.observeHistory().first().single()
            places.setFavorite(original.id, true)
            now = 200L
            places.recordSuccessfulSearch(SelectedPlace.City("KYIV", "KYIV"))
            val updated = places.observeHistory().first().single()
            assertEquals(original.id, updated.id)
            assertEquals(original.coordinates, updated.coordinates)
            assertEquals("Kyiv", updated.displayName)
            assertEquals(200L, updated.lastSearchedAtMillis)
            assertTrue(updated.isFavorite)
            assertEquals(updated, places.observeFavorites().first().single())
        }

    @Test
    fun concurrentSearches_doNotCreateDuplicatesAndHistoryIsNewestFirst() =
        runBlocking {
            val places = repository(openDatabase())
            List(10) { async { places.recordSuccessfulSearch(city()) } }.awaitAll()
            now = 200L
            places.recordSuccessfulSearch(SelectedPlace.City("Lviv", "Lviv"))
            assertEquals(listOf("Lviv", "Kyiv"), places.observeHistory().first().map { it.displayName })
        }

    @Test
    fun clearHistory_keepsFavoritesAndUnfavoritingDoesNotRestoreHistory() =
        runBlocking {
            val places = repository(openDatabase())
            places.recordSuccessfulSearch(city())
            val saved = places.observeHistory().first().single()
            places.setFavorite(saved.id, true)
            places.recordSuccessfulSearch(SelectedPlace.City("Lviv", "Lviv"))
            places.clearHistory()
            assertTrue(places.observeHistory().first().isEmpty())
            assertEquals(
                saved.id,
                places
                    .observeFavorites()
                    .first()
                    .single()
                    .id,
            )
            places.setFavorite(saved.id, false)
            assertTrue(places.observeFavorites().first().isEmpty())
            assertTrue(places.observeHistory().first().isEmpty())
            places.recordSuccessfulSearch(city())
            assertFalse(
                places
                    .observeHistory()
                    .first()
                    .single()
                    .isFavorite,
            )
        }

    @Test
    fun historyAndFavorites_surviveDatabaseRecreation() =
        runBlocking {
            val db = openDatabase()
            val places = repository(db)
            places.recordSuccessfulSearch(city())
            val saved = places.observeHistory().first().single()
            places.setFavorite(saved.id, true)
            val expected = places.observeHistory().first()
            db.close()
            val restored = repository(openDatabase())
            assertEquals(expected, restored.observeHistory().first())
            assertEquals(expected, restored.observeFavorites().first())
        }

    @Test
    fun cancelledTransaction_doesNotWriteHistory() =
        runBlocking {
            val db = openDatabase()
            val places = repository(db)
            val inserted = CompletableDeferred<Unit>()
            val job =
                launch {
                    db.withTransaction {
                        places.recordSuccessfulSearch(city())
                        inserted.complete(Unit)
                        awaitCancellation()
                    }
                }
            inserted.await()
            job.cancelAndJoin()
            assertTrue(places.observeHistory().first().isEmpty())
            assertNull(db.placesDao().findByQuery("kyiv"))
        }

    @Test
    fun savedCity_opensOfflineCoordinateCacheWithoutApiOrGeocoding() =
        runBlocking {
            val db = openDatabase()
            val places = repository(db)
            places.recordSuccessfulSearch(city())
            val saved = places.observeHistory().first().single()
            val forecast = WeatherForecast("Kyiv", CurrentWeather("Clear", 20.0, 1), emptyList(), emptyList())
            val local = RoomWeatherLocalDataSource(db.weatherDao())
            local.saveWeather(WeatherCacheKeyFactory.create("50.45", "30.52", null), forecast, now)
            val api = mock(WeatherApiService::class.java)
            val network =
                object : NetworkMonitor {
                    override fun isOnline() = false

                    override fun observeConnectivity() = flowOf(false)
                }
            val geocoding = mock(GeocodingRepository::class.java)
            val weather = WeatherRepositoryImpl(api, local, TimeProvider { now }, WeatherCachePolicy(), network)
            val result = SearchCityWeatherUseCase(weather, geocoding, network)(saved.toSelectedPlace())
            assertEquals(forecast, result.forecast)
            assertEquals(saved.toSelectedPlace(), result.toSelectedPlace())
            verifyNoInteractions(api, geocoding)
        }

    private fun city() = SelectedPlace.City("Kyiv", "Kyiv", LocationCoordinates(50.45, 30.52))
}
