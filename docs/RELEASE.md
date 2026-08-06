# Building, signing, releasing

## Local build

Requirements: JDK 17+, Android SDK with platform 36 + build-tools 36.0.0
(Android Studio installs these automatically; CLI: `sdkmanager
"platforms;android-36" "build-tools;36.0.0"`).

```bash
./gradlew :core:test            # engine + PVP verification tests (no SDK needed)
./gradlew :app:assembleDebug    # installable debug APK
./gradlew :app:bundleRelease    # Play-uploadable AAB
```

Engine-only environments (no Android SDK / no access to Google Maven) can
still run the full test suite with `GG_SKIP_ANDROID=1 ./gradlew :core:test`.

## CI

`.github/workflows/android.yml` runs the tests and uploads three artifacts
per push: debug APK, release APK (sideload testing), release AAB (Play
upload). Grab them from the Actions run page — since 0.9.3 that means the
run attached to the **open pull request** (or a push to `main`), not a run on
the feature branch: CI follows the PR flow rather than firing on every branch
push, which was building everything twice (D-20).

## Signing

Debug/unconfigured builds sign with the debug key (fine for testing,
rejected by Play). For release:

1. Generate an upload keystore **once** and back it up somewhere that
   survives a lost laptop (password manager + offline copy):

   ```bash
   keytool -genkeypair -v -keystore upload-keystore.jks \
     -keyalg RSA -keysize 2048 -validity 10000 -alias upload \
     -dname "CN=Gravity Gambit"
   ```

2. Provide it via environment (locally or as CI secrets):

   ```bash
   export GG_KEYSTORE_FILE=/absolute/path/upload-keystore.jks
   export GG_KEYSTORE_PASSWORD=...
   export GG_KEY_ALIAS=upload
   export GG_KEY_PASSWORD=...
   ./gradlew :app:bundleRelease
   ```

   In GitHub Actions: store the keystore as a base64 secret, decode it in a
   step, then export the same four variables. (Deliberately not wired into
   the public workflow until secrets exist.)

3. First upload to Play Console → accept **Play App Signing** (Google holds
   the app signing key; your keystore is the *upload* key — if it leaks or
   is lost, it can be reset via Console support, which is exactly why Play
   App Signing is the right default).

## Version bumps — SemVer 2.0.0 (https://semver.org/spec/v2.0.0.html)

`versionName` follows MAJOR.MINOR.PATCH strictly:
- **PATCH** — bug fixes only, no behavior additions.
- **MINOR** — new features, backwards compatible (new modes, cosmetics, SFX).
- **MAJOR** — incompatible changes: replay-protocol bumps (`ShareCodec`
  VERSION — see TUNING.md §Versioning) or save-format breaks. Pre-1.0.0 these
  may ride a MINOR (SemVer §4: initial development), but must be called out
  in CHANGELOG.md; from 1.0.0 they are MAJOR, no exceptions.

`versionCode` is Play's monotonic integer, decoupled from SemVer: +1 on every
uploaded build, never reused, never decreased. Bump both in
`app/build.gradle.kts`; record the version in CHANGELOG.md with its date.

## Release-build QA checklist

Run this on the **release** APK, never debug. R8 and resource shrinking only
apply there, and every historical risk in this app lives in what minification
does to reflection-shaped code (serialization, billing) — a debug pass proves
nothing about the artifact you upload.

*Rewritten 2026-08-06: the previous version was six lines written at 0.2.0 and
tested a fraction of the app. It predated anchors, gambits, all three
objectives, the season track and duel normalization — so a green run of it
told you almost nothing.*

### 0. Before touching the device
- [ ] `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug` green.
- [ ] `python3 tools/verify_release_apk.py` passes (permission allowlist,
      serializer survival under R8, size budget).

### 1. The minification danger zone (do these first — they are why this list exists)
- [ ] **Save/load**: earn stars and stardust, force-stop the app, reopen →
      everything survived. Serialization is `@Serializable` + reflection-shaped
      and is exactly what R8 breaks.
- [ ] **Corrupted code**: paste a mangled challenge code → clean "the physics
      noticed" screen, no crash.
- [ ] **Purchase restore**: if any product has been bought, reinstall and
      confirm entitlements come back on first billing connect.
- [ ] **Season pass is a consumable**: after buying it, the pass is owned for
      *this* season; confirm it does not silently persist across a season
      rollover (change the device date to test), and that the product becomes
      purchasable again.

### 2. Every mode reaches its end state
- [ ] Campaign level → summary with stars.
- [ ] Daily → summary with score; streak increments on a second day.
- [ ] Hot-seat duel → handoff overlay alternates, **pays no stardust and no
      season XP** (self-farm guard).
- [ ] Code duel round-trip between two devices (or a second profile): code
      out, code in, verification passes, the opponent's shot replays, both
      sides agree on HP.

### 3. The mechanics added since 0.2.0
- [ ] **Anchors**: place, flip attract↔repel, remove; the aim preview visibly
      bends; anchors are spent by the shot.
- [ ] **Gambits**: only declarations the level can pay are offered; a kept one
      shows its payout; a **broken one wastes the shot** — the hit does not
      land, and the summary says so.
- [ ] **Capture**: a RETRO shell is issued regardless of loadout, the burn
      button is reachable, the ring fills, four seconds wins.
- [ ] **Courier**: the marked comet wears its reticle; striking a *different*
      body does not win.
- [ ] **Thread**: the next gate is lit, passed gates stay green, the HUD counts
      "Gate N of 3".
- [ ] **Season**: the ladder scrolls to your tier, collecting pays out, and
      the match summary shows `+N season XP`.

### 4. The invariant that must never regress (D-29)
- [ ] On a **fresh profile with nothing unlocked**, start a duel: all four
      shells are selectable and the aim trace is at its longest. If either is
      restricted, PVP fairness is broken and the store copy has become false.

### 5. Presentation and accessibility
- [ ] Impact: the board shakes on a kill, the HUD does not, and the shake
      stops (no lingering wobble).
- [ ] TalkBack: the aim buttons, the anchor bar and the gambit rows are all
      announced and activatable — several are raw pointer targets whose
      semantics are declared by hand.
- [ ] System font at 2x: nothing clips, the controls wrap rather than
      truncate.
- [ ] Landscape/tablet: the HUD sits in the letterbox bars, the board is not
      covered.
- [ ] **Ambience** (docs/AUDIO.md — none of this can be checked off a device):
      - the pad fades in rather than snapping on, and **menus are silent**;
      - it does **not** click, tick or thump every seven seconds. The seam is
        proved continuous in the samples, so a tick here means playback, not
        synthesis — suspect the `resyncPulsarPhase` seek or the fade;
      - it stops when you leave the app and comes back when you return;
      - two different systems audibly differ, and the same system sounds the
        same on a second visit;
      - on a pulsar system, the swell is legible as a *pulse* and not as
        wobble — this is the sonification claim, and it is the one worth
        being sceptical about;
      - **Settings → System ambience off** silences the pad while sound
        effects keep firing, and **Sound effects off** leaves the pad running.
        They are separate switches and each must work alone.

### 6. Environment
- [ ] Airplane mode: everything in sections 2–5 still works. The game is
      offline-complete and this is a load-bearing product claim.
- [ ] Billing unavailable (no Play services / products not yet configured):
      the shop and the season pass offer **hide themselves** rather than
      showing dead buttons.
