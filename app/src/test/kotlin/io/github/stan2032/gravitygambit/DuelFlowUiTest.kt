package io.github.stan2032.gravitygambit

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The duel hand-off overlay is where the game's headline feature actually
 * happens: a shot ends, and the player is handed either the phone (hot-seat)
 * or a challenge code to send. It was the last piece of UI with no coverage,
 * and a crash here would break PVP entirely while every other test stayed
 * green. Drives a real shot to completion through the composed screen.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class DuelFlowUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun startDuel(hotseat: Boolean) {
        compose.mainClock.autoAdvance = false
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.setContent {
            GravityGambitTheme {
                GameScreen(
                    launch = GameLaunch(
                        mode = GameMode.DUEL,
                        seed = Seeds.duelRandom(31337L),
                        difficulty = Seeds.DUEL_DIFFICULTY,
                        hotseat = hotseat,
                    ),
                    profile = Profile(),
                    repo = ProfileRepository(context),
                    ads = NoOpRewardedAds,
                    sound = SoundManager(context),
                    onNavigate = {},
                )
            }
        }
    }

    private fun awaitText(text: String, timeoutMs: Long = 30_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            compose.mainClock.advanceTimeBy(100)
            if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(10)
        }
        throw AssertionError("\"$text\" never appeared within ${timeoutMs}ms")
    }

    private fun click(text: String) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes()
        compose.onAllNodesWithText(text)[0].performClick()
    }

    @Test
    fun codeDuelOffersAShareableCodeAfterTheShot() {
        startDuel(hotseat = false)
        awaitText("FIRE")
        click("FIRE")
        // Shot flies, resolves, and the turn passes to the absent opponent.
        awaitText("Shot locked in 🔒")
        awaitText("Share")
        awaitText("Copy")
    }

    @Test
    fun hotSeatAsksForThePhoneInsteadOfACode() {
        startDuel(hotseat = true)
        awaitText("FIRE")
        click("FIRE")
        // Same moment, different affordance: pass the device, never a code.
        awaitText("Ready — my turn")
        assert(
            compose.onAllNodesWithText("Share").fetchSemanticsNodes().isEmpty()
        ) { "hot-seat play must never offer to share a code" }
    }

    @Test
    fun hotSeatHandsBackToAimingForTheSecondPlayer() {
        startDuel(hotseat = true)
        awaitText("FIRE")
        click("FIRE")
        awaitText("Ready — my turn")
        click("Ready — my turn")
        // Player two now has the controls.
        awaitText("FIRE")
    }
}
