package io.github.stan2032.gravitygambit.core

/**
 * The sound of a star system.
 *
 * Every level in this game is generated from a seed, so there is no finite set
 * of levels to record music for. Shipping one background loop for all of them
 * would say nothing about where you are. Instead the pad is *derived from the
 * arrangement of mass on the board*: each body that matters gets a voice, its
 * pitch set by how heavy it is, its position setting where that voice sits in
 * the loop's phase. A system with one huge star and two pebbles sounds
 * different from three even worlds, because it is different.
 *
 * This is sonification, not decoration, and one voice is load-bearing: a
 * [BodyKind.PULSAR]'s voice pulses at the pulsar's own gravitational period
 * ([Tuning.PULSAR_PERIOD]), so the ear can hear when the pull is strong
 * (docs/VISION.md Stage E). See [pulsarLocked] for exactly how far that claim
 * goes — it is a real lock, and it has a real precondition.
 *
 * ### Why the loop is seamless by construction
 *
 * A rendered loop clicks at its seam unless every component completes a whole
 * number of cycles inside it. So the unit here is not frequency in hertz but
 * [Voice.cycles] — *integer* cycles per loop. Frequency is the derived
 * quantity ([Voice.hz]), not the stored one. That makes seamlessness a
 * property of the type rather than a tuning that someone can nudge out of
 * true, and it holds for the tremolos too ([Voice.lfoCycles]).
 *
 * ### Why the intervals are exact
 *
 * [ROOT_CYCLES] are all multiples of 80, and every interval in [RATIOS] has a
 * denominator dividing 80, so `root * num / den` is always a whole number of
 * cycles. The pad is therefore in exact just intonation *and* seamlessly
 * looped at the same time, with no rounding anywhere — the two constraints
 * would normally fight, and picking the root from a divisible grid is what
 * dissolves the fight. Detuning, where it happens, is then deliberate
 * ([DETUNE_CYCLES]) rather than a rounding artefact.
 *
 * Nothing here touches gameplay: ambience is never hashed, never simulated
 * against, and never crosses the wire (docs/DETERMINISM.md). It lives in
 * `:core` because it is pure arithmetic over a [SystemSpec] and is therefore
 * testable without a device — the same reason every other rule does (D-7).
 */
object Ambience {

    /**
     * Nothing in the pad reaches 1.4 kHz, so 22.05 kHz leaves the top octave
     * of content nearly three octaves below Nyquist. Halving the rate halves
     * the buffer; a device that runs its mixer at 48 kHz resamples a signal
     * with no high-frequency content, which is inaudible.
     */
    const val SAMPLE_RATE = 22_050

    /**
     * Seven seconds, and the seven is not arbitrary: a pulsar's gravity cycle
     * is `PULSAR_PERIOD * DT` = 3.5s, and 7 is the smallest whole number of
     * seconds containing a whole number of those (exactly two). Because the
     * loop is an integer number of pulsar periods, looping forever never
     * drifts out of lock — the phase relationship survives every seam.
     * [pulsarPeriodsPerLoop] proves the divisibility rather than assuming it.
     */
    const val LOOP_SECONDS = 7

    const val LOOP_SAMPLES = SAMPLE_RATE * LOOP_SECONDS

    /** Loop length in seconds, as a double, for frequency conversions. */
    const val LOOP_SECONDS_D = LOOP_SECONDS.toDouble()

    /**
     * Peak sample of the finished loop, as a fraction of full scale (about
     * -10.5 dBFS). A pad is a floor, not an event: it has to leave headroom
     * for the SFX that land on top of it without the player reaching for the
     * volume key between the menu and a hit.
     */
    const val TARGET_PEAK = 0.30

    /** Voices past this are inaudible under the ones above them; masses are ranked, so the loudest survive. */
    const val MAX_BODY_VOICES = 6

    /**
     * Candidate roots, in cycles per loop: 45.7, 57.1, 68.6 and 80.0 Hz.
     * All multiples of 80 so that every interval in [RATIOS] lands on a whole
     * cycle count. Low, because this is the floor of the mix.
     */
    val ROOT_CYCLES = intArrayOf(320, 400, 480, 560)

    /**
     * One interval set per [Objective], indexed by `objectiveId`, each as
     * flattened numerator/denominator pairs. The objective is the thing the
     * system is asking of you, so it is the right thing to colour the harmony:
     * a Breach and a Capture should not sound alike, and the difference should
     * be audible before you read the brief.
     *
     * - Breach: bare fifths and octaves — open, hard, nothing to resolve.
     * - Capture: a minor third and a minor seventh — held, unresolved, which
     *   is what four seconds of orbit feels like.
     * - Courier: a suspended second over the fifth — refuses to settle, like
     *   the moving target it names.
     * - Thread: a major triad extended — ordered, the only objective with a
     *   correct sequence, and the only one that sounds resolved.
     */
    val RATIOS: Array<IntArray> = arrayOf(
        intArrayOf(1, 1, 3, 2, 2, 1, 3, 1, 9, 4),      // BREACH
        intArrayOf(1, 1, 6, 5, 3, 2, 9, 5, 12, 5),     // CAPTURE
        intArrayOf(1, 1, 9, 8, 3, 2, 27, 16, 4, 1),    // COURIER
        intArrayOf(1, 1, 5, 4, 3, 2, 15, 8, 5, 2),     // THREAD
    )

    /**
     * A repulsor gets 7/5 whatever the system's set — a tritone, the one
     * interval in the file that is meant to sound wrong, because a repulsor is
     * the one body that pushes your shot away and cannot be hit.
     */
    val REPULSOR_RATIO = intArrayOf(7, 5)

    /**
     * Cycles added or subtracted to detune a voice against its neighbours.
     * One cycle per loop is 1/7 Hz, so two voices a cycle apart beat once
     * every seven seconds — exactly one loop, so the beat itself is seamless.
     * This is the movement in the pad; without it a sum of pure tones is dead
     * air.
     */
    const val DETUNE_CYCLES = 1

    /** Pulsar tremolo depth: deep, because it is information, not texture. */
    const val PULSAR_DEPTH = 0.75

    /**
     * How many pulsar gravity cycles fit in one loop. Whole by construction —
     * [AmbienceTest] fails loudly if a future retune of [Tuning.PULSAR_PERIOD]
     * breaks the divisibility and silently turns the sonification into a lie.
     */
    val pulsarPeriodsPerLoop: Double
        get() = LOOP_SECONDS_D / (Tuning.PULSAR_PERIOD * Tuning.DT)

    /**
     * One partial of the pad.
     *
     * Everything periodic is stored as an integer count of cycles per loop
     * rather than a frequency, which is what makes the seam exact rather than
     * approximately exact.
     */
    data class Voice(
        /** Whole cycles of the carrier per loop; frequency is [hz]. */
        val cycles: Int,
        /** Peak contribution before the mix is normalized, 0..1. */
        val level: Double,
        /** Whole tremolo cycles per loop; 0 for a steady voice. */
        val lfoCycles: Int,
        /** Fraction of [level] the tremolo removes at its trough, 0..1. */
        val lfoDepth: Double,
        /** Sample offset of the carrier's start, spreading voices so they do not all crest together. */
        val phaseSample: Int,
        /** True for pulsars: the tremolo starts at its peak, so sample 0 is maximum pull. */
        val lfoStartsHigh: Boolean = false,
    ) {
        val hz: Double get() = cycles / LOOP_SECONDS_D
    }

    /**
     * The voices for [spec]. Pure, deterministic, and cheap — the expensive
     * half is [render].
     */
    fun voicesFor(spec: SystemSpec): List<Voice> {
        val rng = Rng(Rng.mix(spec.seed, AMBIENCE_SALT))
        val root = ROOT_CYCLES[rng.nextInt(ROOT_CYCLES.size)]
        val set = RATIOS[spec.objectiveId.coerceIn(0, RATIOS.size - 1)]
        val voices = ArrayList<Voice>(MAX_BODY_VOICES + 1)

        // The floor: the root itself, always present, so every system has a
        // body under it even when the board is nearly empty.
        voices.add(Voice(cycles = root, level = 0.50, lfoCycles = 1, lfoDepth = 0.15, phaseSample = 0))

        // Heaviest first: what dominates the field should dominate the chord.
        val ranked = spec.bodies.sortedByDescending { StrictMath.abs(it.mass) }.take(MAX_BODY_VOICES)
        val heaviest = ranked.firstOrNull()?.let { StrictMath.abs(it.mass) } ?: 0.0
        if (heaviest <= 0.0) return voices

        for ((i, body) in ranked.withIndex()) {
            val m = StrictMath.abs(body.mass)
            val share = m / heaviest

            val ratio = if (body.kind == BodyKind.REPULSOR) REPULSOR_RATIO
            else intArrayOf(set[(i % 5) * 2], set[(i % 5) * 2 + 1])
            var cycles = root * ratio[0] / ratio[1]

            // Heavy sits low, light sits high — the one mapping in here that
            // needs no explaining to a listener.
            cycles = when {
                body.kind == BodyKind.COMET -> cycles * 4
                share >= 0.60 -> cycles / 2
                share <= 0.20 -> cycles * 2
                else -> cycles
            }
            if (i > 0) cycles += if (rng.chance(0.5)) DETUNE_CYCLES else -DETUNE_CYCLES

            val (lfoCycles, lfoDepth) = when (body.kind) {
                // The load-bearing one: two tremolo cycles per loop is exactly
                // the pulsar's 3.5s gravity period.
                BodyKind.PULSAR -> pulsarPeriodsPerLoop.toInt() to PULSAR_DEPTH
                BodyKind.COMET -> 11 to 0.55       // a shimmer that says something is moving
                BodyKind.REPULSOR -> 5 to 0.40     // uneasy, on top of an uneasy interval
                else -> (1 + rng.nextInt(3)) to 0.18 + rng.nextDouble() * 0.12
            }

            // Phase from where the body actually is, so two systems with the
            // same masses in different places still differ.
            val turns = body.x / Tuning.WORLD_W * 0.5 + body.y / Tuning.WORLD_H * 0.5
            val phase = ((turns % 1.0) * LOOP_SAMPLES).toInt().let { if (it < 0) it + LOOP_SAMPLES else it }

            voices.add(
                Voice(
                    cycles = cycles.coerceIn(1, LOOP_SAMPLES / 4),
                    level = 0.20 + 0.55 * StrictMath.sqrt(share),
                    lfoCycles = lfoCycles,
                    lfoDepth = lfoDepth,
                    phaseSample = phase % LOOP_SAMPLES,
                    lfoStartsHigh = body.kind == BodyKind.PULSAR,
                )
            )
        }
        return voices
    }

    /**
     * Whether this system's pad carries real pulsar information.
     *
     * The honest scope of the claim: the tremolo runs at the pulsar's rate
     * always, but it only tells you the *phase* of the pull if playback is
     * restarted at the shot's first tick, because a free-running loop has no
     * idea when the player fired. The app resyncs on fire when this is true
     * and leaves the track alone when it is false, so systems with no pulsar
     * never pay for a seek they would learn nothing from.
     */
    fun pulsarLocked(spec: SystemSpec): Boolean = spec.bodies.any { it.kind == BodyKind.PULSAR }

    /**
     * Renders [voices] to one seamless loop of 16-bit mono PCM.
     *
     * Costs one sine table and one mix buffer, both transient. The table makes
     * every sample an exact lookup instead of a transcendental call: because
     * each voice advances by a whole number of table steps per sample, the
     * index arithmetic is integer modular and lands on real table entries
     * forever — no interpolation, and no drift across the millions of samples
     * a long session plays.
     */
    fun render(voices: List<Voice>): ShortArray {
        val n = LOOP_SAMPLES
        val table = FloatArray(n) { StrictMath.sin(2.0 * Math.PI * it / n).toFloat() }
        val mix = FloatArray(n)

        for (v in voices) {
            val carrierStep = v.cycles
            val lfoStep = v.lfoCycles
            var k = v.phaseSample
            // sin peaks a quarter turn in, which is where a pulsar's voice starts.
            var l = if (v.lfoStartsHigh) n / 4 else 0
            val steady = (v.level * (1.0 - v.lfoDepth)).toFloat()
            val swing = (v.level * v.lfoDepth).toFloat()
            val flat = v.level.toFloat()

            for (i in 0 until n) {
                val amp = if (lfoStep == 0) flat else steady + swing * (0.5f + 0.5f * table[l])
                mix[i] += amp * table[k]
                k += carrierStep
                if (k >= n) k -= n
                if (lfoStep != 0) {
                    l += lfoStep
                    if (l >= n) l -= n
                }
            }
        }

        var peak = 0f
        for (s in mix) {
            val a = if (s < 0f) -s else s
            if (a > peak) peak = a
        }
        val gain = if (peak > 0f) (TARGET_PEAK / peak).toFloat() else 0f
        return ShortArray(n) { i ->
            val v = Math.round(mix[i] * gain * Short.MAX_VALUE).toInt()
            v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /** Convenience: the finished loop for a system. */
    fun loopFor(spec: SystemSpec): ShortArray = render(voicesFor(spec))

    /** Keeps the pad's seed independent of every other seed derived from [SystemSpec.seed]. */
    private const val AMBIENCE_SALT = 0x4D551CL
}
