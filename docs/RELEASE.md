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

- [ ] `./gradlew :core:test` green.
- [ ] Install the **release** APK (not debug) on a real device.
- [ ] Full loop: campaign level → daily → hot-seat duel → code duel
      round-trip between two devices (or device + second profile).
- [ ] Paste a corrupted code → clean "failed verification" message.
- [ ] Kill the app mid-menu → progress (stars, stardust) survived.
- [ ] Airplane mode: everything above still works.
