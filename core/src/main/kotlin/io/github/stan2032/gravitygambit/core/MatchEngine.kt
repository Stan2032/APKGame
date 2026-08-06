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

    /** CAMPAIGN: did the player breach the target core? */
    val campaignWon: Boolean get() = hp[1] <= 0

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
        val winning = results.lastOrNull { r -> r.stationHits.any { it == 1 } }
        val workedTheField = winning != null &&
            (winning.slingshotGain >= Tuning.THREE_STAR_MIN_GAIN || winning.styleBonus > 0)
        return when {
            shotsUsed == 1 && workedTheField -> 3
            shotsUsed <= 2 -> 2
            else -> 1
        }
    }

    fun beginShot(
        angleDeci: Int,
        power: Int,
        typeId: Int,
        presetBrakeTick: Int = -1,
        anchors: List<Anchor> = emptyList(),
    ): LiveShot = LiveShot(
        spec.bodies, stations, currentShooter, angleDeci, power, typeId,
        presetBrakeTick, Tuning.MAX_TICKS, anchors,
    )

    /** Apply a finished shot to match state. Must be called exactly once per shot. */
    fun commit(result: ShotResult) {
        val input = result.input
        turns.add(input)
        foldHash(result.hash)

        val type = ProjectileType.byId(input.typeId)
        when (mode) {
            GameMode.CAMPAIGN -> {
                if (result.stationHits.any { it == 1 }) hp[1] = 0
            }
            GameMode.DAILY -> {
                val hits = result.stationHits.count { it == 1 }
                if (hits > 0) {
                    dailyHits += hits
                    score += hits * Tuning.DAILY_HIT_SCORE + result.styleBonus
                    relocateDailyTarget()
                }
            }
            GameMode.DUEL -> {
                for (si in result.stationHits) {
                    hp[si] = (hp[si] - type.damage).coerceAtLeast(0)
                }
            }
        }
        results.add(result)
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
            val shot = engine.beginShot(t.angleDeci, t.power, t.typeId, t.brakeTick, t.anchors)
            engine.commit(shot.runToCompletion())
        }
        return Rebuilt(engine, engine.combinedHash)
    }
}
