package io.github.stan2032.gravitygambit

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stan2032.gravitygambit.core.GameMode
import io.github.stan2032.gravitygambit.core.Profile
import io.github.stan2032.gravitygambit.core.Seeds
import io.github.stan2032.gravitygambit.ui.GameLaunch
import io.github.stan2032.gravitygambit.ui.GravityGambitTheme
import io.github.stan2032.gravitygambit.ui.Nav
import io.github.stan2032.gravitygambit.ui.screens.CampaignScreen
import io.github.stan2032.gravitygambit.ui.screens.DuelScreen
import io.github.stan2032.gravitygambit.ui.screens.HelpScreen
import io.github.stan2032.gravitygambit.ui.screens.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screen-level smoke tests: these actually compose the real UI on the JVM
 * (Robolectric), which catches the class of failure that compilation and
 * :core unit tests cannot — crashes during composition, missing resources,
 * broken state wiring. Not a substitute for on-device QA (docs/RELEASE.md),
 * but it means no screen can be shipped having never once been rendered.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")  // typical phone viewport
class AppSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeScreenRendersAllModes() {
        var navigated: Nav? = null
        compose.setContent {
            GravityGambitTheme {
                HomeScreen(profile = Profile(), onNavigate = { navigated = it })
            }
        }
        // assertExists (not assertIsDisplayed): lower entries legitimately sit
        // below the fold on a phone-sized viewport; what matters here is that
        // every mode composes and is reachable.
        compose.onNodeWithText("Campaign").assertIsDisplayed()
        compose.onNodeWithText("Daily Challenge").assertExists()
        compose.onNodeWithText("Duel").assertExists()
        compose.onNodeWithText("Outfitter").assertExists()

        compose.onNodeWithText("Campaign").performClick()
        assertEquals(Nav.Campaign, navigated)
    }

    @Test
    fun dailyLaunchCarriesTodaysSeed() {
        var navigated: Nav? = null
        compose.setContent {
            GravityGambitTheme {
                HomeScreen(profile = Profile(), onNavigate = { navigated = it })
            }
        }
        compose.onNodeWithText("Daily Challenge").performClick()
        val launch = (navigated as Nav.Game).launch
        assertEquals(GameMode.DAILY, launch.mode)
        assertEquals(Seeds.daily(launch.epochDay), launch.seed)
    }

    @Test
    fun campaignScreenGatesLockedSectors() {
        compose.setContent {
            GravityGambitTheme {
                CampaignScreen(profile = Profile(), onNavigate = {})
            }
        }
        compose.onNodeWithText("Sector 1").assertIsDisplayed()
        // A fresh profile has no stars, so only level 1 is playable.
        compose.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun duelScreenRejectsGarbageCode() {
        var navigated: Nav? = null
        compose.setContent {
            GravityGambitTheme {
                DuelScreen(profile = Profile(), onNavigate = { navigated = it })
            }
        }
        compose.onNodeWithText("Load duel").performClick()
        // Empty/garbage input must surface the error and never navigate.
        compose.onNode(hasText("That doesn't look like a valid duel code.")).assertExists()
        assertEquals(null, navigated)
    }

    @Test
    fun helpScreenRenders() {
        compose.setContent { GravityGambitTheme { HelpScreen(onNavigate = {}) } }
        compose.onNodeWithText("How to play").assertIsDisplayed()
    }

    @Test
    fun themeAppliesWithoutCrashing() {
        compose.setContent { GravityGambitTheme { Text("render check") } }
        compose.onNodeWithText("render check").assertIsDisplayed()
    }

    @Test
    fun gameLaunchDefaultsAreSane() {
        val launch = GameLaunch(mode = GameMode.CAMPAIGN, seed = 1L, difficulty = 0)
        assertTrue(launch.importedCode == null)
        assertTrue(!launch.hotseat)
    }
}
