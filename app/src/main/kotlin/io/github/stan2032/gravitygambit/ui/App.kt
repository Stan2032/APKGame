package io.github.stan2032.gravitygambit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.stan2032.gravitygambit.ads.NoOpRewardedAds
import io.github.stan2032.gravitygambit.ads.RewardedAdsProvider
import io.github.stan2032.gravitygambit.billing.BillingManager
import io.github.stan2032.gravitygambit.core.GameMode
import io.github.stan2032.gravitygambit.data.ProfileRepository
import io.github.stan2032.gravitygambit.ui.game.GameScreen
import io.github.stan2032.gravitygambit.ui.screens.CampaignScreen
import io.github.stan2032.gravitygambit.ui.screens.DuelScreen
import io.github.stan2032.gravitygambit.ui.screens.HelpScreen
import io.github.stan2032.gravitygambit.ui.screens.HomeScreen
import io.github.stan2032.gravitygambit.ui.screens.SettingsScreen
import io.github.stan2032.gravitygambit.ui.screens.ShopScreen

/** Everything needed to start (or resume) a match. */
data class GameLaunch(
    val mode: GameMode,
    val seed: Long,
    val difficulty: Int,
    val sector: Int = -1,
    val level: Int = -1,
    val epochDay: Long = 0L,
    val hotseat: Boolean = false,
    /** Non-null when joining/continuing a code duel: the pasted challenge code. */
    val importedCode: String? = null,
    /** Distinguishes replays of an identical launch (retry/rematch) in nav state. */
    val nonce: Long = 0L,
)

sealed interface Nav {
    data object Home : Nav
    data object Campaign : Nav
    data object Duel : Nav
    data object Shop : Nav
    data object Settings : Nav
    data object Help : Nav
    data class Game(val launch: GameLaunch) : Nav
}

@Composable
fun App(
    repo: ProfileRepository,
    billing: BillingManager,
    ads: RewardedAdsProvider = NoOpRewardedAds,
) {
    var nav by remember { mutableStateOf<Nav>(Nav.Home) }
    val profile by repo.profile.collectAsState()

    // Ad Pass daily drop, claimed automatically on app open.
    LaunchedEffect(profile.adPassActive) {
        if (profile.adPassActive) {
            repo.claimPassDailyBonus(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay())
        }
    }

    Box(Modifier.fillMaxSize().background(BgColor)) {
        when (val n = nav) {
            is Nav.Home -> HomeScreen(profile = profile, onNavigate = { nav = it })
            is Nav.Campaign -> CampaignScreen(profile = profile, onNavigate = { nav = it })
            is Nav.Duel -> DuelScreen(profile = profile, onNavigate = { nav = it })
            is Nav.Shop -> ShopScreen(
                profile = profile, repo = repo, billing = billing, ads = ads,
                onNavigate = { nav = it },
            )
            is Nav.Settings -> SettingsScreen(profile = profile, repo = repo, onNavigate = { nav = it })
            is Nav.Help -> HelpScreen(onNavigate = { nav = it })
            is Nav.Game -> GameScreen(
                launch = n.launch,
                profile = profile,
                repo = repo,
                ads = ads,
                onNavigate = { nav = it },
            )
        }
    }
}
