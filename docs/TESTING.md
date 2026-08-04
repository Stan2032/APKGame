# TESTING.md — strategy, current coverage, extension rules

## Philosophy
Everything that can corrupt a wallet, a replay, or a save is in :core and unit-tested on JVM. UI is thin enough to verify by build + scripted manual pass (RELEASE.md QA). No emulator tests in v1 (cost/benefit; revisit if UI regressions recur).

## Run
- `GG_SKIP_ANDROID=1 ./gradlew :core:test` — full engine suite, no SDK needed, ~90s incl. generator/solver work.
- CI runs the same suite before every APK/AAB build; failure uploads test-report artifacts.

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
