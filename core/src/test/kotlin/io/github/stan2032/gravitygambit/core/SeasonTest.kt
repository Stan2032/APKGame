package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The season track is the long-term progression, and the thing it must never
 * become is a way to buy a duel. These tests pin both halves: the curve behaves
 * like a track, and nothing on it can reach PVP.
 */
class SeasonTest {

    @Test
    fun seasonsAreFourWeeksAndDerivedFromTheDate() {
        val d0 = Season.EPOCH_DAY
        assertEquals(0, Season.indexFor(d0))
        assertEquals(0, Season.dayWithin(d0))
        assertEquals(0, Season.indexFor(d0 + 27))
        assertEquals(27, Season.dayWithin(d0 + 27))
        assertEquals(1, Season.indexFor(d0 + 28))
        assertEquals(0, Season.dayWithin(d0 + 28))
        assertEquals(13, Season.indexFor(d0 + 28 * 13 + 5))
        // Before launch is still season 0 rather than a negative one.
        assertEquals(0, Season.indexFor(d0 - 500))
        assertEquals(Season.LENGTH_DAYS.toInt(), Season.daysLeft(d0))
    }

    @Test
    fun theTierCurveRisesAndIsConsistentWithItself() {
        assertEquals(1, Season.tierFor(0))
        var previousStep = 0
        for (tier in 2..Season.TIERS) {
            val step = Season.xpForTier(tier) - Season.xpForTier(tier - 1)
            assertTrue("tier $tier costs nothing", step > 0)
            assertTrue("the curve must not flatten at tier $tier", step >= previousStep)
            previousStep = step
            // The XP that reaches a tier must actually report that tier.
            assertEquals(tier, Season.tierFor(Season.xpForTier(tier)))
            assertEquals(tier - 1, Season.tierFor(Season.xpForTier(tier) - 1))
        }
        assertEquals("the track ends", Season.TIERS, Season.tierFor(Int.MAX_VALUE / 2))
    }

    /**
     * A season should be finishable by someone who plays properly, and not by
     * someone who opens the app twice. Sanity-check the curve against a real
     * play rate rather than trusting the constants.
     */
    @Test
    fun theTrackIsFinishableByPlayingButNotByTouristing() {
        val full = Season.xpForTier(Season.TIERS)

        // A committed player: the daily on a long streak, two campaign clears
        // and a code duel, every day for the season.
        val perDayCommitted =
            Season.dailyXp(score = 700, streak = 14) +
                2 * Season.campaignXp(difficulty = 6, stars = 2, firstClear = true) +
                Season.duelXp(win = true, codeDuel = true) +
                Season.flourishXp(gambitsKept = 1, objectiveMet = false)
        val committedDays = full / perDayCommitted
        assertTrue(
            "a committed player needs $committedDays days of a 28-day season — too easy",
            committedDays >= 12,
        )
        assertTrue(
            "a committed player needs $committedDays days of a 28-day season — unreachable",
            committedDays <= 28,
        )

        // A tourist: the daily, no streak, nothing else.
        val perDayTourist = Season.dailyXp(score = 200, streak = 1)
        assertTrue(
            "the daily alone would finish the track in ${full / perDayTourist} days",
            full / perDayTourist > 28,
        )
    }

    @Test
    fun hotSeatEarnsNoSeasonProgressEither() {
        assertEquals(0, Season.duelXp(win = true, codeDuel = false))
        assertEquals(0, Season.duelXp(win = false, codeDuel = false))
        assertTrue(
            "a code duel must pay whether it is won or lost — the point is that " +
                "you played a person",
            Season.duelXp(win = false, codeDuel = true) > 0,
        )
        assertTrue(
            Season.duelXp(win = true, codeDuel = true) >
                Season.duelXp(win = false, codeDuel = true),
        )
    }

    @Test
    fun thePaidLaneIsAdditiveAndNeverEmpty() {
        for (tier in 1..Season.TIERS) {
            assertNotNull("premium tier $tier pays nothing", Season.reward(tier, premium = true))
        }
        // The free lane is deliberately sparse — a track with something in
        // every slot has no shape — but must still pay at every milestone.
        for (tier in intArrayOf(10, 20, 30, 40, 50)) {
            assertNotNull("free milestone $tier pays nothing", Season.reward(tier, premium = false))
        }
        assertNull(Season.reward(0, premium = false))
        assertNull(Season.reward(Season.TIERS + 1, premium = true))
    }

    @Test
    fun buyingThePassPaysOutEveryTierAlreadyEarned() {
        // Reached tier 20 on the free lane, claimed all of it.
        val claimedFree = (1..20).toSet()
        assertTrue(
            "nothing left on the free lane",
            Season.claimableTiers(20, premium = false, claimed = claimedFree).isEmpty(),
        )
        val owed = Season.claimableTiers(20, premium = true, claimed = emptySet())
        assertEquals(
            "buying at tier 20 must owe every premium tier up to 20",
            (1..20).toList(), owed,
        )
    }

    /**
     * The load-bearing one. Progression is allowed to be long, generous and
     * purchasable — but a duel must not be able to notice any of it.
     */
    @Test
    fun nothingOnTheTrackCanReachADuel() {
        // Every shell, to both players, unconditionally.
        assertEquals(
            ProjectileType.entries.map { it.id }.sorted(),
            Economy.duelLoadout().sorted(),
        )
        // Including the ones the premium lane grants.
        val granted = (1..Season.TIERS)
            .mapNotNull { Season.reward(it, premium = true) }
            .filter { it.kind == Season.RewardKind.PROJECTILE }
            .map { it.amount }
        assertTrue("the premium lane grants no shells at all", granted.isNotEmpty())
        for (id in granted) {
            assertTrue(
                "shell $id is a premium reward but not in the duel loadout",
                Economy.duelLoadout().contains(id),
            )
        }
        // Preview is issued at its ceiling, so a granted tier cannot exceed it.
        val previewGrants = (1..Season.TIERS)
            .mapNotNull { Season.reward(it, premium = true) }
            .filter { it.kind == Season.RewardKind.PREVIEW_TIER }
            .map { it.amount }
        for (level in previewGrants) {
            assertTrue(
                "premium grants preview tier $level, above what duels issue",
                level <= Economy.MAX_PREVIEW_LEVEL,
            )
        }
    }

    @Test
    fun aNewSeasonWipesProgressAndThePass() {
        val d0 = Season.EPOCH_DAY
        val p = Profile(
            seasonId = 0, seasonXp = 9000,
            seasonClaimedFree = listOf(2, 4), seasonClaimedPremium = listOf(1),
            seasonPassOwned = true, stardust = 500,
        )
        assertEquals("same season is untouched", p, p.rolledIntoSeason(d0 + 3))

        val next = p.rolledIntoSeason(d0 + 28)
        assertEquals(1, next.seasonId)
        assertEquals(0, next.seasonXp)
        assertTrue(next.seasonClaimedFree.isEmpty())
        assertTrue(next.seasonClaimedPremium.isEmpty())
        assertFalse("a pass is bought per season, not owned forever", next.seasonPassOwned)
        assertEquals("nothing outside the season is touched", 500, next.stardust)
    }

    @Test
    fun seasonStateSurvivesTheProfileRoundTrip() {
        val p = Profile(
            seasonId = 4, seasonXp = 1234,
            seasonClaimedFree = listOf(2, 6, 10), seasonClaimedPremium = listOf(1, 2),
            seasonPassOwned = true,
        )
        assertEquals(p, Profile.fromJson(p.toJson()))
        // Forward compatibility: a save written before seasons existed loads.
        val old = """{"stardust":42,"previewLevel":1}"""
        val loaded = Profile.fromJson(old)
        assertEquals(42, loaded.stardust)
        assertEquals(0, loaded.seasonId)
        assertEquals(0, loaded.seasonXp)
    }
}
