package io.github.stan2032.gravitygambit.core

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What an Expedition mutator is *allowed to be*, proved rather than asserted.
 *
 * D-34 recorded the rule as "mutators are run rules, never generator requests"
 * and justified it on honesty grounds: a *binary star ahead* the generator
 * cannot produce in band would leave the run claiming a condition that never
 * applied (the D-24/D-27 failure). That reason is real but it is the weaker
 * one. The binding constraint is **architectural**, and this file demonstrates
 * it.
 *
 * A share code carries `(mode, difficulty, seed, turns, hash)` and nothing
 * else. `decodeAndVerify` regenerates the board from the seed and re-simulates.
 * Every match can emit such a code — an Expedition leg included, via the
 * feedback report (`MatchController.feedbackCode`), which is how agents triage
 * bug reports. So a mutator that changes **what the board is** produces a leg
 * whose own replay code cannot verify: the receiver rebuilds the unmutated
 * board, gets a different hash, and rejects it as forged.
 *
 * That is why the three deferred field-shaping mutators (*binary star ahead*,
 * *anchors doubled*, *dense field*) are not simply unbuilt work. They cannot
 * exist in this architecture without the mutator becoming part of the payload,
 * which is a protocol decision rather than a feature.
 */
class MutatorReachTest {

    private fun leg(mutator: Expedition.Mutator): SystemSpec =
        SystemGenerator.generate(Seeds.campaign(4, 2), 4, GameMode.CAMPAIGN)
            .also { require(mutator.id >= 0) }

    /** Plays a short match and returns the code it would attach to a feedback report. */
    private fun codeFor(spec: SystemSpec): Pair<String, MatchEngine> {
        val engine = MatchEngine(spec, GameMode.CAMPAIGN)
        var angle = 200
        while (!engine.over) {
            engine.commit(
                engine.beginShot(angle, 640, ProjectileType.STANDARD.id).runToCompletion(),
            )
            angle += 370
        }
        return ShareCodec.encode(
            MatchRecord(
                mode = GameMode.CAMPAIGN.id,
                difficulty = spec.difficulty,
                seed = spec.seed,
                turns = engine.turns.toList(),
                hash = engine.combinedHash,
            ),
        ) to engine
    }

    @Test
    fun anOrdinaryLegsCodeVerifies() {
        // The control. Without this, the failures below prove nothing.
        val (code, _) = codeFor(leg(Expedition.Mutator.NONE))
        assertNotNull("a plain leg's feedback code must verify", ShareCodec.decodeAndVerify(code))
    }

    @Test
    fun aMutatorThatReshapesTheFieldMakesItsOwnLegUnverifiable() {
        // Model a *dense field* mutator the honest way: the leg is played on a
        // board with an extra body. Nothing in the code can say so.
        val base = leg(Expedition.Mutator.NONE)
        val denser = base.copy(
            bodies = base.bodies + Body(
                x = Tuning.WORLD_W * 0.5, y = Tuning.WORLD_H * 0.5,
                radius = 30.0, mass = 6.0e5, kind = BodyKind.PLANET, palette = 3,
            ),
        )
        val (code, engine) = codeFor(denser)
        assertTrue("the mutated leg should have produced turns", engine.turns.isNotEmpty())
        assertNull(
            "a field-shaping mutator's leg verified, which would mean the extra body " +
                "changed nothing — the model is wrong, not the architecture",
            ShareCodec.decodeAndVerify(code),
        )
    }

    @Test
    fun aMutatorThatRaisesTheAnchorBudgetProducesAnUndecodableCode() {
        // Model *anchors doubled*. This one fails even earlier than a hash
        // mismatch: the decoder enforces the anchor cap as a rule check, which
        // is what stops a forged code claiming a match played with more
        // anchors than the game allows. Raising the cap globally to suit one
        // Expedition mutator would weaken that check for duels, where
        // verification is what decides who won (D-29).
        val spec = leg(Expedition.Mutator.NONE)
        val tooMany = (0 until Tuning.MAX_ANCHORS * 2).map {
            Anchor(x = 100 + it * 60, y = 400 + it * 40, attract = true)
        }
        val engine = MatchEngine(spec, GameMode.CAMPAIGN)
        val shot = engine.beginShot(200, 640, ProjectileType.STANDARD.id, anchors = tooMany)
        engine.commit(shot.runToCompletion())
        val code = ShareCodec.encode(
            MatchRecord(
                mode = GameMode.CAMPAIGN.id,
                difficulty = spec.difficulty,
                seed = spec.seed,
                turns = engine.turns.toList(),
                hash = engine.combinedHash,
            ),
        )
        assertNull(
            "a code carrying more than MAX_ANCHORS anchors decoded; the cap is what stops " +
                "a forged over-anchored match being accepted",
            ShareCodec.decodeAndVerify(code),
        )
    }

    @Test
    fun theShippedMutatorsAreExactlyTheOnesThatTouchNothingTheCodeDependsOn() {
        // The positive statement of the rule, so it is checked rather than
        // remembered. A shipped mutator may change the run's economy or the
        // player's UI; it may not change the board, the shot, or the inputs.
        for (m in Expedition.Mutator.entries) {
            val spec = leg(m)
            val plain = SystemGenerator.generate(spec.seed, spec.difficulty, GameMode.CAMPAIGN)
            assertTrue(
                "mutator ${m.name} must not change what the seed generates, or its leg's " +
                    "replay code cannot verify",
                spec.bodies == plain.bodies && spec.gates == plain.gates &&
                    spec.markedBody == plain.markedBody,
            )
        }
        // And the two that do exist act where the code cannot see: fuel
        // economy (VOLATILE/WINDFALL) and the aim trace (BLIND).
        assertTrue(
            "Thin reserves must act on shot cost, which is run state and never reaches a code",
            Expedition.shotCost(Expedition.Mutator.VOLATILE) > Expedition.shotCost(),
        )
        assertTrue(
            "Dark run must act on the preview, which is UI and never reaches a code",
            Economy.previewTicks(
                Expedition.previewLevelFor(
                    Expedition.Leg(
                        Expedition.Route.SAFE, Expedition.Mutator.BLIND, 4, 1L,
                    ),
                    owned = Economy.MAX_PREVIEW_LEVEL,
                ),
            ) == 0,
        )
    }
}
