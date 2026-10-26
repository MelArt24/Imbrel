package com.am24.imbrel

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.am24.imbrel.ads.AdMobBanner
import com.am24.imbrel.ads.BannerView
import com.google.android.gms.ads.AdSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdMobBannerLifecycleTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun recompositionAndVisibilityChanges_doNotReloadAndFinalRemovalDestroysView() {
        val visible = mutableStateOf(true)
        val present = mutableStateOf(true)
        val revision = mutableStateOf(0)
        lateinit var banner: FakeBanner
        val factory: (Context, AdSize, (Boolean) -> Unit) -> BannerView = { context, _, onLoaded ->
            FakeBanner(context, onLoaded).also { banner = it }
        }
        composeRule.setContent {
            Text(revision.value.toString())
            if (present.value) AdMobBanner(AdSize.BANNER, visible.value, factory)
        }
        composeRule.runOnIdle {
            assertEquals(1, banner.loads)
            revision.value++
            visible.value = false
        }
        composeRule.runOnIdle {
            assertEquals(1, banner.loads)
            assertEquals(View.INVISIBLE, banner.view.visibility)
            assertTrue(banner.pauses > 0)
            visible.value = true
        }
        composeRule.runOnIdle {
            assertEquals(1, banner.loads)
            assertEquals(View.VISIBLE, banner.view.visibility)
            assertTrue(banner.resumes > 0)
            present.value = false
        }
        composeRule.runOnIdle { assertEquals(1, banner.destroys) }
    }

    @Test
    fun failedBanner_keepsContainerAndDoesNotRetryOnRecomposition() {
        val revision = mutableStateOf(0)
        lateinit var banner: FakeBanner
        val factory: (Context, AdSize, (Boolean) -> Unit) -> BannerView = { context, _, _ ->
            FakeBanner(context) {}.also { banner = it }
        }
        composeRule.setContent {
            Text(revision.value.toString())
            AdMobBanner(AdSize.BANNER, true, factory)
        }
        composeRule.runOnIdle {
            assertEquals(View.INVISIBLE, banner.view.visibility)
            revision.value++
        }
        composeRule.runOnIdle { assertEquals(1, banner.loads) }
    }

    @Test
    fun activityPauseAndResume_controlViewWithoutLoadingAnotherAd() {
        lateinit var owner: FakeLifecycleOwner
        lateinit var banner: FakeBanner
        val factory: (Context, AdSize, (Boolean) -> Unit) -> BannerView = { context, _, onLoaded ->
            FakeBanner(context, onLoaded).also { banner = it }
        }
        composeRule.setContent {
            owner = remember { FakeLifecycleOwner().apply { registry.currentState = Lifecycle.State.RESUMED } }
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { AdMobBanner(AdSize.BANNER, true, factory) }
        }
        var previousPauses = 0
        var previousResumes = 0
        composeRule.runOnIdle {
            previousPauses = banner.pauses
            previousResumes = banner.resumes
            owner.registry.currentState = Lifecycle.State.CREATED
        }
        composeRule.runOnIdle {
            assertTrue(banner.pauses > previousPauses)
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        composeRule.runOnIdle {
            assertTrue(banner.resumes > previousResumes)
            assertEquals(1, banner.loads)
        }
    }

    private class FakeLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private class FakeBanner(
        context: Context,
        private val onLoaded: (Boolean) -> Unit,
    ) : BannerView {
        override val view = FrameLayout(context)
        var loads = 0
        var pauses = 0
        var resumes = 0
        var destroys = 0

        override fun load() {
            loads++
            onLoaded(true)
        }

        override fun pause() {
            pauses++
        }

        override fun resume() {
            resumes++
        }

        override fun destroy() {
            destroys++
        }
    }
}
