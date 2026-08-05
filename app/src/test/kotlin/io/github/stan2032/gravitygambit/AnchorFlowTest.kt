package io.github.stan2032.gravitygambit

import io.github.stan2032.gravitygambit.core.GameMode
import io.github.stan2032.gravitygambit.core.Seeds
import io.github.stan2032.gravitygambit.core.ShareCodec
import io.github.stan2032.gravitygambit.core.Tuning
import io.github.stan2032.gravitygambit.core.Vec2
import io.github.stan2032.gravitygambit.ui.GameLaunch
import io.github.stan2032.gravitygambit.ui.game.MatchController
import io.github.stan2032.gravitygambit.ui.game.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Anchors are the mechanic the rest of the design rests on (docs/VISION.md),
 * so the interaction loop is tested end to end: place, flip, remove, fire,
 * and — critically — that an opponent replaying the shot gets the anchors
 * too. Without that last one a duel would silently desync.
 */
class AnchorFlowTest {

    private fun controller(hotseat: Boolean = false) = MatchController.create(
        GameLaunch(
            mode = GameMode.DUEL,
            seed = Seeds.duelRandom(2468L),
            difficulty = Seeds.DUEL_DIFFICULTY,
            hotseat = hotseat,
        )
    )!!

    private fun drive(c: MatchController) {
        var guard = 0
        while (c.phase == Phase.FLYING && guard++ < 4000) c.advanceBy(4)
        if (c.phase == Phase.RESOLVING) c.postResolve()
    }

    /** A spot the controller accepts (clear of bodies and stations). */
    private fun freeSpot(c: MatchController): Vec2 {
        var y = 300.0
        while (y < Tuning.WORLD_H - 300) {
            var x = 120.0
            while (x < Tuning.WORLD_W - 120) {
                val v = Vec2(x, y)
                if (c.isAnchorSpotLegal(v)) return v
                x += 40
            }
            y += 40
        }
        throw AssertionError("no legal anchor spot in this system")
    }

    @Test
    fun tapCyclesAnchorThroughAttractRepelAndGone() {
        val c = controller()
        val spot = freeSpot(c)
        c.tapAnchor(spot)
        assertEquals(1, c.anchors.size)
        assertTrue("first tap places an attractor", c.anchors[0].attract)

        c.tapAnchor(spot)
        assertEquals(1, c.anchors.size)
        assertTrue("second tap flips it to repel", !c.anchors[0].attract)

        c.tapAnchor(spot)
        assertTrue("third tap removes it", c.anchors.isEmpty())
    }

    @Test
    fun budgetIsEnforced() {
        val c = controller()
        var placed = 0
        var y = 300.0
        while (y < Tuning.WORLD_H - 300 && placed < Tuning.MAX_ANCHORS + 3) {
            val v = Vec2(500.0, y)
            if (c.isAnchorSpotLegal(v)) { c.tapAnchor(v); placed++ }
            y += 130
        }
        assertTrue("must have tried more than the budget", placed > Tuning.MAX_ANCHORS)
        assertEquals(Tuning.MAX_ANCHORS, c.anchors.size)
    }

    @Test
    fun anchorsCannotBeDroppedOnBodiesOrStations() {
        val c = controller()
        val body = c.spec.bodies.first()
        assertTrue("a body must reject an anchor", !c.isAnchorSpotLegal(Vec2(body.x, body.y)))
        val station = c.engine.stations[0]
        assertTrue("a station must reject an anchor", !c.isAnchorSpotLegal(Vec2(station.x, station.y)))
    }

    @Test
    fun firedAnchorsReachTheShotAndTheShareCode() {
        val c = controller()
        val spot = freeSpot(c)
        c.tapAnchor(spot)
        val placed = c.anchors.toList()
        c.fire()
        drive(c)

        assertEquals("the shot must record the anchors", placed, c.engine.turns[0].anchors)
        val code = c.duelCode
        assertNotNull("a code duel must emit a code", code)
        val verified = ShareCodec.decodeAndVerify(code!!)
        assertNotNull("anchored shots must verify for the opponent", verified)
        assertEquals(placed, verified!!.record.turns[0].anchors)
    }

    @Test
    fun anchorsAreSpentByTheShotTheyShaped() {
        val c = controller(hotseat = true)
        c.tapAnchor(freeSpot(c))
        assertEquals(1, c.anchors.size)
        c.fire()
        drive(c)
        assertTrue("anchors must not carry into the next turn", c.anchors.isEmpty())
    }

    @Test
    fun placementModeEndsWhenTheShotIsFired() {
        val c = controller()
        c.placingAnchors = true
        c.fire()
        assertTrue("firing must leave placement mode", !c.placingAnchors)
    }
}
