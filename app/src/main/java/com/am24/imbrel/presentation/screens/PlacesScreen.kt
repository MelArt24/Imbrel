package com.am24.imbrel.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.am24.imbrel.R
import com.am24.imbrel.domain.model.SavedCity
import com.am24.imbrel.presentation.PlacesUiState
import com.am24.imbrel.presentation.PlacesViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun PlacesScreen(
    viewModel: PlacesViewModel,
    onSearchClick: () -> Unit = {},
    onCitySelected: (SavedCity) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlacesScreenContent(state, onCitySelected, viewModel::setFavorite, viewModel::clearHistory, viewModel::retry, onSearchClick)
}

@Composable
fun PlacesScreenContent(
    state: PlacesUiState,
    onCitySelected: (SavedCity) -> Unit,
    onFavoriteChanged: (SavedCity, Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onRetry: () -> Unit = {},
    onSearchClick: () -> Unit = {},
) {
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colorResource(R.color.places_background)
    ) {
        PlacesList(state, onCitySelected, onFavoriteChanged, onSearchClick, { showClearConfirmation = true }, onRetry)
    }
    if (showClearConfirmation) {
        ClearHistoryDialog(
            onDismiss = { showClearConfirmation = false },
            onConfirm = {
                showClearConfirmation = false
                onClearHistory()
            },
        )
    }
}

@Composable
private fun PlacesList(
    state: PlacesUiState,
    onCitySelected: (SavedCity) -> Unit,
    onFavoriteChanged: (SavedCity, Boolean) -> Unit,
    onSearchClick: () -> Unit,
    onClearRequested: () -> Unit,
    onRetry: () -> Unit,
) {
    val favoriteIds = state.favorites.map { it.id }.toSet()
    val recent = state.recentSearches.filterNot { it.isFavorite || it.id in favoriteIds }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PlacesHeader() }
        if (state.isLoading) {
            item {
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        if (state.hasStorageError) {
            item {
                PlacesStorageError(onRetry)
            }
        }
        item {
            SectionHeader(
                R.string.favorites,
                state.favorites.size
            )
        }
        if (state.favorites.isEmpty() && !state.isLoading && !state.hasStorageError) {
            item {
                EmptyPlaces(
                    R.drawable.ic_star_outline,
                    R.string.favorites_empty,
                    onSearchClick
                )
            }
        }
        items(state.favorites, key = { "favorite-${it.id}" }) { city ->
            FavoriteCityCard(city, !state.isUpdating, onCitySelected, onFavoriteChanged)
        }
        item {
            SectionHeader(R.string.recent_searches, recent.size) {
                IconButton(onClick = onClearRequested, enabled = state.recentSearches.isNotEmpty() && !state.isUpdating) {
                    Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.clear_history))
                }
            }
        }
        if (recent.isEmpty() && !state.isLoading && !state.hasStorageError) {
            item {
                val message = if (state.recentSearches.isEmpty()) R.string.recent_searches_empty else R.string.recent_searches_all_favorites
                EmptyPlaces(R.drawable.ic_history, message, onSearchClick)
            }
        }
        items(recent, key = { "recent-${it.id}" }) { city ->
            RecentCityRow(city, !state.isUpdating, onCitySelected, onFavoriteChanged)
        }
    }
}

@Composable
private fun PlacesHeader() {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.navigation_places),
            style = MaterialTheme.typography.headlineLarge,
            color = colorResource(R.color.places_text),
        )
        Text(
            stringResource(R.string.places_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = colorResource(R.color.places_secondary_text),
        )
    }
}

@Composable
private fun SectionHeader(
    title: Int,
    count: Int,
    action: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = colorResource(R.color.places_text),
        )
        Surface(shape = RoundedCornerShape(12.dp), color = colorResource(R.color.places_icon_background)) {
            Text(
                count.toString(),
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = colorResource(R.color.places_text),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        action()
    }
}

@Composable
private fun FavoriteCityCard(
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
private fun RecentCityRow(
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
private fun EmptyPlaces(
    icon: Int,
    message: Int,
    onSearchClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colorResource(R.color.places_recent_background),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(painterResource(icon), null, Modifier.size(32.dp), tint = colorResource(R.color.places_secondary_text))
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = colorResource(R.color.places_secondary_text))
            TextButton(onClick = onSearchClick) { Text(stringResource(R.string.search_city), color = colorResource(R.color.places_text)) }
        }
    }
}

@Composable
private fun PlacesStorageError(onRetry: () -> Unit) {
    Column {
        Text(stringResource(R.string.places_storage_error), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun ClearHistoryDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_history_title)) },
        text = { Text(stringResource(R.string.clear_history_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.clear_history)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
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
