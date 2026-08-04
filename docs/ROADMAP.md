# ROADMAP.md — living release plan

Tactical queue: TODO.md. Rationale for entries: DESIGN.md §Roadmap. This file = sequencing.

## v0.1 — built ✅ (2026-08-04)
Full game: campaign/daily/duels, deterministic engine + verified-code PVP, shop, Billing 8 scaffold, ads architecture (NoOp), CI APK+AAB.

## v0.2 — "feels right" (pre-store, no account needed)
- Physics/pacing tuning from real playtests (TUNING.md checklist).
- Haptics detail pass (graze buzz, brake click), explosion/trail polish.
- Synthesized SFX + seed-derived ambient (no licensed assets).
- Any UI friction found on first devices (nudge ergonomics, text scale).
Exit: 5 external humans play 15+ min without being asked twice.

## v1.0 — store launch
- Keystore + Play account + listing assets (STORE_LISTING.md), privacy policy hosted.
- Internal → closed test (12 testers × 14 days, the schedule anchor) → production.
- IAP products created post-first-AAB; prices per MONETIZATION.md.
Exit: production live at 20%→100% rollout.

## v1.1 — "your progress, everywhere" (first content update)
- Play Games Services v2: auto sign-in, Snapshots cloud save, daily leaderboard, ~10 achievements (facts: reference/play-policy-snapshot.md §PGS).
- Coordinated compliance flip: +INTERNET, Data Safety "User IDs", privacy policy rev.
Exit: cross-device save works; daily has a worldwide board.

## v1.2 — monetization data pass (conditional)
- AdMob rewarded integration ONLY if D1/D7 retention justifies (ADS.md playbook + UMP + app-ads.txt).
- Price/pass tuning from real eCPM; seasonal cosmetic drop (tests cosmic_pack "future trails" promise).

## v2.0 — realtime
- WebRTC lockstep duels over the existing deterministic engine (MULTIPLAYER.md §Phase 2); minimal signaling service; matchmaking = code-rooms first, ladder later.
- Orbital motion for bodies (pos(t) rails) — ShareCodec VERSION bump, dual-version decode window.

## v2.x — depth
- Daily ghost replays (friend's run overlaid). Weekly leagues on seed sets. Level Up program mechanics if PGS adoption is real. Localization wave 2.
