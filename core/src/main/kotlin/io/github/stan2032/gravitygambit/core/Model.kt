package io.github.stan2032.gravitygambit.core

import kotlinx.serialization.Serializable

/** Simple immutable 2D vector used at API boundaries (hot loops use raw doubles). */
data class Vec2(val x: Double, val y: Double)

enum class GameMode(val id: Int) {
    CAMPAIGN(0), DAILY(1), DUEL(2);

    companion object {
        fun byId(id: Int): GameMode = entries.firstOrNull { it.id == id } ?: CAMPAIGN
    }
}

enum class BodyKind {
    STAR,
    PLANET,
    /** Negative mass, intangible: pushes shots away, cannot be hit. */
    REPULSOR,
    /** Drifts across the field on a deterministic rail; hitting one pays out. */
    COMET,
    /** Gravity pulses between weak and strong on a fixed period. */
    PULSAR,
}

/**
 * A gravitational body.
 *
 * Motion is a *pure function of the integer tick* ([vx]/[vy] are world units
 * per second, wrapped at the field edges), never mutable state — that is what
 * keeps moving bodies compatible with the determinism contract
 * (docs/DETERMINISM.md). Static bodies simply have zero velocity.
 * PULSAR mass oscillates by tick for the same reason.
 */
@Serializable
data class Body(
    val x: Double,
    val y: Double,
    val radius: Double,
    val mass: Double,
    val kind: BodyKind,
    val palette: Int,
    val vx: Double = 0.0,
    val vy: Double = 0.0,
) {
    val moves: Boolean get() = vx != 0.0 || vy != 0.0

    /** Position at [tick]; wraps through the field margins so comets recur. */
    fun xAt(tick: Int): Double =
        if (vx == 0.0) x else wrap(x + vx * tick * Tuning.DT, Tuning.WORLD_W)

    fun yAt(tick: Int): Double =
        if (vy == 0.0) y else wrap(y + vy * tick * Tuning.DT, Tuning.WORLD_H)

    /** Effective mass at [tick]; only PULSAR varies. */
    fun massAt(tick: Int): Double =
        if (kind != BodyKind.PULSAR) mass
        else mass * (Tuning.PULSAR_MIN + Tuning.PULSAR_SWING *
            (1.0 + StrictMath.cos(2.0 * Math.PI * tick / Tuning.PULSAR_PERIOD)) / 2.0)

    private fun wrap(v: Double, span: Double): Double {
        val m = Tuning.WRAP_MARGIN
        val lo = -m
        val range = span + 2 * m
        var r = (v - lo) % range
        if (r < 0) r += range
        return r + lo
    }
}

/** A player station. HP and ownership rules live in [MatchEngine]. */
@Serializable
data class Station(val x: Double, val y: Double, val owner: Int)

/**
 * A fully generated star system. Everything needed to replay a match is this
 * spec (reproducible from [seed]) plus the list of [TurnInput]s.
 */
@Serializable
data class SystemSpec(
    val seed: Long,
    val difficulty: Int,
    val name: String,
    val bodies: List<Body>,
    val stations: List<Station>,
    /** Fraction of solver sample shots that hit the target; a difficulty fingerprint. */
    val solverHitRate: Double,
    /**
     * Fraction of missing shots a single anchor can rescue. Measures whether
     * the player's field-bending tools matter here without trivializing aim
     * (SystemGenerator.anchorLeverage).
     */
    val anchorLeverage: Double = 0.0,
    /**
     * Per seat: a bitmask (by [Gambit.id]) of the declarations that seat can
     * actually honour, proved by the generator finding at least one winning
     * shot from that station satisfying each. Some layouts simply have no
     * route that passes two worlds, and offering a bet the arena cannot pay
     * would be a lie told by the game itself.
     *
     * Per seat rather than shared, because in a duel the two stations face
     * genuinely different fields. Intersecting them was tried first and left
     * *half of duels with no declarations at all*; each player is now offered
     * the bets their own seat can win, which is both more honest and more
     * available. Fairness is already enforced by the hit-rate ratio check.
     *
     * Conservative by construction: every bit was set by a real simulated hit,
     * so the sweep can under-report but never invent. What is offered exists.
     */
    val gambitMasks: List<Int> = listOf(0, 0),
    /** What this system asks of the player (docs/VISION.md §3.5). */
    val objectiveId: Int = Objective.BREACH.id,
    /**
     * The body this system's objective points at — the world a Capture ring
     * belongs to, or the comet a Courier must strike. -1 when the objective
     * does not use one. Generic rather than per-objective, so the next
     * objective that needs a target does not add a third field.
     */
    val markedBody: Int = -1,
) {
    val objective: Objective get() = Objective.byId(objectiveId)

    /** Declarations this seat is known to be able to honour, in menu order. */
    fun gambitOptionsFor(seat: Int): List<Gambit> {
        val mask = gambitMasks.getOrElse(seat) { 0 }
        return Gambit.declarable.filter { (mask shr it.id) and 1 == 1 }
    }
}

/**
 * One quantized aiming input. Quantization is the determinism keystone:
 * angle in tenths of a degree (0..3599), power in thousandths (0..1000),
 * brakeTick is the exact simulation tick a RETRO brake fired (-1 = none).
 * Two devices given this input MUST produce bit-identical simulations.
 */
@Serializable
data class TurnInput(
    val shooter: Int,
    val angleDeci: Int,
    val power: Int,
    val typeId: Int,
    val brakeTick: Int,
    /**
     * Player-placed masses that bend this shot (docs/VISION.md §3.1).
     * Quantized to integer world coordinates so they stay replay-safe.
     */
    val anchors: List<Anchor> = emptyList(),
    /**
     * Declared constraint for this shot ([Gambit] id). Part of the input, not
     * the outcome: it is chosen before firing and hashed with everything else,
     * so a shared code cannot be re-declared after the fact.
     */
    val gambitId: Int = 0,
)

/**
 * A point mass the player places before firing: the mechanic that turns the
 * player from a solver of the field into its author. [attract] false makes it
 * repel. Coordinates are integers — determinism depends on it.
 */
@Serializable
data class Anchor(val x: Int, val y: Int, val attract: Boolean) {
    val mass: Double get() = if (attract) Tuning.ANCHOR_MASS else -Tuning.ANCHOR_MASS
}

enum class ProjectileType(
    val id: Int,
    val displayName: String,
    val damage: Int,
    val radius: Double,
    /** Fraction of MAX_SPEED reachable at full power (heavier shells launch slower). */
    val maxPowerFrac: Double,
    val splits: Boolean,
    val canBrake: Boolean,
) {
    STANDARD(0, "Bolt", 34, 5.0, 1.00, splits = false, canBrake = false),
    HEAVY(1, "Breaker", 50, 9.0, 0.72, splits = false, canBrake = false),
    SPLITTER(2, "Triad", 14, 4.0, 1.00, splits = true, canBrake = false),
    RETRO(3, "Retro", 30, 5.0, 1.00, splits = false, canBrake = true);

    companion object {
        fun byId(id: Int): ProjectileType = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

/** All physics and scoring constants in one place, tuned as a set. */
object Tuning {
    const val WORLD_W = 1000.0
    const val WORLD_H = 1600.0
    const val DT = 1.0 / 60.0
    const val OUT_MARGIN = 320.0
    const val MAX_TICKS = 1500          // 25s of flight — generous for orbit shots
    /**
     * Solver horizon. Curated levels force curved routes, which take longer
     * than a straight lob — at 600 ticks the solver was scoring genuine
     * solutions as misses and reporting levels unsolvable.
     */
    const val SOLVER_MAX_TICKS = 950
    const val SOFTENING2 = 16.0         // avoids singular accelerations at close range

    const val MIN_SPEED = 140.0
    const val MAX_SPEED = 520.0
    const val LAUNCH_OFFSET = 34.0

    const val STATION_RADIUS = 26.0
    const val STATION_HP = 100
    const val OWN_STATION_GRACE_TICKS = 30

    const val SPLIT_TICK = 72
    const val SPLIT_ANGLE_DEG = 8.0
    const val BRAKE_FACTOR = 0.45

    const val GRAZE_DIST = 15.0
    const val GRAZE_BONUS = 15
    const val LONGSHOT_TICKS = 360
    const val LONGSHOT_BONUS = 10
    const val TRICKSHOT_BONUS = 25

    /** Comets drift; pulsars breathe. Both are pure functions of the tick. */
    const val WRAP_MARGIN = 260.0
    const val PULSAR_PERIOD = 210.0
    const val PULSAR_MIN = 0.35
    const val PULSAR_SWING = 1.3
    const val COMET_HIT_BONUS = 40

    /**
     * Anchors: strong enough to visibly bend a shot within a few hundred
     * units, weak enough that they cannot trivially drag a shot to the
     * target from anywhere. Tuning this is the heart of Stage A balance.
     */
    const val ANCHOR_MASS = 9.0e5
    const val ANCHOR_RADIUS = 9.0
    const val MAX_ANCHORS = 3
    const val ANCHOR_MIN_CLEARANCE = 46.0

    /**
     * Slingshot scoring: a shot that leaves at speed S and reaches peak P has
     * stolen (P - S) of velocity from the field itself. That is the whole
     * fantasy of the game, so it pays — scaled per unit of gain above the
     * threshold, capped so one lucky whip cannot dwarf accuracy.
     */
    const val SLINGSHOT_MIN_GAIN = 60.0
    const val SLINGSHOT_PER_UNIT = 0.10
    const val SLINGSHOT_MAX_BONUS = 45

    /** Speed gained from the field that a three-star shot must demonstrate. */
    const val THREE_STAR_MIN_GAIN = 45.0

    // ---- Capture objective (docs/VISION.md §3.5; see Objective.kt) ----
    /**
     * The ring the shot must hold, as offsets from the target body's surface,
     * and how long it must hold it.
     *
     * 240 ticks (4s) is not a taste: measured across twelve generated levels,
     * the best an *unbraked* shot ever managed was 191 ticks — a single flyby
     * transit, not an orbit. Sitting the bar above that means a flyby can
     * never accidentally satisfy Capture, the same principle that makes
     * `fallsToNaiveShot` reject levels the obvious shot wins. Braked shots on
     * those same levels reached 339-818, so the bar is comfortably clearable
     * by the manoeuvre it is asking for.
     */
    const val CAPTURE_RING_INNER = 40.0
    const val CAPTURE_RING_OUTER = 190.0
    const val CAPTURE_TICKS = 240

    // ---- Gambit thresholds (docs/VISION.md §3.2; see Gambit.kt) ----
    /** "Featherweight": power must be under this, on the 0..1000 scale. */
    const val FEATHERWEIGHT_MAX_POWER = 400

    /**
     * "Two-body": a body counts as having bent the shot if the flight came
     * within this many of its radii of its surface. Scaled by radius rather
     * than absolute, because a bigger body here is a heavier one — its grip
     * genuinely does reach further.
     */
    const val DEFLECT_RADII = 1.8

    /**
     * "Stolen speed": gain that unambiguously came from the field. Measured,
     * not guessed — across 201 winning shots on twelve curated levels the
     * median gain was 101 and the 10th percentile was 59, so the first value
     * tried (70) was satisfied by roughly nine winning shots in ten. A
     * declaration that nearly every hit honours is not a stake. 120 sits near
     * the 80th percentile: reachable on any level, but only by a shot that
     * genuinely leaned on the field.
     */
    const val STOLEN_SPEED_MIN_GAIN = 120.0

    /**
     * "The long way": how far off the launch→target line the flight must
     * stray, and how far from either endpoint samples must be before they
     * count. Every shot begins on that line and a hit ends on it, so without
     * the end caps every connecting shot would read as a crossing.
     */
    const val LONG_WAY_MIN_DETOUR = 150.0
    const val DETOUR_CAP = 130.0
    const val DETOUR_CAP2 = DETOUR_CAP * DETOUR_CAP

    const val CAMPAIGN_SHOTS = 4
    const val DAILY_SHOTS = 5
    const val DAILY_HIT_SCORE = 100
    const val DUEL_MAX_TURNS = 30
}
