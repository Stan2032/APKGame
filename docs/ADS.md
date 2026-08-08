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

> ⚠️ **CORRECTED 0.19.12 — the invariant below is NOT currently satisfied, and
> the old math said it was.** The previous version assumed "5 shop views + ~5
> post-match doubles = **10 views/day**" as the heaviest possible watcher, and
> treated the whole thing as governed by `AdRewards.DAILY_WATCH_CAP`. **Only
> the shop surface is capped.** The post-match "Double it" button
> (`GameScreen`) grants its reward via `repo.update { ... }` and **never calls
> `recordAdWatch`**, so it never increments `adWatchesToday` and no daily cap
> applies to it. It is limited only by a local `remember` flag — once per
> summary screen, i.e. **once per match, unlimited per day**. The bound is the
> player's match count, not the cap.

There are **two** rewarded surfaces, and they are governed differently:

| Surface | Reward | Capped? |
|---|---|---|
| Shop "free stardust" button | `AdRewards.WATCH_STARDUST` (40✦) | **Yes** — `DAILY_WATCH_CAP` = 5/day |
| Post-match "Double it" | doubles that match's stardust | **No** — once per match, no daily limit |

Assumptions from public rewarded-video benchmarks: $10–25 eCPM in tier-1
markets, ~50% lower elsewhere. Net figures apply Play's 15% small-dev tier.

```
views/day  = 5 (shop, capped) + one per match played (uncapped)
10 matches/day → 15 views → $4.50–11.25/mo gross → ~$3.82–9.56 net
20 matches/day → 25 views → $7.50–18.75/mo gross → ~$6.38–15.94 net
40 matches/day → 45 views → $13.50–33.75/mo gross → ~$11.47–28.69 net
Average users watch far less: $0.30–1.50/month typical.
```

- `ad_pass_monthly` at $4.99 → **~$4.24 net**. It beats the *average* watcher
  by 3–10× — that part was always true and still is.
- It does **not** beat a heavy watcher: at 10 matches/day and a $25 eCPM the
  ads are worth ~$9.56 against the pass's $4.24, and the gap widens with play
  rate. The old claim that it "matches or beats even the theoretical maximum
  watcher" was arithmetic built on a cap that governs half the surface.

**The invariant this file exists to protect (D-13, and an owner directive from
the start — "Ad Pass priced to earn more than the ads it replaces") is
therefore currently UNPROVEN at high play rates.** Nothing is live: ads ship
disabled behind a no-op provider (D-12), so there is time to decide rather
than a fire to put out. The options, for the owner:

1. **Count post-match doubles against `DAILY_WATCH_CAP`** — restores the
   invariant exactly as written, at the cost of capping a player-friendly
   feature that currently has no limit.
2. **Give doubling its own daily cap** — same effect, tuned separately, so the
   shop and the summary do not compete for one allowance.
3. **Raise the pass price** — preserves the feature, but $4.99 would have to
   roughly double to cover a 20-match/day player, which fights the
   "cheap IAP" directive (D-13).
4. **Accept it and restate the directive** — the pass is for people who do not
   want ads, not an instrument for out-earning the heaviest watcher. Honest,
   but it is a change to what the owner asked for, so it is their call.

Keep this invariant when tuning: *pass price(net, monthly-equivalent) ≥ the
maximum ad revenue a player can generate across **every** rewarded surface.*
When adding a surface, ask what caps it — the failure above was a surface
added without one, while the math kept assuming the cap covered everything
(constants live in `AdRewards`).

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
