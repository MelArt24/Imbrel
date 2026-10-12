package com.am24.imbrel.domain.model

data class CityWeatherResult(
    val forecast: WeatherForecast,
    val city: String,
    val normalizedQuery: String,
    val coordinates: LocationCoordinates? = null,
) {
    fun toSelectedPlace(): SelectedPlace.City = SelectedPlace.City(normalizedQuery, city, coordinates)
}
