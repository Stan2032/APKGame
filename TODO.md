# TODO — living work queue

Done items move to CHANGELOG.md. Owner key: [H]=human-only (needs Stan), [A]=agent-doable, [H/A]=either.

## NOW (blocks first playtest / release track)
- [ ] [H] Sideload debug APK from latest green CI run → first real playtest. Report feel.
- [ ] [A] Physics feel tuning pass after playtest feedback (docs/TUNING.md; likely: speeds, star mass, difficulty band slope).
- [ ] [H] Create Play Console account ($25, ID verification — days of lead time; start now).
- [ ] [H] Generate upload keystore + back it up (docs/RELEASE.md §Signing).
- [ ] [H] Recruit 12+ closed-test testers (14 consecutive days required — longest pole in the tent).

## NEXT (pre-submission)
- [ ] [H/A] 512×512 store icon + 1024×500 feature graphic (export from adaptive icon; concept in docs/STORE_LISTING.md).
- [ ] [H] 4–8 phone screenshots from real gameplay (shot list in docs/STORE_LISTING.md).
- [ ] [H] Host docs/PRIVACY_POLICY.md publicly (GitHub Pages ok) → URL into Play Console.
- [ ] [A] Store listing final copy (draft in docs/STORE_LISTING.md; voice per docs/AI_LOOK_AVOIDANCE.md).
- [ ] [H] Play Console: app content forms (Data Safety = none collected; content rating; ads = no).
- [ ] [H] After first AAB upload: create IAP products (3 INAPP + 2 SUBS ids in docs/MONETIZATION.md).
- [ ] [A] Release QA script pass on release APK (docs/RELEASE.md checklist).
- [ ] [A] Wire CI release signing via secrets (docs/RELEASE.md; deliberately unwired until keystore exists).

## LATER (post-launch, ordered)
- [ ] [A] v1.1 Play Games Services v2: sign-in + Snapshots cloud save + daily leaderboard + achievements (facts: docs/reference/play-policy-snapshot.md §PGS; Data Safety update required).
- [ ] [A] AdMob rewarded integration IF retention data justifies (playbook docs/ADS.md; needs [H] AdMob account + App ID; UMP consent; Data Safety + privacy policy update; app-ads.txt).
- [ ] [A] Sound design: synthesized SFX + seed-derived ambient (no licensed assets).
- [ ] [A] Enable R8/minify with serialization keep rules (docs/DECISIONS.md D-8).
- [ ] [A] Localization pass (externalize inline strings → res; es/pt/de/fr/ja first).
- [ ] [A] Tablet/landscape layout polish (world letterboxes already; HUD could two-column).
- [ ] [A] In-app "what's new" + rate-us prompt (post-retention only, never nagging).

## V2 HORIZON
- [ ] Realtime P2P duels: WebRTC lockstep over existing deterministic engine (docs/MULTIPLAYER.md §Phase 2; needs INTERNET + tiny signaling svc).
- [ ] Orbital motion for bodies (deterministic rails pos(t); requires ShareCodec VERSION bump — docs/TUNING.md §Versioning).
- [ ] Daily ghost replays (friend's run as translucent overlay — codes already carry everything needed).
- [ ] Weekly leagues on seed sets.

## PARKED / DECIDED-NO (see docs/DECISIONS.md)
- Interstitial/forced ads (D-12: never). Loot boxes (D-13). Accounts/own backend (D-2). Families program opt-in (PLAY_STORE_CHECKLIST).
