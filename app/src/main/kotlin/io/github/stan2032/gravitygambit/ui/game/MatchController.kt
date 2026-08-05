package io.github.stan2032.gravitygambit.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.stan2032.gravitygambit.core.Anchor
import io.github.stan2032.gravitygambit.core.GameMode
import io.github.stan2032.gravitygambit.core.LiveShot
import io.github.stan2032.gravitygambit.core.MatchEngine
import io.github.stan2032.gravitygambit.core.MatchRecord
import io.github.stan2032.gravitygambit.core.MatchReplayer
import io.github.stan2032.gravitygambit.core.ProjectileType
import io.github.stan2032.gravitygambit.core.Rng
import io.github.stan2032.gravitygambit.core.Seeds
import io.github.stan2032.gravitygambit.core.ShareCodec
import io.github.stan2032.gravitygambit.core.ShotResult
import io.github.stan2032.gravitygambit.core.SystemGenerator
import io.github.stan2032.gravitygambit.core.SystemSpec
import io.github.stan2032.gravitygambit.core.Tuning
import io.github.stan2032.gravitygambit.core.TurnInput
import io.github.stan2032.gravitygambit.core.Vec2
import io.github.stan2032.gravitygambit.ui.GameLaunch
import io.github.stan2032.gravitygambit.ui.ShareMessages
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class Phase { AIMING, FLYING, RESOLVING, HANDOFF, OVER }

class Explosion(val at: Vec2, val big: Boolean, seed: Long) {
    class Particle(val dx: Double, val dy: Double, val speed: Double, val hue: Int)

    val particles: List<Particle> = buildList {
        val rng = Rng(seed)
        repeat(if (big) 30 else 16) {
            val ang = rng.range(0.0, 2 * Math.PI)
            add(
                Particle(
                    StrictMath.cos(ang), StrictMath.sin(ang),
                    rng.range(40.0, 170.0), rng.nextInt(3),
                )
            )
        }
    }
}

class Summary(
    val win: Boolean,
    val title: String,
    val subtitle: String,
    val stars: Int,
    val score: Int,
    val shareText: String?,
    val duelCode: String?,
)

/**
 * Presentation-side match state machine. All rules live in core [MatchEngine];
 * this class owns pacing (live simulation stepped per frame), aim state,
 * replay playback of an opponent's incoming turns, and end-of-match summaries.
 */
class MatchController private constructor(
    val launch: GameLaunch,
    val spec: SystemSpec,
    val engine: MatchEngine,
    val localPlayer: Int,
    pendingReplays: List<TurnInput>,
) {
    var phase by mutableStateOf(Phase.AIMING); private set
    var aimAngleDeci by mutableStateOf(2700)   // screen-up in y-down coordinates
    var aimPower by mutableStateOf(550)
    var selectedType by mutableStateOf(ProjectileType.STANDARD.id)
    var aiming by mutableStateOf(false)

    /**
     * Player-placed masses for the shot being aimed (docs/VISION.md §3.1).
     * This is the mechanic that turns the player from a solver of the field
     * into its author, so it lives in aim state and resets per shot.
     */
    val anchors = mutableStateListOf<Anchor>()
    var placingAnchors by mutableStateOf(false)

    /**
     * Tap handling for anchor mode: an empty spot places a new attracting
     * anchor; tapping an existing one cycles attract -> repel -> gone. One
     * gesture, no modal buttons, and every state reachable in three taps.
     */
    fun tapAnchor(at: Vec2) {
        val hitIndex = anchors.indexOfFirst { a ->
            val dx = a.x - at.x
            val dy = a.y - at.y
            dx * dx + dy * dy < ANCHOR_TAP_RADIUS * ANCHOR_TAP_RADIUS
        }
        if (hitIndex >= 0) {
            val a = anchors[hitIndex]
            if (a.attract) anchors[hitIndex] = a.copy(attract = false) else anchors.removeAt(hitIndex)
            return
        }
        if (anchors.size >= Tuning.MAX_ANCHORS) return
        if (!isAnchorSpotLegal(at)) return
        anchors.add(Anchor(at.x.roundToInt(), at.y.roundToInt(), attract = true))
    }

    /** Anchors may not be dropped on top of a body or a station. */
    fun isAnchorSpotLegal(at: Vec2): Boolean {
        if (at.x < 0 || at.y < 0 || at.x > Tuning.WORLD_W || at.y > Tuning.WORLD_H) return false
        for (b in spec.bodies) {
            val dx = b.xAt(0) - at.x
            val dy = b.yAt(0) - at.y
            val min = b.radius + Tuning.ANCHOR_MIN_CLEARANCE
            if (dx * dx + dy * dy < min * min) return false
        }
        for (s in engine.stations) {
            val dx = s.x - at.x
            val dy = s.y - at.y
            val min = Tuning.STATION_RADIUS + Tuning.ANCHOR_MIN_CLEARANCE
            if (dx * dx + dy * dy < min * min) return false
        }
        return true
    }

    fun clearAnchors() = anchors.clear()

    var liveShot: LiveShot? = null; private set
    var isReplaying by mutableStateOf(false); private set
    var brakeUsed by mutableStateOf(false); private set

    /**
     * Trail is a plain deque + a version counter, NOT snapshot state: appending
     * ~120 points/sec to a SnapshotStateList churns snapshot records. Renderers
     * read [trailVersion] (draw-phase invalidation) then iterate [trail] directly;
     * both mutation and draw happen on the main thread.
     */
    val trail = ArrayDeque<Vec2>()
    var trailVersion by mutableStateOf(0); private set

    var explosion by mutableStateOf<Explosion?>(null); private set
    var explosionProgress by mutableStateOf(0f)
    var lastResult by mutableStateOf<ShotResult?>(null); private set
    var hpTick by mutableStateOf(0); private set

    var handoffText by mutableStateOf<String?>(null); private set
    var duelCode by mutableStateOf<String?>(null); private set
    var summary by mutableStateOf<Summary?>(null); private set

    var rewardsApplied = false
    var dustEarned by mutableStateOf(0)

    private val replayQueue = ArrayDeque(pendingReplays)

    init {
        if (replayQueue.isNotEmpty()) {
            startReplay(replayQueue.removeFirst())
        }
    }

    val isMyTurn: Boolean
        get() = launch.mode != GameMode.DUEL || launch.hotseat || engine.currentShooter == localPlayer

    // ---- Aiming ----

    fun setAimFromDrag(worldPos: Vec2) {
        val station = engine.stations[engine.currentShooter]
        val dx = station.x - worldPos.x
        val dy = station.y - worldPos.y
        val len = sqrt(dx * dx + dy * dy)
        if (len < 14.0) return
        val deg = Math.toDegrees(atan2(dy, dx))
        aimAngleDeci = (((deg * 10).roundToInt() % 3600) + 3600) % 3600
        aimPower = (((len - 30.0) / 430.0) * 1000).roundToInt().coerceIn(0, 1000)
    }

    fun nudgeAngle(deltaDeci: Int) {
        aimAngleDeci = ((aimAngleDeci + deltaDeci) % 3600 + 3600) % 3600
    }

    fun nudgePower(delta: Int) {
        aimPower = (aimPower + delta).coerceIn(0, 1000)
    }

    // ---- Firing & simulation pacing ----

    fun fire() {
        if (phase != Phase.AIMING) return
        isReplaying = false
        brakeUsed = false
        liveShot = engine.beginShot(aimAngleDeci, aimPower, selectedType, -1, anchors.toList())
        trail.clear(); trailVersion++
        placingAnchors = false
        phase = Phase.FLYING
    }

    private fun startReplay(turn: TurnInput) {
        isReplaying = true
        brakeUsed = true // replayed shots brake themselves via presetBrakeTick
        liveShot = engine.beginShot(
            turn.angleDeci, turn.power, turn.typeId, turn.brakeTick, turn.anchors,
        )
        trail.clear(); trailVersion++
        phase = Phase.FLYING
    }

    fun brakeNow() {
        val shot = liveShot ?: return
        if (!isReplaying && shot.brakeNow()) brakeUsed = true
    }

    /**
     * Advance the live simulation by [ticks]. The caller derives tick count
     * from wall-clock time (PLAYBACK_TICKS_PER_SEC), NOT from frame count —
     * otherwise playback speed would scale with display refresh rate
     * (2x too fast on 120Hz panels).
     */
    fun advanceBy(ticks: Int) {
        val shot = liveShot ?: return
        repeat(ticks) {
            if (!shot.done) {
                shot.step()
                shot.visitProjectiles { _, x, y, _ -> trail.addLast(Vec2(x, y)) }
            }
        }
        while (trail.size > TRAIL_CAP) trail.removeFirst()
        trailVersion++
        if (shot.done) resolve(shot)
    }

    private fun resolve(shot: LiveShot) {
        val res = shot.result ?: return
        engine.commit(res)
        hpTick++
        lastResult = res
        val impact = shot.lastImpact
        if (impact != null) {
            explosion = Explosion(
                impact,
                big = shot.lastImpactWasStation,
                seed = Rng.mix(spec.seed, engine.turns.size.toLong()),
            )
        }
        explosionProgress = 0f
        phase = Phase.RESOLVING
    }

    /** Called by the UI when the explosion animation completes. */
    fun postResolve() {
        explosion = null
        liveShot = null
        if (engine.over) {
            finishMatch()
            return
        }
        if (replayQueue.isNotEmpty()) {
            startReplay(replayQueue.removeFirst())
            return
        }
        when (launch.mode) {
            GameMode.DUEL -> when {
                launch.hotseat -> {
                    resetAimFor(engine.currentShooter)
                    handoffText = "Player ${engine.currentShooter + 1} — take the phone"
                    phase = Phase.HANDOFF
                }
                engine.currentShooter == localPlayer -> {
                    resetAimFor(localPlayer)
                    phase = Phase.AIMING
                }
                else -> {
                    duelCode = buildCode()
                    handoffText = null
                    phase = Phase.HANDOFF
                }
            }
            else -> {
                // Anchors are consumed by the shot they shaped.
                anchors.clear()
                phase = Phase.AIMING // keep last aim: campaign/daily iterate on it
            }
        }
    }

    /** Duel turns flip sides; point the new shooter roughly at the enemy. */
    private fun resetAimFor(shooter: Int) {
        anchors.clear()
        aimAngleDeci = if (shooter == 0) 2700 else 900
        aimPower = 550
    }

    fun continueFromHandoff() {
        if (phase != Phase.HANDOFF) return
        if (launch.hotseat) {
            handoffText = null
            phase = Phase.AIMING
        }
    }

    // ---- Codes & summaries ----

    /** Replay code of the match so far — attached to in-app feedback reports. */
    fun feedbackCode(): String = buildCode()

    private fun buildCode(): String = ShareCodec.encode(
        MatchRecord(
            mode = launch.mode.id,
            difficulty = spec.difficulty,
            seed = spec.seed,
            turns = engine.turns.toList(),
            hash = engine.combinedHash,
        )
    )

    private fun finishMatch() {
        summary = when (launch.mode) {
            GameMode.CAMPAIGN -> {
                val won = engine.campaignWon
                Summary(
                    win = won,
                    title = if (won) "Core breached!" else "Out of shots",
                    subtitle = if (won) "${spec.name} cleared in ${engine.shotsUsed} shot${if (engine.shotsUsed == 1) "" else "s"}"
                    else "The core survives. Study the field and try again.",
                    stars = engine.starsEarned(),
                    score = 0,
                    shareText = null,
                    duelCode = null,
                )
            }
            GameMode.DAILY -> {
                val n = Seeds.dailyNumber(launch.epochDay)
                val code = buildCode()
                Summary(
                    win = engine.score > 0,
                    title = "Daily #$n complete",
                    subtitle = "${engine.dailyHits} hits · ${engine.score} points",
                    stars = 0,
                    score = engine.score,
                    shareText = ShareMessages.dailyResult(n, engine.score, engine.shotsUsed) + "\n\n$code",
                    duelCode = null,
                )
            }
            GameMode.DUEL -> {
                val winner = engine.duelWinner()
                val won = winner != null && (launch.hotseat || winner == localPlayer)
                val title = when {
                    winner == null -> "Stalemate"
                    launch.hotseat -> "Player ${winner + 1} wins!"
                    winner == localPlayer -> "Victory!"
                    else -> "Defeat"
                }
                val code = if (!launch.hotseat) buildCode() else null
                Summary(
                    win = won,
                    title = title,
                    subtitle = "Station integrity — P1: ${engine.hp[0]} · P2: ${engine.hp[1]}",
                    stars = 0,
                    score = 0,
                    shareText = null,
                    duelCode = code,
                )
            }
        }
        phase = Phase.OVER
    }

    companion object {
        /** Sim ticks consumed per wall-clock second during playback (2x real time). */
        const val PLAYBACK_TICKS_PER_SEC = 120
        const val MAX_TICKS_PER_FRAME = 8   // catch-up clamp after jank/pauses
        const val TRAIL_CAP = 600
        /** World-space tap tolerance for grabbing an existing anchor. */
        const val ANCHOR_TAP_RADIUS = 46.0

        /**
         * Heavy: generates (or verifies + rebuilds) the star system. Run on a
         * background dispatcher. Returns null for an invalid/tampered code.
         */
        fun create(launch: GameLaunch): MatchController? {
            return if (launch.importedCode != null) {
                val verified = ShareCodec.decodeAndVerify(launch.importedCode) ?: return null
                val record = verified.record
                if (record.mode != GameMode.DUEL.id) return null
                val local = record.turns.size % 2
                val played = record.turns.dropLast(1)
                val engine = MatchReplayer.rebuild(verified.spec, played, GameMode.DUEL).engine
                MatchController(
                    launch = launch,
                    spec = verified.spec,
                    engine = engine,
                    localPlayer = local,
                    pendingReplays = record.turns.takeLast(1),
                )
            } else {
                val spec = SystemGenerator.generate(launch.seed, launch.difficulty, launch.mode)
                MatchController(
                    launch = launch,
                    spec = spec,
                    engine = MatchEngine(spec, launch.mode),
                    localPlayer = 0,
                    pendingReplays = emptyList(),
                )
            }
        }
    }
}
