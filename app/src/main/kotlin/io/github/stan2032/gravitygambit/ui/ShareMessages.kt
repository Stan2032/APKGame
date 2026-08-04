package io.github.stan2032.gravitygambit.ui

/**
 * The text that leaves the game and lands in someone else's chat app.
 *
 * These messages carry more weight than any store listing: a duel code is
 * frequently the recipient's *first* contact with the game, so it must be
 * self-explanatory to someone who has never heard of it — what it is, and
 * what to do with it — without reading like an ad. Voice rules:
 * docs/AI_LOOK_AVOIDANCE.md (pointing test: "code" is insider shorthand, so
 * the instruction says where to paste it rather than naming the mechanism).
 */
object ShareMessages {

    /**
     * Set once the app is live on Play, then every shared code doubles as an
     * install link. Empty until then — a dead link is worse than none.
     */
    const val STORE_URL = ""

    private fun withStoreLink(body: String): String =
        if (STORE_URL.isEmpty()) body else "$body\n\n$STORE_URL"

    /** Mid-duel: the opponent must open the game and paste this to continue. */
    fun duelChallenge(code: String): String = withStoreLink(
        """
        ⚔️ Your move in Gravity Gambit.

        Open the game, tap Duel → "Got a code?", and paste this:

        $code
        """.trimIndent()
    )

    /** Final turn: the match is decided; this code proves the whole thing. */
    fun duelResult(code: String): String = withStoreLink(
        """
        ⚔️ That's the match — paste this into Gravity Gambit (Duel → "Got a code?") to watch how it ended:

        $code
        """.trimIndent()
    )

    /** Daily challenge result. The code is a verifiable proof of the score. */
    fun dailyResult(dailyNumber: Int, score: Int, shots: Int): String = withStoreLink(
        """
        🌌 Gravity Gambit Daily #$dailyNumber — $score points in $shots shots.

        Same star system for everyone today. Paste this into the game to replay my run and see it's real:
        """.trimIndent()
    )
}
