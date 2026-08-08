package io.github.stan2032.gravitygambit.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The Ad Pass must earn more than the ads it replaces.
 *
 * That sentence is an owner directive from the project's first brief, recorded
 * as D-13. Until 0.19.15 it was enforced by arithmetic written in a document,
 * which is to say it was not enforced at all: D-53 found the arithmetic had
 * been wrong for many versions, because a second rewarded surface appeared and
 * the prose kept assuming one cap governed everything.
 *
 * So the arithmetic moved into [AdEconomics] and this test recomputes it on
 * every build. It is not a check that someone wrote the right number down —
 * it *derives* the right number and demands the constant match. Change the
 * pass price, the eCPM assumption, Play's fee, or a cap, and exactly one of
 * these assertions fails with the new figures in the message.
 *
 * What "maximum" means here: every input is taken at the end of its range that
 * makes the invariant hardest to satisfy — the top of the eCPM band, the
 * longest month, the cheaper of the two pass products, and a player who hits
 * the cap every single day without missing one. A real player is nowhere near
 * this, which is the point of a bound.
 */
class AdPassInvariantTest {

    /** The invariant itself, stated once, in the smallest form that means it. */
    @Test
    fun passOutEarnsTheAdsItReplaces() {
        val adRevenue = AdEconomics.monthlyAdNet(AdRewards.DAILY_REWARDED_VIEWS)
        val passRevenue = AdEconomics.monthlyPassNet()
        assertTrue(
            "D-13, an owner directive: the Ad Pass must out-earn the ads it replaces. " +
                "A player watching the full daily budget of ${AdRewards.DAILY_REWARDED_VIEWS} " +
                "views every day for ${AdEconomics.DAYS_PER_MONTH} days nets " +
                "$${"%.2f".format(adRevenue)}, against the pass's " +
                "$${"%.2f".format(passRevenue)}. Either lower " +
                "AdRewards.DAILY_REWARDED_VIEWS or raise AdEconomics.PASS_MONTHLY_USD — " +
                "and if neither is acceptable, the directive itself is what changed, " +
                "which is the owner's call and belongs in DECISIONS.md before it belongs here.",
            adRevenue <= passRevenue,
        )
    }

    /**
     * And the budget must be the *largest* one that satisfies it. An invariant
     * that only holds because the cap is timidly small is not being respected,
     * it is being avoided — and every view below the affordable line is value
     * withheld from players for nothing.
     */
    @Test
    fun theDailyBudgetIsTheLargestTheInvariantAllows() {
        assertEquals(
            "AdRewards.DAILY_REWARDED_VIEWS is not the number the pricing supports. " +
                "At $${AdEconomics.MAX_ECPM_USD} eCPM and a " +
                "$${AdEconomics.PASS_MONTHLY_USD} monthly pass, the pass covers " +
                "${AdEconomics.affordableDailyViews()} views/day; the constant says " +
                "${AdRewards.DAILY_REWARDED_VIEWS}. Too high breaks D-13; too low gives " +
                "players fewer rewards than the economics actually allow.",
            AdEconomics.affordableDailyViews(), AdRewards.DAILY_REWARDED_VIEWS,
        )
    }

    /** One more view than the budget must genuinely break it — proof the bound bites. */
    @Test
    fun oneViewOverTheBudgetBreaksTheInvariant() {
        assertTrue(
            "The budget is not actually binding: ${AdRewards.DAILY_REWARDED_VIEWS + 1} views/day " +
                "still nets less than the pass, so affordableDailyViews() and the constant " +
                "have drifted apart in a way the equality test should have caught.",
            AdEconomics.monthlyAdNet(AdRewards.DAILY_REWARDED_VIEWS + 1) > AdEconomics.monthlyPassNet(),
        )
    }

    /**
     * The shop's sub-cap exists to stop one surface eating the whole day and
     * leaving the post-match doubler dead on arrival. If it ever equals the
     * budget it has stopped doing that, silently.
     */
    @Test
    fun theShopCannotConsumeTheWholeDailyBudget() {
        assertTrue(
            "AdRewards.DAILY_WATCH_CAP (${AdRewards.DAILY_WATCH_CAP}) must leave room inside " +
                "DAILY_REWARDED_VIEWS (${AdRewards.DAILY_REWARDED_VIEWS}) for the post-match " +
                "doubler, or a player who used the shop finds a button that is present, " +
                "advertised, and pays nothing.",
            AdRewards.DAILY_WATCH_CAP < AdRewards.DAILY_REWARDED_VIEWS,
        )
        assertTrue(
            "A sub-cap above the budget is not a sub-cap.",
            AdRewards.DAILY_WATCH_CAP in 1..AdRewards.DAILY_REWARDED_VIEWS,
        )
    }

    /**
     * ADS.md is where a human goes to price this. D-51 and D-52 were both a
     * document that had quietly stopped describing the code, so the numbers
     * that matter are asserted rather than trusted.
     *
     * The needles are deliberately `NAME = value` and not bare digits. The
     * first draft of this test searched for "6" and "4", which any stray
     * character in a 120-line document satisfies — a test that passes on a
     * doc nobody updated is worse than no test, because it reports safety.
     * Same defect class as the rounded "0.0%" that read as zero.
     */
    @Test
    fun adsDocQuotesTheLiveNumbers() {
        val doc = repoFile("docs/ADS.md")
        assumeTrue("could not locate docs/ADS.md", doc != null)
        val text = doc!!.readText()

        for (needle in listOf(
            "DAILY_REWARDED_VIEWS` = ${AdRewards.DAILY_REWARDED_VIEWS}",
            "DAILY_WATCH_CAP` = ${AdRewards.DAILY_WATCH_CAP}",
            "PASS_MONTHLY_USD` = ${AdEconomics.PASS_MONTHLY_USD}",
        )) {
            assertTrue(
                "docs/ADS.md does not state \"$needle\". The pricing section is the only place " +
                    "a human sees these numbers before setting a real price in Play Console; " +
                    "if the code moved and the doc did not, the next person prices from fiction.",
                text.contains(needle),
            )
        }
    }

    private fun repoFile(relative: String): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(4) {
            val candidate = dir?.resolve(relative)
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        return null
    }
}
