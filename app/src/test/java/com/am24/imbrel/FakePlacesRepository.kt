package com.am24.imbrel

import com.am24.imbrel.domain.model.SavedCity
import com.am24.imbrel.domain.model.SelectedPlace
import com.am24.imbrel.domain.repository.PlacesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class FakePlacesRepository : PlacesRepository {
    val searches = mutableListOf<SelectedPlace.City>()
    val history = MutableStateFlow<List<SavedCity>>(emptyList())
    val favorites = MutableStateFlow<List<SavedCity>>(emptyList())
    var mutationFailure: Exception? = null
    var readFailure: Exception? = null
    var favoriteChange: Pair<Long, Boolean>? = null
    var clearCount = 0

    override fun observeHistory() =
        flow {
            readFailure?.let { throw it }
            emitAll(history)
        }

    override fun observeFavorites() = favorites

    override suspend fun recordSuccessfulSearch(city: SelectedPlace.City) {
        mutationFailure?.let { throw it }
        searches.add(city)
    }

    override suspend fun setFavorite(
        id: Long,
        isFavorite: Boolean,
    ) {
        mutationFailure?.let { throw it }
        favoriteChange = id to isFavorite
    }

    override suspend fun clearHistory() {
        mutationFailure?.let { throw it }
        clearCount++
        history.value = emptyList()
    }
}
