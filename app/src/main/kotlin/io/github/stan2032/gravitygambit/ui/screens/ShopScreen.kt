package io.github.stan2032.gravitygambit.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stan2032.gravitygambit.ads.RewardedAdsProvider
import io.github.stan2032.gravitygambit.billing.BillingManager
import io.github.stan2032.gravitygambit.core.AdRewards
import io.github.stan2032.gravitygambit.core.Economy
import io.github.stan2032.gravitygambit.core.Iap
import io.github.stan2032.gravitygambit.core.Profile
import io.github.stan2032.gravitygambit.core.ProjectileType
import io.github.stan2032.gravitygambit.data.ProfileRepository
import io.github.stan2032.gravitygambit.ui.Nav
import io.github.stan2032.gravitygambit.ui.OkGreen
import io.github.stan2032.gravitygambit.ui.StarYellow
import io.github.stan2032.gravitygambit.ui.TextDim
import io.github.stan2032.gravitygambit.ui.TextPrimary

@Composable
fun ShopScreen(
    profile: Profile,
    repo: ProfileRepository,
    billing: BillingManager,
    ads: RewardedAdsProvider,
    onNavigate: (Nav) -> Unit,
) {
    ScreenFrame(title = "Outfitter", onBack = { onNavigate(Nav.Home) }) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✦ ${profile.stardust}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = StarYellow)
                Spacer(Modifier.weight(1f))
                Text("★ ${profile.totalStars} campaign stars", color = TextDim, fontSize = 13.sp)
            }
        }

        SectionCard {
            Column {
                CardTitle("Projectiles")
                Subtitle("Earn with stars and stardust. Usable everywhere.")
                Spacer(Modifier.height(8.dp))
                for (unlock in Economy.PROJECTILE_UNLOCKS) {
                    val type = ProjectileType.byId(unlock.typeId)
                    val owned = profile.unlockedProjectiles.contains(unlock.typeId)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(type.displayName, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                projectileBlurb(type),
                                color = TextDim, fontSize = 12.sp,
                            )
                        }
                        if (owned) {
                            Text("OWNED", color = OkGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            val canByStars = profile.totalStars >= unlock.starsRequired
                            val canByDust = profile.stardust >= unlock.cost
                            Button(
                                enabled = canByStars && canByDust,
                                onClick = {
                                    repo.spend(unlock.cost) { p ->
                                        p.copy(unlockedProjectiles = (p.unlockedProjectiles + unlock.typeId).distinct())
                                    }
                                },
                            ) {
                                Text(if (canByStars) "✦ ${unlock.cost}" else "★ ${unlock.starsRequired} needed", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        SectionCard {
            Column {
                CardTitle("Targeting computer")
                Subtitle("See further along your shot before firing.")
                Spacer(Modifier.height(8.dp))
                val level = profile.previewLevel
                Text(
                    "Current trace: ${Economy.previewTicks(level)} ticks",
                    color = TextPrimary, fontSize = 14.sp,
                )
                if (level < 2) {
                    val cost = Economy.PREVIEW_UPGRADE_COSTS[level]
                    Spacer(Modifier.height(6.dp))
                    Button(
                        enabled = profile.stardust >= cost,
                        onClick = { repo.spend(cost) { p -> p.copy(previewLevel = p.previewLevel + 1) } },
                    ) { Text("Upgrade — ✦ $cost") }
                }
            }
        }

        SectionCard {
            Column {
                CardTitle("Projectile trails")
                // Was "Your rival sees these in the replay." That is false:
                // GameScreen draws an opponent's replay with the *classic*
                // trail, because a share code carries (seed, turns, hash) and
                // nothing else — the same limit that killed Blind (D-23) and
                // forced duel normalization (D-29). The code carries moves,
                // not the shopping. Selling a cosmetic on a promise the
                // protocol cannot keep is the worst place in the app to be
                // wrong (D-36).
                Subtitle("Yours, on your screen — every shot you fly.")
                Spacer(Modifier.height(8.dp))
                for (trail in Economy.TRAILS) {
                    val owned = profile.ownedTrails.contains(trail.id)
                    val selected = profile.selectedTrail == trail.id
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            for (c in trail.colors.take(5)) {
                                Spacer(
                                    Modifier
                                        .padding(end = 4.dp)
                                        .size(14.dp)
                                        .background(Color(c), CircleShape)
                                )
                            }
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text(trail.name, color = TextPrimary)
                        }
                        when {
                            selected -> Text("EQUIPPED", color = OkGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            owned -> OutlinedButton(onClick = {
                                repo.update { it.copy(selectedTrail = trail.id) }
                            }) { Text("Equip", fontSize = 12.sp) }
                            else -> Button(
                                enabled = profile.stardust >= trail.cost,
                                onClick = {
                                    repo.spend(trail.cost) { p ->
                                        p.copy(ownedTrails = (p.ownedTrails + trail.id).distinct())
                                    }
                                },
                            ) { Text("✦ ${trail.cost}", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }

        val activity = LocalActivity.current
        val adsAvailable by ads.available.collectAsState()

        // Opt-in rewarded ad: visible ONLY when an ad is actually loaded, and
        // never for Ad Pass holders (they get boosts automatically).
        if (adsAvailable && !profile.adPassActive && activity != null) {
            SectionCard {
                Column {
                    CardTitle("Free stardust")
                    val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay()
                    val used = if (profile.lastAdWatchDay == today) profile.adWatchesToday else 0
                    // The count is the SHARED daily budget, so a player who
                    // doubled a couple of matches sees it move here too. Said
                    // out loud, because a number that ticks down on its own
                    // reads as a bug if the screen never explains it.
                    Subtitle(
                        "Watch a short ad, entirely optional, and pocket " +
                            "✦ ${AdRewards.WATCH_STARDUST}. ($used/${AdRewards.DAILY_WATCH_CAP} " +
                            "today — shares a ${AdRewards.DAILY_REWARDED_VIEWS}-ad daily limit " +
                            "with doubling a match reward)"
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = used < AdRewards.DAILY_WATCH_CAP,
                        onClick = { ads.show(activity) { repo.recordAdWatch(today) } },
                    ) { Text("Watch → ✦ ${AdRewards.WATCH_STARDUST}") }
                }
            }
        }

        val subscriptions by billing.subscriptions.collectAsState()
        if (profile.adPassActive) {
            SectionCard {
                Column {
                    CardTitle("⭐ Ad Pass — active")
                    Subtitle(
                        "Thanks for the support! You earn " +
                            "${AdRewards.PASS_REWARD_MULTIPLIER}× stardust from every match, " +
                            "collect ✦ ${AdRewards.PASS_DAILY_BONUS} daily, and will never be shown an ad."
                    )
                }
            }
        } else if (subscriptions.isNotEmpty() && activity != null) {
            SectionCard {
                Column {
                    CardTitle("⭐ Ad Pass")
                    Subtitle(
                        "${AdRewards.PASS_REWARD_MULTIPLIER}× stardust from every match, " +
                            "✦ ${AdRewards.PASS_DAILY_BONUS} every day automatically, and no ads, ever. " +
                            "Cancel anytime in Play."
                    )
                    Spacer(Modifier.height(8.dp))
                    for (pd in subscriptions) {
                        val price = pd.subscriptionOfferDetails?.firstOrNull()
                            ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: continue
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(pd.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(pd.description, color = TextDim, fontSize = 12.sp)
                            }
                            Button(onClick = { billing.launchPurchase(activity, pd) }) {
                                Text(price, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        val products by billing.products.collectAsState()
        if (products.isNotEmpty() && activity != null) {
            SectionCard {
                Column {
                    CardTitle("Support the game")
                    Subtitle("Everything above is earnable by playing. These just skip the grind.")
                    Spacer(Modifier.height(8.dp))
                    // The season pass is deliberately NOT sold here. It is the
                    // one product that grants content rather than accelerating
                    // it — the premium lane cannot be earned at any amount of
                    // play — so listing it under "just skip the grind" would
                    // make that sentence false the moment Console products
                    // exist. It has its own screen, where the offer is shown
                    // in full before it is bought (D-36).
                    for (pd in products.filter { it.productId != Iap.SEASON_PASS }) {
                        val price = pd.oneTimePurchaseOfferDetails?.formattedPrice ?: continue
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(pd.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(pd.description, color = TextDim, fontSize = 12.sp)
                            }
                            Button(onClick = { billing.launchPurchase(activity, pd) }) {
                                Text(price, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun projectileBlurb(type: ProjectileType): String = when (type) {
    ProjectileType.STANDARD -> "Reliable bolt. 34 damage."
    ProjectileType.HEAVY -> "Slow, huge, hits for 50."
    ProjectileType.SPLITTER -> "Splits into three shards mid-flight. 3 × 14 damage."
    ProjectileType.RETRO -> "One mid-flight brake burn on command. 30 damage, infinite finesse."
}
