package com.am24.imbrel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.am24.imbrel.ads.AdsConsentController
import com.am24.imbrel.ads.UmpConsentGateway
import com.am24.imbrel.domain.error.LocationErrorReason
import com.am24.imbrel.presentation.DialogManager
import com.am24.imbrel.presentation.MainViewModel
import com.am24.imbrel.presentation.PlacesViewModel
import com.am24.imbrel.presentation.SettingsViewModel
import com.am24.imbrel.presentation.WeatherApp
import com.am24.imbrel.presentation.theme.WeatherForecastAppTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private lateinit var adsController: AdsConsentController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel: MainViewModel by viewModel()
        val placesViewModel: PlacesViewModel by viewModel()
        val settingsViewModel: SettingsViewModel by viewModel()
        initializeAds()
        setContent {
            val adsState by adsController.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            var permissionRequestId by rememberSaveable { mutableLongStateOf(-1L) }
            val locationPermissionLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { permissions ->
                    val id = permissionRequestId
                    if (viewModel.isCurrentRequest(id)) {
                        if (permissions.values.any { it }) {
                            checkLocation(viewModel, id)
                        } else {
                            viewModel.requestCurrentLocationWeather(allowCurrentLocation = false, expectedRequestId = id)
                            Toast.makeText(context, getString(R.string.location_permission_denied), Toast.LENGTH_SHORT).show()
                        }
                    }
                }

            val requestLocation = { viewModel.requestLocation() }

            LaunchedEffect(viewModel) {
                viewModel.initializeStartup()
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.locationRequests.collect { request ->
                        if (viewModel.isCurrentRequest(request.id)) {
                            when {
                                hasLocationPermission() -> checkLocation(viewModel, request.id)
                                request.requestPermission -> {
                                    permissionRequestId = request.id
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                        ),
                                    )
                                }
                                else -> viewModel.requestSavedLocationOrPermission(request.id)
                            }
                        }
                    }
                }
            }

            WeatherForecastAppTheme {
                WeatherApp(
                    viewModel,
                    requestLocation,
                    placesViewModel,
                    settingsViewModel,
                    adsState = adsState,
                    onPrivacyOptions = adsController::showPrivacyOptions,
                )
            }
        }
    }

    private fun initializeAds() {
        adsController =
            AdsConsentController(
                UmpConsentGateway(this),
                (application as WeatherForecastApplication).adsInitializer,
                lifecycleScope,
            )
        adsController.start()
    }

    override fun onDestroy() {
        adsController.close()
        super.onDestroy()
    }

    private fun checkLocation(
        viewModel: MainViewModel,
        requestId: Long,
    ) {
        if (!viewModel.isCurrentRequest(requestId)) return
        if (!viewModel.isOnline.value || isLocationEnabled()) {
            viewModel.requestCurrentLocationWeather(expectedRequestId = requestId)
        } else {
            viewModel.requestCurrentLocationWeather(
                allowCurrentLocation = false,
                expectedRequestId = requestId,
                missingLocationReason = LocationErrorReason.Unavailable,
            )
            DialogManager.locationSettingsDialog(
                this,
                object : DialogManager.Listener {
                    override fun onClick(name: String?) {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                },
            )
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
}
