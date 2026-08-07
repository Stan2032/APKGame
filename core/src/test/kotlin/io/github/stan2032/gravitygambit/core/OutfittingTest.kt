package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Run progression, and the one thing it must never do.
 *
 * D-34's guarantee is that Expedition runs **provably end**: travel cost
 * rises without bound while refunds are bounded, so past a computed depth
 * every leg is a net loss however well it is played. Permanent upgrades are
 * exactly the kind of feature that quietly voids a proof like that, so the
 * load-bearing test here is [runsAlwaysEndEvenFullyOutfitted] — the same
 * best-possible-player argument, re-run with everything bought.
 */
class OutfittingTest {

    private val maxed: Map<String, Int> =
        Outfitting.Outfit.entries.associate { it.id to it.tiers }

    // ---- the proof, re-checked under upgrades ----

    @Test
    fun runsAlwaysEndEvenFullyOutfitted() {
        // Best player the rules permit — one shot, three stars, richest route
        // every time — now carrying every fitting at max tier.
        var r = Expedition.Run(seed = 5L, fuel = Outfitting.startFuel(maxed))
        var legs = 0
        while (!r.over && legs < Expedition.MAX_LEGS * 2) {
            val best = Expedition.routesFor(r).maxBy { Expedition.refundFor(it, 3) }
            r = Expedition.afterClear(r, best, shots = 1, stars = 3)
            legs++
        }
        println("fully outfitted perfect run ended at depth ${r.depth}, fuel ${r.fuel}")
        assertTrue("upgrades made a perfect run unbounded — D-34's proof is void", r.over)
        assertTrue(
            "the outfitted perfect run hit the leg cap (${r.depth}) rather than running dry",
            r.depth < Expedition.MAX_LEGS,
        )
    }

    @Test
    fun aWreckRefundNeverReturnsTravel() {
        // Travel is what makes the run finite, so a rig may soften a bad
        // system but must never buy back distance. Checked at a depth where
        // travel dominates the leg's cost.
        val depth = 15
        val leg = Expedition.routesFor(Expedition.Run(seed = 2L, depth = depth)).first()
        val shots = Tuning.CAMPAIGN_SHOTS
        val full = Expedition.legCost(depth, leg.mutator, shots)
        val refund = Outfitting.wreckRefund(maxed, shots, leg.mutator)
        val shotPart = shots * Expedition.shotCost(leg.mutator)
        assertTrue("a rig must never refund more than the shots cost", refund <= shotPart)
        assertTrue("a wrecked leg must still cost the travel", full - refund >= Expedition.travelCost(depth))
    }

    @Test
    fun outfitsMakeAWreckHurtLessButStillHurt() {
        val run = Expedition.Run(seed = 3L, depth = 8, fuel = 60)
        val leg = Expedition.routesFor(run).first()
        val bare = Expedition.afterWreck(run, leg, shots = 4)
        val rigged = Expedition.afterWreck(run, leg, shots = 4, outfits = maxed)
        assertTrue("a salvage rig must soften a wreck", rigged.fuel > bare.fuel)
        assertTrue("a wreck must still cost fuel even fully rigged", rigged.fuel < run.fuel)
        assertEquals("a wreck still advances the run", run.depth + 1, rigged.depth)
        assertEquals(1, rigged.wrecks)
    }

    // ---- the economy ----

    @Test
    fun salvageRewardsDepthAndNeverPaysForWrecking() {
        val shallow = Expedition.Run(seed = 1L, depth = 2, starsEarned = 4)
        val deep = Expedition.Run(seed = 1L, depth = 14, starsEarned = 30)
        assertTrue(Outfitting.salvageFor(deep) > Outfitting.salvageFor(shallow))
        assertTrue("even a failed run must bring something home", Outfitting.salvageFor(Expedition.Run(seed = 1L)) > 0)

        // Wrecking must never be worth more than not wrecking: two runs that
        // reached the same depth, one by clearing and one by wrecking.
        val cleared = Expedition.Run(seed = 1L, depth = 6, starsEarned = 12, wrecks = 0)
        val wrecked = Expedition.Run(seed = 1L, depth = 6, starsEarned = 12, wrecks = 4)
        assertTrue(
            "wrecks must not increase salvage — that would reward the thing the run avoids",
            Outfitting.salvageFor(wrecked) <= Outfitting.salvageFor(cleared),
        )
    }

    @Test
    fun buyingIsPureAndCannotOverdrawOrExceedMaxTier() {
        val tank = Outfitting.Outfit.RESERVE_TANK
        assertNull("cannot buy what cannot be afforded", Outfitting.buy(0, emptyMap(), tank))

        var salvage = 500
        var owned = emptyMap<String, Int>()
        for (t in 1..tank.tiers) {
            val cost = tank.costOfNextTier(t - 1)!!
            val (nextSalvage, nextOwned) = Outfitting.buy(salvage, owned, tank)!!
            assertEquals("buying must deduct exactly the listed cost", salvage - cost, nextSalvage)
            assertEquals(t, Outfitting.tierOf(nextOwned, tank))
            salvage = nextSalvage
            owned = nextOwned
        }
        assertNull("cannot buy past the last tier", Outfitting.buy(salvage, owned, tank))
        assertNull("a maxed outfit has no next cost", tank.costOfNextTier(tank.tiers))

        // Each tier costs more than the last, so the ladder is a decision
        // rather than a formality.
        for (o in Outfitting.Outfit.entries) {
            for (t in 1 until o.tiers) {
                assertTrue(
                    "${o.id} tier ${t + 1} must cost more than tier $t",
                    o.costOfNextTier(t)!! > o.costOfNextTier(t - 1)!!,
                )
            }
        }
    }

    @Test
    fun aFullLadderIsSeveralRunsOfWorkNotOneOrFifty() {
        // Calibration, stated openly and modelled like D-30's tier curve and
        // D-34's run depths. A ladder a single run buys is not progression; a
        // ladder fifty runs buys is a wall.
        val total = Outfitting.investedIn(maxed)
        val perGoodRun = Outfitting.salvageFor(Expedition.Run(seed = 1L, depth = 12, starsEarned = 24))
        val runs = total.toDouble() / perGoodRun
        println("full outfit ladder: $total salvage, ~$perGoodRun per good run → ~%.1f runs".format(runs))
        assertTrue("the ladder is bought too fast to be progression (%.1f runs)".format(runs), runs >= 4.0)
        assertTrue("the ladder is a wall (%.1f runs)".format(runs), runs <= 20.0)
    }

    @Test
    fun startingFuelRisesOnlyWithTheTank() {
        assertEquals(Expedition.START_FUEL, Outfitting.startFuel(emptyMap()))
        assertEquals(
            Expedition.START_FUEL + Outfitting.FUEL_PER_TANK_TIER * Outfitting.Outfit.RESERVE_TANK.tiers,
            Outfitting.startFuel(maxed),
        )
        // The survey array is information only: it must move no number.
        val surveyOnly = mapOf(Outfitting.Outfit.SURVEY_ARRAY.id to 1)
        assertEquals(Expedition.START_FUEL, Outfitting.startFuel(surveyOnly))
        assertEquals(0, Outfitting.wreckRefund(surveyOnly, shots = 4, mutator = Expedition.Mutator.NONE))
        assertTrue(Outfitting.canSurveyAhead(surveyOnly))
        assertTrue(!Outfitting.canSurveyAhead(emptyMap()))
    }

    @Test
    fun tiersAreClampedAgainstACorruptedSave() {
        // Profile is forward-compatible JSON a user could in principle edit.
        // A nonsense tier must not multiply into unbounded starting fuel.
        val absurd = mapOf(Outfitting.Outfit.RESERVE_TANK.id to 9999)
        assertEquals(Outfitting.Outfit.RESERVE_TANK.tiers, Outfitting.tierOf(absurd, Outfitting.Outfit.RESERVE_TANK))
        assertEquals(Outfitting.startFuel(maxed), Outfitting.startFuel(absurd))
        assertEquals(0, Outfitting.tierOf(mapOf("nonsense" to 3), Outfitting.Outfit.RESERVE_TANK))
    }
}
