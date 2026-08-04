package io.github.stan2032.gravitygambit.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import io.github.stan2032.gravitygambit.core.Body
import io.github.stan2032.gravitygambit.core.BodyKind
import io.github.stan2032.gravitygambit.core.GameMode
import io.github.stan2032.gravitygambit.core.ProjView
import io.github.stan2032.gravitygambit.core.Rng
import io.github.stan2032.gravitygambit.core.Station
import io.github.stan2032.gravitygambit.core.Tuning
import io.github.stan2032.gravitygambit.core.Vec2
import io.github.stan2032.gravitygambit.ui.AccentCyan
import io.github.stan2032.gravitygambit.ui.BodyPalettes
import io.github.stan2032.gravitygambit.ui.DangerRed
import io.github.stan2032.gravitygambit.ui.OkGreen
import io.github.stan2032.gravitygambit.ui.StarYellow
import kotlin.math.min
import kotlin.math.sin

/** Uniform letterbox fit of the fixed-size world into the canvas. */
class WorldTransform(widthPx: Float, heightPx: Float) {
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

fun DrawScope.drawStarfield(seed: Long, t: Float, tr: WorldTransform) {
    val rng = Rng(Rng.mix(seed, 0x57A2F1E1DL))
    repeat(110) {
        val x = rng.range(0.0, Tuning.WORLD_W)
        val y = rng.range(0.0, Tuning.WORLD_H)
        val size = rng.range(0.6, 1.9)
        val speed = rng.range(0.4, 1.6)
        val phase = rng.range(0.0, 6.28)
        val alpha = (0.30f + 0.28f * sin(t * speed.toFloat() + phase.toFloat())).coerceIn(0.05f, 0.65f)
        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = tr.r(size),
            center = tr.o(x, y),
        )
    }
}

fun DrawScope.drawBodies(bodies: List<Body>, t: Float, tr: WorldTransform) {
    for (b in bodies) {
        val center = tr.o(b.x, b.y)
        val radius = tr.r(b.radius)
        when (b.kind) {
            BodyKind.STAR -> {
                val glow = BodyPalettes[b.palette % BodyPalettes.size].first
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = 0.45f), Color.Transparent),
                        center = center,
                        radius = radius * 2.8f,
                    ),
                    radius = radius * 2.8f,
                    center = center,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, StarYellow, Color(0xFFFF8F00)),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
            BodyKind.PLANET -> {
                val (light, dark) = BodyPalettes[b.palette % BodyPalettes.size]
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(light, dark),
                        center = center + Offset(-radius * 0.35f, -radius * 0.35f),
                        radius = radius * 1.7f,
                    ),
                    radius = radius,
                    center = center,
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.25f),
                    radius = radius,
                    center = center + Offset(radius * 0.18f, radius * 0.18f),
                    style = Stroke(width = radius * 0.28f),
                )
            }
            BodyKind.REPULSOR -> {
                val pulse = 1f + 0.16f * sin(t * 2.4f)
                drawCircle(
                    color = DangerRed.copy(alpha = 0.75f),
                    radius = radius * pulse,
                    center = center,
                    style = Stroke(width = tr.r(3.0)),
                )
                drawCircle(
                    color = DangerRed.copy(alpha = 0.35f),
                    radius = radius * pulse * 1.5f,
                    center = center,
                    style = Stroke(width = tr.r(1.5)),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF2A0A14), Color(0xFF0D0208)),
                        center = center,
                        radius = radius * 0.8f,
                    ),
                    radius = radius * 0.8f,
                    center = center,
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
) {
    for ((i, s) in stations.withIndex()) {
        val center = tr.o(s.x, s.y)
        val radius = tr.r(Tuning.STATION_RADIUS)
        val isTargetCore = mode != GameMode.DUEL && i == 1
        val baseColor = if (i == 0) AccentCyan else Color(0xFFFF8A65)

        if (isTargetCore) {
            // Objective core: pulsing target rings.
            val pulse = 1f + 0.12f * sin(t * 3f)
            drawCircle(baseColor.copy(alpha = 0.9f), radius * 0.55f, center)
            drawCircle(Color.White.copy(alpha = 0.85f), radius * 0.2f, center)
            drawCircle(baseColor.copy(alpha = 0.7f), radius * pulse, center, style = Stroke(tr.r(2.5)))
            drawCircle(baseColor.copy(alpha = 0.3f), radius * pulse * 1.45f, center, style = Stroke(tr.r(1.5)))
        } else {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(baseColor, baseColor.copy(alpha = 0.25f)),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            drawCircle(Color(0xFF0B1021), radius * 0.55f, center)
            drawCircle(Color.White.copy(alpha = 0.9f), radius * 0.18f, center)
            // HP ring.
            val frac = hp[i].coerceAtLeast(0) / Tuning.STATION_HP.toFloat()
            drawArc(
                color = lerp(DangerRed, OkGreen, frac),
                startAngle = -90f,
                sweepAngle = 360f * frac,
                useCenter = false,
                topLeft = center - Offset(radius + tr.r(7.0), radius + tr.r(7.0)),
                size = androidx.compose.ui.geometry.Size((radius + tr.r(7.0)) * 2, (radius + tr.r(7.0)) * 2),
                style = Stroke(width = tr.r(3.5)),
            )
        }
    }
}

fun DrawScope.drawPreview(paths: List<List<Vec2>>, tr: WorldTransform) {
    for (path in paths) {
        val n = path.size
        var i = 0
        while (i < n) {
            val alpha = (1f - i.toFloat() / n) * 0.45f + 0.08f
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = tr.r(2.2),
                center = tr.o(path[i]),
            )
            i += 4
        }
    }
}

fun DrawScope.drawTrail(trail: List<Vec2>, colors: List<Color>, tr: WorldTransform) {
    val n = trail.size
    if (n == 0) return
    for (i in 0 until n) {
        val f = i.toFloat() / n
        val colorPos = f * (colors.size - 1)
        val ci = colorPos.toInt().coerceIn(0, colors.size - 1)
        val cj = (ci + 1).coerceAtMost(colors.size - 1)
        val color = lerp(colors[ci], colors[cj], colorPos - ci)
        drawCircle(
            color = color.copy(alpha = f * 0.65f + 0.05f),
            radius = tr.r(2.6) * (0.5f + 0.5f * f),
            center = tr.o(trail[i]),
        )
    }
}

fun DrawScope.drawProjectiles(projs: List<ProjView>, color: Color, tr: WorldTransform) {
    for (p in projs) {
        val c = tr.o(p.x, p.y)
        drawCircle(color.copy(alpha = 0.30f), tr.r(p.radius * 2.6), c)
        drawCircle(Color.White, tr.r(p.radius), c)
    }
}

fun DrawScope.drawAim(
    station: Station,
    angleDeci: Int,
    power: Int,
    color: Color,
    tr: WorldTransform,
) {
    val a = Math.toRadians(angleDeci / 10.0)
    val dx = StrictMath.cos(a)
    val dy = StrictMath.sin(a)
    val start = tr.o(
        station.x + dx * (Tuning.STATION_RADIUS + 6),
        station.y + dy * (Tuning.STATION_RADIUS + 6),
    )
    val len = 55.0 + power * 0.11
    val end = tr.o(station.x + dx * len, station.y + dy * len)
    drawLine(color.copy(alpha = 0.9f), start, end, strokeWidth = tr.r(3.0))
    // Arrowhead.
    val back = 14.0
    val side = 7.0
    val bx = station.x + dx * (len - back)
    val by = station.y + dy * (len - back)
    drawLine(color, end, tr.o(bx - dy * side, by + dx * side), strokeWidth = tr.r(2.5))
    drawLine(color, end, tr.o(bx + dy * side, by - dx * side), strokeWidth = tr.r(2.5))
}

private val ExplosionColors = listOf(Color.White, StarYellow, Color(0xFFFF8A65))

fun DrawScope.drawExplosion(e: Explosion, progress: Float, tr: WorldTransform) {
    val center = tr.o(e.at)
    val fade = 1f - progress
    drawCircle(
        color = Color.White.copy(alpha = fade * 0.7f),
        radius = tr.r(8.0 + 90.0 * progress),
        center = center,
        style = Stroke(width = tr.r(2.5) * fade + 1f),
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
