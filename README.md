# Gravity Gambit

**Slingshot artillery in living star systems.** A deterministic N-body
physics duel game for Android: curve shots through real gravity, master
procedurally generated systems that are *machine-proven solvable*, and duel
friends over any messenger with cryptographically verifiable challenge
codes — no servers, no accounts, no internet permission.

| | |
|---|---|
| Platform | Android 8.0+ (minSdk 26), target/compile SDK 36 |
| Stack | Kotlin 2.1, Jetpack Compose, Play Billing 8, pure-JVM game core |
| Multiplayer | Hot-seat + asynchronous PVP via ~230-char verified replay codes |
| Monetization | Cosmetic-only IAP (Play Billing), everything earnable in-game |
| Privacy | No data collection, no network access, no analytics |

## Modules

- **`:core`** — the entire game logic as a pure Kotlin/JVM library:
  deterministic simulation, procedural generator with built-in solvability
  solver, replay codec + verification, match rules, economy. Fully
  unit-tested (`./gradlew :core:test`), no Android dependency.
- **`:app`** — Compose UI, rendering, input, DataStore persistence,
  billing. Thin by design: every rule lives in `:core` so live play and
  replay verification can never diverge.

## Build

```bash
./gradlew :core:test            # engine tests (works without Android SDK)
./gradlew :app:assembleDebug    # debug APK
./gradlew :app:bundleRelease    # Play-ready AAB
```

CI (GitHub Actions) runs all of the above on every push and uploads the
APK/AAB artifacts. Engine-only environments: `GG_SKIP_ANDROID=1 ./gradlew :core:test`.

## Documentation

Start at the **[docs index](docs/README.md)**. Living state: [TODO.md](TODO.md) ·
[CHANGELOG.md](CHANGELOG.md) · [AGENTS.md](AGENTS.md) (instructions for AI
sessions working on this repo). Highlights: [design rationale](docs/DESIGN.md),
[determinism contract](docs/DETERMINISM.md), [serverless PVP protocol](docs/MULTIPLAYER.md),
[tuning guide](docs/TUNING.md), [Play submission checklist](docs/PLAY_STORE_CHECKLIST.md).
