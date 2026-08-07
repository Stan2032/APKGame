package io.github.stan2032.gravitygambit.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.stan2032.gravitygambit.ui.StarYellow
import io.github.stan2032.gravitygambit.ui.TextDim
import io.github.stan2032.gravitygambit.ui.animationsEnabled
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The stars a campaign clear earned, arriving one at a time.
 *
 * Before this they were a line of text — `"★★☆"` — which stated the result
 * and celebrated nothing, on the single screen where the game is supposed to
 * say *well done*. It is also one of the shots listed in
 * docs/STORE_LISTING.md, and a static glyph row does not sell anything.
 *
 * Each star lands with a short overshoot and throws a ring of rays that
 * expands and fades. They are **staggered**, which is the whole trick: three
 * stars arriving together is one event, while three arriving in sequence is
 * three, and the third one landing is what a player waits for.
 *
 * Unearned stars are drawn hollow from the start and never animate — the gap
 * between what you got and what was available should be visible, not
 * performed.
 *
 * Pure presentation: nothing here is read by the engine, hashed, or shared.
 * Honours [animationsEnabled], and when motion is off it draws the finished
 * state immediately rather than nothing (see `ui/Motion.kt`).
 */
@Composable
fun StarBurst(
    stars: Int,
    total: Int = 3,
    modifier: Modifier = Modifier,
) {
    val animate = animationsEnabled()
    // One driver per star: 0 = not yet arrived, 1 = fully landed.
    val landings = remember(stars, total, animate) {
        List(total) { Animatable(if (animate) 0f else 1f) }
    }
    LaunchedEffect(stars, total, animate) {
        if (!animate) return@LaunchedEffect
        for (i in 0 until total) {
            if (i >= stars) {
                landings[i].snapTo(1f)
                continue
            }
            delay(STAGGER_MS)
            landings[i].animateTo(1f, ARRIVAL)
        }
    }

    Row(
        modifier.semantics {
            contentDescription = "$stars of $total stars"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until total) {
            val earned = i < stars
            Box(Modifier.size(STAR_BOX)) {
                Canvas(Modifier.size(STAR_BOX)) {
                    drawStar(landings[i].value, earned)
                }
            }
            if (i < total - 1) Box(Modifier.width(GAP).height(1.dp))
        }
    }
}

/**
 * One star at [progress] through its landing.
 *
 * The overshoot is in the *scale*, not in the position: a star that slides
 * into place drags the eye sideways, while one that punches down in place
 * keeps the eye where the next star is about to appear.
 */
private fun DrawScope.drawStar(progress: Float, earned: Boolean) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension / 2f

    if (!earned) {
        // Hollow, dim, and static. The shape of what was missed.
        drawStarPath(c, r * 0.66f, TextDim.copy(alpha = 0.45f), filled = false)
        return
    }
    if (progress <= 0f) return

    // Rays: a quick expanding ring, gone by the time the star settles. Drawn
    // first so the star sits on top of them.
    val rayLife = (progress / RAY_FRACTION).coerceIn(0f, 1f)
    if (rayLife < 1f) {
        val reach = r * (0.7f + 1.5f * rayLife)
        val fade = (1f - rayLife) * 0.8f
        for (k in 0 until RAYS) {
            val a = (2.0 * PI * k / RAYS).toFloat() + RAY_TILT
            val dir = Offset(cos(a), sin(a))
            drawLine(
                StarYellow.copy(alpha = fade),
                c + dir * (reach * 0.55f),
                c + dir * reach,
                strokeWidth = 2f,
            )
        }
    }

    // Overshoot: past 1.0 and settling back, so it reads as an arrival rather
    // than a fade-in.
    val settle = 1f + OVERSHOOT * sin(PI.toFloat() * progress) * (1f - progress)
    val alpha = progress.coerceIn(0f, 1f)
    scale(settle, settle, pivot = c) {
        drawStarPath(c, r * 0.72f, StarYellow.copy(alpha = alpha), filled = true)
    }
}

/**
 * A five-pointed star as ten alternating radii.
 *
 * Drawn rather than typed, because the `★` glyph is a font's opinion — it
 * differs across devices and cannot be made to burst.
 */
private fun DrawScope.drawStarPath(c: Offset, r: Float, color: Color, filled: Boolean) {
    val path = androidx.compose.ui.graphics.Path()
    for (k in 0 until 10) {
        val radius = if (k % 2 == 0) r else r * INNER_RATIO
        val a = (2.0 * PI * k / 10.0).toFloat() - PI.toFloat() / 2f
        val p = c + Offset(cos(a) * radius, sin(a) * radius)
        if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    if (filled) drawPath(path, color) else drawPath(path, color, style = Stroke(1.6f))
}

private operator fun Offset.times(f: Float) = Offset(x * f, y * f)

/** Rotation applied to the ray ring so it never lines up with the star's points. */
private const val RAY_TILT = 0.31f
private const val RAYS = 8

/** Fraction of the landing during which rays are visible. */
private const val RAY_FRACTION = 0.55f

/** How far past full size a star punches before settling. */
private const val OVERSHOOT = 0.38f

private const val INNER_RATIO = 0.44f

/** The wait between stars — three arrivals, not one. */
private const val STAGGER_MS = 190L

private val ARRIVAL = androidx.compose.animation.core.tween<Float>(durationMillis = 420)

private val STAR_BOX = 38.dp
private val GAP = 8.dp
