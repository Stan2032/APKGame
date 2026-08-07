package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A run mode lives or dies on its economy, and an economy is a claim about
 * arithmetic — so it is checked here rather than felt for later.
 *
 * The load-bearing test is [runsAlwaysEnd]: if a perfect player can run
 * forever, Expedition is not a run, it is an idle game with extra steps.
 */
class ExpeditionTest {

    private fun run(depth: Int = 0, fuel: Int = Expedition.START_FUEL) =
        Expedition.Run(seed = 1234L, depth = depth, fuel = fuel)

    // ---- the economy ----

    @Test
    fun runsAlwaysEnd() {
        // The best player the rules permit: clears every system in one shot,
        // three stars, always taking the route that pays most. If *this* run
        // is unbounded, no real run ever ends.
        var r = run()
        var legs = 0
        while (!r.over && legs < Expedition.MAX_LEGS * 2) {
            val best = Expedition.routesFor(r).maxBy { Expedition.refundFor(it, 3) }
            r = Expedition.afterClear(r, best, shots = 1, stars = 3)
            legs++
        }
        println("perfect run ended at depth ${r.depth} with ${r.fuel} fuel, score ${r.score}")
        assertTrue(
            "a perfect player never ran out — the fuel economy has no clock in it",
            r.over,
        )
        // MAX_LEGS is a bound on state, not the intended ending. If the
        // perfect run is stopped by the cap rather than by fuel, the cost
        // curve is too shallow to be doing any work.
        assertTrue(
            "the perfect run hit the leg cap (${r.depth}) rather than running dry — " +
                "travel cost is not actually ending runs",
            r.depth < Expedition.MAX_LEGS,
        )
    }

    /**
     * Run depth for a player who always clears in [shots] for [stars], wrecks
     * every [wreckEvery] legs (0 = never), and picks the richest route.
     */
    private fun modelledDepth(shots: Int, stars: Int, wreckEvery: Int = 0): Int {
        var r = run()
        var leg = 0
        while (!r.over) {
            val choice = Expedition.routesFor(r).maxBy { Expedition.refundFor(it, stars) }
            r = if (wreckEvery > 0 && leg % wreckEvery == wreckEvery - 1) {
                Expedition.afterWreck(r, choice, Tuning.CAMPAIGN_SHOTS)
            } else {
                Expedition.afterClear(r, choice, shots, stars)
            }
            leg++
        }
        return r.depth
    }

    @Test
    fun modelledRunsLandInAPlayableBand() {
        // Calibration evidence, and modelled rather than measured — exactly
        // like the season tier curve (D-30). No solver here can tell us how
        // many shots a real person needs, so the archetypes are assumptions,
        // stated openly. What this test *does* prove is that the economy maps
        // skill onto run length monotonically and inside a sane band, which
        // is the part that is arithmetic rather than psychology.
        val expert = modelledDepth(shots = 2, stars = 3)
        val steady = modelledDepth(shots = 3, stars = 2)
        val rough = modelledDepth(shots = 4, stars = 1)
        val unlucky = modelledDepth(shots = 3, stars = 2, wreckEvery = 3)
        println("modelled depths — expert $expert, steady $steady, rough $rough, unlucky $unlucky")

        assertTrue("skill must map onto depth: expert ($expert) ≤ steady ($steady)", expert > steady)
        assertTrue("steady ($steady) must outlast rough ($rough)", steady > rough)
        assertTrue("wrecks must cost depth", unlucky < steady)

        // A run nobody survives past leg 3 is a punishment; one that runs 40
        // legs is a chore. Both ends are judgement calls and both are stated
        // here so a future retune has to argue with a number.
        assertTrue("even a rough run must be worth starting (got $rough)", rough >= 4)
        assertTrue("an expert run must still end in one sitting (got $expert)", expert <= 32)
    }

    @Test
    fun theTerminationArgumentHolds() {
        // The proof the class note claims, stated as arithmetic: past
        // BLEED_DEPTH, travel alone exceeds the best clear any player could
        // possibly earn, so fuel strictly decreases from there.
        assertTrue(
            "travel must outgrow the richest possible refund inside the leg cap",
            Expedition.BLEED_DEPTH < Expedition.MAX_LEGS,
        )
        assertTrue(Expedition.travelCost(Expedition.BLEED_DEPTH + 1) > Expedition.MAX_REFUND)

        // And MAX_REFUND must really be the maximum, not a stale guess: sweep
        // every route, every mutator, every star count.
        var observed = 0
        for (seed in 0L until 60L) {
            for (leg in Expedition.routesFor(Expedition.Run(seed = seed))) {
                for (stars in 1..3) {
                    observed = maxOf(observed, Expedition.refundFor(leg, stars))
                }
            }
        }
        assertEquals(
            "MAX_REFUND is out of step with the route/mutator tables",
            Expedition.MAX_REFUND, observed,
        )
    }

    @Test
    fun shootingWellDecidesTheRunOnceTravelBites() {
        // With a flat shot cost the *absolute* saving from a tidy clear is
        // constant, so the claim is not "the gap grows" — it is that the gap
        // stops being pocket change. There is a depth where two shots keep
        // the run alive and four shots end it, on the same system.
        val depth = 12
        val leg = Expedition.routesFor(run(depth = depth))
            .first { it.route == Expedition.Route.RICH }
        // The smallest tank a tidy clear can survive on, derived rather than
        // guessed: arrive, fire twice, bank the refund, and still afford the
        // next journey plus one shot.
        val survive = Expedition.travelCost(depth + 1) + Expedition.shotCost()
        val tank = survive + Expedition.legCost(depth, leg.mutator, shots = 2) -
            Expedition.refundFor(leg, stars = 2)
        val tidy = Expedition.afterClear(run(depth = depth, fuel = tank), leg, shots = 2, stars = 2)
        val sloppy = Expedition.afterClear(run(depth = depth, fuel = tank), leg, shots = 4, stars = 1)
        println("leg $depth: tank $tank, tidy leaves ${tidy.fuel}, sloppy leaves ${sloppy.fuel}, need $survive")
        assertTrue("a tidy clear must leave more in the tank", tidy.fuel > sloppy.fuel)
        assertTrue("the tidy run continues", !tidy.over)
        assertTrue("the sloppy run on the same system ends", sloppy.over)
    }

    @Test
    fun aWreckHurtsButDoesNotEndTheRun() {
        // Ending on one bad system would make the first minute the whole
        // game, and would punish the player who took the hard route — which
        // is the choice the mode wants to encourage.
        val leg = Expedition.routesFor(run()).first { it.route == Expedition.Route.RICH }
        val before = run(fuel = 16)
        val after = Expedition.afterWreck(before, leg, shots = Tuning.CAMPAIGN_SHOTS)
        assertTrue("a wreck must cost fuel", after.fuel < before.fuel)
        assertEquals("a wreck still advances the run", before.depth + 1, after.depth)
        assertEquals(1, after.wrecks)
        assertTrue("one wreck from a full tank must not end the run", !after.over)
        assertEquals("a wreck scores nothing", before.score, after.score)
    }

    @Test
    fun fuelNeverGoesNegative() {
        // The UI renders this number; a negative tank is a bug the player sees.
        var r = run(depth = 30, fuel = 2)
        r = Expedition.afterWreck(r, Expedition.routesFor(r).first(), shots = 4)
        assertTrue(r.fuel >= 0)
        assertTrue("out of fuel must read as over", r.over)
    }

    @Test
    fun theHardRouteActuallyPaysMore() {
        // A risk with no premium is not a choice, it is a trap.
        val legs = Expedition.routesFor(run(depth = 4))
        val safe = legs.first { it.route == Expedition.Route.SAFE }
        val rich = legs.first { it.route == Expedition.Route.RICH }
        assertTrue("the deep way must be harder", rich.difficulty > safe.difficulty)
        assertTrue(
            "the deep way must pay more for the same performance",
            Expedition.refundFor(rich, 2) > Expedition.refundFor(safe, 2),
        )
        assertTrue(
            "the deep way must score more for the same performance",
            Expedition.scoreFor(rich, 2) > Expedition.scoreFor(safe, 2),
        )
    }

    // ---- the junction ----

    @Test
    fun everyJunctionOffersOneOfEachFlavourAndNeverRerolls() {
        val r = run(depth = 3)
        val a = Expedition.routesFor(r)
        val b = Expedition.routesFor(r)
        assertEquals("leaving and returning to a junction must not reroll it", a, b)
        assertEquals(Expedition.Route.entries.size, a.size)
        assertEquals(Expedition.Route.entries.toSet(), a.map { it.route }.toSet())
    }

    @Test
    fun thethreeOffersAreThreeDifferentSystems() {
        // Otherwise "choose your route" is choosing a difficulty label printed
        // on the same board.
        val seeds = Expedition.routesFor(run(depth = 2)).map { it.seed }
        assertEquals(seeds.size, seeds.toSet().size)
    }

    @Test
    fun junctionsDifferByDepthAndByRun() {
        val r1 = Expedition.Run(seed = 1L)
        val r2 = Expedition.Run(seed = 2L)
        assertNotEquals(Expedition.routesFor(r1), Expedition.routesFor(r2))
        assertNotEquals(Expedition.routesFor(r1), Expedition.routesFor(r1.copy(depth = 1)))
    }

    @Test
    fun unchartedAlwaysCarriesARealCondition() {
        // NONE means "no mutator on this route", not a possible roll — an
        // Uncharted route that turned out to be ordinary would be the mode
        // lying about the one thing that distinguishes it.
        for (seed in 0L until 40L) {
            for (depth in 0 until 6) {
                val legs = Expedition.routesFor(Expedition.Run(seed = seed, depth = depth))
                for (leg in legs) {
                    if (leg.route == Expedition.Route.UNKNOWN) {
                        assertNotEquals("Uncharted rolled no condition", Expedition.Mutator.NONE, leg.mutator)
                    } else {
                        assertEquals("only Uncharted carries a condition", Expedition.Mutator.NONE, leg.mutator)
                    }
                }
            }
        }
    }

    @Test
    fun everyMutatorIsReachable() {
        // A condition that can never be rolled is dead code pretending to be
        // content. Sweep enough junctions that each must appear.
        val seen = HashSet<Expedition.Mutator>()
        for (seed in 0L until 200L) {
            Expedition.routesFor(Expedition.Run(seed = seed))
                .forEach { seen.add(it.mutator) }
        }
        val real = Expedition.Mutator.entries.filter { it != Expedition.Mutator.NONE }
        assertTrue("unreachable mutators: ${real - seen}", seen.containsAll(real))
    }

    // ---- the mutators, each doing what it says ----

    @Test
    fun eachMutatorHasAnEffectSomethingCanRead() {
        val base = Expedition.routesFor(run()).first { it.route == Expedition.Route.SAFE }

        assertEquals(
            "Thin reserves must actually cost more per shot",
            Expedition.shotCost() + 1, Expedition.shotCost(Expedition.Mutator.VOLATILE),
        )

        val windfall = base.copy(mutator = Expedition.Mutator.WINDFALL)
        assertEquals(
            "Rich seam must double the reward",
            2 * Expedition.refundFor(base, 2), Expedition.refundFor(windfall, 2),
        )

        // Dark run is D-23's Blind, legitimate here because a run is solo and
        // nothing is sent: it binds only the player who chose it.
        //
        // Asserted in TICKS, not in tier number, and that distinction is the
        // whole point. This test used to say `previewLevelFor(blind) == 0`,
        // which looks like "blind" and is not: tier 0 is the base 90-tick
        // trace every player owns, so the mutator was removing a *purchase*
        // while its own description promised "No aim trace on this system".
        // The test pinned the implementation instead of the claim, so it
        // guarded the bug rather than catching it (0.19.0).
        val blind = base.copy(mutator = Expedition.Mutator.BLIND)
        assertEquals(
            "a Dark run promises no aim trace, so it must yield zero preview ticks",
            0,
            Economy.previewTicks(
                Expedition.previewLevelFor(blind, owned = Economy.MAX_PREVIEW_LEVEL),
            ),
        )
        assertEquals(
            "an ordinary leg must not touch the player's preview tier",
            Economy.MAX_PREVIEW_LEVEL, Expedition.previewLevelFor(base, Economy.MAX_PREVIEW_LEVEL),
        )
    }

    // ---- legs are ordinary campaign systems ----

    @Test
    fun everyOfferedLegGeneratesAPlayableSystem() {
        // Expedition is additive (D-32b): a leg is a campaign match, so it
        // must come out of the same generator with the same guarantees. If
        // this ever fails, the mode has grown its own content path.
        var r = run()
        repeat(6) {
            for (leg in Expedition.routesFor(r)) {
                val spec = SystemGenerator.generate(leg.seed, leg.difficulty, GameMode.CAMPAIGN)
                assertTrue(
                    "leg at depth ${r.depth} route ${leg.route} generated an unsolvable system",
                    spec.solverHitRate > 0.0,
                )
                assertEquals(2, spec.stations.size)
            }
            r = Expedition.afterClear(r, Expedition.routesFor(r).first(), shots = 2, stars = 2)
        }
    }

    @Test
    fun difficultyClimbsWithDepthAndStaysInRange() {
        var last = -1
        for (depth in 0 until Expedition.MAX_LEGS) {
            val d = Expedition.baseDifficulty(depth)
            assertTrue("difficulty $d out of the generator's range", d in 0..11)
            assertTrue("difficulty must never fall as the run deepens", d >= last)
            last = d
        }
        assertEquals("a long run must reach the hardest systems", 11, Expedition.baseDifficulty(Expedition.MAX_LEGS - 1))
        for (leg in Expedition.routesFor(run(depth = Expedition.MAX_LEGS - 1))) {
            assertTrue("route delta pushed difficulty out of range", leg.difficulty in 0..11)
        }
    }

    // ---- what a run is worth ----

    @Test
    fun aRunPaysStardustAndSeasonXpOnceAtTheEnd() {
        // Both currencies must key on depth, or the mode pays the same as
        // playing the campaign while also risking it.
        val shallow = Expedition.Run(seed = 1L, depth = 2, score = 300)
        val deep = Expedition.Run(seed = 1L, depth = 14, score = 2000)
        assertTrue(Expedition.payout(deep) > Expedition.payout(shallow))
        assertTrue(Season.expeditionXp(deep.depth, deep.wrecks) > Season.expeditionXp(shallow.depth, shallow.wrecks))

        // A run that ended immediately still pays: a mode that can pay
        // nothing is a mode people stop opening.
        assertTrue(Season.expeditionXp(0, 0) > 0)
        assertTrue(Expedition.payout(Expedition.Run(seed = 1L)) > 0)

        // Wrecks cost XP but can never drive it negative, however badly a run went.
        for (depth in 0..Expedition.MAX_LEGS) {
            assertTrue(
                "an all-wreck run at depth $depth paid negative XP",
                Season.expeditionXp(depth, depth) >= 0,
            )
        }
    }

    @Test
    fun payoutRewardsDepthAndIsNeverInsulting() {
        val short = Expedition.payout(Expedition.Run(seed = 1L, depth = 1, score = 150))
        val long = Expedition.payout(Expedition.Run(seed = 1L, depth = 12, score = 1800))
        assertTrue("a run that got nowhere must still pay something", short > 0)
        assertTrue("a deep run must pay more than a shallow one", long > short * 3)
    }
}
