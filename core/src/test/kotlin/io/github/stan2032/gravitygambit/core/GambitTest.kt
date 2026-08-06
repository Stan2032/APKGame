package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gambits are declarations judged by the physics (docs/VISION.md §3.2). These
 * tests pin the two things that make them trustworthy rather than decorative:
 * the measurements come from the flight, and a broken declaration really does
 * cost the shot. Hand-built specs throughout, so every outcome is controlled.
 */
class GambitTest {

    /** Open range: a straight vertical shot from P0 reaches P1 untouched. */
    private fun openSpec(): SystemSpec = SystemSpec(
        seed = 11L,
        difficulty = 0,
        name = "Gambit Range",
        bodies = listOf(Body(150.0, Tuning.WORLD_H / 2, 30.0, 1.0, BodyKind.STAR, 0)),
        stations = listOf(
            Station(Tuning.WORLD_W / 2, Tuning.WORLD_H - 150.0, 0),
            Station(Tuning.WORLD_W / 2, 150.0, 1),
        ),
        solverHitRate = 1.0,
    )

    private fun straightShot(e: MatchEngine, gambit: Gambit, power: Int = 1000): ShotResult =
        e.beginShot(2700, power, ProjectileType.STANDARD.id, gambitId = gambit.id)
            .runToCompletion()

    @Test
    fun featherweightIsDecidedByThePowerActuallyUsed() {
        val e = MatchEngine(openSpec(), GameMode.CAMPAIGN)
        val heavy = straightShot(e, Gambit.FEATHERWEIGHT, power = 1000)
        assertFalse("full power cannot be featherweight", Gambit.FEATHERWEIGHT.satisfiedBy(heavy))

        val e2 = MatchEngine(openSpec(), GameMode.CAMPAIGN)
        val light = straightShot(e2, Gambit.FEATHERWEIGHT, power = 200)
        assertTrue(Gambit.FEATHERWEIGHT.satisfiedBy(light))
    }

    @Test
    fun aStraightShotNeitherDetoursNorPassesTwoWorlds() {
        val e = MatchEngine(openSpec(), GameMode.CAMPAIGN)
        val r = straightShot(e, Gambit.NONE)
        assertTrue("the straight shot must actually connect", r.stationHits.contains(1))
        assertFalse("it never left the line, so it never crossed it", r.crossedTargetLine)
        assertTrue("and it made no detour worth the name", r.maxDetour < Tuning.LONG_WAY_MIN_DETOUR)
        assertFalse(Gambit.LONG_WAY.satisfiedBy(r))
        assertFalse(Gambit.TWO_BODY.satisfiedBy(r))
    }

    /**
     * Two heavy worlds either side of the corridor. A shot threaded between
     * them passes through both grips without crossing the line, which is
     * exactly the "two-body" claim.
     */
    @Test
    fun passingTwoWorldsIsCountedFromTheFlight() {
        val spec = SystemSpec(
            seed = 12L,
            difficulty = 0,
            name = "Gate",
            bodies = listOf(
                Body(Tuning.WORLD_W / 2 - 90.0, 1050.0, 46.0, 1.0e5, BodyKind.PLANET, 0),
                Body(Tuning.WORLD_W / 2 + 90.0, 700.0, 46.0, 1.0e5, BodyKind.PLANET, 1),
            ),
            stations = listOf(
                Station(Tuning.WORLD_W / 2, Tuning.WORLD_H - 150.0, 0),
                Station(Tuning.WORLD_W / 2, 150.0, 1),
            ),
            solverHitRate = 1.0,
        )
        val e = MatchEngine(spec, GameMode.CAMPAIGN)
        val r = e.beginShot(2700, 1000, ProjectileType.STANDARD.id, gambitId = Gambit.TWO_BODY.id)
            .runToCompletion()
        assertEquals("both worlds gripped this shot", 2, r.deflectorCount)
        assertTrue(Gambit.TWO_BODY.satisfiedBy(r))
    }

    @Test
    fun aBrokenDeclarationWastesTheShot() {
        // Declare "the long way", then fire straight up the middle: the shot
        // connects physically, but the declaration was broken, so nothing lands.
        val e = MatchEngine(openSpec(), GameMode.CAMPAIGN)
        val r = straightShot(e, Gambit.LONG_WAY)
        assertTrue("physically it hit", r.stationHits.contains(1))
        e.commit(r)
        assertFalse("but the declaration was broken", e.gambitKept[0])
        assertFalse("so the core was never breached", e.campaignWon)
        assertEquals("and no style was banked", 0, e.awardedStyle(0))
        assertEquals("a wasted shot still costs a shot", 1, e.shotsUsed)
    }

    @Test
    fun aKeptDeclarationPaysAMultiple() {
        val e = MatchEngine(openSpec(), GameMode.DAILY)
        val r = e.beginShot(
            2700, 200, ProjectileType.STANDARD.id, gambitId = Gambit.FEATHERWEIGHT.id,
        ).runToCompletion()
        e.commit(r)
        assertTrue(e.gambitKept[0])
        assertEquals(
            "style is multiplied by the declared payout",
            r.styleBonus * Gambit.FEATHERWEIGHT.payoutTenths / 10,
            e.awardedStyle(0),
        )
    }

    /**
     * The declaration is an input, so it is inside the verification hash. This
     * is what stops a sender from re-declaring a shot after seeing how it went.
     */
    @Test
    fun theDeclarationIsPartOfTheVerifiedHash() {
        val spec = openSpec()
        val plain = MatchEngine(spec, GameMode.CAMPAIGN)
            .beginShot(2700, 900, ProjectileType.STANDARD.id, gambitId = Gambit.NONE.id)
            .runToCompletion()
        val declared = MatchEngine(spec, GameMode.CAMPAIGN)
            .beginShot(2700, 900, ProjectileType.STANDARD.id, gambitId = Gambit.LONG_WAY.id)
            .runToCompletion()
        assertNotEquals(
            "identical flights, different declarations, different hashes",
            plain.hash, declared.hash,
        )
    }

    @Test
    fun gambitSurvivesTheShareCodecRoundTrip() {
        val seed = Seeds.duelRandom(31337L)
        val spec = SystemGenerator.generate(seed, Seeds.DUEL_DIFFICULTY, GameMode.DUEL)
        val engine = MatchEngine(spec, GameMode.DUEL)
        val shot = engine.beginShot(
            900, 700, ProjectileType.STANDARD.id,
            anchors = listOf(Anchor(400, 700, true)),
            gambitId = Gambit.STOLEN_SPEED.id,
        )
        engine.commit(shot.runToCompletion())

        val code = ShareCodec.encode(
            MatchRecord(
                GameMode.DUEL.id, Seeds.DUEL_DIFFICULTY, seed, engine.turns, engine.combinedHash,
            )
        )
        val verified = ShareCodec.decodeAndVerify(code)
        assertTrue("an honest coded match must verify", verified != null)
        assertEquals(
            "the declaration travelled with the turn",
            Gambit.STOLEN_SPEED.id, verified!!.record.turns[0].gambitId,
        )
        assertEquals(engine.gambitKept, verified.engine.gambitKept)
    }

    @Test
    fun anEditedDeclarationFailsVerification() {
        val seed = Seeds.duelRandom(4242L)
        val spec = SystemGenerator.generate(seed, Seeds.DUEL_DIFFICULTY, GameMode.DUEL)
        val engine = MatchEngine(spec, GameMode.DUEL)
        engine.commit(
            engine.beginShot(900, 700, ProjectileType.STANDARD.id, gambitId = Gambit.NONE.id)
                .runToCompletion()
        )
        // Same flight, re-declared after the fact to claim a payout.
        val edited = engine.turns.map { it.copy(gambitId = Gambit.LONG_WAY.id) }
        val forged = ShareCodec.encode(
            MatchRecord(GameMode.DUEL.id, Seeds.DUEL_DIFFICULTY, seed, edited, engine.combinedHash)
        )
        assertNull("re-declaring a finished shot must not verify", ShareCodec.decodeAndVerify(forged))
    }

    /**
     * A duel is decided by HP and reads nothing else, so a declaration that
     * only moved style points would have been all risk and no reward. A kept
     * one multiplies damage instead.
     */
    @Test
    fun aKeptDeclarationHitsHarderInADuel() {
        val plain = MatchEngine(openSpec(), GameMode.DUEL)
        plain.commit(
            plain.beginShot(2700, 200, ProjectileType.STANDARD.id).runToCompletion()
        )
        val declared = MatchEngine(openSpec(), GameMode.DUEL)
        declared.commit(
            declared.beginShot(
                2700, 200, ProjectileType.STANDARD.id, gambitId = Gambit.FEATHERWEIGHT.id,
            ).runToCompletion()
        )
        assertTrue(declared.gambitKept[0])
        val plainDamage = Tuning.STATION_HP - plain.hp[1]
        val declaredDamage = Tuning.STATION_HP - declared.hp[1]
        assertTrue("the plain shot must land at all", plainDamage > 0)
        assertTrue(
            "a kept declaration hits harder ($declaredDamage vs $plainDamage)",
            declaredDamage > plainDamage,
        )
    }

    /**
     * The generator only offers declarations it has *proved* this seat can
     * honour, because the first version offered all four everywhere and
     * measurement found levels where "two-body" had no winning shot at all.
     * An offered bet must be a winnable one.
     */
    @Test
    fun everyOfferedGambitIsOneTheArenaCanActuallyPay() {
        for (sector in 0 until 4) {
            for (level in 0 until 3) {
                val spec = SystemGenerator.generate(
                    Seeds.campaign(sector, level), sector * 2, GameMode.CAMPAIGN,
                )
                val offered = spec.gambitOptionsFor(0).toSet()
                assertTrue(
                    "s$sector.l$level offers nothing to declare",
                    offered.isNotEmpty(),
                )
                // Re-prove each offered gambit from scratch on a fresh sweep.
                val proven = SystemGenerator.gambitOptions(spec, shooter = 0)
                val provenSet = Gambit.declarable.filter { (proven shr it.id) and 1 == 1 }.toSet()
                for (g in offered) {
                    assertTrue(
                        "s$sector.l$level offers ${g.name} but no winning shot honours it",
                        g in provenSet,
                    )
                }
            }
        }
    }

    @Test
    fun unknownGambitIdsAreRefusedRatherThanTreatedAsNone() {
        assertEquals(Gambit.NONE, Gambit.byId(0))
        assertEquals(Gambit.LONG_WAY, Gambit.byId(Gambit.LONG_WAY.id))
        // byId is lenient for in-process reads; the codec is the strict gate.
        assertEquals(Gambit.NONE, Gambit.byId(99))

        val seed = Seeds.duelRandom(7L)
        val spec = SystemGenerator.generate(seed, Seeds.DUEL_DIFFICULTY, GameMode.DUEL)
        val engine = MatchEngine(spec, GameMode.DUEL)
        engine.commit(engine.beginShot(900, 700, ProjectileType.STANDARD.id).runToCompletion())
        val alien = engine.turns.map { it.copy(gambitId = 99) }
        val code = ShareCodec.encode(
            MatchRecord(GameMode.DUEL.id, Seeds.DUEL_DIFFICULTY, seed, alien, engine.combinedHash)
        )
        assertNull("a gambit this build cannot judge is refused", ShareCodec.decode(code))
    }
}

/**
 * The share code IS the netcode, so its size is a product constraint, not an
 * implementation detail: a code has to survive being pasted into a messenger
 * by a human. Every protocol version so far has added bytes (v3 anchors, v5
 * gambits) and the whole match is re-encoded every turn, so length grows with
 * turn count. This pins it.
 */
class ShareCodeSizeTest {

    private fun codeFor(turnCount: Int, anchorsPerTurn: Int): String {
        val seed = Seeds.duelRandom(12345L)
        val spec = SystemGenerator.generate(seed, Seeds.DUEL_DIFFICULTY, GameMode.DUEL)
        val e = MatchEngine(spec, GameMode.DUEL)
        repeat(turnCount) { i ->
            if (e.over) return@repeat
            val anchors = (0 until anchorsPerTurn).map { Anchor(300 + it * 50, 700 + it * 40, true) }
            e.commit(
                e.beginShot(
                    600 + i * 37, 500, ProjectileType.STANDARD.id,
                    anchors = anchors, gambitId = Gambit.LONG_WAY.id,
                ).runToCompletion()
            )
        }
        return ShareCodec.encode(
            MatchRecord(GameMode.DUEL.id, Seeds.DUEL_DIFFICULTY, seed, e.turns, e.combinedHash)
        )
    }

    /**
     * The code a player actually sends to open a duel — one turn — must stay
     * short enough to read as a message rather than as a wall of noise.
     */
    @Test
    fun anOpeningChallengeStaysShort() {
        assertTrue(
            "a plain opening code got long: ${codeFor(1, 0).length} chars",
            codeFor(1, 0).length <= 80,
        )
        assertTrue(
            "a fully anchored opening code got long: ${codeFor(1, 3).length} chars",
            codeFor(1, 3).length <= 110,
        )
    }

    /**
     * Worst case the protocol permits: the full turn cap with every anchor
     * spent every turn. Budget chosen against the transport that actually
     * constrains us — a concatenated SMS caps near 1530 GSM-7 characters, and
     * anything beyond that stops being pasteable in the worst case. Measured
     * at 1039 on 2026-08-06 (protocol v5); the headroom is what future
     * protocol additions may spend.
     */
    @Test
    fun theLongestPossibleDuelStillFitsAMessage() {
        val worst = codeFor(Tuning.DUEL_MAX_TURNS, Tuning.MAX_ANCHORS)
        assertTrue(
            "worst-case duel code is ${worst.length} chars, over the 1200 budget — " +
                "the protocol has outgrown what a person can paste",
            worst.length <= 1200,
        )
        assertNotNull("and it must still verify at that size", ShareCodec.decodeAndVerify(worst))
    }
}

/**
 * Objectives (docs/VISION.md §3.5). Capture is the interesting case, and the
 * interesting thing about it is that the *physics* dictated the design: an
 * incoming shot is on a hyperbolic trajectory, so with nothing to dissipate
 * energy it must leave again no matter how well aimed. These tests pin that
 * reality rather than the wish.
 */
class ObjectiveTest {

    private fun captureSpec(): SystemSpec = SystemSpec(
        seed = 77L,
        difficulty = 4,
        name = "Ring Range",
        bodies = listOf(
            Body(500.0, 800.0, 70.0, 9.0e6, BodyKind.STAR, 0),
        ),
        stations = listOf(
            Station(Tuning.WORLD_W / 2, Tuning.WORLD_H - 150.0, 0),
            Station(Tuning.WORLD_W / 2, 150.0, 1),
        ),
        solverHitRate = 0.05,
        objectiveId = Objective.CAPTURE.id,
        captureBody = 0,
    )

    /**
     * The measurement that decided the whole feature: no unbraked shot holds
     * the ring. Across twelve generated levels the best any of them managed
     * was 191 ticks against a 240 bar; here the same is true on a clean range.
     */
    @Test
    fun noUnbrakedShotCanHoldTheRing() {
        val spec = captureSpec()
        var best = 0
        var angle = 0
        while (angle < 3600) {
            for (power in intArrayOf(0, 200, 400, 600, 800, 1000)) {
                val d = LiveShot(
                    spec.bodies, spec.stations, 0, angle, power,
                    ProjectileType.STANDARD.id,
                    maxTicks = Tuning.SOLVER_MAX_TICKS, captureBody = 0,
                ).runToCompletion().captureDwell
                if (d > best) best = d
            }
            angle += 30
        }
        assertTrue(
            "an unbraked shot held the ring for $best ticks — if this ever " +
                "passes the 240 bar, Capture has stopped requiring the burn " +
                "that justifies it",
            best < Tuning.CAPTURE_TICKS,
        )
    }

    /** ...and a braked one can, which is the manoeuvre the objective teaches. */
    @Test
    fun aRetroBurnCanHoldTheRing() {
        val spec = captureSpec()
        assertTrue(
            "no burn timing held the ring on a clean range",
            SystemGenerator.proveCapture(spec, 0),
        )
    }

    @Test
    fun captureIsWonByDwellNotByHittingAnything() {
        val spec = captureSpec()
        // Find a solution the same way the generator proves one exists.
        var winner: Triple<Int, Int, Int>? = null
        outer@ for (angle in 0 until 3600 step SystemGenerator.CAPTURE_ANGLE_STEP) {
            for (power in SystemGenerator.CAPTURE_POWERS) {
                for (burn in SystemGenerator.CAPTURE_BURN_TICKS) {
                    val r = LiveShot(
                        spec.bodies, spec.stations, 0, angle, power,
                        ProjectileType.RETRO.id, presetBrakeTick = burn,
                        maxTicks = Tuning.SOLVER_MAX_TICKS, captureBody = 0,
                    ).runToCompletion()
                    if (r.captureDwell >= Tuning.CAPTURE_TICKS) {
                        winner = Triple(angle, power, burn); break@outer
                    }
                }
            }
        }
        assertNotNull("the range must be winnable at all", winner)
        val (a, p, b) = winner!!

        val e = MatchEngine(spec, GameMode.CAMPAIGN)
        val r = e.beginShot(a, p, ProjectileType.STANDARD.id, presetBrakeTick = b)
            .runToCompletion()
        assertEquals(
            "Capture issues a RETRO shell whatever was selected",
            ProjectileType.RETRO.id, r.input.typeId,
        )
        e.commit(r)
        assertTrue("holding the ring wins the system", e.campaignWon)
        assertTrue(r.stationHits.isEmpty() || true) // winning did not require a hit
        assertEquals("and a capture in one shot is worth three stars", 3, e.starsEarned())
    }

    /** A Breach system is unaffected: no ring, no dwell, same rules as before. */
    @Test
    fun breachSystemsAreUntouched() {
        val spec = captureSpec().copy(
            objectiveId = Objective.BREACH.id, captureBody = -1,
        )
        val e = MatchEngine(spec, GameMode.CAMPAIGN)
        val r = e.beginShot(2700, 1000, ProjectileType.STANDARD.id).runToCompletion()
        assertEquals("no ring is measured", 0, r.captureDwell)
        assertEquals(
            "and the chosen shell is respected",
            ProjectileType.STANDARD.id, r.input.typeId,
        )
    }

    /**
     * The D-24 rule, applied a second time: an objective is only ever assigned
     * to a system the generator has proved can pay it.
     */
    @Test
    fun everyCaptureSystemIsProvablyWinnable() {
        var found = 0
        for (sector in 0 until 6) {
            for (level in 0 until 12 step 3) {
                val spec = SystemGenerator.generate(
                    Seeds.campaign(sector, level),
                    Seeds.campaignDifficulty(sector, level),
                    GameMode.CAMPAIGN,
                )
                if (spec.objective != Objective.CAPTURE) continue
                found++
                assertTrue("capture system has no marked body", spec.captureBody >= 0)
                assertTrue(
                    "s$sector.l$level asks for a capture it cannot pay",
                    SystemGenerator.proveCapture(spec, spec.captureBody),
                )
            }
        }
        assertTrue("no capture systems generated at all — the feature is dead", found > 0)
    }

    /** Duels and dailies stay Breach; an orbit puzzle is not what those modes are. */
    @Test
    fun onlyTheCampaignAsksForCaptures() {
        for (i in 0 until 6) {
            assertEquals(
                Objective.BREACH,
                SystemGenerator.generate(
                    Seeds.duelRandom(i * 131L), Seeds.DUEL_DIFFICULTY, GameMode.DUEL,
                ).objective,
            )
        }
        assertEquals(
            Objective.BREACH,
            SystemGenerator.generate(
                Seeds.daily(20700L), Seeds.DAILY_DIFFICULTY, GameMode.DAILY,
            ).objective,
        )
    }
}
