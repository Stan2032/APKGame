package io.github.stan2032.gravitygambit.core

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Every `:core` source file must appear in `docs/ARCHITECTURE.md`'s file map.
 *
 * The map is how an agent or a new reader finds their way around, and this
 * project has already recorded that **a file map goes stale as fast as the
 * code moves** — it was found drifting at 0.14.0 *after* being marked cleared
 * in an earlier pass. At 0.19.13 `Objective.kt` was missing: the file defining
 * BREACH/CAPTURE/COURIER/THREAD, an entire VISION stage, absent from a
 * document that describes every other core file.
 *
 * Same pattern as `LaunchGuideProductsTest` and `ReleaseChecklistTest` — make
 * the code the authority and the document the thing that must keep up, so the
 * build says so rather than a human noticing months later.
 *
 * Scoped to `:core` deliberately. The app-side map groups screens by folder
 * ("`ui/screens/` — Common, Home, Campaign, Duel, Shop, Settings, Help"),
 * which is a good way to document them and a bad thing to assert filenames
 * against — a naive check there produces false positives, as one did while
 * writing this.
 */
class ArchitectureMapTest {

    private fun archDoc(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(4) {
            val candidate = dir?.resolve("docs/ARCHITECTURE.md")
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        return null
    }

    private fun coreSources(): List<File>? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(4) {
            val candidate = dir?.resolve("core/src/main/kotlin/io/github/stan2032/gravitygambit/core")
            if (candidate != null && candidate.isDirectory) {
                return candidate.listFiles { f -> f.name.endsWith(".kt") }?.sortedBy { it.name }
            }
            dir = dir?.parentFile
        }
        return null
    }

    @Test
    fun everyCoreSourceFileIsInTheArchitectureMap() {
        val doc = archDoc()
        val sources = coreSources()
        assumeTrue("could not locate docs/ARCHITECTURE.md", doc != null)
        assumeTrue("could not locate the :core source directory", sources != null)

        val text = doc!!.readText()
        val missing = sources!!.map { it.name }.filterNot { text.contains(it) }
        assertTrue(
            "docs/ARCHITECTURE.md's file map does not mention $missing. The map is how a reader " +
                "finds their way around, and nothing fails when it goes stale — which is why it " +
                "has drifted before. Add a line describing what the file holds.",
            missing.isEmpty(),
        )
    }
}
