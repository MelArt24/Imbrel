package com.am24.imbrel.domain.model

enum class CacheStatus { Fresh, Offline, Expired }

data class CachedForecast(
    val key: String,
    val forecast: WeatherForecast,
    val updatedAtMillis: Long,
    val status: CacheStatus,
    val place: SelectedPlace.City? = null,
) {
    val label: String get() = place?.displayName ?: key.removePrefix("city:")
}
