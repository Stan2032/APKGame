package io.github.stan2032.gravitygambit

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stan2032.gravitygambit.billing.BillingManager
import io.github.stan2032.gravitygambit.core.Profile
import io.github.stan2032.gravitygambit.core.Season
import io.github.stan2032.gravitygambit.data.ProfileRepository
import io.github.stan2032.gravitygambit.ui.GravityGambitTheme
import io.github.stan2032.gravitygambit.ui.screens.SeasonScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The season screen has to show the player's *real* position and must not
 * offer anything it cannot deliver — the same posture as the shop, and for the
 * same reason: this ships before the Console products exist.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class SeasonScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(profile: Profile) {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = ProfileRepository(context)
        val billing = BillingManager(context, repo)   // never connected: no products
        compose.setContent {
            GravityGambitTheme {
                SeasonScreen(profile = profile, repo = repo, billing = billing, onNavigate = {})
            }
        }
    }

    private fun today() = LocalDate.now(ZoneOffset.UTC).toEpochDay()

    @Test
    fun aFreshProfileRendersTierOneWithNothingToCollect() {
        show(Profile().rolledIntoSeason(today()))
        compose.onNodeWithText("Tier 1", substring = true).assertExists()
        compose.onNodeWithText("of ${Season.TIERS}", substring = true).assertExists()
        assertTrue(
            "a fresh profile was offered rewards it has not earned",
            compose.onAllNodesWithText("Collect", substring = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    /** Earned tiers must surface a collect action that says how many. */
    @Test
    fun earnedTiersOfferACollect() {
        val xp = Season.xpForTier(8)
        show(Profile(seasonId = Season.indexFor(today()), seasonXp = xp))
        compose.onNodeWithText("Tier 8", substring = true).assertExists()
        assertTrue(
            "tier 8 with nothing claimed offered no rewards",
            compose.onAllNodesWithText("Collect", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /**
     * Billing is unavailable here (never connected), so the pass offer must be
     * absent rather than a button that cannot do anything.
     */
    @Test
    fun thePassOfferIsHiddenWhenBillingCannotServeIt() {
        show(Profile(seasonId = Season.indexFor(today()), seasonXp = Season.xpForTier(20)))
        assertTrue(
            "an unbuyable pass was still offered for sale",
            compose.onAllNodesWithText("Unlock the pass", substring = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * A save whose season does not match today's must roll over *on display*,
     * not merely on the next write — otherwise a player returning after a
     * boundary opens the screen to last season's tier sitting there.
     */
    @Test
    fun aProfileFromAnotherSeasonRollsOverOnDisplay() {
        val stale = Profile(
            seasonId = Season.indexFor(today()) + 7,   // any season but this one
            seasonXp = Season.xpForTier(40),
            seasonPassOwned = true,
        )
        show(stale)
        compose.onNodeWithText("Tier 1", substring = true).assertExists()
        assertTrue(
            "last season's tier 40 rewards were still on offer",
            compose.onAllNodesWithText("Collect", substring = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }
}
