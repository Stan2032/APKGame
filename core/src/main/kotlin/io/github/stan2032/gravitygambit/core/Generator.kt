package io.github.stan2032.gravitygambit.core

/**
 * Procedural star-system generator with a built-in solvability solver.
 *
 * The generator is honest about difficulty: for each candidate layout it
 * brute-force simulates a grid of sample shots (angles x powers) and measures
 * the "solution basin" — what fraction of the aim space actually hits the
 * target. A layout is only accepted when that hit-rate falls inside the band
 * for the requested difficulty, so levels are *guaranteed solvable* and the
 * ramp is measured, not vibes. Everything is deterministic in (seed, difficulty).
 */
object SystemGenerator {

    const val SOLVER_ANGLE_STEP = 20      // deci-degrees -> 180 samples
    val SOLVER_POWERS = intArrayOf(300, 500, 700, 900, 1000)
    private const val MAX_ATTEMPTS = 24

    fun minRate(difficulty: Int): Double =
        maxOf(0.0008, 0.030 * StrictMath.pow(0.72, difficulty.toDouble()))

    fun maxRate(difficulty: Int): Double = minOf(0.28, minRate(difficulty) * 14.0)

    fun generate(seed: Long, difficulty: Int, mode: GameMode): SystemSpec {
        var best: SystemSpec? = null
        var bestScore = Double.MAX_VALUE
        val lo = minRate(difficulty)
        val hi = maxRate(difficulty)

        for (attempt in 0 until MAX_ATTEMPTS) {
            val candidate = candidate(seed, attempt, difficulty) ?: continue
            val rate = solveRate(candidate, shooter = 0, lo, hi)
            if (mode == GameMode.DUEL) {
                // Only solve the mirror side when this one is already in band —
                // an out-of-band candidate can never be accepted, so the second
                // (equally expensive) scan would be wasted work.
                if (rate in lo..hi) {
                    val rate2 = solveRate(candidate, shooter = 1, lo, hi)
                    val ratio = if (rate2 > 0) rate / rate2 else 99.0
                    // Both sides must have a real shot, and comparably so.
                    if (rate2 >= lo && ratio <= 4.0 && ratio >= 0.25) {
                        return candidate.copy(solverHitRate = rate)
                    }
                }
            } else {
                if (rate in lo..hi) return candidate.copy(solverHitRate = rate)
            }
            // Track nearest miss as fallback (distance to band, log-ish).
            if (rate > 0) {
                val d = when {
                    rate < lo -> lo / rate
                    else -> rate / hi
                }
                if (d < bestScore) { bestScore = d; best = candidate.copy(solverHitRate = rate) }
            }
        }
        return best ?: fallback(seed, difficulty)
    }

    /** Fraction of sampled aim space (STANDARD shot) that hits the opponent station. */
    fun solveRate(spec: SystemSpec, shooter: Int): Double = solveRate(spec, shooter, 0.0, 1.0)

    /**
     * Same measurement, but allowed to stop early once the final rate is
     * provably outside [lo, hi] — the caller only needs the in/out verdict for
     * rejected candidates. Cuts worst-case generation (easy levels, which need
     * a rare high-hit-rate layout) by exiting hopeless candidates mid-scan.
     * The returned value is exact whenever it lands inside the band, so an
     * accepted spec's recorded solverHitRate is unchanged.
     */
    fun solveRate(spec: SystemSpec, shooter: Int, lo: Double, hi: Double): Double {
        val target = 1 - shooter
        val total = (3600 / SOLVER_ANGLE_STEP) * SOLVER_POWERS.size
        var hits = 0
        var done = 0
        var angle = 0
        while (angle < 3600) {
            for (power in SOLVER_POWERS) {
                val shot = LiveShot(
                    spec.bodies, spec.stations, shooter, angle, power,
                    ProjectileType.STANDARD.id, maxTicks = Tuning.SOLVER_MAX_TICKS,
                )
                val res = shot.runToCompletion()
                if (res.stationHits.any { it == target }) hits++
                done++
            }
            // Bounds on the achievable final rate given what's left to sample.
            val remaining = total - done
            val minPossible = hits.toDouble() / total
            val maxPossible = (hits + remaining).toDouble() / total
            if (minPossible > hi) return minPossible      // provably too easy
            if (maxPossible < lo) return maxPossible      // provably too hard
            angle += SOLVER_ANGLE_STEP
        }
        return hits.toDouble() / total
    }

    private fun candidate(seed: Long, attempt: Int, difficulty: Int): SystemSpec? {
        val rng = Rng(Rng.mix(seed, attempt.toLong()))
        val bodies = ArrayList<Body>(8)

        // --- Star(s) ---
        val binary = difficulty >= 8 && rng.chance(0.30)
        if (binary) {
            val cx = Tuning.WORLD_W / 2 + rng.range(-80.0, 80.0)
            val cy = Tuning.WORLD_H / 2 + rng.range(-100.0, 100.0)
            val sep = rng.range(150.0, 230.0)
            val ang = rng.range(0.0, Math.PI)
            val ox = StrictMath.cos(ang) * sep / 2
            val oy = StrictMath.sin(ang) * sep / 2
            bodies.add(Body(cx - ox, cy - oy, rng.range(42.0, 60.0), rng.range(3.2e6, 5.5e6), BodyKind.STAR, 0))
            bodies.add(Body(cx + ox, cy + oy, rng.range(42.0, 60.0), rng.range(3.2e6, 5.5e6), BodyKind.STAR, 1))
        } else {
            bodies.add(
                Body(
                    Tuning.WORLD_W / 2 + rng.range(-120.0, 120.0),
                    Tuning.WORLD_H / 2 + rng.range(-140.0, 140.0),
                    rng.range(55.0, 85.0),
                    rng.range(6.0e6, 1.1e7),
                    BodyKind.STAR, 0,
                )
            )
        }

        // --- Planets ---
        val planetCount = when {
            difficulty <= 1 -> 2
            difficulty <= 3 -> 3
            difficulty <= 5 -> 4
            difficulty <= 7 -> 5
            else -> 5 + rng.nextInt(2)
        }
        repeat(planetCount) {
            val placed = tryPlace(rng, bodies) { x, y ->
                val r = rng.range(28.0, 60.0)
                Body(x, y, r, r * r * rng.range(550.0, 850.0), BodyKind.PLANET, 2 + rng.nextInt(5))
            }
            if (!placed) return null
        }

        // --- Repulsor anomaly (negative mass, intangible) ---
        if (difficulty >= 5 && rng.chance(0.35)) {
            tryPlace(rng, bodies) { x, y ->
                Body(x, y, 24.0, -rng.range(8.0e5, 1.6e6), BodyKind.REPULSOR, 7)
            }
        }

        // --- Stations ---
        val ps = placeStation(rng, bodies, yMin = Tuning.WORLD_H - 190.0, yMax = Tuning.WORLD_H - 110.0, owner = 0)
            ?: return null
        val ts = placeStation(rng, bodies, yMin = 110.0, yMax = 190.0, owner = 1)
            ?: return null

        return SystemSpec(
            seed = seed,
            difficulty = difficulty,
            name = NameGen.systemName(Rng(Rng.mix(seed, 777L))),
            bodies = bodies,
            stations = listOf(ps, ts),
            solverHitRate = 0.0,
        )
    }

    private inline fun tryPlace(rng: Rng, bodies: MutableList<Body>, make: (Double, Double) -> Body): Boolean {
        repeat(40) {
            val x = rng.range(90.0, Tuning.WORLD_W - 90.0)
            val y = rng.range(230.0, Tuning.WORLD_H - 230.0)
            val b = make(x, y)
            val ok = bodies.all { o ->
                val dx = o.x - b.x; val dy = o.y - b.y
                val min = (o.radius + b.radius) * 1.9 + 30.0
                dx * dx + dy * dy > min * min
            }
            if (ok) { bodies.add(b); return true }
        }
        return false
    }

    private fun placeStation(rng: Rng, bodies: List<Body>, yMin: Double, yMax: Double, owner: Int): Station? {
        repeat(40) {
            val x = rng.range(140.0, Tuning.WORLD_W - 140.0)
            val y = rng.range(yMin, yMax)
            val ok = bodies.all { b ->
                val dx = b.x - x; val dy = b.y - y
                val min = b.radius + Tuning.STATION_RADIUS + 60.0
                dx * dx + dy * dy > min * min
            }
            if (ok) return Station(x, y, owner)
        }
        return null
    }

    /** Guaranteed-sane layout used only if 24 candidate attempts all fail the band. */
    private fun fallback(seed: Long, difficulty: Int): SystemSpec {
        val bodies = listOf(
            Body(Tuning.WORLD_W / 2, Tuning.WORLD_H / 2, 70.0, 8.0e6, BodyKind.STAR, 0),
            Body(Tuning.WORLD_W * 0.22, Tuning.WORLD_H * 0.33, 40.0, 1.1e6, BodyKind.PLANET, 3),
            Body(Tuning.WORLD_W * 0.78, Tuning.WORLD_H * 0.67, 40.0, 1.1e6, BodyKind.PLANET, 4),
        )
        val stations = listOf(
            Station(Tuning.WORLD_W * 0.5, Tuning.WORLD_H - 150.0, 0),
            Station(Tuning.WORLD_W * 0.5, 150.0, 1),
        )
        val spec = SystemSpec(seed, difficulty, "Haven Reach", bodies, stations, 0.0)
        return spec.copy(solverHitRate = solveRate(spec, 0))
    }
}

object NameGen {
    private val PRE = listOf("Vela", "Kepra", "Orin", "Talos", "Nyx", "Auriga", "Cygni", "Deneb", "Rigel", "Vesta", "Lyra", "Zeta")
    private val SUF = listOf("Reach", "Drift", "Verge", "Expanse", "Cluster", "Run", "Abyss", "Crown", "Wells", "Spur")

    fun systemName(rng: Rng): String = "${PRE[rng.nextInt(PRE.size)]} ${SUF[rng.nextInt(SUF.size)]}"
}

/** Seed derivation for every mode — the single source of truth for "which level is this". */
object Seeds {
    private val MASTER = 0x47524156495459AAUL.toLong() // "GRAVITY" + tag

    /** Days since epoch for 2026-08-01, daily challenge #1. */
    const val LAUNCH_EPOCH_DAY = 20666L

    fun campaign(sector: Int, level: Int): Long = Rng.mix(MASTER, sector * 1000L + level)

    fun daily(epochDay: Long): Long = Rng.mix(MASTER xor 0x00DA111EEDL, epochDay)

    fun dailyNumber(epochDay: Long): Int = (epochDay - LAUNCH_EPOCH_DAY).toInt() + 1

    fun duelRandom(entropy: Long): Long = Rng.mix(MASTER xor 0x00D0E11EL, entropy)

    fun campaignDifficulty(sector: Int, level: Int): Int = minOf(11, sector * 2 + level / 6)

    const val LEVELS_PER_SECTOR = 12
    const val DAILY_DIFFICULTY = 6
    const val DUEL_DIFFICULTY = 5
}
