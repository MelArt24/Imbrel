package com.am24.imbrel.domain.repository

import com.am24.imbrel.domain.model.SelectedPlace

interface SelectedPlaceRepository {
    suspend fun getSelectedPlace(): SelectedPlace?

    suspend fun saveSelectedPlace(place: SelectedPlace)

    suspend fun clearSelectedPlace()
}
