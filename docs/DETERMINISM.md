# Determinism contract

Multiplayer verification (MULTIPLAYER.md) rests on one claim: **given the
same seed and inputs, every device computes the identical match, bit for
bit.** This document is the argument for why that claim holds.

## The rules the engine follows

1. **All inputs are integers.** Aim angle is deci-degrees (0..3599), power is
   thousandths (0..1000), the Retro brake is an exact tick index. No
   device-dependent float (touch coordinates, frame times, sensor values)
   ever enters the simulation. The UI *quantizes first*, then simulates.

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

4. **Own PRNG.** SplitMix64, implemented in `Rng.kt`. `java.util.Random` or
   `kotlin.random` could silently change between platform releases; ours
   cannot. All generation (systems, names, daily relocations, explosion
   cosmetics) derives from it.

5. **Deterministic iteration everywhere.** Body lists are ordered; the
   integrator visits projectiles in creation order; the solver scans a fixed
   grid. No hash-map iteration touches simulation state.

6. **Fixed timestep.** `dt = 1/60` simulated seconds, always. Rendering pace
   (2 ticks/frame) is presentation only; a replayed match steps the same
   ticks regardless of frame rate.

## Defense in depth: the hash

We do not merely *assume* the above — every shot folds trajectory samples
(every 8th tick, positions quantized to 1e-3), split/brake/collision events
and outcomes into a 64-bit FNV-1a hash; per-shot hashes fold into a match
hash carried by every share code. If any device ever disagreed with the
math above, verification would fail loudly rather than diverge silently.
The unit suite (`CoreTests.kt`) locks this down:

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
