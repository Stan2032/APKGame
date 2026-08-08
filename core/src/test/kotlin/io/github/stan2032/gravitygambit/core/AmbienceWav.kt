package io.github.stan2032.gravitygambit.core

import java.io.File

/**
 * Writes sample pads to `.wav` so a human can actually hear them.
 *
 * Every generated thing in this project has been judged by measurement, which
 * works for trajectories and does not work for sound: a loop can pass every
 * assertion in [AmbienceTest] and still be unpleasant to sit inside for ten
 * minutes. Until 0.14.0 the seven SFX had never been heard by anyone, only
 * computed, and that gap existed purely because auditioning them required a
 * device. It does not require a device — it requires a file.
 *
 *     ./gradlew :core:ambienceWav
 *
 * Output lands in `core/build/ambience/`. Nothing here ships: this file is in
 * the test source set, so it is not in the APK.
 */
object AmbienceWav {

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.firstOrNull() ?: "build/ambience").apply { mkdirs() }

        // One per objective, so the harmonic colouring is comparable by ear,
        // plus a pulsar system where the tremolo is the point.
        val samples = listOf(
            Triple("breach", Objective.BREACH, Seeds.campaign(0, 0)),
            Triple("capture", Objective.CAPTURE, Seeds.campaign(4, 1)),
            Triple("courier", Objective.COURIER, Seeds.campaign(7, 2)),
            Triple("thread", Objective.THREAD, Seeds.campaign(9, 3)),
        )

        for ((label, objective, seed) in samples) {
            val spec = SystemGenerator.generate(seed, 6, GameMode.CAMPAIGN)
                .copy(objectiveId = objective.id)
            write(File(out, "ambience-$label.wav"), Ambience.loopFor(spec))
            describe(label, spec)
        }

        // A guaranteed pulsar, since generation only sometimes produces one.
        val pulsarSpec = SystemGenerator.generate(Seeds.campaign(5, 0), 6, GameMode.CAMPAIGN).let {
            it.copy(bodies = it.bodies.mapIndexed { i, b -> if (i == 0) b.copy(kind = BodyKind.PULSAR) else b })
        }
        write(File(out, "ambience-pulsar.wav"), Ambience.loopFor(pulsarSpec))
        describe("pulsar", pulsarSpec)

        println("wrote ${out.absolutePath}")
    }

    private fun describe(label: String, spec: SystemSpec) {
        val voices = Ambience.voicesFor(spec)
        // Voice 0 is the root; the rest follow the bodies in mass order, which
        // is what lets a listener match a tremolo to the thing making it.
        val kinds = spec.bodies.sortedByDescending { StrictMath.abs(it.mass) }
            .take(Ambience.MAX_BODY_VOICES).map { it.kind }
        val parts = voices.mapIndexed { i, v ->
            val kind = if (i == 0) "root" else kinds.getOrNull(i - 1)?.name?.lowercase() ?: "?"
            "%.1fHz %s%s".format(v.hz, kind, if (v.lfoCycles > 0) " ~${v.lfoCycles}" else "")
        }
        println("$label: ${voices.size} voices [${parts.joinToString(", ")}]")
        if (Ambience.pulsarLocked(spec)) {
            println("  listen for: the pulsar's slow swell, ${Ambience.pulsarPeriodsPerLoop.toInt()} per loop " +
                "— that is its real gravity cycle, not a chosen tempo")
        }
    }

    /** Canonical 44-byte RIFF header plus little-endian 16-bit mono samples. */
    fun write(file: File, pcm: ShortArray) {
        val dataBytes = pcm.size * 2
        val buf = java.nio.ByteBuffer.allocate(44 + dataBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(36 + dataBytes)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)                                   // PCM header size
        buf.putShort(1)                                  // format: uncompressed PCM
        buf.putShort(1)                                  // channels: mono
        buf.putInt(Ambience.SAMPLE_RATE)
        buf.putInt(Ambience.SAMPLE_RATE * 2)             // byte rate
        buf.putShort(2)                                  // block align
        buf.putShort(16)                                 // bits per sample
        buf.put("data".toByteArray())
        buf.putInt(dataBytes)
        for (s in pcm) buf.putShort(s)
        file.writeBytes(buf.array())
    }
}
