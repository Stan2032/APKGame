# Google Play policy & platform facts — snapshot

**Retrieved: 2026-08-04.** These are distilled snapshots of the official
documentation, kept in-repo so build decisions can be checked without
re-searching. Each entry cites its source; re-verify entries older than
~6 months before relying on them for a submission.

## Target API level (source: developer.android.com/google/play/requirements/target-sdk)

- From **2026-08-31**: new apps and updates must target **Android 16
  (API 36)** or higher. (Wear OS/Automotive: 35; TV/XR: 34.)
- Extension to 2026-11-01 requestable via Play Console later in the year.
- **This repo**: `targetSdk = 36`, `compileSdk = 36` ✅

## Play Billing Library (source: developer.android.com/google/play/billing/deprecation-faq)

- From **2026-08-31**: new apps and updates must use **Billing Library 8+**.
  Version 7's deadline passed 2026-08-31; v8's own deadline is 2027-08-31
  (then v9 by 2028-08-31 — two-year cycle).
- Latest at snapshot time: **8.3.0** (release-notes page).
- v8 API change that matters here: `queryProductDetailsAsync` returns a
  `QueryProductDetailsResult` (with `productDetailsList` +
  `unfetchedProductList`) instead of a plain list.
- **This repo**: billing-ktx **8.3.0** ✅

## App Bundle requirement

- New apps must publish with **Android App Bundles (.aab)**; APKs are for
  local testing only. **This repo**: CI builds `bundleRelease` ✅

## Closed testing for new personal accounts (sources: Play Console Help 14151465; community guide 255621488)

- Personal developer accounts created after **2023-11-13** must run a
  closed test with **≥12 testers, opted in for 14 consecutive days**,
  before applying for production access. (Was 20 testers; reduced to 12 on
  2024-12-11.) Testers must actually install/use the app; dropping below
  12 can reset the window. Organization accounts are exempt.

## AGP / Gradle / SDK toolchain (source: developer.android.com/build/releases/about-agp)

- **AGP 8.11** requires **Gradle ≥ 8.13**, supports **compileSdk 36**
  (Studio Narwhal 2025.1.1 generation).
- Newer majors exist (AGP 9.x, requiring Gradle 9.x) — upgrade is optional,
  not required for API 36. This repo pins AGP **8.11.0** + Gradle **8.14.3**
  + JDK 17 bytecode ✅
- Compose BOM used: **2025.06.01** (verified existing on the official BOM
  mapping page at snapshot time).

## Developer account cost

- Personal Play Console registration: **$25 one-time**; identity
  verification required and can take days. No D-U-N-S for personal accounts.

## Play Games Services v2 (retrieved 2026-08-04; sources: developer.android.com/games/pgs/overview, /games/pgs/signin, /games/pgs/android/saved-games, /games/pgs/data-collection)

- PGS v2 is Android's "Game Center": **automatic sign-in** built into the
  device (no login screen); the developer receives only an **opaque Player ID
  + gamertag** — never email or real name. Identity infrastructure is
  entirely Google's.
- **Saved Games (Snapshots API)**: cloud saves stored in the *player's own
  Google Drive* app-data folder (`drive.appdata` scope). No developer server
  involved; we never see or store the data.
- Also provides **achievements and leaderboards** (a global daily-challenge
  leaderboard with zero servers). Note: PGS leaderboard submissions are
  client-trusted — spoofable by determined cheaters; our replay-code
  verification remains the strong anti-cheat, PGS boards are casual-tier.
- Requirements: Play Console → Play Games Services setup (OAuth
  credentials), `play-services-games-v2` SDK, INTERNET permission.
- **Data Safety impact** (per Google's own PGS data-disclosure doc): the SDK
  collects gamer identity (user IDs) and analytics/diagnostics — the form can
  no longer say "no data collected"; declarations become "User IDs —
  collected, not shared, for app functionality". The developer remains
  responsible for the form even though Google operates the infrastructure.

## Data safety / permissions posture (design fact, not policy quote)

- This app requests no runtime permissions and has **no INTERNET
  permission**; the only merged permission is `com.android.vending.BILLING`
  (added by the billing library). Data-Safety form: "no data collected or
  shared" — purchases are processed by Google Play itself.
