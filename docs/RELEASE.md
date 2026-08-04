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
upload). Grab them from the Actions run page.

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

## Version bumps

`app/build.gradle.kts`: increment `versionCode` (integer, every upload) and
set a human `versionName`. Suggested scheme: `0.1.0` → `0.1.1` for fixes,
`0.x+1.0` for feature releases.

## Release-build QA checklist

- [ ] `./gradlew :core:test` green.
- [ ] Install the **release** APK (not debug) on a real device.
- [ ] Full loop: campaign level → daily → hot-seat duel → code duel
      round-trip between two devices (or device + second profile).
- [ ] Paste a corrupted code → clean "failed verification" message.
- [ ] Kill the app mid-menu → progress (stars, stardust) survived.
- [ ] Airplane mode: everything above still works.
