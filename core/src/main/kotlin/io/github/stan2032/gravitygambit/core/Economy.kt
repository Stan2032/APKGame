package io.github.stan2032.gravitygambit.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Player profile — the entire save state, serialized to JSON in DataStore.
 *
 * Everything is earnable in-game; purchases accelerate and add breadth. **None
 * of it can decide a duel**: duels issue every shell and the top preview to
 * both players ([Economy.duelLoadout]), because a share code can verify the
 * physics but never the shopping (D-29). That is what keeps PVP fair, and it
 * is what lets the season track sell hard without being pay-to-win.
 */
@Serializable
data class Profile(
    val stardust: Int = 0,
    val campaignStars: Map<String, Int> = emptyMap(),   // "sector:level" -> best stars (1..3)
    val unlockedProjectiles: List<Int> = listOf(0),
    val ownedTrails: List<String> = listOf("classic"),
    val selectedTrail: String = "classic",
    val previewLevel: Int = 0,                          // aim-preview upgrade tier: 0,1,2
    val dailyBest: Map<String, Int> = emptyMap(),       // epochDay -> best score
    val dailyStreak: Int = 0,
    val lastDailyDay: Long = 0,
    val duelWins: Int = 0,
    val duelLosses: Int = 0,
    // ---- Expedition (Expedition.kt) ----
    /**
     * Best run so far. Depth is the headline because it is the thing a player
     * says out loud ("I got to seventeen"); score breaks ties between runs
     * that reached the same depth by different routes.
     *
     * No in-progress run is stored, deliberately and for the same reason as
     * D-9: a run is a sitting. Persisting one would mean persisting the
     * generated leg, the fuel, the mutator and the match inside it, and would
     * invite quitting out of a bad system to reroll it.
     */
    val expeditionBestDepth: Int = 0,
    val expeditionBestScore: Int = 0,
    val expeditionRuns: Int = 0,
    /**
     * Salvage, and the permanent fittings it has bought (`Outfitting`).
     *
     * A currency of its own rather than stardust, because stardust is buyable
     * with real money and Expedition depth is a personal best — a score money
     * can top up is a weak score. Earned only by running, spent only on
     * running.
     */
    val expeditionSalvage: Int = 0,
    val expeditionOutfits: Map<String, Int> = emptyMap(),
    val hapticsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    /**
     * The generated ambience pad, separate from [soundEnabled] on purpose:
     * wanting the hit to land audibly and wanting a drone under it are
     * different wants, and collapsing them into one switch forces players who
     * only object to the second into silence.
     */
    val musicEnabled: Boolean = true,
    /**
     * **Blind practice**: fly with no aim trace at all, by choice, everywhere.
     *
     * This is D-23's idea finally shipping in the one form that survives the
     * objection. *Blind* was cut from the scored Gambits because nothing in a
     * `TurnInput` records whether the sender looked at their preview, so a
     * peer verifying a share code would have to take the claim on trust — a
     * hole in a game whose premise is that nothing is (D-2, D-4).
     *
     * The objection is about **verification by a stranger**, and it simply
     * does not apply here: this pays nothing. No bonus, no multiplier, no
     * star, nothing recorded and nothing transmitted. It is self-imposed
     * difficulty for its own sake, which is exactly what D-23 said the idea
     * was good for. Because it grants nothing, there is nothing to cheat at,
     * and it stays honest in duels too — turning it on can only cost you.
     */
    val blindPractice: Boolean = false,
    val lastDuelCode: String = "",
    /** Ad Pass subscription state (reconciled against Play Billing on connect). */
    val adPassActive: Boolean = false,
    val adWatchesToday: Int = 0,
    val lastAdWatchDay: Long = 0,
    val lastPassBonusDay: Long = 0,
    // ---- Season track (Season.kt) ----
    /** Which season the stored progress belongs to; a new one resets xp/claims. */
    val seasonId: Int = 0,
    val seasonXp: Int = 0,
    /** Tiers already collected, as "free" and "premium" lane sets. */
    val seasonClaimedFree: List<Int> = emptyList(),
    val seasonClaimedPremium: List<Int> = emptyList(),
    /** Whether the paid lane is owned **for [seasonId]** — passes do not carry over. */
    val seasonPassOwned: Boolean = false,
) {
    val totalStars: Int get() = campaignStars.values.sum()

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromJson(s: String): Profile = try {
            json.decodeFromString(serializer(), s)
        } catch (_: Exception) {
            Profile()
        }
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    fun starsFor(sector: Int, level: Int): Int = campaignStars["$sector:$level"] ?: 0

    val seasonTier: Int get() = Season.tierFor(seasonXp)

    /**
     * The profile as it should look for [epochDay]'s season: a rollover wipes
     * xp, claims and pass ownership. Applied on read as well as on write, so a
     * player who was away for a month never sees last season's tier.
     */
    fun rolledIntoSeason(epochDay: Long): Profile {
        val id = Season.indexFor(epochDay)
        if (id == seasonId) return this
        return copy(
            seasonId = id,
            seasonXp = 0,
            seasonClaimedFree = emptyList(),
            seasonClaimedPremium = emptyList(),
            seasonPassOwned = false,
        )
    }
}

object Economy {

    class TrailDef(val id: String, val name: String, val cost: Int, val colors: List<Long>)

    val TRAILS = listOf(
        TrailDef("classic", "Classic", 0, listOf(0xFFB0BEC5, 0xFF78909C)),
        TrailDef("aurora", "Aurora", 300, listOf(0xFF69F0AE, 0xFF00E5FF)),
        TrailDef("ember", "Ember", 300, listOf(0xFFFFAB40, 0xFFFF5252)),
        TrailDef("quantum", "Quantum", 600, listOf(0xFFE040FB, 0xFF40C4FF)),
        TrailDef("prism", "Prism", 900, listOf(0xFFFF5252, 0xFFFFD740, 0xFF69F0AE, 0xFF40C4FF, 0xFFE040FB)),
    )

    fun trail(id: String): TrailDef = TRAILS.firstOrNull { it.id == id } ?: TRAILS[0]

    class ProjectileUnlock(val typeId: Int, val starsRequired: Int, val cost: Int)

    val PROJECTILE_UNLOCKS = listOf(
        ProjectileUnlock(ProjectileType.HEAVY.id, starsRequired = 12, cost = 250),
        ProjectileUnlock(ProjectileType.SPLITTER.id, starsRequired = 30, cost = 600),
        ProjectileUnlock(ProjectileType.RETRO.id, starsRequired = 50, cost = 900),
    )

    /** Cost to move preview tier 0->1 and 1->2. */
    val PREVIEW_UPGRADE_COSTS = listOf(250, 600)

    /**
     * A preview tier meaning **no aim trace at all**, as distinct from tier 0
     * which is the base 90-tick trace every player starts with.
     *
     * This did not exist until 0.19.0 and its absence was a live bug: the
     * Expedition *Dark run* told the player "No aim trace on this system" and
     * then handed them tier 0 — a full base preview. The mutator was removing
     * a *purchase*, not the trace, while the copy promised the trace. Blind
     * practice needs the same thing, so both now go through this.
     */
    const val PREVIEW_NONE = -1

    fun previewTicks(level: Int): Int = when {
        // Explicit, not an `else` fall-through: a negative tier landing in the
        // top branch is exactly how "blind" would silently become "best
        // possible preview", which is the inverse of the intended rule.
        level <= PREVIEW_NONE -> 0
        level == 0 -> 90
        level == 1 -> 180
        else -> 270
    }

    /**
     * Top preview tier — what a duel issues to *both* players.
     *
     * The preview upgrade used to apply in duels, so a player who had bought
     * it saw three times further ahead than one who had not. Nothing about a
     * share code can detect that, and there is no matchmaking to absorb it:
     * you simply sent your friend a match you were better equipped for. See
     * [duelLoadout].
     */
    const val MAX_PREVIEW_LEVEL = 2

    /**
     * **In a duel, both players are issued everything.**
     *
     * Duels are the one mode where two people's purchases meet, and this game
     * cannot referee that: a duel travels as (seed, turns, hash) and the
     * receiving device re-simulates it, so it can verify the *physics* but not
     * the *shopping*. A claimed loadout would have to be taken on trust —
     * exactly the hole that got the Blind gambit cut (D-23), except this one
     * decides who wins. And with no ladder, a paid edge is not smoothed out;
     * it just breaks the match your friend sent you.
     *
     * So duels normalize: the full rack of shells and the top preview, for
     * everyone, unconditionally. Progression is real and long, but it lives in
     * Campaign, Daily and Expedition — the modes where beating your own past
     * is the point. This is what lets the game sell progression hard without
     * any of it being pay-to-win (D-29).
     */
    fun duelLoadout(): List<Int> = ProjectileType.entries.map { it.id }

    fun campaignReward(difficulty: Int, stars: Int, firstClear: Boolean): Int =
        if (firstClear) 25 + 10 * difficulty + 10 * stars else 5 + 2 * stars

    /**
     * Daily payout, multiplied by the streak the player is defending.
     *
     * The streak used to be a number on the home screen and nothing else —
     * DESIGN.md claimed "daily streak → cosmetic economy" and no code read it.
     * It now pays, which is the only thing that makes a streak worth keeping.
     *
     * Multipliers are in tenths so the arithmetic stays integer-exact, and the
     * top band is reached at two weeks and then stops: a streak should reward
     * coming back, not compound forever into the only sane way to earn.
     */
    fun dailyReward(score: Int, streak: Int = 0): Int =
        minOf(60, score / 10) * streakMultiplierTenths(streak) / 10

    fun streakMultiplierTenths(streak: Int): Int = when {
        streak >= 14 -> 16
        streak >= 7 -> 14
        streak >= 3 -> 12
        else -> 10
    }

    /**
     * Daily streak after completing the daily for [playedDay].
     *
     * A rule, so it lives here rather than in the app's repository (D-7) and
     * is testable without DataStore. Note [playedDay] is the day the *match
     * belongs to* (captured when it was launched), not the wall clock at the
     * moment it ended — a run started at 23:58 UTC and finished after midnight
     * still counts for the day it was played, and cannot double-count.
     */
    fun dailyStreakAfter(previousDay: Long, previousStreak: Int, playedDay: Long): Int = when {
        previousDay == playedDay -> previousStreak          // replay of today: unchanged
        previousDay == playedDay - 1 -> previousStreak + 1  // consecutive day
        else -> 1                                           // gap (or first ever): restart
    }

    /**
     * Duel payout. Hot-seat pays nothing at all: one person controls both
     * seats, so any payout there is a self-farm, not a match. That rule used
     * to live in the app's repository while this function still returned 40
     * for it — a number nothing could ever read. It belongs here (D-7), and
     * it now says what actually happens.
     *
     * A code duel is the biggest *repeatable* payout in the game, which is
     * deliberate: it needs a second real person and it carries an invite.
     * Campaign first-clears pay more per match at high difficulty, but each
     * one exists exactly once.
     */
    fun duelReward(win: Boolean, codeDuel: Boolean): Int = when {
        !codeDuel -> 0
        win -> 60
        else -> 10
    }

    /** Sector s unlocks when the previous sector has at least this many stars (of 36). */
    const val SECTOR_UNLOCK_STARS = 20
}

/**
 * Rewarded-ads economy. Policy: ads are ONLY ever opt-in rewards — never
 * interstitials, never forced after matches (see docs/ADS.md). These constants
 * apply whether rewards come from actually watching (RewardedAdsProvider) or
 * automatically via the Ad Pass.
 */
object AdRewards {
    /** Stardust per opt-in rewarded view (shop "free stardust" button). */
    const val WATCH_STARDUST = 40

    /**
     * Total rewarded views a player can be paid for in one day across **every**
     * surface, resetting at UTC midnight. This is the single number that
     * governs ad revenue, and it is not a taste decision — [AdEconomics]
     * derives it from the Ad Pass price, and `AdPassInvariantTest` recomputes
     * it. Raising it breaks the owner's standing directive that the pass must
     * out-earn the ads it replaces (D-13/D-53), and the test says so by name.
     */
    const val DAILY_REWARDED_VIEWS = 6

    /**
     * Sub-cap on the shop's "free stardust" button alone. It exists so one
     * surface cannot eat the whole day's budget and leave the post-match
     * doubler dead: the remainder ([DAILY_REWARDED_VIEWS] − this) is what a
     * player who maxed the shop still has left to double a match with.
     * Revenue is governed by the budget above, never by this.
     */
    const val DAILY_WATCH_CAP = 4

    /** Ad Pass perk: match rewards multiplier (applied to campaign/daily/duel payouts). */
    const val PASS_REWARD_MULTIPLIER = 2

    /** Ad Pass perk: automatic daily stardust drop (no watching required). */
    const val PASS_DAILY_BONUS = 50
}

/**
 * What a rewarded view is actually worth, and what the pass actually costs.
 *
 * These are assumptions about the outside world rather than game rules, and
 * they live in code rather than in prose because
 * [AdRewards.DAILY_REWARDED_VIEWS] is *computed* from them. D-53 happened
 * because the numbers behind the invariant lived only in a document, where
 * nothing could notice when reality moved away from them. Now the arithmetic
 * runs on every build: change a price, an eCPM, or a cap, and
 * `AdPassInvariantTest` recomputes the whole thing and rejects the pair that
 * no longer holds.
 *
 * Sources are public rewarded-video benchmarks for tier-1 markets and Play's
 * published fee schedule; both are cited in docs/ADS.md. Every figure is
 * deliberately chosen at the end of its range that makes the invariant
 * *hardest* to satisfy, so satisfying it means satisfying it everywhere.
 */
object AdEconomics {
    /** Top of the plausible rewarded eCPM range, USD per 1000 views. */
    const val MAX_ECPM_USD = 25.0

    /** Play's cut on the small-developer tier (first $1M/year). */
    const val PLAY_FEE = 0.15

    /** Suggested `ad_pass_monthly` price, USD. The cheaper monthly-equivalent
     *  of the two pass products, so it is the one the invariant binds on. */
    const val PASS_MONTHLY_USD = 4.99

    /** The longest month, because more days means more ad revenue to beat. */
    const val DAYS_PER_MONTH = 31

    /** Net USD kept from a single rewarded view, at the top of the eCPM range. */
    fun netPerView(): Double = MAX_ECPM_USD / 1000.0 * (1.0 - PLAY_FEE)

    /** Net USD from a player watching [viewsPerDay] every day for a month. */
    fun monthlyAdNet(viewsPerDay: Int): Double = netPerView() * viewsPerDay * DAYS_PER_MONTH

    /** Net USD kept from one month of Ad Pass. */
    fun monthlyPassNet(): Double = PASS_MONTHLY_USD * (1.0 - PLAY_FEE)

    /**
     * The largest daily view budget whose maximum monthly ad revenue the pass
     * still beats. [AdRewards.DAILY_REWARDED_VIEWS] must equal this; the test
     * is what makes "must" mean something.
     */
    fun affordableDailyViews(): Int {
        var n = 0
        while (monthlyAdNet(n + 1) <= monthlyPassNet()) n++
        return n
    }
}

/**
 * Play Billing product IDs. Prices are configured in Play Console (suggested:
 * $0.99 / $2.99 / $2.49 — deliberately cheap; see docs/MONETIZATION.md).
 */
object Iap {
    /**
     * The season pass: consumable, because it is bought **per season** rather
     * than owned forever. Grants the premium lane for the current season only,
     * and pays out every tier already reached the moment it is bought.
     */
    const val SEASON_PASS = "season_pass"           // consumable: premium lane, this season

    const val STARDUST_SMALL = "stardust_small"     // consumable: +600 stardust
    const val STARDUST_LARGE = "stardust_large"     // consumable: +2200 stardust
    const val COSMIC_PACK = "cosmic_pack"           // entitlement: all cosmetic trails

    val CONSUMABLE_GRANTS = mapOf(STARDUST_SMALL to 600, STARDUST_LARGE to 2200)
    val ALL_PRODUCT_IDS = listOf(STARDUST_SMALL, STARDUST_LARGE, COSMIC_PACK, SEASON_PASS)

    /**
     * Ad Pass subscriptions: no ads ever shown to you, all rewarded-ad boosts
     * applied automatically, 2x match rewards + daily stardust drop. Priced so
     * one subscriber is worth more than the same player's ad views would be
     * (see docs/ADS.md for the math). Suggested: $1.99/week, $4.99/month.
     */
    const val AD_PASS_WEEKLY = "ad_pass_weekly"
    const val AD_PASS_MONTHLY = "ad_pass_monthly"
    val SUBSCRIPTION_IDS = listOf(AD_PASS_WEEKLY, AD_PASS_MONTHLY)
}
