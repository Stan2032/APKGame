# Ads — rewarded-only, by policy

## The policy (non-negotiable, encoded in the architecture)

1. **Ads are only ever opt-in rewards.** A player taps "watch → get X".
   Nothing is interrupted, nothing auto-plays, nothing shows "after a match"
   uninvited. There are no interstitials anywhere in this codebase and the
   `RewardedAdsProvider` interface cannot express one.
2. **Ad buttons exist only while an ad is actually loaded** (`available`
   flow), so players never tap into a spinner.
3. **Ad Pass subscribers never see an ad**, and automatically receive every
   boost ads could give — watching becomes strictly unnecessary.

Why rewarded-only: interstitials trade retention for pennies; rewarded ads
are the only format players *thank you for*, with eCPMs 3–10× banner rates.
This is both the ethical and the higher-revenue choice.

## Reward surfaces (implemented)

| Surface | Reward | Guard |
|---|---|---|
| Shop "Free stardust" | ✦ 40 per view | 5/day cap (UTC), hidden for Pass holders |
| Post-match "Double it" | doubles that match's stardust | once per match, opt-in button, hidden for Pass holders |

Both are wired through `RewardedAdsProvider` and appear **only** when
`available == true`. The shipped provider is `NoOpRewardedAds` (never
available), so the released app currently shows no ad UI at all and keeps
its zero-network privacy posture.

## The Ad Pass (implemented; create products in Play Console)

| Product ID | Suggested price | Perks |
|---|---|---|
| `ad_pass_weekly` | $1.99/week | 2× match stardust, ✦ 50 daily drop, no ads ever |
| `ad_pass_monthly` | $4.99/month | same, better rate — the one to promote |

### The pricing math (the user's requirement: pass must out-earn ads)

Assumptions from public rewarded-video benchmarks: $10–25 eCPM in tier-1
markets, ~50% lower elsewhere. Our heaviest possible watcher does
5 shop views + ~5 post-match doubles = **10 views/day**:

```
10 views/day × 30 days × ($10–25 / 1000) ≈ $3.00–7.50/month gross
→ after network/store cuts, realistically $2–5/month per HEAVY user
Average users watch far less: $0.30–1.50/month typical.
```

- `ad_pass_monthly` at $4.99 → ~$4.24 net after Play's 15% (small-dev tier):
  **beats the average watcher by 3–10×, matches or beats even the
  theoretical maximum watcher.**
- `ad_pass_weekly` at $1.99 → ~$6.75/month-equivalent net: prices impatience,
  out-earns everything.

Keep this invariant when tuning: *pass price(net, monthly-equivalent) ≥
DAILY_WATCH_CAP-driven maximum ad revenue.* If you raise the watch cap or
add reward surfaces, re-run this math (constants live in `AdRewards`).

## How to integrate a real ad network later (AdMob playbook)

Deliberately not bundled yet — the SDK requires a real AdMob App ID at
startup (crashes without one) and changes the privacy story. When ready:

1. Create an AdMob account, an app entry, and **one rewarded ad unit**.
2. Add `com.google.android.gms:play-services-ads` to `app/build.gradle.kts`
   and the App ID `<meta-data>` to the manifest
   (`com.google.android.gms.ads.APPLICATION_ID`).
3. Implement `AdmobRewardedAds : RewardedAdsProvider` (load on init +
   after each show; set `available` accordingly; call `onReward` only from
   `onUserEarnedReward`). Pass it to `App(...)` in `MainActivity` in place
   of the `NoOpRewardedAds` default. **No other code changes** — every
   surface lights up automatically.
4. Compliance updates (all mandatory):
   - Manifest gains INTERNET/AD_ID permissions (via the SDK) → update the
     **Data Safety form** (advertising data collected by AdMob) and
     **PRIVACY_POLICY.md** (add AdMob as a partner, link Google's policy).
   - Add **UMP consent flow** (EEA/UK GDPR) before loading ads.
   - Declare "Yes, contains ads" in Play Console and add `app-ads.txt` to
     your developer website domain.
5. Re-verify the Ad Pass math against your actual eCPMs after 2 weeks of
   data; adjust prices in Play Console (never in code).
