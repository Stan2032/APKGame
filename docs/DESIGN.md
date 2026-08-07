# Gravity Gambit — Game Design Document

*The argued-out version. This is the thinking, not just the conclusions.*

## Vision

A mobile-first, portrait, physics-duel artillery game set in procedurally
generated star systems. You fling projectiles that curve through real N-body
gravity; the skill ceiling is orbital mechanics itself. Sessions are 30
seconds to 5 minutes; mastery is unbounded.

**Pillars**

1. **The physics is the game.** No fake arcs. Real gravitation, real
   slingshots, real orbits. Depth comes free from mathematics.
2. **Determinism is a feature, not plumbing.** Same seed + same inputs =
   the same universe on every phone, forever. This powers verified replays,
   serverless PVP, and a global daily challenge.
3. **Generated, but guaranteed.** Every level is machine-proven solvable
   before a human ever sees it, with difficulty *measured*, not guessed.
4. **Modes feed each other.** Campaign stars unlock shells; duels and
   Expedition pay the shared economy; the daily feeds cosmetics. No dead ends.
   ⚠️ This pillar read "campaign stars unlock **duel** weapons" until 0.17.1 —
   false since D-29, which issues every shell to both duellists. The same
   wrong claim appeared twice in this file, in the pillars and in the
   interlock list, which is how a headline sentence survives an audit of the
   section below it.
5. **Fair monetization: nothing bought can decide a duel.** Amended from
   "cosmetics and convenience only, never power" (D-13): that wording was
   right in spirit and the game did not honour it — the preview upgrade and
   the Breaker's damage both reached PVP. Purchases *may* now grant real
   progression (shells, aim traces, the season pass); the invariant is
   specifically that **none of it reaches PVP**, which duel normalization
   enforces structurally rather than by promise.

## Why this concept (the argument)

Alternatives considered and rejected:

| Candidate | Why not |
|---|---|
| Idle/merge hybrid | Crowded to the point of parody; wins on ad spend, not craft. Zero mathematical soul. |
| Roguelike deckbuilder | Strong genre but content-hungry (hundreds of hand-tuned cards); procgen can't carry card *balance* on day one. |
| Realtime .io arena | Realtime netcode + servers + matchmaking liquidity on day one — the exact costs we're avoiding. Dead game if <100 concurrent players. |
| Word/trivia PVP | Language-locked, content-hungry, and undifferentiated. |
| **Gravity artillery duel** | Physics generates infinite depth from ~zero content; determinism gives PVP without servers; artillery is a proven-fun genre (Worms, Angry Birds, Gravity Wars) with almost no modern, *honest-physics* mobile entry. |

The artillery genre's dirty secret is that most entries fake their ballistics.
Making the *real* thing the differentiator gives us: a skill ceiling that
markets itself in clips (absurd four-planet slingshot kills), a puzzle
generator that can *prove* its puzzles, and multiplayer whose anti-cheat is
physics rather than a server bill.

## Core loop

Aim (drag slingshot, 0.1° nudge precision) → watch gravity work → hit/miss →
adapt. The aim-preview ("targeting computer") shows only the first 1.5–4.5s
of flight (upgradeable), so long shots stay a judgment call, not a solved UI.

### Shot types (unlocked via campaign stars + stardust)

| Type | Effect | Why it's interesting |
|---|---|---|
| Bolt | Baseline, 34 dmg | The control group. |
| Breaker | 50 dmg, slower launch, big radius | Trades reach for punch; changes which orbits are usable. |
| Triad | Splits into 3 shards mid-flight | Turns one trajectory into a fan; gravity separates the shards beautifully. |
| Retro | One manual brake burn, 30 dmg | The brake tick is recorded into the replay — reflexes become part of the record. |

Because gravitational acceleration is mass-independent, "heavy" projectiles
can't fall differently — so variety comes from launch constraints, splitting,
collision radius and mid-flight control. That's physics-honest design.

### Style system

Graze (+15, skim a surface), Long Shot (+10, ≥6s flight), Trick Shot
(+25, launch >100° away from the target and still connect). Style pays
stardust and teaches advanced play by naming it.

## Modes and how they interlock

- **Campaign** — endless generated sectors of 12 levels. Destroy the target
  core in ≤4 shots. **★★★ is one shot *and* a shot that did something** — a
  kept declaration, a real slingshot gain, or a style bonus; a plain one-shot
  kill with no flourish is ★★ (`MatchEngine.starsEarned`). This line read
  "1 shot = ★★★" until 0.17.1, which is the same false rule the in-app Help
  screen carried until 0.16.1: fixed there, missed here. Stars gate projectile
  unlocks and sector progression.
- **Daily Challenge** — one seed worldwide per UTC day (difficulty 6, five
  shots, target relocates after each hit). Streaks; Wordle-style share text
  that embeds a *verifiable* replay code.
- **Expedition** — a run: chained systems on one tank of fuel, with a route
  choice and sometimes a condition between them. Additive to the campaign
  rather than replacing it (D-32b). Travel costs more the deeper you go,
  which is what makes runs provably end (D-34); nothing is saved mid-run.
  Pays **salvage**, a run-only currency that buys permanent fittings (D-38).
- **Duels (PVP)** — same generated system, alternating shots, 100 HP
  stations, friendly fire on.
  - *Hot-seat*: two players, one phone. Party mode.
  - *Code duels*: asynchronous PVP over any messenger via challenge codes —
    **52 characters** to open one, growing with the match because the whole
    thing is re-encoded every turn (1039 at the 30-turn cap with every anchor
    spent; measured, and held there by `ShareCodeSizeTest`). No servers, no
    accounts (see docs/MULTIPLAYER.md).

**Interlocks** (each checked against the code, 2026-08-06 — two of the three
this section used to claim were not true):

- **Campaign stars → PVE arsenal.** The Heavy, Splitter and Retro shells need
  12/30/50 campaign stars *and* stardust. ⚠️ This entry said "**duel** arsenal"
  and called it the strongest link — which **D-29 made false two paragraphs
  below, in this same section**, when duels started issuing every shell to
  both players. A document that contradicts itself inside one section is worse
  than one merely out of date, because both halves look authoritative.
  Corrected 0.17.1. The unlock ladder is real and still matters everywhere a
  duel is not.
- **Daily streak → stardust.** True as of 0.9.0. It was not before: the streak
  was rendered on the home screen and read by nothing. A defended streak now
  multiplies the daily payout (×1.2 at 3 days, ×1.4 at 7, ×1.6 at 14 and then
  capped — a streak should reward returning, not compound into the only sane
  way to earn).
- **Duel wins → the biggest *repeatable* payout.** A code-duel win pays 60 and
  can be earned any number of times; a campaign first-clear pays more at high
  difficulty (up to 165) but each one exists exactly once. The earlier claim of
  "biggest stardust payouts" full stop was simply false. Hot-seat duels pay
  **nothing**: one person holds both seats, so a payout there is a self-farm.

**Duels normalize** (D-29): both players are issued every shell and the top
preview, unconditionally, so nothing anyone bought or unlocked can decide a
match. This was not always true — the preview upgrade and the Breaker's extra
damage both reached duels, while this document claimed they did not. The fix
is not a revenue sacrifice: it is exactly what allows the season track to be
sold hard without any of it being pay-to-win.

The reason is structural, not squeamish. A duel travels as `(seed, turns,
hash)` and the receiving device re-simulates it, so it can verify the
*physics* but never the *shopping* — a claimed loadout would have to be
believed. Progression therefore lives where beating your own past is the
point: Campaign, Daily, Expedition.

## The season track — the long game

A 28-day season, 50 tiers, XP from verified play in every mode: campaign
clears (scaled by difficulty and stars), the daily (scaled by streak), code
duels win or lose, plus a little for kept gambits and met objectives.
Hot-seat earns nothing, same as it earns no stardust.

- **Free lane** — stardust, thickening at every 5th and 10th tier.
- **Paid lane** (`season_pass`, a consumable bought *per season*) — shells,
  trails, preview tiers, and larger stardust drops. Additive and retroactive:
  buying at tier 30 immediately pays every premium tier up to 30.
- **No buy-tiers product.** A track you can pay to skip is a receipt, not
  progression.
- The curve is calibrated against a modelled play rate, not chosen: a
  committed player finishes around day 23 of 28; the daily alone would take
  eight months. `SeasonTest` fails if it drifts either way.

## The generator (the "cutting edge" part, honestly)

`SystemGenerator` composes a system (star or binary, 2–6 planets, optional
negative-mass repulsor anomaly), then **solves it**: it simulates a 180×5
grid of candidate shots and measures the hit-rate of the aim space — the
size of the solution basin. A layout ships only if that rate falls in the
difficulty band `[max(0.0006, 0.019·0.80^d), ×22 capped at 0.30]`
(recalibrated in 0.7.0 when curated layouts made honest hit-rates much lower;
the old `0.03·0.72^d` floor sat *above* what difficulty 0 actually measured,
which silently pushed every early level onto the fallback path). Too easy →
rejected. Unwinnable → impossible to ship by construction. A layout beatable
by aiming within 22° of the target at any power is rejected outright, on every
path including the fallbacks. Duel systems are additionally checked from
*both* stations so neither player spawns dead.

Difficulty is therefore an *empirical measurement* of the level itself, not
a designer's guess. The fingerprint (`solverHitRate`) is stored in the spec.

## Gambits — calling your shot

Before firing you may **declare** a constraint on the shot you are about to
take: under 40% power, pass through the pull of two different worlds, end
faster than you launched, or never cross the line to the target. Keep the
promise and the shot pays 1.5x-2.2x — style points in campaign and daily,
**damage** in duels, which is the only thing a duel settles on. Break it and
the shot is wasted: the hit does not land.

Two constraints on the design, both of which cut features rather than adding
them:

1. **Only what the physics can see.** A duel travels as a code that the
   receiving device re-simulates, so a declaration has to be decidable from
   the flight. "Fire without looking at the aim preview" is a good challenge
   and is not decidable from anything — it would have to be believed. Cut
   from Gambits (D-23) — **but it shipped after all**, as Expedition's *Dark
   run* condition (0.15.0). The objection was always about verification *by a
   stranger*, and a solo run sends nothing to anyone: the idea was good, only
   the scoring was unverifiable.
2. **Only what this arena can pay.** The generator proves, per seat, which
   declarations have at least one winning shot that honours them, and the game
   offers only those. Measurement found levels where no route passes two
   worlds; without the proof the game would have been selling a bet it could
   not settle.

## Economy (see docs/MONETIZATION.md for pricing)

Stardust flows in from every mode; flows out to projectile unlocks, aim-trace
upgrades, and cosmetic trails. IAP: **four one-time products** — two stardust
packs, a cosmetics bundle, and the per-season `season_pass` — plus two Ad Pass
subscriptions, all priced at pocket change. (This line said "two stardust
packs and a cosmetics bundle" until 0.17.1; `season_pass` shipped in 0.12.0.
Cross-check against `Economy.Iap.ALL_PRODUCT_IDS` rather than any prose list.)

**Salvage is a second currency and deliberately not stardust** (D-38). It is
earned only by finishing Expedition runs and spent only on permanent run
fittings. Stardust is buyable with real money, so pricing run upgrades in it
would let money buy Expedition *depth* — and depth is a personal best, which
is worth nothing if it can be topped up at the till.

No ads in v1 — the Data-Safety story ("collects nothing, works offline") is
itself a conversion feature.

## v1 scope (this repository)

Everything above is implemented: engine + solver + codec in `:core`
(unit-tested, incl. an end-to-end PVP verification test), full Compose UI,
billing scaffold (activates automatically when products exist in Play
Console), CI producing APK + AAB.

## Roadmap (post-v1)

*(Design rationale snapshot — the living, sequenced plan with exit criteria is
[ROADMAP.md](ROADMAP.md).)*

1. **Realtime P2P duels** — WebRTC data channels, lockstep on the same
   deterministic engine; tiny signaling service (free tier). The engine
   needs zero changes: it was built lockstep-ready.
1b. **Play Games Services v2** — automatic sign-in (opaque Player ID only,
   no personal data on our side), cloud saves in the player's own Google
   Drive (Snapshots), plus achievements and a global daily leaderboard with
   zero servers. Costs: INTERNET permission + Data Safety form declares
   "User IDs collected" (see docs/reference/play-policy-snapshot.md).
2. **Orbital motion** — planets on deterministic rails (`pos(t)`), timing
   becomes a weapon.
3. **Leagues** — weekly seed sets with shared verification codes.
4. ~~**Sound design** — synthesized, seed-derived ambient per system.~~
   **Shipped 0.14.0**, pulled forward from post-v1 because the game having no
   music at all was the largest gap a first impression would notice. It went
   further than this line planned: the pad is derived from the *bodies* rather
   than only from the seed, the harmony is chosen by the objective, and a
   pulsar's voice swells at its real gravity period (docs/AUDIO.md, D-33).
5. **Ghost racing** — overlay a friend's daily replay as a translucent ghost.
