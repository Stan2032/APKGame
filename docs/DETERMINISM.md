# Determinism contract

Multiplayer verification (MULTIPLAYER.md) rests on one claim: **given the
same seed and inputs, every device computes the identical match, bit for
bit.** This document is the argument for why that claim holds.

## The rules the engine follows

1. **All inputs are integers.** The full set, which is exactly the fields of
   `TurnInput`: aim angle in deci-degrees (0..3599), power in thousandths
   (0..1000), projectile type, the Retro brake as an exact tick index,
   **anchors as integer world coordinates plus a flag**, and the **declared
   gambit id**. No device-dependent float (touch coordinates, frame times,
   sensor values) ever enters the simulation. The UI *quantizes first*, then
   simulates — the floating-joystick aim is `kotlin.math` right up until it
   rounds to deci-degrees, and not one step further.

2. **Only exactly-specified floating-point operations.** The integrator uses
   IEEE-754 double `+ - * /` and `Math.sqrt` only. The JVM spec (and ART)
   defines these as correctly-rounded IEEE-754 operations — bit-identical on
   every conforming device. Since Java 17, strict FP semantics are the only
   semantics (JEP 306); ARM NEON/VFP hardware is IEEE-754 compliant, and the
   JIT may not fuse `a*b+c` into FMA because that would change results.

3. **Transcendentals only via StrictMath.** `cos`/`sin`/`toRadians` for
   launch vectors and split rotation go through `StrictMath` (fdlibm),
   which is *defined to the bit* — unlike `Math.sin`, which is allowed
   1-ulp leeway and platform intrinsics.

4. **Own PRNG, and generation is in the contract too.** SplitMix64,
   implemented in `Rng.kt`. Both devices *regenerate the system from the
   seed*, so layout generation is as much a part of determinism as the
   simulation is — including its float math (the difficulty bands go through
   `StrictMath.pow`, and the solver's accept/reject comparisons decide which
   layout a seed produces). `java.util.Random` or `kotlin.random` could silently change
   between platform releases; ours cannot. All generation (systems, names, daily relocations, explosion
   cosmetics) derives from it.

5. **Deterministic iteration everywhere.** Body lists are ordered; the
   integrator visits projectiles in creation order; the solver scans a fixed
   grid. No hash-map iteration touches simulation state.

6. **Fixed timestep, and playback paced by the clock — not the frame.**
   `dt = 1/60` simulated seconds, always. Presentation advances the sim by
   ticks derived from **wall-clock elapsed time** (`PLAYBACK_TICKS_PER_SEC`),
   never from frame count. This is not a stylistic choice: frame-counted
   pacing shipped once and ran the game at *double speed on 120Hz panels*,
   because "2 ticks per frame" silently means a different thing on every
   display. A replayed match steps the same ticks regardless of frame rate,
   and takes the same wall-clock time to do it.

## Defense in depth: the hash

We do not merely *assume* the above — everything that can change an outcome
folds into a 64-bit FNV-1a hash, and per-shot hashes fold into a match hash
carried by every share code.

**What the shot hash contains** (`LiveShot`): every input listed above
(angle, power, type, gambit id, and each anchor's x/y/attract); trajectory
samples every 8th tick with positions quantized to 1e-3; and each
split / brake / body-collision / station-hit / out-of-bounds / timeout event
with its tick. **What the match hash adds** (`MatchEngine`): each shot's
hash, whether its declaration was kept, whether the objective has been met,
and any daily target relocation.

> **If you add a field to `TurnInput`, it must be folded in.** An input that
> is not hashed is an input a sender can edit after the fact without the
> receiver noticing — which is precisely how a re-declared gambit would have
> become free points. `DeterminismTest.everyTurnInputFieldChangesTheHash`
> exists to fail when that list falls behind; extend it with the field.

If any device ever disagreed with the math above, verification would fail
loudly rather than diverge silently. The suite locks this down (`CoreTests`,
`GambitTest`, `ObjectiveTest`, `MovingBodyTest`):

- identical inputs ⇒ identical hashes (and a re-run of a full match
  through `MatchReplayer` reproduces live play exactly),
- one deci-degree of aim ⇒ different hash,
- a forged hash ⇒ `decodeAndVerify` returns null,
- generation is value-identical for the same seed.

## The one intentional trade-off

Semi-implicit Euler at 60 Hz is not a high-accuracy integrator — it is a
*stable, cheap, exactly-reproducible* one, which is what a game needs. The
"true" trajectory is irrelevant; the *agreed* trajectory is everything.
Accuracy-critical alternatives (RK4, symplectic higher-order) would also be
deterministic but cost more per tick with no gameplay benefit at our scales.
