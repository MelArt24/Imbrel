package com.am24.imbrel

import com.am24.imbrel.data.local.SavedCityEntity
import com.am24.imbrel.domain.model.LocationCoordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedCityEntityTest {
    @Test
    fun mergingOfflineResult_preservesMetadataIdentityAndFavorite() {
        val existing = city().copy(id = 42, displayName = "Kyiv, Ukraine", isFavorite = true, isInHistory = false)
        val incoming =
            city().copy(
                displayName = "KYIV",
                normalizedQuery = "KYIV",
                latitude = null,
                longitude = null,
                lastSearchedAtMillis = 200,
            )
        val merged = existing.mergeSearch(incoming)
        assertEquals(42L, merged.id)
        assertEquals("Kyiv, Ukraine", merged.displayName)
        assertEquals(LocationCoordinates(50.45, 30.52), merged.toDomain().coordinates)
        assertEquals(200L, merged.lastSearchedAtMillis)
        assertTrue(merged.isFavorite)
        assertTrue(merged.isInHistory)
    }

    @Test
    fun richerResult_fillsMissingCoordinatesAndNameWithoutRegressingTimestamp() {
        val existing = city().copy(latitude = null, longitude = null, lastSearchedAtMillis = 200)
        val incoming = city().copy(displayName = "Kyiv, Ukraine", lastSearchedAtMillis = 100)
        val merged = existing.mergeSearch(incoming)
        assertEquals(incoming.latitude, merged.latitude)
        assertEquals(incoming.longitude, merged.longitude)
        assertEquals(incoming.displayName, merged.displayName)
        assertEquals(200L, merged.lastSearchedAtMillis)
    }

    @Test
    fun cityWithoutCoordinates_mapsToQuerySelection() {
        val saved = city().copy(latitude = null, longitude = null).toDomain()
        assertNull(saved.coordinates)
        assertEquals("Kyiv", saved.toSelectedPlace().normalizedQuery)
    }

    private fun city() =
        SavedCityEntity(
            queryKey = "kyiv",
            normalizedQuery = "Kyiv",
            displayName = "Kyiv",
            latitude = 50.45,
            longitude = 30.52,
            lastSearchedAtMillis = 100,
        )
}
