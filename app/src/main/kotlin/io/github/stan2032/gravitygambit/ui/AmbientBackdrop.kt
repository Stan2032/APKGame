package io.github.stan2032.gravitygambit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.stan2032.gravitygambit.core.Rng
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

/**
 * Slow, cheap starfield + drifting comet behind the menu screens, so the app
 * looks like one place rather than "game canvas + settings forms".
 *
 * Performance (docs/PERFORMANCE.md): star geometry is precomputed once per
 * canvas size; the frame pass only draws. The clock ticks at ~30fps rather
 * than display rate — menus are idle screens and this halves their redraw
 * cost with no visible difference at these drift speeds.
 */
private class AmbientStar(
    val xFrac: Float, val yFrac: Float, val r: Float,
    val speed: Float, val phase: Float, val drift: Float,
)

private class AmbientScene(w: Float, h: Float) {
    val stars: List<AmbientStar>
    val nebula: Brush
    val nebulaCenter: Offset
    val nebulaRadius: Float

    init {
        val rng = Rng(0x4D3E9C71L)
        stars = List(70) {
            AmbientStar(
                xFrac = rng.nextDouble().toFloat(),
                yFrac = rng.nextDouble().toFloat(),
                r = rng.range(0.7, 2.1).toFloat(),
                speed = rng.range(0.25, 0.9).toFloat(),
                phase = rng.range(0.0, 6.28).toFloat(),
                drift = rng.range(1.5, 7.0).toFloat(),
            )
        }
        nebulaCenter = Offset(w * 0.78f, h * 0.20f)
        nebulaRadius = maxOf(w, h) * 0.55f
        nebula = Brush.radialGradient(
            colors = listOf(
                AccentPurple.copy(alpha = 0.10f),
                AccentCyan.copy(alpha = 0.04f),
                Color.Transparent,
            ),
            center = nebulaCenter,
            radius = nebulaRadius,
        )
    }
}

@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var lastEmit = 0L
        while (isActive) {
            val now = withFrameNanos { it }
            // ~30fps cap: idle screens don't need display-rate redraws.
            if (now - lastEmit >= 33_000_000L) {
                lastEmit = now
                time = (now - start) / 1e9f
            }
        }
    }

    var scene by remember { mutableStateOf<AmbientScene?>(null) }
    Canvas(modifier.fillMaxSize()) {
        val s = scene ?: AmbientScene(size.width, size.height).also { scene = it }
        val t = time

        drawCircle(brush = s.nebula, radius = s.nebulaRadius, center = s.nebulaCenter)

        for (star in s.stars) {
            // Gentle horizontal drift, wrapping at the edges.
            val x = ((star.xFrac * size.width + t * star.drift) % (size.width + 40f)) - 20f
            val y = star.yFrac * size.height
            val alpha = 0.18f + 0.34f * (0.5f + 0.5f * sin(t * star.speed + star.phase))
            drawCircle(Color.White.copy(alpha = alpha), star.r, Offset(x, y))
        }

        // A single slow comet on a wide arc — the only "event" in the menus.
        val period = 26f
        val p = (t % period) / period
        if (p < 0.55f) {
            val a = Math.PI.toFloat() * (0.15f + p * 1.1f)
            val cx = size.width * 0.5f + cos(a) * size.width * 0.85f
            val cy = size.height * 0.22f + sin(a) * size.height * 0.30f
            val fade = (1f - kotlin.math.abs(p - 0.275f) / 0.275f).coerceIn(0f, 1f)
            for (i in 0 until 14) {
                val f = i / 14f
                val tx = cx - cos(a) * f * 90f
                val ty = cy - sin(a) * f * 90f
                drawCircle(
                    AccentCyan.copy(alpha = (1f - f) * 0.32f * fade),
                    (1f - f) * 2.6f + 0.6f,
                    Offset(tx, ty),
                )
            }
            drawCircle(Color.White.copy(alpha = 0.75f * fade), 2.6f, Offset(cx, cy))
        }
    }
}

/** Thin orbit-motif divider used to break up long menu screens. */
@Composable
fun OrbitRule(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val y = size.height / 2f
        drawLine(
            Brush.horizontalGradient(
                listOf(Color.Transparent, AccentCyan.copy(alpha = 0.35f), Color.Transparent)
            ),
            Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f,
        )
        drawCircle(AccentCyan.copy(alpha = 0.55f), 3.5f, Offset(size.width / 2f, y))
        drawCircle(
            AccentCyan.copy(alpha = 0.30f), 8f, Offset(size.width / 2f, y),
            style = Stroke(width = 1.2f),
        )
    }
}

