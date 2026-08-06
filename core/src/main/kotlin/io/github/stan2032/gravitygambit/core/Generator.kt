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

    /**
     * Anchor-leverage probe. Anchors (docs/VISION.md §3.1) let the player bend
     * the field, so a hit-rate measured on the bare field understates how
     * solvable a system really is. Rather than re-sweeping the whole aim space
     * per anchor placement (prohibitively expensive), we ask a sharper
     * question: **of the shots that currently miss, what fraction can a single
     * anchor rescue?**
     *
     * Only the upper bound is enforced. An initial design required a *minimum*
     * leverage too ("anchors must matter here"), but that rejected perfectly
     * good pure-aim layouts — variety in how much anchors help is a feature,
     * not a defect — and each rejection costs another full solve sweep, which
     * tripled generation time. So: measure always, reject only when anchors
     * trivialize aiming ([MAX_LEVERAGE]).
     */
    /**
     * "Aim at it and fire" probe: a cone around the direct bearing, at every
     * power. Anything inside this cone that connects means the level teaches
     * nothing.
     */
    const val NAIVE_SPREAD_DECI = 220        // +/-22 degrees: genuinely "at it"
    const val NAIVE_STEP_DECI = 40           // 4-degree steps
    val NAIVE_POWERS = intArrayOf(250, 400, 550, 700, 850, 1000)

    const val LEVERAGE_PROBE_SHOTS = 5
    const val LEVERAGE_GRID_X = 4
    const val LEVERAGE_GRID_Y = 5
    /**
     * Shorter horizon than the main solver: a shot an anchor rescues connects
     * early, so the long tail is wasted work. Keeps the probe affordable —
     * generation is what a player waits on before their first shot.
     */
    const val LEVERAGE_MAX_TICKS = 600
    const val MAX_LEVERAGE = 0.80
    val SOLVER_POWERS = intArrayOf(300, 500, 700, 900, 1000)

    /**
     * Grid for proving gambit feasibility. Coarser than the solver grid on
     * angle (existence, not a rate) but wider on power: "featherweight" lives
     * below 400, which the solver grid barely samples.
     */
    const val GAMBIT_ANGLE_STEP = 40
    val GAMBIT_POWERS = intArrayOf(250, 350, 500, 700, 900, 1000)

    /**
     * Search grid for proving a [Objective.CAPTURE] system is winnable.
     *
     * Deliberately narrow, and aimed where the answers actually are: a probe
     * that swept the whole space found every capture solution at low power
     * (200-350) with the burn around ticks 140-220, because a capture is a
     * slow arrival followed by an insertion burn. Searching the fast half of
     * the power range is searching where solutions cannot be.
     */
    const val CAPTURE_ANGLE_STEP = 60
    val CAPTURE_POWERS = intArrayOf(200, 350, 500)
    val CAPTURE_BURN_TICKS = intArrayOf(100, 140, 180, 220, 260)
    /** Fraction of eligible campaign systems that ask for something else. */
    const val OBJECTIVE_CHANCE = 0.28
    /** Below this the field is too sparse for an objective worth flying. */
    const val OBJECTIVE_MIN_DIFFICULTY = 2
    private const val MAX_ATTEMPTS = 70

    /**
     * Difficulty bands, recalibrated for curated layouts.
     *
     * The old numbers assumed a randomly-scattered field with an often-open
     * corridor, where a healthy slice of the aim space connected. Now that a
     * keeper always blocks the direct line, honest hit-rates are far lower —
     * measurement put difficulty 0 at ~2.7%, which fell *below* its own old
     * floor of 3% and silently pushed every early level onto the fallback
     * path. Bands follow the game rather than the game following the bands.
     */
    fun minRate(difficulty: Int): Double =
        maxOf(0.0006, 0.019 * StrictMath.pow(0.80, difficulty.toDouble()))

    fun maxRate(difficulty: Int): Double = minOf(0.30, minRate(difficulty) * 22.0)

    fun generate(seed: Long, difficulty: Int, mode: GameMode): SystemSpec =
        withObjective(withGambitMasks(generateLayout(seed, difficulty, mode), mode), seed, difficulty, mode)

    /**
     * Assign the system's objective — and only ever one it has been *proved*
     * to be able to pay.
     *
     * Campaign only. A duel is a contest between two players and a daily is a
     * score gauntlet; neither has a place for "hold an orbit", and inventing
     * one would change what those modes are.
     *
     * If the proof fails the system stays a Breach. That is the D-24 rule
     * applied a second time: the game must never set a task it cannot confirm
     * is achievable, and a failed proof is not a reason to ship it anyway.
     */
    private fun withObjective(
        spec: SystemSpec,
        seed: Long,
        difficulty: Int,
        mode: GameMode,
    ): SystemSpec {
        if (mode != GameMode.CAMPAIGN || difficulty < OBJECTIVE_MIN_DIFFICULTY) return spec
        val rng = Rng(Rng.mix(seed, 0x0B1EC71E5L))
        if (!rng.chance(OBJECTIVE_CHANCE)) return spec

        // Collect only the objectives this system has been *proved* to pay,
        // then pick among them. Order is fixed so the choice stays a pure
        // function of the seed.
        val offers = ArrayList<Pair<Objective, Int>>(2)
        captureCandidate(spec)?.let { if (proveCapture(spec, it)) offers.add(Objective.CAPTURE to it) }
        courierCandidate(spec)?.let { if (proveCourier(spec, it)) offers.add(Objective.COURIER to it) }
        if (offers.isEmpty()) return spec
        val (objective, target) = offers[rng.nextInt(offers.size)]
        return spec.copy(objectiveId = objective.id, markedBody = target)
    }

    /**
     * The heaviest settled world: an orbit needs something to orbit, and a
     * marked target that drifts (comet) or repels (anomaly) would be a
     * different, worse puzzle.
     */
    private fun captureCandidate(spec: SystemSpec): Int? {
        var best = -1
        var bestMass = 0.0
        for (i in spec.bodies.indices) {
            val b = spec.bodies[i]
            if (b.kind != BodyKind.STAR && b.kind != BodyKind.PLANET) continue
            if (b.mass > bestMass) { bestMass = b.mass; best = i }
        }
        return if (best >= 0) best else null
    }

    /** The comet a Courier must strike; null if the system has none. */
    private fun courierCandidate(spec: SystemSpec): Int? =
        spec.bodies.indexOfFirst { it.kind == BodyKind.COMET }.takeIf { it >= 0 }

    /**
     * Is the marked comet actually reachable? Measured need: of 36 sampled
     * levels 11 carried a comet and **two of those could not be hit at all**,
     * so presence is not proof.
     */
    fun proveCourier(spec: SystemSpec, bodyIndex: Int): Boolean {
        var angle = 0
        while (angle < 3600) {
            for (power in GAMBIT_POWERS) {
                val res = LiveShot(
                    spec.bodies, spec.stations, 0, angle, power,
                    ProjectileType.STANDARD.id,
                    maxTicks = Tuning.SOLVER_MAX_TICKS,
                    markedBody = bodyIndex,
                ).runToCompletion()
                if (res.markedBodyHit) return true
            }
            angle += GAMBIT_ANGLE_STEP
        }
        return false
    }

    /** Is there a RETRO shot and burn tick that holds the ring? First hit wins. */
    fun proveCapture(spec: SystemSpec, bodyIndex: Int): Boolean {
        var angle = 0
        while (angle < 3600) {
            for (power in CAPTURE_POWERS) {
                for (burn in CAPTURE_BURN_TICKS) {
                    val res = LiveShot(
                        spec.bodies, spec.stations, 0, angle, power,
                        ProjectileType.RETRO.id,
                        presetBrakeTick = burn,
                        maxTicks = Tuning.SOLVER_MAX_TICKS,
                        markedBody = bodyIndex,
                    ).runToCompletion()
                    if (res.captureDwell >= Tuning.CAPTURE_TICKS) return true
                }
            }
            angle += CAPTURE_ANGLE_STEP
        }
        return false
    }

    private fun withGambitMasks(spec: SystemSpec, mode: GameMode): SystemSpec {
        val seats = if (mode == GameMode.DUEL) 2 else 1
        // Accepted layouts already carry masks from their (completed) solver
        // sweeps, at no extra cost. The dedicated sweep is only paid for a seat
        // that came back with nothing — either a fallback layout whose scan
        // bailed early, or a seat where the solver grid genuinely found no
        // qualifying shot. Its grid samples low powers the solver grid does
        // not, so it is a second opinion rather than a repeat.
        val masks = (0 until seats).map { seat ->
            val fromScan = spec.gambitMasks.getOrElse(seat) { 0 }
            if (fromScan != 0) fromScan else gambitOptions(spec, seat)
        }
        return spec.copy(
            gambitMasks = if (seats == 1) listOf(masks[0], masks[0]) else masks,
        )
    }

    private fun generateLayout(seed: Long, difficulty: Int, mode: GameMode): SystemSpec {
        var best: SystemSpec? = null
        var bestScore = Double.MAX_VALUE
        val lo = minRate(difficulty)
        val hi = maxRate(difficulty)

        for (attempt in 0 until MAX_ATTEMPTS) {
            val candidate = candidate(seed, attempt, difficulty) ?: continue
            // Cheapest meaningful rejection first: if the obvious shot wins,
            // nothing else about this layout matters.
            if (fallsToNaiveShot(candidate, shooter = 0)) continue
            if (mode == GameMode.DUEL && fallsToNaiveShot(candidate, shooter = 1)) continue

            val scan = scan(candidate, shooter = 0, lo, hi)
            val rate = scan.rate
            if (mode == GameMode.DUEL) {
                // Only solve the mirror side when this one is already in band —
                // an out-of-band candidate can never be accepted, so the second
                // (equally expensive) scan would be wasted work.
                if (rate in lo..hi) {
                    val scan2 = scan(candidate, shooter = 1, lo, hi)
                    val rate2 = scan2.rate
                    val ratio = if (rate2 > 0) rate / rate2 else 99.0
                    // Both sides must have a real shot, and comparably so.
                    if (rate2 >= lo && ratio <= 4.0 && ratio >= 0.25) {
                        // Each seat gets the declarations its own station can
                        // honour; the fields they face are not the same.
                        return candidate.copy(
                            solverHitRate = rate,
                            gambitMasks = listOf(scan.gambitMask, scan2.gambitMask),
                        )
                    }
                }
            } else if (rate in lo..hi) {
                // The bare hit-rate says how hard the level looks. Anchor
                // leverage says whether the player's own tools trivialize it.
                val leverage = anchorLeverage(candidate, shooter = 0)
                if (leverage <= MAX_LEVERAGE) {
                    return candidate.copy(
                        solverHitRate = rate,
                        anchorLeverage = leverage,
                        // Single-shooter modes: both seats read the same menu.
                        gambitMasks = listOf(scan.gambitMask, scan.gambitMask),
                    )
                }
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
        // A duel fallback that only one seat can win is worse than useless, so
        // never hand one back: prefer any candidate that is playable from both
        // sides, even out of band.
        if (mode == GameMode.DUEL) {
            val b = best
            if (b == null || solveRate(b, 1) <= 0.0 || solveRate(b, 0) <= 0.0) {
                return blockingFallback(seed, difficulty, bothSeats = true)
            }
        }
        return best ?: blockingFallback(seed, difficulty, bothSeats = false)
    }

    /**
     * Which declarations this arena can honour: a coarse sweep looking for at
     * least one *winning* shot per gambit. Run once, on the spec that is
     * actually returned — never on rejected candidates.
     *
     * Deliberately coarse, and deliberately asymmetric in its error: a sparse
     * grid can miss a satisfying shot (so an achievable gambit goes unoffered)
     * but it can never invent one, because every bit set was set by a real
     * simulated hit. Under-offering costs a little variety; over-offering would
     * mean the game inviting the player to make a bet it knows is unwinnable.
     *
     * Measured need: on twelve curated levels, "two-body" had **zero** winning
     * shots on three of them. Without this the menu would have shown it anyway.
     */
    fun gambitOptions(spec: SystemSpec, shooter: Int): Int {
        val target = 1 - shooter
        val complete = Gambit.declarable.fold(0) { m, g -> m or (1 shl g.id) }
        var mask = 0
        var angle = 0
        while (angle < 3600) {
            for (power in GAMBIT_POWERS) {
                if (mask == complete) return mask
                val res = LiveShot(
                    spec.bodies, spec.stations, shooter, angle, power,
                    ProjectileType.STANDARD.id, maxTicks = Tuning.SOLVER_MAX_TICKS,
                ).runToCompletion()
                if (res.stationHits.none { it == target }) continue
                for (g in Gambit.declarable) {
                    if (g.satisfiedBy(res)) mask = mask or (1 shl g.id)
                }
            }
            angle += GAMBIT_ANGLE_STEP
        }
        return mask
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
    fun solveRate(spec: SystemSpec, shooter: Int, lo: Double, hi: Double): Double =
        scan(spec, shooter, lo, hi).rate

    /**
     * One solver sweep and everything derivable from it.
     *
     * [gambitMask] rides along for free: the sweep already simulates every
     * sample shot to completion, and [ShotResult] already carries the
     * measurements a gambit is judged on, so proving feasibility costs four
     * comparisons per *winning* sample rather than a second sweep. A dedicated
     * pass measured 300–500ms on hard levels; this measures nothing.
     *
     * [complete] says whether the sweep reached the end of the grid. An
     * early-exited scan has only seen part of the aim space, so its mask is
     * not trustworthy as a complete answer and callers must not publish it.
     */
    class SolveScan(val rate: Double, val complete: Boolean, val gambitMask: Int)

    fun scan(spec: SystemSpec, shooter: Int, lo: Double, hi: Double): SolveScan {
        val target = 1 - shooter
        val total = (3600 / SOLVER_ANGLE_STEP) * SOLVER_POWERS.size
        var hits = 0
        var done = 0
        var angle = 0
        var mask = 0
        while (angle < 3600) {
            for (power in SOLVER_POWERS) {
                val shot = LiveShot(
                    spec.bodies, spec.stations, shooter, angle, power,
                    ProjectileType.STANDARD.id, maxTicks = Tuning.SOLVER_MAX_TICKS,
                )
                val res = shot.runToCompletion()
                if (res.stationHits.any { it == target }) {
                    hits++
                    for (g in Gambit.declarable) {
                        if (g.satisfiedBy(res)) mask = mask or (1 shl g.id)
                    }
                }
                done++
            }
            // Bounds on the achievable final rate given what's left to sample.
            val remaining = total - done
            val minPossible = hits.toDouble() / total
            val maxPossible = (hits + remaining).toDouble() / total
            if (minPossible > hi) return SolveScan(minPossible, false, mask)  // too easy
            if (maxPossible < lo) return SolveScan(maxPossible, false, mask)  // too hard
            angle += SOLVER_ANGLE_STEP
        }
        return SolveScan(hits.toDouble() / total, true, mask)
    }

    /**
     * Does the level fall to the obvious shot — point at the target and fire?
     *
     * This is the question the hit-rate solver never asked, and it is the one
     * that decides whether a level has a *purpose*. A system can sit perfectly
     * inside its difficulty band while still being beaten by a straight lob,
     * because "what fraction of the whole aim space hits" says nothing about
     * "does the first thing anyone tries work". Levels that fail this are
     * rejected outright: if gravity is optional, there is no game.
     */
    fun fallsToNaiveShot(spec: SystemSpec, shooter: Int): Boolean {
        val target = spec.stations[1 - shooter]
        val from = spec.stations[shooter]
        val direct = StrictMath.toDegrees(StrictMath.atan2(target.y - from.y, target.x - from.x))
        val centre = (((direct * 10).toInt() % 3600) + 3600) % 3600
        var dev = -NAIVE_SPREAD_DECI
        while (dev <= NAIVE_SPREAD_DECI) {
            val angle = ((centre + dev) % 3600 + 3600) % 3600
            for (power in NAIVE_POWERS) {
                val res = LiveShot(
                    spec.bodies, spec.stations, shooter, angle, power,
                    ProjectileType.STANDARD.id, maxTicks = Tuning.SOLVER_MAX_TICKS,
                ).runToCompletion()
                if (res.stationHits.any { it == 1 - shooter }) return true
            }
            dev += NAIVE_STEP_DECI
        }
        return false
    }

    /**
     * Fraction of missing sample shots that one anchor can convert into a hit.
     * Cheap by construction: a handful of representative misses against a
     * coarse anchor grid, with an early exit once the verdict is decided.
     */
    fun anchorLeverage(spec: SystemSpec, shooter: Int): Double {
        val target = 1 - shooter
        // Collect a spread of shots that currently miss.
        val misses = ArrayList<Pair<Int, Int>>(LEVERAGE_PROBE_SHOTS)
        var angle = 0
        while (angle < 3600 && misses.size < LEVERAGE_PROBE_SHOTS) {
            for (power in intArrayOf(450, 750, 1000)) {
                if (misses.size >= LEVERAGE_PROBE_SHOTS) break
                val res = LiveShot(
                    spec.bodies, spec.stations, shooter, angle, power,
                    ProjectileType.STANDARD.id, maxTicks = LEVERAGE_MAX_TICKS,
                ).runToCompletion()
                if (res.stationHits.none { it == target }) misses.add(angle to power)
            }
            angle += 260
        }
        if (misses.isEmpty()) return 1.0   // nothing missed: already trivial

        var rescued = 0
        for ((i, m) in misses.withIndex()) {
            if (canOneAnchorRescue(spec, shooter, target, m.first, m.second)) rescued++
            // Early exit once the outcome cannot change the verdict.
            val remaining = misses.size - i - 1
            if (rescued.toDouble() / misses.size > MAX_LEVERAGE) break
            if ((rescued + remaining).toDouble() / misses.size <= MAX_LEVERAGE) break
        }
        return rescued.toDouble() / misses.size
    }

    private fun canOneAnchorRescue(
        spec: SystemSpec,
        shooter: Int,
        target: Int,
        angle: Int,
        power: Int,
    ): Boolean {
        for (gx in 0 until LEVERAGE_GRID_X) {
            for (gy in 0 until LEVERAGE_GRID_Y) {
                val x = Tuning.WORLD_W * (gx + 0.5) / LEVERAGE_GRID_X
                val y = Tuning.WORLD_H * (gy + 0.5) / LEVERAGE_GRID_Y
                if (!anchorSpotClear(spec, x, y)) continue
                for (attract in booleanArrayOf(true, false)) {
                    val res = LiveShot(
                        spec.bodies, spec.stations, shooter, angle, power,
                        ProjectileType.STANDARD.id, maxTicks = LEVERAGE_MAX_TICKS,
                        anchors = listOf(Anchor(x.toInt(), y.toInt(), attract)),
                    ).runToCompletion()
                    if (res.stationHits.any { it == target }) return true
                }
            }
        }
        return false
    }

    /** Mirrors the placement rule the UI enforces, so the probe is honest. */
    private fun anchorSpotClear(spec: SystemSpec, x: Double, y: Double): Boolean {
        for (b in spec.bodies) {
            val dx = b.xAt(0) - x; val dy = b.yAt(0) - y
            val min = b.radius + Tuning.ANCHOR_MIN_CLEARANCE
            if (dx * dx + dy * dy < min * min) return false
        }
        for (st in spec.stations) {
            val dx = st.x - x; val dy = st.y - y
            val min = Tuning.STATION_RADIUS + Tuning.ANCHOR_MIN_CLEARANCE
            if (dx * dx + dy * dy < min * min) return false
        }
        return true
    }

    /**
     * Builds a layout *around the shot it wants to force*, rather than
     * scattering bodies and hoping.
     *
     * The old approach placed bodies at random with only separation rules,
     * which produced levels whose direct station-to-station corridor was wide
     * open — a straight lob won, gravity was decorative, and the level had no
     * purpose. Now the corridor is furnished deliberately:
     *
     *  1. Stations are fixed first, defining the direct line.
     *  2. A **keeper** is placed ON that line, sized to eclipse it, so the
     *     obvious shot is blocked by construction rather than by luck.
     *  3. **Shepherds** are placed alternating either side of the line at
     *     offsets that make a curved route plausible — the bodies a player
     *     will actually slingshot around.
     *  4. Decor (repulsor/pulsar/comet) fills the rest of the field.
     *
     * The solver still has the final say; this only stops it wasting attempts
     * on layouts that were never going to be interesting.
     */
    private fun candidate(seed: Long, attempt: Int, difficulty: Int): SystemSpec? {
        val rng = Rng(Rng.mix(seed, attempt.toLong()))
        val bodies = ArrayList<Body>(8)

        // --- 1. Stations first: they define the problem. ---
        val ps = Station(rng.range(150.0, Tuning.WORLD_W - 150.0), rng.range(Tuning.WORLD_H - 190.0, Tuning.WORLD_H - 120.0), 0)
        val ts = Station(rng.range(150.0, Tuning.WORLD_W - 150.0), rng.range(120.0, 190.0), 1)

        val lx = ts.x - ps.x
        val ly = ts.y - ps.y
        val llen = Math.sqrt(lx * lx + ly * ly)
        if (llen < 1e-6) return null
        val ux = lx / llen           // unit vector along the corridor
        val uy = ly / llen
        val nx = -uy                 // unit normal to it
        val ny = ux

        fun onLine(t: Double, offset: Double) = Pair(
            ps.x + ux * llen * t + nx * offset,
            ps.y + uy * llen * t + ny * offset,
        )

        // --- 2. The keeper: blocks the obvious shot by construction. ---
        val keeperT = rng.range(0.38, 0.62)
        val keeperR = rng.range(58.0, 86.0)
        val (kx, ky) = onLine(keeperT, rng.range(-12.0, 12.0))
        if (kx < keeperR || kx > Tuning.WORLD_W - keeperR) return null
        val keeperIsStar = difficulty <= 6 || rng.chance(0.6)
        bodies.add(
            if (keeperIsStar) Body(kx, ky, keeperR, rng.range(6.0e6, 1.05e7), BodyKind.STAR, 0)
            else Body(kx, ky, keeperR, keeperR * keeperR * rng.range(700.0, 900.0), BodyKind.PLANET, 2 + rng.nextInt(5))
        )

        // --- 3. Shepherds: the bodies a curved route can use. ---
        val shepherds = when {
            difficulty <= 1 -> 1
            difficulty <= 4 -> 2
            difficulty <= 7 -> 3
            else -> 3 + rng.nextInt(2)
        }
        var side = if (rng.chance(0.5)) 1.0 else -1.0
        repeat(shepherds) {
            // Retry: a single attempt failed often enough to leave levels with
            // nothing but a keeper, which is not a puzzle, it is a wall.
            var tries = 0
            while (tries++ < 14) {
                val t = rng.range(0.16, 0.9)
                val offset = side * rng.range(140.0, 340.0)
                val r = rng.range(30.0, 56.0)
                val (sx, sy) = onLine(t, offset)
                if (sx < r + 40 || sx > Tuning.WORLD_W - r - 40) continue
                if (sy < r + 60 || sy > Tuning.WORLD_H - r - 60) continue
                val clear = bodies.all { o ->
                    val dx = o.x - sx; val dy = o.y - sy
                    val min = (o.radius + r) * 1.5 + 20.0
                    dx * dx + dy * dy > min * min
                }
                val clearOfStations = listOf(ps, ts).all { st ->
                    val dx = st.x - sx; val dy = st.y - sy
                    val min = r + Tuning.STATION_RADIUS + 56.0
                    dx * dx + dy * dy > min * min
                }
                if (clear && clearOfStations) {
                    bodies.add(Body(sx, sy, r, r * r * rng.range(560.0, 860.0), BodyKind.PLANET, 2 + rng.nextInt(5)))
                    break
                }
            }
            side = -side   // alternate sides so routes exist both ways
        }

        // A keeper alone is a wall, not a puzzle: every level needs something
        // to curve around.
        if (bodies.size < 2) return null

        // --- 4. Decor / hazards ---
        if (difficulty >= 5 && rng.chance(0.35)) {
            tryPlace(rng, bodies) { x, y ->
                Body(x, y, 24.0, -rng.range(8.0e5, 1.6e6), BodyKind.REPULSOR, 7)
            }
        }
        if (difficulty >= 4 && rng.chance(0.28)) {
            tryPlace(rng, bodies) { x, y ->
                Body(x, y, rng.range(26.0, 34.0), rng.range(1.6e6, 2.8e6), BodyKind.PULSAR, 8)
            }
        }
        if (difficulty >= 2 && rng.chance(0.42)) {
            val horizontal = rng.chance(0.5)
            val speed = rng.range(38.0, 78.0) * (if (rng.chance(0.5)) 1.0 else -1.0)
            bodies.add(
                Body(
                    x = if (horizontal) rng.range(0.0, Tuning.WORLD_W) else rng.range(120.0, Tuning.WORLD_W - 120.0),
                    y = if (horizontal) rng.range(260.0, Tuning.WORLD_H - 260.0) else rng.range(0.0, Tuning.WORLD_H),
                    radius = rng.range(14.0, 20.0),
                    mass = rng.range(1.4e5, 3.2e5),
                    kind = BodyKind.COMET,
                    palette = 9,
                    vx = if (horizontal) speed else 0.0,
                    vy = if (horizontal) 0.0 else speed,
                )
            )
        }

        // Stations must not have ended up inside anything.
        for (st in listOf(ps, ts)) {
            for (b in bodies) {
                if (b.kind == BodyKind.REPULSOR) continue
                val dx = b.x - st.x; val dy = b.y - st.y
                val min = b.radius + Tuning.STATION_RADIUS + 44.0
                if (dx * dx + dy * dy < min * min) return null
            }
        }

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

    /**
     * Guaranteed-blocking fallback, used when curated attempts cannot satisfy
     * the difficulty band.
     *
     * Every fallback must clear the same bar as a normal level: a fallback
     * that is beatable by pointing at the target would smuggle back exactly
     * the purposeless level this whole design exists to remove — and fallbacks
     * are invisible, so the bug would hide. The central keeper therefore grows
     * until the point-and-fire cone is provably dead, and (for duels) until
     * both seats have a route.
     */
    private fun blockingFallback(seed: Long, difficulty: Int, bothSeats: Boolean): SystemSpec {
        var last: SystemSpec? = null
        // Search a rich space rather than one fixed arena: station offsets make
        // the corridor diagonal, and a second keeper closes the cone when one
        // is not enough. A fallback that fails the naive probe would smuggle
        // back the purposeless level this change exists to remove — and
        // fallbacks are invisible, so that bug would hide.
        for (i in 0 until 48) {
            val rng = Rng(Rng.mix(seed, 0xFA11BACCL + i))
            val stations = listOf(
                Station(Tuning.WORLD_W / 2 + rng.range(-220.0, 220.0), Tuning.WORLD_H - rng.range(120.0, 180.0), 0),
                Station(Tuning.WORLD_W / 2 + rng.range(-220.0, 220.0), rng.range(120.0, 180.0), 1),
            )
            val a = stations[0]
            val b = stations[1]
            val lx = b.x - a.x
            val ly = b.y - a.y
            val len = Math.sqrt(lx * lx + ly * ly)
            val ux = lx / len
            val uy = ly / len
            val nx = -uy
            val ny = ux

            val bodies = ArrayList<Body>(4)
            val doubleKeeper = i >= 16
            val kr1 = 66.0 + (i % 8) * 5.0
            val t1 = if (doubleKeeper) 0.32 else rng.range(0.42, 0.58)
            bodies.add(
                Body(
                    a.x + ux * len * t1 + nx * rng.range(-10.0, 10.0),
                    a.y + uy * len * t1 + ny * rng.range(-10.0, 10.0),
                    kr1, rng.range(7.0e6, 1.05e7), BodyKind.STAR, 0,
                )
            )
            if (doubleKeeper) {
                val kr2 = 48.0 + (i % 6) * 5.0
                bodies.add(
                    Body(
                        a.x + ux * len * 0.7 + nx * rng.range(-14.0, 14.0),
                        a.y + uy * len * 0.7 + ny * rng.range(-14.0, 14.0),
                        kr2, kr2 * kr2 * 780.0, BodyKind.PLANET, 3,
                    )
                )
            }
            for (sgn in intArrayOf(-1, 1)) {
                val off = sgn * rng.range(190.0, 320.0)
                val t = rng.range(0.3, 0.75)
                val px = a.x + ux * len * t + nx * off
                val py = a.y + uy * len * t + ny * off
                if (px > 90 && px < Tuning.WORLD_W - 90 && py > 120 && py < Tuning.WORLD_H - 120) {
                    bodies.add(Body(px, py, 42.0, 1.35e6, BodyKind.PLANET, 4))
                }
            }
            if (bodies.size < 2) continue

            val stationsClear = stations.all { st ->
                bodies.all { body ->
                    val dx = body.x - st.x; val dy = body.y - st.y
                    val min = body.radius + Tuning.STATION_RADIUS + 40.0
                    dx * dx + dy * dy > min * min
                }
            }
            if (!stationsClear) continue

            val spec = SystemSpec(
                seed, difficulty, NameGen.systemName(Rng(Rng.mix(seed, 777L))),
                bodies, stations, 0.0,
            )
            last = spec
            if (fallsToNaiveShot(spec, 0)) continue
            if (bothSeats && fallsToNaiveShot(spec, 1)) continue
            val r0 = solveRate(spec, 0)
            if (r0 <= 0.0) continue
            if (bothSeats && solveRate(spec, 1) <= 0.0) continue
            return spec.copy(solverHitRate = r0)
        }
        return last!!.copy(solverHitRate = solveRate(last, 0))
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
