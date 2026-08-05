package io.github.stan2032.gravitygambit.core

import java.nio.ByteBuffer
import java.util.Base64
import java.util.zip.CRC32

/**
 * A complete, self-contained match record: everything needed to reconstruct and
 * VERIFY a match on any device. This is the entire multiplayer "protocol" —
 * ~30 bytes + 8 bytes per turn — small enough to travel through any messenger,
 * which is how PVP works with zero servers (see docs/MULTIPLAYER.md).
 */
data class MatchRecord(
    val mode: Int,
    val difficulty: Int,
    val seed: Long,
    val turns: List<TurnInput>,
    /** Combined verification hash claimed by the sender; must match a local re-simulation. */
    val hash: Long,
)

object ShareCodec {
    const val PREFIX = "GG1."

    /**
     * v2 added moving bodies, pulsars and slingshot scoring; v3 added player
     * anchors to TurnInput. Both change what a code means, so superseded
     * versions are refused rather than silently mis-verified
     * (docs/TUNING.md §Versioning).
     */
    private const val VERSION = 3

    fun encode(record: MatchRecord): String {
        val anchorBytes = record.turns.sumOf { it.anchors.size * 5 }
        val buf = ByteBuffer.allocate(16 + 1 + record.turns.size * 9 + anchorBytes + 8 + 4)
        buf.put('G'.code.toByte()).put('G'.code.toByte())
        buf.put(VERSION.toByte())
        buf.put(record.mode.toByte())
        buf.put(record.difficulty.toByte())
        buf.putLong(record.seed)
        buf.put(record.turns.size.toByte())
        for (t in record.turns) {
            buf.put(t.shooter.toByte())
            buf.putShort(t.angleDeci.toShort())
            buf.putShort(t.power.toShort())
            buf.put(t.typeId.toByte())
            buf.putShort(t.brakeTick.toShort())
            buf.put(t.anchors.size.toByte())
            for (a in t.anchors) {
                buf.putShort(a.x.toShort())
                buf.putShort(a.y.toShort())
                buf.put(if (a.attract) 1 else 0)
            }
        }
        buf.putLong(record.hash)
        val body = ByteArray(buf.position())
        buf.flip(); buf.get(body)
        val crc = CRC32().apply { update(body) }.value.toInt()
        val out = ByteBuffer.allocate(body.size + 4).put(body).putInt(crc)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.array())
    }

    /** Tolerant decode: finds the code anywhere in pasted text. Returns null if invalid. */
    fun decode(text: String): MatchRecord? {
        val idx = text.indexOf(PREFIX)
        if (idx < 0) return null
        val b64 = text.substring(idx + PREFIX.length).takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
        val bytes = try {
            Base64.getUrlDecoder().decode(b64)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (bytes.size < 16 + 1 + 8 + 4) return null
        val body = bytes.copyOfRange(0, bytes.size - 4)
        val crcStored = ByteBuffer.wrap(bytes, bytes.size - 4, 4).int
        val crcActual = CRC32().apply { update(body) }.value.toInt()
        if (crcStored != crcActual) return null

        val buf = ByteBuffer.wrap(body)
        if (buf.get() != 'G'.code.toByte() || buf.get() != 'G'.code.toByte()) return null
        if (buf.get().toInt() != VERSION) return null
        val mode = buf.get().toInt()
        val difficulty = buf.get().toInt()
        val seed = buf.long
        val turnCount = buf.get().toInt() and 0xFF
        if (turnCount > 64) return null
        val turns = ArrayList<TurnInput>(turnCount)
        repeat(turnCount) {
            if (buf.remaining() < 9) return null
            val shooter = buf.get().toInt()
            val angle = buf.short.toInt()
            val power = buf.short.toInt()
            val typeId = buf.get().toInt()
            val brake = buf.short.toInt()
            val anchorCount = buf.get().toInt() and 0xFF
            if (shooter !in 0..1 || angle !in 0..3599 || power !in 0..1000) return null
            if (anchorCount > Tuning.MAX_ANCHORS) return null
            if (buf.remaining() < anchorCount * 5 + 8) return null
            val anchors = ArrayList<Anchor>(anchorCount)
            repeat(anchorCount) {
                val ax = buf.short.toInt()
                val ay = buf.short.toInt()
                val attract = buf.get().toInt() != 0
                anchors.add(Anchor(ax, ay, attract))
            }
            turns.add(TurnInput(shooter, angle, power, typeId, brake, anchors))
        }
        if (buf.remaining() != 8) return null
        val hash = buf.long
        return MatchRecord(mode, difficulty, seed, turns, hash)
    }

    /**
     * Full trust pipeline for an incoming code:
     * decode -> regenerate system from seed -> re-simulate every turn -> compare hash.
     * A forged score cannot survive this; the physics itself is the referee.
     */
    fun decodeAndVerify(text: String): VerifiedMatch? {
        val record = decode(text) ?: return null
        val mode = GameMode.byId(record.mode)
        val spec = SystemGenerator.generate(record.seed, record.difficulty, mode)
        val rebuilt = MatchReplayer.rebuild(spec, record.turns, mode)
        if (rebuilt.verifiedHash != record.hash) return null
        return VerifiedMatch(record, spec, rebuilt.engine)
    }
}

class VerifiedMatch(
    val record: MatchRecord,
    val spec: SystemSpec,
    val engine: MatchEngine,
)
