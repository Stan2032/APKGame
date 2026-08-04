# Changelog

Format: date · commit · change. Newest first. [Unreleased] = on branch, pre-Play-release.

## [Unreleased]

### 2026-08-04
- `docs` — Full documentation system: AGENTS.md, TODO.md, CHANGELOG.md, docs/README.md index, DECISIONS.md (ADRs), ARCHITECTURE.md, TUNING.md, TESTING.md, ROADMAP.md, STORE_LISTING.md, AI_LOOK_AVOIDANCE.md.
- `pending` — Play Games Services v2 research snapshot (identity/cloud-save delegation) + roadmap entry. (docs commit; CI run #4)
- `e093387` — Rewarded-only ads architecture (opt-in only: shop free-stardust ✦40 ×5/day cap; post-match optional double) behind NoOp provider (no SDK/network yet); Ad Pass subscriptions (`ad_pass_weekly`/`ad_pass_monthly`): 2× match stardust + ✦50 daily + no ads, billing reconciles both directions; pricing math in docs/ADS.md. (CI run #3)
- `2708198` — Fix: missing `pointerInput` import in GameScreen (sole cause of run #1's 8 cascade errors). **CI run #2 GREEN — first successful APK/AAB artifacts.**
- `0a1ea6e` — **Gravity Gambit v0.1.0 initial implementation.** :core deterministic N-body engine (IEEE-strict, quantized inputs, FNV-1a hashing), procgen with solvability solver + difficulty bands, tamper-proof share-code codec (serverless PVP), MatchEngine rules, economy; full unit suite incl. end-to-end PVP verification. :app Compose UI (7 screens), campaign/daily/duel (hot-seat + code), shop, Billing 8.3.0, DataStore, no INTERNET permission, targetSdk 36. CI workflow (APK+AAB). Docs: DESIGN, DETERMINISM, MULTIPLAYER, MONETIZATION, PLAY_STORE_CHECKLIST, RELEASE, PRIVACY_POLICY, reference/play-policy-snapshot. (CI run #1 failed on import; superseded)

## Release history
- (none yet — first Play release will be 0.1.0)
