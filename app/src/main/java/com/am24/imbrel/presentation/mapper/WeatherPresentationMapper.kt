package com.am24.imbrel.presentation.mapper

import com.am24.imbrel.domain.model.WeatherForecast
import com.am24.imbrel.presentation.WeatherConditionLocalizer
import com.am24.imbrel.presentation.model.WeatherModel
import java.time.Clock
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val HOUR_MINUTE_TEXT_LENGTH = 5

class WeatherPresentationMapper(
    private val conditionLocalizer: WeatherConditionLocalizer,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    operator fun invoke(
        forecast: WeatherForecast,
        city: String?,
        includeCurrentWithoutDaily: Boolean = false,
    ): WeatherPresentationResult {
        val cityName = city ?: forecast.cityName ?: "Your city"
        val hourlyWeather =
            forecast.hourly.map { hour ->
                WeatherModel(
                    city = cityName,
                    time = hour.date.substringAfterLast('T').take(HOUR_MINUTE_TEXT_LENGTH),
                    condition = conditionLocalizer.localize(hour.condition, hour.summary),
                    currentTemperature = hour.temperature.toInt().toString() + "\u00B0C",
                    minimumTemperature = "",
                    maximumTemperature = "",
                    imageURL = hour.iconCode.toString(),
                    hourlyWeather = emptyList(),
                )
            }
        val daily =
            forecast.daily.map { day ->
                WeatherModel(
                    city = cityName,
                    time = day.day,
                    condition = conditionLocalizer.localize(day.condition, day.summary),
                    currentTemperature = "",
                    minimumTemperature = day.temperatureMin.toInt().toString(),
                    maximumTemperature = day.temperatureMax.toInt().toString(),
                    imageURL = day.iconCode.toString(),
                    hourlyWeather = hourlyWeather,
                )
            }
        val current =
            daily.firstOrNull().let { firstDay ->
                if (firstDay == null && !includeCurrentWithoutDaily) return@let null
                WeatherModel(
                    city = cityName,
                    time = LocalTime.now(clock).format(CURRENT_TIME_FORMATTER),
                    condition =
                        conditionLocalizer.localize(
                            forecast.current.condition,
                            forecast.current.summary,
                        ),
                    currentTemperature =
                        forecast.current.temperature
                            .toInt()
                            .toString() + "\u00B0C",
                    minimumTemperature = firstDay?.minimumTemperature.orEmpty(),
                    maximumTemperature = firstDay?.maximumTemperature.orEmpty(),
                    imageURL = forecast.current.iconCode.toString(),
                    hourlyWeather = hourlyWeather,
                )
            }
        return WeatherPresentationResult(current = current, daily = daily)
    }

    private companion object {
        val CURRENT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

data class WeatherPresentationResult(
    val current: WeatherModel?,
    val daily: List<WeatherModel>,
)
