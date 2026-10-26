package com.am24.imbrel.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.am24.imbrel.data.cache.ClockTimeProvider
import com.am24.imbrel.data.cache.TimeProvider
import com.am24.imbrel.data.cache.WeatherCachePolicy
import com.am24.imbrel.data.local.DataStoreSavedLocationLocalDataSource
import com.am24.imbrel.data.local.RoomWeatherLocalDataSource
import com.am24.imbrel.data.local.SavedLocationLocalDataSource
import com.am24.imbrel.data.local.WeatherDatabase
import com.am24.imbrel.data.local.WeatherLocalDataSource
import com.am24.imbrel.data.network.AndroidNetworkMonitor
import com.am24.imbrel.data.remote.RetrofitClient
import com.am24.imbrel.data.remote.WeatherApiService
import com.am24.imbrel.data.repository.DataStoreAppSettingsRepository
import com.am24.imbrel.data.repository.DataStoreSelectedPlaceRepository
import com.am24.imbrel.data.repository.GeocodingRepositoryImpl
import com.am24.imbrel.data.repository.LocationRepositoryImpl
import com.am24.imbrel.data.repository.RoomPlacesRepository
import com.am24.imbrel.data.repository.WeatherRepositoryImpl
import com.am24.imbrel.domain.network.NetworkMonitor
import com.am24.imbrel.domain.repository.AppSettingsRepository
import com.am24.imbrel.domain.repository.GeocodingRepository
import com.am24.imbrel.domain.repository.LocationRepository
import com.am24.imbrel.domain.repository.PlacesRepository
import com.am24.imbrel.domain.repository.SelectedPlaceRepository
import com.am24.imbrel.domain.repository.WeatherRepository
import com.am24.imbrel.domain.usecase.ObserveCachedForecastsUseCase
import com.am24.imbrel.domain.usecase.OpenCachedForecastUseCase
import com.am24.imbrel.domain.usecase.ResolveStartupPlaceUseCase
import com.am24.imbrel.presentation.AndroidWeatherConditionLocalizer
import com.am24.imbrel.presentation.MainViewModel
import com.am24.imbrel.presentation.PlacesViewModel
import com.am24.imbrel.presentation.SettingsViewModel
import com.am24.imbrel.presentation.WeatherConditionLocalizer
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val Context.locationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "last_current_location",
)

private val Context.selectedPlaceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "selected_place",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

val appModule =
    module {
        single<DataStore<Preferences>>(named("app_settings")) { get<Context>().appSettingsDataStore }
        single<AppSettingsRepository> {
            DataStoreAppSettingsRepository(get(named("app_settings")))
        }
        single { ResolveStartupPlaceUseCase(get(), get()) }
        viewModel { SettingsViewModel(get(), get(), get()) }
        single<NetworkMonitor> { AndroidNetworkMonitor(get<Context>()) }
        single<DataStore<Preferences>> { get<Context>().locationDataStore }
        single<DataStore<Preferences>>(named("selected_place")) { get<Context>().selectedPlaceDataStore }
        single<SelectedPlaceRepository> { DataStoreSelectedPlaceRepository(get(named("selected_place"))) }
        single<SavedLocationLocalDataSource> { DataStoreSavedLocationLocalDataSource(get()) }
        single<GeocodingRepository> { GeocodingRepositoryImpl(get()) }
        single<LocationRepository> {
            LocationRepositoryImpl(
                context = get(),
                geocodingRepository = get(),
                savedLocationLocalDataSource = get(),
            )
        }
        single<WeatherConditionLocalizer> { AndroidWeatherConditionLocalizer(get()) }
        single<WeatherApiService> { RetrofitClient.weatherApiService }
        single {
            Room
                .databaseBuilder(
                    get(),
                    WeatherDatabase::class.java,
                    "weather_cache.db",
                ).addMigrations(WeatherDatabase.MIGRATION_1_2)
                .build()
        }
        single { get<WeatherDatabase>().weatherDao() }
        single { get<WeatherDatabase>().placesDao() }
        single<PlacesRepository> { RoomPlacesRepository(get(), get()) }
        single<WeatherLocalDataSource> { RoomWeatherLocalDataSource(get()) }
        single<TimeProvider> { ClockTimeProvider() }
        single { WeatherCachePolicy() }

        single<WeatherRepository> {
            WeatherRepositoryImpl(
                apiService = get(),
                localDataSource = get(),
                timeProvider = get(),
                cachePolicy = get(),
                networkMonitor = get(),
                placesDao = get(),
            )
        }
        single {
            ObserveCachedForecastsUseCase(get())
        }
        single {
            OpenCachedForecastUseCase(get())
        }
        viewModel { PlacesViewModel(get(), get(), get(), get(), get()) }

        viewModel {
            MainViewModel(
                getCurrentWeatherUseCase = get(),
                getCurrentLocationUseCase = get(),
                searchCityWeatherUseCase = get(),
                selectedPlaceRepository = get(),
                recordCitySearchUseCase = get(),
                openCachedForecast = get(),
                resolveStartupPlace = get(),
                weatherPresentationMapper = get(),
                networkMonitor = get(),
            )
        }
    }
