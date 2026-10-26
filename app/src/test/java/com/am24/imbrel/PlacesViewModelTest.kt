package com.am24.imbrel

import com.am24.imbrel.domain.model.SavedCity
import com.am24.imbrel.domain.usecase.ClearSearchHistoryUseCase
import com.am24.imbrel.domain.usecase.ObserveFavoriteCitiesUseCase
import com.am24.imbrel.domain.usecase.ObserveSearchHistoryUseCase
import com.am24.imbrel.domain.usecase.SetCityFavoriteUseCase
import com.am24.imbrel.presentation.PlacesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlacesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun lists_followRepositoryUpdatesAndClearDoesNotRemoveFavorites() =
        runTest(dispatcher) {
            val repository = FakePlacesRepository()
            val viewModel = viewModel(repository)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            runCurrent()
            assertFalse(viewModel.uiState.value.isLoading)
            val favorite = SavedCity(1, "Kyiv", "Kyiv", null, 100, true)
            repository.history.value = listOf(favorite)
            repository.favorites.value = listOf(favorite)
            runCurrent()
            assertEquals(listOf(favorite), viewModel.uiState.value.favorites)
            assertEquals(listOf(favorite), viewModel.uiState.value.recentSearches)
            viewModel.clearHistory()
            runCurrent()
            assertTrue(
                viewModel.uiState.value.recentSearches
                    .isEmpty(),
            )
            assertEquals(listOf(favorite), viewModel.uiState.value.favorites)
            assertEquals(1, repository.clearCount)
        }

    @Test
    fun favoriteMutationFailure_preservesListsAndCanBeRetried() =
        runTest(dispatcher) {
            val repository = FakePlacesRepository()
            val viewModel = viewModel(repository)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            val city = SavedCity(1, "Kyiv", "Kyiv", null, 100, false)
            repository.history.value = listOf(city)
            repository.mutationFailure = IllegalStateException("Storage failed")
            runCurrent()
            viewModel.setFavorite(city, true)
            runCurrent()
            assertTrue(viewModel.uiState.value.hasStorageError)
            assertFalse(viewModel.uiState.value.isUpdating)
            assertEquals(listOf(city), viewModel.uiState.value.recentSearches)
            repository.mutationFailure = null
            viewModel.retry()
            runCurrent()
            assertFalse(viewModel.uiState.value.hasStorageError)
            viewModel.setFavorite(city, true)
            runCurrent()
            assertEquals(1L to true, repository.favoriteChange)
        }

    @Test
    fun failedListObservation_recoversOnRetry() =
        runTest(dispatcher) {
            val repository = FakePlacesRepository().apply { readFailure = IllegalStateException("Read failed") }
            val viewModel = viewModel(repository)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            runCurrent()
            assertTrue(viewModel.uiState.value.hasStorageError)
            repository.readFailure = null
            val city = SavedCity(1, "Kyiv", "Kyiv", null, 100, false)
            repository.history.value = listOf(city)
            viewModel.retry()
            runCurrent()
            assertFalse(viewModel.uiState.value.hasStorageError)
            assertEquals(listOf(city), viewModel.uiState.value.recentSearches)
        }

    private fun viewModel(repository: FakePlacesRepository) =
        PlacesViewModel(
            ObserveSearchHistoryUseCase(repository),
            ObserveFavoriteCitiesUseCase(repository),
            SetCityFavoriteUseCase(repository),
            ClearSearchHistoryUseCase(repository),
        )
}
