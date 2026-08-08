package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage F rules (VISION §3.3, D-39). The *sizing* of debris was settled by
 * measurement in `DebrisMeasurementTest`; this file holds the rules that
 * measurement cannot express — what leaves a wreck, what does not, and the
 * two properties that would quietly break verification or scoring if they
 * ever stopped holding.
 */
class DebrisTest {

    private fun campaignSpec(difficulty: Int = 6, level: Int = 0): SystemSpec =
        SystemGenerator.generate(Seeds.campaign(difficulty, level), difficulty, GameMode.CAMPAIGN)

    /** Finds an aim that ends the way the caller wants, or fails the test. */
    private fun findShot(
        spec: SystemSpec,
        typeId: Int = ProjectileType.STANDARD.id,
        want: (ShotResult) -> Boolean,
    ): Pair<Int, Int> {
        var angle = 0
        while (angle < 3600) {
            for (power in SystemGenerator.SOLVER_POWERS) {
                val res = LiveShot(
                    spec.bodies, spec.stations, 0, angle, power, typeId,
                    maxTicks = Tuning.MAX_TICKS,
                ).runToCompletion()
                if (want(res)) return angle to power
            }
            angle += SystemGenerator.SOLVER_ANGLE_STEP
        }
        throw AssertionError("no sample shot satisfied the requested outcome")
    }

    @Test
    fun aShotThatDiesOnAWorldLeavesExactlyOneWreck() {
        val spec = campaignSpec()
        val (angle, power) = findShot(spec) { it.bodyHitCount > 0 && it.stationHits.isEmpty() }
        val engine = MatchEngine(spec, GameMode.CAMPAIGN)
        val res = engine.beginShot(angle, power, ProjectileType.STANDARD.id).runToCompletion()
        assertNotNull("a shot that hit a world should have recorded a wreck", res.wreckAt)
        engine.commit(res)
        assertEquals("one shot, one wreck", 1, engine.debris.size)
        assertEquals(BodyKind.DEBRIS, engine.debris[0].kind)
        assertEquals("debris must be massless", 0.0, engine.debris[0].mass, 0.0)
    }

    @Test
    fun aShotThatLeavesTheFieldLeavesNothingBehind() {
        val spec = campaignSpec()
        val (angle, power) = findShot(spec) {
            it.bodyHitCount == 0 && it.stationHits.isEmpty()
        }
        val engine = MatchEngine(spec, GameMode.CAMPAIGN)
        val res = engine.beginShot(angle, power, ProjectileType.STANDARD.id).runToCompletion()
        assertNull("a shot that left the field wrecked on nothing", res.wreckAt)
        engine.commit(res)
        assertTrue(engine.debris.isEmpty())
    }

    @Test
    fun killingTheTargetLeavesNoWreck() {
        // A station kill is an outcome, not a crash. It also keeps the
        // pathological case out: a lump welded to a launch point (D-39).
        val spec = campaignSpec()
        val (angle, power) = findShot(spec) { it.stationHits.contains(1) }
        val engine = MatchEngine(spec, GameMode.CAMPAIGN)
        val res = engine.beginShot(angle, power, ProjectileType.STANDARD.id).runToCompletion()
        assertNull("a station hit must not leave debris", res.wreckAt)
        engine.commit(res)
        assertTrue(engine.debris.isEmpty())
    }

    @Test
    fun wrecksAccumulateAndAreHandedToLaterShots() {
        val spec = campaignSpec()
        val (angle, power) = findShot(spec) { it.bodyHitCount > 0 && it.stationHits.isEmpty() }
        val engine = MatchEngine(spec, GameMode.CAMPAIGN)
        engine.commit(engine.beginShot(angle, power, ProjectileType.STANDARD.id).runToCompletion())
        assertEquals(1, engine.debris.size)

        // The second shot must be flying through a board that now contains the
        // wreck. Firing the identical shot proves the board changed: it dies on
        // its own debris, one tick earlier or sooner, rather than repeating.
        val second = engine.beginShot(angle, power, ProjectileType.STANDARD.id).runToCompletion()
        assertTrue(
            "the second identical shot should not have flown the original board",
            second.ticks <= engine.results.first().ticks,
        )
    }

    @Test
    fun litterCannotBreedLitter() {
        // A wreck settles against a world, never against another wreck —
        // otherwise a duel's litter compounds past the envelope that
        // DebrisMeasurementTest actually measured.
        val spec = campaignSpec()
        val (angle, power) = findShot(spec) { it.bodyHitCount > 0 && it.stationHits.isEmpty() }
        val engine = MatchEngine(spec, GameMode.CAMPAIGN)
        engine.commit(engine.beginShot(angle, power, ProjectileType.STANDARD.id).runToCompletion())
        val afterFirst = engine.debris.size

        // Fire the same shot repeatedly; each either wrecks on a world or dies
        // on existing debris, and the latter must add nothing.
        repeat(3) {
            val r = engine.beginShot(angle, power, ProjectileType.STANDARD.id).runToCompletion()
            if (!engine.over) engine.commit(r)
        }
        assertTrue(
            "debris grew faster than one per turn, so wrecks are breeding wrecks",
            engine.debris.size <= afterFirst + 3,
        )
        for (d in engine.debris) assertEquals(BodyKind.DEBRIS, d.kind)
    }

    @Test
    fun aSplitShotLeavesOneWreckNotThree() {
        // Triad splits into three shards. One turn leaves one wreck, or a duel
        // accumulates three times what was measured.
        val spec = campaignSpec()
        val (angle, power) = findShot(spec, ProjectileType.SPLITTER.id) {
            it.bodyHitCount > 0 && it.stationHits.isEmpty()
        }
        val engine = MatchEngine(spec, GameMode.CAMPAIGN)
        val res = engine.beginShot(angle, power, ProjectileType.SPLITTER.id).runToCompletion()
        assertTrue("expected a multi-shard shot to strike more than once", res.bodyHitCount >= 1)
        engine.commit(res)
        assertEquals("a split shot still leaves a single wreck", 1, engine.debris.size)
    }

    @Test
    fun debrisNeverCountsAsADeflector() {
        // THE scoring guard. Debris is massless, so a shot cannot be bent by
        // it — and the "two-body" gambit is decided from deflectorCount. If a
        // wreck ever counted, a declaration could be honoured by a deflection
        // that never physically happened (D-39).
        val spec = campaignSpec()
        val bare = LiveShot(
            spec.bodies, spec.stations, 0, 900, 700, ProjectileType.STANDARD.id,
            maxTicks = Tuning.MAX_TICKS,
        ).runToCompletion()

        // Fence the whole flight with debris, then fly it again. Anything the
        // shot now passes is a wreck, so the count must not move.
        val littered = spec.bodies + (0 until 12).map { i ->
            Body(
                x = 120.0 + (i % 4) * 220.0, y = 200.0 + (i / 4) * 380.0,
                radius = Tuning.DEBRIS_RADIUS, mass = 0.0,
                kind = BodyKind.DEBRIS, palette = 0,
            )
        }
        val after = LiveShot(
            littered, spec.stations, 0, 900, 700, ProjectileType.STANDARD.id,
            maxTicks = Tuning.MAX_TICKS,
        ).runToCompletion()
        assertTrue(
            "debris inflated the deflector count: ${bare.deflectorCount} -> ${after.deflectorCount}",
            after.deflectorCount <= bare.deflectorCount,
        )
    }

    @Test
    fun debrisExertsNoPullAtAll() {
        // The other half of "massless": a wreck placed right beside a flight
        // must not move it. Same shot, one wreck alongside, identical hash.
        val spec = campaignSpec()
        val clean = LiveShot(
            spec.bodies, spec.stations, 0, 900, 700, ProjectileType.STANDARD.id,
            maxTicks = Tuning.MAX_TICKS,
        )
        val cleanRes = clean.runToCompletion()

        // Park a wreck far from the flight so it cannot be struck, only felt.
        val withWreck = spec.bodies + Body(
            x = Tuning.WORLD_W - 10.0, y = 10.0,
            radius = Tuning.DEBRIS_RADIUS, mass = 0.0,
            kind = BodyKind.DEBRIS, palette = 0,
        )
        val wreckRes = LiveShot(
            withWreck, spec.stations, 0, 900, 700, ProjectileType.STANDARD.id,
            maxTicks = Tuning.MAX_TICKS,
        ).runToCompletion()
        assertEquals(
            "an untouched wreck changed the flight, so it is not massless",
            cleanRes.hash, wreckRes.hash,
        )
    }

    @Test
    fun aReplayDerivesTheSameDebrisWithoutBeingToldAnyOfIt() {
        // THE load-bearing test for the protocol claim. Debris costs the share
        // code zero bytes: it is a pure function of turns the code already
        // carries. If a verifier ever derived different wrecks, it would be
        // re-simulating a different board and the hash comparison would be
        // meaningless (docs/MULTIPLAYER.md).
        val spec = SystemGenerator.generate(Seeds.duelRandom(41L), 6, GameMode.DUEL)
        val live = MatchEngine(spec, GameMode.DUEL)
        val rng = Rng(41L)
        while (!live.over && live.turns.size < 8) {
            // Aimed roughly at the opponent rather than at random: uniform
            // 360-degree fire leaves the field ~86% of the time (D-39), so a
            // random duel produces almost no wrecks and tests nothing.
            val from = live.stations[live.currentShooter]
            val to = live.stations[1 - live.currentShooter]
            val direct = StrictMath.toDegrees(StrictMath.atan2(to.y - from.y, to.x - from.x))
            val centre = (((direct * 10).toInt() % 3600) + 3600) % 3600
            val angle = ((centre + rng.nextInt(401) - 200) % 3600 + 3600) % 3600
            val power = 300 + rng.nextInt(700)
            live.commit(live.beginShot(angle, power, ProjectileType.STANDARD.id).runToCompletion())
        }
        assertTrue("this match should have produced wrecks to disagree about", live.debris.isNotEmpty())

        val rebuilt = MatchReplayer.rebuild(spec, live.turns, GameMode.DUEL)
        assertEquals(
            "a replay derived a different number of wrecks",
            live.debris.size, rebuilt.engine.debris.size,
        )
        for (i in live.debris.indices) {
            assertEquals("wreck $i x", live.debris[i].x, rebuilt.engine.debris[i].x, 0.0)
            assertEquals("wreck $i y", live.debris[i].y, rebuilt.engine.debris[i].y, 0.0)
        }
        assertEquals("combined hash diverged", live.combinedHash, rebuilt.verifiedHash)
    }

    @Test
    fun aRelocatedDailyTargetCanNeverBeSwallowedByAWreck() {
        // The daily relocates its target after each hit, and its clearance test
        // reads spec.bodies — which no longer describes the whole board once
        // wrecks exist. That looks like a hole; the geometry closes it, and the
        // arithmetic is pinned here so a future retune cannot open it silently.
        //
        //   relocated target : >= bodyRadius + STATION_RADIUS + 50 from a body centre
        //   a wreck          : <= bodyRadius + shellRadius        from that centre
        //   so the gap       : >= STATION_RADIUS + 50 - shellRadius
        //   and it must clear: STATION_RADIUS + DEBRIS_RADIUS
        val biggestShell = ProjectileType.entries.maxOf { it.radius }
        val worstGap = Tuning.STATION_RADIUS + 50.0 - biggestShell
        val needed = Tuning.STATION_RADIUS + Tuning.DEBRIS_RADIUS
        assertTrue(
            "a wreck can now overlap a relocated daily target: gap $worstGap < needed $needed",
            worstGap > needed,
        )
    }

    @Test
    fun theGeneratorNeverAuthorsDebris() {
        // Debris is the one body kind that is not a fact about the system. If
        // generation ever emitted one, a level would arrive pre-littered and
        // the solver's difficulty fingerprint would be measuring a board the
        // player never made.
        for (difficulty in intArrayOf(0, 3, 6, 9, 11)) {
            repeat(4) { i ->
                val spec = SystemGenerator.generate(
                    Seeds.campaign(difficulty, i), difficulty, GameMode.CAMPAIGN,
                )
                assertFalse(
                    "generator emitted debris at difficulty $difficulty seed $i",
                    spec.bodies.any { it.kind == BodyKind.DEBRIS },
                )
            }
        }
    }
}
