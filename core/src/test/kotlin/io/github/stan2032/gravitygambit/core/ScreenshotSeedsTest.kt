package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finds the levels — and for the slingshot, the exact aim — that produce each
 * seed-dependent shot in docs/STORE_LISTING.md's screenshot list.
 *
 * Why this exists. The store list says "the money shot — retake until
 * beautiful", and whether a frame is beautiful is a question for eyes, not for
 * a test. But *reaching* the moment is not: a Capture system is a ~28% roll,
 * and a slingshot worth photographing needs a specific shot on a specific
 * board. Grinding for those is the reason the screenshot task drags, and Play
 * will not accept a listing without them.
 *
 * So this computes coordinates a human can navigate straight to — real
 * (sector, level) pairs, not raw seeds — and the aim that produces the arc.
 * Shots 7–9 (duel handoff, season ladder, sector map) are not seed-dependent
 * and are deliberately absent.
 *
 * It also found that one requested shot **could never be taken**: the list
 * asked for a "binary-star field", and this game has no binary stars (D-41).
 * A shot list is a set of claims about the artifact like any other.
 *
 * Run: `./gradlew :core:test --tests "*ScreenshotSeedsTest*"`
 */
class ScreenshotSeedsTest {

    /** Real campaign coordinates: what a player taps, not a raw seed. */
    private class Coord(val sector: Int, val level: Int) {
        val difficulty get() = Seeds.campaignDifficulty(sector, level)
        val seed get() = Seeds.campaign(sector, level)
        fun spec(): SystemSpec = SystemGenerator.generate(seed, difficulty, GameMode.CAMPAIGN)
        override fun toString() = "sector $sector level ${level + 1}"
    }

    /** The span a player can actually reach early enough to photograph. */
    private fun coords(): List<Coord> =
        (0..4).flatMap { s -> (0 until 12).map { l -> Coord(s, l) } }

    @Test
    fun findTheScreenshotMoments() {
        println("=== screenshot seed catalogue (docs/STORE_LISTING.md) ===")

        // --- Shot 1: the slingshot. A hit, with velocity stolen from the field
        // and a close pass, which is what makes the arc read as a slingshot
        // rather than a long lob.
        var bestGain = 0.0
        var bestLine = "none found"
        for (c in coords()) {
            val spec = c.spec()
            var angle = 0
            while (angle < 3600) {
                for (power in SystemGenerator.SOLVER_POWERS) {
                    val res = LiveShot(
                        spec.bodies, spec.stations, 0, angle, power,
                        ProjectileType.STANDARD.id, maxTicks = Tuning.MAX_TICKS,
                    ).runToCompletion()
                    if (!res.stationHits.contains(1)) continue
                    // Weight the pass distance in: a big gain from far away is
                    // a boring straight line on screen.
                    if (res.slingshotGain > bestGain && res.minSurfaceDist < 60.0) {
                        bestGain = res.slingshotGain
                        bestLine = "$c (d${c.difficulty})  aim=$angle power=$power  " +
                            "gain=%.0f  passes %.0fu from a surface  %d ticks  %s".format(
                                res.slingshotGain, res.minSurfaceDist, res.ticks,
                                res.styleNotes.joinToString("; ").ifEmpty { "no style bonus" },
                            )
                    }
                }
                angle += SystemGenerator.SOLVER_ANGLE_STEP
            }
        }
        println("1. SLINGSHOT   $bestLine")

        // --- Shot 2: a heavy two-body field to aim through.
        //
        // The store list originally asked for a "binary-star field". No such
        // thing exists: the generator makes at most ONE star, and its
        // `doubleKeeper` path adds a second large PLANET, never a second star.
        // Measured across 720 generated systems the star-count histogram is
        // {0: 93, 1: 627} — a maximum of one. The obtainable equivalent is a
        // double keeper: two massive bodies with a route threading between.
        val heavy = coords()
            .map { it to it.spec().bodies.filter { b -> b.radius >= 45.0 } }
            .filter { (_, big) -> big.size >= 2 }
            .maxByOrNull { (_, big) -> big.sumOf { b -> b.radius } }
        println(
            "2. TWO KEEPERS " + (heavy?.let { (c, big) ->
                "$c (d${c.difficulty})  ${big.size} bodies r>=45: " +
                    big.joinToString(", ") { b -> "${b.kind} r=%.0f".format(b.radius) }
            } ?: "none in range"),
        )
        // Structural, not sampled: guards the correction above against a future
        // generator change quietly making the old store copy true again.
        val everBinary = (0..11).any { d ->
            (0 until 30).any { l ->
                SystemGenerator.generate(Seeds.campaign(d, l), d, GameMode.CAMPAIGN)
                    .bodies.count { it.kind == BodyKind.STAR } >= 2
            }
        }
        println("   (binary star systems exist: $everBinary — store copy assumes false)")

        // --- Shot 3: anchors visibly bending a preview. anchorLeverage is the
        // measured fraction of missing shots one anchor can rescue, so a high
        // value is exactly a board where a placed mass changes the picture.
        val anchor = coords().maxByOrNull { it.spec().anchorLeverage }
        println(
            "3. ANCHORS     " + (anchor?.let {
                "$it (d${it.difficulty})  leverage=%.2f".format(it.spec().anchorLeverage)
            } ?: "none"),
        )

        // --- Shot 4: the gambit dialog, with as many honourable declarations
        // as the arena can prove — an empty-looking menu sells nothing.
        val gambit = coords().maxByOrNull { Integer.bitCount(it.spec().gambitMasks[0]) }
        println(
            "4. GAMBITS     " + (gambit?.let {
                val m = it.spec().gambitMasks[0]
                val names = Gambit.declarable.filter { g -> (m shr g.id) and 1 == 1 }
                    .joinToString(", ") { g -> g.name }
                "$it (d${it.difficulty})  ${Integer.bitCount(m)} offered: $names"
            } ?: "none"),
        )

        // --- Shot 5: a Capture system, with the ring part-filled. Capture
        // forces a RETRO shell, so the aim below includes the brake tick.
        var captureLine = "none in range"
        outer@ for (c in coords()) {
            val spec = c.spec()
            if (spec.objective != Objective.CAPTURE) continue
            var angle = 0
            while (angle < 3600) {
                for (power in SystemGenerator.SOLVER_POWERS) {
                    for (brake in intArrayOf(40, 70, 100, 140, 190)) {
                        val res = LiveShot(
                            spec.bodies, spec.stations, 0, angle, power,
                            ProjectileType.RETRO.id, presetBrakeTick = brake,
                            maxTicks = Tuning.MAX_TICKS, markedBody = spec.markedBody,
                        ).runToCompletion()
                        if (res.captureDwell >= Tuning.CAPTURE_TICKS) {
                            captureLine = "$c (d${c.difficulty})  aim=$angle power=$power " +
                                "brake@tick=$brake  dwell=${res.captureDwell} " +
                                "(needs ${Tuning.CAPTURE_TICKS})"
                            break@outer
                        }
                    }
                }
                angle += SystemGenerator.SOLVER_ANGLE_STEP
            }
        }
        println("5. CAPTURE     $captureLine")

        // --- Bonus, new in 0.18.0: a board wearing its own history. Debris is
        // the most photogenic thing added since the shot list was written.
        val litter = coords().firstOrNull { c ->
            val spec = c.spec()
            val engine = MatchEngine(spec, GameMode.CAMPAIGN)
            var made = 0
            var angle = 0
            while (angle < 3600 && !engine.over) {
                val res = engine.beginShot(angle, 700, ProjectileType.STANDARD.id).runToCompletion()
                if (res.wreckAt != null) { engine.commit(res); made++ }
                angle += SystemGenerator.SOLVER_ANGLE_STEP
            }
            made >= 3
        }
        println(
            "6. DEBRIS      " + (litter?.let { "$it (d${it.difficulty})  reaches 3 wrecks in 4 shots" }
                ?: "none in range"),
        )
        println("=== shots 7-9 (duel handoff, season ladder, sector map) are not seed-dependent ===")
    }

    /**
     * The catalogue published in docs/STORE_LISTING.md, checked against the
     * generator rather than trusted.
     *
     * A screenshot coordinate is a claim about the artifact like any other, and
     * it rots the moment generation changes — silently, because nothing fails
     * when a doc goes stale. It has already gone wrong once in the other
     * direction: the list asked for a "binary-star field" that cannot exist
     * (D-41). Levels are written **1-indexed** in the doc, as a player reads
     * them, so the -1 below is the translation and not an off-by-one.
     */
    @Test
    fun thePublishedScreenshotCatalogueIsStillTrue() {
        // Shot 1 — the slingshot, at the exact published aim.
        val slingshot = Coord(4, 11 - 1)
        val s1 = slingshot.spec()
        val shot1 = LiveShot(
            s1.bodies, s1.stations, 0, 3060, 300,
            ProjectileType.STANDARD.id, maxTicks = Tuning.MAX_TICKS,
        ).runToCompletion()
        assertTrue(
            "published slingshot aim no longer hits: ${slingshot}",
            shot1.stationHits.contains(1),
        )
        assertTrue(
            "published slingshot gain fell to %.0f (doc says 230)".format(shot1.slingshotGain),
            shot1.slingshotGain > 180.0,
        )

        // Shot 2 — a double keeper, which is what replaced "binary star".
        val keepers = Coord(4, 4 - 1).spec().bodies.count { it.radius >= 45.0 }
        assertTrue("published two-keeper board now has $keepers big bodies", keepers >= 2)

        // Shot 4 — the gambit dialog is only worth photographing if the arena
        // can actually pay every declaration it displays.
        val mask = Coord(0, 1 - 1).spec().gambitMasks[0]
        assertEquals(
            "published gambit board no longer offers all four declarations",
            Gambit.declarable.size, Integer.bitCount(mask),
        )

        // Shot 5 — still a Capture system, and still capturable at the
        // published aim. Capture forces the RETRO shell.
        val capture = Coord(1, 2 - 1)
        val s5 = capture.spec()
        assertEquals(
            "published capture level is no longer a Capture system",
            Objective.CAPTURE, s5.objective,
        )
        val shot5 = LiveShot(
            s5.bodies, s5.stations, 0, 2360, 1000, ProjectileType.RETRO.id,
            presetBrakeTick = 40, maxTicks = Tuning.MAX_TICKS, markedBody = s5.markedBody,
        ).runToCompletion()
        assertTrue(
            "published capture aim now dwells ${shot5.captureDwell}, needs ${Tuning.CAPTURE_TICKS}",
            shot5.captureDwell >= Tuning.CAPTURE_TICKS,
        )

        // The correction itself: if the generator ever learns to make a real
        // binary, the store copy saying it cannot becomes the false claim.
        val everBinary = (0..11).any { d ->
            (0 until 30).any { l ->
                SystemGenerator.generate(Seeds.campaign(d, l), d, GameMode.CAMPAIGN)
                    .bodies.count { it.kind == BodyKind.STAR } >= 2
            }
        }
        assertFalse(
            "a binary star system now exists; STORE_LISTING and DESIGN.md say it cannot (D-41)",
            everBinary,
        )
    }
}
