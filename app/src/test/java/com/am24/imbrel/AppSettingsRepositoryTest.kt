package com.am24.imbrel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.am24.imbrel.data.repository.DataStoreAppSettingsRepository
import com.am24.imbrel.domain.model.AppSettings
import com.am24.imbrel.domain.model.LocationCoordinates
import com.am24.imbrel.domain.model.SelectedPlace
import com.am24.imbrel.domain.model.StartupMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class AppSettingsRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val city = SelectedPlace.City("Kyiv", "Kyiv", LocationCoordinates(50.45, 30.52))

    @Test
    fun firstLaunch_andResetUseLastSelectedWhileCityChangesPreserveMode() =
        runTest {
            val repository = DataStoreAppSettingsRepository(MemoryStore())
            assertEquals(AppSettings(), repository.observeSettings().first())
            repository.setDefaultCity(city)
            repository.setStartupMode(StartupMode.DefaultCity)
            repository.setDefaultCity(city.copy(displayName = "Updated"))
            assertEquals(StartupMode.DefaultCity, repository.observeSettings().first().startupMode)
            repository.setDefaultCity(null)
            assertEquals(AppSettings(), repository.observeSettings().first())
            repository.setStartupMode(StartupMode.CurrentLocation)
            repository.setDefaultCity(city)
            repository.setDefaultCity(null)
            assertEquals(AppSettings(StartupMode.CurrentLocation), repository.observeSettings().first())
        }

    @Test(expected = IllegalArgumentException::class)
    fun defaultCityMode_cannotBeEnabledWithoutCity() =
        runTest {
            DataStoreAppSettingsRepository(MemoryStore()).setStartupMode(StartupMode.DefaultCity)
        }

    @Test
    fun diskRestart_preservesFullIndependentCityAndStartupMode() =
        runTest {
            val file = temporaryFolder.newFolder().resolve("settings.preferences_pb")
            val firstJob = SupervisorJob()
            val firstStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + firstJob)) { file }
            val firstRepository = DataStoreAppSettingsRepository(firstStore)
            try {
                firstRepository.setDefaultCity(city)
                firstRepository.setStartupMode(StartupMode.DefaultCity)
            } finally {
                firstJob.cancelAndJoin()
            }
            val secondJob = SupervisorJob()
            val secondStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + secondJob)) { file }
            try {
                assertEquals(
                    AppSettings(StartupMode.DefaultCity, city),
                    DataStoreAppSettingsRepository(secondStore).observeSettings().first(),
                )
            } finally {
                secondJob.cancelAndJoin()
            }
        }

    @Test
    fun malformedCityOrUnknownMode_fallsBackSafely() =
        runTest {
            val store = MemoryStore()
            val repository = DataStoreAppSettingsRepository(store)
            store.data.value = mutablePreferencesOf(stringPreferencesKey("startup_mode") to "DefaultCity")
            assertEquals(AppSettings(), repository.observeSettings().first())
            store.data.value = mutablePreferencesOf(stringPreferencesKey("startup_mode") to "unknown")
            assertEquals(AppSettings(), repository.observeSettings().first())
        }

    @Test
    fun ioFailuresAndCancellation_areNotSilentlyConvertedIntoSavedSettings() =
        runTest {
            for (failure in listOf(IOException("Storage"), CancellationException("Cancelled"))) {
                val repository =
                    DataStoreAppSettingsRepository(
                        object : DataStore<Preferences> {
                            override val data = flow<Preferences> { throw failure }

                            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw failure
                        },
                    )
                val read = runCatching { repository.observeSettings().first() }.exceptionOrNull()
                val write = runCatching { repository.setDefaultCity(city) }.exceptionOrNull()
                assertTrue(read === failure)
                assertTrue(write === failure)
            }
        }

    private class MemoryStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            data.value = transform(data.value)
            return data.value
        }
    }
}
