package com.am24.imbrel.presentation.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.am24.imbrel.R
import com.am24.imbrel.presentation.MainViewModel
import com.am24.imbrel.presentation.WeatherIconHelper
import com.am24.imbrel.presentation.WeatherUiError
import com.am24.imbrel.presentation.WeatherUiEvent
import com.am24.imbrel.presentation.WeatherUiState
import com.am24.imbrel.presentation.formatter.formatDailyForecastDate
import com.am24.imbrel.presentation.model.WeatherModel
import com.am24.imbrel.presentation.theme.Black
import com.am24.imbrel.presentation.theme.BlueBg
import com.am24.imbrel.presentation.theme.OfflineBannerBackgroundColor
import com.am24.imbrel.presentation.theme.ScreenGradientBottomColor
import com.am24.imbrel.presentation.theme.ScreenGradientTopColor
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onLocationRequest: () -> Unit,
    onSearchClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WeatherUiEvent.ShowError -> {
                    Toast
                        .makeText(
                            context,
                            context.getString(event.error.messageResource()),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }
    }

    MainScreenContent(
        uiState = uiState,
        isOnline = isOnline,
        onRetry = viewModel::retryLastRequest,
        onSearchClick = onSearchClick,
        onLocationClick = onLocationRequest,
    )
}

@Composable
fun MainScreenContent(
    uiState: WeatherUiState,
    isOnline: Boolean,
    onRetry: () -> Unit,
    onSearchClick: () -> Unit,
    onLocationClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    ScreenGradientTopColor,
                                    ScreenGradientBottomColor,
                                ),
                        ),
                ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = !isOnline,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                InternetConnectivityBanner()
            }

            Box(modifier = Modifier.weight(1f)) {
                val persistentError = uiState.error?.takeIf { !uiState.hasWeather }
                if (persistentError != null) {
                    InitialErrorContent(
                        error = persistentError,
                        onRetry = if (persistentError is WeatherUiError.CitySearch) onRetry else null,
                        onSearchClick = onSearchClick,
                        onLocationClick = onLocationClick,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                        ) {
                            MainCard(
                                weather = uiState.currentWeather,
                                onSyncClick = onLocationClick,
                                onSearchClick = onSearchClick,
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            WeatherTabs(
                                currentWeather = uiState.currentWeather,
                                dailyWeather = uiState.dailyWeather,
                                isLoading = uiState.isLoading && !uiState.hasWeather,
                            )
                        }

                        if (uiState.hasWeather && (uiState.isRefreshing || uiState.isCitySearchLoading)) {
                            WeatherRefreshOverlay()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherRefreshOverlay() {
    val loadingDescription = stringResource(R.string.loading_weather)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
                .semantics { contentDescription = loadingDescription },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun InternetConnectivityBanner() {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .padding(top = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(8.dp),
        color = OfflineBannerBackgroundColor,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        tonalElevation = 2.dp,
    ) {
        Text(
            text = stringResource(R.string.no_internet_connection),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InitialErrorContent(
    error: WeatherUiError,
    onRetry: (() -> Unit)?,
    onSearchClick: () -> Unit,
    onLocationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = BlueBg.copy(alpha = 0.9f),
                    contentColor = Color.White,
                ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(error.messageResource()),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onLocationClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_sync),
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.use_current_location))
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSearchClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.search_city))
                }
                onRetry?.let { retry ->
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = retry) {
                        Text(
                            text = stringResource(R.string.retry),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainCard(
    weather: WeatherModel?,
    onSyncClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = BlueBg.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = weather?.time ?: "--/--/---- --:--",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
                weather?.let {
                    Image(
                        painter = painterResource(id = WeatherIconHelper.getWeatherIcon(it.imageURL)),
                        contentDescription = stringResource(R.string.current_weather_icon),
                        modifier = Modifier.size(48.dp),
                    )
                } ?: Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = weather?.city ?: stringResource(R.string.your_city),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Text(
                text = weather?.currentTemperature ?: "--°C",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
            )
            Text(
                text = weather?.condition ?: "-",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = if (weather != null) "${weather.maximumTemperature}°C / ${weather.minimumTemperature}°C" else "--°C / --°C",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = stringResource(R.string.search_your_city),
                        tint = Color.White,
                    )
                }
                IconButton(onClick = onSyncClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_sync),
                        contentDescription = stringResource(R.string.temperature_synchronization),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
fun WeatherTabs(
    currentWeather: WeatherModel?,
    dailyWeather: List<WeatherModel>,
    isLoading: Boolean,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val titles = listOf(stringResource(R.string.hours), stringResource(R.string.days))

    Column(modifier = Modifier.clip(RoundedCornerShape(10.dp))) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = BlueBg,
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = Color.White,
                )
            },
        ) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(text = title.uppercase()) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> HoursList(currentWeather, isLoading)
                1 -> DaysList(dailyWeather, isLoading)
            }
        }
    }
}

@Composable
fun HoursList(
    weather: WeatherModel?,
    isLoading: Boolean,
) {
    val hours = weather?.hourlyWeather.orEmpty()

    if (hours.isEmpty() && !isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.no_hourly_data_available),
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    } else if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 8.dp, top = 4.dp),
        ) {
            items(hours) { item ->
                WeatherListItem(item)
            }
        }
    }
}

@Composable
fun DaysList(
    days: List<WeatherModel>,
    isLoading: Boolean,
) {
    val locale = LocalConfiguration.current.locales[0]
    if (days.isEmpty() && !isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.no_daily_data_available),
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    } else if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 8.dp, top = 4.dp),
        ) {
            items(days) { item ->
                WeatherListItem(
                    item = item,
                    timeLabel = formatDailyForecastDate(item.time, locale),
                )
            }
        }
    }
}

@Composable
fun WeatherListItem(
    item: WeatherModel,
    timeLabel: String = item.time,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.8f),
                contentColor = Black,
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                )
                Text(
                    text = item.condition,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Black,
                )
            }
            Text(
                text = item.currentTemperature.ifEmpty { "${item.maximumTemperature}°C / ${item.minimumTemperature}°C" },
                style = MaterialTheme.typography.titleLarge,
                color = BlueBg,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Image(
                painter = painterResource(id = WeatherIconHelper.getWeatherIcon(item.imageURL)),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}
