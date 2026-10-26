package com.am24.imbrel

import com.am24.imbrel.ads.AdsConsentController
import com.am24.imbrel.ads.AdsInitializer
import com.am24.imbrel.ads.AdsUiState
import com.am24.imbrel.ads.ConsentGateway
import com.am24.imbrel.ads.ConsentSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdsConsentControllerTest {
    @Test
    fun consentAndSdkReadiness_areBothRequiredAndStartIsIdempotent() =
        runTest {
            val consent = FakeConsent()
            val initializer = FakeInitializer()
            val controller = AdsConsentController(consent, initializer, backgroundScope)
            controller.start()
            controller.start()
            runCurrent()
            assertEquals(1, consent.requests)
            assertTrue(controller.uiState.value.formInProgress)
            assertFalse(controller.uiState.value.requestsAllowed)
            consent.complete(ConsentSnapshot(canRequestAds = true))
            runCurrent()
            assertEquals(1, initializer.calls)
            assertFalse(controller.uiState.value.requestsAllowed)
            initializer.ready.value = true
            runCurrent()
            assertTrue(controller.uiState.value.showBanner(isHome = true))
            assertFalse(controller.uiState.value.showBanner(isHome = false))
            consent.complete(ConsentSnapshot(canRequestAds = true))
            controller.start()
            runCurrent()
            assertEquals(1, initializer.calls)
            assertEquals(1, consent.requests)
        }

    @Test
    fun deniedConsent_blocksInitializationEvenIfSdkWasAlreadyReady() =
        runTest {
            val consent = FakeConsent()
            val initializer = FakeInitializer().apply { ready.value = true }
            val controller = AdsConsentController(consent, initializer, backgroundScope)
            controller.start()
            consent.complete(ConsentSnapshot())
            runCurrent()
            assertFalse(controller.uiState.value.requestsAllowed)
            assertEquals(0, initializer.calls)
        }

    @Test
    fun umpErrors_useOnlyUmpCanRequestAdsIncludingPreviousConsent() =
        runTest {
            for (allowed in listOf(false, true)) {
                val consent = FakeConsent()
                val initializer = FakeInitializer().apply { ready.value = true }
                val controller = AdsConsentController(consent, initializer, backgroundScope)
                controller.start()
                consent.complete(ConsentSnapshot(canRequestAds = allowed, hasError = true))
                runCurrent()
                assertEquals(allowed, controller.uiState.value.requestsAllowed)
                assertTrue(controller.uiState.value.hasPrivacyError)
            }
        }

    @Test
    fun privacyForm_blocksRequestsWhileOpenAndRevocationRemovesPermission() =
        runTest {
            val consent = FakeConsent()
            val initializer = FakeInitializer().apply { ready.value = true }
            val controller = AdsConsentController(consent, initializer, backgroundScope)
            controller.start()
            consent.complete(ConsentSnapshot(canRequestAds = true, privacyOptionsRequired = true))
            runCurrent()
            controller.showPrivacyOptions()
            controller.showPrivacyOptions()
            runCurrent()
            assertEquals(1, consent.privacyRequests)
            assertFalse(controller.uiState.value.requestsAllowed)
            consent.complete(ConsentSnapshot(canRequestAds = false, privacyOptionsRequired = true))
            runCurrent()
            assertFalse(controller.uiState.value.requestsAllowed)
            assertFalse(controller.uiState.value.formInProgress)
            assertTrue(controller.uiState.value.privacyOptionsRequired)
            assertEquals(1, initializer.calls)
        }

    @Test
    fun privacyFormFailure_restoresUmpPermissionWithoutDuplicateInitialization() =
        runTest {
            val consent = FakeConsent()
            val initializer = FakeInitializer().apply { ready.value = true }
            val controller = AdsConsentController(consent, initializer, backgroundScope)
            controller.start()
            consent.complete(ConsentSnapshot(canRequestAds = true, privacyOptionsRequired = true))
            runCurrent()
            controller.showPrivacyOptions()
            consent.complete(ConsentSnapshot(canRequestAds = true, privacyOptionsRequired = true, hasError = true))
            runCurrent()
            assertTrue(controller.uiState.value.requestsAllowed)
            assertTrue(controller.uiState.value.hasPrivacyError)
            assertEquals(1, initializer.calls)
        }

    @Test
    fun privacyEntryIsUnavailableWhenNotRequiredAndLateCallbacksAreIgnoredAfterClose() =
        runTest {
            val consent = FakeConsent()
            val initializer = FakeInitializer()
            val controller = AdsConsentController(consent, initializer, backgroundScope)
            controller.showPrivacyOptions()
            assertEquals(0, consent.privacyRequests)
            controller.start()
            controller.close()
            consent.complete(ConsentSnapshot(canRequestAds = true))
            runCurrent()
            assertFalse(controller.uiState.value.requestsAllowed)
            assertEquals(0, initializer.calls)
        }

    @Test
    fun bannerVisibility_isRestrictedToHomeAndEveryGateMustPass() {
        val allowed = AdsUiState(canRequestAds = true, sdkReady = true)
        assertTrue(allowed.showBanner(true))
        assertFalse(allowed.showBanner(false))
        assertFalse(allowed.copy(formInProgress = true).showBanner(true))
        assertFalse(allowed.copy(sdkReady = false).showBanner(true))
        assertFalse(allowed.copy(canRequestAds = false).showBanner(true))
    }

    private class FakeConsent : ConsentGateway {
        var requests = 0
        var privacyRequests = 0
        private lateinit var callback: (ConsentSnapshot) -> Unit

        override fun gatherConsent(onComplete: (ConsentSnapshot) -> Unit) {
            requests++
            callback = onComplete
        }

        override fun showPrivacyOptions(onComplete: (ConsentSnapshot) -> Unit) {
            privacyRequests++
            callback = onComplete
        }

        fun complete(snapshot: ConsentSnapshot) = callback(snapshot)
    }

    private class FakeInitializer : AdsInitializer {
        override val ready = MutableStateFlow(false)
        var calls = 0

        override fun initialize() {
            calls++
        }
    }
}
