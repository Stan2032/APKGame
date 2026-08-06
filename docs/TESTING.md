# TESTING.md — strategy, current coverage, extension rules

## Philosophy
Everything that can corrupt a wallet, a replay, or a save is in :core and unit-tested on JVM. The :app module adds Robolectric smoke tests so no screen ships having never been rendered, plus direct tests of MatchController (the phase machine + duel-code flow). On-device QA (RELEASE.md) remains the final gate — these tests prove code *runs*, not that it *feels* right.

## Run
- `GG_SKIP_ANDROID=1 ./gradlew :core:test` — full engine suite, no SDK needed, ~90s incl. generator/solver work.
- `./gradlew :app:testDebugUnitTest` — Robolectric screen smoke tests + MatchController tests (~15s, needs SDK).
- `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug` — the full local gate; run before every push.
- CI runs all of the above before building artifacts; failures upload test reports.

## :app suite (app/src/test)
- `DuelFlowUiTest`: drives a real shot through the composed screen and asserts the hand-off moment — code duels surface "Shot locked in" with Share/Copy; hot-seat asks for the phone and **never** offers a code; tapping through hands control to player two. This is where the headline feature happens, so a crash here must not hide behind green unit tests.
- `AppSmokeTest` (Robolectric, phone-sized viewport): Home renders every mode and routes correctly; Daily launch carries today's seed; Campaign gates locked content; Duel rejects garbage codes without navigating; Help and theme render. Note: use `assertExists()` for below-fold content — `assertIsDisplayed()` additionally requires viewport visibility and will fail on long screens.
- `GameScreenTest`: the match screen — the game itself, and the hardest thing here to test, because its ambient animation means the composition **never goes idle**, so the default auto-advancing clock hangs. Pattern: `mainClock.autoAdvance = false` plus an `awaitText` pump that advances the clock while polling, since system generation also runs on a background dispatcher. Covers campaign/daily HUDs, the first-launch coaching hint appearing only for fresh profiles, and the corrupt-code refusal screen. **Copy this pattern for any future test touching the match screen.**
- `ShopScreenTest`: proves the shop degrades gracefully with billing unconnected and ads disabled — earnable content renders, purchase and ad sections self-hide rather than showing stubs or dead buttons, Ad Pass holders see their state. This is what makes the app safe to ship before Console products exist.
- `AccessibilityTest`: renders real screens at 1.8x-2.0x font scale (via a `LocalDensity` override) because fixed-height controls clip their labels at large system font sizes and nothing else catches it; asserts campaign levels expose spoken labels rather than bare star glyphs.
- `MatchControllerTest`: campaign match drives to summary within its shot cap; **full code-duel round trip** (A fires → code emitted → B imports → verification passes → A's shot replays → seats/HP/hash agree on both devices); forged hash refused; hot-seat handoff alternates and never emits codes; aim nudges wrap/clamp; a feedback report's attached replay code verifies (guards the triage pipeline).

## Current suite (core/src/test — 78 tests across 8 files)
`CoreTests.kt` holds the original five classes; the rest have their own files.
- `RngTest` — sequence determinism, mix stability, double range.
- `DeterminismTest` — identical inputs ⇒ identical hash/ticks/hits; 0.1° delta ⇒ different hash; MatchReplayer reproduces live play (hash + HP); preset brake tick deterministic; **`everyTurnInputFieldChangesTheHash`** asserts TurnInput's exact field list and that every one of them moves the hash — the structural guard against an input escaping verification.
- `ShareCodecTest` — roundtrip; decode-inside-chat-text; single-char corruption rejected (CRC); garbage rejected; superseded protocol versions refused.
- `GeneratorTest` — d∈{0,2,4,6} solvable (rate>0), stations clear of bodies, in-bounds; generation value-deterministic; duel both-sides solvable.
- `EndToEndPvpTest` — THE load-bearing test: play duel → encode → decodeAndVerify passes ⇒ same HP; forged hash rejected.
- `DailyLogicTest` — relocation deterministic per hit-count, differs across counts.
- `EconomyTest` — Profile JSON roundtrip incl. totals; corrupt save ⇒ default; reward sanity/bounds; the streak actually pays; hot-seat pays nothing; code duels are the biggest repeatable earn.
- `MatchRulesTests.kt` — duel damage/winner edges, turn caps, star math, on hand-built specs.
- `MovingBodyTest.kt` — comets and pulsars stay bit-deterministic as pure functions of the tick.
- `PurposeTest.kt` — **no generated level falls to a near-straight shot**; the guard on 0.7.0's whole point.
- `GambitTest.kt` — declarations are decided from the flight, a broken one wastes the shot, an edited one fails verification, and every offered gambit is one the arena can pay.
- `ObjectiveTest.kt` — **no unbraked shot can hold the ring** (the measurement Capture is built on — if this ever fails, Capture has stopped requiring its burn), a braked one can, capture is won by dwell not by hitting anything, every capture system is provably winnable, and only the campaign asks for one.
- `ShareCodeSizeTest.kt` — an opening challenge stays short and the longest legal duel still fits a message (1200-char budget).
- `GeneratorSweepTest.kt` / `GenerationPerfTest.kt` — breadth across sectors/difficulties/dailies, and generation-cost guardrails.

## Extension rules (add tests WITH the change, same commit)
- New projectile type ⇒ DeterminismTest case (preset inputs incl. its special mechanic) + damage case in a MatchEngine test + codec roundtrip with its typeId.
- Any Tuning/sim change ⇒ suite must pass unchanged EXCEPT hash-value assumptions; confirm ShareCodec VERSION bumped (TUNING.md §Versioning) and add old-version-rejection test.
- Generator changes ⇒ keep GeneratorTest difficulty spread green; if bands move, update band test values in same commit with rationale in DECISIONS.md.
- New Profile field ⇒ roundtrip case + forward-compat case (decode JSON lacking the field).
- New share-code field ⇒ roundtrip + corruption + length assertions (`ShareCodeSizeTest` — the budget is a product constraint, not a nicety).
- **New `TurnInput` field ⇒ fold it into the hash and extend `everyTurnInputFieldChangesTheHash`.** That test asserts the exact field list, so it fails on any addition rather than letting an unhashed input ship. An input outside the hash is one a sender can edit undetected.
- **New Gambit ⇒ prove the arena can pay it.** Measure across real generated levels before choosing a threshold: the first "stolen speed" value was satisfied by ~9 winning shots in 10, and "two-body" had *zero* winning shots on 3 of 12 levels. Add the feasibility case to `GambitTest`.
- **New Objective ⇒ a generator proof, and a test that it holds** (D-27). Capture measured *first*, which is how it turned out that an unbraked shot cannot hold a ring at all. Never ship an objective the arena has not been shown to pay.
- **Any performance claim ⇒ measure A/B in the same session on the same machine** before attributing a change (PERFORMANCE.md); absolute millisecond figures are machine-relative and have varied 5× between sessions on unchanged code.
- Economy math (rewards, caps, pass multiplier) ⇒ pure functions in :core with direct cases; ProfileRepository stays a thin applier (not unit-tested; keep it logic-free).

## Manual QA (pre-release, on release APK)
Script lives in RELEASE.md §Release-build QA. Additions when ads/PGS land: reward grant exactly-once per view; cap resets at UTC midnight; Ad Pass purchase→×2 visible in summary; pass expiry deactivates (test card refund path).
