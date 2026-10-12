package com.am24.imbrel.data.local

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.am24.imbrel.domain.model.LocationCoordinates
import com.am24.imbrel.domain.model.SelectedPlace

internal object SelectedPlacePreferencesMapper {
    private val TYPE = stringPreferencesKey("type")
    private val QUERY = stringPreferencesKey("normalized_query")
    private val DISPLAY_NAME = stringPreferencesKey("display_name")
    private val LATITUDE = doublePreferencesKey("latitude")
    private val LONGITUDE = doublePreferencesKey("longitude")

    fun read(preferences: Preferences): SelectedPlace? =
        try {
            when (preferences[TYPE]) {
                "current_location" -> SelectedPlace.CurrentLocation
                "city" -> readCity(preferences)
                else -> null
            }
        } catch (_: ClassCastException) {
            // A preference with the expected name may have an incompatible stored type.
            null
        } catch (_: IllegalArgumentException) {
            // Invalid persisted fields must not become a usable domain selection.
            null
        }

    private fun readCity(preferences: Preferences): SelectedPlace.City? {
        val query = preferences[QUERY] ?: return null
        val displayName = preferences[DISPLAY_NAME] ?: return null
        val latitude = preferences[LATITUDE]
        val longitude = preferences[LONGITUDE]
        if ((latitude == null) != (longitude == null)) return null
        val coordinates =
            if (latitude != null && longitude != null) {
                LocationCoordinates(latitude, longitude)
            } else {
                null
            }
        return SelectedPlace.City(query, displayName, coordinates)
    }

    fun write(
        preferences: MutablePreferences,
        place: SelectedPlace,
    ) {
        // This mapper owns a dedicated store; replacing a selection removes stale fields.
        preferences.clear()
        when (place) {
            SelectedPlace.CurrentLocation -> preferences[TYPE] = "current_location"
            is SelectedPlace.City -> {
                preferences[TYPE] = "city"
                preferences[QUERY] = place.normalizedQuery
                preferences[DISPLAY_NAME] = place.displayName
                place.coordinates?.let {
                    preferences[LATITUDE] = it.latitude
                    preferences[LONGITUDE] = it.longitude
                }
            }
        }
    }
}
