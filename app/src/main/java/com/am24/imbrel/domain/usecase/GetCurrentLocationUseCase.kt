package com.am24.imbrel.domain.usecase

import com.am24.imbrel.domain.error.DomainError
import com.am24.imbrel.domain.error.DomainFailureException
import com.am24.imbrel.domain.error.LocationErrorReason
import com.am24.imbrel.domain.error.NetworkErrorReason
import com.am24.imbrel.domain.model.UserLocation
import com.am24.imbrel.domain.network.NetworkMonitor
import com.am24.imbrel.domain.network.isOnlineOrDomainFailure
import com.am24.imbrel.domain.repository.LocationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class GetCurrentLocationUseCase(
    private val locationRepository: LocationRepository,
    private val networkMonitor: NetworkMonitor =
        object : NetworkMonitor {
            override fun isOnline(): Boolean = true

            override fun observeConnectivity(): Flow<Boolean> = flowOf(true)
        },
) {
    /**
     * Returns a saved location when offline or when acquiring a current location fails.
     *
     * If no saved value is available, an offline probe produces an offline domain failure, while
     * an acquisition failure is rethrown unchanged. Saving a newly acquired location is
     * best-effort; cancellation is always propagated.
     */
    suspend operator fun invoke(
        allowCurrentLocation: Boolean = true,
        missingLocationReason: LocationErrorReason = LocationErrorReason.PermissionDenied,
    ): UserLocation {
        if (!allowCurrentLocation) {
            return getSavedLocation() ?: throw DomainFailureException(DomainError.Location(missingLocationReason))
        }

        if (!networkMonitor.isOnlineOrDomainFailure()) {
            return getSavedLocation() ?: throw DomainFailureException(
                DomainError.Network(NetworkErrorReason.Offline),
            )
        }

        val currentLocation =
            try {
                locationRepository.getCurrentLocation()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (
                @Suppress("TooGenericExceptionCaught")
                locationFailure: Exception,
            ) {
                val savedLocation = getSavedLocation()
                return savedLocation ?: throw locationFailure
            }
        try {
            locationRepository.saveLastLocation(currentLocation)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // A current location remains usable when best-effort persistence fails.
        }
        return currentLocation
    }

    suspend fun getSavedLocation(): UserLocation? =
        try {
            locationRepository.getLastSavedLocation()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
}
