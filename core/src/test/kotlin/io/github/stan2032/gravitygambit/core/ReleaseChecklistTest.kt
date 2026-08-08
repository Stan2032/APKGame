package io.github.stan2032.gravitygambit.core

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The release QA checklist must name every shipped mechanic, because it is the
 * **only human gate** before an upload and it fails silently when it does not.
 *
 * `docs/RELEASE.md` is what a person works through on a real device before
 * shipping. Nothing about the app breaks when the list omits a feature — the
 * run just comes back green having tested less than the reader believes.
 *
 * That is not hypothetical. The checklist was rewritten at 0.13.2 precisely
 * *because* the version before it "predated anchors, gambits, all three
 * objectives, the season track and duel normalization — so a green run of it
 * told you almost nothing". By 0.19.10 the rewrite had gone stale **in exactly
 * the same way**: it mentioned Expedition, salvage, outfitting, debris, Blind
 * practice and reduced motion **zero times** between them, so a green run
 * tested none of roughly six versions of work.
 *
 * The fix is not a longer hand-maintained list — that is the thing that rots.
 * It is to derive the requirement from the code, so adding a mutator or a
 * fitting makes this test fail until the checklist gains a line (D-51's
 * pattern: the code is the authority and the document must keep up).
 */
class ReleaseChecklistTest {

    private fun releaseDoc(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(4) {
            val candidate = dir?.resolve("docs/RELEASE.md")
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        return null
    }

    @Test
    fun theReleaseChecklistNamesEveryShippedMechanic() {
        val doc = releaseDoc()
        assumeTrue(
            "could not locate docs/RELEASE.md from ${System.getProperty("user.dir")}",
            doc != null,
        )
        val text = doc!!.readText().lowercase()
        val missing = ArrayList<String>()

        // Every run condition, by the label the player is shown. A mutator the
        // QA pass never exercises is a mutator nobody has seen work — which is
        // how Dark run shipped broken for four versions (D-43).
        for (m in Expedition.Mutator.entries) {
            if (m == Expedition.Mutator.NONE) continue
            if (!text.contains(m.displayName.lowercase())) missing.add("mutator '${m.displayName}'")
        }

        // Every permanent fitting, by its id stem — these change the economy a
        // run is played under, and none was mentioned before 0.19.11.
        for (o in Outfitting.Outfit.entries) {
            val stem = o.id.substringBefore('_')
            if (!text.contains(stem.lowercase())) missing.add("outfit '${o.id}'")
        }

        // Debris is a body kind the *player* creates during a match, and the
        // only one that is not a fact about the generated system.
        if (!text.contains("debris") && !text.contains("wreck")) missing.add("debris/wreckage")

        // The run mode itself, and its currency.
        if (!text.contains("expedition")) missing.add("Expedition mode")
        if (!text.contains("salvage")) missing.add("salvage currency")

        assertTrue(
            "docs/RELEASE.md does not mention $missing. That checklist is the only human gate " +
                "before an upload, and an omitted mechanic fails SILENTLY — the pass comes back " +
                "green having tested less than the reader believes. Add a line rather than " +
                "loosening this test.",
            missing.isEmpty(),
        )
    }
}
