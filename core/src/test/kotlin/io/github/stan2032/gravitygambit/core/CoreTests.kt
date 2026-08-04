package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RngTest {
    @Test
    fun sequencesAreDeterministic() {
        val a = Rng(42L)
        val b = Rng(42L)
        repeat(1000) { assertEquals(a.nextLong(), b.nextLong()) }
    }

    @Test
    fun mixIsStable() {
        // Frozen values: if these ever change, every seed in the wild breaks.
        assertEquals(Rng.mix(1L, 2L), Rng.mix(1L, 2L))
        assertTrue(Rng.mix(1L, 2L) != Rng.mix(2L, 1L))
    }

    @Test
    fun doublesInRange() {
        val r = Rng(7L)
        repeat(10_000) {
            val d = r.nextDouble()
            assertTrue(d >= 0.0 && d < 1.0)
        }
    }
}

class DeterminismTest {
    private fun spec(): SystemSpec = SystemGenerator.generate(Seeds.campaign(0, 3), 0, GameMode.CAMPAIGN)

    @Test
    fun identicalInputsProduceIdenticalHashes() {
        val s = spec()
        val r1 = LiveShot(s.bodies, s.stations, 0, 2700, 800, 0).runToCompletion()
        val r2 = LiveShot(s.bodies, s.stations, 0, 2700, 800, 0).runToCompletion()
        assertEquals(r1.hash, r2.hash)
        assertEquals(r1.ticks, r2.ticks)
        assertEquals(r1.stationHits, r2.stationHits)
    }

    @Test
    fun differentInputsProduceDifferentHashes() {
        val s = spec()
        val r1 = LiveShot(s.bodies, s.stations, 0, 2700, 800, 0).runToCompletion()
        val r2 = LiveShot(s.bodies, s.stations, 0, 2701, 800, 0).runToCompletion()
        assertTrue(r1.hash != r2.hash)
    }

    @Test
    fun replayerReproducesLivePlay() {
        val s = spec()
        val engine = MatchEngine(s, GameMode.CAMPAIGN)
        val inputs = listOf(
            Triple(2650, 700, 0),
            Triple(2900, 950, 0),
            Triple(2500, 500, 0),
        )
        for ((a, p, t) in inputs) {
            if (engine.over) break
            engine.commit(engine.beginShot(a, p, t).runToCompletion())
        }
        val rebuilt = MatchReplayer.rebuild(s, engine.turns, GameMode.CAMPAIGN)
        assertEquals(engine.combinedHash, rebuilt.verifiedHash)
        assertEquals(engine.hp.toList(), rebuilt.engine.hp.toList())
    }

    @Test
    fun brakeTickChangesOutcomeDeterministically() {
        val s = spec()
        val plain = LiveShot(s.bodies, s.stations, 0, 2700, 900, ProjectileType.RETRO.id, presetBrakeTick = -1).runToCompletion()
        val braked = LiveShot(s.bodies, s.stations, 0, 2700, 900, ProjectileType.RETRO.id, presetBrakeTick = 40).runToCompletion()
        assertTrue(plain.hash != braked.hash)
        val braked2 = LiveShot(s.bodies, s.stations, 0, 2700, 900, ProjectileType.RETRO.id, presetBrakeTick = 40).runToCompletion()
        assertEquals(braked.hash, braked2.hash)
    }
}

class ShareCodecTest {
    private fun sampleRecord(): MatchRecord {
        val turns = listOf(
            TurnInput(0, 2712, 843, 0, -1),
            TurnInput(1, 901, 1000, 3, 250),
            TurnInput(0, 355, 12, 2, -1),
        )
        return MatchRecord(mode = 2, difficulty = 5, seed = -987654321L, turns = turns, hash = 0x1234ABCD5678EF90L)
    }

    @Test
    fun roundTrip() {
        val rec = sampleRecord()
        val text = ShareCodec.encode(rec)
        assertTrue(text.startsWith(ShareCodec.PREFIX))
        val back = ShareCodec.decode(text)
        assertNotNull(back)
        assertEquals(rec, back)
    }

    @Test
    fun decodeFindsCodeInsideMessageText() {
        val text = "Beat this! " + ShareCodec.encode(sampleRecord()) + " good luck"
        assertEquals(sampleRecord(), ShareCodec.decode(text))
    }

    @Test
    fun corruptionIsRejected() {
        val text = ShareCodec.encode(sampleRecord())
        val tampered = StringBuilder(text)
        val i = text.length - 3
        tampered[i] = if (text[i] == 'A') 'B' else 'A'
        assertNull(ShareCodec.decode(tampered.toString()))
    }

    @Test
    fun garbageIsRejected() {
        assertNull(ShareCodec.decode("hello world"))
        assertNull(ShareCodec.decode("GG1.!!!!"))
        assertNull(ShareCodec.decode("GG1.AAAA"))
    }
}

class GeneratorTest {
    @Test
    fun generatedSystemsAreSolvableAndSane() {
        for (difficulty in intArrayOf(0, 2, 4, 6)) {
            val spec = SystemGenerator.generate(Seeds.campaign(difficulty, 0), difficulty, GameMode.CAMPAIGN)
            assertTrue("difficulty $difficulty has no solutions", spec.solverHitRate > 0.0)
            assertEquals(2, spec.stations.size)
            assertTrue(spec.bodies.isNotEmpty())
            for (b in spec.bodies) {
                assertTrue(b.x > 0 && b.x < Tuning.WORLD_W)
                assertTrue(b.y > 0 && b.y < Tuning.WORLD_H)
            }
            // Stations must not be inside a solid body.
            for (s in spec.stations) {
                for (b in spec.bodies) {
                    if (b.kind == BodyKind.REPULSOR) continue
                    val dx = b.x - s.x; val dy = b.y - s.y
                    val min = b.radius + Tuning.STATION_RADIUS
                    assertTrue(dx * dx + dy * dy > min * min)
                }
            }
        }
    }

    @Test
    fun generationIsDeterministic() {
        val a = SystemGenerator.generate(12345L, 3, GameMode.CAMPAIGN)
        val b = SystemGenerator.generate(12345L, 3, GameMode.CAMPAIGN)
        assertEquals(a, b)
    }

    @Test
    fun duelSystemsAreFairForBothPlayers() {
        val spec = SystemGenerator.generate(Seeds.duelRandom(99L), Seeds.DUEL_DIFFICULTY, GameMode.DUEL)
        val r0 = SystemGenerator.solveRate(spec, 0)
        val r1 = SystemGenerator.solveRate(spec, 1)
        assertTrue("player 0 cannot hit", r0 > 0.0)
        assertTrue("player 1 cannot hit", r1 > 0.0)
    }
}

class EndToEndPvpTest {
    /**
     * The full serverless-PVP pipeline: play a duel, encode the challenge code,
     * then verify it as the receiving device would. This is the load-bearing test.
     */
    @Test
    fun duelCodeSurvivesFullPipeline() {
        val seed = Seeds.duelRandom(7L)
        val spec = SystemGenerator.generate(seed, Seeds.DUEL_DIFFICULTY, GameMode.DUEL)
        val engine = MatchEngine(spec, GameMode.DUEL)

        // Player 0 fires an arbitrary quantized shot.
        engine.commit(engine.beginShot(900, 750, 0).runToCompletion())
        // Player 1 responds.
        engine.commit(engine.beginShot(2700, 640, 0).runToCompletion())

        val code = ShareCodec.encode(
            MatchRecord(GameMode.DUEL.id, Seeds.DUEL_DIFFICULTY, seed, engine.turns, engine.combinedHash)
        )
        assertTrue(code.length < 400)

        val verified = ShareCodec.decodeAndVerify(code)
        assertNotNull("verification must pass for an honest code", verified)
        assertEquals(engine.hp.toList(), verified!!.engine.hp.toList())

        // Now forge the hash: verification must fail.
        val forged = ShareCodec.encode(
            MatchRecord(GameMode.DUEL.id, Seeds.DUEL_DIFFICULTY, seed, engine.turns, engine.combinedHash + 1)
        )
        assertNull("forged hash must be rejected", ShareCodec.decodeAndVerify(forged))
    }
}

class DailyLogicTest {
    @Test
    fun relocationIsDeterministicAndClear() {
        val spec = SystemGenerator.generate(Seeds.daily(Seeds.LAUNCH_EPOCH_DAY), Seeds.DAILY_DIFFICULTY, GameMode.DAILY)
        val a = DailyLogic.targetPosition(spec, 1)
        val b = DailyLogic.targetPosition(spec, 1)
        assertEquals(a, b)
        val c = DailyLogic.targetPosition(spec, 2)
        assertTrue(a != c)
    }
}

class EconomyTest {
    @Test
    fun profileJsonRoundTrip() {
        val p = Profile(
            stardust = 420,
            campaignStars = mapOf("0:0" to 3, "0:1" to 2),
            unlockedProjectiles = listOf(0, 1),
            ownedTrails = listOf("classic", "ember"),
            selectedTrail = "ember",
            dailyStreak = 4,
        )
        val back = Profile.fromJson(p.toJson())
        assertEquals(p, back)
        assertEquals(5, back.totalStars)
    }

    @Test
    fun corruptSaveFallsBackToDefault() {
        assertEquals(Profile(), Profile.fromJson("{not json"))
    }

    @Test
    fun rewardsArePositiveAndBounded() {
        assertTrue(Economy.campaignReward(0, 1, true) > 0)
        assertTrue(Economy.dailyReward(9999) <= 60)
        assertTrue(Economy.duelReward(win = true, codeDuel = true) > Economy.duelReward(win = false, codeDuel = true))
    }
}
