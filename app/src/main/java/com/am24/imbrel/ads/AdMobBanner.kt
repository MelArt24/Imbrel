package com.am24.imbrel.ads

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdSize

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun AdMobBannerSlot(
    isHome: Boolean,
    state: AdsUiState,
) {
    val context = LocalContext.current
    val orientation = LocalConfiguration.current.orientation
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        val widthDp = maxWidth.value.toInt().coerceAtLeast(1)

        // The standard anchored format is intentional; the SDK's large replacement is not used.
        @Suppress("DEPRECATION")
        val size =
            remember(context, widthDp, orientation) {
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
            }
        val height = size.height.coerceAtLeast(MIN_BANNER_HEIGHT_DP).dp
        BannerPlacement(height, isHome = isHome) {
            if (state.requestsAllowed && size.height > 0) {
                key(size.width, size.height, orientation) { HomeBanner(state.showBanner(isHome), size) }
            }
        }
    }
}

@Composable
private fun HomeBanner(
    isHome: Boolean,
    size: AdSize,
) {
    var requestedOnHome by remember { mutableStateOf(isHome) }
    LaunchedEffect(isHome) { if (isHome) requestedOnHome = true }
    if (requestedOnHome) AdMobBanner(size, isVisible = isHome)
}

@Composable
fun AdMobBanner(
    size: AdSize,
    isVisible: Boolean,
    factory: (Context, AdSize, (Boolean) -> Unit) -> BannerView = ::GoogleBannerView,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var loaded by remember { mutableStateOf(false) }
    val view =
        remember(context, size, factory) {
            try {
                factory(context, size) { loaded = it }
            } catch (_: RuntimeException) {
                null
            }
        } ?: return
    DisposableEffect(view, owner, isVisible) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> if (isVisible) view.resume() else view.pause()
                    Lifecycle.Event.ON_PAUSE -> view.pause()
                    else -> Unit
                }
            }
        owner.lifecycle.addObserver(observer)
        if (isVisible && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.resume() else view.pause()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    key(view) {
        AndroidView(
            factory = {
                view.view.visibility = View.INVISIBLE
                // Only factory runs this request; recompositions and tab switches never reload it.
                view.load()
                view.view
            },
            modifier = Modifier.fillMaxWidth().height(size.height.dp),
            update = { it.visibility = if (isVisible && loaded) View.VISIBLE else View.INVISIBLE },
            onRelease = { view.destroy() },
        )
    }
}

private const val MIN_BANNER_HEIGHT_DP = 50
