# TODO — living work queue

Discipline (owner directive): consult at START and END of every work unit; done
items move to ARCHIVE below with dates (this file only grows); newly discovered
work is added the moment it's discovered. Owner key: [H]=human-only,
[A]=agent-doable, [H/A]=either.

> 📖 **Publishing: follow [docs/LAUNCH_GUIDE.md](docs/LAUNCH_GUIDE.md)** — it
> sequences every [H] item below with links and waiting times. The three
> multi-day waits (ID verification, bank micro-deposit, 12 testers × 14 days)
> should all be started as early as possible; they, not the code, set the date.

> **Direction (owner, 2026-08-06 — D-32):** alternate feature work and launch
> prep, one unit each; Expedition is **additive**, not a campaign replacement;
> difficulty stays **demanding** (do not soften bands or star rules to widen
> the audience); presentation gets **real investment** — it is a storefront
> product and screenshots sell the listing.

## VISION WORK (docs/VISION.md — the long game; production is not close)
- [x] Stage A engine: anchors in TurnInput + simulation + codec v3 + tests.
- [x] Stage A UI: anchor placement mode — tap to place, tap to flip, tap to remove; live preview; pips show budget. v0.5.0.
- [x] Stage A solver awareness: `anchorLeverage` measures what fraction of missing shots one anchor can rescue; levels that let anchors trivialize aiming are rejected. v0.6.0.
- [ ] [A] Optimise duel generation (~330ms; both-sides solve × more bodies since 0.4.0). Not urgent — behind a loading screen — but it is the slowest path in the game.
- [x] Stage B: Gambits — shipped 0.9.0 (four verifiable declarations, per-seat generator-proved menus, damage multiplier in duels, protocol v5).
- [ ] [H] **Play the gambits**: is ×2.2 damage too swingy in a duel against 100 HP? Does "wasted shot" feel like a fair price or a punishment? Are four declarations enough variety, or does the menu go stale?
- [ ] [A] Consider an unscored *Blind* practice toggle (D-23): the idea is good, only the *scoring* is unverifiable.
- [x] Stage C: objectives beyond destruction — **complete**. Capture (0.10.0), Courier (0.11.0), Thread (0.13.0). Every one generator-proved before it is offered.
- [ ] [H] **Play a Courier system**: is leading a drifting comet satisfying or fiddly? Does the reticle make the target obvious?
- [ ] [H] **Play a Capture system**: is the insertion burn readable, or does it feel like guesswork? Is 4 seconds the right hold? Does the ring communicate the rule without words?
- [ ] [H] **Play a Thread system**: is the order readable from the lit gate alone? Is the 78-unit half-width gate too tight or too forgiving (D-31)?
- [x] [A] Stage D **engine**: Expedition run structure — additive per D-32b. Fuel economy, three routes, run-rule mutators, provable termination. A leg is an ordinary campaign match, so the mode cost the replay protocol nothing. v0.15.0, D-34.
- [x] [A] **Stage D UI: the Expedition screen** — junction with three route cards, the fuel gauge, the run summary, and the Home entry. VISION Stage D complete. v0.16.0.
- [ ] [H] **Play a run.** Does the junction decision feel like a decision, or is the deep way always right? Does the fuel gauge read as a clock? Is a 20+ leg run a sitting or a chore (D-34's numbers are modelled, not measured)? Does losing a run sting the right amount, given nothing is saved?
- [ ] [H] **Judge the run length**: modelled at expert 27 legs / steady 22 / rough 15, which is a *model*, not a measurement (D-34). At ~90s a leg that is 20–40 minutes. If it is a chore, `travelCost`'s slope is the one constant to turn.
- [ ] [A] Field-shaping mutators (*binary star ahead*, *anchors doubled*, *dense field*) — deferred deliberately: each must ask the generator for a layout it may not produce in band, so each needs a **proof that the mutator actually took**, or the run claims a condition that never applied (D-24/D-27's failure mode). Only worth building alongside that proof.
- [ ] [A] Expedition meta-currency + the starting options it buys — **as one unit, never separately**. Paying out an unspendable token first is a number that goes up and means nothing (D-34).
- [ ] [A] Stage E: resonance + audio sonification of pulsar phase.
- [ ] [A] Stage F: debris from misses.

## PRESENTATION (D-32d — real investment, it is a storefront product)
- [x] [A] Impact juice: decaying camera shake (board only, never the HUD) + 75ms hit-stop. v0.13.1.
- [x] [A] **Generated ambience — the biggest presentation hole is closed.** A pad per system, synthesized at runtime from the bodies on the board (pitch from mass, harmony from the objective), seamless by construction, exactly in tune, zero APK bytes and zero CPU while playing. A pulsar's voice swells at its real gravity period. v0.14.0, docs/AUDIO.md, D-33.
- [ ] [H] **Listen to the pads and say whether the character is right** — five samples were sent to you as .wav, or regenerate any time with `./gradlew :core:ambienceWav`. Specifically: does a 7-second loop reveal itself as a loop over a five-minute match? Is the pulsar swell legible as *information* or does it read as wobble? Are the four objective colourings distinguishable by ear or only on paper? Is it too quiet / too loud under the SFX? Full list in docs/AUDIO.md §7.
- [ ] [A] **Multi-pulsar polyrhythm** (VISION §3.4's remaining half): the pad already gives each pulsar its own tremolo, but the generator never makes two with different periods. Constraint discovered in D-33 — a second period only stays phase-locked across loop repeats if it also divides the loop, so periods must come from divisors of 7s rather than being chosen freely.
- [ ] [A] The 7 SFX are still un-auditioned, but no longer *un-auditionable*: `AmbienceWav.write()` is a general WAV writer, so a companion task could dump `res/raw/*.wav` alongside the pads for one listening pass.
- [ ] [A] Win/star flourish on the summary — the moment a level falls is currently understated.
- [ ] [A] Screenshot-moment polish specifically (the shots listed in STORE_LISTING): slingshot arc, capture ring, anchors bending a preview, gambit dialog, season ladder.

## SEASON TRACK (shipped 0.12.0 engine / 0.12.1 UI)
- [x] [A] Season screen, Home entry, XP on the match summary, `season_pass` billing wiring. v0.12.1.
- [ ] [H] Create the `season_pass` product in Play Console alongside the others (docs/MONETIZATION.md).
- [ ] [H] **Judge the season pace on a device** once the screen exists: does tier 1→5 land fast enough to feel real? The curve is calibrated against a *modelled* play rate, which is the best I can do without hands.
- [ ] [A] Consider a second premium lane theme per season (VISION Stage D's Expedition would be the natural source of season-specific rewards).

## NOW (blocks first playtest / release track)
- [ ] [A] **Keep auditing claims against code, doc by doc.** Five passes, every one found something: 0.9.1 two false interlock claims + a stale band; 0.9.2 share-code sizes 5× understated; 0.9.4 TUNING.md two protocol versions old; 0.10.1 DETERMINISM.md's hashed-content list stopped at 0.2.0; 0.12.2 PLAY_STORE_CHECKLIST contradicting itself on permissions + STORE_LISTING describing the 0.2.0 game. 0.13.2 RELEASE.md's QA script was six lines from 0.2.0; 0.14.0 ARCHITECTURE.md's file map had drifted **again** (BodyKind still listed as three kinds when comets and pulsars shipped in 0.4.0; "3 INAPP" after `season_pass` made it four) — note it had been marked *cleared* in an earlier pass, so a file map goes stale roughly as fast as the code moves and needs re-checking every time a file is added, not once. 0.14.1 TESTING.md claimed "78 tests across 8 files" against a real 104 across 19 classes in 12, gave `:app` no count at all, omitted 9 classes between them, and quoted suite runtimes 3× and 2× off — a coverage list is the *easiest* doc to let rot, because nothing fails when it does. Cleared: MONETIZATION.md, ADS.md perks, store copy, RELEASE.md, PRIVACY_POLICY, TESTING.md, feedback/README (audited 0.14.1 — blob format matches `Feedback.compose` field for field, including the `match:` context line, and AGENTS.md really does mandate inbox triage at session start). Still unaudited: LAUNCH_GUIDE links (needs live URL checks — see below), DESIGN.md beyond its roadmap section, HELP/in-app copy. A claim nobody has checked against the artifact is a guess (D-3).
- [ ] [H] **Judge the 0.8.0 aiming on a device**: does the floating stick land where your thumb expects? Is the 26-unit dead zone too sticky or too loose? Do the fine-tune buttons ramp fast enough held, without overshooting?
- [ ] [H] **Feel question from 0.8.0**: `AIM_FULL_PULL` is 300 world units. Because `WorldTransform` is a *fit*, that is already a fixed fraction of the viewport — ~30% of the board's width in portrait, ~19% of its height in landscape — not a fixed pixel count, so it does not need converting to screen units. The only open question is whether that fraction is the right one, which is a thumb question, not a geometry one.
- [ ] [H] **Re-test 0.7.0 levels**: is the straight shot really gone? Do levels now feel like they have a purpose — is there a visible route to find? Are they now too *hard* (bands were recalibrated from measurement, not feel)?
- [ ] [H] **Judge the 0.4.0 changes on a device**: does the power meter read better than the old percentage? Are comets fun to lead, or frustrating? Is the Slingshot bonus paying out often enough to teach the whip-around habit? Does Home still feel plain?
- [ ] [H] Sideload debug APK → first real playtest. Report feel. Get it from the green CI run linked on the open PR (Actions → the run → Artifacts → `gravity-gambit-debug-apk`); since 0.9.3 CI runs on PRs and `main` rather than every branch push.
- [ ] [H] Audition the synthesized SFX (they've never been *heard*, only computed); report which need re-voicing → `tools/gen_sfx.py` params. No longer needs a device — see the WAV-dump item under PRESENTATION.
- [ ] [A] Physics feel tuning pass after playtest feedback (docs/TUNING.md; likely: speeds, star mass, difficulty band slope).
- [ ] [H] Create Play Console account ($25, ID verification — days of lead time; start now).
- [ ] [H] Generate upload keystore + back it up (docs/RELEASE.md §Signing).
- [ ] [H] Recruit 12+ closed-test testers (14 consecutive days — longest pole in the tent).

## NEXT (pre-submission)
- [ ] [H] Feedback relay habit: when testers send GGFB1 blobs, paste each verbatim into `feedback/inbox/YYYY-MM-DD-n.md` (GitHub mobile app works) — agents triage from there (feedback/README.md).
- [ ] [H] 4–8 phone screenshots from real gameplay (shot list in docs/STORE_LISTING.md).
- [ ] [H] Host docs/PRIVACY_POLICY.md publicly (GitHub Pages ok) → URL into Play Console.
- [ ] [H] Play Console: app content forms (Data Safety = none collected; content rating; ads = no).
- [ ] [H] After first AAB upload: create the **six** IAP products (4 one-time incl. `season_pass`, 2 subs — docs/MONETIZATION.md). Cross-check against `Economy.Iap` rather than any doc list.
- [ ] [A] Set `ShareMessages.STORE_URL` to the real Play listing URL once live — every shared duel code then doubles as an install link (one-line change, guarded by a test that forbids placeholder links pre-launch).
- [ ] [H/A] Decide whether to attempt excluding `com.google.android.datatransport` to drop INTERNET/ACCESS_NETWORK_STATE (D-3). Needs a device: exclusion risks NoClassDefFoundError inside Play Billing at purchase time. Low priority — current wording is accurate as-is.
- [x] [A] Release QA script rewritten (docs/RELEASE.md) — was six lines from 0.2.0 that predated anchors, gambits, all three objectives, the season and duel normalization. Now ordered by risk, starting with the minification danger zone, and includes the D-29 fresh-profile duel check. v0.13.2.
- [ ] [H] **Verify the 11 unreachable links in LAUNCH_GUIDE/PLAY_STORE_CHECKLIST.** I could only reach 2 of 13 from this environment (both `developer.android.com`, both 200); `play.google.com`, `support.google.com` and even `semver.org` are blocked by the sandbox's egress policy, which is **not** evidence the links are dead. Click through them once when you next open the guide — a dead link in a launch checklist wastes time at the worst moment. Do not let an agent mark these verified without actually loading them.
- [x] [A] CI release signing wired via secrets — **self-activating**, so nothing needs turning on later: add the four repo secrets and the next run signs. Until then CI still builds everything but labels the bundle `gravity-gambit-release-aab-DEBUGKEY-DO-NOT-UPLOAD`, and `verify_release_apk.py` prints `signed by:` on every run. v0.15.1.
- [ ] [H/A] **The SIGNED CI path has never run** — only the unsigned one. Verified on the runner (run 31128642249, all 21 steps green): the decode step's no-secrets branch, the audit's unsigned branch, the shred step, and the artifact genuinely named `gravity-gambit-release-aab-DEBUGKEY-DO-NOT-UPLOAD`. What is **not** proven is what happens once the four secrets exist: the base64 decode, the `GITHUB_ENV` handoff into Gradle, `--require-upload-key` passing on a properly signed build, and the un-suffixed artifact name. That branch cannot be tested without a keystore, so **the first signed run is itself a test** — watch it, and expect the audit line to read `signed by:` with your own DN rather than `DEBUG KEY`. Do not treat signing as working until you have seen that line.
- [ ] [H] **Add the four CI secrets when you generate the keystore** (`GG_KEYSTORE_BASE64`, `GG_KEYSTORE_PASSWORD`, `GG_KEY_ALIAS`, `GG_KEY_PASSWORD` — exact commands in LAUNCH_GUIDE Phase 3 step 5). Two minutes while the keystore is already open; skipping it means repeating the manual signing dance for every release.
- [ ] [H] Approve or restyle store graphics drafts in `store/` (regen via tools/store_assets.py).

## LATER (post-launch, ordered)
- [ ] [A] v1.1 Play Games Services v2: sign-in + Snapshots cloud save + daily leaderboard + achievements (facts: docs/reference/play-policy-snapshot.md §PGS; Data Safety update required).
- [ ] [A] AdMob rewarded integration IF retention data justifies (docs/ADS.md playbook; needs [H] AdMob account; UMP consent; Data Safety + privacy policy update; app-ads.txt).
- [ ] [A] Per-system ambience *variation over a match* — the pad is fixed for a system today. Candidates: the mix leaning toward a body as the shot passes near it, or the pad thinning during flight so impacts land in more space. Only worth doing if the fixed pad proves monotonous in playtest (docs/AUDIO.md §7), not before.
- [ ] [A] Localization pass (externalize inline strings → res; es/pt/de/fr/ja first). **Deliberately deferred until after first playtest**: copy is still volatile (feel/tuning feedback will rewrite wording), and externalizing early means every copy tweak edits XML instead of the composable. Do it once the wording settles — ideally right before the v1.0 store push. Note `:core` display names (projectile/trail names) are game terms; decide then whether they map to string resources or stay untranslated proper nouns.
- [ ] [H] Tablet/landscape check during playtest: does the side-bar HUD feel reachable one-handed in landscape, and is the board big enough on a tablet? (Implemented + tested at three size classes; only real hands can judge it.)
- [ ] [A] Baseline Profile + Macrobenchmark once a device lab exists (docs/PERFORMANCE.md).
- [ ] [A] In-app "what's new" + rate prompt (post-retention only, never nagging).
- [ ] [A] Consider first-use coaching for the duel-code flow (mirror of the aim hint added 0.2.0).

## V2 HORIZON
- [ ] Realtime P2P duels: WebRTC lockstep over existing deterministic engine (docs/MULTIPLAYER.md §Phase 2; needs INTERNET + tiny signaling svc).
- [ ] Orbital motion for bodies (deterministic rails pos(tick); ShareCodec VERSION bump — docs/TUNING.md §Versioning; MAJOR under SemVer post-1.0). **Assessed 2026-08-04 (D-22): no engine restructuring needed** — body positions are read in only two hot-loop sites, `Body` is already immutable, and motion is a pure function of the integer tick. Changes are additive; the real cost is the protocol break and a `RenderCache` rethink. Do not pre-refactor for it.
- [ ] Daily ghost replays (codes already carry everything needed).
- [ ] Weekly leagues on seed sets.

## PARKED / DECIDED-NO (see docs/DECISIONS.md)
- Interstitial/forced ads (D-12: never). Loot boxes (D-13). Accounts/own backend (D-2). Families program opt-in (PLAY_STORE_CHECKLIST).

---

## ARCHIVE (completed — grows forever)
### 2026-08-06
- [x] [A] v0.13.2 Launch-prep unit: release QA script rewritten from six 0.2.0-era lines into a risk-ordered checklist covering every mechanic since, with the D-29 fresh-profile duel invariant as an explicit gate; privacy-policy code contents completed; link audit attempted and honestly recorded as inconclusive (11 of 13 URLs unreachable from this sandbox, which is not evidence they are dead).
- [x] [A] v0.13.1 Owner direction captured as D-32 (split cadence · Expedition additive · difficulty stays demanding · presentation gets real investment); first presentation unit = impact juice (decaying camera shake + hit-stop), tested as provably presentation-only.
- [x] [A] v0.13.0 **Stage C complete**: Thread (gates in order). Gates derived from a real trajectory rather than scattered, then re-proved independently (D-31); order enforced with a test that fails a flight passing all three out of sequence; crossings folded into the hash. Protocol v8.
- [x] [A] v0.12.2 Audit pass 5 (store-facing): PLAY_STORE_CHECKLIST contradicted itself on permissions ("No permissions at all" three sections after correctly documenting them) and undercounted IAP products 3→6; STORE_LISTING still described the 0.2.0 game and quoted a duel-code length wrong at both ends; the "nothing you can buy wins a duel" line was false when written and is true now. Same numbers corrected in README, MULTIPLAYER, BillingManager and AI_LOOK_AVOIDANCE (whose own example of "be concrete" was the wrong figure). D-13 amended.
- [x] [A] v0.12.1 Season track made visible: screen with both-lane 50-tier ladder auto-scrolled to the player's tier, Home entry, XP on the match summary, `season_pass` wired as a per-season consumable (consume-then-grant), offer hidden when billing cannot serve it, rollover applied on display.
- [x] [A] v0.12.0 **Duels normalized** (D-29) after finding the game already sold PVP advantage — preview tier and Breaker damage both reached duels while the docs claimed fairness. Both players now get every shell and the top preview. **Season track** engine (D-30): 28 days, 50 tiers, XP from verified play, free + per-season paid lane, additive and retroactive, no tier-skip. Curve calibrated after the first attempt needed 69 days to finish a 28-day season.
- [x] [A] v0.11.0 Stage C second objective: **Courier** (strike the marked drifting comet). Measured first again — only 11 of 36 levels carry a comet and two of those cannot be hit, so `proveCourier` gates it. Generalised `captureBody` → `markedBody` and made objective assignment pick among all proved-payable objectives (D-28), so the next objective costs an enum entry rather than three new branches. Protocol v7.
- [x] [A] v0.10.1 Audit pass 4: DETERMINISM.md's hashed-content list was four protocol versions stale and described the removed frame-counted pacing as the contract; now enforced structurally by `everyTurnInputFieldChangesTheHash` (asserts TurnInput's exact field list). TESTING.md rewritten against the real tree (1 of 8 files listed) with the measured lessons as extension rules. Test file split three ways so names mean what they say.
- [x] [A] v0.10.0 Stage C first objective: **Capture**. Measured before building — an unbraked shot cannot hold a ring at all (hyperbolic arrival, no dissipation; best 191 ticks vs a 240 bar), so Capture became an orbital-insertion puzzle requiring the RETRO burn (339-818 ticks with it), which finally gives that shell a purpose. Campaign-only, never assigned without a generator proof. Protocol v6. D-26, D-27.
- [x] [A] v0.9.4 Audit pass 3: TUNING.md corrected against code — difficulty bands (two versions stale, and the same band I'd already fixed in DESIGN.md), the star rule, two missing style bonuses, scatter-vs-curated generation, missing comets/pulsars, the streak multiplier, hot-seat's zero payout; added the Gambits and Anchors sections that never existed. ARCHITECTURE.md file map verified complete.
- [x] [A] v0.9.3 CI runs once per change, not twice (push+pull_request were both firing on feature-branch pushes); artifacts now come from the PR run.
- [x] [H/A] **Repo now has a PR flow.** `main` created at the session branch's tip (agent) and set as default (owner). The repo had been born empty, so its first push became both default and only branch — a branch cannot PR into itself, so every change had been landing straight on the repo head with nothing gating it. Work continues on `claude/google-play-apk-game-139njt`; `main` moves only via draft PRs that CI has passed. D-20 rewritten.
- [x] [A] v0.9.2 Audit pass 2: share-code size claims were 5× understated (52 chars to open a duel, 1039 worst case, vs "~200 bytes" claimed); per-turn size stale at 8 bytes since v3/v5 added anchors and gambits. Pinned by `ShareCodeSizeTest` against a 1200-char budget. MONETIZATION.md/ADS.md checked and found accurate.
- [x] [A] v0.9.1 Interlock audit: checked DESIGN.md's three "modes affect each other" claims against the code; two were false. Daily streak now actually pays (was display-only); duel-payout claim corrected to "biggest repeatable" and pinned by a test; hot-seat's unreachable reward branch moved into `:core` and made honest; stale 0.7.0 difficulty band corrected.
- [x] [A] v0.9.0 Stage B Gambits: declare-before-firing constraints judged by the simulation; Blind cut as unverifiable (D-23); per-seat generator-proved menus after measurement found unwinnable offers (D-24); duel damage multiplier after finding declarations were pure downside in PVP (D-25); "stolen speed" threshold recalibrated from a 201-shot distribution; feasibility proving folded into the existing solver sweep at no cost; protocol v5.
- [x] [A] v0.8.0 aiming controls from owner feedback: floating joystick that appears at the touch point (was pinned to the station, i.e. to one screen corner), hand-rolled gesture so the ring centres on the true touch-down; press-and-hold auto-repeat with a growing step (×1/×3/×8) on the fine-tune buttons; TalkBack role/activation declared explicitly on the new raw pointer target; portrait reserve 188→196dp for 48dp touch targets.
- [x] [A] v0.7.0 curated generation: layouts built around the shot they force (keeper on the direct line + shepherds), `fallsToNaiveShot` rejection on every path including fallbacks, third star now requires working the field, difficulty bands recalibrated, protocol v4. 0/72 levels fall to a straight shot; generation ~20x faster.

### 2026-08-04
- [x] [A] v0.4.0 playfeel pass from owner feedback: comets (moving bodies) + pulsars, slingshot/speed scoring, aiming redesign (power meter by feel, labelled fine-tune), bespoke Home, copy trimmed ~700 chars, protocol v2.
- [x] [A] DuelFlowUiTest (hand-off overlay: code duel vs hot-seat vs handback) — last untested UI now covered.
- [x] [A] Daily streak rule moved to `:core` (D-7 compliance) + UTC-midnight rollover locked by tests.
- [x] [A] Docs de-drift pass: ARCHITECTURE (renderer/pacing/layout/new trees), TESTING, PERFORMANCE.
- [x] [A] D-22 assessment: orbital motion needs no engine restructuring; do not pre-refactor.
- [x] [A] Landscape/tablet layout: HUD moves into the letterbox bars on wide screens (65% of a landscape screen was empty); WideScreenTest at 3 size classes; D-17 updated.
- [x] [A] GameScreenTest + ShopScreenTest (30 app tests; every screen now rendered by a test). Documented the manual-clock/pump pattern needed for the continuously-animating match screen.
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
