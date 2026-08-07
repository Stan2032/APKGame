package io.github.stan2032.gravitygambit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgColor = Color(0xFF070B18)
val PanelColor = Color(0xFF121A30)
val PanelColorLight = Color(0xFF1B2544)

/** Menu surfaces sit over the ambient starfield, so they let a little sky through. */
val PanelTranslucent = Color(0xE6121A30)
val PanelTranslucentLight = Color(0xE61B2544)
val AccentCyan = Color(0xFF4DD0E1)
val AccentPurple = Color(0xFF9575FF)
val StarYellow = Color(0xFFFFD54F)
val DangerRed = Color(0xFFFF5370)
val OkGreen = Color(0xFF69F0AE)
val TextPrimary = Color(0xFFE8EAF6)
val TextDim = Color(0xFF8E9AC6)

/**
 * Wreckage left by a shot that died on a world (VISION §3.3). Deliberately the
 * only thing on the board that emits nothing: every other body glows, pulses or
 * catches a rim light, so a cold grey silhouette reads instantly as spent
 * matter. That is the honest picture — debris blocks but is massless, so it
 * must not look like a gravity source (D-39).
 */
val DebrisCore = Color(0xFF3A4058)
val DebrisRim = Color(0xFF5C6488)

/** Planet render palettes indexed by Body.palette: (surface, shadow). */
val BodyPalettes = listOf(
    Pair(Color(0xFFFFD54F), Color(0xFFFF8F00)),  // 0 star warm
    Pair(Color(0xFFFFE082), Color(0xFFFF6F00)),  // 1 star alt
    Pair(Color(0xFF81D4FA), Color(0xFF01579B)),  // 2 ice
    Pair(Color(0xFFA5D6A7), Color(0xFF1B5E20)),  // 3 verdant
    Pair(Color(0xFFFFAB91), Color(0xFFBF360C)),  // 4 rust
    Pair(Color(0xFFCE93D8), Color(0xFF4A148C)),  // 5 violet
    Pair(Color(0xFFBCAAA4), Color(0xFF3E2723)),  // 6 rock
    Pair(Color(0xFFFF5370), Color(0xFF880E4F)),  // 7 repulsor
    Pair(Color(0xFFB388FF), Color(0xFF311B92)),  // 8 pulsar
    Pair(Color(0xFF80DEEA), Color(0xFF006064)),  // 9 comet
)

@Composable
fun GravityGambitTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = AccentCyan,
        onPrimary = Color(0xFF002B31),
        secondary = AccentPurple,
        onSecondary = Color(0xFF1D0F4F),
        background = BgColor,
        onBackground = TextPrimary,
        surface = PanelColor,
        onSurface = TextPrimary,
        surfaceVariant = PanelColorLight,
        onSurfaceVariant = TextDim,
        error = DangerRed,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
