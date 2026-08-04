package io.github.stan2032.gravitygambit

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stan2032.gravitygambit.ads.NoOpRewardedAds
import io.github.stan2032.gravitygambit.billing.BillingManager
import io.github.stan2032.gravitygambit.core.Profile
import io.github.stan2032.gravitygambit.data.ProfileRepository
import io.github.stan2032.gravitygambit.ui.GravityGambitTheme
import io.github.stan2032.gravitygambit.ui.screens.ShopScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The shop must degrade gracefully: with billing unavailable (no Play
 * services, products not yet configured in Console) and ads disabled, it
 * shows only what a player can earn — no dead buttons, no empty sections,
 * no crash. That posture is what makes the app safe to ship before the
 * Console products exist (docs/MONETIZATION.md).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ShopScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun showShop(profile: Profile) {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = ProfileRepository(context)
        val billing = BillingManager(context, repo)   // never connected: no products
        compose.setContent {
            GravityGambitTheme {
                ShopScreen(
                    profile = profile,
                    repo = repo,
                    billing = billing,
                    ads = NoOpRewardedAds,
                    onNavigate = {},
                )
            }
        }
    }

    @Test
    fun rendersEarnableContentWithoutBilling() {
        showShop(Profile(stardust = 500))
        compose.onNodeWithText("Outfitter").assertExists()
        compose.onNodeWithText("Projectiles").assertExists()
        compose.onNodeWithText("Projectile trails").assertExists()
        compose.onNodeWithText("✦ 500").assertExists()
    }

    @Test
    fun hidesPurchaseSectionsWhenBillingIsUnavailable() {
        showShop(Profile())
        // No products means no "Support the game" section and no Ad Pass offer:
        // sections self-hide rather than showing stubs.
        assertTrue(
            compose.onAllNodesWithText("Support the game").fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            compose.onAllNodesWithText("⭐ Ad Pass").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun hidesAdRewardsWhenNoAdIsLoaded() {
        showShop(Profile())
        // NoOpRewardedAds is never available, so the reward button must not exist.
        assertTrue(
            "ad reward UI must not appear without a loaded ad",
            compose.onAllNodesWithText("Free stardust").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun adPassHoldersSeeTheirActiveState() {
        showShop(Profile(adPassActive = true))
        compose.onNodeWithText("⭐ Ad Pass — active").assertExists()
    }
}
