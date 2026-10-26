package com.am24.imbrel.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.am24.imbrel.domain.model.LocationCoordinates
import com.am24.imbrel.domain.model.SavedCity

@Entity(tableName = "saved_cities", indices = [Index(value = ["queryKey"], unique = true)])
data class SavedCityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val queryKey: String,
    val normalizedQuery: String,
    val displayName: String,
    val latitude: Double?,
    val longitude: Double?,
    val lastSearchedAtMillis: Long,
    val isFavorite: Boolean = false,
    val isInHistory: Boolean = true,
) {
    fun toDomain(): SavedCity =
        SavedCity(
            id = id,
            normalizedQuery = normalizedQuery,
            displayName = displayName,
            coordinates = if (latitude != null && longitude != null) LocationCoordinates(latitude, longitude) else null,
            lastSearchedAtMillis = lastSearchedAtMillis,
            isFavorite = isFavorite,
        )

    fun mergeSearch(incoming: SavedCityEntity): SavedCityEntity =
        copy(
            displayName =
                incoming.displayName.takeIf { !it.equals(incoming.normalizedQuery, ignoreCase = true) }
                    ?: displayName,
            latitude = incoming.latitude ?: latitude,
            longitude = incoming.longitude ?: longitude,
            lastSearchedAtMillis = maxOf(lastSearchedAtMillis, incoming.lastSearchedAtMillis),
            isInHistory = true,
        )
}
