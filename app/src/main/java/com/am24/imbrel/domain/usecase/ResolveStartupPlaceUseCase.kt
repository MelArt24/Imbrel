package com.am24.imbrel.domain.usecase

import com.am24.imbrel.domain.model.AppSettings
import com.am24.imbrel.domain.model.SelectedPlace
import com.am24.imbrel.domain.model.StartupMode
import com.am24.imbrel.domain.repository.AppSettingsRepository
import com.am24.imbrel.domain.repository.SelectedPlaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class ResolveStartupPlaceUseCase(
    private val settings: AppSettingsRepository,
    private val selectedPlace: SelectedPlaceRepository,
) {
    suspend operator fun invoke(): SelectedPlace {
        val preferences = safely { settings.observeSettings().first() } ?: AppSettings()
        return when (preferences.startupMode) {
            StartupMode.CurrentLocation -> SelectedPlace.CurrentLocation
            StartupMode.DefaultCity -> preferences.defaultCity ?: lastSelectedOrFallback(preferences)
            StartupMode.LastSelected -> lastSelectedOrFallback(preferences)
        }
    }

    private suspend fun lastSelectedOrFallback(preferences: AppSettings): SelectedPlace =
        safely {
            selectedPlace.getSelectedPlace()
        } ?: preferences.defaultCity ?: SelectedPlace.CurrentLocation

    private suspend fun <T> safely(action: suspend () -> T): T? =
        try {
            action()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
}
