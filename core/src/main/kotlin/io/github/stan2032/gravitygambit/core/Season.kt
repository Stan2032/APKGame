package io.github.stan2032.gravitygambit.core

/**
 * The long game: a seasonal track that keeps paying out for months.
 *
 * **Why this shape and not Clash Royale's.** The obvious ask is card levels —
 * spend, get stronger, win more. That cannot work here, and not for taste
 * reasons. A duel travels as `(seed, turns, hash)` and the receiving device
 * re-simulates it, so it can verify the *physics* but never the *shopping*: a
 * claimed shell level would have to be believed. That is the hole that got the
 * Blind gambit cut (D-23), except it would decide who wins. And with no
 * matchmaking there is no ladder to absorb a paid edge — it just breaks the
 * match your friend sent you.
 *
 * So the track buys **breadth and time, never power in PVP**. Duels issue
 * everything to both players ([Economy.duelLoadout]); the season is where a
 * long tail of progression and purchase lives, fed by play in every mode
 * (D-29, D-30).
 *
 * Everything here is a pure function of (epochDay, xp) so it works offline,
 * needs no server, and is testable without a device — the same discipline as
 * the daily streak.
 */
object Season {

    /** Four weeks: long enough to be a project, short enough to come back for. */
    const val LENGTH_DAYS = 28L
    const val TIERS = 50

    /**
     * Day 0 of season 0. Anchored to the daily-challenge epoch so both
     * calendars line up and a season never starts mid-week.
     */
    const val EPOCH_DAY = Seeds.LAUNCH_EPOCH_DAY

    /** Which season [epochDay] falls in. Negative days sit in season 0. */
    fun indexFor(epochDay: Long): Int =
        if (epochDay <= EPOCH_DAY) 0 else ((epochDay - EPOCH_DAY) / LENGTH_DAYS).toInt()

    /** Days elapsed within the current season, 0-based. */
    fun dayWithin(epochDay: Long): Int =
        if (epochDay <= EPOCH_DAY) 0 else ((epochDay - EPOCH_DAY) % LENGTH_DAYS).toInt()

    fun daysLeft(epochDay: Long): Int = (LENGTH_DAYS - dayWithin(epochDay)).toInt()

    /**
     * Cumulative XP needed to have *reached* [tier] (1-based).
     *
     * Gently superlinear: early tiers land fast enough to teach that the track
     * is real, later ones ask for commitment without turning into a wall that
     * only a purchase can climb — there is deliberately no "buy tiers" product,
     * because a track you can pay to skip is not progression, it is a receipt.
     *
     * The constants are **calibrated against a measured play rate**, not
     * chosen. The first curve tried (`120t + 6t²`) needed a committed player
     * **69 days to finish a 28-day season** — a track nobody could complete,
     * which is worse than no track. `SeasonTest` models a committed day and a
     * tourist day and fails if the season becomes unfinishable *or*
     * trivial; at these values a committed player lands around day 23, and the
     * daily alone would take eight months.
     */
    fun xpForTier(tier: Int): Int {
        if (tier <= 1) return 0
        val t = (tier - 1).coerceAtMost(TIERS - 1)
        return 40 * t + 2 * t * t
    }

    /** Tier reached with [xp], 1..TIERS. */
    fun tierFor(xp: Int): Int {
        var tier = 1
        while (tier < TIERS && xp >= xpForTier(tier + 1)) tier++
        return tier
    }

    /** 0..1 progress through the current tier — for a bar that means something. */
    fun progressWithinTier(xp: Int): Float {
        val tier = tierFor(xp)
        if (tier >= TIERS) return 1f
        val floor = xpForTier(tier)
        val ceil = xpForTier(tier + 1)
        if (ceil <= floor) return 1f
        return ((xp - floor).toFloat() / (ceil - floor)).coerceIn(0f, 1f)
    }

    // ---- Earning ----

    /**
     * XP for finishing a campaign system. Paid for *what you did*, not merely
     * that you turned up: difficulty and stars both move it, so the track
     * rewards the same thing the stars do rather than pulling against them.
     */
    fun campaignXp(difficulty: Int, stars: Int, firstClear: Boolean): Int =
        (if (firstClear) 30 else 8) + 4 * difficulty + 10 * stars

    /** XP for the daily. Streak feeds it, same as the stardust payout does. */
    fun dailyXp(score: Int, streak: Int): Int =
        (20 + minOf(60, score / 25)) * Economy.streakMultiplierTenths(streak) / 10

    /**
     * XP for a duel. A code duel is worth real XP win *or* lose, because the
     * point is that you played a person; hot-seat pays nothing, for the same
     * reason it pays no stardust — one player holding both seats is not a match.
     */
    fun duelXp(win: Boolean, codeDuel: Boolean): Int = when {
        !codeDuel -> 0
        win -> 55
        else -> 25
    }

    /**
     * XP for a finished Expedition run, paid **once at the end** rather than
     * per leg.
     *
     * Per-leg payment would make the run strictly worse than playing the same
     * systems in the campaign, since a run also risks them: you would be paid
     * the same and could lose the rest. Paying on the run makes depth the
     * unit, which is what the mode is about. A run that ended at leg one
     * still pays something — a mode that can pay nothing is a mode people
     * stop opening.
     */
    fun expeditionXp(depth: Int, wrecks: Int): Int =
        18 + 9 * depth - 3 * wrecks.coerceAtMost(depth)

    /** A little extra for the things the game is actually about. */
    fun flourishXp(gambitsKept: Int, objectiveMet: Boolean): Int =
        12 * gambitsKept + (if (objectiveMet) 15 else 0)

    // ---- Rewards ----

    enum class RewardKind { STARDUST, TRAIL, PREVIEW_TIER, PROJECTILE }

    class Reward(
        val kind: RewardKind,
        /** Stardust amount, or the id of the trail/projectile granted. */
        val amount: Int = 0,
        val trailId: String = "",
    )

    /**
     * What tier [tier] pays. [premium] is the paid lane, which is **additive**:
     * owning the pass never removes anything from the free lane, and every tier
     * already earned pays out retroactively the moment it is bought.
     *
     * Nothing here can be won by a duel opponent's wallet: shells and preview
     * tiers granted by the track apply to Campaign, Daily and Expedition, and
     * duels issue the full rack regardless ([Economy.duelLoadout]).
     */
    fun reward(tier: Int, premium: Boolean): Reward? {
        if (tier < 1 || tier > TIERS) return null
        return if (premium) premiumReward(tier) else freeReward(tier)
    }

    private fun freeReward(tier: Int): Reward? = when {
        tier % 10 == 0 -> Reward(RewardKind.STARDUST, amount = 250)
        tier % 5 == 0 -> Reward(RewardKind.STARDUST, amount = 120)
        tier % 2 == 0 -> Reward(RewardKind.STARDUST, amount = 45)
        else -> null    // a track with something in every slot has no shape
    }

    private fun premiumReward(tier: Int): Reward = when (tier) {
        12 -> Reward(RewardKind.PROJECTILE, amount = ProjectileType.HEAVY.id)
        18 -> Reward(RewardKind.TRAIL, trailId = "aurora")
        25 -> Reward(RewardKind.PROJECTILE, amount = ProjectileType.SPLITTER.id)
        30 -> Reward(RewardKind.PREVIEW_TIER, amount = 1)
        36 -> Reward(RewardKind.TRAIL, trailId = "quantum")
        42 -> Reward(RewardKind.PROJECTILE, amount = ProjectileType.RETRO.id)
        45 -> Reward(RewardKind.PREVIEW_TIER, amount = 2)
        TIERS -> Reward(RewardKind.TRAIL, trailId = "prism")
        else -> Reward(RewardKind.STARDUST, amount = 60 + 4 * tier)
    }

    /** Every tier from 1..[tier] that has an unclaimed reward on this lane. */
    fun claimableTiers(tier: Int, premium: Boolean, claimed: Set<Int>): List<Int> =
        (1..tier.coerceAtMost(TIERS)).filter { it !in claimed && reward(it, premium) != null }
}
