package io.github.stan2032.gravitygambit.core

/**
 * What an Expedition run brings home, and what it buys.
 *
 * D-34 deliberately shipped Expedition paying **stardust** and nothing else,
 * on the grounds that minting an unspendable token before the thing it buys
 * exists is a number that goes up and means nothing. This is the other half:
 * the things to buy, and therefore the currency that earns them.
 *
 * ### Why salvage is its own currency and not stardust
 *
 * Stardust is buyable with real money (`Iap.STARDUST_SMALL` / `_LARGE`). If
 * run upgrades were priced in stardust, then **money would buy Expedition
 * depth** — and depth is the number this mode asks you to brag about. That
 * does not breach the PVP invariant (D-13/D-29 is about duels, and Expedition
 * is solo), but a personal best that can be topped up at the till is a weak
 * personal best, and the mode's whole pull is "how far did *you* get".
 *
 * Salvage is therefore earned only by running, spent only on running. The
 * loop closes on itself: run → salvage → a run that reaches further. That is
 * the "one more run" shape VISION §3.6 is after, and it keeps the shop
 * economy and the run economy from competing for the same pocket.
 *
 * ### Why every outfit is safe against the termination proof
 *
 * D-34's guarantee is that runs provably end, because travel cost rises
 * without bound while refunds are bounded. Every option here had to be
 * checked against that, and the check is the reason two obvious ideas are
 * absent:
 *
 * - **Cheaper travel** would attack the clock itself. Even a constant
 *   discount leaves termination intact (travel still grows), but it makes the
 *   proof depend on a purchase, which is a bad thing to owe a receipt.
 * - **Junction rerolls** would break the rule that a junction is a pure
 *   function of (seed, depth), which exists so leaving and returning cannot
 *   fish for a better offer.
 *
 * What survives changes the *tank* and the *refunds*, both bounded, so the
 * argument is untouched: fuel is finite and travel is not.
 * [ExpeditionTest.runsAlwaysEndEvenFullyOutfitted] proves it at max tier.
 */
object Outfitting {

    /**
     * Salvage from a finished run.
     *
     * Depth-led, because depth is what the mode is about, with a small share
     * for shooting well so that a tidy shallow run is not worthless. Wrecks
     * pay nothing extra: you are already being paid for the depth a wreck
     * bought you, and paying *for* wrecks would reward the thing the run is
     * trying to make you avoid.
     */
    fun salvageFor(run: Expedition.Run): Int = 2 + run.depth + run.starsEarned / 4

    /**
     * A permanent fitting, bought once per tier and kept forever.
     *
     * Three of them, each answering a different complaint a losing run makes:
     * *I never got going*, *one bad system ended me*, *I could not see it
     * coming*.
     */
    enum class Outfit(
        val id: String,
        val displayName: String,
        val brief: String,
        val tiers: Int,
        /** Salvage for tier 1; each further tier costs this much more again. */
        val baseCost: Int,
    ) {
        /** +fuel at the start. The plainest fix for "I never got going". */
        RESERVE_TANK(
            "reserve_tank", "Reserve tank",
            "Start every run with more fuel", tiers = 3, baseCost = 12,
        ),

        /**
         * A wreck refunds part of what it burned. Softens variance without
         * touching the clock: the travel already spent is *not* refunded, so
         * the leg still costs you ground.
         */
        SALVAGE_RIG(
            "salvage_rig", "Salvage rig",
            "Recover part of the shots a wrecked system cost", tiers = 2, baseCost = 20,
        ),

        /**
         * See the difficulty of the systems one junction further on. Pure
         * information — it changes no number in the economy, which is exactly
         * why it can be the expensive one without unbalancing anything.
         */
        SURVEY_ARRAY(
            "survey_array", "Survey array",
            "Read the next junction before you commit to this one", tiers = 1, baseCost = 34,
        );

        /** Cost of moving from [owned] tiers to [owned] + 1, or null at max. */
        fun costOfNextTier(owned: Int): Int? =
            if (owned >= tiers) null else baseCost * (owned + 1)

        companion object {
            fun byId(id: String): Outfit? = entries.firstOrNull { it.id == id }
        }
    }

    const val FUEL_PER_TANK_TIER = 5

    /** Fraction of a wrecked leg's *shot* cost returned, per rig tier, in tenths. */
    const val RIG_REFUND_TENTHS_PER_TIER = 3

    /** Starting fuel for a player carrying [owned] outfits. */
    fun startFuel(owned: Map<String, Int>): Int =
        Expedition.START_FUEL + FUEL_PER_TANK_TIER * tierOf(owned, Outfit.RESERVE_TANK)

    /**
     * Fuel handed back after wrecking a leg that burned [shots].
     *
     * Deliberately computed from the shots alone. Travel is what makes the
     * run finite (D-34), so travel is never refunded — a rig softens a bad
     * system, it does not buy back the distance.
     */
    fun wreckRefund(owned: Map<String, Int>, shots: Int, mutator: Expedition.Mutator): Int {
        val tier = tierOf(owned, Outfit.SALVAGE_RIG)
        if (tier <= 0) return 0
        val spentOnShots = shots * Expedition.shotCost(mutator)
        return spentOnShots * RIG_REFUND_TENTHS_PER_TIER * tier / 10
    }

    /** Whether the player can read one junction ahead. */
    fun canSurveyAhead(owned: Map<String, Int>): Boolean =
        tierOf(owned, Outfit.SURVEY_ARRAY) > 0

    fun tierOf(owned: Map<String, Int>, outfit: Outfit): Int =
        (owned[outfit.id] ?: 0).coerceIn(0, outfit.tiers)

    /** Total salvage sunk into [owned] — what a reset would refund, if one ever existed. */
    fun investedIn(owned: Map<String, Int>): Int =
        Outfit.entries.sumOf { o ->
            (1..tierOf(owned, o)).sumOf { t -> o.baseCost * t }
        }

    /**
     * Buy the next tier of [outfit], or null if it cannot be afforded or is
     * already maxed. Returns the new (salvage, owned) pair.
     *
     * Pure, so the UI cannot invent a purchase the rules would not allow.
     */
    fun buy(salvage: Int, owned: Map<String, Int>, outfit: Outfit): Pair<Int, Map<String, Int>>? {
        val tier = tierOf(owned, outfit)
        val cost = outfit.costOfNextTier(tier) ?: return null
        if (salvage < cost) return null
        return (salvage - cost) to (owned + (outfit.id to tier + 1))
    }
}
