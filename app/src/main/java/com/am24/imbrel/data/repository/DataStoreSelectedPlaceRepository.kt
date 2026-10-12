package com.am24.imbrel.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.am24.imbrel.data.local.SelectedPlacePreferencesMapper
import com.am24.imbrel.domain.model.SelectedPlace
import com.am24.imbrel.domain.repository.SelectedPlaceRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

class DataStoreSelectedPlaceRepository(
    private val dataStore: DataStore<Preferences>,
) : SelectedPlaceRepository {
    override suspend fun getSelectedPlace(): SelectedPlace? {
        val preferences =
            dataStore.data
                .catch { failure ->
                    if (failure is IOException) emit(emptyPreferences()) else throw failure
                }.first()
        return SelectedPlacePreferencesMapper.read(preferences)
    }

    override suspend fun saveSelectedPlace(place: SelectedPlace) {
        dataStore.edit { SelectedPlacePreferencesMapper.write(it, place) }
    }

    override suspend fun clearSelectedPlace() {
        dataStore.edit { it.clear() }
    }
}
