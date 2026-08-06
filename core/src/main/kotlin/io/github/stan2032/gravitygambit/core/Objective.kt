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
    ;

    /** Capture is impossible without a brake, so it issues one. */
    val forcesRetro: Boolean get() = this == CAPTURE

    companion object {
        fun byId(id: Int): Objective = entries.firstOrNull { it.id == id } ?: BREACH
    }
}
