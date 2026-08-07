package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * MEASUREMENT ONLY — no design is committed here.
 *
 * VISION Stage F proposes that "a shot that dies leaves debris: a small
 * permanent body at its death point". Before that rule is written, three
 * things have to be *measured*, because each of them can kill the naive
 * version outright:
 *
 *  1. **Where do misses actually die?** If most shots leave the field, then
 *     "debris at the death point" spawns almost nothing on the board and the
 *     mechanic is inert. This is the same question that reshaped Capture
 *     (D-26): the naive design assumed a physical possibility that
 *     measurement denied.
 *  2. **How much does one debris body perturb the field?** A body with mass
 *     bends every later shot; a pure collider only blocks. The difference
 *     decides whether debris is a tactical wrinkle or a wrecking ball.
 *  3. **Does debris strand the player?** Every objective is generator-proved
 *     solvable on the *initial* board. Debris accumulates mid-match, so a
 *     level proved winnable can stop being winnable — which is exactly the
 *     failure mode D-24 and D-27 exist to forbid.
 *
 * These print rather than assert. The numbers go into docs/DECISIONS.md and
 * the rule is written from them, not from this comment.
 */
class DebrisMeasurementTest {

    private enum class Fate { TARGET, BODY, OWN_STATION, OUT_OF_BOUNDS, TIMEOUT }

    private class Death(val fate: Fate, val x: Double, val y: Double)

    /**
     * Runs one shot and reports how it ended and where. Restricted to STANDARD
     * shells, which never split, so there is exactly one projectile to follow.
     */
    private fun fateOf(spec: SystemSpec, shooter: Int, angle: Int, power: Int): Death {
        val shot = LiveShot(
            spec.bodies, spec.stations, shooter, angle, power,
            ProjectileType.STANDARD.id, maxTicks = Tuning.SOLVER_MAX_TICKS,
        )
        var lastX = 0.0
        var lastY = 0.0
        while (shot.result == null) {
            shot.visitProjectiles { _, x, y, _ -> lastX = x; lastY = y }
            shot.step()
        }
        val res = shot.result!!
        val impact = shot.lastImpact
        val fate = when {
            res.stationHits.any { it != shooter } -> Fate.TARGET
            res.stationHits.isNotEmpty() -> Fate.OWN_STATION
            impact != null -> Fate.BODY
            res.ticks >= Tuning.SOLVER_MAX_TICKS -> Fate.TIMEOUT
            else -> Fate.OUT_OF_BOUNDS
        }
        val at = impact ?: Vec2(lastX, lastY)
        return Death(fate, at.x, at.y)
    }

    /** The solver's own sample grid, so the sample matches how levels are judged. */
    private fun sweep(spec: SystemSpec, shooter: Int): List<Death> {
        val out = ArrayList<Death>(180 * SystemGenerator.SOLVER_POWERS.size)
        var angle = 0
        while (angle < 3600) {
            for (power in SystemGenerator.SOLVER_POWERS) {
                out.add(fateOf(spec, shooter, angle, power))
            }
            angle += SystemGenerator.SOLVER_ANGLE_STEP
        }
        return out
    }

    private fun onBoard(d: Death): Boolean =
        d.x >= 0.0 && d.x <= Tuning.WORLD_W && d.y >= 0.0 && d.y <= Tuning.WORLD_H

    /**
     * The shots a *player* actually fires, which is not what the solver grid
     * samples. The grid sweeps all 360 degrees, so most of it is aimed into
     * empty space away from the target; a player points roughly at the enemy
     * and misses by a bit. This cone mirrors [SystemGenerator.fallsToNaiveShot]'s
     * framing — deviations around the direct launch->target line — but widened,
     * because a player who has learned to use gravity aims off-line on purpose.
     */
    private fun playerCone(spec: SystemSpec, shooter: Int, spreadDeg: Int): List<Death> {
        val target = spec.stations[1 - shooter]
        val from = spec.stations[shooter]
        val direct = StrictMath.toDegrees(
            StrictMath.atan2(target.y - from.y, target.x - from.x),
        )
        val centre = (((direct * 10).toInt() % 3600) + 3600) % 3600
        val out = ArrayList<Death>()
        var dev = -spreadDeg * 10
        while (dev <= spreadDeg * 10) {
            val angle = ((centre + dev) % 3600 + 3600) % 3600
            for (power in SystemGenerator.SOLVER_POWERS) {
                out.add(fateOf(spec, shooter, angle, power))
            }
            dev += SystemGenerator.SOLVER_ANGLE_STEP
        }
        return out
    }

    @Test
    fun measureWherePlayerMissesDie() {
        println("=== M1b: fate within a plausible player aim cone ===")
        for (spreadDeg in intArrayOf(15, 30, 60)) {
            for (difficulty in intArrayOf(0, 6, 11)) {
                val counts = HashMap<Fate, Int>()
                var onBoardDeaths = 0
                var total = 0
                repeat(4) { i ->
                    val spec = SystemGenerator.generate(
                        Seeds.campaign(difficulty, i), difficulty, GameMode.CAMPAIGN,
                    )
                    for (d in playerCone(spec, 0, spreadDeg)) {
                        counts[d.fate] = (counts[d.fate] ?: 0) + 1
                        total++
                        if (d.fate != Fate.TARGET && onBoard(d)) onBoardDeaths++
                    }
                }
                val pct = { f: Fate -> "%.1f%%".format(100.0 * (counts[f] ?: 0) / total) }
                println(
                    "spread=+/-${spreadDeg}deg d=$difficulty n=$total  target=${pct(Fate.TARGET)}  " +
                        "body=${pct(Fate.BODY)}  ownStation=${pct(Fate.OWN_STATION)}  " +
                        "out=${pct(Fate.OUT_OF_BOUNDS)}  timeout=${pct(Fate.TIMEOUT)}  |  " +
                        "MISSES DYING ON BOARD: %.1f%%".format(100.0 * onBoardDeaths / total),
                )
            }
        }
    }

    /**
     * Which body a shot died on, or -1. Needed because accretion has to grow
     * *the body that was hit*, and [ShotResult] does not carry the index.
     */
    private fun bodyHitIndex(spec: SystemSpec, shooter: Int, angle: Int, power: Int): Int {
        val shot = LiveShot(
            spec.bodies, spec.stations, shooter, angle, power,
            ProjectileType.STANDARD.id, maxTicks = Tuning.SOLVER_MAX_TICKS,
        )
        while (shot.result == null) shot.step()
        if (shot.result!!.stationHits.isNotEmpty()) return -1
        val impact = shot.lastImpact ?: return -1
        // The impact point sits on the surface of whichever body was struck.
        var best = -1
        var bestD2 = Double.MAX_VALUE
        val t = shot.result!!.ticks
        for ((i, b) in spec.bodies.withIndex()) {
            if (b.kind == BodyKind.REPULSOR) continue
            val dx = b.xAt(t) - impact.x
            val dy = b.yAt(t) - impact.y
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) { bestD2 = d2; best = i }
        }
        return best
    }

    /** Area-conserving accretion: the shell's cross-section is added to the body. */
    private fun accrete(b: Body, shellRadius: Double): Body =
        b.copy(radius = Math.sqrt(b.radius * b.radius + shellRadius * shellRadius))

    @Test
    fun measureAccretionAgainstSolvability() {
        println("=== M2: does accretion strand the player? ===")
        println("model: a miss that dies on body i grows body i by area-conserving accretion")
        val shell = ProjectileType.STANDARD.radius
        for (difficulty in intArrayOf(0, 6, 11)) {
            repeat(3) { i ->
                val spec0 = SystemGenerator.generate(
                    Seeds.campaign(difficulty, i), difficulty, GameMode.CAMPAIGN,
                )
                val base = SystemGenerator.solveRate(spec0, 0)
                // Replay a plausible sequence of misses, accreting as we go, and
                // re-measure solvability after each. 30 turns is the match cap.
                var spec = spec0
                val rates = ArrayList<String>()
                var applied = 0
                val target = spec.stations[1]
                val from = spec.stations[0]
                val direct = StrictMath.toDegrees(
                    StrictMath.atan2(target.y - from.y, target.x - from.x),
                )
                val centre = (((direct * 10).toInt() % 3600) + 3600) % 3600
                val rng = Rng(Seeds.campaign(difficulty, i))
                while (applied < 16) {
                    val angle = ((centre + rng.nextInt(601) - 300) % 3600 + 3600) % 3600
                    val power = SystemGenerator.SOLVER_POWERS[rng.nextInt(SystemGenerator.SOLVER_POWERS.size)]
                    val hit = bodyHitIndex(spec, 0, angle, power)
                    if (hit < 0) continue
                    val grown = spec.bodies.toMutableList()
                    grown[hit] = accrete(grown[hit], shell)
                    spec = spec.copy(bodies = grown)
                    applied++
                    if (applied == 1 || applied == 2 || applied == 4 || applied == 8 || applied == 16) {
                        rates.add("$applied:%.4f".format(SystemGenerator.solveRate(spec, 0)))
                    }
                }
                val maxR = spec.bodies.maxOf { it.radius }
                val maxR0 = spec0.bodies.maxOf { it.radius }
                println(
                    "d=$difficulty seed$i base=%.4f".format(base) +
                        "  after ${rates.joinToString(" ")}" +
                        "  maxRadius %.1f->%.1f".format(maxR0, maxR),
                )
            }
        }
    }

    /**
     * The impact point itself, which is where a free-standing debris lump would
     * sit: on the struck body's surface, on the side the shot came from.
     */
    private fun impactPoint(spec: SystemSpec, shooter: Int, angle: Int, power: Int): Vec2? {
        val shot = LiveShot(
            spec.bodies, spec.stations, shooter, angle, power,
            ProjectileType.STANDARD.id, maxTicks = Tuning.SOLVER_MAX_TICKS,
        )
        while (shot.result == null) shot.step()
        if (shot.result!!.stationHits.isNotEmpty()) return null
        return shot.lastImpact
    }

    @Test
    fun measureTangentDebrisAgainstSolvability() {
        println("=== M3: debris as a free-standing lump at the impact point ===")
        println("isotropic growth was invisible (M2); a tangent lump is directional")
        for (debrisRadius in doubleArrayOf(6.0, 12.0, 20.0)) {
            for (difficulty in intArrayOf(0, 6, 11)) {
                repeat(3) { i ->
                    val spec0 = SystemGenerator.generate(
                        Seeds.campaign(difficulty, i), difficulty, GameMode.CAMPAIGN,
                    )
                    val base = SystemGenerator.solveRate(spec0, 0)
                    var spec = spec0
                    val rates = ArrayList<String>()
                    var applied = 0
                    val target = spec.stations[1]
                    val from = spec.stations[0]
                    val direct = StrictMath.toDegrees(
                        StrictMath.atan2(target.y - from.y, target.x - from.x),
                    )
                    val centre = (((direct * 10).toInt() % 3600) + 3600) % 3600
                    val rng = Rng(Seeds.campaign(difficulty, i))
                    var guard = 0
                    while (applied < 12 && guard++ < 400) {
                        val angle = ((centre + rng.nextInt(601) - 300) % 3600 + 3600) % 3600
                        val power = SystemGenerator.SOLVER_POWERS[
                            rng.nextInt(SystemGenerator.SOLVER_POWERS.size),
                        ]
                        val at = impactPoint(spec, 0, angle, power) ?: continue
                        // Mass 0: a pure obstacle. Whether debris should also pull
                        // is a separate question, deliberately not conflated here.
                        val lump = Body(
                            x = at.x, y = at.y, radius = debrisRadius, mass = 0.0,
                            kind = BodyKind.PLANET, palette = 0,
                        )
                        spec = spec.copy(bodies = spec.bodies + lump)
                        applied++
                        if (applied == 1 || applied == 3 || applied == 6 || applied == 12) {
                            rates.add("$applied:%.4f".format(SystemGenerator.solveRate(spec, 0)))
                        }
                    }
                    println(
                        "r=$debrisRadius d=$difficulty seed$i base=%.4f".format(base) +
                            "  after ${rates.joinToString(" ")}",
                    )
                }
            }
        }
    }

    /**
     * The question M3 leaves open: a 30-turn match missing ~60% on-board makes
     * roughly 18 lumps, so the tail matters more than the first few. If any
     * configuration drives the solve rate to zero, that is a stranded player
     * and the rule is not shippable at that size (D-24).
     */
    @Test
    fun measureStrandingAtFullMatchLength() {
        println("=== M4: worst-case litter, does anything strand? ===")
        var worstRatio = 1.0
        var worstLabel = ""
        var anyZero = false
        for (debrisRadius in doubleArrayOf(12.0, 20.0)) {
            for (difficulty in intArrayOf(0, 6, 11)) {
                repeat(3) { i ->
                    val spec0 = SystemGenerator.generate(
                        Seeds.campaign(difficulty, i), difficulty, GameMode.CAMPAIGN,
                    )
                    val base = SystemGenerator.solveRate(spec0, 0)
                    var spec = spec0
                    var applied = 0
                    val target = spec.stations[1]
                    val from = spec.stations[0]
                    val direct = StrictMath.toDegrees(
                        StrictMath.atan2(target.y - from.y, target.x - from.x),
                    )
                    val centre = (((direct * 10).toInt() % 3600) + 3600) % 3600
                    val rng = Rng(Seeds.campaign(difficulty, i))
                    var guard = 0
                    // 30 turns is the match cap, so 30 lumps is strictly worse
                    // than any real match can produce.
                    while (applied < 30 && guard++ < 800) {
                        val angle = ((centre + rng.nextInt(601) - 300) % 3600 + 3600) % 3600
                        val power = SystemGenerator.SOLVER_POWERS[
                            rng.nextInt(SystemGenerator.SOLVER_POWERS.size),
                        ]
                        val at = impactPoint(spec, 0, angle, power) ?: continue
                        spec = spec.copy(
                            bodies = spec.bodies + Body(
                                x = at.x, y = at.y, radius = debrisRadius, mass = 0.0,
                                kind = BodyKind.PLANET, palette = 0,
                            ),
                        )
                        applied++
                    }
                    val end = SystemGenerator.solveRate(spec, 0)
                    val ratio = if (base > 0) end / base else 1.0
                    if (end <= 0.0) anyZero = true
                    if (ratio < worstRatio) {
                        worstRatio = ratio
                        worstLabel = "r=$debrisRadius d=$difficulty seed$i"
                    }
                    println(
                        "r=$debrisRadius d=$difficulty seed$i  lumps=$applied  " +
                            "base=%.4f end=%.4f  retained %.0f%%".format(base, end, 100.0 * ratio),
                    )
                }
            }
        }
        println("WORST: $worstLabel retained %.0f%% of winning shots".format(100.0 * worstRatio))
        println("ANY STRANDED (solve rate hit 0): $anyZero")
        // The load-bearing safety property. If a future change to debris size,
        // body size or the difficulty bands makes a littered board unwinnable,
        // this must fail rather than ship a dead end (D-24, D-27).
        assertFalse(
            "debris drove a level's solve rate to zero: the player would be stranded",
            anyZero,
        )
    }

    /**
     * The gap M4 left. M4 measured whether a littered board still has a shot
     * that *hits the enemy station* — but three of the four objectives are not
     * won that way, and they are the fragile ones: a wreck can fence a capture
     * ring or sit across a thread gate. D-27 forbids setting a task the arena
     * cannot pay, and an objective proved winnable on a clean board is not
     * proved winnable on a littered one.
     *
     * Objectives are campaign-only, and a campaign match is
     * [Tuning.CAMPAIGN_SHOTS] shots, so at most 3 wrecks can exist before the
     * last shot. That is the real envelope; the higher counts are stress.
     */
    @Test
    fun measureObjectiveSurvivalUnderLitter() {
        println("=== M5: can a littered board still pay its objective? ===")
        println("campaign is ${Tuning.CAMPAIGN_SHOTS} shots, so <=${Tuning.CAMPAIGN_SHOTS - 1} wrecks are reachable in play")
        val found = HashMap<Objective, Int>()
        var checked = 0
        var lost = 0
        for (difficulty in intArrayOf(2, 4, 6, 8, 11)) {
            for (level in 0 until 24) {
                val spec = SystemGenerator.generate(
                    Seeds.campaign(difficulty, level), difficulty, GameMode.CAMPAIGN,
                )
                if (spec.objective == Objective.BREACH) continue
                found[spec.objective] = (found[spec.objective] ?: 0) + 1

                // Litter the board the way play would: wrecks at the impact
                // points of real missed shots, not at convenient places.
                var littered = spec
                var applied = 0
                val target = spec.stations[1]
                val from = spec.stations[0]
                val direct = StrictMath.toDegrees(
                    StrictMath.atan2(target.y - from.y, target.x - from.x),
                )
                val centre = (((direct * 10).toInt() % 3600) + 3600) % 3600
                val rng = Rng(Seeds.campaign(difficulty, level))
                var guard = 0
                while (applied < Tuning.CAMPAIGN_SHOTS - 1 && guard++ < 300) {
                    val angle = ((centre + rng.nextInt(601) - 300) % 3600 + 3600) % 3600
                    val power = SystemGenerator.SOLVER_POWERS[
                        rng.nextInt(SystemGenerator.SOLVER_POWERS.size),
                    ]
                    val at = impactPoint(littered, 0, angle, power) ?: continue
                    littered = littered.copy(
                        bodies = littered.bodies + Body(
                            x = at.x, y = at.y, radius = Tuning.DEBRIS_RADIUS, mass = 0.0,
                            kind = BodyKind.DEBRIS, palette = 0,
                        ),
                    )
                    applied++
                }
                if (applied == 0) continue
                checked++

                val stillPayable = when (spec.objective) {
                    Objective.THREAD -> SystemGenerator.proveThread(littered)
                    Objective.COURIER -> SystemGenerator.proveCourier(littered, littered.markedBody)
                    Objective.CAPTURE -> SystemGenerator.proveCapture(littered, littered.markedBody)
                    Objective.BREACH -> true
                }
                if (!stillPayable) {
                    lost++
                    println(
                        "  LOST: ${spec.objective} d=$difficulty level=$level " +
                            "became unprovable after $applied wrecks",
                    )
                }
            }
        }
        println("objectives sampled: $found")
        println("littered and re-proved: $checked   became unwinnable: $lost")
        // D-27's bar, applied to Stage F. An objective that a few wrecks can
        // make impossible is a dead end the player walked into by missing.
        assertEquals(
            "litter made an objective unwinnable; debris cannot ship into objective systems as-is",
            0, lost,
        )
    }

    @Test
    fun measureWhereMissesDie() {
        println("=== M1: fate of every sample shot, by difficulty ===")
        println("world is ${Tuning.WORLD_W.toInt()} x ${Tuning.WORLD_H.toInt()}, out-margin ${Tuning.OUT_MARGIN}")
        var nonBodyOnBoard = 0
        var timeouts = 0
        var ownStation = 0
        for (difficulty in intArrayOf(0, 3, 6, 9, 11)) {
            val counts = HashMap<Fate, Int>()
            var onBoardDeaths = 0
            var total = 0
            repeat(4) { i ->
                val spec = SystemGenerator.generate(
                    Seeds.campaign(difficulty, i), difficulty, GameMode.CAMPAIGN,
                )
                for (d in sweep(spec, 0)) {
                    counts[d.fate] = (counts[d.fate] ?: 0) + 1
                    total++
                    if (d.fate == Fate.TIMEOUT) timeouts++
                    if (d.fate == Fate.OWN_STATION) ownStation++
                    if (d.fate != Fate.TARGET && onBoard(d)) {
                        onBoardDeaths++
                        if (d.fate != Fate.BODY) nonBodyOnBoard++
                    }
                }
            }
            val n = { f: Fate -> counts[f] ?: 0 }
            println(
                "d=$difficulty n=$total  target=${n(Fate.TARGET)}  body=${n(Fate.BODY)}  " +
                    "ownStation=${n(Fate.OWN_STATION)}  out=${n(Fate.OUT_OF_BOUNDS)}  " +
                    "timeout=${n(Fate.TIMEOUT)}  |  MISSES DYING ON BOARD: $onBoardDeaths " +
                    "(%.1f%%)".format(100.0 * onBoardDeaths / total),
            )
        }
        // Raw counts, not percentages: a rounded "0.0%" column is not zero, and
        // reading it as zero is how a structural claim gets made on a display
        // artifact rather than on the data.
        println(
            "NON-BODY ON-BOARD DEATHS: $nonBodyOnBoard  (timeouts=$timeouts ownStation=$ownStation)",
        )
        // The property Stage F's placement rule actually depends on: a shot is
        // never still alive and on the board when the clock runs out, so debris
        // can never be planted in OPEN SPACE — only against something already
        // solid. Own-station suicide is a real but vanishingly rare on-board
        // death (measured 1 in 18,000 grid samples), and it is excluded by
        // spawning debris on body impact only; a lump welded to your own launch
        // point would be a self-inflicted dead end.
        assertEquals(
            "a shot survived to the tick cap while still on the board, so debris could land in open space",
            0, timeouts,
        )
    }
}
