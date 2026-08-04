# TODO — living work queue

Discipline (owner directive): consult at START and END of every work unit; done
items move to ARCHIVE below with dates (this file only grows); newly discovered
work is added the moment it's discovered. Owner key: [H]=human-only,
[A]=agent-doable, [H/A]=either.

## NOW (blocks first playtest / release track)
- [ ] [H] Sideload debug APK from latest green CI run → first real playtest. Report feel.
- [ ] [H] Audition the synthesized SFX on-device (they've never been *heard*, only computed); report which need re-voicing → `tools/gen_sfx.py` params.
- [ ] [A] Physics feel tuning pass after playtest feedback (docs/TUNING.md; likely: speeds, star mass, difficulty band slope).
- [ ] [H] Create Play Console account ($25, ID verification — days of lead time; start now).
- [ ] [H] Generate upload keystore + back it up (docs/RELEASE.md §Signing).
- [ ] [H] Recruit 12+ closed-test testers (14 consecutive days — longest pole in the tent).

## NEXT (pre-submission)
- [ ] [H] Feedback relay habit: when testers send GGFB1 blobs, paste each verbatim into `feedback/inbox/YYYY-MM-DD-n.md` (GitHub mobile app works) — agents triage from there (feedback/README.md).
- [ ] [H] 4–8 phone screenshots from real gameplay (shot list in docs/STORE_LISTING.md).
- [ ] [H] Host docs/PRIVACY_POLICY.md publicly (GitHub Pages ok) → URL into Play Console.
- [ ] [H] Play Console: app content forms (Data Safety = none collected; content rating; ads = no).
- [ ] [H] After first AAB upload: create IAP products (3 INAPP + 2 SUBS ids in docs/MONETIZATION.md).
- [ ] [A] Set `ShareMessages.STORE_URL` to the real Play listing URL once live — every shared duel code then doubles as an install link (one-line change, guarded by a test that forbids placeholder links pre-launch).
- [ ] [H/A] Decide whether to attempt excluding `com.google.android.datatransport` to drop INTERNET/ACCESS_NETWORK_STATE (D-3). Needs a device: exclusion risks NoClassDefFoundError inside Play Billing at purchase time. Low priority — current wording is accurate as-is.
- [ ] [A] Release QA script pass (docs/RELEASE.md) — MUST run on the R8 build and exercise save/load, corrupted-code rejection, and purchase-restore paths specifically (minification risk areas).
- [ ] [A] Wire CI release signing via secrets (blocked on [H] keystore existing).
- [ ] [H] Approve or restyle store graphics drafts in `store/` (regen via tools/store_assets.py).

## LATER (post-launch, ordered)
- [ ] [A] v1.1 Play Games Services v2: sign-in + Snapshots cloud save + daily leaderboard + achievements (facts: docs/reference/play-policy-snapshot.md §PGS; Data Safety update required).
- [ ] [A] AdMob rewarded integration IF retention data justifies (docs/ADS.md playbook; needs [H] AdMob account; UMP consent; Data Safety + privacy policy update; app-ads.txt).
- [ ] [A] Ambient audio: seed-derived per-system drone pad (SFX shipped 0.2.0; music/ambience still open).
- [ ] [A] Localization pass (externalize inline strings → res; es/pt/de/fr/ja first). **Deliberately deferred until after first playtest**: copy is still volatile (feel/tuning feedback will rewrite wording), and externalizing early means every copy tweak edits XML instead of the composable. Do it once the wording settles — ideally right before the v1.0 store push. Note `:core` display names (projectile/trail names) are game terms; decide then whether they map to string resources or stay untranslated proper nouns.
- [ ] [A] Tablet/landscape HUD polish (world letterboxes already; HUD could two-column).
- [ ] [A] Baseline Profile + Macrobenchmark once a device lab exists (docs/PERFORMANCE.md).
- [ ] [A] In-app "what's new" + rate prompt (post-retention only, never nagging).
- [ ] [A] Consider first-use coaching for the duel-code flow (mirror of the aim hint added 0.2.0).

## V2 HORIZON
- [ ] Realtime P2P duels: WebRTC lockstep over existing deterministic engine (docs/MULTIPLAYER.md §Phase 2; needs INTERNET + tiny signaling svc).
- [ ] Orbital motion for bodies (deterministic rails pos(t); ShareCodec VERSION bump — docs/TUNING.md §Versioning; MAJOR under SemVer post-1.0).
- [ ] Daily ghost replays (codes already carry everything needed).
- [ ] Weekly leagues on seed sets.

## PARKED / DECIDED-NO (see docs/DECISIONS.md)
- Interstitial/forced ads (D-12: never). Loot boxes (D-13). Accounts/own backend (D-2). Families program opt-in (PLAY_STORE_CHECKLIST).

---

## ARCHIVE (completed — grows forever)
### 2026-08-04
- [x] [A] Release APK audit (`tools/verify_release_apk.py`, wired into CI): permission allowlist, R8 serializer survival, size budget. Caught and corrected the false "no INTERNET permission" claim across all user-facing copy + docs (D-3).
- [x] [A] Share messages rewritten to be legible to a stranger (a duel code is often first contact with the game); `ShareMessages` + tests; STORE_URL hook for launch.
- [x] [A] GeneratorSweepTest: ~50 systems across sectors/extreme difficulties/a month of dailies/duels all proven solvable and sane; daily numbering pinned to 2026-08-01.
- [x] [A] Accessibility pass: canvas + level-cell content descriptions, decorative canvases excluded, large-font clipping fixed (heightIn/FlowRow), AccessibilityTest at 2x font scale.
- [x] [A] Robolectric UI smoke tests + MatchControllerTest (13 app-module tests incl. full code-duel round trip); CI now runs app tests + lint before artifacts.
- [x] [A] Perf: solver early-exit (~2x faster worst-case generation = difficulty 0 = first level played); GenerationPerfTest guardrails; aim preview measured cheap (0.065ms) and documented as do-not-touch.
- [x] [A] Art: AmbientBackdrop (starfield/nebula/comet) behind all menus + translucent panels; OrbitRule divider.
- [x] [A] In-app feedback → `feedback/inbox/` agent pipeline (GGFB1 blob + replay attach + triage protocol + session-start mandate). v0.3.0.
- [x] [A] R8 + resource shrinking with serialization keep rules; release APK 12.4MB → 1.7MB. (was LATER; pulled forward under performance directive)
- [x] [A] Sound design v1: 7 synthesized SFX + SoundManager + settings toggle (`tools/gen_sfx.py`).
- [x] [A] First-launch aim coaching hint (Sector 1·L1, fresh profiles only).
- [x] [A] SemVer 2.0.0 adopted: policy in RELEASE.md, version → 0.2.0/code 2 (owner directive).
- [x] [A] Codec future-version rejection test (protocol gate).
- [x] [A] Renderer rebuild: RenderCache, zero per-frame allocation, refresh-rate-independent pacing, full art pass (nebulae/rings/coronas/anomaly/beacons/reticles/vignette). docs/PERFORMANCE.md standing rules.
- [x] [A] Pointing test on store surfaces ("DUELS BY TEXT", "NO ACCOUNTS"); codified in AI_LOOK_AVOIDANCE.md.
- [x] [A] 512×512 store icon + 1024×500 feature graphic → `store/` (tools/store_assets.py).
- [x] [A] Lint error-free + MatchRulesTests (rule-edge coverage).
- [x] [A] Documentation system: AGENTS/TODO/CHANGELOG + 10 docs + index.
- [x] [A] Store listing copy drafts (STORE_LISTING.md, voice-checked).
- [x] [A] Rewarded-only ads architecture + Ad Pass subscriptions + pricing math.
- [x] [A] PGS v2 research snapshot (identity/cloud-save delegation path).
- [x] [A] v0.1.0: full game, engine + verified serverless PVP, CI, initial docs. First green APK/AAB.
- [x] [H] dl.google.com allowlisted → local full-build loop enabled.
