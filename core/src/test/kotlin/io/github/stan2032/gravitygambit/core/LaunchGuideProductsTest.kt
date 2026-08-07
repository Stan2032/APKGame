package io.github.stan2032.gravitygambit.core

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The launch guide must name **every** billing product, because the failure
 * mode when it does not is silent and expensive.
 *
 * `docs/LAUNCH_GUIDE.md` is the document the owner follows to create products
 * in Play Console by hand. Until 0.19.10 it listed three one-time products
 * when `Economy.Iap.ALL_PRODUCT_IDS` has four — `season_pass` was missing —
 * and the fast checklist said "5 IAP products" against a real six. Following
 * it would have shipped the game with its **only recurring revenue product
 * simply absent**, and nothing would have said so: the app asks billing for a
 * product that was never created, billing returns nothing, and the Season
 * screen deliberately *hides* the offer rather than erroring (`SeasonScreenTest`
 * asserts that hiding, because it is what makes the app safe to ship before
 * Console products exist).
 *
 * So the omission is invisible from inside the app, invisible in CI, and only
 * discoverable by noticing the pass never appears. This test makes the code
 * the authority and the document the thing that must keep up.
 */
class LaunchGuideProductsTest {

    /**
     * Walks up from the working directory to the repo root. Gradle runs tests
     * with the module directory as the working directory, so `docs/` is one
     * level up — but that is a build detail, not something to hard-code.
     */
    private fun launchGuide(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(4) {
            val candidate = dir?.resolve("docs/LAUNCH_GUIDE.md")
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        return null
    }

    @Test
    fun theLaunchGuideNamesEveryBillingProduct() {
        val guide = launchGuide()
        // If the doc genuinely cannot be located the test says so rather than
        // passing quietly — but a missing file is an environment problem, not
        // a product problem, so it does not fail the build.
        assumeTrue("could not locate docs/LAUNCH_GUIDE.md from ${System.getProperty("user.dir")}", guide != null)
        val text = guide!!.readText()

        val required = Iap.ALL_PRODUCT_IDS + Iap.SUBSCRIPTION_IDS
        val missing = required.filterNot { text.contains(it) }
        assertTrue(
            "docs/LAUNCH_GUIDE.md does not mention $missing. That guide is how products get " +
                "created in Play Console by hand, and an omitted product fails SILENTLY — " +
                "billing returns nothing and the app hides the offer. Add it to Phase 7 and to " +
                "the fast checklist count.",
            missing.isEmpty(),
        )

        // The checklist carries a count as well as a list, and at 0.19.9 the
        // two disagreed with the code in the same direction. A count is a
        // claim like any other.
        val total = required.size
        assertTrue(
            "the fast checklist must state $total IAP products (found neither '$total IAP' nor " +
                "'$total in-app'); a stale count is how the list drifted last time",
            text.contains("$total IAP") || text.contains("$total in-app"),
        )
    }
}
