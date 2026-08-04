package io.github.stan2032.gravitygambit.core

/**
 * Deterministic shot simulation.
 *
 * Determinism contract (see docs/DETERMINISM.md):
 *  - All state advances with IEEE-754 double +, -, *, / and Math.sqrt, which are
 *    exactly specified and bit-identical on every conforming JVM/ART device.
 *  - Any transcendental function (cos/sin for launch angles and split rotation)
 *    goes through StrictMath, which is defined to the bit (fdlibm).
 *  - All inputs are integers (TurnInput); no device-dependent floats ever enter.
 *  - A 64-bit FNV-1a hash folds in trajectory samples and outcomes, so replays
 *    can be *verified*, not trusted.
 */
class ShotResult(
    val input: TurnInput,
    /** Station index hit, one entry per projectile that connected. */
    val stationHits: List<Int>,
    val bodyHitCount: Int,
    val ticks: Int,
    val minSurfaceDist: Double,
    val hash: Long,
    val styleBonus: Int,
    val styleNotes: List<String>,
)

class ProjView(val id: Int, val x: Double, val y: Double, val radius: Double)

class LiveShot(
    private val bodies: List<Body>,
    private val stations: List<Station>,
    val shooter: Int,
    private val angleDeci: Int,
    private val power: Int,
    private val typeId: Int,
    presetBrakeTick: Int = -1,
    private val maxTicks: Int = Tuning.MAX_TICKS,
) {
    private val type = ProjectileType.byId(typeId)

    private class Proj(
        val id: Int,
        var px: Double, var py: Double,
        var vx: Double, var vy: Double,
        val radius: Double,
        var alive: Boolean = true,
    )

    private val projs = ArrayList<Proj>(3)
    private var nextId = 0
    private var hasSplit = false

    var tick: Int = 0; private set
    var done: Boolean = false; private set

    private var brakeTick: Int = presetBrakeTick
    private var brakeUsed = false

    private val launchX: Double
    private val launchY: Double
    private val dirX: Double
    private val dirY: Double

    private val stationHits = ArrayList<Int>(3)
    private var bodyHits = 0
    private var minSurfaceDist = Double.MAX_VALUE
    private var hash = FNV_OFFSET

    /** Set when a projectile dies, for the renderer to draw an explosion. */
    var lastImpact: Vec2? = null; private set
    var lastImpactWasStation = false; private set

    init {
        val st = stations[shooter]
        val a = StrictMath.toRadians(angleDeci / 10.0)
        dirX = StrictMath.cos(a)
        dirY = StrictMath.sin(a)
        launchX = st.x + dirX * Tuning.LAUNCH_OFFSET
        launchY = st.y + dirY * Tuning.LAUNCH_OFFSET
        val topSpeed = Tuning.MAX_SPEED * type.maxPowerFrac
        val speed = Tuning.MIN_SPEED + (power / 1000.0) * (topSpeed - Tuning.MIN_SPEED)
        projs.add(Proj(nextId++, launchX, launchY, dirX * speed, dirY * speed, type.radius))
        foldHash(angleDeci.toLong()); foldHash(power.toLong()); foldHash(typeId.toLong())
    }

    /** Arm the RETRO brake right now (records the exact tick into the replay input). */
    fun brakeNow(): Boolean {
        if (!type.canBrake || brakeUsed || done) return false
        brakeTick = tick + 1
        return true
    }

    fun projectiles(): List<ProjView> =
        projs.filter { it.alive }.map { ProjView(it.id, it.px, it.py, it.radius) }

    /** The finalized input, including the actual brake tick — this is what gets shared. */
    fun finalInput(): TurnInput = TurnInput(shooter, angleDeci, power, typeId, if (brakeUsed) brakeTick else -1)

    var result: ShotResult? = null; private set

    fun step() {
        if (done) return
        tick++

        if (type.splits && !hasSplit && tick == Tuning.SPLIT_TICK) split()
        if (brakeTick == tick && !brakeUsed && type.canBrake) {
            brakeUsed = true
            for (p in projs) if (p.alive) { p.vx *= Tuning.BRAKE_FACTOR; p.vy *= Tuning.BRAKE_FACTOR }
        }

        var anyAlive = false
        for (p in projs) {
            if (!p.alive) continue
            integrate(p)
            collide(p)
            if (p.alive) anyAlive = true
        }

        if (tick % HASH_STRIDE == 0) {
            for (p in projs) if (p.alive) {
                foldHash(StrictMath.round(p.px * 1000.0))
                foldHash(StrictMath.round(p.py * 1000.0))
            }
        }

        if (!anyAlive || tick >= maxTicks) finish(timedOut = anyAlive)
    }

    private fun integrate(p: Proj) {
        var ax = 0.0
        var ay = 0.0
        for (b in bodies) {
            val dx = b.x - p.px
            val dy = b.y - p.py
            val r2 = dx * dx + dy * dy + Tuning.SOFTENING2
            val r = Math.sqrt(r2)
            val a = b.mass / r2
            ax += a * dx / r
            ay += a * dy / r
            if (b.kind != BodyKind.REPULSOR) {
                val surface = r - b.radius - p.radius
                if (surface < minSurfaceDist) minSurfaceDist = surface
            }
        }
        // Semi-implicit (symplectic) Euler: stable orbits, no energy explosion.
        p.vx += ax * Tuning.DT
        p.vy += ay * Tuning.DT
        p.px += p.vx * Tuning.DT
        p.py += p.vy * Tuning.DT
    }

    private fun collide(p: Proj) {
        // Bodies (repulsors are intangible anomalies — field only).
        for ((bi, b) in bodies.withIndex()) {
            if (b.kind == BodyKind.REPULSOR) continue
            val dx = b.x - p.px
            val dy = b.y - p.py
            val rr = b.radius + p.radius
            if (dx * dx + dy * dy < rr * rr) {
                p.alive = false
                bodyHits++
                lastImpact = Vec2(p.px, p.py); lastImpactWasStation = false
                foldHash(1L); foldHash(bi.toLong()); foldHash(tick.toLong())
                return
            }
        }
        // Stations (own station has a launch grace window, then friendly fire is real).
        for ((si, s) in stations.withIndex()) {
            if (si == shooter && tick <= Tuning.OWN_STATION_GRACE_TICKS) continue
            val dx = s.x - p.px
            val dy = s.y - p.py
            val rr = Tuning.STATION_RADIUS + p.radius
            if (dx * dx + dy * dy < rr * rr) {
                p.alive = false
                stationHits.add(si)
                lastImpact = Vec2(p.px, p.py); lastImpactWasStation = true
                foldHash(2L); foldHash(si.toLong()); foldHash(tick.toLong())
                return
            }
        }
        // Out of bounds.
        if (p.px < -Tuning.OUT_MARGIN || p.px > Tuning.WORLD_W + Tuning.OUT_MARGIN ||
            p.py < -Tuning.OUT_MARGIN || p.py > Tuning.WORLD_H + Tuning.OUT_MARGIN
        ) {
            p.alive = false
            foldHash(3L); foldHash(tick.toLong())
        }
    }

    private fun split() {
        hasSplit = true
        val parent = projs.firstOrNull { it.alive } ?: return
        val theta = StrictMath.toRadians(Tuning.SPLIT_ANGLE_DEG)
        val c = StrictMath.cos(theta)
        val s = StrictMath.sin(theta)
        // Two extra shards rotated +/- theta from the parent's velocity.
        val vx = parent.vx; val vy = parent.vy
        projs.add(Proj(nextId++, parent.px, parent.py, vx * c - vy * s, vx * s + vy * c, parent.radius))
        projs.add(Proj(nextId++, parent.px, parent.py, vx * c + vy * s, -vx * s + vy * c, parent.radius))
        foldHash(4L); foldHash(tick.toLong())
    }

    private fun finish(timedOut: Boolean) {
        done = true
        if (timedOut) { foldHash(5L); foldHash(tick.toLong()) }

        var style = 0
        val notes = ArrayList<String>(3)
        val hitEnemy = stationHits.any { it != shooter }
        if (hitEnemy) {
            if (minSurfaceDist in 0.0..Tuning.GRAZE_DIST) {
                style += Tuning.GRAZE_BONUS; notes.add("Graze +${Tuning.GRAZE_BONUS}")
            }
            if (tick >= Tuning.LONGSHOT_TICKS) {
                style += Tuning.LONGSHOT_BONUS; notes.add("Long shot +${Tuning.LONGSHOT_BONUS}")
            }
            val firstHit = stationHits.first { it != shooter }
            val st = stations[firstHit]
            val tx = st.x - launchX
            val ty = st.y - launchY
            val tl = Math.sqrt(tx * tx + ty * ty)
            if (tl > 1e-9) {
                val cos = (dirX * tx + dirY * ty) / tl
                if (cos < -0.17) { // launched >100 degrees away from the target, and still hit
                    style += Tuning.TRICKSHOT_BONUS; notes.add("Trick shot +${Tuning.TRICKSHOT_BONUS}")
                }
            }
        }
        foldHash(style.toLong())

        result = ShotResult(
            input = finalInput(),
            stationHits = stationHits.toList(),
            bodyHitCount = bodyHits,
            ticks = tick,
            minSurfaceDist = minSurfaceDist,
            hash = hash,
            styleBonus = style,
            styleNotes = notes,
        )
    }

    fun runToCompletion(): ShotResult {
        while (!done) step()
        return result!!
    }

    private fun foldHash(v: Long) {
        var h = hash
        var x = v
        repeat(8) {
            h = (h xor (x and 0xFF)) * FNV_PRIME
            x = x ushr 8
        }
        hash = h
    }

    companion object {
        const val HASH_STRIDE = 8
        val FNV_OFFSET = 0xCBF29CE484222325UL.toLong()
        val FNV_PRIME = 0x100000001B3L
    }
}

/** Aim-preview trajectories: one polyline per projectile, cheap enough to run per drag event. */
object Preview {
    fun trajectories(
        bodies: List<Body>,
        stations: List<Station>,
        shooter: Int,
        angleDeci: Int,
        power: Int,
        typeId: Int,
        maxTicks: Int,
    ): List<List<Vec2>> {
        val shot = LiveShot(bodies, stations, shooter, angleDeci, power, typeId, maxTicks = maxTicks)
        val paths = LinkedHashMap<Int, MutableList<Vec2>>()
        while (!shot.done) {
            shot.step()
            for (p in shot.projectiles()) {
                paths.getOrPut(p.id) { ArrayList(maxTicks) }.add(Vec2(p.x, p.y))
            }
        }
        return paths.values.toList()
    }
}
