package com.am24.imbrel.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.am24.imbrel.domain.model.SavedCity
import com.am24.imbrel.domain.usecase.ClearSearchHistoryUseCase
import com.am24.imbrel.domain.usecase.ObserveFavoriteCitiesUseCase
import com.am24.imbrel.domain.usecase.ObserveSearchHistoryUseCase
import com.am24.imbrel.domain.usecase.SetCityFavoriteUseCase
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

data class PlacesUiState(
    val favorites: List<SavedCity> = emptyList(),
    val recentSearches: List<SavedCity> = emptyList(),
    val isLoading: Boolean = true,
    val hasStorageError: Boolean = false,
    val isUpdating: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlacesViewModel(
    observeSearchHistory: ObserveSearchHistoryUseCase,
    observeFavoriteCities: ObserveFavoriteCitiesUseCase,
    private val setCityFavorite: SetCityFavoriteUseCase,
    private val clearSearchHistory: ClearSearchHistoryUseCase,
) : ViewModel() {
    private val operationState = MutableStateFlow(OperationState())
    private val mutationMutex = Mutex()
    private val reload = MutableStateFlow(0)
    val uiState =
        reload
            .flatMapLatest {
                combine(
                    observeSearchHistory(),
                    observeFavoriteCities(),
                    operationState,
                ) { history, favorites, operation ->
                    PlacesUiState(
                        favorites,
                        history,
                        false,
                        operation.hasError,
                        operation.isUpdating,
                    )
                }.onStart { emit(PlacesUiState()) }
                    .catch { emit(PlacesUiState(isLoading = false, hasStorageError = true)) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PlacesUiState())

    fun retry() {
        operationState.update { it.copy(hasError = false) }
        reload.update { it + 1 }
    }

    fun setFavorite(
        city: SavedCity,
        isFavorite: Boolean,
    ) {
        mutate { setCityFavorite(city.id, isFavorite) }
    }

    fun clearHistory() {
        mutate { clearSearchHistory() }
    }

    private fun mutate(action: suspend () -> Unit) {
        viewModelScope.launch {
            mutationMutex.withLock {
                operationState.value = OperationState(isUpdating = true)
                try {
                    action()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    operationState.update { it.copy(hasError = true) }
                } finally {
                    operationState.update { it.copy(isUpdating = false) }
                }
            }
        }
    }

    private data class OperationState(
        val hasError: Boolean = false,
        val isUpdating: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
