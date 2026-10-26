package com.am24.imbrel

import android.app.Application
import com.am24.imbrel.ads.GoogleAdsInitializer
import com.am24.imbrel.di.appModule
import com.am24.imbrel.di.useCaseModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class WeatherForecastApplication : Application() {
    val adsInitializer by lazy { GoogleAdsInitializer(this) }

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@WeatherForecastApplication)
            modules(appModule, useCaseModule)
        }
    }
}
