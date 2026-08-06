package io.github.stan2032.gravitygambit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stan2032.gravitygambit.core.Expedition
import io.github.stan2032.gravitygambit.core.GameMode
import io.github.stan2032.gravitygambit.core.Profile
import io.github.stan2032.gravitygambit.core.Season
import io.github.stan2032.gravitygambit.ui.AccentCyan
import io.github.stan2032.gravitygambit.ui.DangerRed
import io.github.stan2032.gravitygambit.ui.GameLaunch
import io.github.stan2032.gravitygambit.ui.Nav
import io.github.stan2032.gravitygambit.ui.OkGreen
import io.github.stan2032.gravitygambit.ui.PanelColor
import io.github.stan2032.gravitygambit.ui.StarYellow
import io.github.stan2032.gravitygambit.ui.TextDim
import io.github.stan2032.gravitygambit.ui.TextPrimary

/** The green a fuel gauge is until it isn't. */
private val FuelGreen = Color(0xFF7FE3B0)

/**
 * The Expedition junction — the screen a run lives on between systems.
 *
 * Everything here answers one question: *which way, and can I afford it?* So
 * the three route cards each state their cost and their payout in the same
 * units as the gauge above them, and the gauge shows what this leg will take
 * before it is taken. A run mode where the player discovers the price after
 * committing is a run mode that feels arbitrary.
 *
 * Rules all live in `:core` [Expedition]; this file decides nothing.
 */
@Composable
fun ExpeditionScreen(
    profile: Profile,
    run: Expedition.Run?,
    onStart: () -> Unit,
    onAbandon: () -> Unit,
    onTake: (Expedition.Leg) -> Unit,
    onNavigate: (Nav) -> Unit,
) {
    ScreenFrame(title = "Expedition", onBack = { onNavigate(Nav.Home) }) {
        when {
            run == null -> Briefing(profile, onStart)
            run.over -> RunOver(run, onStart, onAbandon)
            else -> Junction(run, onTake)
        }
    }
}

@Composable
private fun Briefing(profile: Profile, onStart: () -> Unit) {
    SectionCard {
        Column {
            CardTitle("One run, until the fuel runs out")
            Spacer(Modifier.height(8.dp))
            Subtitle(
                "A chain of systems, each harder than the last. Clearing one buys " +
                    "fuel for the next; travelling costs more the deeper you get. " +
                    "Nothing is saved mid-run — a run is a sitting."
            )
            Spacer(Modifier.height(10.dp))
            Subtitle(
                "Shoot tidily. Two shots instead of four is small change at the " +
                    "first junction and the whole run at the fifteenth."
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    if (profile.expeditionRuns > 0) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    CardTitle("Best: ${profile.expeditionBestDepth} systems")
                    Subtitle("${profile.expeditionRuns} runs · best score ${profile.expeditionBestScore}")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Launch") }
}

@Composable
private fun Junction(run: Expedition.Run, onTake: (Expedition.Leg) -> Unit) {
    FuelGauge(run)
    Spacer(Modifier.height(14.dp))
    Text(
        "Choose your route",
        color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(2.dp))
    Subtitle("Travel to the next system costs ${Expedition.travelCost(run.depth)} fuel, whichever way you go.")
    Spacer(Modifier.height(10.dp))
    for (leg in Expedition.routesFor(run)) {
        RouteCard(run, leg, onTake)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun FuelGauge(run: Expedition.Run) {
    // The gauge is relative to the tank you started with, not to a maximum
    // that does not exist — fuel can and should exceed START_FUEL on a good
    // run, and a bar pinned at "full" would hide exactly that.
    val frac = (run.fuel.toFloat() / Expedition.START_FUEL).coerceIn(0f, 1f)
    val thisLeg = Expedition.travelCost(run.depth) + Expedition.shotCost()
    val tight = run.fuel < thisLeg * 2
    SectionCard {
        Column(Modifier.semantics {
            contentDescription =
                "${run.fuel} fuel, ${run.depth} systems deep, score ${run.score}"
        }) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${run.fuel}",
                    color = if (tight) StarYellow else FuelGreen,
                    fontSize = 34.sp, fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.width(6.dp))
                Text("fuel", color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${run.depth} deep", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (run.wrecks > 0) "${run.score} pts · ${run.wrecks} wrecked" else "${run.score} pts",
                        color = TextDim, fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().height(7.dp)
                    .clip(RoundedCornerShape(4.dp)).background(PanelColor)
            ) {
                Box(
                    Modifier.fillMaxWidth(frac).height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (tight) StarYellow else FuelGreen)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (tight) "Getting there and firing once costs $thisLeg. This is the thin part."
                else "Getting there and firing once costs $thisLeg.",
                color = if (tight) StarYellow else TextDim, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun RouteCard(run: Expedition.Run, leg: Expedition.Leg, onTake: (Expedition.Leg) -> Unit) {
    val accent = when (leg.route) {
        Expedition.Route.SAFE -> AccentCyan
        Expedition.Route.RICH -> Color(0xFFFF8A65)
        Expedition.Route.UNKNOWN -> StarYellow
    }
    // What a clear is worth here, stated at both ends of the plausible range
    // rather than as one number, because the range IS the decision.
    val low = Expedition.refundFor(leg, 1)
    val high = Expedition.refundFor(leg, 3)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PanelColor)
            .clickable { onTake(leg) }
            .heightIn(min = 96.dp)
            .padding(horizontal = 16.dp, vertical = 13.dp)
            .semantics {
                contentDescription = "${leg.route.label}. ${leg.route.brief}. " +
                    "Difficulty ${leg.difficulty}. Returns $low to $high fuel." +
                    if (leg.mutator != Expedition.Mutator.NONE) " Condition: ${leg.mutator.displayName}, ${leg.mutator.brief}." else ""
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(leg.route.label, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("+$low–$high fuel", color = FuelGreen, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Subtitle(leg.route.brief)
            Spacer(Modifier.weight(1f))
            DifficultyPips(leg.difficulty, accent)
        }
        if (leg.mutator != Expedition.Mutator.NONE) {
            Spacer(Modifier.height(9.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(accent.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(leg.mutator.displayName, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(leg.mutator.brief, color = TextDim, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Difficulty as pips rather than a number: 0..11 is the generator's scale,
 * not a scale anybody reads, and what the player needs is "harder than the
 * last one" at a glance.
 */
@Composable
private fun DifficultyPips(difficulty: Int, accent: Color) {
    val filled = (difficulty * 5 / 11).coerceIn(0, 5) + 1
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 6) {
            Box(
                Modifier.width(if (i < filled) 8.dp else 6.dp).height(if (i < filled) 8.dp else 6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (i < filled) accent else TextDim.copy(alpha = 0.28f))
            )
        }
    }
}

@Composable
private fun RunOver(run: Expedition.Run, onStart: () -> Unit, onAbandon: () -> Unit) {
    SectionCard {
        Column {
            Text(
                if (run.depth == 0) "Out of fuel before you began" else "The tank is empty",
                color = TextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Subtitle(
                if (run.depth >= 10) "You went a long way out. Nothing was coming back from there."
                else "Every run ends. The question is how far out."
            )
            Spacer(Modifier.height(16.dp))
            Stat("Systems cleared", "${run.depth - run.wrecks}")
            if (run.wrecks > 0) Stat("Wrecked", "${run.wrecks}", DangerRed)
            Stat("Score", "${run.score}")
            Stat("Stars taken", "${run.starsEarned}", StarYellow)
            Spacer(Modifier.height(10.dp))
            Stat("Stardust earned", "${Expedition.payout(run)}", OkGreen)
            Stat("Season XP", "${Season.expeditionXp(run.depth, run.wrecks)}", OkGreen)
        }
    }
    Spacer(Modifier.height(14.dp))
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Run again") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onAbandon, modifier = Modifier.fillMaxWidth()) { Text("Done") }
}

@Composable
private fun Stat(label: String, value: String, color: Color = TextPrimary) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = TextDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The launch for one leg — an ordinary campaign match, per D-34. */
fun expeditionLaunch(leg: Expedition.Leg, nonce: Long): GameLaunch = GameLaunch(
    mode = GameMode.CAMPAIGN,
    seed = leg.seed,
    difficulty = leg.difficulty,
    // Sector/level -1 keeps the leg out of the campaign star map: an
    // Expedition clear is not a campaign clear, and writing one there would
    // hand out sector unlocks for systems the player never found on the grid.
    sector = -1,
    level = -1,
    expeditionLeg = leg,
    nonce = nonce,
)
