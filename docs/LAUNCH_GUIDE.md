# LAUNCH_GUIDE.md — from this repo to money in your bank account

Every step, in order, with links. Facts verified 2026-08-04 against Google's
own documentation (sources at the bottom; policy snapshot in
`reference/play-policy-snapshot.md`). ⏱ = rough time. 🔒 = blocks later steps.

**The critical path is testers, not code.** Steps 1, 2 and 6 have multi-day
waits baked in. Start them today even if you touch nothing else.

---

## Phase 0 — Before you touch Play Console (⏱ 30 min)

You need, in your own name:
- [ ] A Google account you will still control in ten years (not a work one).
      Publishing is permanently tied to it.
- [ ] **$25 USD**, one-time, non-refundable, on a card.
- [ ] **Government photo ID** (passport/driving licence) for identity verification.
- [ ] **A bank account in your own country** — Google pays out to it, and it must
      match the country you register with. This cannot be changed later without pain.
- [ ] Your **legal name and a real physical address** (no PO boxes — Google rejects them).

> Decide now: register as **Personal** (fast, no D-U-N-S needed) or
> **Organization** (needs a D-U-N-S number, takes weeks, but **skips the
> 12-tester requirement entirely**). For a solo first game, Personal is right —
> just accept the testing phase. Organization only pays off if you already have
> a registered company.

---

## Phase 1 🔒 — Create the developer account (⏱ 30 min + up to several days waiting)

1. Go to **https://play.google.com/console/signup**
2. Choose account type (see box above), pay the **$25**.
3. Complete **identity verification** — upload ID. *This can take hours to days.*
   You cannot publish anything until it clears, so do it first.
4. While waiting, continue to Phase 2 and 3 — they don't depend on it.

---

## Phase 2 🔒 — Set up payments (this is the "generating income" step) (⏱ 30 min + 3–5 days verification)

Do this **early**: bank verification involves a micro-deposit that takes days,
and you cannot receive a single cent until it completes.

1. In Play Console: **Settings → Payments profile** (or
   https://play.google.com/console → Setup → Payments profile).
2. Create the payments profile with:
   - Legal name (appears on customer receipts)
   - Legal physical address (no PO box)
   - Primary contact + customer support email
   - The name that appears **on customers' card statements** — use
     `Gravity Gambit` so buyers recognise it and don't file chargebacks.
3. **Add your bank account.** It must be in the same country as your business location.
4. Google deposits **under $1** into that account within **3–5 business days**,
   then withdraws it. Return to Console and **enter the exact amount** to verify.
5. Complete the **tax information** section (US developers: W-9; elsewhere: the
   equivalent tax forms Google presents). Payouts are withheld until this is done.

📅 **Payout schedule**: Google pays out monthly, around the 15th, for the
previous month's earnings, once you exceed the payment threshold. Your first
money therefore arrives roughly 4–6 weeks after your first sale. Plan for that.

Google's split: you keep **85%** of the first $1M/year in earnings (15%
service fee), which is the tier you will be in.

---

## Phase 3 — Produce a signed release build (⏱ 45 min)

The repo already builds a Play-ready `.aab`; it just needs your signing key.

1. **Generate an upload keystore — once, ever:**
   ```bash
   keytool -genkeypair -v -keystore upload-keystore.jks \
     -keyalg RSA -keysize 2048 -validity 10000 -alias upload \
     -dname "CN=Gravity Gambit"
   ```
2. 🔒 **Back it up in two places** (password manager + offline copy). Losing it
   is survivable thanks to Play App Signing, but it is a support ticket you
   don't want. **Never commit it** — `.gitignore` already blocks `*.jks`.
3. Build the bundle:
   ```bash
   export GG_KEYSTORE_FILE=/absolute/path/upload-keystore.jks
   export GG_KEYSTORE_PASSWORD=...
   export GG_KEY_ALIAS=upload
   export GG_KEY_PASSWORD=...
   ./gradlew :app:bundleRelease
   ```
   Output: `app/build/outputs/bundle/release/app-release.aab`
4. Sanity-check the artifact before uploading:
   ```bash
   ./gradlew :app:assembleRelease && python3 tools/verify_release_apk.py
   ```
   (Alternatively grab the AAB from any green CI run — see `RELEASE.md`.)

---

## Phase 4 — Create the app and upload (⏱ 1 hour)

1. Play Console → **Create app**.
   - App name: `Gravity Gambit` · Default language: English (US)
   - Type: **Game** · **Free** (⚠️ irreversible — a free app can add in-app
     purchases, but can never become paid)
2. **Release → Testing → Internal testing → Create new release.**
3. Upload the `.aab`. **Accept Play App Signing** when prompted (Google holds
   the real signing key; yours is only the upload key — this is what makes a
   lost keystore recoverable).
4. Add yourself as an internal tester, install via the opt-in link, and play it
   on a real phone. **Do not skip this** — it's your first proof the signed,
   minified build actually runs. Work through the QA checklist in `RELEASE.md`.

---

## Phase 5 — Fill in every "App content" form (⏱ 1–2 hours)

Console → **Policy → App content**. All of these are mandatory; the app cannot
go live with any left incomplete.

| Form | What to answer for this app |
|---|---|
| **Privacy policy** | Requires a **public URL**. Host `docs/PRIVACY_POLICY.md` — easiest is a public GitHub repo with Pages enabled (Settings → Pages), then paste the URL. |
| **Ads** | **No**, the app does not contain ads. (Rewarded ads are shipped disabled — see `ADS.md`. If you ever enable AdMob, you must change this answer.) |
| **App access** | All functionality available without restrictions — no login exists. |
| **Content rating** | Complete the questionnaire honestly: abstract sci-fi, no realistic violence, no gambling, no user-generated content. Expect **PEGI 3 / ESRB Everyone**. |
| **Target audience** | Choose **13+** (avoids the Families programme's extra requirements — do *not* opt into Designed for Families). |
| **Data safety** | **No data collected, no data shared.** True: no analytics, no accounts, our code sends nothing. ⚠️ Do **not** claim "no internet permission" — the merged manifest has one from Play Billing (see `DECISIONS.md` D-3). |
| **Government apps / Financial features / Health** | No to all. |
| **News app** | No. |

---

## Phase 6 🔒 — The 12-tester gauntlet (⏱ 14 days minimum — the real bottleneck)

Personal accounts created after 13 Nov 2023 must pass this before production.

1. **Release → Testing → Closed testing → Create a track** (the default
   "Alpha" track is fine). Upload the same `.aab`.
2. Create an **email list** of testers: Console → Testers tab → Create email list.
3. Recruit **at least 12 people with real Android devices and real Google
   accounts** — friends, family, colleagues, communities. Emulators, bots and
   duplicate accounts do **not** count and risk rejection.
4. Send them the **opt-in URL**. Each must click it and **install the app**.
5. **They must stay opted in for 14 consecutive days.** Aim for 15+ testers so
   one person uninstalling doesn't reset your clock. They don't need to play
   daily — they must remain opted in.
6. Meanwhile, collect feedback: testers use in-app **Settings → Write feedback**,
   send you the blob, and you paste each into `feedback/inbox/` for triage
   (`feedback/README.md`).
7. After 14 days: Console shows **Apply for production access**. Fill in the
   short questionnaire about what you tested and what you learned. Review
   typically takes a few days.

---

## Phase 7 — Turn on the money (⏱ 1 hour, after first upload)

In-app products can only be created **after** an AAB containing the Billing
library has been uploaded (already true from Phase 4).

1. Console → **Monetize → Products → In-app products → Create product**, three times:

   | Product ID (must match exactly) | Type | Price | Name / description |
   |---|---|---|---|
   | `stardust_small` | One-time | $0.99 | 600 Stardust |
   | `stardust_large` | One-time | $2.99 | 2,200 Stardust |
   | `cosmic_pack` | One-time | $2.49 | Cosmic Pack — every trail, forever |

2. Console → **Monetize → Products → Subscriptions → Create subscription**, twice:

   | Product ID | Billing period | Price | Notes |
   |---|---|---|---|
   | `ad_pass_weekly` | Weekly | $1.99 | Base plan, auto-renewing |
   | `ad_pass_monthly` | Monthly | $4.99 | Base plan, auto-renewing — promote this one |

   For each subscription: create a **base plan**, set the price, then
   **activate** it. An inactive base plan is invisible to the app.
3. **Activate every product** (they default to inactive — the single most
   common reason "my IAP doesn't show up").
4. Set prices for other countries — accept Google's auto-conversion, then spot
   check that no market ends up absurdly priced.
5. **Test purchases without spending money**: Console → Setup → **License
   testing** → add your Gmail. Purchases by that account are free and
   auto-refunded. Verify: buying stardust credits the balance; buying Ad Pass
   makes matches pay 2× and hides ad UI; reinstalling restores `cosmic_pack`.

Pricing rationale, and why the Ad Pass is priced to out-earn ads:
`MONETIZATION.md` and `ADS.md`.

---

## Phase 8 — Go live (⏱ 1 hour)

1. Complete the **store listing**: Console → Grow → Main store listing. Copy is
   pre-written in `STORE_LISTING.md`.
   - Short + full description: paste from that doc
   - **App icon 512×512**: `store/icon-512.png` ✅ already generated
   - **Feature graphic 1024×500**: `store/feature-1024x500.png` ✅ already generated
   - **Phone screenshots (2–8 required)**: capture on a real device — shot list
     in `STORE_LISTING.md`. This is the one asset only you can make.
2. **Release → Production → Create new release** → upload the AAB → add release
   notes → **staged rollout at 20%**.
3. Review usually takes **a few days to a week** for a first submission.
4. Once live and stable for a few days, raise the rollout to **100%**.

---

## Phase 9 — After launch (ongoing)

- [ ] Set `ShareMessages.STORE_URL` in the app to your real listing URL
      (`https://play.google.com/store/apps/details?id=io.github.stan2032.gravitygambit`),
      ship an update, and every shared duel code becomes an install link.
      One-line change; the viral loop is the cheapest marketing you have.
- [ ] Watch **Console → Quality → Android vitals** for crashes/ANRs.
- [ ] Reply to reviews — Play weights this, and early replies build goodwill.
- [ ] Triage `feedback/inbox/` and tune the game (`TUNING.md`).
- [ ] Every update: bump `versionCode` (+1) and `versionName` per SemVer
      (`RELEASE.md`), then Production → Create new release.

---

## Realistic expectations

A first game from an unknown developer typically earns very little at launch —
downloads come from people you tell, not from search. What actually moves
revenue here, in order: the **daily streak** (retention), **duel codes**
(each one is an install pitch to a friend), and **cosmetics visible in your
opponent's replay**. Judge the game after 200+ installs, not 20, and use
`feedback/inbox/` to decide what to build next rather than guessing.

## Fast checklist (print this)

```
[ ] $25 account created + ID verified          (days)
[ ] Payments profile + bank verified + tax     (3-5 days)  ← do early
[ ] Keystore generated + BACKED UP TWICE
[ ] AAB uploaded to internal testing + played on a real phone
[ ] All App content forms complete
[ ] 12+ testers opted in, 14 consecutive days   ← the bottleneck
[ ] 5 IAP products created AND ACTIVATED
[ ] License testing verified purchases work
[ ] Screenshots captured on device
[ ] Applied for production access → approved
[ ] Production release at 20% → 100%
[ ] STORE_URL set in app, update shipped
```

## Sources (verified 2026-08-04)
- Developer signup: https://play.google.com/console/signup
- Testing requirements for new personal accounts: https://support.google.com/googleplay/android-developer/answer/14151465
- 12-testers community guide: https://support.google.com/googleplay/android-developer/community-guide/255621488/everything-about-the-12-testers-requirement
- Create a payments profile: https://support.google.com/googleplay/android-developer/answer/7161426
- Merchant bank account: https://support.google.com/googleplay/android-developer/answer/7161440
- Link developer account to payments profile: https://support.google.com/googleplay/android-developer/answer/3092739
- Target API level requirements: https://developer.android.com/google/play/requirements/target-sdk
- Billing library deprecation schedule: https://developer.android.com/google/play/billing/deprecation-faq
- Data safety form: https://support.google.com/googleplay/android-developer/answer/10787469
