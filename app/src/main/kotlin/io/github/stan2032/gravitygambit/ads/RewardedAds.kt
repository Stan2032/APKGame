package io.github.stan2032.gravitygambit.ads

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Rewarded-ads abstraction. POLICY (docs/ADS.md): ads in this game are only
 * ever opt-in rewards the player taps for — never interstitials, never forced
 * after matches, never blocking anything.
 *
 * The UI renders ad-reward buttons only while [available] is true, and never
 * for Ad Pass subscribers (they get every boost automatically). The shipped
 * implementation is [NoOpRewardedAds]: no ad SDK, no network, buttons simply
 * don't exist. Integrating a real provider (AdMob rewarded ads) is a drop-in
 * replacement documented step-by-step in docs/ADS.md — it deliberately isn't
 * bundled yet because the SDK requires a real AdMob App ID at startup and
 * changes the app's privacy/Data-Safety posture.
 */
interface RewardedAdsProvider {
    /** True only when an ad is loaded and ready to show right now. */
    val available: StateFlow<Boolean>

    /**
     * Shows the ad; [onReward] fires only if the ad network confirms the
     * reward was earned (user watched through).
     */
    fun show(activity: Activity, onReward: () -> Unit)
}

object NoOpRewardedAds : RewardedAdsProvider {
    override val available: StateFlow<Boolean> = MutableStateFlow(false)
    override fun show(activity: Activity, onReward: () -> Unit) = Unit
}
