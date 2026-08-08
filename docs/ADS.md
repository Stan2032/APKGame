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

### The pricing math (the owner's requirement: pass must out-earn ads)

**The invariant:** *pass revenue (net, monthly) ≥ the maximum ad revenue one
player can generate across **every** rewarded surface.* Owner directive from
the first brief, recorded as D-13.

> **History.** 0.19.12 found this invariant broken and the old math claiming it
> held (D-53). The prose assumed "5 shop views + ~5 post-match doubles = 10
> views/day, all governed by `DAILY_WATCH_CAP`". Only the shop was capped: the
> post-match "Double it" button paid out with a bare `repo.update { }`, never
> touched `adWatchesToday`, and was limited only by a `remember` flag — once
> per match, **unlimited per day**. The ceiling was the player's match count.
> 0.19.15 fixed the cause rather than the arithmetic (D-56): there is now one
> budget, every surface spends from it, and the numbers below are computed in
> code rather than written down here.

#### One budget, spent by every surface

| Surface | Reward | Governed by |
|---|---|---|
| Shop "free stardust" | `AdRewards.WATCH_STARDUST` (40✦) | shared budget, plus its own `DAILY_WATCH_CAP` sub-cap |
| Post-match "Double it" | doubles that match's stardust | shared budget (naturally ≤1 per match) |

- `AdRewards.DAILY_REWARDED_VIEWS` = 6 — **total** paid views per day, every
  surface, resetting at UTC midnight. This is the only number that governs
  revenue.
- `AdRewards.DAILY_WATCH_CAP` = 4 — the shop alone, so it cannot spend the
  whole day and leave the doubler advertised but worthless. The asymmetry is
  deliberate and in the player's favour: doubling is the earned, contextual
  surface, so if a player spends their whole budget there, good.
- Both surfaces call `ProfileRepository.spendRewardedView`, which is the only
  way stardust can be granted for an ad. A payout that does not go through it
  fails `AdSurfaceInventoryTest`.

#### Where 6 comes from

It is not chosen by taste. `AdEconomics` holds the world-assumptions and
derives the cap; `AdPassInvariantTest` recomputes it on every build and fails
if the constant and the arithmetic disagree in *either* direction — too high
breaks the directive, too low withholds rewards the economics allow.

| Constant | Value | Why this end of the range |
|---|---|---|
| `AdEconomics.MAX_ECPM_USD` | 25.0 | top of the tier-1 rewarded band ($10–25; ~50% lower elsewhere) |
| `AdEconomics.PLAY_FEE` | 0.15 | Play's small-developer tier (first $1M/year) |
| `AdEconomics.PASS_MONTHLY_USD` | 4.99 | the cheaper monthly-equivalent of the two pass products, so it binds |
| `AdEconomics.DAYS_PER_MONTH` | 31 | the longest month means the most ad revenue to beat |

```
net per view = $25.00 / 1000 × 0.85            = $0.02125
pass, net    = $4.99 × 0.85                    = $4.2415 / month
6 views/day  = $0.02125 × 6 × 31               = $3.9525  ≤ $4.2415  ✓
7 views/day  = $0.02125 × 7 × 31               = $4.6113  >  $4.2415  ✗
```

So 6 is the largest budget the pass covers, and it covers it against a player
who hits the cap **every single day for the longest month at the top eCPM**.
A real player is nowhere near that — typical watchers generate $0.30–1.50/month
— so against the average the pass wins by roughly 3–10×.

Nothing here is live yet: ads ship disabled behind a no-op provider (D-12).

**When adding a surface**, the only question is what spends the view. Route it
through `spendRewardedView` and the budget governs it automatically; the
failure in D-53 was a surface added without anyone asking, while the math went
on assuming one cap covered everything.

**If the price changes** in Play Console, change `AdEconomics.PASS_MONTHLY_USD`
(currently `AdEconomics.PASS_MONTHLY_USD` = 4.99, matching the `ad_pass_monthly`
row above) to match and let the test tell you the new budget. Raising the price to buy a
bigger budget fights the cheap-IAP directive (D-13) and is an owner call; so is
abandoning the invariant, which would be a change to what was asked for and
belongs in DECISIONS.md before it belongs in code.

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
