package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fundamental guarantee this game sells: **gravity is not optional**.
 *
 * A player reported clearing a level with a straight shot, using no gravity at
 * all, and taking three stars for it. They were right that the level had no
 * purpose. The hit-rate solver never asked the question that matters — it
 * measured what fraction of the whole aim space connects, which says nothing
 * about whether the first thing anyone tries works.
 *
 * These tests hold the line: no generated level may fall to "point at it and
 * fire", and the direct corridor must actually be furnished.
 */
class PurposeTest {

    @Test
    fun noCampaignLevelFallsToAStraightShot() {
        val offenders = ArrayList<String>()
        for (sector in 0 until 3) {
            for (level in 0 until Seeds.LEVELS_PER_SECTOR) {
                val d = Seeds.campaignDifficulty(sector, level)
                val spec = SystemGenerator.generate(Seeds.campaign(sector, level), d, GameMode.CAMPAIGN)
                if (SystemGenerator.fallsToNaiveShot(spec, 0)) offenders.add("s$sector l$level (d$d)")
            }
        }
        assertTrue(
            "levels beatable by aiming straight at the target: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun noDailyFallsToAStraightShot() {
        val offenders = ArrayList<String>()
        for (offset in 0 until 14) {
            val day = Seeds.LAUNCH_EPOCH_DAY + offset
            val spec = SystemGenerator.generate(Seeds.daily(day), Seeds.DAILY_DIFFICULTY, GameMode.DAILY)
            if (SystemGenerator.fallsToNaiveShot(spec, 0)) offenders.add("day+$offset")
        }
        assertTrue("dailies beatable by a straight shot: $offenders", offenders.isEmpty())
    }

    @Test
    fun neitherDuelSeatCanJustPointAndFire() {
        val offenders = ArrayList<String>()
        repeat(6) { i ->
            val spec = SystemGenerator.generate(
                Seeds.duelRandom(i * 4099L), Seeds.DUEL_DIFFICULTY, GameMode.DUEL,
            )
            if (SystemGenerator.fallsToNaiveShot(spec, 0)) offenders.add("duel$i seat0")
            if (SystemGenerator.fallsToNaiveShot(spec, 1)) offenders.add("duel$i seat1")
        }
        assertTrue("duels winnable by a straight shot: $offenders", offenders.isEmpty())
    }

    @Test
    fun theCorridorBetweenStationsIsFurnished() {
        // Something substantial must sit near the direct line — the "keeper".
        for (sector in 0 until 2) {
            for (level in 0 until Seeds.LEVELS_PER_SECTOR step 2) {
                val d = Seeds.campaignDifficulty(sector, level)
                val spec = SystemGenerator.generate(Seeds.campaign(sector, level), d, GameMode.CAMPAIGN)
                val a = spec.stations[0]
                val b = spec.stations[1]
                val lx = b.x - a.x
                val ly = b.y - a.y
                val len = Math.sqrt(lx * lx + ly * ly)
                val nearest = spec.bodies
                    .filter { it.kind != BodyKind.REPULSOR && it.kind != BodyKind.COMET }
                    .minOf { body ->
                        // Perpendicular distance from the body to the corridor.
                        Math.abs((body.x - a.x) * ly - (body.y - a.y) * lx) / len - body.radius
                    }
                assertTrue(
                    "s$sector l$level: corridor is wide open (clearance $nearest)",
                    nearest < 40.0,
                )
            }
        }
    }
}
