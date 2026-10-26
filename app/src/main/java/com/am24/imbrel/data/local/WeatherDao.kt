package com.am24.imbrel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {
    @Query("DELETE FROM cached_weather")
    suspend fun clearWeather()

    @Transaction
    @Query("SELECT * FROM cached_weather ORDER BY cachedAtMillis DESC, cacheKey")
    fun observeWeather(): Flow<List<CachedWeatherAggregate>>

    @Transaction
    @Query("SELECT * FROM cached_weather WHERE cacheKey = :cacheKey")
    suspend fun getWeather(cacheKey: String): CachedWeatherAggregate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeather(weather: CachedWeatherEntity)

    @Insert
    suspend fun insertHourly(hourly: List<CachedHourlyWeatherEntity>)

    @Insert
    suspend fun insertDaily(daily: List<CachedDailyWeatherEntity>)

    @Query("DELETE FROM cached_hourly_weather WHERE cacheKey = :cacheKey")
    suspend fun deleteHourly(cacheKey: String)

    @Query("DELETE FROM cached_daily_weather WHERE cacheKey = :cacheKey")
    suspend fun deleteDaily(cacheKey: String)

    @Transaction
    suspend fun replaceWeather(aggregate: CachedWeatherAggregate) {
        val key = aggregate.weather.cacheKey
        insertWeather(aggregate.weather)
        deleteHourly(key)
        deleteDaily(key)
        if (aggregate.hourly.isNotEmpty()) insertHourly(aggregate.hourly)
        if (aggregate.daily.isNotEmpty()) insertDaily(aggregate.daily)
    }
}
