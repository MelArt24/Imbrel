package com.am24.imbrel.domain.model

data class SavedCity(
    val id: Long,
    val normalizedQuery: String,
    val displayName: String,
    val coordinates: LocationCoordinates?,
    val lastSearchedAtMillis: Long,
    val isFavorite: Boolean,
) {
    fun toSelectedPlace(): SelectedPlace.City = SelectedPlace.City(normalizedQuery, displayName, coordinates)
}
