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
)

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
)

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
    const val SOLVER_MAX_TICKS = 600
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
     * Slingshot scoring: a shot that leaves at speed S and reaches peak P has
     * stolen (P - S) of velocity from the field itself. That is the whole
     * fantasy of the game, so it pays — 1 point per unit of gain above the
     * threshold, capped so a single lucky whip can't dwarf accuracy.
     */
    const val SLINGSHOT_MIN_GAIN = 60.0
    const val SLINGSHOT_PER_UNIT = 0.10
    const val SLINGSHOT_MAX_BONUS = 45

    const val CAMPAIGN_SHOTS = 4
    const val DAILY_SHOTS = 5
    const val DAILY_HIT_SCORE = 100
    const val DUEL_MAX_TURNS = 30
}
