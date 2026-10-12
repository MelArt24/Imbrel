package com.am24.imbrel

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.am24.imbrel.data.local.DataStoreSavedLocationLocalDataSource
import com.am24.imbrel.data.repository.DataStoreSelectedPlaceRepository
import com.am24.imbrel.domain.model.LocationCoordinates
import com.am24.imbrel.domain.model.SavedLocation
import com.am24.imbrel.domain.model.SelectedPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.UUID

class SelectedPlacePersistenceTest {
    private val directory =
        File(
            ApplicationProvider.getApplicationContext<Context>().filesDir,
            "selected-place-test-${UUID.randomUUID()}",
        ).apply { mkdirs() }
    private val scopes = mutableListOf<CoroutineScope>()
    private val selectedFile = File(directory, "selected_place.preferences_pb")

    private fun store(file: File = selectedFile) =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes.add(it) },
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        ) { file }

    @After
    fun tearDown() =
        runBlocking {
            scopes.forEach { it.coroutineContext[Job]?.cancelAndJoin() }
            directory.deleteRecursively()
            Unit
        }

    @Test
    fun city_survivesStoreRecreationWithoutPrecisionLoss() =
        runBlocking {
            val city =
                SelectedPlace.City(
                    "Kyiv",
                    "Kyiv",
                    LocationCoordinates(50.450001234567, 30.523456789012),
                )
            DataStoreSelectedPlaceRepository(store()).saveSelectedPlace(city)
            scopes.last().coroutineContext[Job]?.cancelAndJoin()

            assertEquals(city, DataStoreSelectedPlaceRepository(store()).getSelectedPlace())
        }

    @Test
    fun selectionReplacementAndClear_arePersisted() =
        runBlocking {
            val repository = DataStoreSelectedPlaceRepository(store())
            repository.saveSelectedPlace(SelectedPlace.City("Kyiv", "Kyiv"))
            repository.saveSelectedPlace(SelectedPlace.CurrentLocation)
            scopes.last().coroutineContext[Job]?.cancelAndJoin()
            val restored = DataStoreSelectedPlaceRepository(store())
            assertEquals(SelectedPlace.CurrentLocation, restored.getSelectedPlace())

            restored.clearSelectedPlace()
            scopes.last().coroutineContext[Job]?.cancelAndJoin()
            assertNull(DataStoreSelectedPlaceRepository(store()).getSelectedPlace())
        }

    @Test
    fun incompleteCity_returnsNullAndCanBeReplaced() =
        runBlocking {
            val dataStore = store()
            dataStore.edit { it[stringPreferencesKey("type")] = "city" }
            val repository = DataStoreSelectedPlaceRepository(dataStore)
            assertNull(repository.getSelectedPlace())

            val city = SelectedPlace.City("Kyiv", "Kyiv")
            repository.saveSelectedPlace(city)
            assertEquals(city, repository.getSelectedPlace())
        }

    @Test
    fun corruptFile_isRecoveredAndCanBeWritten() =
        runBlocking {
            selectedFile.writeText("invalid preferences protobuf")
            val repository = DataStoreSelectedPlaceRepository(store())
            assertNull(repository.getSelectedPlace())

            repository.saveSelectedPlace(SelectedPlace.CurrentLocation)
            assertEquals(SelectedPlace.CurrentLocation, repository.getSelectedPlace())
        }

    @Test
    fun selectedPlaceStore_doesNotModifySavedCurrentLocation() =
        runBlocking {
            val savedLocation = SavedLocation(50.45, 30.52, "Kyiv", 123L)
            val locationDataSource =
                DataStoreSavedLocationLocalDataSource(store(File(directory, "last_current_location.preferences_pb")))
            locationDataSource.saveLocation(savedLocation)
            val selectedPlaceRepository = DataStoreSelectedPlaceRepository(store())

            selectedPlaceRepository.saveSelectedPlace(SelectedPlace.City("London", "London"))
            selectedPlaceRepository.clearSelectedPlace()

            assertEquals(savedLocation, locationDataSource.getLocation())
        }
}
