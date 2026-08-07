package io.github.stan2032.gravitygambit

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stan2032.gravitygambit.core.GameMode
import io.github.stan2032.gravitygambit.core.MatchEngine
import io.github.stan2032.gravitygambit.core.Seeds
import io.github.stan2032.gravitygambit.core.SystemGenerator
import io.github.stan2032.gravitygambit.core.Tuning
import io.github.stan2032.gravitygambit.ui.GravityGambitTheme
import io.github.stan2032.gravitygambit.ui.screens.HelpScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Help is the first place a confused player looks, so a stale Help screen is
 * not a stale doc — it is a broken feature, and the only screen in the app
 * that can be *wrong* rather than merely ugly.
 *
 * The 0.16.1 audit found it describing the 0.2.0 game: no anchors, no
 * declarations, no objectives, no season, no Expedition — and one outright
 * false rule, *"one shot = ★★★"*, which stopped being true once three stars
 * started requiring that the winning shot also did something. A player
 * chasing that line was optimising for the wrong thing, told so by the game.
 *
 * These tests exist so that particular class of lie has to be told twice.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class HelpAccuracyTest {

    @get:Rule
    val compose = createComposeRule()

    private fun help() {
        compose.setContent { GravityGambitTheme { HelpScreen(onNavigate = {}) } }
    }

    /**
     * At least one node mentions [text].
     *
     * Deliberately "at least one" rather than `onNodeWithText`, which demands
     * exactly one and fails when a word appears in both a card's title and its
     * body — as "Duels" does. The claim here is that the screen *says* a
     * thing, not that it says it once.
     */
    private fun assertMentions(text: String) {
        val hits = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes()
        assertTrue("Help never mentions \"$text\"", hits.isNotEmpty())
    }

    @Test
    fun everyMechanicTheGameShipsIsExplainedSomewhere() {
        help()
        // Each of these is a mechanic a player will meet without warning. The
        // list is the point: it grows when the game does, and the test fails
        // if a future mechanic ships with no explanation anywhere in the app.
        for (topic in listOf("Anchors", "Call your shot", "Expedition", "Season", "Duels", "Campaign")) {
            assertMentions(topic)
        }
        // All four objectives, by the names the HUD uses.
        for (objective in listOf("Breach", "Capture", "Courier", "Thread")) {
            assertMentions(objective)
        }
    }

    @Test
    fun theThreeStarRuleOnScreenMatchesTheEngine() {
        // The engine's rule: one shot AND the shot did something. Prove the
        // second half is real before asserting the screen says so — otherwise
        // this test would just be pinning another guess.
        val spec = SystemGenerator.generate(Seeds.campaign(0, 0), 2, GameMode.CAMPAIGN)
        val engine = MatchEngine(spec, GameMode.CAMPAIGN)
        assertTrue(
            "three stars must require more than speed, or the screen's caveat is wrong",
            Tuning.THREE_STAR_MIN_GAIN > 0.0,
        )
        assertTrue("a fresh match has no stars", engine.starsEarned() == 0)

        help()
        // The caveat, not just the headline: a plain one-shot kill is two stars.
        assertMentions("has to have done something")
        assertMentions("no flourish")
    }

    @Test
    fun theNumbersOnScreenComeFromTuning() {
        // Typed numbers drift silently; interpolated ones cannot. If someone
        // retunes the shot cap or the capture hold, the screen follows and
        // this test keeps passing — which is the intended outcome, not a
        // weakness. What it catches is someone replacing them with literals.
        help()
        assertMentions("${Tuning.CAMPAIGN_SHOTS} shots")
        assertMentions("${Tuning.DAILY_SHOTS} shots")
        assertMentions("${Tuning.MAX_ANCHORS} anchors")
        assertMentions("${Tuning.STATION_HP} damage")
        assertMentions("${Tuning.CAPTURE_TICKS / 60} seconds")
    }

    @Test
    fun theDuelFairnessPromiseIsStatedWhereAPlayerWillSeeIt() {
        // D-29 is the reason PVP can be sold as skill-only, and the store
        // listing says so to strangers. A player who has just lost should be
        // able to find it inside the app too.
        help()
        assertMentions("every shell")
        assertMentions("can decide a match")
    }
}
