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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stan2032.gravitygambit.core.Economy
import io.github.stan2032.gravitygambit.core.GameMode
import io.github.stan2032.gravitygambit.core.Profile
import io.github.stan2032.gravitygambit.core.Seeds
import io.github.stan2032.gravitygambit.ui.GameLaunch
import io.github.stan2032.gravitygambit.ui.Nav
import io.github.stan2032.gravitygambit.ui.PanelColorLight
import io.github.stan2032.gravitygambit.ui.StarYellow
import io.github.stan2032.gravitygambit.ui.TextDim
import io.github.stan2032.gravitygambit.ui.TextPrimary

@Composable
fun CampaignScreen(profile: Profile, onNavigate: (Nav) -> Unit) {
    ScreenFrame(title = "Campaign", onBack = { onNavigate(Nav.Home) }) {
        Subtitle(
            "Endless procedurally generated sectors. Every level is solvable — the " +
                "generator proves it by simulation before you ever see it. " +
                "Fewer shots, more stars."
        )

        val sectorsToShow = highestReachableSector(profile) + 1
        for (sector in 0 until sectorsToShow) {
            SectorCard(profile, sector, onNavigate)
        }
    }
}

private fun sectorStars(profile: Profile, sector: Int): Int =
    (0 until Seeds.LEVELS_PER_SECTOR).sumOf { profile.starsFor(sector, it) }

private fun sectorUnlocked(profile: Profile, sector: Int): Boolean =
    sector == 0 || sectorStars(profile, sector - 1) >= Economy.SECTOR_UNLOCK_STARS

private fun highestReachableSector(profile: Profile): Int {
    var s = 0
    while (sectorUnlocked(profile, s + 1)) s++
    return s
}

@Composable
private fun SectorCard(profile: Profile, sector: Int, onNavigate: (Nav) -> Unit) {
    val unlocked = sectorUnlocked(profile, sector)
    val stars = sectorStars(profile, sector)
    SectionCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle("Sector ${sector + 1}")
                Spacer(Modifier.weight(1f))
                Text("★ $stars / ${3 * Seeds.LEVELS_PER_SECTOR}", color = StarYellow, fontSize = 14.sp)
            }
            if (!unlocked) {
                Spacer(Modifier.height(6.dp))
                Subtitle("Locked — earn ${Economy.SECTOR_UNLOCK_STARS}★ in Sector $sector to open.")
            } else {
                Spacer(Modifier.height(10.dp))
                for (rowStart in 0 until Seeds.LEVELS_PER_SECTOR step 4) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (level in rowStart until minOf(rowStart + 4, Seeds.LEVELS_PER_SECTOR)) {
                            LevelCell(profile, sector, level, Modifier.weight(1f), onNavigate)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelCell(
    profile: Profile,
    sector: Int,
    level: Int,
    modifier: Modifier,
    onNavigate: (Nav) -> Unit,
) {
    val stars = profile.starsFor(sector, level)
    val playable = level == 0 || profile.starsFor(sector, level - 1) > 0
    val bg = if (playable) PanelColorLight else PanelColorLight.copy(alpha = 0.35f)
    Box(
        modifier = modifier
            .height(52.dp)
            .background(bg, RoundedCornerShape(10.dp))
            .clickable(enabled = playable) {
                onNavigate(
                    Nav.Game(
                        GameLaunch(
                            mode = GameMode.CAMPAIGN,
                            seed = Seeds.campaign(sector, level),
                            difficulty = Seeds.campaignDifficulty(sector, level),
                            sector = sector,
                            level = level,
                        )
                    )
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${level + 1}",
                color = if (playable) TextPrimary else TextDim,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Text(
                when (stars) {
                    0 -> if (playable) "· · ·" else "🔒"
                    1 -> "★☆☆"
                    2 -> "★★☆"
                    else -> "★★★"
                },
                color = if (stars > 0) StarYellow else TextDim,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
