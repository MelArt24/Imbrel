package com.am24.imbrel.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.am24.imbrel.data.local.SelectedPlacePreferencesMapper
import com.am24.imbrel.domain.model.AppSettings
import com.am24.imbrel.domain.model.SelectedPlace
import com.am24.imbrel.domain.model.StartupMode
import com.am24.imbrel.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.map

class DataStoreAppSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : AppSettingsRepository {
    override fun observeSettings() = dataStore.data.map(::read)

    override suspend fun setStartupMode(mode: StartupMode) {
        dataStore.edit { preferences ->
            require(mode != StartupMode.DefaultCity || read(preferences).defaultCity != null)
            preferences[STARTUP_MODE] = mode.name
        }
    }

    override suspend fun setDefaultCity(city: SelectedPlace.City?) {
        dataStore.edit { preferences ->
            val previous = read(preferences)
            // The shared city mapper owns the remaining fields in this dedicated store.
            if (city == null) preferences.clear() else SelectedPlacePreferencesMapper.write(preferences, city)
            val mode =
                if (city == null &&
                    previous.startupMode == StartupMode.DefaultCity
                ) {
                    StartupMode.LastSelected
                } else {
                    previous.startupMode
                }
            preferences[STARTUP_MODE] = mode.name
        }
    }

    private fun read(preferences: Preferences): AppSettings {
        val city = SelectedPlacePreferencesMapper.read(preferences) as? SelectedPlace.City
        val mode =
            try {
                StartupMode.entries.firstOrNull { it.name == preferences[STARTUP_MODE] } ?: StartupMode.LastSelected
            } catch (_: ClassCastException) {
                StartupMode.LastSelected
            }
        return AppSettings(if (mode == StartupMode.DefaultCity && city == null) StartupMode.LastSelected else mode, city)
    }

    private companion object {
        val STARTUP_MODE = stringPreferencesKey("startup_mode")
    }
}
