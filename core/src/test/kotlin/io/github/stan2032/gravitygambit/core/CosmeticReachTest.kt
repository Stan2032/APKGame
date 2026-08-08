package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a share code can and cannot carry — and therefore what the shop is
 * allowed to promise.
 *
 * The 0.16.3 audit found the shop selling trails with *"Your rival sees these
 * in the replay."* They do not. A code carries `(seed, turns, hash)`; the
 * receiving device regenerates everything else from the seed, so a cosmetic
 * choice never crosses the wire and an opponent's replay draws the classic
 * trail. That is the same limit that killed Blind (D-23) and forced duel
 * normalization (D-29) — **the code carries moves, not the shopping** — and
 * this test states it structurally so the next person to write shop copy has
 * a fact to check against rather than an assumption to inherit.
 */
class CosmeticReachTest {

    @Test
    fun aShareCodeCarriesNoCosmeticChoice() {
        // Checked against the *type* rather than against one encoded match:
        // what a code can carry is a property of TurnInput's fields, and that
        // list is already asserted exhaustively by
        // DeterminismTest.everyTurnInputFieldChangesTheHash. So there is
        // nowhere for a cosmetic to hide, and this states the consequence.
        val fields = TurnInput::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
        for (f in fields) {
            assertTrue(
                "TurnInput.$f looks cosmetic — cosmetics must not enter the protocol without a " +
                    "deliberate version bump, and the shop copy would need revisiting (D-36)",
                !f.contains("trail", ignoreCase = true) && !f.contains("skin", ignoreCase = true),
            )
        }
    }

    @Test
    fun theShopSellsOnlyThingsPlayCanReach() {
        // "Everything above is earnable by playing" is a claim about the
        // stardust catalogue, so it is checked against the catalogue: every
        // trail, shell and preview tier must have a finite stardust price.
        for (t in Economy.TRAILS) {
            assertTrue("trail ${t.id} has no earnable price", t.cost >= 0)
        }
        for (u in Economy.PROJECTILE_UNLOCKS) {
            assertTrue("shell ${u.typeId} has no earnable price", u.cost > 0)
        }
        for (c in Economy.PREVIEW_UPGRADE_COSTS) {
            assertTrue("a preview tier has no earnable price", c > 0)
        }
        // The season pass is the exception that proves it: it grants a lane
        // play cannot reach, which is exactly why it must not be listed under
        // "these just skip the grind" (D-36).
        assertTrue(
            "season_pass must stay in the product list — it is sold, just not from the shop's grind-skip section",
            Iap.SEASON_PASS in Iap.ALL_PRODUCT_IDS,
        )
        assertEquals(
            "the grind-skip section is stardust packs and the cosmetic pack; anything else needs its own framing",
            listOf(Iap.STARDUST_SMALL, Iap.STARDUST_LARGE, Iap.COSMIC_PACK).toSet(),
            (Iap.ALL_PRODUCT_IDS - Iap.SEASON_PASS).toSet(),
        )
    }
}
