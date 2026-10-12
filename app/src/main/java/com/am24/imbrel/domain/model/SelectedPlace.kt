package com.am24.imbrel.domain.model

sealed interface SelectedPlace {
    data object CurrentLocation : SelectedPlace

    data class City(
        val normalizedQuery: String,
        val displayName: String,
        val coordinates: LocationCoordinates? = null,
    ) : SelectedPlace {
        init {
            require(normalizedQuery.isNotBlank() && normalizedQuery == normalizeQuery(normalizedQuery))
            require(displayName.isNotBlank())
            require(coordinates == null || coordinates.isValid())
        }

        companion object {
            private const val MAX_LATITUDE = 90.0
            private const val MAX_LONGITUDE = 180.0

            fun normalizeQuery(query: String): String = query.trim().replace(Regex("\\s+"), " ")

            internal fun LocationCoordinates.isValid(): Boolean =
                latitude.isFinite() &&
                    longitude.isFinite() &&
                    latitude in -MAX_LATITUDE..MAX_LATITUDE &&
                    longitude in -MAX_LONGITUDE..MAX_LONGITUDE
        }
    }
}
