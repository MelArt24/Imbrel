package com.am24.imbrel.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.am24.imbrel.R
import com.am24.imbrel.domain.model.SavedCity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun FavoriteCityCard(
    city: SavedCity,
    enabled: Boolean,
    onCitySelected: (SavedCity) -> Unit,
    onFavoriteChanged: (SavedCity, Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorResource(R.color.places_favorite_background),
    ) {
        Row(
            modifier = Modifier.clickable { onCitySelected(city) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = colorResource(R.color.places_icon_background)) {
                Icon(
                    painterResource(R.drawable.ic_places),
                    null,
                    Modifier.padding(12.dp).size(24.dp),
                    tint = colorResource(R.color.places_text),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    city.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = colorResource(R.color.places_text),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (city.normalizedQuery != city.displayName) city.normalizedQuery else stringResource(R.string.favorite_city_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorResource(R.color.places_secondary_text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FavoriteButton(city, enabled, onFavoriteChanged)
        }
    }
}

@Composable
internal fun RecentCityRow(
    city: SavedCity,
    enabled: Boolean,
    onCitySelected: (SavedCity) -> Unit,
    onFavoriteChanged: (SavedCity, Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colorResource(R.color.places_recent_background),
    ) {
        Row(
            modifier = Modifier.clickable { onCitySelected(city) }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_history), null, Modifier.size(20.dp), tint = colorResource(R.color.places_secondary_text))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    city.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorResource(R.color.places_text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    lastSearchLabel(city.lastSearchedAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorResource(R.color.places_secondary_text),
                )
            }
            FavoriteButton(city, enabled, onFavoriteChanged)
        }
    }
}

@Composable
private fun FavoriteButton(
    city: SavedCity,
    enabled: Boolean,
    onFavoriteChanged: (SavedCity, Boolean) -> Unit,
) {
    IconButton(onClick = { onFavoriteChanged(city, !city.isFavorite) }, enabled = enabled) {
        Icon(
            painter = painterResource(if (city.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline),
            contentDescription = stringResource(if (city.isFavorite) R.string.remove_favorite else R.string.add_favorite, city.displayName),
            tint = colorResource(if (city.isFavorite) R.color.places_text else R.color.places_secondary_text),
        )
    }
}

@Composable
private fun lastSearchLabel(timestampMillis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val searchedAt = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val time = searchedAt.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    return when (searchedAt.toLocalDate()) {
        today -> stringResource(R.string.searched_today, time)
        today.minusDays(1) -> stringResource(R.string.searched_yesterday, time)
        else -> searchedAt.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
    }
}
