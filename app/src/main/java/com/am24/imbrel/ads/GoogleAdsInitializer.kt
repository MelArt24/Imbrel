package com.am24.imbrel.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** One instance per application process; no dependency on the weather request scope. */
class GoogleAdsInitializer(
    context: Context,
) : AdsInitializer {
    private val applicationContext = context.applicationContext
    private val started = AtomicBoolean(false)
    private val initialized = MutableStateFlow(false)
    override val ready = initialized.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun initialize() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            try {
                MobileAds.initialize(applicationContext) { initialized.value = true }
            } catch (_: RuntimeException) {
                // A failed advertising initialization must not interrupt weather or navigation.
                initialized.value = false
            }
        }
    }
}
