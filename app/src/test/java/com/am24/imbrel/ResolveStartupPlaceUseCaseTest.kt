package com.am24.imbrel

import com.am24.imbrel.domain.model.AppSettings
import com.am24.imbrel.domain.model.SelectedPlace
import com.am24.imbrel.domain.model.StartupMode
import com.am24.imbrel.domain.repository.SelectedPlaceRepository
import com.am24.imbrel.domain.usecase.ResolveStartupPlaceUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.IOException

class ResolveStartupPlaceUseCaseTest {
    private val city = SelectedPlace.City("Kyiv", "Kyiv")
    private val last = SelectedPlace.City("Lviv", "Lviv")

    @Test
    fun startupModes_followFallbackOrderWithoutWritingSelections() =
        runTest {
            val cases =
                listOf(
                    Triple(AppSettings(), null, SelectedPlace.CurrentLocation),
                    Triple(AppSettings(defaultCity = city), null, city),
                    Triple(AppSettings(defaultCity = city), last, last),
                    Triple(AppSettings(defaultCity = city), SelectedPlace.CurrentLocation, SelectedPlace.CurrentLocation),
                    Triple(AppSettings(StartupMode.DefaultCity, city), last, city),
                    Triple(AppSettings(StartupMode.DefaultCity), last, last),
                    Triple(AppSettings(StartupMode.DefaultCity), null, SelectedPlace.CurrentLocation),
                    Triple(AppSettings(StartupMode.CurrentLocation, city), last, SelectedPlace.CurrentLocation),
                )
            cases.forEach { (settings, selected, expected) ->
                val resolver = ResolveStartupPlaceUseCase(FakeAppSettingsRepository(settings), selection { selected })
                assertEquals(expected, resolver())
            }
        }

    @Test
    fun readFailures_fallBackIndependentlyAndCancellationPropagates() =
        runTest {
            val settings = FakeAppSettingsRepository().apply { readFailure = IOException("Settings") }
            assertEquals(last, ResolveStartupPlaceUseCase(settings, selection { last })())
            settings.readFailure = null
            settings.settings.value = AppSettings(defaultCity = city)
            assertEquals(city, ResolveStartupPlaceUseCase(settings, selection { throw IOException("Selection") })())
            val cancelled = CancellationException("Cancelled")
            settings.readFailure = cancelled
            assertSame(cancelled, runCatching { ResolveStartupPlaceUseCase(settings, selection { last })() }.exceptionOrNull())
        }

    @Test
    fun explicitModes_doNotReadLastSelection() =
        runTest {
            for (mode in listOf(StartupMode.DefaultCity, StartupMode.CurrentLocation)) {
                val resolver =
                    ResolveStartupPlaceUseCase(FakeAppSettingsRepository(AppSettings(mode, city)), selection { error("Must not read") })
                assertEquals(if (mode == StartupMode.DefaultCity) city else SelectedPlace.CurrentLocation, resolver())
            }
        }

    private fun selection(read: suspend () -> SelectedPlace?) =
        object : SelectedPlaceRepository {
            override suspend fun getSelectedPlace() = read()

            override suspend fun saveSelectedPlace(place: SelectedPlace) = error("Must not write")

            override suspend fun clearSelectedPlace() = error("Must not clear")
        }
}
