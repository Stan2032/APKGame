# TUNING.md — constants, safe ranges, coupled invariants

All knobs in `core/Model.kt → Tuning` (+ bands in `SystemGenerator`, rewards in `Economy`). Tune → `GG_SKIP_ANDROID=1 ./gradlew :core:test` → playtest.

## ⚠️ Versioning invariant (read first)
Sim-affecting constants (anything LiveShot/MatchEngine/Generator reads: DT, speeds, masses, radii, softening, split/brake, damage, bands, world size, relocation logic) are part of the **replay protocol**. Changing any of them makes old share codes fail hash verification (correct behavior — but must be intentional): bump `ShareCodec.VERSION`, keep decode of older versions returning a clean "outdated code" rejection. UI-only values (colors, anim durations, trail lengths) are free.

## Physics core
| Knob | Now | Effect / safe range | Coupled to |
|---|---|---|---|
| DT | 1/60 | sim resolution; do not change (protocol + pacing) | everything |
| WORLD 1000×1600 | — | portrait board | generator margins, camera |
| MIN/MAX_SPEED | 140/520 | shot energy range; feel knob #1 | star mass (below) |
| SOFTENING2 | 16 | anti-singularity; ↑ = mushier close passes | graze tuning |
| MAX_TICKS | 1500 (25s) | orbit patience; ↓ if timeout shots feel long | LONGSHOT_TICKS |
| OUT_MARGIN | 320 | how far off-board before dead; ↑ = more comeback slingshots | — |

**Escape-velocity coupling:** star mass 6e6–1.1e7 ⇒ v_esc at r=400 ≈ `sqrt(2M/400)` ≈ 173–235. MAX_SPEED (520) must stay well above (escapes exist); MIN_SPEED (140) below (capture orbits exist). Keep `MIN < v_esc(min star, mid-range) < MAX×0.6` or shots become all-escape (boring) / all-capture (frustrating).

## Bodies (Generator)
- Star r 55–85, M 6e6–1.1e7; binary d≥8 @30% (2× 3.2e6–5.5e6).
- Planets: count 2..6 by difficulty; r 28–60; M = r²×(550–850) ⇒ 4.3e5–3.1e6. Slingshot strength scales here.
- Repulsor d≥5 @35%: M −(0.8–1.6)e6, intangible.
- Placement: separation ≥ (r1+r2)×1.9+30; stations ≥ bodyR+26+60 clear.

## Difficulty bands (Generator)
`minRate = max(8e-4, 0.03×0.72^d)`, `maxRate = min(0.28, 14×minRate)`; solver = 180 angles × powers {300,500,700,900,1000} × 600 ticks. Campaign d = sector×2 + level/6 (cap 11); daily d=6; duel d=5 (+ both-sides rate, ratio ∈ [0.25,4]).
- Too-hard reports ⇒ raise 0.72→0.78 (gentler decay). Too-easy ⇒ 0.72→0.66.
- Solver cost ≈ 60–150ms/attempt JVM; keep angle step ≥20 deci-deg.

## Combat pacing
- Campaign: 1-hit core, 4 shots; stars 1/2/≥3 shots = ★★★/★★/★.
- Duel: HP 100; damage Bolt 34 (3 hits), Breaker 50 (2), Triad 3×14 (all-connect 42), Retro 30. Breaker counterweight = maxPowerFrac 0.72 (can't reach far orbits).
- SPLIT_TICK 72 (1.2s); SPLIT_ANGLE 8°; BRAKE_FACTOR 0.45; OWN_STATION_GRACE 30 ticks (must exceed LAUNCH_OFFSET exit time).
- Daily: 5 shots; hit=100+style; relocation zone y ∈ [110, 0.45H].

## Style bonuses
Graze <15u = +15 · Longshot ≥360 ticks = +10 · Trickshot launch-vs-target cos < −0.17 (>100°) = +25. All only on enemy hit (no farming misses).

## Economy (Economy.kt — replay-safe, tune freely)
- First clear 25+10d+10★; re-clear 5+2★; star improvement 10/★. Daily ✦ = score/10 cap 60 (first play/day). Duel code W/L 60/10.
- Sinks: Breaker ★12/✦250 · Triad ★30/✦600 · Retro ★50/✦900; preview 90→180→270 @ ✦250/✦600; trails 300–900.
- Sector gate 20/36★. Ad: watch ✦40 ×5/day; Pass ×2 + ✦50/day (keep Pass ≥ max ad yield — ADS.md math).
- Target pacing: early sector ≈ 3–4 levels per unlock-step; ~2 weeks casual to full arsenal.

## Feel heuristics (playtest checklist)
1. A straight lob at mid power should NOT reach the target on d≥2 (gravity must matter).
2. At least one "wow" slingshot per sector (if solver rate band forces only boring solutions, widen maxRate).
3. Duel first-turn advantage: watch win% by seat over ~20 codes; if P0 >60%, increase station y-symmetry or alternate first shooter by seed parity (protocol bump!).
4. Retro brake should feel like a save ~1 in 3 attempts, not a guaranteed aimbot (BRAKE_FACTOR ↑ toward 0.6 weakens it).
