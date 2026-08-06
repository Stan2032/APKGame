# Google Play submission checklist (verified 2026-08-04; re-audited 2026-08-06)

Facts below were verified against Google's documentation on 2026-08-04;
snapshots with sources live in `docs/reference/`. Re-verify anything
marked ⏳ before acting if significant time has passed.

## Account & compliance gates (things only YOU can do)

- [ ] **Play Console developer account** — one-time $25, needs ID
      verification (can take days). https://play.google.com/console/signup
- [ ] **Closed-testing requirement**: personal accounts created after
      2023-11-13 must run a closed test with **≥12 testers opted-in for 14
      consecutive days** before applying for production. Plan this from day
      one: friends/family/colleagues; add their Gmail addresses as testers,
      make sure they *open and use* the app during the window. Dropping
      below 12 can reset the clock.
- [ ] **D-U-N-S is NOT needed** for personal accounts (organizations only).

## Technical requirements (already satisfied by this repo)

- [x] **Target API 36** — required for new apps from 2026-08-31
      (`targetSdk = 36`, `compileSdk = 36`).
- [x] **AAB format** — new apps must upload App Bundles, not APKs
      (`:app:bundleRelease` in CI). The APK artifacts are for sideload
      testing only.
- [x] **64-bit support** — pure Kotlin/JVM app, no native code: compliant
      by construction.
- [x] **Play Billing Library ≥ 8** — required for new apps from 2026-08-31
      (we ship 8.3.0).
- [x] **Permissions**: we declare none. The merged manifest carries BILLING
      plus INTERNET/ACCESS_NETWORK_STATE contributed by Play Billing's
      transitive datatransport component (audited with aapt2; allowlisted and
      guarded by `tools/verify_release_apk.py`). Our code performs no
      networking, so the Data-Safety answer is still "no data collected or
      shared" — but do not claim "no internet permission" anywhere, because a
      reviewer can see it in the manifest.

## Release preparation

- [ ] **Upload keystore** — generate once, back it up like a passport
      (see RELEASE.md). Enroll in Play App Signing at first upload.
- [ ] **Privacy policy URL** — required (billing is present). Host
      `docs/PRIVACY_POLICY.md` anywhere public (GitHub Pages is fine) and
      paste the URL in Play Console → App content.
- [ ] **Data Safety form** — declare: no data collected, no data shared.
      (True: no analytics, no ads, no network. Purchases are processed by
      Google Play, which is exempt as the payments processor.)
- [ ] **Content rating questionnaire** — arcade game, no violence against
      realistic humans, no gambling: expect PEGI 3 / Everyone.
- [ ] **Ads declaration** — "No, my app does not contain ads."
- [ ] **In-app products** — create **six** products from MONETIZATION.md
      *after* the first AAB upload (Console requires a build with billing
      before it lets you add products):
      - 4 one-time: `stardust_small`, `stardust_large`, `cosmic_pack`,
        `season_pass` (the pass is a **consumable**, because it is bought
        once per 28-day season rather than owned forever).
      - 2 subscriptions: `ad_pass_weekly`, `ad_pass_monthly`.
      The count was stale here at "three" through two product additions —
      cross-check against `Economy.Iap.ALL_PRODUCT_IDS` + `SUBSCRIPTION_IDS`
      rather than this list if they ever disagree.
- [ ] **Store listing**:
  - Title ≤ 30 chars: `Gravity Gambit`
  - Short description ≤ 80 chars, e.g. *"Slingshot artillery in real
    gravity. Duel friends with codes — no accounts."*
  - Full description: lead with physics-honest PVP + offline privacy.
  - Graphics: 512×512 icon (export from the adaptive icon), 1024×500
    feature graphic, ≥4 phone screenshots (grab from a device/emulator;
    the game renders its own good-looking screenshots by design).
- [ ] **App category**: Game → Arcade. Tags: physics, PvP, offline.

## Review-rejection traps to avoid (why this app dodges them)

| Common rejection | Our posture |
|---|---|
| Broken/placeholder functionality | Every advertised mode works offline; billing UI hides itself when unconfigured. |
| Data-Safety form contradicts traffic | Our code generates no traffic; billing's library is Google's own and is declared as such. |
| Background location/SMS/etc. permission abuse | We declare none ourselves; the merged manifest carries only BILLING plus INTERNET/ACCESS_NETWORK_STATE from Play Billing's own transitive component, allowlisted and CI-guarded. **Do not write "no permissions at all"** — this row used to say exactly that, contradicting the Technical Requirements section above it and repeating the claim D-3 exists to prevent. |
| Payments outside Play Billing | Only Play Billing. Purchases cover cosmetics, stardust and the season pass; **nothing purchasable can affect PVP** — duels issue every shell and the top aim trace to both players (D-29). |
| "Designed for families" mismatches | Do NOT opt into the Families program — just answer the target-audience questionnaire honestly (13+ recommended to keep it simple). |
| Crash on reviewer's device | minSdk 26, no exotic APIs, no native libs; test the release AAB via internal testing first. |

## Suggested rollout sequence

1. Internal testing (just you) → sanity-check the signed build.
2. Closed testing (your 12+ testers, 14 days) — iterate freely here.
3. Apply for production access → production rollout at 20% → 100%.
