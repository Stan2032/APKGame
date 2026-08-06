# Monetization — cheap on purpose

## Philosophy

The user's brief: purchases "made cheap as possible," PVP as the revenue
engine. The design conclusion: **sell identity and momentum, never power.**
PVP games live or die on perceived fairness; the moment a purchase wins a
duel, word-of-mouth dies. Cosmetics + convenience keeps the fun loop clean
and the Play policy surface minimal.

## Products (configure these IDs in Play Console → Monetize → Products)

| Product ID | Type | Grants | Suggested price |
|---|---|---|---|
| `stardust_small` | Consumable | +600 ✦ | $0.99 |
| `stardust_large` | Consumable | +2200 ✦ | $2.99 |
| `cosmic_pack` | Non-consumable | every cosmetic trail, current & future | $2.49 |
| `season_pass` | Consumable (per season) | The season's premium lane: shells, trails, preview tiers, bigger stardust drops — additive to the free lane and retroactive to every tier already earned | $4.99/season |
| `ad_pass_weekly` | Subscription | 2× match rewards, ✦50/day, never any ads | $1.99/wk |
| `ad_pass_monthly` | Subscription | same — the headline offer | $4.99/mo |

Ads themselves are **rewarded-only and opt-in** (never interstitials, never
forced after matches), currently shipped disabled behind a no-op provider.
The Ad Pass is deliberately priced to net more per subscriber than that
player's ad views ever could — full policy, math and the AdMob integration
playbook live in [ADS.md](ADS.md).

Notes:
- Stardust buys *nothing* a player can't earn by playing; packs are a
  time-shortcut only. State this in the store listing — it converts.
- **Nothing purchasable can decide a duel** (D-29). Duels issue every shell
  and the top preview to both players, so the season pass, the stardust packs
  and the shop are all free to be generous without becoming pay-to-win. Say
  this in the listing too: "PVP is skill only — everyone gets everything."
- There is deliberately **no tier-skip product**. It would convert, and it
  would also turn the one long-term progression system into a receipt.
- `cosmic_pack` is deliberately the "generous" SKU: cheap, permanent,
  restore-supported (`queryPurchasesAsync` on every connect).
- Prices are set in Play Console, not code; local currency floors sometimes
  beat $0.99 — let Console's price templates handle that.

## Implementation status

`BillingManager` (Play Billing Library 8 — **mandatory ≥ v8 for new apps
since 2026-08-31**, see reference/play-policy-snapshot.md):

- Connects lazily with auto-reconnection; the whole game works when billing
  is unavailable (shop simply shows earnable items only).
- Consumables are consumed *before* granting (a failed consume ⇒ Play
  redelivers ⇒ no lost grants, no double-grants).
- Entitlements are acknowledged (unacknowledged purchases auto-refund after
  3 days).
- Products appear in the UI only when Play returns details, so the app is
  safe to ship before Console products exist.

## Deliberate non-choices

- **No ads in v1.** Ads force an SDK, the AD_ID permission, a heavier
  Data-Safety form, and a worse first impression. Revisit only with real
  retention data, and then rewarded-only (e.g. "watch to double a duel
  payout").
- **No subscription.** Nothing recurring to sell yet. A "Season Pass" of
  cosmetic tracks is a natural later fit once dailies have an audience.
- **No gacha/loot boxes.** Regulatory drag in several markets, reputational
  drag everywhere.

## Revenue expectations (honest)

Solo-dev v1 games earn approximately nothing until retention proves out —
budget emotionally for that. The instruments that actually move revenue
here, in order: daily streak (retention) → duel codes (viral loop, every
code is an install pitch) → cosmetics visible *in the opponent's replay*
(the trail you bought is literally rendered on your rival's screen —
that's the store).
