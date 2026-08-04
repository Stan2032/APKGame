package io.github.stan2032.gravitygambit.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stan2032.gravitygambit.core.Profile
import io.github.stan2032.gravitygambit.core.Seeds
import io.github.stan2032.gravitygambit.ui.AccentCyan
import io.github.stan2032.gravitygambit.ui.AccentPurple
import io.github.stan2032.gravitygambit.ui.GameLaunch
import io.github.stan2032.gravitygambit.ui.Nav
import io.github.stan2032.gravitygambit.ui.PanelTranslucent
import io.github.stan2032.gravitygambit.ui.StarYellow
import io.github.stan2032.gravitygambit.ui.TextDim
import io.github.stan2032.gravitygambit.ui.TextPrimary
import io.github.stan2032.gravitygambit.core.GameMode
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun HomeScreen(profile: Profile, onNavigate: (Nav) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Text("GRAVITY", fontSize = 40.sp, fontWeight = FontWeight.Black, color = TextPrimary, letterSpacing = 8.sp)
        Text("GAMBIT", fontSize = 40.sp, fontWeight = FontWeight.Black, color = AccentCyan, letterSpacing = 8.sp)
        Spacer(Modifier.height(6.dp))
        Text("slingshot artillery in living star systems", color = TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(28.dp))

        MenuEntry(
            icon = "🚀", title = "Campaign",
            note = "★ ${profile.totalStars} collected",
        ) { onNavigate(Nav.Campaign) }

        MenuEntry(
            icon = "🌌", title = "Daily Challenge",
            note = if (profile.dailyStreak > 0) "🔥 streak: ${profile.dailyStreak}" else "same seed for everyone, every day",
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

        MenuEntry(
            icon = "⚔️", title = "Duel",
            note = "PVP — hot-seat or challenge codes (${profile.duelWins}W/${profile.duelLosses}L)",
        ) { onNavigate(Nav.Duel) }

        MenuEntry(
            icon = "✨", title = "Outfitter",
            note = "✦ ${profile.stardust} stardust",
        ) { onNavigate(Nav.Shop) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallEntry("How to play", Modifier.weight(1f)) { onNavigate(Nav.Help) }
            SmallEntry("Settings", Modifier.weight(1f)) { onNavigate(Nav.Settings) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MenuEntry(icon: String, title: String, note: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = PanelTranslucent),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 26.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(note, fontSize = 13.sp, color = TextDim)
            }
        }
    }
}

@Composable
private fun SmallEntry(title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = PanelTranslucent),
    ) {
        Text(
            title,
            Modifier.padding(14.dp).fillMaxWidth(),
            fontSize = 15.sp,
            color = AccentPurple,
            fontWeight = FontWeight.Medium,
        )
    }
}
