package io.github.stan2032.gravitygambit.core

/**
 * Owns all match rules: turn order, HP, scoring, daily target relocation, and the
 * combined verification hash. Both live play (app UI) and replay verification
 * ([MatchReplayer]) run through this exact class, so a replay can never diverge
 * from what the original player saw.
 */
class MatchEngine(val spec: SystemSpec, val mode: GameMode) {

    val hp: IntArray = when (mode) {
        GameMode.DUEL -> intArrayOf(Tuning.STATION_HP, Tuning.STATION_HP)
        else -> intArrayOf(Tuning.STATION_HP, 1)
    }

    /** Mutable copy: the daily gauntlet relocates the target after each hit. */
    val stations: MutableList<Station> = spec.stations.toMutableList()

    val turns = ArrayList<TurnInput>()
    val results = ArrayList<ShotResult>()

    /**
     * Wrecks left by shots that died on a world, in the order they were made
     * (VISION §3.3, D-39). This is the one piece of board state the *generator*
     * did not author — it accumulates from play, so it lives on the match
     * rather than on [SystemSpec].
     *
     * It costs the share code **nothing**. Debris is a pure function of turns
     * already in the payload, and [MatchReplayer] drives the same
     * [beginShot]/[commit] path, so a verifier re-derives identical wrecks
     * without a byte being sent. What it does cost is a protocol version: the
     * same (seed, turns) now produces a different board, so pre-v9 codes mean
     * something else and are refused.
     */
    val debris = ArrayList<Body>()

    /**
     * Whether each shot honoured its declaration, parallel to [results]. Shots
     * with no declaration are always true. Kept here rather than on ShotResult
     * because it is a *rule*, and [LiveShot] only measures.
     */
    val gambitKept = ArrayList<Boolean>()

    var score = 0; private set          // daily mode
    var dailyHits = 0; private set
    var combinedHash: Long = LiveShot.FNV_OFFSET; private set

    val shotsUsed: Int get() = turns.size

    val currentShooter: Int
        get() = if (mode == GameMode.DUEL) turns.size % 2 else 0

    val over: Boolean
        get() = when (mode) {
            GameMode.CAMPAIGN -> hp[1] <= 0 || shotsUsed >= Tuning.CAMPAIGN_SHOTS
            GameMode.DAILY -> shotsUsed >= Tuning.DAILY_SHOTS
            GameMode.DUEL -> hp[0] <= 0 || hp[1] <= 0 || shotsUsed >= Tuning.DUEL_MAX_TURNS
        }

    /** True once the system's objective has been satisfied. */
    var objectiveMet = false; private set

    /** CAMPAIGN: has the system's objective been achieved? */
    val campaignWon: Boolean
        get() = when (spec.objective) {
            Objective.BREACH -> hp[1] <= 0
            Objective.CAPTURE, Objective.COURIER, Objective.THREAD -> objectiveMet
        }

    /** DUEL: winning player index, or null (draw / not over). Shooter survives ties. */
    fun duelWinner(): Int? {
        if (!over) return null
        val lastShooter = (turns.size - 1).coerceAtLeast(0) % 2
        val opp = 1 - lastShooter
        return when {
            hp[opp] <= 0 -> lastShooter
            hp[lastShooter] <= 0 -> opp
            else -> null
        }
    }

    /**
     * Stars reward *how* the level fell, not just how fast.
     *
     * Shot count alone gave three stars for a lucky first shot, which is how a
     * player ended up with a perfect score for a straight lob that used no
     * gravity at all. The third star now additionally requires the winning
     * shot to have actually worked the field — stolen speed from it, curved
     * around it, or threaded a comet — so the top rating means "you played the
     * game", not "you got there quickly".
     */
    fun starsEarned(): Int {
        if (!campaignWon) return 0
        // The shot that actually breached: a hit inside a broken declaration
        // did not land, so it cannot be the winning shot either.
        val winIndex = results.indices.lastOrNull { i ->
            gambitKept[i] && when (spec.objective) {
                Objective.BREACH -> results[i].stationHits.any { it == 1 }
                else -> objectiveSatisfiedBy(results[i])
            }
        }
        val winning = winIndex?.let { results[it] }
        val declared = winning != null && winning.input.gambitId != Gambit.NONE.id
        val workedTheField = winning != null && (
            // Neither holding a ring for four seconds nor leading a drifting
            // comet happens by accident.
            spec.objective != Objective.BREACH ||
                declared ||
                winning.slingshotGain >= Tuning.THREE_STAR_MIN_GAIN ||
                winning.styleBonus > 0
            )
        return when {
            shotsUsed == 1 && workedTheField -> 3
            shotsUsed <= 2 -> 2
            else -> 1
        }
    }

    /** Has this shot achieved what the system asks? Rules, not measurement. */
    fun objectiveSatisfiedBy(r: ShotResult): Boolean = when (spec.objective) {
        Objective.BREACH -> r.stationHits.any { it == 1 }
        Objective.CAPTURE -> r.captureDwell >= Tuning.CAPTURE_TICKS
        Objective.COURIER -> r.markedBodyHit
        Objective.THREAD -> spec.gates.isNotEmpty() && r.gatesPassed >= spec.gates.size
    }

    fun beginShot(
        angleDeci: Int,
        power: Int,
        typeId: Int,
        presetBrakeTick: Int = -1,
        anchors: List<Anchor> = emptyList(),
        gambitId: Int = 0,
    ): LiveShot = LiveShot(
        // Wrecks are appended, never inserted: markedBody and the gambit
        // bookkeeping index into this list, so existing indices must not move.
        if (debris.isEmpty()) spec.bodies else spec.bodies + debris,
        stations, currentShooter,
        angleDeci, power,
        // Capture is impossible without a brake, so the objective issues the
        // shell rather than assuming the player has unlocked it.
        if (spec.objective.forcesRetro) ProjectileType.RETRO.id else typeId,
        presetBrakeTick, Tuning.MAX_TICKS, anchors, gambitId, spec.markedBody, spec.gates,
    )

    /** Apply a finished shot to match state. Must be called exactly once per shot. */
    fun commit(result: ShotResult) {
        val input = result.input
        turns.add(input)
        foldHash(result.hash)

        // Consequence: a shot that died on a world leaves its wreck behind, and
        // every later shot this match has to fly around it (VISION §3.3).
        // Folded into the combined hash because it is match state that changes
        // what happens next — a verifier that derived different debris must not
        // be able to agree on the outcome by accident.
        result.wreckAt?.let { at ->
            debris.add(
                Body(
                    x = at.x, y = at.y,
                    radius = Tuning.DEBRIS_RADIUS,
                    mass = 0.0,
                    kind = BodyKind.DEBRIS,
                    palette = 0,
                ),
            )
            foldHash(StrictMath.round(at.x * 1000.0))
            foldHash(StrictMath.round(at.y * 1000.0))
        }

        // A declaration is a stake, not a bonus: break it and the shot is
        // wasted outright — the damage does not land. That is what makes
        // declaring a decision rather than free money (docs/VISION.md §3.2).
        val gambit = Gambit.byId(input.gambitId)
        val kept = gambit.satisfiedBy(result)
        gambitKept.add(kept)
        foldHash(if (kept) 1L else 0L)
        val hits = if (kept) result.stationHits else emptyList()

        // The objective can only be met by a shot that kept its declaration —
        // a wasted shot is wasted for every objective, not just for damage.
        if (kept && objectiveSatisfiedBy(result)) objectiveMet = true
        foldHash(if (objectiveMet) 1L else 0L)

        val type = ProjectileType.byId(input.typeId)
        when (mode) {
            GameMode.CAMPAIGN -> {
                if (hits.any { it == 1 }) hp[1] = 0
            }
            GameMode.DAILY -> {
                val n = hits.count { it == 1 }
                if (n > 0) {
                    dailyHits += n
                    score += n * Tuning.DAILY_HIT_SCORE + gambit.payout(result.styleBonus)
                    relocateDailyTarget()
                }
            }
            GameMode.DUEL -> {
                // Duels are decided by HP, and nothing else, so a declaration
                // has to pay in HP or it is pure downside — which is what it
                // was on the first pass: style points that a duel never reads.
                // A kept declaration multiplies damage; a broken one already
                // cost the whole turn above.
                val damage = gambit.payout(type.damage)
                for (si in hits) {
                    hp[si] = (hp[si] - damage).coerceAtLeast(0)
                }
            }
        }
        results.add(result)
    }

    /** Style points actually banked for a committed shot, after its declaration. */
    fun awardedStyle(index: Int): Int {
        val r = results[index]
        if (!gambitKept[index]) return 0
        return Gambit.byId(r.input.gambitId).payout(r.styleBonus)
    }

    private fun relocateDailyTarget() {
        stations[1] = DailyLogic.targetPosition(spec, dailyHits)
        foldHash(StrictMath.round(stations[1].x * 1000.0))
        foldHash(StrictMath.round(stations[1].y * 1000.0))
    }

    private fun foldHash(v: Long) {
        var h = combinedHash
        var x = v
        repeat(8) {
            h = (h xor (x and 0xFF)) * LiveShot.FNV_PRIME
            x = x ushr 8
        }
        combinedHash = h
    }
}

/** Deterministic target relocation for the daily score-attack gauntlet. */
object DailyLogic {
    fun targetPosition(spec: SystemSpec, hitCount: Int): Station {
        if (hitCount <= 0) return spec.stations[1]
        val rng = Rng(Rng.mix(spec.seed, 0xDA111EEDL + hitCount))
        repeat(80) {
            val x = rng.range(120.0, Tuning.WORLD_W - 120.0)
            val y = rng.range(110.0, Tuning.WORLD_H * 0.45)
            if (isClear(spec, x, y)) return Station(x, y, owner = 1)
        }
        return spec.stations[1]
    }

    private fun isClear(spec: SystemSpec, x: Double, y: Double): Boolean {
        for (b in spec.bodies) {
            val dx = b.x - x; val dy = b.y - y
            val min = b.radius + Tuning.STATION_RADIUS + 50.0
            if (dx * dx + dy * dy < min * min) return false
        }
        val p = spec.stations[0]
        val dx = p.x - x; val dy = p.y - y
        return dx * dx + dy * dy > 300.0 * 300.0
    }
}

/**
 * Rebuilds a full match from (spec, turns). Used to verify shared replays:
 * if the rebuilt combinedHash differs from the claimed hash, the code was
 * tampered with or corrupted — reject it.
 */
object MatchReplayer {
    class Rebuilt(
        val engine: MatchEngine,
        val verifiedHash: Long,
    )

    fun rebuild(spec: SystemSpec, turns: List<TurnInput>, mode: GameMode): Rebuilt {
        val engine = MatchEngine(spec, mode)
        for (t in turns) {
            if (engine.over) break
            val shot = engine.beginShot(
                t.angleDeci, t.power, t.typeId, t.brakeTick, t.anchors, t.gambitId,
            )
            engine.commit(shot.runToCompletion())
        }
        return Rebuilt(engine, engine.combinedHash)
    }
}
