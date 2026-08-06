package io.github.stan2032.gravitygambit.core

/**
 * What a system asks of you. Until 0.10.0 there was exactly one answer —
 * destroy the core — and every generated layout was a variation on the same
 * sentence (docs/VISION.md §3.5).
 *
 * Like [Gambit], an objective may only be something the simulation can decide
 * for itself, because a duel travels as inputs and is judged by re-running the
 * physics on the other device (docs/MULTIPLAYER.md §Trust).
 */
enum class Objective(val id: Int, val displayName: String, val brief: String) {
    /** Destroy the enemy core. The original, and still the default. */
    BREACH(0, "Breach", "Destroy the enemy core"),

    /**
     * Hold a ring: keep the shot inside an annulus around the marked world for
     * [Tuning.CAPTURE_TICKS] unbroken ticks.
     *
     * This one is only possible because the physics is real, and measuring it
     * taught the design rather than the other way round. A shot arriving from
     * far away is on a hyperbolic trajectory: with a single dominant body and
     * nothing to dissipate energy, it *must* leave again — no amount of aim
     * fixes that. Measured across twelve generated levels, the best dwell any
     * unbraked shot achieved was 191 ticks, and every refined optimum pinned
     * itself against the slowest launch the game permits.
     *
     * Fire a RETRO shell and burn at the right moment and the same levels
     * yield 339–818 ticks. The brake is the orbital-insertion burn, which is
     * exactly how the manoeuvre works in reality. So Capture hands the player a
     * RETRO shell whatever their loadout ([forcesRetro]) — the objective
     * requires the tool, so the objective supplies it — and the skill becomes
     * *when* to burn rather than only where to aim.
     */
    CAPTURE(1, "Capture", "Hold orbit around the marked world"),

    /**
     * Strike the drifting comet rather than a fixed station: the target moves,
     * so the shot has to be led.
     *
     * Cheap to add because comets already exist, but not cheap to assume — of
     * 36 sampled levels only 11 carried a comet, and **two of those comets
     * could not be hit at all**. Which is the whole reason [Objective] is only
     * ever assigned after a proof (D-27).
     */
    COURIER(2, "Courier", "Strike the drifting comet"),

    /**
     * Fly through every gate, in order. The *order* is the puzzle: a path that
     * clips all three in the wrong sequence has not threaded anything.
     *
     * Gates are not scattered and hoped over. The generator finds a real
     * trajectory first and hangs the gates on it, so a solution provably
     * exists before the level is offered — then `proveThread` re-derives one
     * independently, because "we built it from a path" and "a player can find
     * a path" are different claims (D-27).
     */
    THREAD(3, "Thread", "Fly through the gates in order"),
    ;

    /** Capture is impossible without a brake, so it issues one. */
    val forcesRetro: Boolean get() = this == CAPTURE

    /** Does this objective point at a particular body? */
    val needsMarkedBody: Boolean get() = this == CAPTURE || this == COURIER

    companion object {
        fun byId(id: Int): Objective = entries.firstOrNull { it.id == id } ?: BREACH
    }
}
