package io.github.stan2032.gravitygambit.core

/**
 * A constraint the player declares *before* firing, in exchange for a bigger
 * payout. Succeed and the shot pays a multiple; fail and it is wasted — the
 * hit does not land. That stake is the whole point: it converts mastery into
 * something risked rather than something displayed, which is what the word
 * "gambit" actually means (docs/VISION.md §3.2).
 *
 * **Every gambit here is decided by the simulation.** That is a hard rule, not
 * a preference. A duel code carries only inputs; the receiving device re-runs
 * the physics and derives everything else. So a constraint the physics cannot
 * observe cannot be checked by the person you send it to. VISION.md proposed
 * *Blind* — fire with no aim preview — and it is a good idea for solo practice,
 * but no peer can verify that you did not peek. In a game whose entire premise
 * is that nothing is taken on trust, a scoring rule taken on trust is a hole,
 * so Blind is deliberately absent from this list rather than shipped as a
 * gambit that only works if everyone is honest (docs/MULTIPLAYER.md §Trust).
 *
 * The four below are each measured by [LiveShot] from the trajectory itself.
 */
enum class Gambit(
    val id: Int,
    val displayName: String,
    /** Second person: the player is making a promise, and will be held to it. */
    val promise: String,
    /** Payout multiplier in tenths — integer math keeps scoring bit-exact. */
    val payoutTenths: Int,
) {
    NONE(0, "No call", "Fire without a declaration", 10),
    FEATHERWEIGHT(1, "Featherweight", "Fire under 40% power", 15),
    TWO_BODY(2, "Two-body", "Pass through the pull of two different worlds", 18),
    STOLEN_SPEED(3, "Stolen speed", "End faster than you launched, by a clear margin", 16),
    LONG_WAY(4, "The long way", "Go around: never cross the line to the target", 22),
    ;

    /** True if the flight that actually happened honoured the declaration. */
    fun satisfiedBy(r: ShotResult): Boolean = when (this) {
        NONE -> true
        FEATHERWEIGHT -> r.input.power < Tuning.FEATHERWEIGHT_MAX_POWER
        TWO_BODY -> r.deflectorCount >= 2
        STOLEN_SPEED -> r.slingshotGain >= Tuning.STOLEN_SPEED_MIN_GAIN
        LONG_WAY -> !r.crossedTargetLine && r.maxDetour >= Tuning.LONG_WAY_MIN_DETOUR
    }

    /** Payout applied to a shot's style bonus, in whole points. */
    fun payout(styleBonus: Int): Int = styleBonus * payoutTenths / 10

    companion object {
        fun byId(id: Int): Gambit = entries.firstOrNull { it.id == id } ?: NONE

        /** Declarable options, in the order they are offered. */
        val declarable: List<Gambit> get() = entries.filter { it != NONE }
    }
}
