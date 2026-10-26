package com.am24.imbrel.ads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ConsentSnapshot(
    val canRequestAds: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
    val hasError: Boolean = false,
)

interface ConsentGateway {
    fun gatherConsent(onComplete: (ConsentSnapshot) -> Unit)

    fun showPrivacyOptions(onComplete: (ConsentSnapshot) -> Unit)
}

interface AdsInitializer {
    val ready: StateFlow<Boolean>

    fun initialize()
}

data class AdsUiState(
    val canRequestAds: Boolean = false,
    val sdkReady: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
    val formInProgress: Boolean = false,
    val hasPrivacyError: Boolean = false,
) {
    val requestsAllowed: Boolean get() = canRequestAds && sdkReady && !formInProgress

    fun showBanner(isHome: Boolean): Boolean = isHome && requestsAllowed
}

/** Activity-scoped orchestration only. Consent itself is always owned and read by UMP. */
class AdsConsentController(
    private val consent: ConsentGateway,
    private val initializer: AdsInitializer,
    scope: CoroutineScope,
) {
    private val consentState = MutableStateFlow(AdsUiState())
    private var started = false
    private var closed = false
    private var operation = 0L
    private var initializationRequested = false
    val uiState =
        combine(consentState, initializer.ready) { state, ready -> state.copy(sdkReady = ready) }
            .stateIn(scope, SharingStarted.Eagerly, AdsUiState())

    fun start() {
        if (started || closed) return
        started = true
        requestConsent()
    }

    fun showPrivacyOptions() {
        if (closed || consentState.value.formInProgress || !consentState.value.privacyOptionsRequired) return
        val id = beginForm()
        consent.showPrivacyOptions { completeForm(id, it) }
    }

    fun close() {
        closed = true
    }

    private fun requestConsent() {
        val id = beginForm()
        consent.gatherConsent { completeForm(id, it) }
    }

    private fun beginForm(): Long {
        consentState.value = consentState.value.copy(formInProgress = true, canRequestAds = false, hasPrivacyError = false)
        return ++operation
    }

    private fun completeForm(
        id: Long,
        snapshot: ConsentSnapshot,
    ) {
        if (closed || id != operation || !consentState.value.formInProgress) return
        consentState.value =
            AdsUiState(
                canRequestAds = snapshot.canRequestAds,
                privacyOptionsRequired = snapshot.privacyOptionsRequired,
                hasPrivacyError = snapshot.hasError,
            )
        if (snapshot.canRequestAds && !initializationRequested) {
            initializationRequested = true
            initializer.initialize()
        }
    }
}
