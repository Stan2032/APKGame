package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rule-level coverage for MatchEngine that CoreTests' pipeline tests don't
 * reach: duel damage bookkeeping, winner edge cases, turn caps, star math.
 * Uses hand-built specs (no generator) so outcomes are fully controlled.
 */
class MatchRulesTests {

    /**
     * Minimal open field: a tiny, nearly massless star placed OFF the direct
     * firing line between the stations, so a straight vertical shot connects.
     */
    private fun openSpec(): SystemSpec = SystemSpec(
        seed = 1L,
        difficulty = 0,
        name = "Test Range",
        bodies = listOf(
            Body(150.0, Tuning.WORLD_H / 2, 30.0, 1.0, BodyKind.STAR, 0),
        ),
        stations = listOf(
            Station(Tuning.WORLD_W / 2, Tuning.WORLD_H - 150.0, 0),
            Station(Tuning.WORLD_W / 2, 150.0, 1),
        ),
        solverHitRate = 1.0,
    )

    /** Straight-up shot from P0 lands on P1's station in the open field. */
    private fun directHit(engine: MatchEngine): ShotResult {
        val shot = engine.beginShot(2700, 1000, ProjectileType.STANDARD.id)
        return shot.runToCompletion()
    }

    @Test
    fun duelDamageAndWinner() {
        val engine = MatchEngine(openSpec(), GameMode.DUEL)
        assertEquals(0, engine.currentShooter)
        // P0 hits three times; P1 fires into empty space in between.
        repeat(3) {
            val hit = directHit(engine)
            assertTrue("P0 shot should connect", hit.stationHits.contains(1))
            engine.commit(hit)
            if (!engine.over) {
                assertEquals(1, engine.currentShooter)
                // P1 fires sideways: flies out of bounds.
                engine.commit(engine.beginShot(0, 1000, ProjectileType.STANDARD.id).runToCompletion())
            }
        }
        assertTrue(engine.over)
        assertEquals(0, engine.hp[1])
        assertEquals(100, engine.hp[0])
        assertEquals(0, engine.duelWinner())
    }

    @Test
    fun duelTurnCapIsDraw() {
        val engine = MatchEngine(openSpec(), GameMode.DUEL)
        while (!engine.over) {
            // Both players deliberately miss (fire sideways out of bounds).
            engine.commit(engine.beginShot(0, 1000, ProjectileType.STANDARD.id).runToCompletion())
        }
        assertEquals(Tuning.DUEL_MAX_TURNS, engine.shotsUsed)
        assertEquals(100, engine.hp[0])
        assertEquals(100, engine.hp[1])
        assertNull("no winner at full HP turn cap", engine.duelWinner())
    }

    @Test
    fun campaignStarsByShotsUsed() {
        // A one-shot clear that used no gravity is NOT three stars: the top
        // rating means "you played the game", not "you got there quickly".
        // This is the exact case a player hit — straight shot, full marks.
        var engine = MatchEngine(openSpec(), GameMode.CAMPAIGN)
        engine.commit(directHit(engine))
        assertTrue(engine.over && engine.campaignWon)
        assertEquals("a straight lob must not earn three stars", 2, engine.starsEarned())

        // Miss, miss, hit = 1 star (3 shots).
        engine = MatchEngine(openSpec(), GameMode.CAMPAIGN)
        repeat(2) { engine.commit(engine.beginShot(0, 1000, 0).runToCompletion()) }
        engine.commit(directHit(engine))
        assertTrue(engine.campaignWon)
        assertEquals(1, engine.starsEarned())

        // Four misses = loss, zero stars.
        engine = MatchEngine(openSpec(), GameMode.CAMPAIGN)
        repeat(Tuning.CAMPAIGN_SHOTS) { engine.commit(engine.beginShot(0, 1000, 0).runToCompletion()) }
        assertTrue(engine.over)
        assertTrue(!engine.campaignWon)
        assertEquals(0, engine.starsEarned())
    }

    @Test
    fun threeStarsRequireWorkingTheField() {
        // Same one-shot clear, but the shot falls past a heavy star first and
        // arrives carrying speed it stole from the field: that is three stars.
        val spec = SystemSpec(
            seed = 3L, difficulty = 0, name = "Assist Range",
            bodies = listOf(Body(500.0, 800.0, 60.0, 9.0e6, BodyKind.STAR, 0)),
            stations = listOf(Station(500.0, 1450.0, 0), Station(500.0, 150.0, 1)),
            solverHitRate = 1.0,
        )
        val engine = MatchEngine(spec, GameMode.CAMPAIGN)
        val res = engine.beginShot(2700, 1000, ProjectileType.STANDARD.id).runToCompletion()
        engine.commit(res)
        if (engine.campaignWon) {
            assertTrue("this shot should have gained speed", res.slingshotGain > 0)
            assertEquals(3, engine.starsEarned())
        }
    }

    @Test
    fun dailyScoresAndRelocatesTarget() {
        val spec = openSpec()
        val engine = MatchEngine(spec, GameMode.DAILY)
        val originalTarget = engine.stations[1]
        engine.commit(directHit(engine))
        assertEquals(1, engine.dailyHits)
        assertTrue("hit must score", engine.score >= Tuning.DAILY_HIT_SCORE)
        assertTrue("target must relocate after a hit", engine.stations[1] != originalTarget)
        // Match runs the full five shots regardless of hits.
        while (!engine.over) {
            engine.commit(engine.beginShot(0, 1000, 0).runToCompletion())
        }
        assertEquals(Tuning.DAILY_SHOTS, engine.shotsUsed)
    }

    @Test
    fun heavyDamageAndPowerCapApply() {
        val engine = MatchEngine(openSpec(), GameMode.DUEL)
        val hit = engine.beginShot(2700, 1000, ProjectileType.HEAVY.id).runToCompletion()
        assertTrue(hit.stationHits.contains(1))
        engine.commit(hit)
        assertEquals(100 - ProjectileType.HEAVY.damage, engine.hp[1])
    }

    @Test
    fun seedsAndDailyNumberAreStable() {
        assertEquals(Seeds.campaign(2, 5), Seeds.campaign(2, 5))
        assertTrue(Seeds.campaign(2, 5) != Seeds.campaign(5, 2))
        assertEquals(1, Seeds.dailyNumber(Seeds.LAUNCH_EPOCH_DAY))
        assertEquals(4, Seeds.dailyNumber(Seeds.LAUNCH_EPOCH_DAY + 3))
        assertEquals(0, Seeds.campaignDifficulty(0, 0))
        assertEquals(11, Seeds.campaignDifficulty(9, 11)) // capped
    }

    @Test
    fun previewRespectsTickBudget() {
        val spec = openSpec()
        val paths = Preview.trajectories(
            spec.bodies, spec.stations, 0, 900, 200, ProjectileType.STANDARD.id, maxTicks = 90,
        )
        assertTrue(paths.isNotEmpty())
        assertTrue("preview must not exceed its tick budget", paths.all { it.size <= 90 })
    }
}
