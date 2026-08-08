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

   **In GitHub Actions this is already wired** (`.github/workflows/android.yml`)
   and switches itself on the moment the secrets exist — there is no flag to
   remember and no workflow edit to make. Add these four **repository
   secrets** (Settings → Secrets and variables → Actions):

   | Secret | Value |
   |---|---|
   | `GG_KEYSTORE_BASE64` | `base64 -w0 upload-keystore.jks` (one line, no newlines) |
   | `GG_KEYSTORE_PASSWORD` | the store password |
   | `GG_KEY_ALIAS` | `upload` |
   | `GG_KEY_PASSWORD` | the key password |

   On macOS use `base64 -i upload-keystore.jks | tr -d '\n'` — there is no
   `-w0` there, and a keystore with embedded newlines decodes to garbage.

   What the workflow does with them: decodes the keystore into `RUNNER_TEMP`
   (outside the workspace, so it can never be swept into an artifact upload),
   exports the same four variables the local build reads, shreds the file in
   a step that runs even when the build fails, and passes
   `--require-upload-key` to the APK audit so a signed repo can never hand
   back a debug-key artifact that merely *looks* upload-ready.

   **Until those secrets exist, CI still builds everything** — it just signs
   with the debug key, and names the bundle artifact
   `gravity-gambit-release-aab-DEBUGKEY-DO-NOT-UPLOAD`. That name is the
   point: a debug-signed bundle is indistinguishable from a real one in a
   downloads folder, and finding out from a Play Console rejection costs an
   hour spent doubting the build rather than the key.

   To check any local build: `python3 tools/verify_release_apk.py` prints
   `signed by:` on every run.

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

*Extended 2026-08-07 (0.19.11), and the reason is uncomfortable: **the
rewrite above went stale in exactly the way it criticised.** Written at
0.13.2, it predated Expedition, outfitting, salvage, debris, Blind practice
and reduced-motion support — a green run tested none of them, and nothing
anywhere said so. A checklist omitting a feature fails silently by
construction, which is why `ReleaseChecklistTest` now derives the required
entries from the code (mutators, fittings, body kinds) rather than trusting
this list to keep up.*

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
- [ ] **Expedition run reaches an end state**: launch a run, take legs at a
      junction, and keep going until the tank runs dry. The run summary
      appears and pays **salvage**. A run must end by *fuel*, not by hitting
      the leg cap — if you reach leg 40 with fuel to spare, `travelCost` has
      stopped clocking the run and the termination proof (D-34) has a hole
      the tests did not catch.

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

### 3b. The mechanics added since 0.13.0 (this section is why the list was extended)
- [ ] **Expedition junction**: three routes offered, each stating its cost
      **before** it is taken; an Uncharted route always names its condition.
- [ ] **Run mutators**, each doing what its card says:
      - *Dark run* — **no aim trace at all**. Not a shorter one: if you can
        still see a trace, this is the 0.19.0 bug returning (it used to hand
        back the base 90-tick preview while promising none).
      - *Thin reserves* — each shot costs 1 more fuel.
      - *Rich seam* — double fuel for clearing the system.
- [ ] **Outfitting bay**: appears only after a first run has finished; buying
      a **reserve tank** visibly raises the fuel the launch button promises;
      **salvage rig** softens a wreck; **survey array** shows the next
      junction. Salvage is spent, never stardust.
- [ ] **Debris**: miss into a world and a wreck stays there for the rest of
      the match, on the side the shot came from. It must **block without
      pulling** — a shot passing near one is not bent — and no wreck forms on
      a station or on another wreck. The aim trace must fly *around* wrecks;
      a preview that ignores them is drawing a board the shot will not meet.
- [ ] **Blind practice** (Settings): switch on → no aim trace anywhere,
      **including duels**, and the screen says it pays nothing. Switch off →
      the trace returns.
- [ ] **Duel first-use card**: on a profile that has never done a code duel,
      the Duel screen explains the exchange; it disappears once you have one.

### 4. The invariant that must never regress (D-29)
- [ ] On a **fresh profile with nothing unlocked**, start a duel: all four
      shells are selectable and the aim trace is at its longest. If either is
      restricted, PVP fairness is broken and the store copy has become false.

### 5. Presentation and accessibility
- [ ] Impact: the board shakes on a kill, the HUD does not, and the shake
      stops (no lingering wobble).
- [ ] **Reduced motion** (D-35 — Developer options → Animator duration scale →
      **Off**): the board must **not** shake, and the summary stars must
      appear **instantly and complete** rather than not at all. Information
      never hides just because decoration is off. Everything else behaves
      normally. This has never been checked on a device.
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
