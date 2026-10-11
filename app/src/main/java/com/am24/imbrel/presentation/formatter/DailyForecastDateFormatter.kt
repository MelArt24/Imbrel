package com.am24.imbrel.presentation.formatter

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

internal fun formatDailyForecastDate(
    date: String,
    locale: Locale,
): String =
    try {
        val weekday = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE", locale))
        "$date ($weekday)"
    } catch (_: DateTimeParseException) {
        date
    }
