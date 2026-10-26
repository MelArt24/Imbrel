package com.am24.imbrel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CachedWeatherEntity::class,
        CachedHourlyWeatherEntity::class,
        CachedDailyWeatherEntity::class,
        SavedCityEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao

    abstract fun placesDao(): PlacesDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `saved_cities` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `queryKey` TEXT NOT NULL,
                            `normalizedQuery` TEXT NOT NULL,
                            `displayName` TEXT NOT NULL,
                            `latitude` REAL,
                            `longitude` REAL,
                            `lastSearchedAtMillis` INTEGER NOT NULL,
                            `isFavorite` INTEGER NOT NULL,
                            `isInHistory` INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_cities_queryKey` ON `saved_cities` (`queryKey`)")
                }
            }
    }
}
