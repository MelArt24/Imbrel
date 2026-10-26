package com.am24.imbrel.ads

import android.content.Context
import android.view.View
import com.am24.imbrel.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/** Small SDK boundary that also allows lifecycle tests without real advertising requests. */
interface BannerView {
    val view: View

    fun load()

    fun pause()

    fun resume()

    fun destroy()
}

internal class GoogleBannerView(
    context: Context,
    size: AdSize,
    private val onLoaded: (Boolean) -> Unit,
) : BannerView {
    override val view =
        AdView(context).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
            setAdSize(size)
            adListener =
                object : AdListener() {
                    override fun onAdLoaded() {
                        onLoaded(true)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        onLoaded(false)
                    }
                }
        }

    override fun load() {
        try {
            view.loadAd(AdRequest.Builder().build())
        } catch (_: RuntimeException) {
            onLoaded(false)
        }
    }

    override fun pause() = view.pause()

    override fun resume() = view.resume()

    override fun destroy() {
        view.adListener = object : AdListener() {}
        view.destroy()
    }
}
