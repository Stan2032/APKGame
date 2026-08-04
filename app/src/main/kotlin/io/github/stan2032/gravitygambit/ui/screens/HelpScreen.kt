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
            "Drag anywhere and release the slingshot: pull back to aim, further pull = more power. " +
                "Your shot obeys real N-body gravity — every star and planet bends its path. " +
                "Use the nudge buttons for one-tenth-degree precision, then FIRE.",
        )
        HelpCard(
            "🪐 Gravity is your weapon",
            "Straight shots are for amateurs. Curve around planets for a slingshot boost. " +
                "Skim a surface for a Graze bonus, fire away from the target and still hit for a " +
                "Trick Shot, or ride a long orbit for a Long Shot. Style pays stardust.",
        )
        HelpCard(
            "🚀 Campaign",
            "Endless generated sectors, guaranteed solvable, difficulty measured by simulation. " +
                "Destroy the target core within 4 shots. 1 shot = ★★★.",
        )
        HelpCard(
            "🌌 Daily Challenge",
            "One seed for the whole world each UTC day. Five shots, the target relocates after " +
                "every hit. Share your score — the code includes a mathematical proof of your run.",
        )
        HelpCard(
            "⚔️ Duels",
            "Hot-seat on one phone, or challenge codes over any messenger — no servers, no accounts. " +
                "Each code replays and verifies the whole match on your device; forged scores are " +
                "physically impossible. Stations have 100 HP. Friendly fire is on. Be careful.",
        )
        HelpCard(
            "🧮 The math (for the curious)",
            "The simulation is bit-for-bit deterministic: quantized inputs, IEEE-754 arithmetic, " +
                "and a 64-bit trajectory hash. Two phones given the same shot always compute " +
                "exactly the same universe. That's why PVP needs no server: the physics is the referee.",
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Tip: the RETRO projectile's brake burn is recorded at the exact tick you press it — " +
                "your reflexes become part of the replay.",
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
