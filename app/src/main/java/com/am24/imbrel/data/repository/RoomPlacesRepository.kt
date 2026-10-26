package com.am24.imbrel.data.repository

import com.am24.imbrel.data.cache.TimeProvider
import com.am24.imbrel.data.local.PlacesDao
import com.am24.imbrel.data.local.SavedCityEntity
import com.am24.imbrel.domain.model.SelectedPlace
import com.am24.imbrel.domain.repository.PlacesRepository
import kotlinx.coroutines.flow.map
import java.util.Locale

class RoomPlacesRepository(
    private val dao: PlacesDao,
    private val timeProvider: TimeProvider,
) : PlacesRepository {
    override fun observeHistory() = dao.observeHistory().map { cities -> cities.map { it.toDomain() } }

    override fun observeFavorites() = dao.observeFavorites().map { cities -> cities.map { it.toDomain() } }

    override suspend fun recordSuccessfulSearch(city: SelectedPlace.City) {
        dao.recordSearch(
            SavedCityEntity(
                queryKey = city.normalizedQuery.lowercase(Locale.ROOT),
                normalizedQuery = city.normalizedQuery,
                displayName = city.displayName,
                latitude = city.coordinates?.latitude,
                longitude = city.coordinates?.longitude,
                lastSearchedAtMillis = timeProvider.currentTimeMillis(),
            ),
        )
    }

    override suspend fun setFavorite(
        id: Long,
        isFavorite: Boolean,
    ) = dao.setFavorite(id, isFavorite)

    override suspend fun clearHistory() = dao.clearHistory()
}
