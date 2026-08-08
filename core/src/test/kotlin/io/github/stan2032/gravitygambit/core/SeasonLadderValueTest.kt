package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A season pass bought twice must be worth buying twice.
 *
 * `premiumReward` is a pure function of tier, so every season pays the same
 * eight unlocks. The pass is a **per-season consumable** — `rolledIntoSeason`
 * clears `seasonPassOwned` — so a returning player buys it again and reaches
 * those tiers already owning everything in them. Grants de-duplicate
 * (`.distinct()` on trails and projectiles, `maxOf` on preview level), so
 * before 0.19.17 those eight tiers paid **nothing**, and the ladder went on
 * displaying them as rewards right up to the moment they were claimed.
 *
 * `Season.effectiveReward` pays a redundant unlock out at the item's own shop
 * price instead. These tests assert the *property* — no premium tier may ever
 * pay nothing, for any owner — rather than today's eight tier numbers, so
 * reshuffling the ladder cannot quietly reintroduce the hole.
 */
class SeasonLadderValueTest {

    /** Someone who has played a season: owns every unlock the premium lane grants. */
    private fun veteran(): Profile = Profile(
        ownedTrails = Economy.TRAILS.map { it.id },
        unlockedProjectiles = Economy.PROJECTILE_UNLOCKS.map { it.typeId },
        previewLevel = Economy.MAX_PREVIEW_LEVEL,
    )

    @Test
    fun noPremiumTierEverPaysNothing() {
        val dead = ArrayList<String>()
        for (t in 1..Season.TIERS) {
            val advertised = Season.reward(t, premium = true) ?: continue
            val actual = Season.effectiveReward(t, premium = true, owned = veteran().seasonOwnership())
            assertNotNull("tier $t advertises a reward but effectiveReward returned null", actual)
            val worth = when (actual!!.kind) {
                Season.RewardKind.STARDUST -> actual.amount
                // A non-stardust reward this owner already holds is the bug.
                Season.RewardKind.TRAIL ->
                    if (actual.trailId in veteran().ownedTrails) 0 else 1
                Season.RewardKind.PROJECTILE ->
                    if (actual.amount in veteran().unlockedProjectiles) 0 else 1
                Season.RewardKind.PREVIEW_TIER ->
                    if (veteran().previewLevel >= actual.amount) 0 else 1
            }
            if (worth <= 0) dead.add("tier $t (${advertised.kind})")
        }
        assertTrue(
            "These premium tiers pay a player who already owns their unlock NOTHING: $dead. " +
                "The season pass is a per-season consumable, so this is what a returning " +
                "buyer receives for the same price. Every premium tier must pay something " +
                "for every owner — see Season.effectiveReward.",
            dead.isEmpty(),
        )
    }

    /**
     * The substitution must be a real price, not a token. Paying 1 stardust for
     * a 900-stardust trail would satisfy the test above while still gutting the
     * product, so each redundant tier is checked against the item's own cost.
     */
    @Test
    fun aRedundantUnlockPaysItsOwnShopPrice() {
        for (t in 1..Season.TIERS) {
            val advertised = Season.reward(t, premium = true) ?: continue
            val expected = when (advertised.kind) {
                Season.RewardKind.STARDUST -> continue
                Season.RewardKind.TRAIL ->
                    Economy.TRAILS.firstOrNull { it.id == advertised.trailId }?.cost
                Season.RewardKind.PROJECTILE ->
                    Economy.PROJECTILE_UNLOCKS.firstOrNull { it.typeId == advertised.amount }?.cost
                Season.RewardKind.PREVIEW_TIER ->
                    Economy.PREVIEW_UPGRADE_COSTS.getOrNull(advertised.amount - 1)
            }
            assertNotNull(
                "Tier $t grants a ${advertised.kind} that Economy cannot price, so " +
                    "effectiveReward falls back to granting the duplicate and the tier pays " +
                    "nothing to a returning buyer. Give it a price, or stop granting it.",
                expected,
            )
            val actual = Season.effectiveReward(t, premium = true, owned = veteran().seasonOwnership())!!
            assertEquals("tier $t substitution", Season.RewardKind.STARDUST, actual.kind)
            assertEquals("tier $t pays the wrong amount", expected!!.toLong(), actual.amount.toLong())
        }
    }

    /** A first-season player must still get the actual goods, not the cash. */
    @Test
    fun aNewPlayerStillReceivesTheUnlocksThemselves() {
        val fresh = Profile()
        var unlocks = 0
        for (t in 1..Season.TIERS) {
            val base = Season.reward(t, premium = true) ?: continue
            val actual = Season.effectiveReward(t, premium = true, owned = fresh.seasonOwnership())!!
            assertEquals("tier $t changed for a fresh profile", base.kind, actual.kind)
            if (base.kind != Season.RewardKind.STARDUST) unlocks++
        }
        assertTrue(
            "The premium lane grants no unlocks at all to a fresh player, so this test is " +
                "passing vacuously and the substitution logic is untested.",
            unlocks >= 4,
        )
    }

    /**
     * The gap this closes, stated in numbers so a change to the ladder's
     * generosity is visible rather than silent.
     *
     * The valuation counts what is **delivered**, not what is advertised: an
     * unlock the owner already holds is worth zero, because that is exactly
     * what it is worth to them. The first draft priced every trail at its
     * catalogue cost regardless of ownership, which made both seasons total
     * 10988 *even with the bug present* — a test that would have passed on the
     * defect it was written to catch. Found by checking it rejects.
     */
    @Test
    fun theSecondSeasonIsWorthWhatTheFirstIs() {
        fun laneValue(owner: Profile): Int = (1..Season.TIERS).sumOf { t ->
            val r = Season.effectiveReward(t, premium = true, owned = owner.seasonOwnership()) ?: return@sumOf 0
            when (r.kind) {
                Season.RewardKind.STARDUST -> r.amount
                Season.RewardKind.TRAIL ->
                    if (r.trailId in owner.ownedTrails) 0
                    else Economy.TRAILS.first { it.id == r.trailId }.cost
                Season.RewardKind.PROJECTILE ->
                    if (r.amount in owner.unlockedProjectiles) 0
                    else Economy.PROJECTILE_UNLOCKS.first { it.typeId == r.amount }.cost
                Season.RewardKind.PREVIEW_TIER ->
                    if (owner.previewLevel >= r.amount) 0
                    else Economy.PREVIEW_UPGRADE_COSTS[r.amount - 1]
            }
        }
        val first = laneValue(Profile())
        val second = laneValue(veteran())
        println("premium lane worth: season 1 = $first, season 2+ = $second stardust-equivalent")
        assertEquals(
            "A returning buyer must get the same value for the same money. Before D-58 the " +
                "second season was worth 6588 against 10988 — 60% — because eight tiers " +
                "granted duplicates that de-duplicated to nothing.",
            first.toLong(), second.toLong(),
        )
    }
}
