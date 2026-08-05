package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Moving bodies were the first real test of the determinism design: a comet
 * drifts and a pulsar's gravity breathes, yet both must stay bit-identical
 * across devices. They do, because position and mass are pure functions of
 * the integer tick rather than mutable state (D-22).
 */
class MovingBodyTest {

    private fun comet(vx: Double, vy: Double) = Body(
        x = 300.0, y = 800.0, radius = 16.0, mass = 2.0e5,
        kind = BodyKind.COMET, palette = 9, vx = vx, vy = vy,
    )

    private fun field(vararg extra: Body) = SystemSpec(
        seed = 7L, difficulty = 3, name = "Drift Test",
        bodies = listOf(
            Body(500.0, 800.0, 60.0, 7.0e6, BodyKind.STAR, 0),
        ) + extra,
        stations = listOf(Station(500.0, 1450.0, 0), Station(500.0, 150.0, 1)),
        solverHitRate = 1.0,
    )

    @Test
    fun cometPositionIsAPureFunctionOfTick() {
        val c = comet(vx = 60.0, vy = 0.0)
        assertEquals(300.0, c.xAt(0), 1e-12)
        // 60 units/sec for 60 ticks (1 second) = +60 units.
        assertEquals(360.0, c.xAt(60), 1e-9)
        // Same tick always yields the same answer, in any order.
        assertEquals(c.xAt(500), c.xAt(500), 0.0)
        assertEquals(c.yAt(500), c.yAt(500), 0.0)
    }

    @Test
    fun cometWrapsRatherThanEscapingForever() {
        val c = comet(vx = 200.0, vy = 0.0)
        // After a long flight it must still be somewhere sane, not at infinity.
        val x = c.xAt(6000)
        assertTrue("comet left the world: $x", x > -Tuning.WRAP_MARGIN - 1 && x < Tuning.WORLD_W + Tuning.WRAP_MARGIN + 1)
    }

    @Test
    fun movingBodiesStayDeterministic() {
        val spec = field(comet(vx = 55.0, vy = 0.0))
        val a = LiveShot(spec.bodies, spec.stations, 0, 2700, 800, 0).runToCompletion()
        val b = LiveShot(spec.bodies, spec.stations, 0, 2700, 800, 0).runToCompletion()
        assertEquals(a.hash, b.hash)
        assertEquals(a.ticks, b.ticks)
        assertEquals(a.peakSpeed, b.peakSpeed, 0.0)
    }

    @Test
    fun aMovingCometChangesTheOutcomeVersusAStaticOne() {
        val moving = field(comet(vx = 70.0, vy = 0.0))
        val still = field(comet(vx = 0.0, vy = 0.0))
        val m = LiveShot(moving.bodies, moving.stations, 0, 2700, 900, 0).runToCompletion()
        val s = LiveShot(still.bodies, still.stations, 0, 2700, 900, 0).runToCompletion()
        assertNotEquals("motion must actually affect the simulation", m.hash, s.hash)
    }

    @Test
    fun pulsarMassBreathesButRepeats() {
        val p = Body(500.0, 700.0, 30.0, 2.0e6, BodyKind.PULSAR, 8)
        val atZero = p.massAt(0)
        val atHalf = p.massAt((Tuning.PULSAR_PERIOD / 2).toInt())
        assertTrue("pulsar must vary", atZero > atHalf)
        // One full period later it is back where it started.
        assertEquals(atZero, p.massAt(Tuning.PULSAR_PERIOD.toInt()), 1e-6)
        // Never negative — a pulsar attracts, it just varies how hard.
        for (t in 0..420) assertTrue(p.massAt(t) > 0)
    }

    @Test
    fun slingshotBonusRewardsStealingSpeedFromTheField() {
        // A shot fired straight up past a heavy star gains speed on the way in.
        val spec = field()
        val res = LiveShot(spec.bodies, spec.stations, 0, 2700, 300, 0).runToCompletion()
        assertTrue("peak speed must be recorded", res.peakSpeed > 0)
        assertTrue("a shot falling toward a star must gain speed", res.slingshotGain > 0)
    }

    @Test
    fun cometStrikePaysOutOnItsOwn() {
        // Park a comet directly in the shot's path and confirm the bonus.
        val spec = field(
            Body(500.0, 1200.0, 26.0, 1.0e5, BodyKind.COMET, 9, vx = 0.0, vy = 0.0)
        )
        val res = LiveShot(spec.bodies, spec.stations, 0, 2700, 900, 0).runToCompletion()
        assertTrue("expected a comet strike", res.styleNotes.any { it.startsWith("Comet strike") })
        assertTrue(res.styleBonus >= Tuning.COMET_HIT_BONUS)
    }

    @Test
    fun anchorsBendTheShotAndStayDeterministic() {
        val spec = field()
        val plain = LiveShot(spec.bodies, spec.stations, 0, 2700, 700, 0).runToCompletion()
        val anchors = listOf(Anchor(640, 1100, attract = true))
        val bent = LiveShot(spec.bodies, spec.stations, 0, 2700, 700, 0, anchors = anchors).runToCompletion()
        assertNotEquals("an anchor must change the trajectory", plain.hash, bent.hash)
        val again = LiveShot(spec.bodies, spec.stations, 0, 2700, 700, 0, anchors = anchors).runToCompletion()
        assertEquals("anchored shots must stay deterministic", bent.hash, again.hash)
    }

    @Test
    fun repellingAnchorPushesTheOppositeWayFromAnAttractingOne() {
        val spec = field()
        val pull = LiveShot(
            spec.bodies, spec.stations, 0, 2700, 700, 0,
            anchors = listOf(Anchor(600, 1100, attract = true)),
        ).runToCompletion()
        val push = LiveShot(
            spec.bodies, spec.stations, 0, 2700, 700, 0,
            anchors = listOf(Anchor(600, 1100, attract = false)),
        ).runToCompletion()
        assertNotEquals("attract and repel must differ", pull.hash, push.hash)
    }

    @Test
    fun anchorsSurviveTheShareCodeRoundTrip() {
        // Anchors are part of the shot, so a replay must reproduce them
        // exactly — otherwise a verified code would show a different shot.
        val seed = Seeds.duelRandom(555L)
        val spec = SystemGenerator.generate(seed, Seeds.DUEL_DIFFICULTY, GameMode.DUEL)
        val engine = MatchEngine(spec, GameMode.DUEL)
        val anchors = listOf(Anchor(420, 900, true), Anchor(700, 640, false))
        engine.commit(engine.beginShot(2700, 800, 0, -1, anchors).runToCompletion())
        val code = ShareCodec.encode(
            MatchRecord(GameMode.DUEL.id, Seeds.DUEL_DIFFICULTY, seed, engine.turns, engine.combinedHash)
        )
        val verified = ShareCodec.decodeAndVerify(code)
        assertNotNull("anchored matches must verify", verified)
        assertEquals(anchors, verified!!.record.turns[0].anchors)
    }

    @Test
    fun generatedSystemsWithMotionStillVerifyEndToEnd() {
        // The full PVP pipeline must survive moving bodies.
        val seed = Seeds.campaign(4, 2)
        val spec = SystemGenerator.generate(seed, 6, GameMode.CAMPAIGN)
        val engine = MatchEngine(spec, GameMode.CAMPAIGN)
        engine.commit(engine.beginShot(2700, 750, 0).runToCompletion())
        val code = ShareCodec.encode(
            MatchRecord(GameMode.CAMPAIGN.id, 6, seed, engine.turns, engine.combinedHash)
        )
        assertNotNull("codes must verify even with drifting bodies", ShareCodec.decodeAndVerify(code))
    }
}
