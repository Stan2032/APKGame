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
import io.github.stan2032.gravitygambit.audio.AmbienceTrack
import io.github.stan2032.gravitygambit.audio.SoundManager
import io.github.stan2032.gravitygambit.billing.BillingManager
import io.github.stan2032.gravitygambit.core.Expedition
import io.github.stan2032.gravitygambit.core.GameMode
import io.github.stan2032.gravitygambit.core.Outfitting
import io.github.stan2032.gravitygambit.core.Seeds
import io.github.stan2032.gravitygambit.data.ProfileRepository
import io.github.stan2032.gravitygambit.ui.game.GameScreen
import io.github.stan2032.gravitygambit.ui.screens.CampaignScreen
import io.github.stan2032.gravitygambit.ui.screens.DuelScreen
import io.github.stan2032.gravitygambit.ui.screens.ExpeditionScreen
import io.github.stan2032.gravitygambit.ui.screens.expeditionLaunch
import io.github.stan2032.gravitygambit.ui.screens.HelpScreen
import io.github.stan2032.gravitygambit.ui.screens.HomeScreen
import io.github.stan2032.gravitygambit.ui.screens.SeasonScreen
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
    /**
     * Set when this match is one leg of an Expedition run. The mode stays
     * CAMPAIGN — a leg *is* a campaign match (D-34) — but the leg carries the
     * run's conditions (a Dark run removes the aim trace) and marks the match
     * as one whose rewards belong to the run rather than to the star map.
     */
    val expeditionLeg: Expedition.Leg? = null,
)

sealed interface Nav {
    data object Home : Nav
    data object Campaign : Nav
    data object Duel : Nav
    data object Expedition : Nav
    data object Season : Nav
    data object Shop : Nav
    data object Settings : Nav
    data object Help : Nav
    data class Game(val launch: GameLaunch) : Nav
}

@Composable
fun App(
    repo: ProfileRepository,
    billing: BillingManager,
    sound: SoundManager,
    ads: RewardedAdsProvider = NoOpRewardedAds,
    ambience: AmbienceTrack = remember { AmbienceTrack() },
) {
    var nav by remember { mutableStateOf<Nav>(Nav.Home) }
    val profile by repo.profile.collectAsState()

    /**
     * The Expedition run in progress, or null between runs.
     *
     * Held here rather than persisted, and that is the design (D-9's reasoning
     * applied to D-34): a run is a sitting. Storing one would mean storing the
     * generated leg, the fuel and the match inside it — and would invite
     * quitting out of a system that went badly to reroll it, which is exactly
     * the pressure a run mode exists to create.
     */
    var expedition by remember { mutableStateOf<Expedition.Run?>(null) }
    var runNonce by remember { mutableStateOf(0L) }

    // Ad Pass daily drop, claimed automatically on app open.
    LaunchedEffect(profile.adPassActive) {
        if (profile.adPassActive) {
            repo.claimPassDailyBonus(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay())
        }
    }
    LaunchedEffect(profile.soundEnabled) { sound.enabled = profile.soundEnabled }
    LaunchedEffect(profile.musicEnabled) { ambience.enabled = profile.musicEnabled }
    // Menus are silent on purpose: arriving in a system should be a change you
    // hear, which only works if the place you came from was quiet.
    LaunchedEffect(nav is Nav.Game) { if (nav !is Nav.Game) ambience.setSystem(null) }

    // Bank a run the moment it ends, not when the player taps through the
    // summary — the screen showing the payout must not also be the thing that
    // decides whether it happened. Keyed on the run itself, so a recomposition
    // cannot pay twice and an abandoned run cannot pay at all.
    val finished = expedition?.takeIf { it.over }
    LaunchedEffect(finished) {
        if (finished != null) {
            repo.recordExpedition(
                finished,
                java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay(),
            )
        }
    }

    Box(Modifier.fillMaxSize().background(BgColor)) {
        // Living backdrop everywhere except the match itself, which renders its
        // own (far richer) universe.
        if (nav !is Nav.Game) AmbientBackdrop()

        when (val n = nav) {
            is Nav.Home -> HomeScreen(profile = profile, onNavigate = { nav = it })
            is Nav.Campaign -> CampaignScreen(profile = profile, onNavigate = { nav = it })
            is Nav.Duel -> DuelScreen(profile = profile, onNavigate = { nav = it })
            is Nav.Expedition -> ExpeditionScreen(
                profile = profile,
                run = expedition,
                onStart = {
                    // Starting fuel is whatever the player has fitted — the
                    // one place an outfit changes a run before it begins.
                    expedition = Expedition.Run(
                        seed = Seeds.expeditionRun(System.currentTimeMillis()),
                        fuel = Outfitting.startFuel(profile.expeditionOutfits),
                    )
                },
                onAbandon = { expedition = null; nav = Nav.Home },
                onTake = { leg ->
                    runNonce++
                    nav = Nav.Game(expeditionLaunch(leg, runNonce))
                },
                onNavigate = { nav = it },
                onBuyOutfit = { repo.buyOutfit(it) },
            )
            is Nav.Season -> SeasonScreen(
                profile = profile, repo = repo, billing = billing,
                onNavigate = { nav = it },
            )
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
                sound = sound,
                ambience = ambience,
                // Applied here rather than inside the match, because the run
                // is App's state and the match must stay a match: a leg does
                // not know it is part of anything.
                onLegFinished = { shots, stars, cleared ->
                    val leg = n.launch.expeditionLeg
                    val current = expedition
                    if (leg != null && current != null && !current.over) {
                        expedition = if (cleared) {
                            Expedition.afterClear(current, leg, shots, stars)
                        } else {
                            Expedition.afterWreck(current, leg, shots, profile.expeditionOutfits)
                        }
                    }
                },
                onNavigate = { nav = it },
            )
        }
    }
}
