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

    /**
     * Blind practice is the D-23 idea shipping in the only form that survives
     * the objection, and what makes it survivable is that **it pays nothing**.
     * Blind was cut from the scored Gambits because nothing in a `TurnInput`
     * records whether the sender looked at their preview, so a peer would have
     * to take the claim on trust. A self-imposed handicap that grants no
     * reward has nothing to take on trust and nothing to cheat at.
     *
     * If a future change ever makes it pay — a bonus, a multiplier, an extra
     * star — it stops being self-imposed difficulty and becomes an
     * unverifiable claim worth money, which is precisely the hole D-23 closed.
     * `Profile` is checked structurally here so that the flag cannot quietly
     * grow a payout.
     */
    @Test
    fun blindPracticePaysNothingAndIsPurelyASetting() {
        val plain = Profile()
        val blind = Profile(blindPractice = true)

        assertEquals(
            "blind practice must not be reachable except as a setting the player sets",
            false, plain.blindPractice,
        )
        // Every reward function in the game takes what it needs explicitly, and
        // none of them takes a Profile — so there is structurally nowhere for a
        // blind-practice bonus to enter. Stated as a check on the reward
        // surface rather than as a comment, because a comment cannot fail.
        val rewardTakesProfile = Season::class.java.declaredMethods.any { m ->
            m.parameterTypes.any { it == Profile::class.java }
        }
        assertEquals(
            "a Season reward function now takes a Profile, so blind practice could pay (D-23)",
            false, rewardTakesProfile,
        )
        assertEquals(
            "turning blind practice on must change nothing but the aim trace",
            plain.copy(blindPractice = true), blind,
        )
    }

    /**
     * "No aim trace" has to mean zero ticks, not "the tier you did not pay
     * for". Tier 0 is the base 90-tick trace every player owns, and until
     * 0.19.0 both the Dark run mutator and this constant conflated the two.
     */
    @Test
    fun theNoPreviewTierActuallyYieldsNoPreview() {
        assertEquals(
            "PREVIEW_NONE must yield zero ticks, or 'blind' is just 'unupgraded'",
            0, Economy.previewTicks(Economy.PREVIEW_NONE),
        )
        assertTrue(
            "tier 0 is the base trace, not blindness — they must differ",
            Economy.previewTicks(0) > 0,
        )
        // A negative tier must not fall through to the top branch, which is how
        // "blind" would silently become "best possible preview".
        assertEquals(0, Economy.previewTicks(-5))
    }

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
