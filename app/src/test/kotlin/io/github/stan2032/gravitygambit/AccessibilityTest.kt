package io.github.stan2032.gravitygambit

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stan2032.gravitygambit.core.Profile
import io.github.stan2032.gravitygambit.data.ProfileRepository
import io.github.stan2032.gravitygambit.ui.GravityGambitTheme
import io.github.stan2032.gravitygambit.ui.screens.CampaignScreen
import io.github.stan2032.gravitygambit.ui.screens.HomeScreen
import io.github.stan2032.gravitygambit.ui.screens.SettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Accessibility guardrails. Android lets users scale text well past 200%;
 * fixed-height controls silently clip their labels at those sizes, which no
 * compiler or lint check catches. These render the real screens at large
 * font scales and assert the controls survive.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class AccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setContentAtFontScale(scale: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = scale)
            ) {
                GravityGambitTheme { content() }
            }
        }
    }

    @Test
    fun homeSurvivesLargeFontScale() {
        setContentAtFontScale(2.0f) { HomeScreen(profile = Profile(), onNavigate = {}) }
        compose.onNodeWithText("Campaign").assertIsDisplayed()
        compose.onNodeWithText("Daily Challenge").assertExists()
    }

    @Test
    fun settingsSurvivesLargeFontScale() {
        val repo = ProfileRepository(ApplicationProvider.getApplicationContext())
        setContentAtFontScale(2.0f) {
            SettingsScreen(profile = Profile(), repo = repo, onNavigate = {})
        }
        compose.onNodeWithText("Haptic feedback").assertExists()
        compose.onNodeWithText("Sound effects").assertExists()
    }

    @Test
    fun campaignLevelsExposeSpokenLabels() {
        compose.setContent {
            GravityGambitTheme { CampaignScreen(profile = Profile(), onNavigate = {}) }
        }
        // Star glyphs are decorative; each level must carry a spoken label.
        compose.onNodeWithContentDescription("Level 1, not yet cleared").assertExists()
        compose.onNodeWithContentDescription("Level 2, locked").assertExists()
    }

    @Test
    fun campaignSurvivesLargeFontScale() {
        setContentAtFontScale(1.8f) { CampaignScreen(profile = Profile(), onNavigate = {}) }
        compose.onNodeWithText("Sector 1").assertExists()
    }
}
