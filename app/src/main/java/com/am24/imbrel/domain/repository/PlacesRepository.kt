package com.am24.imbrel.domain.repository

import com.am24.imbrel.domain.model.SavedCity
import com.am24.imbrel.domain.model.SelectedPlace
import kotlinx.coroutines.flow.Flow

interface PlacesRepository {
    fun observeHistory(): Flow<List<SavedCity>>

    fun observeFavorites(): Flow<List<SavedCity>>

    suspend fun recordSuccessfulSearch(city: SelectedPlace.City)

    suspend fun setFavorite(
        id: Long,
        isFavorite: Boolean,
    )

    suspend fun clearHistory()
}
