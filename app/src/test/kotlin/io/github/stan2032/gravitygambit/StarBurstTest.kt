package io.github.stan2032.gravitygambit

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stan2032.gravitygambit.ui.GravityGambitTheme
import io.github.stan2032.gravitygambit.ui.game.StarBurst
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The star flourish is decoration, so almost nothing about it can be asserted
 * — how it *looks* is a human question. Two things can be, and both are the
 * kind that break silently:
 *
 * 1. It must survive composition at every star count, including zero and the
 *    full three, because it draws a different shape for earned and unearned
 *    and an off-by-one in that loop would crash the summary — the screen that
 *    appears at the end of *every* match.
 * 2. It must speak. The stars used to be text, which TalkBack read for free;
 *    replacing them with a Canvas silently removes that unless the semantics
 *    are declared by hand. A player using a screen reader must still be told
 *    what they scored.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class StarBurstTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyStarCountComposes() {
        // All four in one composition: `setContent` may only be called once
        // per test, and composing them side by side also exercises several
        // independent flourishes running at the same time, which is what a
        // summary would do if the layout ever changed.
        compose.setContent {
            GravityGambitTheme {
                Column { for (stars in 0..3) StarBurst(stars = stars) }
            }
        }
        for (stars in 0..3) {
            compose.onNodeWithContentDescription("$stars of 3 stars").assertExists()
        }
    }

    @Test
    fun theScoreIsSpokenNotOnlyDrawn() {
        // Canvas art has no text to read. This is the regression that would
        // otherwise ship invisibly, because the screen looks better than
        // before while being less usable for someone who cannot see it.
        compose.setContent { GravityGambitTheme { StarBurst(stars = 2) } }
        compose.onNodeWithContentDescription("2 of 3 stars").assertIsDisplayed()
    }
}
