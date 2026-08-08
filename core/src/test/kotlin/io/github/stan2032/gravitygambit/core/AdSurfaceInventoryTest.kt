package io.github.stan2032.gravitygambit.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Every place the app can show a rewarded ad must be a **known** place.
 *
 * This exists because of how D-53 happened, not because of what D-53 found.
 * The Ad Pass pricing invariant broke when a second rewarded surface — the
 * post-match "Double it" button — was added **without a cap policy**: it
 * grants its reward directly and never calls `recordAdWatch`, so
 * `AdRewards.DAILY_WATCH_CAP` does not govern it. Meanwhile ADS.md kept doing
 * arithmetic that assumed the cap covered everything. Nothing failed for many
 * versions, because nothing was watching the *set* of surfaces.
 *
 * So this asserts the inventory, not the policy. It deliberately does **not**
 * check which surfaces are capped — that is the open owner decision (four
 * options in ADS.md §pricing), and a test that pinned today's answer would
 * entrench the bug it came from. What it pins is that adding a **new** way to
 * show an ad fails the build until someone states what caps it.
 *
 * Current inventory, and their governance as of 0.19.14:
 *  - `ShopScreen.kt`   — "free stardust" button. **Capped**: calls
 *                        `recordAdWatch`, so `DAILY_WATCH_CAP` (5/day) applies.
 *  - `GameScreen.kt`   — post-match "Double it". **Uncapped**: once per match,
 *                        no daily limit. This is the D-53 finding.
 *
 * Lives in `:core` alongside the other source/doc guards, which already walk
 * up to the repo root to read files they do not own.
 */
class AdSurfaceInventoryTest {

    /** Files permitted to call `ads.show(...)`, with why each is allowed. */
    private val knownSurfaces = setOf("ShopScreen.kt", "GameScreen.kt")

    private fun appSources(): List<File>? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(4) {
            val candidate = dir?.resolve("app/src/main/kotlin")
            if (candidate != null && candidate.isDirectory) {
                return candidate.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList()
            }
            dir = dir?.parentFile
        }
        return null
    }

    @Test
    fun everyRewardedAdSurfaceIsAccountedFor() {
        val sources = appSources()
        assumeTrue("could not locate app/src/main/kotlin", sources != null)

        val found = sources!!
            .filter { it.readText().contains("ads.show(") }
            .map { it.name }
            .toSortedSet()

        assertEquals(
            "The set of files that can show a rewarded ad has changed. This is the exact shape " +
                "of D-53: a surface was added without anyone deciding what caps it, ADS.md kept " +
                "assuming DAILY_WATCH_CAP covered everything, and the Ad Pass quietly stopped " +
                "out-earning ads. Before changing this list, state the new surface's cap policy " +
                "in ADS.md and re-run the pricing math there — the invariant is 'pass price(net) " +
                ">= max ad revenue across EVERY surface'.",
            knownSurfaces.toSortedSet(), found,
        )
    }
}
