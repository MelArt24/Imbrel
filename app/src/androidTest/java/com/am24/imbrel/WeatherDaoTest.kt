package com.am24.imbrel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.am24.imbrel.data.local.CachedDailyWeatherEntity
import com.am24.imbrel.data.local.CachedHourlyWeatherEntity
import com.am24.imbrel.data.local.CachedWeatherAggregate
import com.am24.imbrel.data.local.CachedWeatherEntity
import com.am24.imbrel.data.local.WeatherDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class WeatherDaoTest {
    private lateinit var database: WeatherDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, WeatherDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    @Throws(IOException::class)
    fun closeDatabase() = database.close()

    @Test
    fun clearWeather_cascadesToDetailsAndPreservesSavedCities() =
        runBlocking {
            database.weatherDao().replaceWeather(aggregate("city:kyiv", 1, 2, 2))
            val city =
                com.am24.imbrel.data.local.SavedCityEntity(
                    queryKey = "kyiv",
                    normalizedQuery = "Kyiv",
                    displayName = "Kyiv",
                    latitude = 50.45,
                    longitude = 30.52,
                    lastSearchedAtMillis = 1,
                    isFavorite = true,
                )
            database.placesDao().insert(city)
            database.weatherDao().clearWeather()
            assertEquals(emptyList<CachedWeatherAggregate>(), database.weatherDao().observeWeather().first())
            listOf("cached_weather", "cached_hourly_weather", "cached_daily_weather").forEach { table ->
                database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(0, cursor.getInt(0))
                }
            }
            assertEquals(
                "Kyiv",
                database
                    .placesDao()
                    .observeHistory()
                    .first()
                    .single()
                    .displayName,
            )
            assertEquals(
                true,
                database
                    .placesDao()
                    .observeFavorites()
                    .first()
                    .single()
                    .isFavorite,
            )
        }

    @Test
    fun cacheObservation_reflectsInsertsAndReplacementWithoutDeletingExpiredRows() =
        runBlocking {
            val dao = database.weatherDao()
            assertEquals(emptyList<CachedWeatherAggregate>(), dao.observeWeather().first())
            dao.replaceWeather(aggregate("old", 0, 1, 1))
            dao.replaceWeather(aggregate("new", 100, 1, 1))
            assertEquals(listOf("new", "old"), dao.observeWeather().first().map { it.weather.cacheKey })
            dao.replaceWeather(aggregate("old", 200, 2, 2))
            val rows = dao.observeWeather().first()
            assertEquals(listOf("old", "new"), rows.map { it.weather.cacheKey })
            assertEquals(2, rows.first().daily.size)
        }

    @Test
    fun insertAndRead_preservesAggregateAndTimestamp() =
        runBlocking {
            val aggregate = aggregate("a", 123L, hourlyCount = 2, dailyCount = 2)

            database.weatherDao().replaceWeather(aggregate)

            val result = database.weatherDao().getWeather("a")
            assertEquals(123L, result?.weather?.cachedAtMillis)
            assertEquals(aggregate.hourly, result?.hourly?.sortedBy { it.position })
            assertEquals(aggregate.daily, result?.daily?.sortedBy { it.position })
        }

    @Test
    fun differentLocations_doNotOverwriteEachOther() =
        runBlocking {
            database.weatherDao().replaceWeather(aggregate("a", 1L, 1, 1))
            database.weatherDao().replaceWeather(aggregate("b", 2L, 2, 2))

            assertEquals(
                1,
                database
                    .weatherDao()
                    .getWeather("a")
                    ?.hourly
                    ?.size,
            )
            assertEquals(
                2,
                database
                    .weatherDao()
                    .getWeather("b")
                    ?.hourly
                    ?.size,
            )
            assertNull(database.weatherDao().getWeather("missing"))
        }

    @Test
    fun updatingLocation_replacesOldForecastRows() =
        runBlocking {
            database.weatherDao().replaceWeather(aggregate("a", 1L, 3, 3))

            database.weatherDao().replaceWeather(aggregate("a", 2L, 1, 1))

            val result = database.weatherDao().getWeather("a")
            assertEquals(2L, result?.weather?.cachedAtMillis)
            assertEquals(1, result?.hourly?.size)
            assertEquals(1, result?.daily?.size)
            assertEquals("hour-0", result?.hourly?.single()?.date)
        }

    private fun aggregate(
        key: String,
        timestamp: Long,
        hourlyCount: Int,
        dailyCount: Int,
    ) = CachedWeatherAggregate(
        weather = CachedWeatherEntity(key, "City $key", "Clear", 20.0, 2, timestamp),
        hourly =
            List(hourlyCount) { position ->
                CachedHourlyWeatherEntity(key, position, "hour-$position", "Clear", 20.0, 2)
            },
        daily =
            List(dailyCount) { position ->
                CachedDailyWeatherEntity(key, position, "day-$position", "Clear", 2, 10.0, 20.0)
            },
    )
}
