# ARCHITECTURE.md — code map + data flow (dense, agent-oriented)

## Module rule
`:core` = every rule, deterministic, JVM-only, unit-tested. `:app` = presentation/IO only. If a change involves "who wins / what's earned / where things move" it belongs in :core.

## :core file map (io.github.stan2032.gravitygambit.core)
- `Rng.kt` — SplitMix64 + static `mix(a,b)` seed derivation. FROZEN constants (D-5).
- `Model.kt` — Vec2, GameMode, Body(+BodyKind STAR/PLANET/REPULSOR), Station, SystemSpec, TurnInput (quantized: angleDeci 0..3599, power 0..1000, brakeTick), ProjectileType enum (damage/radius/maxPowerFrac/splits/canBrake), `Tuning` (ALL physics+scoring constants; see TUNING.md).
- `Simulation.kt` — `LiveShot`: tick-stepped shot sim (semi-implicit Euler, dt=1/60, softened gravity, splits, brake, collisions, bounds, style detection, FNV-1a hash folding every 8 ticks + events). `ShotResult`. `Preview.trajectories()` (aim preview = same sim, capped ticks). `ProjView` for rendering.
- `MatchEngine.kt` — turn order, HP, damage, daily scoring+target relocation (`DailyLogic`, seeded by hit count), stars, over/winner logic, combined match hash. `MatchReplayer.rebuild()` = verification path (same engine ⇒ can't diverge).
- `Generator.kt` — `SystemGenerator.generate(seed,difficulty,mode)`: candidate layout (rejection placement) → solver (180 angles × 5 powers, STANDARD, 600 ticks) → accept iff hitRate ∈ band; duel = both-sides + ratio ≤4 fairness; 24 attempts → nearest-miss fallback → hardcoded fallback. `NameGen`, `Seeds` (campaign/daily/duel seed derivation, difficulty curve, LEVELS_PER_SECTOR=12, LAUNCH_EPOCH_DAY=20666).
- `Replay.kt` — `MatchRecord`, `ShareCodec` ("GG1."+base64url: magic/ver/mode/diff/seed/turns[8B each]/hash/crc32; tolerant substring decode; range-validated). `decodeAndVerify()` = decode → regenerate → resim → hash compare → `VerifiedMatch`|null.
- `Economy.kt` — `Profile` (full save state, kotlinx-serialization JSON, ignoreUnknownKeys⇒forward-compatible), `Economy` (trails, projectile unlocks star+dust gates, preview tiers 90/180/270 ticks, rewards, SECTOR_UNLOCK_STARS=20), `AdRewards` (watch ✦40 ×5/day; pass ×2 + ✦50/day), `Iap` (3 INAPP + 2 SUBS ids).

## :app file map (io.github.stan2032.gravitygambit)
- `GravityGambitApp` — Application; owns ProfileRepository singleton.
- `MainActivity` — edgeToEdge, BillingManager.connect(), setContent App().
- `data/ProfileRepository` — DataStore(prefs) "profile_json" ↔ Profile; StateFlow snapshot + atomic `update`; reward writers (recordCampaign/Daily/Duel — apply Ad Pass ×2 via `withPassMultiplier`), `spend`, `recordAdWatch` (capped), `claimPassDailyBonus`, `setAdPassActive`.
- `billing/BillingManager` — Billing 8: INAPP + SUBS queryProductDetails (v8 QueryProductDetailsResult), launch flow (auto offerToken for subs), purchase handling (consume-then-grant consumables; ack entitlements/subs), restore + two-way Ad Pass reconcile.
- `ads/RewardedAds.kt` — `RewardedAdsProvider` (available: StateFlow, show(activity,onReward)); `NoOpRewardedAds` shipped (D-12). AdMob impl = drop-in later (ADS.md).
- `ui/Theme.kt` — palette (BgColor #070B18 …), `BodyPalettes` (indexed body colors), M3 dark scheme.
- `ui/App.kt` — `Nav` sealed (Home/Campaign/Duel/Shop/Settings/Help/Game(GameLaunch)); `GameLaunch` (mode/seed/difficulty/sector/level/epochDay/hotseat/importedCode/nonce — nonce distinguishes retry of identical launch); Ad Pass daily-bonus claim effect.
- `ui/screens/` — Common (ScreenFrame/SectionCard/typography), Home, Campaign (sector cards, ★-gated levels), Duel (hot-seat / create-code / paste+validate / resume-last), Shop (unlocks, preview tiers, trails, free-stardust [ad], Ad Pass subs, IAP — every section self-hides when unavailable), Settings (haptics, privacy blurb, reset w/ confirm), Help.
- `ui/game/MatchController.kt` — presentation FSM around MatchEngine: `Phase` AIMING→FLYING→RESOLVING→(HANDOFF|AIMING|OVER); aim state (drag-slingshot + deci nudges; duel turn flip resets aim toward enemy, campaign/daily keeps last); live sim @2 ticks/frame; imported-code path: verify → rebuild minus last turn → cinematic replay of opponent shot (queue); duel code build (whole match re-encoded per turn); Summary construction; rewardsApplied/dustEarned handshake with UI.
- `ui/game/WorldRenderer.kt` — `WorldTransform` (letterbox fit, world↔screen), DrawScope fns: starfield (seeded twinkle), bodies (star glow/planet gradient/repulsor pulse), stations (HP arc vs pulsing target core), preview dots, trail (equipped-trail gradient; opponent replay = classic), projectiles, aim arrow, explosion (seeded particles).
- `ui/game/GameScreen.kt` — create (background: generate or decodeAndVerify; invalid ⇒ "the physics noticed" error screen); frame loops (clock; FLYING stepper; RESOLVING explosion anim + haptic); rewards-once effect; lastDuelCode persist effect; Canvas + drag input; TopHud (mode status, HP, incoming-replay + style banners); BottomControls (readout, nudges, type chips, FIRE / RETRO BURN); overlays: Handoff (hot-seat pass / code share), Summary (stars, dust ±pass note, optional ad double, next/retry/share/rematch), quit dialog.

## Data flow (one shot)
touch drag → quantize (angleDeci,power ints) → preview via same sim → FIRE → `engine.beginShot` → LiveShot.step ×2/frame (UI thread; ~µs each) → done → `engine.commit(result)` (rules+hash) → RESOLVING anim → next phase. Code duel: turns → `ShareCodec.encode(MatchRecord)` → messenger → peer `decodeAndVerify` → rebuilt engine + replay queue.

## Threading
Main: sim stepping (trivial cost), UI. Dispatchers.Default: system generation + code verification (100ms–3s). Dispatchers.IO: DataStore writes. No other concurrency.

## Persistence
Single JSON Profile in DataStore prefs. Forward-compatible (unknown keys ignored, defaults on new fields). Corrupt ⇒ default Profile (never crash). No match-in-progress persistence (D-9).
