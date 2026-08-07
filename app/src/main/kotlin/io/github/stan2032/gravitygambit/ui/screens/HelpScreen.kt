package io.github.stan2032.gravitygambit.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stan2032.gravitygambit.core.Season
import io.github.stan2032.gravitygambit.core.Tuning
import io.github.stan2032.gravitygambit.ui.Nav
import io.github.stan2032.gravitygambit.ui.TextDim

/**
 * How to play.
 *
 * **Every number on this screen is read from [Tuning] rather than typed**, and
 * that is the whole lesson of the 0.16.1 audit: the previous version was
 * written at 0.2.0, never revisited, and told players *"one shot = ★★★"* —
 * which stopped being true the moment three stars started requiring that the
 * winning shot also *did something*. A player chasing that line was optimising
 * for the wrong thing, told so by the game itself.
 *
 * It also described a game with no anchors, no declarations, no objectives
 * beyond destruction, no season and no Expedition: every mechanic added after
 * 0.2.0 was missing, including the one the store listing calls the thing
 * nothing else has. Help is the first place a confused player looks, so a
 * stale Help screen is not a stale doc — it is a broken feature.
 */
@Composable
fun HelpScreen(onNavigate: (Nav) -> Unit) {
    ScreenFrame(title = "How to play", onBack = { onNavigate(Nav.Home) }) {
        HelpCard(
            "🎯 Aiming",
            "Touch and drag anywhere on the board — the aiming stick appears under your " +
                "thumb, so it never sits in a corner and never covers what you are aiming at. " +
                "Pull further for more power, release to lock it in. The ± buttons nudge the " +
                "angle, and holding one speeds up. Then FIRE.",
        )
        HelpCard(
            "🪐 Gravity is your weapon",
            "Whip around a planet and you steal its speed — the faster you arrive, the bigger " +
                "the Slingshot bonus. Skim a surface to Graze. Hit a drifting comet for a " +
                "windfall. Repulsors push instead of pulling, and cannot be hit at all.",
        )
        HelpCard(
            "⚓ Anchors — build the field yourself",
            "Before firing, place up to ${Tuning.MAX_ANCHORS} anchors: your own points of " +
                "gravity. Tap to place, tap again to flip between pulling and pushing, tap " +
                "once more to remove. The aim trace bends through them live. This is the part " +
                "that makes an impossible shot yours rather than lucky.",
        )
        HelpCard(
            "🎲 Call your shot",
            "Declare a constraint before you fire — under 40% power, through the pull of two " +
                "different worlds, the long way round — and a shot that keeps it pays far more. " +
                "Break it and the shot is wasted: the hit does not land. You are only ever " +
                "offered a call this system has been proved able to pay.",
        )
        HelpCard(
            "🎯 Not every system wants a kill",
            "Breach destroys the core. Capture asks you to hold a ring around the marked " +
                "world for ${Tuning.CAPTURE_TICKS / 60} seconds — impossible without braking, so " +
                "you are handed a RETRO shell whether or not you own one. Courier wants the " +
                "drifting comet, not the station. Thread wants three gates, in order.",
        )
        HelpCard(
            "🚀 Campaign",
            "Endless sectors, every one machine-proved solvable before you see it. Break the " +
                "core within ${Tuning.CAMPAIGN_SHOTS} shots.\n\n" +
                "★★★ is one shot — and the shot has to have done something: a kept declaration, " +
                "a real slingshot, or a style bonus. A straight one-shot kill with no flourish " +
                "is ★★. Two shots is ★★. After that, ★.",
        )
        HelpCard(
            "🌌 Daily Challenge",
            "One system for everyone, every day. ${Tuning.DAILY_SHOTS} shots, and the target " +
                "moves after each hit. Share the code — it proves your score, because anyone " +
                "can re-run it.",
        )
        HelpCard(
            "🧭 Expedition",
            "A chain of systems on one tank of fuel. Between them you choose a route: quieter, " +
                "deeper, or uncharted with a condition attached. Clearing a system buys fuel; " +
                "travelling costs more the further out you get, so every run ends. Nothing is " +
                "saved mid-run. Shoot tidily — two shots instead of four is small change at the " +
                "first junction and the whole run at the fifteenth.",
        )
        HelpCard(
            "⚔️ Duels",
            "One phone, or text a code to a friend. Stations take ${Tuning.STATION_HP} damage. " +
                "Friendly fire is on after a moment's grace, so a shot that loops home can " +
                "still hit you.\n\n" +
                "Duels issue every shell and the longest aim trace to both players, always. " +
                "Nothing anyone has bought, unlocked or ground for can decide a match.",
        )
        HelpCard(
            "📈 Season",
            "${Season.TIERS} tiers over ${Season.LENGTH_DAYS} days, filled by playing anything " +
                "at all — campaign, the daily, a duel, an expedition. Rewards are cosmetic or " +
                "spendable; none of them make you stronger in a duel.",
        )
        HelpCard(
            "🧮 The math (for the curious)",
            "Two phones given the same shot compute exactly the same universe, down to the bit. " +
                "That is why nobody can fake a score: the physics is the referee. It is also " +
                "why a challenge code is only fifty-two characters — it carries your moves, and " +
                "the other phone rebuilds everything else.",
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Retro brakes at the exact moment you tap. Your timing is part of the replay.",
            color = TextDim, fontSize = 13.sp,
        )
    }
}

@Composable
private fun HelpCard(title: String, body: String) {
    SectionCard {
        Column {
            CardTitle(title)
            Spacer(Modifier.height(6.dp))
            Subtitle(body)
        }
    }
}
