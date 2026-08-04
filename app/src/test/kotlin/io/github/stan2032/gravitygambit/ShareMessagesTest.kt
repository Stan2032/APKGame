package io.github.stan2032.gravitygambit

import io.github.stan2032.gravitygambit.core.ShareCodec
import io.github.stan2032.gravitygambit.ui.ShareMessages
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A shared duel code is often the recipient's first encounter with the game,
 * so these messages have to survive being read cold by a stranger — and the
 * embedded code has to survive the decoder's tolerant substring scan.
 */
class ShareMessagesTest {

    private val sampleCode = "GG1.R0cxAgUAAAAAAAAAKgEAA5gDLAAA__8AAAAAAAAAAAAAAAA"

    @Test
    fun challengeTellsAStrangerWhatToDo() {
        val msg = ShareMessages.duelChallenge(sampleCode)
        assertTrue("must name the game", msg.contains("Gravity Gambit"))
        assertTrue("must say where to paste", msg.contains("Duel"))
        assertTrue("must carry the code", msg.contains(sampleCode))
    }

    @Test
    fun messagesKeepTheCodeRecoverable() {
        // ShareCodec scans for the prefix anywhere in pasted text; the
        // surrounding prose must not break that.
        for (msg in listOf(
            ShareMessages.duelChallenge(sampleCode),
            ShareMessages.duelResult(sampleCode),
            ShareMessages.dailyResult(4, 380, 5) + "\n\n" + sampleCode,
        )) {
            val idx = msg.indexOf(ShareCodec.PREFIX)
            assertTrue("code prefix missing from share text", idx >= 0)
            val extracted = msg.substring(idx).takeWhile {
                it.isLetterOrDigit() || it == '-' || it == '_' || it == '.'
            }
            assertTrue("code truncated by surrounding text", extracted.startsWith(sampleCode))
        }
    }

    @Test
    fun dailyResultStatesTheScorePlainly() {
        val msg = ShareMessages.dailyResult(12, 450, 5)
        assertTrue(msg.contains("#12"))
        assertTrue(msg.contains("450"))
        assertTrue(msg.contains("5 shots"))
    }

    @Test
    fun noStoreLinkUntilOneExists() {
        // A dead link is worse than none; STORE_URL stays empty pre-launch.
        val msg = ShareMessages.duelChallenge(sampleCode)
        if (ShareMessages.STORE_URL.isEmpty()) {
            assertTrue("no placeholder URLs may leak into shared text", !msg.contains("http"))
        } else {
            assertTrue(msg.contains(ShareMessages.STORE_URL))
        }
    }
}
