package com.am24.imbrel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.am24.imbrel.data.local.SelectedPlacePreferencesMapper
import com.am24.imbrel.data.repository.DataStoreSelectedPlaceRepository
import com.am24.imbrel.domain.model.LocationCoordinates
import com.am24.imbrel.domain.model.SelectedPlace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.IOException

class SelectedPlaceRepositoryTest {
    @Test
    fun selection_roundTripsAndClearsStaleCityFields() =
        runTest {
            val store = MemoryDataStore()
            val repository = DataStoreSelectedPlaceRepository(store)
            val city = SelectedPlace.City("Kyiv", "Kyiv", LocationCoordinates(50.45, 30.52))
            assertNull(repository.getSelectedPlace())

            repository.saveSelectedPlace(city)
            assertEquals(city, repository.getSelectedPlace())
            repository.saveSelectedPlace(city.copy(coordinates = null))
            assertEquals(city.copy(coordinates = null), repository.getSelectedPlace())
            assertEquals(
                3,
                store.data.value
                    .asMap()
                    .size,
            )

            repository.saveSelectedPlace(SelectedPlace.CurrentLocation)
            assertSame(SelectedPlace.CurrentLocation, repository.getSelectedPlace())
            assertEquals(
                1,
                store.data.value
                    .asMap()
                    .size,
            )
            repository.clearSelectedPlace()
            assertNull(repository.getSelectedPlace())
            assertEquals(emptyPreferences(), store.data.value)
        }

    @Test
    fun malformedRecords_returnNull() {
        val type = stringPreferencesKey("type")
        val query = stringPreferencesKey("normalized_query")
        val name = stringPreferencesKey("display_name")
        val latitude = doublePreferencesKey("latitude")
        val longitude = doublePreferencesKey("longitude")
        val records =
            listOf(
                emptyPreferences(),
                mutablePreferencesOf(type to "unknown"),
                mutablePreferencesOf(type to "city"),
                mutablePreferencesOf(type to "city", query to "Kyiv"),
                mutablePreferencesOf(type to "city", query to "", name to "Kyiv"),
                mutablePreferencesOf(type to "city", query to " Kyiv ", name to "Kyiv"),
                mutablePreferencesOf(type to "city", query to "Kyiv", name to " "),
                mutablePreferencesOf(type to "city", query to "Kyiv", name to "Kyiv", latitude to 50.45),
                mutablePreferencesOf(type to "city", query to "Kyiv", name to "Kyiv", longitude to 30.52),
                mutablePreferencesOf(type to "city", query to "Kyiv", name to "Kyiv", latitude to Double.NaN, longitude to 30.52),
                mutablePreferencesOf(type to "city", query to "Kyiv", name to "Kyiv", latitude to 91.0, longitude to 30.52),
                mutablePreferencesOf(type to "city", query to "Kyiv", name to "Kyiv", latitude to 50.45, longitude to 181.0),
                mutablePreferencesOf(intPreferencesKey("type") to 1),
                mutablePreferencesOf(type to "city", intPreferencesKey("normalized_query") to 1, name to "Kyiv"),
            )

        records.forEach { assertNull(SelectedPlacePreferencesMapper.read(it)) }
    }

    @Test
    fun readIoFailure_returnsNoSelection() =
        runTest {
            val repository = DataStoreSelectedPlaceRepository(FailingDataStore(IOException("read")))

            assertNull(repository.getSelectedPlace())
        }

    @Test
    fun cancellation_isPropagated() =
        runTest {
            val cancellation = CancellationException("cancel")
            val repository = DataStoreSelectedPlaceRepository(FailingDataStore(cancellation))
            val failure =
                try {
                    repository.getSelectedPlace()
                    null
                } catch (caught: CancellationException) {
                    caught
                }

            assertSame(cancellation, failure)
        }

    @Test(expected = IOException::class)
    fun writeFailure_isPropagated() =
        runTest {
            DataStoreSelectedPlaceRepository(FailingDataStore(IOException("write")))
                .saveSelectedPlace(SelectedPlace.CurrentLocation)
        }

    private class MemoryDataStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            data.value = transform(data.value)
            return data.value
        }
    }

    private class FailingDataStore(
        private val failure: Throwable,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw failure }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw failure
    }
}
