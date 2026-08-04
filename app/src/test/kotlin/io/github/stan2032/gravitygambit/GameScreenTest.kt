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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The match screen is the game — and until now it was the one screen never
 * rendered by any test. It is also the hardest to test: it runs a continuous
 * ambient animation clock, so the composition is never idle and the default
 * auto-advancing test clock would hang. We drive the clock manually instead.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class GameScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun launchMatch(launch: GameLaunch, profile: Profile = Profile()) {
        compose.mainClock.autoAdvance = false   // continuous animation never idles
        val repo = ProfileRepository(ApplicationProvider.getApplicationContext())
        compose.setContent {
            GravityGambitTheme {
                GameScreen(
                    launch = launch,
                    profile = profile,
                    repo = repo,
                    ads = NoOpRewardedAds,
                    sound = SoundManager(ApplicationProvider.getApplicationContext()),
                    onNavigate = {},
                )
            }
        }
    }

    /**
     * System generation runs on a background dispatcher, so the screen starts
     * on its "Charting the system" state. Pump the manual clock (the ambient
     * animation means the composition never goes idle on its own) until the
     * expected text appears, or fail with what is on screen instead.
     */
    private fun awaitText(text: String, substring: Boolean = false, timeoutMs: Long = 20_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy(100)
            val found = compose
                .onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes()
                .isNotEmpty()
            if (found) return
            Thread.sleep(15)
        }
        throw AssertionError("\"$text\" never appeared within ${timeoutMs}ms")
    }

    private fun assertNoText(text: String, substring: Boolean = false) {
        assertTrue(
            "\"$text\" should not be present",
            compose.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun campaignMatchRendersAndOffersToFire() {
        launchMatch(
            GameLaunch(
                mode = GameMode.CAMPAIGN,
                seed = Seeds.campaign(0, 0),
                difficulty = 0, sector = 0, level = 0,
            )
        )
        awaitText("FIRE")
        awaitText("Sector 1 · Level 1")
    }

    @Test
    fun firstEverLevelShowsTheCoachingHint() {
        launchMatch(
            GameLaunch(
                mode = GameMode.CAMPAIGN,
                seed = Seeds.campaign(0, 0),
                difficulty = 0, sector = 0, level = 0,
            ),
            profile = Profile(),  // fresh: no stars
        )
        awaitText("Drag anywhere to aim", substring = true)
    }

    @Test
    fun veteranPlayerIsNotCoached() {
        launchMatch(
            GameLaunch(
                mode = GameMode.CAMPAIGN,
                seed = Seeds.campaign(0, 0),
                difficulty = 0, sector = 0, level = 0,
            ),
            profile = Profile(campaignStars = mapOf("0:0" to 3)),
        )
        awaitText("FIRE")   // screen is fully up...
        assertNoText("Drag anywhere to aim", substring = true)   // ...but uncoached
    }

    @Test
    fun dailyModeRendersItsOwnHud() {
        launchMatch(
            GameLaunch(
                mode = GameMode.DAILY,
                seed = Seeds.daily(Seeds.LAUNCH_EPOCH_DAY),
                difficulty = Seeds.DAILY_DIFFICULTY,
                epochDay = Seeds.LAUNCH_EPOCH_DAY,
            )
        )
        awaitText("Daily #1")
        awaitText("FIRE")
    }

    @Test
    fun corruptChallengeCodeShowsTheRefusalScreen() {
        launchMatch(
            GameLaunch(
                mode = GameMode.DUEL,
                seed = 1L,
                difficulty = Seeds.DUEL_DIFFICULTY,
                importedCode = "GG1.thisIsNotARealCode",
            )
        )
        awaitText("failed verification", substring = true)
    }
}
