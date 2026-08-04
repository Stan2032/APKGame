# TESTING.md — strategy, current coverage, extension rules

## Philosophy
Everything that can corrupt a wallet, a replay, or a save is in :core and unit-tested on JVM. The :app module adds Robolectric smoke tests so no screen ships having never been rendered, plus direct tests of MatchController (the phase machine + duel-code flow). On-device QA (RELEASE.md) remains the final gate — these tests prove code *runs*, not that it *feels* right.

## Run
- `GG_SKIP_ANDROID=1 ./gradlew :core:test` — full engine suite, no SDK needed, ~90s incl. generator/solver work.
- `./gradlew :app:testDebugUnitTest` — Robolectric screen smoke tests + MatchController tests (~15s, needs SDK).
- `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug` — the full local gate; run before every push.
- CI runs all of the above before building artifacts; failures upload test reports.

## :app suite (app/src/test)
- `AppSmokeTest` (Robolectric, phone-sized viewport): Home renders every mode and routes correctly; Daily launch carries today's seed; Campaign gates locked content; Duel rejects garbage codes without navigating; Help and theme render. Note: use `assertExists()` for below-fold content — `assertIsDisplayed()` additionally requires viewport visibility and will fail on long screens.
- `AccessibilityTest`: renders real screens at 1.8x-2.0x font scale (via a `LocalDensity` override) because fixed-height controls clip their labels at large system font sizes and nothing else catches it; asserts campaign levels expose spoken labels rather than bare star glyphs.
- `MatchControllerTest`: campaign match drives to summary within its shot cap; **full code-duel round trip** (A fires → code emitted → B imports → verification passes → A's shot replays → seats/HP/hash agree on both devices); forged hash refused; hot-seat handoff alternates and never emits codes; aim nudges wrap/clamp; a feedback report's attached replay code verifies (guards the triage pipeline).

## Current suite (core/src/test — CoreTests.kt)
- `RngTest` — sequence determinism, mix stability, double range.
- `DeterminismTest` — identical inputs ⇒ identical hash/ticks/hits; 0.1° delta ⇒ different hash; MatchReplayer reproduces live play (hash + HP); preset brake tick deterministic.
- `ShareCodecTest` — roundtrip; decode-inside-chat-text; single-char corruption rejected (CRC); garbage rejected.
- `GeneratorTest` — d∈{0,2,4,6} solvable (rate>0), stations clear of bodies, in-bounds; generation value-deterministic; duel both-sides solvable.
- `EndToEndPvpTest` — THE load-bearing test: play duel → encode → decodeAndVerify passes ⇒ same HP; forged hash rejected. Code length <400.
- `DailyLogicTest` — relocation deterministic per hit-count, differs across counts.
- `EconomyTest` — Profile JSON roundtrip incl. totals; corrupt save ⇒ default; reward sanity/bounds.

## Extension rules (add tests WITH the change, same commit)
- New projectile type ⇒ DeterminismTest case (preset inputs incl. its special mechanic) + damage case in a MatchEngine test + codec roundtrip with its typeId.
- Any Tuning/sim change ⇒ suite must pass unchanged EXCEPT hash-value assumptions; confirm ShareCodec VERSION bumped (TUNING.md §Versioning) and add old-version-rejection test.
- Generator changes ⇒ keep GeneratorTest difficulty spread green; if bands move, update band test values in same commit with rationale in DECISIONS.md.
- New Profile field ⇒ roundtrip case + forward-compat case (decode JSON lacking the field).
- New share-code field ⇒ roundtrip + corruption + length assertions.
- Economy math (rewards, caps, pass multiplier) ⇒ pure functions in :core with direct cases; ProfileRepository stays a thin applier (not unit-tested; keep it logic-free).

## Manual QA (pre-release, on release APK)
Script lives in RELEASE.md §Release-build QA. Additions when ads/PGS land: reward grant exactly-once per view; cap resets at UTC midnight; Ad Pass purchase→×2 visible in summary; pass expiry deactivates (test card refund path).
