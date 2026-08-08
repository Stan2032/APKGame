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
 * 0.19.14 asserted the inventory and deliberately *not* the policy, because
 * which surfaces are capped was an open owner decision and a test pinning
 * today's answer would have entrenched the bug it came from. 0.19.15 closed
 * that decision (D-56): every surface now draws on one shared daily budget, so
 * the policy is finally a fact rather than a preference and can be asserted.
 *
 * Current inventory, and their governance as of 0.19.15:
 *  - `ShopScreen.kt`   — "free stardust" button. Spends a view via
 *                        `recordAdWatch`, held to the `DAILY_WATCH_CAP` sub-cap.
 *  - `GameScreen.kt`   — post-match "Double it". Spends a view via
 *                        `spendRewardedView`, held to the shared budget.
 *
 * The second test is a file-level check, and that is an honest limitation: it
 * proves a rewarded surface lives in a file that also knows how to spend a
 * view, not that this particular call site does. It is aimed squarely at the
 * way D-53 actually happened — a payout written as a bare `repo.update { }` in
 * a file that had no idea a budget existed — and it catches that.
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

    @Test
    fun noRewardedSurfacePaysOutOutsideTheDailyBudget() {
        val sources = appSources()
        assumeTrue("could not locate app/src/main/kotlin", sources != null)

        val ungoverned = sources!!
            .filter { it.readText().contains("ads.show(") }
            .filter { file ->
                val text = file.readText()
                !text.contains("spendRewardedView") && !text.contains("recordAdWatch")
            }
            .map { it.name }
            .toSortedSet()

        assertEquals(
            "A file shows a rewarded ad but never spends a view from the daily budget. " +
                "That is D-53 verbatim: the post-match doubler paid out with a bare " +
                "`repo.update { }`, no counter moved, and the Ad Pass silently stopped " +
                "out-earning ads for many versions. Route the payout through " +
                "ProfileRepository.spendRewardedView(epochDay) { ... } so " +
                "AdRewards.DAILY_REWARDED_VIEWS actually governs it, then re-run the " +
                "arithmetic in AdPassInvariantTest.",
            emptySet<String>().toSortedSet(), ungoverned,
        )
    }
}
