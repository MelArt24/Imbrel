package com.am24.imbrel.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.am24.imbrel.R
import com.am24.imbrel.domain.model.CacheStatus
import com.am24.imbrel.domain.model.CachedForecast
import com.am24.imbrel.presentation.AndroidWeatherConditionLocalizer
import java.text.DateFormat
import java.util.Date

@Composable
internal fun CachedWeatherCard(
    entry: CachedForecast,
    onSelected: (CachedForecast) -> Unit,
) {
    val localizer = AndroidWeatherConditionLocalizer(LocalContext.current)
    val status =
        when (entry.status) {
            CacheStatus.Fresh -> R.string.cache_fresh
            CacheStatus.Offline -> R.string.cache_offline
            CacheStatus.Expired -> R.string.cache_expired
        }
    Card(
        onClick = { onSelected(entry) },
        enabled = entry.status != CacheStatus.Expired,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(entry.label, style = MaterialTheme.typography.titleMedium)
            Text(
                "${entry.forecast.current.temperature.toInt()}\u00B0C \u00B7 ${localizer.localize(
                    entry.forecast.current.condition,
                    entry.forecast.current.summary,
                )}",
            )
            Text(stringResource(R.string.cache_updated, DateFormat.getDateTimeInstance().format(Date(entry.updatedAtMillis))))
            Text(stringResource(status), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun CachedForecastNotice(entry: CachedForecast) {
    val status = if (entry.status == CacheStatus.Fresh) R.string.cache_fresh else R.string.cache_offline
    val updatedAt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.updatedAtMillis))
    Text(
        text = stringResource(status) + " \u00B7 " + stringResource(R.string.cache_updated, updatedAt),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}
