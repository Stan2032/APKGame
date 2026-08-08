package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether VISION §3.4's resonance *mechanic* is reachable at all — asked
 * before building it, because the section's premise turns out to be absent
 * from the game.
 *
 * §3.4 says: *"Pulsars already breathe on fixed periods. Make that a timing
 * mechanic: fire in phase and the assist amplifies; fire out of phase and it
 * fights you."* The first sentence is true. The second describes a choice the
 * player **cannot make**, and this file demonstrates why.
 *
 * A pulsar's mass is `massAt(tick)`, and `tick` is [LiveShot]'s own counter,
 * which starts at 0 for every shot. So every shot ever fired meets the pulsar
 * at exactly the same phase — `cos(0)`, its heaviest. Phase varies *within* a
 * flight, which is what makes a pulsar interesting to fly past, but it does
 * not vary *between* shots, so "fire in phase" names nothing the player
 * decides. There is no timing to get right.
 *
 * Making it real means phase must depend on when the shot was fired relative
 * to the match, not relative to itself — which changes what every existing
 * pulsar level does and is therefore a protocol break, not a tuning change.
 */
class ResonanceReachTest {

    private fun pulsar() = Body(
        x = 500.0, y = 800.0, radius = 60.0, mass = 5.0e6,
        kind = BodyKind.PULSAR, palette = 0,
    )

    @Test
    fun everyShotMeetsAPulsarAtTheSamePhase() {
        val p = pulsar()
        // Launch phase is identical by construction: tick 0 for every shot.
        assertEquals(
            "a pulsar is at its heaviest at tick 0, for every shot ever fired",
            p.mass * (Tuning.PULSAR_MIN + Tuning.PULSAR_SWING),
            p.massAt(0),
            1e-9,
        )
        // And the period really does swing, so this is not a dead body — the
        // variation exists, it just is not addressable by the player.
        val half = (Tuning.PULSAR_PERIOD / 2).toInt()
        assertTrue(
            "the pulsar must actually breathe within a flight, or §3.4 has no subject",
            p.massAt(half) < p.massAt(0) * 0.6,
        )
    }

    @Test
    fun twoShotsFiredAtDifferentMomentsOfAMatchAreIdentical() {
        // THE finding, stated as a match-level fact rather than a constant.
        // Fire the same aim as the first turn and as a later turn: if timing
        // were a mechanic, these would differ. They are bit-identical.
        val spec = SystemGenerator.generate(Seeds.campaign(6, 1), 6, GameMode.CAMPAIGN)
            .let { s -> s.copy(bodies = s.bodies + pulsar()) }

        val first = LiveShot(
            spec.bodies, spec.stations, 0, 900, 700,
            ProjectileType.STANDARD.id, maxTicks = Tuning.MAX_TICKS,
        ).runToCompletion()

        // Play a turn first, then fire the identical shot as turn two.
        val engine = MatchEngine(spec, GameMode.CAMPAIGN)
        engine.commit(engine.beginShot(1400, 500, ProjectileType.STANDARD.id).runToCompletion())
        val later = engine.beginShot(900, 700, ProjectileType.STANDARD.id).runToCompletion()

        assertEquals(
            "an identical aim fired later in the match produced a different flight — if this " +
                "ever fails, pulsar phase has become match-relative and §3.4's mechanic is live",
            first.hash, later.hash,
        )
        assertEquals("and the same duration", first.ticks, later.ticks)
    }

    /**
     * How much of the game a resonance mechanic would even touch, and the
     * separate finding that **the polyrhythm half has no subject**.
     *
     * TODO carried "multi-pulsar polyrhythm: the generator never makes two
     * with different periods", which reads as *two pulsars exist and share a
     * period*. They do not co-exist at all. That is the same shape as the
     * binary star this game does not have (D-41): a documented feature whose
     * premise the generator never produces. Periods are the second problem;
     * the first is that there is only ever one pulsar to give a period to.
     */
    @Test
    fun pulsarsAreCommonEnoughToMatterAndNeverAppearInPairs() {
        var withPulsar = 0
        var withTwo = 0
        var total = 0
        var lowestWithPulsar = Int.MAX_VALUE
        for (d in 0..11) {
            for (l in 0 until 30) {
                val spec = SystemGenerator.generate(Seeds.campaign(d, l), d, GameMode.CAMPAIGN)
                total++
                val n = spec.bodies.count { it.kind == BodyKind.PULSAR }
                if (n > 0) {
                    withPulsar++
                    if (d < lowestWithPulsar) lowestWithPulsar = d
                }
                if (n >= 2) withTwo++
            }
        }
        println("pulsar systems: $withPulsar/$total (${100 * withPulsar / total}%), lowest difficulty $lowestWithPulsar")
        assertTrue(
            "pulsars must be common enough for a resonance mechanic to be worth building",
            withPulsar * 4 >= total,
        )
        assertEquals(
            "a system with two pulsars now exists; the polyrhythm item's premise has changed " +
                "and VISION §3.4 must be re-read before building on it",
            0, withTwo,
        )
    }
}
