package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pad makes three claims that are easy to state and easy to get wrong:
 * it loops without a seam, it is in tune, and a pulsar's voice runs at the
 * pulsar's real gravitational rate. All three are checked here against the
 * rendered samples rather than against the intent (D-3).
 */
class AmbienceTest {

    private fun spec(seed: Long, difficulty: Int = 5) =
        SystemGenerator.generate(seed, difficulty, GameMode.CAMPAIGN)

    @Test
    fun theLoopIsSeamlessBecauseEveryVoiceIsWholeCycles() {
        for (seed in 0L until 12L) {
            for (v in Ambience.voicesFor(spec(seed))) {
                // Integer by type; the real assertion is that they are in a
                // sane audible range and never zero (a zero-cycle carrier is
                // DC, which is silence you still pay to render).
                assertTrue("carrier ${v.cycles} cycles is out of range", v.cycles in 1..(Ambience.LOOP_SAMPLES / 4))
                assertTrue("lfo ${v.lfoCycles} cycles is negative", v.lfoCycles >= 0)
                assertTrue("phase ${v.phaseSample} out of buffer", v.phaseSample in 0 until Ambience.LOOP_SAMPLES)
                assertTrue("voice at ${v.hz}Hz is above the pad's stated ceiling", v.hz < 1400.0)
            }
        }
    }

    @Test
    fun theSeamIsContinuousInTheRenderedSamples() {
        // The claim that matters to an ear: joining the last sample to the
        // first must not jump more than the signal jumps anywhere else inside
        // the loop. If it did, the loop would tick once every seven seconds.
        val pcm = Ambience.loopFor(spec(7L))
        var worstInside = 0
        for (i in 1 until pcm.size) {
            val d = StrictMath.abs(pcm[i] - pcm[i - 1])
            if (d > worstInside) worstInside = d
        }
        val seam = StrictMath.abs(pcm[0] - pcm[pcm.size - 1])
        assertTrue(
            "seam jump $seam exceeds the worst step inside the loop ($worstInside) — the loop would click",
            seam <= worstInside,
        )
    }

    @Test
    fun aPulsarsVoicePulsesAtThePulsarsOwnPeriod() {
        // The divisibility this rests on. If PULSAR_PERIOD is ever retuned to
        // something that does not divide the loop, this fails rather than
        // letting the game keep claiming the pad tracks the pull.
        val periods = Ambience.pulsarPeriodsPerLoop
        assertEquals(
            "loop is not a whole number of pulsar periods ($periods) — pulsar phase lock is broken",
            StrictMath.rint(periods), periods, 0.0,
        )
        assertTrue("loop must contain at least one pulsar period", periods >= 1.0)

        val pulsar = Body(500.0, 800.0, 40.0, 6.0e6, BodyKind.PULSAR, 0)
        val withPulsar = spec(3L).copy(bodies = listOf(pulsar))
        val v = Ambience.voicesFor(withPulsar).last()
        assertEquals("pulsar tremolo must run at the gravity period", periods.toInt(), v.lfoCycles)
        assertTrue("pulsar tremolo must start at its peak so sample 0 is maximum pull", v.lfoStartsHigh)
        assertTrue(Ambience.pulsarLocked(withPulsar))
        assertTrue(!Ambience.pulsarLocked(spec(3L).copy(bodies = listOf(pulsar.copy(kind = BodyKind.STAR)))))
    }

    @Test
    fun intervalsAreExactJustIntonationWithNoRounding() {
        // Every ratio applied to every root must land on a whole cycle count,
        // which is what lets the pad be both in tune and seamless at once.
        for (root in Ambience.ROOT_CYCLES) {
            for (set in Ambience.RATIOS + arrayOf(Ambience.REPULSOR_RATIO)) {
                var i = 0
                while (i < set.size) {
                    val num = set[i]
                    val den = set[i + 1]
                    assertEquals(
                        "root $root cycles does not divide evenly by $num/$den — the interval would be detuned by rounding",
                        0, (root * num) % den,
                    )
                    i += 2
                }
            }
        }
    }

    @Test
    fun theMixIsNormalizedAndNeverClips() {
        for (seed in 0L until 8L) {
            val pcm = Ambience.loopFor(spec(seed))
            assertEquals(Ambience.LOOP_SAMPLES, pcm.size)
            var peak = 0
            var sum = 0L
            for (s in pcm) {
                val a = StrictMath.abs(s.toInt())
                if (a > peak) peak = a
                sum += s
            }
            val target = (Ambience.TARGET_PEAK * Short.MAX_VALUE).toInt()
            assertTrue("seed $seed peaked at $peak, target $target", peak in (target - 8)..(target + 8))
            // A sum of sines has no DC component; a large offset means a bug
            // in the amplitude envelope, and on a phone it is a thump.
            val meanBias = StrictMath.abs(sum.toDouble() / pcm.size)
            assertTrue("seed $seed has DC offset $meanBias", meanBias < 40.0)
        }
    }

    @Test
    fun differentSystemsSoundDifferent() {
        // The whole reason for generating this rather than shipping one loop.
        val a = Ambience.voicesFor(spec(1L))
        val b = Ambience.voicesFor(spec(2L))
        assertNotEquals("two systems produced an identical pad", a, b)

        // Same system, same pad — the pad must not drift between visits.
        assertEquals(Ambience.voicesFor(spec(1L)), a)
    }

    @Test
    fun theObjectiveColoursTheHarmony() {
        // Breach and Capture must not be the same chord; the brief should be
        // audible before it is read.
        val base = spec(11L)
        val breach = Ambience.voicesFor(base.copy(objectiveId = Objective.BREACH.id)).map { it.cycles }
        val capture = Ambience.voicesFor(base.copy(objectiveId = Objective.CAPTURE.id)).map { it.cycles }
        val thread = Ambience.voicesFor(base.copy(objectiveId = Objective.THREAD.id)).map { it.cycles }
        assertNotEquals(breach, capture)
        assertNotEquals(breach, thread)
        assertNotEquals(capture, thread)
    }

    @Test
    fun renderIsCheapEnoughToBuildOnLevelStart() {
        val voices = Ambience.voicesFor(spec(4L))
        repeat(2) { Ambience.render(voices) }   // warm the JIT, as on device
        val t0 = System.nanoTime()
        Ambience.render(voices)
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("ambience render: ${ms}ms for ${voices.size} voices, ${Ambience.LOOP_SAMPLES} samples")
        // Runs off the main thread while the level is already on screen, so
        // the budget is generous; this catches an order-of-magnitude
        // regression, e.g. someone replacing the table with sin() per sample.
        assertTrue("ambience render took ${ms}ms (desktop budget 400ms)", ms < 400)
    }
}
