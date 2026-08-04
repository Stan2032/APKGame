# AGENTS.md — machine-first instructions for AI/automation sessions

Audience: future Claude/agent sessions. Terse by design. Humans: see README.md.

## Repo map
- `:core` pure Kotlin/JVM — ALL game logic (sim, procgen+solver, replay codec, rules, economy). No Android imports, ever.
- `:app` Android/Compose — UI, persistence (DataStore), billing, ads abstraction. Thin; contains zero game rules.
- `docs/` reference docs (index: docs/README.md). `docs/reference/` = dated snapshots of external facts (Google policy, toolchain); re-verify entries >6mo old before relying on them.
- Living state: TODO.md (work queue), CHANGELOG.md (history), docs/DECISIONS.md (ADRs — check before re-litigating any choice).

## Build/verify protocol
- Core tests (works everywhere, incl. offline-ish envs): `GG_SKIP_ANDROID=1 ./gradlew :core:test`
- Full local build (preferred loop): `./gradlew :core:test :app:assembleDebug :app:bundleRelease :app:lintDebug`. Needs Android SDK at /opt/android-sdk (`local.properties` sdk.dir, gitignored). If missing, install: cmdline-tools zip from dl.google.com → `sdkmanager --licenses` → `"platforms;android-36" "build-tools;36.0.0"`. `dl.google.com` was egress-blocked until 2026-08-04; the owner allowlisted it for this environment. If a future session finds it blocked again, fall back to the CI loop below and keep `GG_SKIP_ANDROID=1` for core work.
- CI remains the artifact source of truth: push → GitHub Actions (`.github/workflows/android.yml`) → verify conclusion=success + artifacts (debug APK, release APK, release AAB) via GitHub MCP tools. Never claim green without seeing it.
- If waiting on CI, arm a check-in (send_later ~20min); local-first building makes this rare.
- Local gradle: preinstalled at /opt/gradle (8.14.3); wrapper committed. Lint is part of the verify loop — `:app:lintDebug` must be error-free.
- Store graphics regenerate via `python3 tools/store_assets.py` (deterministic; Pillow) → `store/`.

## Invariants — breaking any of these breaks shipped multiplayer
1. Determinism (docs/DETERMINISM.md): core sim uses ONLY double +-*/ , Math.sqrt, StrictMath transcendentals, own Rng (SplitMix64). NEVER Math.sin/cos, never platform RNG, never HashMap iteration over sim state, never float from UI entering sim un-quantized.
2. Rng constants and Rng.mix are frozen — changing them invalidates every seed in the wild.
3. Any change to physics/sim constants (Tuning), generator logic, rules in MatchEngine, or hash folding = old share codes verify differently → MUST bump ShareCodec VERSION and handle old-version decode (reject with message). See docs/TUNING.md.
4. :core stays Android-free (JVM-testable). Rules live ONLY in MatchEngine/core (UI must never duplicate rule math).
5. App must remain fully functional with billing unavailable and ads NoOp. No INTERNET permission in v1 — adding any network dep is a documented decision (docs/DECISIONS.md D-3), not a drive-by.
6. Play compliance floor: targetSdk 36, Billing 8+, AAB for store. Sources snapshotted in docs/reference/play-policy-snapshot.md.

## Standing priorities (owner directives)
- **Performance, always**: docs/PERFORMANCE.md rules bind every change touching rendering/sim/UI hot paths (precompute-per-spec, draw-phase-only state reads, zero per-frame allocation, time-based pacing, bounded draw calls). Audit before merge.
- **Words point at reader-world referents**: docs/AI_LOOK_AVOIDANCE.md pointing test on all player-facing copy.

## Conventions
- Docs: SCREAMING_SNAKE.md in docs/; update docs/README.md index + CHANGELOG.md + TODO.md with every meaningful change.
- Commits: imperative summary + body explaining why; end with the session's Co-Authored-By/Claude-Session footer; never put model IDs in committed artifacts.
- Branch: work stays on `claude/google-play-apk-game-139njt` (repo default; born from empty repo — no PR base exists).
- Kotlin style: match existing; comments only for non-obvious constraints.
- UI text voice: see docs/AI_LOOK_AVOIDANCE.md before writing ANY player-facing copy.

## Known env quirks (Claude remote container)
- Egress proxy: see /root/.ccr/README.md; do not disable TLS/proxy. Blocked-host 403s are policy, not transient — don't retry.
- `GG_SKIP_ANDROID=1` gates `include(":app")` in settings.gradle.kts (exists precisely for the blocked-Google-Maven case).
- GitHub access scoped to Stan2032/APKGame only.
