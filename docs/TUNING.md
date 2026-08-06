# TUNING.md — constants, safe ranges, coupled invariants

All knobs in `core/Model.kt → Tuning` (+ bands in `SystemGenerator`, rewards in `Economy`). Tune → `GG_SKIP_ANDROID=1 ./gradlew :core:test` → playtest.

## ⚠️ Versioning invariant (read first)
Sim-affecting constants (anything LiveShot/MatchEngine/Generator reads: DT, speeds, masses, radii, softening, split/brake, damage, bands, world size, relocation logic) are part of the **replay protocol**. Changing any of them makes old share codes fail hash verification (correct behavior — but must be intentional): bump `ShareCodec.VERSION`, keep decode of older versions returning a clean "outdated code" rejection. UI-only values (colors, anim durations, trail lengths) are free. **Gambit thresholds count as sim-affecting**: they decide whether a declaration held, which decides whether a hit landed. Protocol history: v2 moving bodies/slingshot · v3 anchors · v4 curated layouts · v5 gambits.

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
Since 0.7.0 layouts are **curated, not scattered** — placement is built around
the shot it wants to force, so these are the knobs of a construction, not of a
rejection sampler:
- **Keeper** — sits *on* the station-to-station line at t ∈ 0.38–0.62 (±12 jitter), r 58–86. Star at M 6e6–1.05e7, else planet at M = r²×(700–900). Its job is to eclipse the direct shot.
- **Shepherds** — alternate sides of the line at offsets 140–340, r²×(560–860). They are what makes a curved route plausible. A layout with fewer than two bodies total is discarded: a keeper alone is a wall, not a puzzle.
- **Repulsor** d≥5 @35%: r 24, M −(0.8–1.6)e6, intangible.
- **Pulsar** d≥4 @28%: r 26–34, M 1.6e6–2.8e6, breathing on `PULSAR_PERIOD` 210 ticks between `PULSAR_MIN` 0.35 and +`PULSAR_SWING` 1.3 of that.
- **Comet** d≥2 @42%: r 14–20, M 1.4e5–3.2e5, drifting 38–78 u/s on one axis and wrapping at `WRAP_MARGIN` 260. Hitting one pays `COMET_HIT_BONUS` 40.
- Placement: separation ≥ (r1+r2)×1.9+30; stations ≥ bodyR+26+56 clear.
- **Naive-shot rejection**: any layout beatable by aiming within `NAIVE_SPREAD_DECI` 220 (±22°) of the target at any of {250,400,550,700,850,1000} power is discarded — on *every* path including the fallbacks, which is where the bug originally hid.

## Difficulty bands (Generator)
`minRate = max(6e-4, 0.019×0.80^d)`, `maxRate = min(0.30, 22×minRate)`; solver = 180 angles × powers {300,500,700,900,1000} × `SOLVER_MAX_TICKS` 950. Campaign d = sector×2 + level/6 (cap 11); daily d=6; duel d=5 (+ both-sides rate, ratio ∈ [0.25,4]).
- **Recalibrated in 0.7.0** and these are the live numbers. The previous band (`0.03×0.72^d`, ×14, 600 ticks) was written for open corridors that curated layouts no longer produce: its floor sat *above* what difficulty 0 actually measures (~2.7%), so every early level was silently taking the fallback path, and the 600-tick horizon scored genuine curved solutions as misses. Bands follow the game; the game does not follow the bands.
- Too-hard reports ⇒ raise 0.80→0.85 (gentler decay). Too-easy ⇒ 0.80→0.75.
- Keep angle step ≥20 deci-deg. For solver cost, measure — do not quote: absolute timings are machine-relative and the same unchanged code has measured 17ms and 136ms on different days (docs/PERFORMANCE.md §Absolute numbers).

## Combat pacing
- Campaign: 1-hit core, 4 shots. Stars: **★★★ = one shot AND that shot worked the field** (slingshot gain ≥ `THREE_STAR_MIN_GAIN` 45, or any style bonus, or a kept declaration); ★★ = ≤2 shots; ★ otherwise. Shot count alone used to be the whole rule, which is how a straight lob that used no gravity scored full marks (0.7.0). A hit inside a *broken* declaration cannot be the winning shot, because it did not land (0.9.0).
- Duel: HP 100; damage Bolt 34 (3 hits), Breaker 50 (2), Triad 3×14 (all-connect 42), Retro 30. Breaker counterweight = maxPowerFrac 0.72 (can't reach far orbits).
- SPLIT_TICK 72 (1.2s); SPLIT_ANGLE 8°; BRAKE_FACTOR 0.45; OWN_STATION_GRACE 30 ticks (must exceed LAUNCH_OFFSET exit time).
- Daily: 5 shots; hit=100+style; relocation zone y ∈ [110, 0.45H].

## Style bonuses
Graze <15u = +15 · Longshot ≥360 ticks = +10 · Trickshot launch-vs-target cos < −0.17 (>100°) = +25 · Slingshot = (gain − `SLINGSHOT_MIN_GAIN` 60) × 0.10, capped +45. All four only on enemy hit (no farming misses). Comet strike = +40 per comet and pays **whether or not the shot went on to connect** — a comet is a deliberate target of opportunity, not a consolation prize.

## Gambits (Gambit.kt — sim-affecting, see §Versioning)
Declared before firing; kept ⇒ payout multiplier, broken ⇒ the shot is wasted (the hit does not land). Multipliers are tenths so scoring stays integer-exact.
- Featherweight ×1.5 — power < `FEATHERWEIGHT_MAX_POWER` 400.
- Two-body ×1.8 — ≥2 distinct bodies approached within `DEFLECT_RADII` 1.8 of their own radius (scaled by radius, because a bigger body here is a heavier one).
- Stolen speed ×1.6 — gain ≥ `STOLEN_SPEED_MIN_GAIN` 120. Set from a 201-shot distribution (median gain 101); at the first value tried, 70, roughly nine winning shots in ten satisfied it, which is not a stake.
- The long way ×2.2 — never crosses the launch→target line and strays ≥ `LONG_WAY_MIN_DETOUR` 150 from it, sampled outside a `DETOUR_CAP` 130 radius at each end.
- Payout lands on style in campaign/daily and on **damage** in duels, which is the only currency a duel reads.
- The menu is proved per seat by the generator; raising any threshold above what levels can supply silently empties menus, so re-run `GambitTest.everyOfferedGambitIsOneTheArenaCanActuallyPay` after touching these.

## Anchors (docs/VISION.md §3.1 — sim-affecting)
`ANCHOR_MASS` 9.0e5 (negated when repelling) · `MAX_ANCHORS` 3 · `ANCHOR_RADIUS` 9 · `ANCHOR_MIN_CLEARANCE` 46. Strong enough to visibly bend a shot over a few hundred units, weak enough not to drag one to the target from anywhere. Generation rejects layouts where one anchor can rescue more than `MAX_LEVERAGE` 0.80 of currently-missing shots.

## Economy (Economy.kt — replay-safe, tune freely)
- First clear 25+10d+10★; re-clear 5+2★; star improvement 10/★. Daily ✦ = (score/10 cap 60) × streak multiplier (×1.2 at 3 days, ×1.4 at 7, ×1.6 at 14, then capped), first play/day only. Duel **code** W/L 60/10; **hot-seat pays 0** — one person holds both seats.
- Sinks: Breaker ★12/✦250 · Triad ★30/✦600 · Retro ★50/✦900; preview 90→180→270 @ ✦250/✦600; trails 300–900.
- Sector gate 20/36★. Ad: watch ✦40 ×5/day; Pass ×2 + ✦50/day (keep Pass ≥ max ad yield — ADS.md math).
- Target pacing: early sector ≈ 3–4 levels per unlock-step; ~2 weeks casual to full arsenal.

## Feel heuristics (playtest checklist)
1. A straight lob at mid power should NOT reach the target on d≥2 (gravity must matter).
2. At least one "wow" slingshot per sector (if solver rate band forces only boring solutions, widen maxRate).
3. Duel first-turn advantage: watch win% by seat over ~20 codes; if P0 >60%, increase station y-symmetry or alternate first shooter by seed parity (protocol bump!).
4. Retro brake should feel like a save ~1 in 3 attempts, not a guaranteed aimbot (BRAKE_FACTOR ↑ toward 0.6 weakens it).
