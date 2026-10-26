package com.am24.imbrel

import com.am24.imbrel.domain.model.AppSettings
import com.am24.imbrel.domain.model.SelectedPlace
import com.am24.imbrel.domain.model.StartupMode
import com.am24.imbrel.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

internal class FakeAppSettingsRepository(
    initial: AppSettings = AppSettings(),
) : AppSettingsRepository {
    val settings = MutableStateFlow(initial)
    var readFailure: Exception? = null
    var writeFailure: Exception? = null

    override fun observeSettings() =
        flow {
            readFailure?.let { throw it }
            emitAll(settings)
        }

    override suspend fun setStartupMode(mode: StartupMode) {
        writeFailure?.let { throw it }
        require(mode != StartupMode.DefaultCity || settings.value.defaultCity != null)
        settings.value = settings.value.copy(startupMode = mode)
    }

    override suspend fun setDefaultCity(city: SelectedPlace.City?) {
        writeFailure?.let { throw it }
        settings.value =
            settings.value.copy(
                defaultCity = city,
                startupMode =
                    if (city == null &&
                        settings.value.startupMode == StartupMode.DefaultCity
                    ) {
                        StartupMode.LastSelected
                    } else {
                        settings.value.startupMode
                    },
            )
    }
}
