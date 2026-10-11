package com.am24.imbrel

import com.am24.imbrel.presentation.formatter.formatDailyForecastDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DailyForecastDateFormatterTest {
    @Test
    fun monday_usesEnglishAbbreviation() {
        assertEquals("2026-10-12 (Mon)", formatDailyForecastDate("2026-10-12", Locale.ENGLISH))
    }

    @Test
    fun monday_usesUkrainianAbbreviation() {
        assertEquals("2026-10-12 (пн)", formatDailyForecastDate("2026-10-12", Locale.forLanguageTag("uk-UA")))
    }

    @Test
    fun sunday_isCalculatedFromForecastDate() {
        assertEquals("2026-10-18 (Sun)", formatDailyForecastDate("2026-10-18", Locale.ENGLISH))
        assertEquals("2026-10-18 (нд)", formatDailyForecastDate("2026-10-18", Locale.forLanguageTag("uk-UA")))
    }

    @Test
    fun invalidDates_areReturnedUnchanged() {
        val dates = listOf("", "invalid", "12/10/2026", "2026-02-30", "2026-10-12 12:00")
        val locales = listOf(Locale.ENGLISH, Locale.forLanguageTag("uk-UA"))

        locales.forEach { locale ->
            dates.forEach { date ->
                assertEquals(date, formatDailyForecastDate(date, locale))
            }
        }
    }
}
