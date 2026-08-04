# PERFORMANCE.md — standing priority, not a phase

Owner directive: performance is a non-stop priority. Every rendering or
gameplay change gets audited against this doc before merge.

## Budget
- Frame: ≤ 8ms CPU on a mid-tier device (Pixel-a class) at 60Hz — headroom
  for 120Hz panels, thermals, and low-end phones.
- Zero heap allocation per frame in the draw + sim hot paths (GC pauses are
  the jank source on Android; Color/Offset are value types and free).
- Sim cost is trivial by design (~µs per tick); rendering is the only budget
  consumer worth watching.

## The rules
1. **Precompute per (system, canvas).** Anything derivable from the spec +
   size lives in `RenderCache`: star positions, nebula/vignette brushes,
   per-body gradient brushes, ring geometry, stroke widths, trail color LUT.
   The frame pass may only *draw*.
2. **State reads in draw phase only.** The canvas lambda reads `timeSec`,
   `trailVersion`, etc. inside the draw block → Compose invalidates DRAW,
   not composition. Never read per-frame state in composition scope.
3. **Plain collections + version counters over snapshot state** for
   high-frequency data (trail: ArrayDeque + `trailVersion++`). SnapshotStateList
   at 120 appends/sec churns snapshot records.
4. **Time-based pacing, never frame-based.** Playback ticks derive from
   wall-clock (`PLAYBACK_TICKS_PER_SEC=120`, catch-up clamped to 8/frame).
   Frame-counting runs 2x fast on 120Hz — this was a real bug, fixed.
5. **Bounded draw calls.** Trail: cap 600 points, stride 2 above 420, sparse
   glow pass (1 in 3). Starfield: 150 precomputed, 4-point sparkle on ~10.
   Body pass: ≤6 draws each. Explosion: 30 particles + 2 rings + 1 flash rect.
   Current worst-case ≈ 800 primitive draws/frame — cheap for Skia.
6. **Allocation-free iteration APIs** where rendering meets the engine:
   `LiveShot.visitProjectiles` (no lists), `shot::visitProjectiles` reference.
7. **Ambient redraw is a deliberate spend**: menus are static; only the game
   canvas animates continuously. Lever if battery reports appear: drop the
   ambience clock to 30fps during AIMING by frame-skipping in the clock loop.

## Current implementation notes (2026-08-04)
- `RenderCache` rebuilds only on system change or canvas resize.
- Verified-green loop: `./gradlew :core:test :app:assembleDebug :app:lintDebug`.
- Not yet done (levers in reserve, in order of payoff): render bodies+nebulae
  once into a layer/Picture and blit (if body-pass cost ever shows up),
  `graphicsLayer` isolation for HUD, Baseline Profiles + Macrobenchmark for
  startup, R8 (tracked in TODO).

## How to measure (on device)
- Quick look: Developer options → Profile HWUI rendering → bars under green line.
- Numbers: `adb shell dumpsys gfxinfo io.github.stan2032.gravitygambit framestats`.
- Jank hunting: Android Studio Profiler → System Trace during a Triad shot over
  a binary-star field (worst case: 3 projectiles + max trail + max bodies).
- Regression gate (roadmap with CI device lab): Macrobenchmark
  `frameOverrunMs` P95 < 0 on the same scenario.
