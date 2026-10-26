package com.am24.imbrel.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.am24.imbrel.domain.model.AppSettings
import com.am24.imbrel.domain.model.SavedCity
import com.am24.imbrel.domain.model.StartupMode
import com.am24.imbrel.domain.repository.AppSettingsRepository
import com.am24.imbrel.domain.repository.PlacesRepository
import com.am24.imbrel.domain.repository.WeatherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val cities: List<SavedCity> = emptyList(),
    val cacheCount: Int = 0,
    val isLoading: Boolean = true,
    val hasReadError: Boolean = false,
    val isSaving: Boolean = false,
    val isClearingCache: Boolean = false,
    val hasOperationError: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val settingsRepository: AppSettingsRepository,
    placesRepository: PlacesRepository,
    private val weatherRepository: WeatherRepository,
) : ViewModel() {
    private val reload = MutableStateFlow(0)
    private val operation = MutableStateFlow(Operation())
    private val mutationMutex = Mutex()
    private val content =
        reload.flatMapLatest {
            combine(
                settingsRepository.observeSettings(),
                placesRepository.observeFavorites(),
                placesRepository.observeHistory(),
                weatherRepository.observeCachedForecasts(),
            ) { settings, favorites, history, forecasts ->
                SettingsUiState(
                    settings = settings,
                    cities = (favorites + history).distinctBy { it.id },
                    cacheCount = forecasts.size,
                    isLoading = false,
                )
            }.onStart { emit(SettingsUiState()) }
                .catch { emit(SettingsUiState(isLoading = false, hasReadError = true)) }
        }
    val uiState =
        combine(content, operation) { state, mutation ->
            state.copy(isSaving = mutation.saving, isClearingCache = mutation.clearing, hasOperationError = mutation.error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    fun retry() {
        operation.update { it.copy(error = false) }
        reload.update { it + 1 }
    }

    fun setStartupMode(mode: StartupMode) = mutate { settingsRepository.setStartupMode(mode) }

    fun setDefaultCity(city: SavedCity) = mutate { settingsRepository.setDefaultCity(city.toSelectedPlace()) }

    fun resetDefaultCity() = mutate { settingsRepository.setDefaultCity(null) }

    fun clearCache() = mutate(clearing = true) { weatherRepository.clearWeatherCache() }

    private fun mutate(
        clearing: Boolean = false,
        action: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            mutationMutex.withLock {
                operation.value = Operation(saving = !clearing, clearing = clearing)
                try {
                    action()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    operation.update { it.copy(error = true) }
                } finally {
                    operation.update { it.copy(saving = false, clearing = false) }
                }
            }
        }
    }

    private data class Operation(
        val saving: Boolean = false,
        val clearing: Boolean = false,
        val error: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
