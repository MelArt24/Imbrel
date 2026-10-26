package com.am24.imbrel.domain.model

enum class StartupMode { LastSelected, DefaultCity, CurrentLocation }

data class AppSettings(
    val startupMode: StartupMode = StartupMode.LastSelected,
    val defaultCity: SelectedPlace.City? = null,
)
