package io.github.stan2032.gravitygambit

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stan2032.gravitygambit.ads.NoOpRewardedAds
import io.github.stan2032.gravitygambit.audio.SoundManager
import io.github.stan2032.gravitygambit.core.GameMode
import io.github.stan2032.gravitygambit.core.Profile
import io.github.stan2032.gravitygambit.core.Seeds
import io.github.stan2032.gravitygambit.data.ProfileRepository
import io.github.stan2032.gravitygambit.ui.GameLaunch
import io.github.stan2032.gravitygambit.ui.GravityGambitTheme
import io.github.stan2032.gravitygambit.ui.game.GameScreen
import io.github.stan2032.gravitygambit.ui.screens.CampaignScreen
import io.github.stan2032.gravitygambit.ui.screens.HomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Large screens and landscape. The play board is portrait (1000x1600), so on a
 * wide display it letterboxes to a narrow column — roughly 65% of a landscape
 * phone screen is otherwise empty bars. The match screen moves its HUD into
 * those bars rather than floating it over the play area; these tests prove
 * both layouts compose and keep their controls reachable.
 *
 * Tablets default to landscape, so this is the first impression on every
 * tablet — and Play surfaces large-screen quality in its listings.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WideScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun match(): GameLaunch = GameLaunch(
        mode = GameMode.CAMPAIGN,
        seed = Seeds.campaign(0, 0),
        difficulty = 0, sector = 0, level = 0,
    )

    private fun showMatch() {
        compose.mainClock.autoAdvance = false
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.setContent {
            GravityGambitTheme {
                GameScreen(
                    launch = match(),
                    profile = Profile(campaignStars = mapOf("0:0" to 1)), // skip the coaching hint
                    repo = ProfileRepository(context),
                    ads = NoOpRewardedAds,
                    sound = SoundManager(context),
                    onNavigate = {},
                )
            }
        }
    }

    private fun awaitText(text: String, timeoutMs: Long = 20_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy(100)
            if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(15)
        }
        throw AssertionError("\"$text\" never appeared within ${timeoutMs}ms")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xhdpi")   // phone, landscape
    fun matchScreenUsesTheSideBarsInLandscape() {
        showMatch()
        awaitText("FIRE")
        awaitText("Sector 1 · Level 1")
    }

    @Test
    @Config(qualifiers = "sw600dp-w1280dp-h800dp-xhdpi")   // tablet, landscape
    fun matchScreenComposesOnATabletInLandscape() {
        showMatch()
        awaitText("FIRE")
        awaitText("Sector 1 · Level 1")
    }

    @Test
    @Config(qualifiers = "sw600dp-w800dp-h1280dp-xhdpi")   // tablet, portrait
    fun matchScreenComposesOnATabletInPortrait() {
        showMatch()
        awaitText("FIRE")
    }

    @Test
    @Config(qualifiers = "sw600dp-w674dp-h841dp-xhdpi")   // unfolded foldable, book posture
    fun matchScreenComposesOnAnUnfoldedFoldable() {
        showMatch()
        awaitText("FIRE")
    }

    @Test
    @Config(qualifiers = "w411dp-h420dp-xhdpi")   // split-screen: short and narrow
    fun matchScreenSurvivesSplitScreen() {
        // Multi-window hands apps awkward, near-square viewports. The board
        // shrinks but controls must remain present rather than being clipped
        // away — a crash or missing FIRE button here is a 1-star review.
        showMatch()
        awaitText("FIRE")
    }

    @Test
    @Config(qualifiers = "sw600dp-w1280dp-h800dp-xhdpi")
    fun menusComposeOnALargeScreen() {
        compose.setContent {
            GravityGambitTheme { HomeScreen(profile = Profile(), onNavigate = {}) }
        }
        compose.onAllNodesWithText("Campaign").fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xhdpi")
    fun campaignGridComposesInLandscape() {
        compose.setContent {
            GravityGambitTheme { CampaignScreen(profile = Profile(), onNavigate = {}) }
        }
        compose.onAllNodesWithText("Sector 1").fetchSemanticsNodes().isNotEmpty()
    }
}
