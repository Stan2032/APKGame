package io.github.stan2032.gravitygambit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stan2032.gravitygambit.core.GameMode
import io.github.stan2032.gravitygambit.core.Profile
import io.github.stan2032.gravitygambit.core.Seeds
import io.github.stan2032.gravitygambit.ui.AccentCyan
import io.github.stan2032.gravitygambit.ui.AccentPurple
import io.github.stan2032.gravitygambit.ui.GameLaunch
import io.github.stan2032.gravitygambit.core.Season
import io.github.stan2032.gravitygambit.ui.Nav
import io.github.stan2032.gravitygambit.ui.StarYellow
import io.github.stan2032.gravitygambit.ui.TextDim
import io.github.stan2032.gravitygambit.ui.TextPrimary
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Home is the first thing anyone sees, so it does not use stock Material
 * cards: each entry is a hairline-bordered slate with its own drawn orbit
 * glyph, sitting over the live starfield. The look should belong to this
 * game and no other (docs/AI_LOOK_AVOIDANCE.md).
 */
@Composable
fun HomeScreen(profile: Profile, onNavigate: (Nav) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(44.dp))
        Text("GRAVITY", fontSize = 42.sp, fontWeight = FontWeight.Black, color = TextPrimary, letterSpacing = 9.sp)
        Text("GAMBIT", fontSize = 42.sp, fontWeight = FontWeight.Black, color = AccentCyan, letterSpacing = 9.sp)
        Spacer(Modifier.height(30.dp))

        Entry(
            glyph = Glyph.ORBIT,
            title = "Campaign",
            detail = if (profile.totalStars > 0) "${profile.totalStars} stars" else "Start the first sector",
            accent = AccentCyan,
        ) { onNavigate(Nav.Campaign) }

        Entry(
            glyph = Glyph.COMET,
            title = "Daily Challenge",
            detail = if (profile.dailyStreak > 0) "${profile.dailyStreak}-day streak" else "One system, everyone, today",
            accent = StarYellow,
        ) {
            val day = LocalDate.now(ZoneOffset.UTC).toEpochDay()
            onNavigate(
                Nav.Game(
                    GameLaunch(
                        mode = GameMode.DAILY,
                        seed = Seeds.daily(day),
                        difficulty = Seeds.DAILY_DIFFICULTY,
                        epochDay = day,
                    )
                )
            )
        }

        Entry(
            glyph = Glyph.DUEL,
            title = "Duel",
            detail = if (profile.duelWins + profile.duelLosses > 0)
                "${profile.duelWins}W · ${profile.duelLosses}L"
            else "Play a friend",
            accent = Color(0xFFFF8A65),
        ) { onNavigate(Nav.Duel) }

        // Expedition sits under the three fixed modes: it is the one you
        // reach for once the campaign has taught you to aim, and the detail
        // line is a personal best rather than a count, because depth is the
        // number a run player actually carries around.
        Entry(
            glyph = Glyph.ROUTE,
            title = "Expedition",
            detail = if (profile.expeditionRuns > 0)
                "Best: ${profile.expeditionBestDepth} systems deep"
            else "Chain systems until the fuel runs out",
            accent = Color(0xFF7FE3B0),
        ) { onNavigate(Nav.Expedition) }

        // The season sits above the shop: it is the thing that keeps paying,
        // and it should be the thing a returning player sees first.
        val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay()
        val season = profile.rolledIntoSeason(today)
        Entry(
            glyph = Glyph.TRACK,
            title = "Season ${season.seasonId + 1}",
            detail = "Tier ${season.seasonTier} of ${Season.TIERS} · " +
                "${Season.daysLeft(today)} days left",
            accent = StarYellow,
        ) { onNavigate(Nav.Season) }

        Spacer(Modifier.height(10.dp))

        Entry(
            glyph = Glyph.SPARK,
            title = "Outfitter",
            detail = "${profile.stardust} stardust",
            accent = AccentPurple,
        ) { onNavigate(Nav.Shop) }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            QuietLink("How to play") { onNavigate(Nav.Help) }
            Spacer(Modifier.width(26.dp))
            QuietLink("Settings") { onNavigate(Nav.Settings) }
        }
        Spacer(Modifier.height(28.dp))
    }
}

private enum class Glyph { ORBIT, COMET, DUEL, SPARK, TRACK, ROUTE }

@Composable
private fun Entry(
    glyph: Glyph,
    title: String,
    detail: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.13f), Color(0xCC121A30), Color(0xCC121A30))
                )
            )
            .border(1.dp, accent.copy(alpha = 0.32f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 74.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphMark(glyph, accent)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(detail, fontSize = 12.sp, color = TextDim)
        }
        Text("›", fontSize = 22.sp, color = accent.copy(alpha = 0.75f))
    }
}

/** Small drawn marks — the game's own iconography rather than emoji. */
@Composable
private fun GlyphMark(glyph: Glyph, accent: Color) {
    Canvas(Modifier.size(30.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        when (glyph) {
            Glyph.ORBIT -> {
                drawCircle(accent.copy(alpha = 0.55f), r * 0.92f, c, style = Stroke(1.6f))
                drawCircle(StarYellow, r * 0.26f, c)
                drawCircle(accent, r * 0.15f, c + Offset(r * 0.92f, 0f))
            }
            Glyph.COMET -> {
                for (k in 1..6) {
                    val f = k / 6f
                    drawCircle(
                        accent.copy(alpha = (1f - f) * 0.6f),
                        r * 0.24f * (1f - f * 0.6f),
                        c + Offset(-r * 0.85f * f, r * 0.7f * f),
                    )
                }
                drawCircle(Color.White, r * 0.26f, c + Offset(r * 0.18f, -r * 0.16f))
            }
            Glyph.DUEL -> {
                drawCircle(accent, r * 0.24f, c + Offset(-r * 0.62f, r * 0.42f))
                drawCircle(accent.copy(alpha = 0.75f), r * 0.24f, c + Offset(r * 0.62f, -r * 0.5f))
                drawArc(
                    color = accent.copy(alpha = 0.5f),
                    startAngle = 150f, sweepAngle = 220f, useCenter = false,
                    topLeft = Offset(c.x - r * 0.7f, c.y - r * 0.7f),
                    size = androidx.compose.ui.geometry.Size(r * 1.4f, r * 1.4f),
                    style = Stroke(1.5f),
                )
            }
            Glyph.SPARK -> {
                for (i in 0 until 4) {
                    val a = Math.PI.toFloat() / 2f * i + 0.4f
                    val dx = kotlin.math.cos(a) * r * 0.95f
                    val dy = kotlin.math.sin(a) * r * 0.95f
                    drawLine(accent.copy(alpha = 0.7f), c - Offset(dx, dy), c + Offset(dx, dy), 1.5f)
                }
                drawCircle(Color.White, r * 0.22f, c)
            }
            // A ladder climbing away: rungs that shorten with distance, which
            // is what a track of 50 tiers looks like from tier one.
            Glyph.TRACK -> {
                for (i in 0 until 4) {
                    val t = i / 3f
                    val y = c.y + r * (0.75f - 1.5f * t)
                    val half = r * (0.85f - 0.45f * t)
                    drawLine(
                        accent.copy(alpha = 0.35f + 0.55f * t),
                        Offset(c.x - half, y), Offset(c.x + half, y), 2f,
                    )
                }
                drawCircle(Color.White, r * 0.16f, Offset(c.x, c.y - r * 0.75f))
            }
            // A path that forks: one line in, three out. The whole decision a
            // run keeps asking, drawn once.
            Glyph.ROUTE -> {
                val hub = Offset(c.x - r * 0.55f, c.y)
                drawLine(accent.copy(alpha = 0.55f), Offset(c.x - r, c.y), hub, 2f)
                for (i in 0 until 3) {
                    val dy = r * (i - 1) * 0.72f
                    drawLine(
                        accent.copy(alpha = if (i == 1) 0.85f else 0.4f),
                        hub, Offset(c.x + r * 0.72f, c.y + dy), 1.6f,
                    )
                    drawCircle(
                        if (i == 1) Color.White else accent.copy(alpha = 0.7f),
                        r * (if (i == 1) 0.17f else 0.12f),
                        Offset(c.x + r * 0.8f, c.y + dy),
                    )
                }
                drawCircle(StarYellow, r * 0.15f, hub)
            }
        }
    }
}

@Composable
private fun QuietLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = TextDim,
        fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}
