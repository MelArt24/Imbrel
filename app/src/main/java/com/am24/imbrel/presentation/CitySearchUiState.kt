package com.am24.imbrel.presentation

// A retained result belongs to one search request and is explicitly acknowledged by its screen.
data class CitySearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val isQueryBlank: Boolean = false,
    val error: WeatherUiError.CitySearch? = null,
    val requestId: Long? = null,
    val successId: Long? = null,
)
