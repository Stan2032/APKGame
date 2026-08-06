package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
