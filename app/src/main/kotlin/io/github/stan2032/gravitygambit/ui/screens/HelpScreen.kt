package io.github.stan2032.gravitygambit.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stan2032.gravitygambit.ui.Nav
import io.github.stan2032.gravitygambit.ui.TextDim

@Composable
fun HelpScreen(onNavigate: (Nav) -> Unit) {
    ScreenFrame(title = "How to play", onBack = { onNavigate(Nav.Home) }) {
        HelpCard(
            "🎯 The basics",
            "Drag to aim, pull further for more power, release. Gravity does the rest. " +
                "Use the arrows to fine-tune, then FIRE.",
        )
        HelpCard(
            "🪐 Gravity is your weapon",
            "Whip around a planet and you steal its speed — the faster you arrive, the bigger " +
                "the Slingshot bonus. Skim a surface to Graze. Hit a drifting comet for a windfall.",
        )
        HelpCard(
            "🚀 Campaign",
            "Endless sectors, every one solvable. Break the core in 4 shots. One shot = ★★★.",
        )
        HelpCard(
            "🌌 Daily Challenge",
            "One system for everyone, every day. Five shots, moving target. Share the code — " +
                "it proves your score.",
        )
        HelpCard(
            "⚔️ Duels",
            "One phone, or text a code to a friend. Stations take 100 damage. " +
                "Friendly fire is on — be careful.",
        )
        HelpCard(
            "🧮 The math (for the curious)",
            "Two phones given the same shot compute exactly the same universe, down to the bit. " +
                "That is why nobody can fake a score: the physics is the referee.",
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
