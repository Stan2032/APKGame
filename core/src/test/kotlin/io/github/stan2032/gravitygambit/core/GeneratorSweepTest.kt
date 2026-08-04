package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Broad correctness sweep. The four-sample checks in GeneratorTest prove the
 * happy path; this walks a wide spread of real seeds because a single
 * unwinnable level shipped to a player is a one-star review, and the rare
 * paths (fallback layouts, extreme difficulties) are exactly where such a
 * level would come from.
 */
class GeneratorSweepTest {

    private fun assertSane(spec: SystemSpec, label: String) {
        assertTrue("$label: no solution exists", spec.solverHitRate > 0.0)
        assertEquals("$label: wrong station count", 2, spec.stations.size)
        assertTrue("$label: no bodies", spec.bodies.isNotEmpty())
        assertTrue("$label: unnamed", spec.name.isNotBlank())

        for (b in spec.bodies) {
            assertTrue("$label: body out of bounds", b.x > 0 && b.x < Tuning.WORLD_W)
            assertTrue("$label: body out of bounds", b.y > 0 && b.y < Tuning.WORLD_H)
            assertTrue("$label: non-positive radius", b.radius > 0)
            if (b.kind == BodyKind.REPULSOR) {
                assertTrue("$label: repulsor must have negative mass", b.mass < 0)
            } else {
                assertTrue("$label: attractor must have positive mass", b.mass > 0)
            }
        }
        for ((i, s) in spec.stations.withIndex()) {
            assertTrue("$label: station $i out of bounds", s.x > 0 && s.x < Tuning.WORLD_W)
            assertTrue("$label: station $i out of bounds", s.y > 0 && s.y < Tuning.WORLD_H)
            for (b in spec.bodies) {
                if (b.kind == BodyKind.REPULSOR) continue
                val dx = b.x - s.x
                val dy = b.y - s.y
                val min = b.radius + Tuning.STATION_RADIUS
                assertTrue(
                    "$label: station $i is inside a body",
                    dx * dx + dy * dy > min * min,
                )
            }
        }
        // The two stations must not overlap each other.
        val (a, b) = spec.stations
        val dx = a.x - b.x
        val dy = a.y - b.y
        assertTrue("$label: stations overlap", dx * dx + dy * dy > (Tuning.STATION_RADIUS * 3).let { it * it })
    }

    @Test
    fun everyCampaignLevelAcrossFourSectorsIsPlayable() {
        for (sector in 0 until 4) {
            for (level in 0 until Seeds.LEVELS_PER_SECTOR step 3) {
                val difficulty = Seeds.campaignDifficulty(sector, level)
                val spec = SystemGenerator.generate(
                    Seeds.campaign(sector, level), difficulty, GameMode.CAMPAIGN,
                )
                assertSane(spec, "campaign s$sector l$level (d$difficulty)")
            }
        }
    }

    @Test
    fun extremeDifficultiesStillProduceSolvableSystems() {
        for (difficulty in intArrayOf(0, 1, 11, 12, 20)) {
            repeat(3) { i ->
                val spec = SystemGenerator.generate(
                    Seeds.campaign(difficulty, 90 + i), difficulty, GameMode.CAMPAIGN,
                )
                assertSane(spec, "extreme d$difficulty #$i")
            }
        }
    }

    @Test
    fun aMonthOfDailiesIsPlayable() {
        for (offset in 0 until 30 step 4) {
            val day = Seeds.LAUNCH_EPOCH_DAY + offset
            val spec = SystemGenerator.generate(Seeds.daily(day), Seeds.DAILY_DIFFICULTY, GameMode.DAILY)
            assertSane(spec, "daily day+$offset")
            // Relocation targets must also stay clear of bodies.
            for (hits in 1..3) {
                val moved = DailyLogic.targetPosition(spec, hits)
                for (b in spec.bodies) {
                    if (b.kind == BodyKind.REPULSOR) continue
                    val dx = b.x - moved.x
                    val dy = b.y - moved.y
                    val min = b.radius + Tuning.STATION_RADIUS
                    assertTrue(
                        "daily day+$offset relocation $hits landed inside a body",
                        dx * dx + dy * dy > min * min,
                    )
                }
            }
        }
    }

    @Test
    fun duelSystemsAreSolvableFromBothSeats() {
        repeat(8) { i ->
            val spec = SystemGenerator.generate(
                Seeds.duelRandom(i * 7919L), Seeds.DUEL_DIFFICULTY, GameMode.DUEL,
            )
            assertSane(spec, "duel #$i")
            assertTrue("duel #$i: seat 0 cannot hit", SystemGenerator.solveRate(spec, 0) > 0.0)
            assertTrue("duel #$i: seat 1 cannot hit", SystemGenerator.solveRate(spec, 1) > 0.0)
        }
    }

    @Test
    fun everySweptSystemProducesAVerifiableCode() {
        for (difficulty in intArrayOf(0, 4, 8)) {
            val seed = Seeds.campaign(difficulty, 55)
            val spec = SystemGenerator.generate(seed, difficulty, GameMode.CAMPAIGN)
            val engine = MatchEngine(spec, GameMode.CAMPAIGN)
            engine.commit(engine.beginShot(2700, 700, 0).runToCompletion())
            val code = ShareCodec.encode(
                MatchRecord(GameMode.CAMPAIGN.id, difficulty, seed, engine.turns, engine.combinedHash)
            )
            assertNotNull("d$difficulty code failed verification", ShareCodec.decodeAndVerify(code))
        }
    }

    @Test
    fun dailyNumberingMatchesTheLaunchDate() {
        // A wrong constant would silently mislabel every daily challenge.
        assertEquals(
            LocalDate.of(2026, 8, 1),
            LocalDate.ofEpochDay(Seeds.LAUNCH_EPOCH_DAY),
        )
        assertEquals(1, Seeds.dailyNumber(Seeds.LAUNCH_EPOCH_DAY))
        assertEquals(31, Seeds.dailyNumber(LocalDate.of(2026, 8, 31).toEpochDay()))
    }
}
