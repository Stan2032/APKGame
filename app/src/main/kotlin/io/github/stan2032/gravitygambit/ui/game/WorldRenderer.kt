package io.github.stan2032.gravitygambit.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import io.github.stan2032.gravitygambit.core.BodyKind
import io.github.stan2032.gravitygambit.core.GameMode
import io.github.stan2032.gravitygambit.core.Rng
import io.github.stan2032.gravitygambit.core.Station
import io.github.stan2032.gravitygambit.core.SystemSpec
import io.github.stan2032.gravitygambit.core.Tuning
import io.github.stan2032.gravitygambit.core.Vec2
import io.github.stan2032.gravitygambit.ui.AccentCyan
import io.github.stan2032.gravitygambit.ui.BodyPalettes
import io.github.stan2032.gravitygambit.ui.DangerRed
import io.github.stan2032.gravitygambit.ui.OkGreen
import io.github.stan2032.gravitygambit.ui.StarYellow
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** Uniform letterbox fit of the fixed-size world into the canvas. */
class WorldTransform(val widthPx: Float, val heightPx: Float) {
    val scale: Float = min(widthPx / Tuning.WORLD_W.toFloat(), heightPx / Tuning.WORLD_H.toFloat())
    val offX: Float = (widthPx - Tuning.WORLD_W.toFloat() * scale) / 2f
    val offY: Float = (heightPx - Tuning.WORLD_H.toFloat() * scale) / 2f

    fun x(wx: Double): Float = offX + wx.toFloat() * scale
    fun y(wy: Double): Float = offY + wy.toFloat() * scale
    fun o(v: Vec2): Offset = Offset(x(v.x), y(v.y))
    fun o(wx: Double, wy: Double): Offset = Offset(x(wx), y(wy))
    fun r(wr: Double): Float = wr.toFloat() * scale
    fun toWorld(p: Offset): Vec2 = Vec2(
        ((p.x - offX) / scale).toDouble(),
        ((p.y - offY) / scale).toDouble(),
    )
}

/**
 * Everything computable once per (system, canvas size) lives here so the
 * per-frame draw pass allocates nothing: star positions, nebula/vignette and
 * per-body gradient brushes, ring geometry. Performance is a standing
 * priority (docs/PERFORMANCE.md) — hot loops below are draw calls only.
 */
class RenderCache(spec: SystemSpec, tr: WorldTransform) {

    class Star(val x: Float, val y: Float, val r: Float, val speed: Float, val phase: Float, val bright: Boolean)
    class Neb(val brush: Brush, val center: Offset, val radius: Float)
    class BodyVisual(
        val kind: BodyKind,
        val center: Offset,
        val radius: Float,
        val glowBrush: Brush?,
        val surfaceBrush: Brush,
        val rimColor: Color,
        val hasRing: Boolean,
        val ringColor: Color,
        val ringRx: Float,
        val ringRy: Float,
    )

    val thin: Float = tr.r(1.6)
    val med: Float = tr.r(3.0)
    val stars: List<Star>
    val nebulae: List<Neb>
    val bodyVisuals: List<BodyVisual>
    val vignette: Brush
    val canvasSize = Size(tr.widthPx, tr.heightPx)

    init {
        val rng = Rng(Rng.mix(spec.seed, 0x57A2F1E1DL))

        stars = List(150) { i ->
            Star(
                x = tr.x(rng.range(-30.0, Tuning.WORLD_W + 30.0)),
                y = tr.y(rng.range(-30.0, Tuning.WORLD_H + 30.0)),
                r = tr.r(rng.range(0.5, 1.9)),
                speed = rng.range(0.4, 1.7).toFloat(),
                phase = rng.range(0.0, 6.28).toFloat(),
                bright = i % 14 == 0,
            )
        }

        val nebColors = listOf(
            Color(0xFF7C4DFF), Color(0xFF00B8D4), Color(0xFF283593),
        )
        nebulae = List(3) { i ->
            val c = tr.o(
                rng.range(0.0, Tuning.WORLD_W),
                rng.range(0.0, Tuning.WORLD_H),
            )
            val rad = tr.r(rng.range(280.0, 520.0))
            Neb(
                brush = Brush.radialGradient(
                    colors = listOf(nebColors[i].copy(alpha = 0.085f), Color.Transparent),
                    center = c, radius = rad,
                ),
                center = c, radius = rad,
            )
        }

        bodyVisuals = spec.bodies.mapIndexed { i, b ->
            val center = tr.o(b.x, b.y)
            val radius = tr.r(b.radius)
            val (light, dark) = BodyPalettes[b.palette % BodyPalettes.size]
            val bodyRng = Rng(Rng.mix(spec.seed, 0xB0D1E5L + i))
            when (b.kind) {
                BodyKind.STAR -> BodyVisual(
                    kind = b.kind, center = center, radius = radius,
                    glowBrush = Brush.radialGradient(
                        colors = listOf(
                            light.copy(alpha = 0.50f), light.copy(alpha = 0.14f), Color.Transparent,
                        ),
                        center = center, radius = radius * 3.1f,
                    ),
                    surfaceBrush = Brush.radialGradient(
                        colors = listOf(Color.White, StarYellow, Color(0xFFFF8F00)),
                        center = center, radius = radius,
                    ),
                    rimColor = light,
                    hasRing = false, ringColor = Color.Transparent, ringRx = 0f, ringRy = 0f,
                )
                BodyKind.PLANET -> {
                    val ring = b.radius > 38.0 && bodyRng.chance(0.30)
                    BodyVisual(
                        kind = b.kind, center = center, radius = radius,
                        glowBrush = null,
                        surfaceBrush = Brush.radialGradient(
                            colors = listOf(light, dark),
                            center = center + Offset(-radius * 0.38f, -radius * 0.38f),
                            radius = radius * 1.75f,
                        ),
                        rimColor = light.copy(alpha = 0.45f),
                        hasRing = ring,
                        ringColor = lerp(light, Color.White, 0.35f),
                        ringRx = radius * 1.85f,
                        ringRy = radius * 0.52f,
                    )
                }
                // Comets move, so their centre is recomputed per frame; only
                // brushes/geometry are cached here (docs/PERFORMANCE.md, D-22).
                BodyKind.COMET -> BodyVisual(
                    kind = b.kind, center = center, radius = radius,
                    glowBrush = Brush.radialGradient(
                        colors = listOf(Color(0xFF80DEEA).copy(alpha = 0.40f), Color.Transparent),
                        center = Offset.Zero, radius = radius * 3.4f,
                    ),
                    surfaceBrush = Brush.radialGradient(
                        colors = listOf(Color.White, Color(0xFF4DD0E1)),
                        center = Offset.Zero, radius = radius,
                    ),
                    rimColor = Color(0xFF80DEEA),
                    hasRing = false, ringColor = Color.Transparent, ringRx = 0f, ringRy = 0f,
                )
                BodyKind.PULSAR -> BodyVisual(
                    kind = b.kind, center = center, radius = radius,
                    glowBrush = Brush.radialGradient(
                        colors = listOf(Color(0xFFB388FF).copy(alpha = 0.45f), Color.Transparent),
                        center = center, radius = radius * 3.0f,
                    ),
                    surfaceBrush = Brush.radialGradient(
                        colors = listOf(Color.White, Color(0xFF7C4DFF), Color(0xFF311B92)),
                        center = center, radius = radius,
                    ),
                    rimColor = Color(0xFFB388FF),
                    hasRing = false, ringColor = Color.Transparent, ringRx = 0f, ringRy = 0f,
                )
                BodyKind.REPULSOR -> BodyVisual(
                    kind = b.kind, center = center, radius = radius,
                    glowBrush = Brush.radialGradient(
                        colors = listOf(DangerRed.copy(alpha = 0.22f), Color.Transparent),
                        center = center, radius = radius * 2.4f,
                    ),
                    surfaceBrush = Brush.radialGradient(
                        colors = listOf(Color(0xFF2A0A14), Color(0xFF06010A)),
                        center = center, radius = radius * 0.85f,
                    ),
                    rimColor = DangerRed,
                    hasRing = false, ringColor = Color.Transparent, ringRx = 0f, ringRy = 0f,
                )
            }
        }

        vignette = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Transparent, Color(0xFF000000).copy(alpha = 0.34f)),
            center = Offset(tr.widthPx / 2f, tr.heightPx / 2f),
            radius = maxOf(tr.widthPx, tr.heightPx) * 0.72f,
        )
    }

    /** Multi-stop trail color LUT so per-point drawing needs no lerp math. */
    companion object {
        fun buildTrailLut(colors: List<Color>, size: Int = 64): Array<Color> = Array(size) { i ->
            val pos = i.toFloat() / (size - 1) * (colors.size - 1)
            val a = pos.toInt().coerceIn(0, colors.size - 1)
            val b = (a + 1).coerceAtMost(colors.size - 1)
            lerp(colors[a], colors[b], pos - a)
        }
    }
}

// ---------------------------------------------------------------------------
// Frame pass — order matters; every function is allocation-free.
// ---------------------------------------------------------------------------

fun DrawScope.drawSpaceBackdrop(cache: RenderCache, t: Float) {
    for (n in cache.nebulae) {
        drawCircle(brush = n.brush, radius = n.radius, center = n.center)
    }
    for (s in cache.stars) {
        val tw = 0.55f + 0.45f * sin(t * s.speed + s.phase)
        val alpha = (if (s.bright) 0.85f else 0.45f) * tw + 0.08f
        drawCircle(Color.White.copy(alpha = alpha.coerceIn(0.05f, 1f)), s.r, Offset(s.x, s.y))
        if (s.bright) {
            // 4-point sparkle on the handful of hero stars.
            val len = s.r * (2.6f + 1.2f * tw)
            val c = Color.White.copy(alpha = 0.35f * tw)
            drawLine(c, Offset(s.x - len, s.y), Offset(s.x + len, s.y), cache.thin * 0.6f)
            drawLine(c, Offset(s.x, s.y - len), Offset(s.x, s.y + len), cache.thin * 0.6f)
        }
    }
}

/**
 * Moving bodies (comets) are drawn from their live tick position; everything
 * else uses the cached centre. [tick] is the simulation tick so the drawn
 * position always matches the physics exactly.
 */
fun DrawScope.drawMovingBodies(
    spec: SystemSpec,
    cache: RenderCache,
    tick: Int,
    t: Float,
    tr: WorldTransform,
) {
    for ((i, b) in spec.bodies.withIndex()) {
        if (!b.moves) continue
        val v = cache.bodyVisuals[i]
        val c = tr.o(b.xAt(tick), b.yAt(tick))
        // Tail trailing the direction of travel.
        val len = tr.r(46.0)
        val sp = Math.hypot(b.vx, b.vy)
        if (sp > 1e-9) {
            val ux = (b.vx / sp).toFloat()
            val uy = (b.vy / sp).toFloat()
            for (k in 1..10) {
                val f = k / 10f
                drawCircle(
                    v.rimColor.copy(alpha = (1f - f) * 0.30f),
                    v.radius * (1f - f * 0.7f),
                    c - Offset(ux * len * f, uy * len * f),
                )
            }
        }
        drawCircle(v.glowBrush!!, v.radius * 3.4f, c)
        drawCircle(Color.White, v.radius, c)
        val halo = 1f + 0.14f * sin(t * 4f)
        drawCircle(v.rimColor.copy(alpha = 0.7f), v.radius * 1.5f * halo, c, style = Stroke(cache.thin))
    }
}

fun DrawScope.drawBodies(cache: RenderCache, t: Float) {
    for (b in cache.bodyVisuals) when (b.kind) {
        // Drawn per-frame at their live positions by drawMovingBodies.
        BodyKind.COMET -> Unit
        BodyKind.PULSAR -> {
            // Gravity you can see breathing: the halo swells on the same
            // period as the mass, so timing a shot is a visual read.
            val phase = (1f + kotlin.math.cos(t * 6.2831853f * 60f / Tuning.PULSAR_PERIOD.toFloat())) / 2f
            drawCircle(b.glowBrush!!, b.radius * (2.2f + 1.1f * phase), b.center)
            drawCircle(b.surfaceBrush, b.radius, b.center)
            drawCircle(
                b.rimColor.copy(alpha = 0.25f + 0.5f * phase),
                b.radius * (1.35f + 0.5f * phase), b.center, style = Stroke(cache.med),
            )
        }
        BodyKind.STAR -> {
            drawCircle(b.glowBrush!!, b.radius * 3.1f, b.center)
            drawCircle(b.surfaceBrush, b.radius, b.center)
            // Breathing corona.
            val pulse = 1.22f + 0.07f * sin(t * 1.4f)
            drawCircle(
                b.rimColor.copy(alpha = 0.10f + 0.05f * sin(t * 1.9f)),
                b.radius * pulse, b.center, style = Stroke(cache.med),
            )
        }
        BodyKind.PLANET -> {
            if (b.hasRing) {
                drawOval(
                    color = b.ringColor.copy(alpha = 0.40f),
                    topLeft = b.center - Offset(b.ringRx, b.ringRy),
                    size = Size(b.ringRx * 2, b.ringRy * 2),
                    style = Stroke(cache.med),
                )
            }
            drawCircle(b.surfaceBrush, b.radius, b.center)
            // Terminator shadow hugging the lower-right limb.
            drawCircle(
                Color.Black.copy(alpha = 0.25f),
                b.radius, b.center + Offset(b.radius * 0.18f, b.radius * 0.18f),
                style = Stroke(width = b.radius * 0.30f),
            )
            // Thin atmosphere rim.
            drawCircle(b.rimColor, b.radius * 1.03f, b.center, style = Stroke(cache.thin))
            if (b.hasRing) {
                // Front half of the ring passes over the planet's lower limb.
                drawArc(
                    color = b.ringColor.copy(alpha = 0.85f),
                    startAngle = 8f, sweepAngle = 164f, useCenter = false,
                    topLeft = b.center - Offset(b.ringRx, b.ringRy),
                    size = Size(b.ringRx * 2, b.ringRy * 2),
                    style = Stroke(cache.med),
                )
            }
        }
        BodyKind.REPULSOR -> {
            drawCircle(b.glowBrush!!, b.radius * 2.4f, b.center)
            drawCircle(b.surfaceBrush, b.radius * 0.85f, b.center)
            // Counter-rotating shear arcs — reads as "space is wrong here".
            for (k in 0..2) {
                val dir = if (k % 2 == 0) 1f else -1f
                val start = (t * 46f * dir + k * 120f) % 360f
                val rr = b.radius * (1.05f + k * 0.28f)
                drawArc(
                    color = b.rimColor.copy(alpha = 0.55f - k * 0.13f),
                    startAngle = start, sweepAngle = 95f, useCenter = false,
                    topLeft = b.center - Offset(rr, rr),
                    size = Size(rr * 2, rr * 2),
                    style = Stroke(cache.thin),
                )
            }
        }
    }
}

fun DrawScope.drawStations(
    stations: List<Station>,
    hp: IntArray,
    mode: GameMode,
    t: Float,
    tr: WorldTransform,
    cache: RenderCache,
) {
    for ((i, s) in stations.withIndex()) {
        val center = tr.o(s.x, s.y)
        val radius = tr.r(Tuning.STATION_RADIUS)
        val isTargetCore = mode != GameMode.DUEL && i == 1
        val baseColor = if (i == 0) AccentCyan else Color(0xFFFF8A65)

        if (isTargetCore) {
            val pulse = 1f + 0.12f * sin(t * 3f)
            drawCircle(baseColor.copy(alpha = 0.9f), radius * 0.55f, center)
            drawCircle(Color.White.copy(alpha = 0.9f), radius * 0.2f, center)
            drawCircle(baseColor.copy(alpha = 0.7f), radius * pulse, center, style = Stroke(cache.med * 0.85f))
            // Counter-rotating reticle arcs.
            for (k in 0..1) {
                val dir = if (k == 0) 1f else -1f
                val start = (t * 60f * dir + k * 180f) % 360f
                val rr = radius * (1.45f + k * 0.22f)
                drawArc(
                    color = baseColor.copy(alpha = 0.45f),
                    startAngle = start, sweepAngle = 70f, useCenter = false,
                    topLeft = center - Offset(rr, rr), size = Size(rr * 2, rr * 2),
                    style = Stroke(cache.thin),
                )
            }
        } else {
            // Hull.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(baseColor, baseColor.copy(alpha = 0.25f)),
                    center = center, radius = radius,
                ),
                radius = radius, center = center,
            )
            drawCircle(Color(0xFF0B1021), radius * 0.55f, center)
            drawCircle(Color.White.copy(alpha = 0.9f), radius * 0.18f, center)
            // Antenna + blinking beacon.
            val tip = center - Offset(0f, radius * 1.55f)
            drawLine(baseColor.copy(alpha = 0.8f), center - Offset(0f, radius), tip, cache.thin)
            val blink = 0.35f + 0.65f * abs(sin(t * 2.6f + i * 1.7f))
            drawCircle(Color.White.copy(alpha = blink), cache.thin * 1.6f, tip)
            // HP ring.
            val frac = hp[i].coerceAtLeast(0) / Tuning.STATION_HP.toFloat()
            val rr = radius + tr.r(7.0)
            drawArc(
                color = lerp(DangerRed, OkGreen, frac),
                startAngle = -90f, sweepAngle = 360f * frac, useCenter = false,
                topLeft = center - Offset(rr, rr), size = Size(rr * 2, rr * 2),
                style = Stroke(width = cache.med),
            )
        }
    }
}

fun DrawScope.drawPreview(paths: List<List<Vec2>>, tr: WorldTransform) {
    for (path in paths) {
        val n = path.size
        var i = 0
        while (i < n) {
            val f = 1f - i.toFloat() / n
            val big = (i / 4) % 4 == 0
            drawCircle(
                color = (if (big) AccentCyan else Color.White).copy(alpha = f * 0.42f + 0.08f),
                radius = tr.r(if (big) 2.6 else 1.9),
                center = tr.o(path[i]),
            )
            i += 4
        }
    }
}

fun DrawScope.drawTrail(trail: ArrayDeque<Vec2>, lut: Array<Color>, tr: WorldTransform) {
    val n = trail.size
    if (n == 0) return
    val stride = if (n > 420) 2 else 1
    val base = tr.r(2.6)
    var i = 0
    while (i < n) {
        val f = i.toFloat() / n
        val color = lut[(f * (lut.size - 1)).toInt()]
        val p = trail[i]
        // Soft outer glow on a sparse subset keeps the pass bounded.
        if (i % (stride * 3) == 0) {
            drawCircle(color.copy(alpha = f * 0.22f), base * 2.6f * (0.4f + 0.6f * f), tr.o(p))
        }
        drawCircle(color.copy(alpha = 0.06f + 0.60f * f), base * (0.40f + 0.68f * f), tr.o(p))
        i += stride
    }
}

fun DrawScope.drawProjectilesGlow(shotVisitor: ((Int, Double, Double, Double) -> Unit) -> Unit, tr: WorldTransform) {
    shotVisitor { _, x, y, radius ->
        val c = tr.o(x, y)
        drawCircle(AccentCyan.copy(alpha = 0.16f), tr.r(radius * 3.4), c)
        drawCircle(AccentCyan.copy(alpha = 0.45f), tr.r(radius * 1.9), c)
        drawCircle(Color.White, tr.r(radius), c)
    }
}

fun DrawScope.drawAim(
    station: Station,
    angleDeci: Int,
    power: Int,
    color: Color,
    tr: WorldTransform,
    cache: RenderCache,
) {
    val a = Math.toRadians(angleDeci / 10.0)
    val dx = StrictMath.cos(a)
    val dy = StrictMath.sin(a)
    val center = tr.o(station.x, station.y)
    // Power ring around the station.
    val pr = tr.r(Tuning.STATION_RADIUS + 13.0)
    drawArc(
        color = color.copy(alpha = 0.65f),
        startAngle = -90f, sweepAngle = 306f * (power / 1000f), useCenter = false,
        topLeft = center - Offset(pr, pr), size = Size(pr * 2, pr * 2),
        style = Stroke(cache.thin),
    )
    val start = tr.o(
        station.x + dx * (Tuning.STATION_RADIUS + 6),
        station.y + dy * (Tuning.STATION_RADIUS + 6),
    )
    val len = 55.0 + power * 0.11
    val end = tr.o(station.x + dx * len, station.y + dy * len)
    drawLine(color.copy(alpha = 0.9f), start, end, strokeWidth = cache.med)
    val back = 14.0
    val side = 7.0
    val bx = station.x + dx * (len - back)
    val by = station.y + dy * (len - back)
    drawLine(color, end, tr.o(bx - dy * side, by + dx * side), strokeWidth = cache.med * 0.8f)
    drawLine(color, end, tr.o(bx + dy * side, by - dx * side), strokeWidth = cache.med * 0.8f)
}

private val ExplosionColors = listOf(Color.White, StarYellow, Color(0xFFFF8A65))

fun DrawScope.drawExplosion(e: Explosion, progress: Float, tr: WorldTransform, cache: RenderCache) {
    val center = tr.o(e.at)
    val fade = 1f - progress
    // Station kills flash the whole frame for a beat.
    if (e.big && progress < 0.22f) {
        drawRect(Color.White.copy(alpha = (0.22f - progress) * 0.75f), size = cache.canvasSize)
    }
    drawCircle(
        color = Color.White.copy(alpha = fade * 0.7f),
        radius = tr.r(8.0 + 95.0 * progress),
        center = center,
        style = Stroke(width = cache.med * fade + 1f),
    )
    // Secondary, slower shockwave.
    drawCircle(
        color = AccentCyan.copy(alpha = fade * 0.30f),
        radius = tr.r(4.0 + 60.0 * progress),
        center = center,
        style = Stroke(width = cache.thin),
    )
    for (p in e.particles) {
        val d = p.speed * 0.8 * progress
        drawCircle(
            color = ExplosionColors[p.hue].copy(alpha = fade),
            radius = tr.r(2.4) * (1f - progress * 0.6f),
            center = tr.o(e.at.x + p.dx * d, e.at.y + p.dy * d),
        )
    }
}

fun DrawScope.drawVignette(cache: RenderCache) {
    drawRect(brush = cache.vignette, size = cache.canvasSize)
}
