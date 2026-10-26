package com.am24.imbrel.domain.repository

import com.am24.imbrel.domain.model.AppSettings
import com.am24.imbrel.domain.model.SelectedPlace
import com.am24.imbrel.domain.model.StartupMode
import kotlinx.coroutines.flow.Flow

interface AppSettingsRepository {
    fun observeSettings(): Flow<AppSettings>

    suspend fun setStartupMode(mode: StartupMode)

    suspend fun setDefaultCity(city: SelectedPlace.City?)
}
