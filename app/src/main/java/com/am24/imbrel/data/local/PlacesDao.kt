package com.am24.imbrel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
abstract class PlacesDao {
    @Query("SELECT * FROM saved_cities WHERE isInHistory = 1 ORDER BY lastSearchedAtMillis DESC, id DESC")
    abstract fun observeHistory(): Flow<List<SavedCityEntity>>

    @Query("SELECT * FROM saved_cities WHERE isFavorite = 1 ORDER BY displayName COLLATE NOCASE, id")
    abstract fun observeFavorites(): Flow<List<SavedCityEntity>>

    @Query("SELECT * FROM saved_cities WHERE queryKey = :queryKey LIMIT 1")
    abstract suspend fun findByQuery(queryKey: String): SavedCityEntity?

    @Insert
    abstract suspend fun insert(city: SavedCityEntity): Long

    @Update
    abstract suspend fun update(city: SavedCityEntity)

    @Transaction
    open suspend fun recordSearch(city: SavedCityEntity) {
        val existing = findByQuery(city.queryKey)
        if (existing == null) insert(city) else update(existing.mergeSearch(city))
    }

    @Query("UPDATE saved_cities SET isFavorite = :isFavorite WHERE id = :id")
    abstract suspend fun updateFavorite(
        id: Long,
        isFavorite: Boolean,
    )

    @Query("DELETE FROM saved_cities WHERE isFavorite = 0 AND isInHistory = 0")
    abstract suspend fun deleteUnusedCities()

    @Transaction
    open suspend fun setFavorite(
        id: Long,
        isFavorite: Boolean,
    ) {
        updateFavorite(id, isFavorite)
        deleteUnusedCities()
    }

    @Query("UPDATE saved_cities SET isInHistory = 0")
    abstract suspend fun hideHistory()

    @Transaction
    open suspend fun clearHistory() {
        hideHistory()
        deleteUnusedCities()
    }
}
