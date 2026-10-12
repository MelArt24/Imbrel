package com.am24.imbrel

import com.am24.imbrel.domain.model.LocationCoordinates
import com.am24.imbrel.domain.model.SelectedPlace
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedPlaceTest {
    @Test
    fun queryNormalization_preservesCaseAndCollapsesWhitespace() {
        assertEquals("New York", SelectedPlace.City.normalizeQuery("  New\t York \n"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun city_rejectsUnnormalizedQuery() {
        SelectedPlace.City(" Kyiv ", "Kyiv")
    }

    @Test(expected = IllegalArgumentException::class)
    fun city_rejectsBlankDisplayName() {
        SelectedPlace.City("Kyiv", " ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun city_rejectsInvalidCoordinates() {
        SelectedPlace.City("Kyiv", "Kyiv", LocationCoordinates(Double.NaN, 30.52))
    }
}
