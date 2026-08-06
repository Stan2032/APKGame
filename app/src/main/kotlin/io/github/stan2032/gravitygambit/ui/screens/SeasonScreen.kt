package io.github.stan2032.gravitygambit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stan2032.gravitygambit.core.Economy
import io.github.stan2032.gravitygambit.core.Profile
import io.github.stan2032.gravitygambit.core.ProjectileType
import io.github.stan2032.gravitygambit.core.Iap
import io.github.stan2032.gravitygambit.core.Season
import io.github.stan2032.gravitygambit.billing.BillingManager
import io.github.stan2032.gravitygambit.data.ProfileRepository
import io.github.stan2032.gravitygambit.ui.AccentCyan
import io.github.stan2032.gravitygambit.ui.DangerRed
import io.github.stan2032.gravitygambit.ui.Nav
import io.github.stan2032.gravitygambit.ui.OkGreen
import io.github.stan2032.gravitygambit.ui.PanelColor
import io.github.stan2032.gravitygambit.ui.StarYellow
import io.github.stan2032.gravitygambit.ui.TextDim
import io.github.stan2032.gravitygambit.ui.TextPrimary
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The season track (docs/DESIGN.md §The season track).
 *
 * The ladder is horizontal and scrolls to where the player actually is, because
 * a 50-tier list opened at tier 1 tells you nothing about your own progress.
 * Both lanes are shown at every tier whether or not the pass is owned — seeing
 * what the paid lane holds is the offer, and hiding it would make the purchase
 * a guess.
 */
@Composable
fun SeasonScreen(
    profile: Profile,
    repo: ProfileRepository,
    billing: BillingManager,
    onNavigate: (Nav) -> Unit,
) {
    val activity = LocalActivity.current
    val products by billing.products.collectAsState()
    val passProduct = products.firstOrNull { it.productId == Iap.SEASON_PASS }
    val today = remember { LocalDate.now(ZoneOffset.UTC).toEpochDay() }
    // Read through the rollover so a player returning after a month never sees
    // last season's tier sitting there.
    val p = remember(profile, today) { profile.rolledIntoSeason(today) }
    val tier = p.seasonTier
    val daysLeft = Season.daysLeft(today)
    var claimed by remember { mutableStateOf<List<Season.Reward>>(emptyList()) }

    val pendingFree = Season.claimableTiers(tier, false, p.seasonClaimedFree.toSet())
    val pendingPremium =
        if (p.seasonPassOwned) Season.claimableTiers(tier, true, p.seasonClaimedPremium.toSet())
        else emptyList()
    val pending = pendingFree.size + pendingPremium.size

    ScreenFrame("Season ${p.seasonId + 1}", onBack = { onNavigate(Nav.Home) }) {
        SectionCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Tier $tier",
                        color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("of ${Season.TIERS}", color = TextDim, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (daysLeft == 1) "1 day left" else "$daysLeft days left",
                        color = if (daysLeft <= 3) DangerRed else TextDim,
                        fontSize = 13.sp,
                        fontWeight = if (daysLeft <= 3) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                Spacer(Modifier.height(10.dp))
                XpBar(p.seasonXp, tier)
                Spacer(Modifier.height(6.dp))
                Subtitle(
                    if (tier >= Season.TIERS) "Track complete — every tier earned."
                    else "Play anything: campaign, the daily, or a duel. Everything counts."
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (pending > 0) {
            Button(
                onClick = { claimed = repo.claimSeason(today) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(
                    if (pending == 1) "Collect 1 reward" else "Collect $pending rewards",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        if (claimed.isNotEmpty()) {
            SectionCard {
                Column {
                    CardTitle("Collected")
                    Spacer(Modifier.height(6.dp))
                    for (r in claimed) {
                        Text("· ${rewardLabel(r)}", color = OkGreen, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // The offer is hidden when billing cannot serve it — an "Unlock" button
        // that does nothing is worse than no button (the same rule the shop
        // follows for every other section).
        if (!p.seasonPassOwned && passProduct != null && activity != null) {
            SectionCard {
                Column {
                    CardTitle("Season Pass")
                    Spacer(Modifier.height(4.dp))
                    Subtitle(
                        "Adds the second reward at every tier — shells, trails and " +
                            "longer aim traces. It pays out everything you have already " +
                            "reached the moment you buy it, and it is for this season only."
                    )
                    Spacer(Modifier.height(4.dp))
                    Subtitle(
                        "None of it can decide a duel: duels issue every shell and the " +
                            "longest trace to both players, always."
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { billing.launchPurchase(activity, passProduct) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(
                            "Unlock the pass · " +
                                passProduct.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty(),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        CardTitle("The ladder")
        Spacer(Modifier.height(8.dp))
        TierLadder(tier, p)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun XpBar(xp: Int, tier: Int) {
    val frac = Season.progressWithinTier(xp)
    val next = if (tier >= Season.TIERS) xp else Season.xpForTier(tier + 1)
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(PanelColor)
                .semantics {
                    contentDescription =
                        "Tier $tier, ${(frac * 100).toInt()} percent to the next tier"
                }
        ) {
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(AccentCyan, StarYellow)))
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (tier >= Season.TIERS) "$xp XP" else "$xp / $next XP",
            color = TextDim, fontSize = 12.sp,
        )
    }
}

/**
 * Horizontal ladder, auto-scrolled to the player's tier. Lazy because 50 tiers
 * x 2 lanes is 100 cells and none of the off-screen ones need to exist.
 */
@Composable
private fun TierLadder(tier: Int, p: Profile) {
    val state = rememberLazyListState()
    LaunchedEffect(tier) {
        state.scrollToItem((tier - 2).coerceAtLeast(0))
    }
    val claimedFree = p.seasonClaimedFree.toSet()
    val claimedPremium = p.seasonClaimedPremium.toSet()

    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items((1..Season.TIERS).toList()) { t ->
            val reached = t <= tier
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(76.dp),
            ) {
                Text(
                    "$t",
                    color = if (reached) StarYellow else TextDim,
                    fontSize = 13.sp,
                    fontWeight = if (t == tier) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.height(4.dp))
                RewardCell(Season.reward(t, premium = false), reached, t in claimedFree, false)
                Spacer(Modifier.height(6.dp))
                RewardCell(
                    Season.reward(t, premium = true),
                    reached && p.seasonPassOwned,
                    t in claimedPremium,
                    true,
                )
            }
        }
    }
}

@Composable
private fun RewardCell(
    reward: Season.Reward?,
    available: Boolean,
    collected: Boolean,
    premium: Boolean,
) {
    val border = if (premium) StarYellow else AccentCyan
    Box(
        Modifier
            .size(width = 76.dp, height = 58.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(
                when {
                    reward == null -> PanelColor.copy(alpha = 0.25f)
                    collected -> OkGreen.copy(alpha = 0.20f)
                    available -> border.copy(alpha = 0.22f)
                    else -> PanelColor.copy(alpha = 0.7f)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (reward == null) {
            Text("—", color = TextDim, fontSize = 12.sp)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    rewardGlyph(reward),
                    fontSize = 16.sp,
                    color = if (available || collected) TextPrimary else TextDim,
                )
                Text(
                    rewardShort(reward),
                    fontSize = 10.sp,
                    color = if (available || collected) TextPrimary else TextDim,
                )
                if (collected) Text("✓", fontSize = 10.sp, color = OkGreen)
            }
        }
    }
}

private fun rewardGlyph(r: Season.Reward): String = when (r.kind) {
    Season.RewardKind.STARDUST -> "✦"
    Season.RewardKind.TRAIL -> "≈"
    Season.RewardKind.PREVIEW_TIER -> "⌖"
    Season.RewardKind.PROJECTILE -> "◆"
}

private fun rewardShort(r: Season.Reward): String = when (r.kind) {
    Season.RewardKind.STARDUST -> "${r.amount}"
    Season.RewardKind.TRAIL -> Economy.trail(r.trailId).name
    Season.RewardKind.PREVIEW_TIER -> "Trace ${r.amount}"
    Season.RewardKind.PROJECTILE -> ProjectileType.byId(r.amount).displayName
}

private fun rewardLabel(r: Season.Reward): String = when (r.kind) {
    Season.RewardKind.STARDUST -> "✦ ${r.amount} stardust"
    Season.RewardKind.TRAIL -> "${Economy.trail(r.trailId).name} trail"
    Season.RewardKind.PREVIEW_TIER -> "Aim trace tier ${r.amount}"
    Season.RewardKind.PROJECTILE -> "${ProjectileType.byId(r.amount).displayName} shell"
}
