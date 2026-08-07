package io.github.stan2032.gravitygambit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stan2032.gravitygambit.core.Expedition
import io.github.stan2032.gravitygambit.core.Outfitting
import io.github.stan2032.gravitygambit.core.Profile
import io.github.stan2032.gravitygambit.ui.GravityGambitTheme
import io.github.stan2032.gravitygambit.ui.screens.ExpeditionScreen
import io.github.stan2032.gravitygambit.ui.screens.expeditionLaunch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The junction is the screen a run lives on, and its job is to make a
 * decision affordable to read: which way, what it pays, what it costs.
 * These render it for real and drive a run through it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ExpeditionScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun briefingLeadsToAJunctionWithThreeRoutes() {
        var taken: Expedition.Leg? = null
        compose.setContent {
            GravityGambitTheme {
                var run by remember { mutableStateOf<Expedition.Run?>(null) }
                ExpeditionScreen(
                    profile = Profile(),
                    run = run,
                    onStart = { run = Expedition.Run(seed = 42L) },
                    onAbandon = { run = null },
                    onTake = { taken = it },
                    onNavigate = {},
                )
            }
        }
        // Substring: the button now states the fuel you will leave with,
        // which depends on fitted outfits.
        compose.onNodeWithText("Launch", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Launch", substring = true).performClick()

        // One card per flavour, each naming what it is.
        for (route in Expedition.Route.entries) {
            compose.onNodeWithText(route.label).assertExists()
        }
        compose.onNodeWithText("Choose your route").assertExists()

        compose.onNodeWithText(Expedition.Route.RICH.label).performClick()
        assertEquals(Expedition.Route.RICH, taken?.route)
    }

    @Test
    fun theJunctionStatesWhatTheLegCostsBeforeItIsTaken() {
        // A run mode where the price is discovered after committing feels
        // arbitrary. Deep enough that travel is a real number, not zero.
        val run = Expedition.Run(seed = 7L, depth = 6, fuel = 30)
        compose.setContent {
            GravityGambitTheme {
                ExpeditionScreen(
                    profile = Profile(), run = run,
                    onStart = {}, onAbandon = {}, onTake = {}, onNavigate = {},
                )
            }
        }
        compose.onNodeWithText("30", substring = true).assertExists()   // the tank
        compose.onNodeWithText("6 deep").assertExists()
        compose.onNodeWithText(
            "Travel to the next system costs ${Expedition.travelCost(6)} fuel",
            substring = true,
        ).assertExists()
    }

    @Test
    fun anUnchartedRouteShowsItsCondition() {
        // The condition is the whole reason Uncharted exists; a card that did
        // not name it would be a third difficulty label.
        val run = Expedition.Run(seed = 11L, depth = 2, fuel = 20)
        val uncharted = Expedition.routesFor(run).first { it.route == Expedition.Route.UNKNOWN }
        assertTrue("fixture assumes Uncharted carries a mutator", uncharted.mutator != Expedition.Mutator.NONE)
        compose.setContent {
            GravityGambitTheme {
                ExpeditionScreen(
                    profile = Profile(), run = run,
                    onStart = {}, onAbandon = {}, onTake = {}, onNavigate = {},
                )
            }
        }
        compose.onNodeWithText(uncharted.mutator.displayName).assertExists()
        compose.onNodeWithText(uncharted.mutator.brief).assertExists()
    }

    @Test
    fun anEndedRunShowsWhatItPaid() {
        val over = Expedition.Run(seed = 3L, depth = 9, fuel = 0, score = 1200, starsEarned = 19, wrecks = 2)
        assertTrue("fixture must be an ended run", over.over)
        compose.setContent {
            GravityGambitTheme {
                ExpeditionScreen(
                    profile = Profile(), run = over,
                    onStart = {}, onAbandon = {}, onTake = {}, onNavigate = {},
                )
            }
        }
        compose.onNodeWithText("The tank is empty").assertExists()
        compose.onNodeWithText("Systems cleared").assertExists()
        compose.onNodeWithText("${Expedition.payout(over)}").assertExists()
        compose.onNodeWithText("Run again").assertExists()
    }

    @Test
    fun theOutfittingBayAppearsOnlyAfterAFirstRun() {
        // A first-time player has no salvage and no idea what it is, so the
        // bay would be noise before their first launch — and the launch
        // button states the fuel they will actually leave with.
        compose.setContent {
            GravityGambitTheme {
                ExpeditionScreen(
                    profile = Profile(), run = null,
                    onStart = {}, onAbandon = {}, onTake = {}, onNavigate = {},
                )
            }
        }
        compose.onAllNodesWithText("Outfitting").fetchSemanticsNodes().let {
            assertTrue("a fresh player must not see the outfitting bay", it.isEmpty())
        }
        compose.onNodeWithText("Launch with ${Expedition.START_FUEL} fuel").assertExists()
    }

    @Test
    fun anOutfittedPlayerSeesTheirSalvageAndLaunchesWithMoreFuel() {
        val fitted = Profile(
            expeditionRuns = 3,
            expeditionSalvage = 40,
            expeditionOutfits = mapOf(Outfitting.Outfit.RESERVE_TANK.id to 2),
        )
        var bought: Outfitting.Outfit? = null
        compose.setContent {
            GravityGambitTheme {
                ExpeditionScreen(
                    profile = fitted, run = null,
                    onStart = {}, onAbandon = {}, onTake = {}, onNavigate = {},
                    onBuyOutfit = { bought = it },
                )
            }
        }
        compose.onNodeWithText("Outfitting").assertExists()
        compose.onNodeWithText("40").assertExists()
        // Fitted tiers really change the run that is about to start.
        compose.onNodeWithText(
            "Launch with ${Outfitting.startFuel(fitted.expeditionOutfits)} fuel",
        ).assertExists()
        assertTrue(
            "the fixture must actually raise starting fuel",
            Outfitting.startFuel(fitted.expeditionOutfits) > Expedition.START_FUEL,
        )
        // An affordable tier is buyable; the callback carries which one.
        val cost = Outfitting.Outfit.RESERVE_TANK.costOfNextTier(2)!!
        compose.onNodeWithText("$cost").performClick()
        assertEquals(Outfitting.Outfit.RESERVE_TANK, bought)
    }

    @Test
    fun aLegLaunchesAsACampaignMatchOutsideTheStarMap() {
        // D-34: a leg IS a campaign match. But it must not write campaign
        // stars — sector/level -1 is what keeps an Expedition clear from
        // handing out sector unlocks for systems never found on the grid.
        val leg = Expedition.routesFor(Expedition.Run(seed = 5L)).first()
        val launch = expeditionLaunch(leg, nonce = 1L)
        assertEquals(io.github.stan2032.gravitygambit.core.GameMode.CAMPAIGN, launch.mode)
        assertEquals(leg.seed, launch.seed)
        assertEquals(leg.difficulty, launch.difficulty)
        assertEquals(-1, launch.sector)
        assertEquals(-1, launch.level)
        assertNotNull(launch.expeditionLeg)
    }
}
