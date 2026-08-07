package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which activities actually pay, and how a duel can end.
 *
 * Both are things the UI *tells* players, and at 0.16.5 both were told
 * wrongly: the season screen said "campaign, the daily, or a duel —
 * everything counts" while hot-seat pays zero and Expedition went unmentioned,
 * and the duel screen said "first station down loses" without saying a duel
 * can also simply run out of turns.
 *
 * These are stated here rather than in a screen test because they are facts
 * about the rules, and a screen should be checked against them rather than
 * the other way round (D-37).
 */
class RewardReachTest {

    @Test
    fun everyModeThatPaysSeasonXpIsOneTheScreenCanName() {
        // Campaign, daily, expedition: all pay. Hot-seat duel does not.
        assertTrue(Season.campaignXp(difficulty = 3, stars = 2, firstClear = true) > 0)
        assertTrue(Season.dailyXp(score = 400, streak = 1) > 0)
        assertTrue(Season.expeditionXp(depth = 4, wrecks = 0) > 0)
        assertTrue("a code duel must pay win or lose", Season.duelXp(win = true, codeDuel = true) > 0)
        assertTrue(Season.duelXp(win = false, codeDuel = true) > 0)

        assertEquals(
            "hot-seat must pay no season XP — one player holding both seats is not a match, " +
                "and any screen saying 'everything counts' is lying to a hot-seat player",
            0, Season.duelXp(win = true, codeDuel = false),
        )
        assertEquals(0, Season.duelXp(win = false, codeDuel = false))
        assertEquals("hot-seat must pay no stardust either", 0, Economy.duelReward(win = true, codeDuel = false))
    }

    @Test
    fun theSecondLaneReallyDoesReachEveryTier() {
        // The season screen promises "the second reward at every tier". It is
        // true — premiumReward has an else branch — and it is checked because
        // a claim verified and a claim assumed are different states.
        for (tier in 1..Season.TIERS) {
            assertTrue(
                "premium lane has a hole at tier $tier, so the screen's promise is false",
                Season.reward(tier, premium = true) != null,
            )
        }
        // The free lane deliberately does have holes — a track with something
        // in every slot has no shape — so the copy must never promise it.
        val freeHoles = (1..Season.TIERS).count { Season.reward(it, premium = false) == null }
        assertTrue("the free lane should be sparse by design, got $freeHoles holes", freeHoles > 0)
    }

    @Test
    fun aDuelCanEndWithNoWinner() {
        // "First station down loses" is the win condition, not the whole
        // ending: a duel is also over at DUEL_MAX_TURNS, and duelWinner() is
        // null there. Two players who keep missing will meet this, and they
        // are exactly the players in their first duel.
        val spec = SystemGenerator.generate(Seeds.duelRandom(4L), Seeds.DUEL_DIFFICULTY, GameMode.DUEL)
        val engine = MatchEngine(spec, GameMode.DUEL)
        assertNull("a duel in progress has no winner yet", engine.duelWinner())
        assertTrue(
            "the turn cap must be reachable, or the draw is unreachable and the copy need not mention it",
            Tuning.DUEL_MAX_TURNS in 1..200,
        )
        assertEquals(
            "both stations start whole, so a capped duel really can end with both alive",
            Tuning.STATION_HP, engine.hp[0],
        )
        assertEquals(Tuning.STATION_HP, engine.hp[1])
    }
}
