package com.am24.imbrel.ads

import android.app.Activity
import com.am24.imbrel.BuildConfig
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.lang.ref.WeakReference

class UmpConsentGateway(
    activity: Activity,
) : ConsentGateway {
    private val activityReference = WeakReference(activity)
    private val information = UserMessagingPlatform.getConsentInformation(activity.applicationContext)

    override fun gatherConsent(onComplete: (ConsentSnapshot) -> Unit) {
        val activity = activityReference.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return
        information.requestConsentInfoUpdate(
            activity,
            requestParameters(activity),
            {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error -> onComplete(snapshot(error != null)) }
                }
            },
            { onComplete(snapshot(hasError = true)) },
        )
    }

    override fun showPrivacyOptions(onComplete: (ConsentSnapshot) -> Unit) {
        val activity = activityReference.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error -> onComplete(snapshot(error != null)) }
    }

    private fun requestParameters(activity: Activity): ConsentRequestParameters {
        val parameters = ConsentRequestParameters.Builder()
        if (BuildConfig.DEBUG && BuildConfig.UMP_FORCE_EEA) {
            val debug =
                ConsentDebugSettings
                    .Builder(activity)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            if (BuildConfig.UMP_TEST_DEVICE_HASH.isNotBlank()) debug.addTestDeviceHashedId(BuildConfig.UMP_TEST_DEVICE_HASH)
            parameters.setConsentDebugSettings(debug.build())
        }
        return parameters.build()
    }

    private fun snapshot(hasError: Boolean) =
        ConsentSnapshot(
            canRequestAds = information.canRequestAds(),
            privacyOptionsRequired =
                information.privacyOptionsRequirementStatus == PrivacyOptionsRequirementStatus.REQUIRED,
            hasError = hasError,
        )
}
