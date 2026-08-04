# AGENTS.md — machine-first instructions for AI/automation sessions

Audience: future Claude/agent sessions. Terse by design. Humans: see README.md.

## Repo map
- `:core` pure Kotlin/JVM — ALL game logic (sim, procgen+solver, replay codec, rules, economy). No Android imports, ever.
- `:app` Android/Compose — UI, persistence (DataStore), billing, ads abstraction. Thin; contains zero game rules.
- `docs/` reference docs (index: docs/README.md). `docs/reference/` = dated snapshots of external facts (Google policy, toolchain); re-verify entries >6mo old before relying on them.
- Living state: TODO.md (work queue), CHANGELOG.md (history), docs/DECISIONS.md (ADRs — check before re-litigating any choice).

## Build/verify protocol
- Core tests (works everywhere, incl. offline-ish envs): `GG_SKIP_ANDROID=1 ./gradlew :core:test`
- Full app build needs Android SDK 36 + Google Maven. In Claude remote sessions `dl.google.com` is BLOCKED by egress policy (curl/java both 403; maven.google.com 301s into the same blocked host) → :app CANNOT compile locally there. Verification path = push → GitHub Actions (`.github/workflows/android.yml`) → check run + artifacts via GitHub MCP tools → fix → repeat. Artifacts per green run: debug APK, release APK, release AAB.
- After pushing, arm a check-in (send_later ~20min) to inspect the run; iterate to green. Never claim green without seeing conclusion=success.
- Local gradle: preinstalled at /opt/gradle (8.14.3); wrapper committed.

## Invariants — breaking any of these breaks shipped multiplayer
1. Determinism (docs/DETERMINISM.md): core sim uses ONLY double +-*/ , Math.sqrt, StrictMath transcendentals, own Rng (SplitMix64). NEVER Math.sin/cos, never platform RNG, never HashMap iteration over sim state, never float from UI entering sim un-quantized.
2. Rng constants and Rng.mix are frozen — changing them invalidates every seed in the wild.
3. Any change to physics/sim constants (Tuning), generator logic, rules in MatchEngine, or hash folding = old share codes verify differently → MUST bump ShareCodec VERSION and handle old-version decode (reject with message). See docs/TUNING.md.
4. :core stays Android-free (JVM-testable). Rules live ONLY in MatchEngine/core (UI must never duplicate rule math).
5. App must remain fully functional with billing unavailable and ads NoOp. No INTERNET permission in v1 — adding any network dep is a documented decision (docs/DECISIONS.md D-3), not a drive-by.
6. Play compliance floor: targetSdk 36, Billing 8+, AAB for store. Sources snapshotted in docs/reference/play-policy-snapshot.md.

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
