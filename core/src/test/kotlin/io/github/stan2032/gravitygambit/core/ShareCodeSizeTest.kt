package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
