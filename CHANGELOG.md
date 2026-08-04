# Changelog

Format: date · commit · change. Newest first. [Unreleased] = on branch, pre-Play-release.

## [Unreleased]

### 2026-08-04
- `pending` — Renderer rebuilt for art + performance (owner directive: both, always). RenderCache precomputes all brushes/starfield/ring geometry per (system, canvas); zero per-frame allocation; trail moved off snapshot state (deque + version counter); allocation-free LiveShot.visitProjectiles. FIXED real bug: playback speed was tied to display refresh (2x fast on 120Hz) — now wall-clock paced at 120 ticks/s with catch-up clamp. Art: nebula backdrop, 150-star field with hero-star sparkles, breathing star coronas, planet rings (seeded 30% of large planets) + atmosphere rims + terminator shadows, counter-rotating void-anomaly shear arcs, station antennas with blinking beacons, rotating target reticles, comet-glow projectiles, trail color LUT, power ring on aim, station-kill screen flash, cinematic vignette. New docs/PERFORMANCE.md binds all future hot-path changes; AGENTS.md standing priorities updated.
- `pending` — Local full-build loop enabled (owner allowlisted dl.google.com; SDK 36 installed in env). Lint added to verify loop; fixed its 2 errors (LocalActivity instead of Context-cast in ShopScreen/GameScreen). New MatchRulesTests: duel damage/winner/turn-cap-draw, campaign star math, daily scoring+relocation, heavy damage, seed stability, preview budget. Store listing graphics generated → `store/` via `tools/store_assets.py`.
- `docs` — Full documentation system: AGENTS.md, TODO.md, CHANGELOG.md, docs/README.md index, DECISIONS.md (ADRs), ARCHITECTURE.md, TUNING.md, TESTING.md, ROADMAP.md, STORE_LISTING.md, AI_LOOK_AVOIDANCE.md.
- `pending` — Play Games Services v2 research snapshot (identity/cloud-save delegation) + roadmap entry. (docs commit; CI run #4)
- `e093387` — Rewarded-only ads architecture (opt-in only: shop free-stardust ✦40 ×5/day cap; post-match optional double) behind NoOp provider (no SDK/network yet); Ad Pass subscriptions (`ad_pass_weekly`/`ad_pass_monthly`): 2× match stardust + ✦50 daily + no ads, billing reconciles both directions; pricing math in docs/ADS.md. (CI run #3)
- `2708198` — Fix: missing `pointerInput` import in GameScreen (sole cause of run #1's 8 cascade errors). **CI run #2 GREEN — first successful APK/AAB artifacts.**
- `0a1ea6e` — **Gravity Gambit v0.1.0 initial implementation.** :core deterministic N-body engine (IEEE-strict, quantized inputs, FNV-1a hashing), procgen with solvability solver + difficulty bands, tamper-proof share-code codec (serverless PVP), MatchEngine rules, economy; full unit suite incl. end-to-end PVP verification. :app Compose UI (7 screens), campaign/daily/duel (hot-seat + code), shop, Billing 8.3.0, DataStore, no INTERNET permission, targetSdk 36. CI workflow (APK+AAB). Docs: DESIGN, DETERMINISM, MULTIPLAYER, MONETIZATION, PLAY_STORE_CHECKLIST, RELEASE, PRIVACY_POLICY, reference/play-policy-snapshot. (CI run #1 failed on import; superseded)

## Release history
- (none yet — first Play release will be 0.1.0)
