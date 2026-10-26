package com.am24.imbrel.domain.usecase

import com.am24.imbrel.domain.model.SelectedPlace
import com.am24.imbrel.domain.repository.PlacesRepository

class ObserveSearchHistoryUseCase(
    private val repository: PlacesRepository,
) {
    operator fun invoke() = repository.observeHistory()
}

class ObserveFavoriteCitiesUseCase(
    private val repository: PlacesRepository,
) {
    operator fun invoke() = repository.observeFavorites()
}

class RecordCitySearchUseCase(
    private val repository: PlacesRepository,
) {
    suspend operator fun invoke(city: SelectedPlace.City) = repository.recordSuccessfulSearch(city)
}

class SetCityFavoriteUseCase(
    private val repository: PlacesRepository,
) {
    suspend operator fun invoke(
        id: Long,
        isFavorite: Boolean,
    ) = repository.setFavorite(id, isFavorite)
}

class ClearSearchHistoryUseCase(
    private val repository: PlacesRepository,
) {
    suspend operator fun invoke() = repository.clearHistory()
}
