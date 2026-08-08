package io.github.stan2032.gravitygambit.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guards that read files must actually be reading them.
 *
 * Six tests in this package assert things about documents and sources they do
 * not own — the launch guide's product list, the release checklist's coverage,
 * the architecture map's file list, the inventory of ad surfaces, ADS.md's
 * pricing numbers. Each locates the repo root by walking up from
 * `user.dir`, and each **`assumeTrue`s** the result rather than asserting it,
 * so that an unusual working directory produces a skip instead of a failure.
 *
 * That is a reasonable choice with one nasty property: if the layout ever
 * moves — a renamed module, a changed Gradle working directory, a guard copied
 * into a project laid out differently — **all six stop guarding at once, and
 * the build stays green.** They would report safety while testing nothing,
 * which is the defect class D-56 was written about, sitting inside the very
 * tests that exist to catch it.
 *
 * So the guarantee lives here instead, in the one test that refuses to skip.
 * If this fails, the others are not to be trusted until it passes.
 *
 * It is **derived, not enumerated** (the D-51 pattern, and lesson 2's warning
 * that a guard only checks what is listed in it): rather than hard-coding the
 * six paths, it scans this package's own sources for repo-relative path
 * literals and checks each one resolves. A seventh guard added tomorrow is
 * covered the moment it names a path, with nothing to remember.
 *
 * A path named only in a comment is checked too, and that is deliberate — a
 * test whose prose points at a file that no longer exists is drift worth
 * failing on, in the same way the documents these guards police are.
 */
class GuardReachabilityTest {

    /**
     * Repo-relative paths in any quoted string literal — anything under docs,
     * app, core or tools.
     *
     * Write illustrative examples **unquoted**, as above. The first run of this
     * test failed on two quoted placeholders in this file's own KDoc, which is
     * the scan behaving correctly: a quoted path-shaped string that points at
     * nothing is indistinguishable from a guard aimed at a file that moved.
     */
    private val pathLiteral = Regex("\"((?:docs|app|core|tools)/[A-Za-z0-9_./-]+)\"")

    /**
     * Deliberately **not** `assumeTrue`. Failing to find its own source tree is
     * exactly the condition this test exists to report, so it must be loud.
     */
    private fun guardSources(): List<File> {
        val relative = "core/src/test/kotlin/io/github/stan2032/gravitygambit/core"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(4) {
            val candidate = dir?.resolve(relative)
            if (candidate != null && candidate.isDirectory) {
                return candidate.listFiles { f: File -> f.name.endsWith(".kt") }?.toList().orEmpty()
            }
            dir = dir?.parentFile
        }
        throw AssertionError(
            "Could not find $relative by walking up from ${System.getProperty("user.dir")}. " +
                "Every file-reading guard in this package uses the same four-level walk and " +
                "`assumeTrue`s the result, so if this lookup is broken they are all SKIPPING " +
                "and the build is green on tests that ran nothing. Fix the lookup — do not " +
                "delete this test.",
        )
    }

    /** The same walk the guards use, so this test fails when and only when they would skip. */
    private fun resolves(relative: String): Boolean {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(4) {
            val candidate = dir?.resolve(relative)
            if (candidate != null && candidate.exists()) return true
            dir = dir?.parentFile
        }
        return false
    }

    @Test
    fun everyPathTheGuardsDependOnIsReachable() {
        val byPath = HashMap<String, MutableSet<String>>()
        for (f in guardSources()) {
            for (m in pathLiteral.findAll(f.readText())) {
                byPath.getOrPut(m.groupValues[1]) { sortedSetOf() }.add(f.name)
            }
        }

        val unreachable = byPath.filterKeys { !resolves(it) }
        assertTrue(
            "These repo paths are named in the guards but cannot be found: " +
                unreachable.entries.joinToString(", ") { "${it.key} (${it.value})" } +
                ". Either a guard is pointed at a file that moved — in which case it has been " +
                "silently SKIPPING via assumeTrue and proving nothing — or a comment names a " +
                "path that no longer exists. Both are worth fixing.",
            unreachable.isEmpty(),
        )

        // Known-positive control. If the scan finds nothing it passes vacuously,
        // which is the failure mode this whole file is about (D-49: a negative
        // result is meaningless until you confirm the probe does something).
        assertTrue(
            "The path scan found no repo paths at all in ${guardSources().size} guard sources. " +
                "The regex or the directory lookup is broken, and this test is passing on air.",
            byPath.size >= 4,
        )
    }

    /**
     * Every guard that skips on a missing path must name that path in a way the
     * scan above can see. Otherwise a new guard joins the silent-skip club and
     * nothing here notices — the exact gap that made this file necessary.
     */
    @Test
    fun everySkippableGuardIsCoveredByTheScan() {
        val sources = guardSources()
        val skippable = sources.filter { it.readText().contains("assumeTrue") }.map { it.name }
        val scanned = sources
            .filter { pathLiteral.containsMatchIn(it.readText()) }
            .map { it.name }
            .toSet()

        assertEquals(
            "A guard calls assumeTrue but names no repo-relative path literal the scan can " +
                "check, so if its lookup breaks it will skip silently and " +
                "everyPathTheGuardsDependOnIsReachable will not notice. Either express its " +
                "path as a plain \"docs/...\"-style literal, or assert the lookup instead of " +
                "assuming it.",
            emptyList<String>(), skippable.filter { it !in scanned }.sorted(),
        )
        assertTrue(
            "No guard in this package uses assumeTrue any more. If they were all converted to " +
                "hard assertions that is good news and this test can go — but check that is " +
                "what happened, rather than the source scan having quietly broken.",
            skippable.isNotEmpty(),
        )
    }
}
