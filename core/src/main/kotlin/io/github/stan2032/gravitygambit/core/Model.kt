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

enum class BodyKind { STAR, PLANET, REPULSOR }

/**
 * A gravitational body. Positions are static within a match (deterministic and
 * easy to reason about; orbital motion is a planned v2 feature).
 * REPULSOR bodies have negative mass and no solid surface (no collision).
 */
@Serializable
data class Body(
    val x: Double,
    val y: Double,
    val radius: Double,
    val mass: Double,
    val kind: BodyKind,
    val palette: Int,
)

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

    const val CAMPAIGN_SHOTS = 4
    const val DAILY_SHOTS = 5
    const val DAILY_HIT_SCORE = 100
    const val DUEL_MAX_TURNS = 30
}
