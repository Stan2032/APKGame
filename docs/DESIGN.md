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
4. **Modes feed each other.** Campaign stars unlock duel weapons; duels pay
   the campaign economy; the daily feeds cosmetics. No dead ends.
5. **Fair monetization.** Cosmetics and convenience only. Never power.

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
  core in ≤4 shots; 1 shot = ★★★. Stars gate projectile unlocks and sector
  progression.
- **Daily Challenge** — one seed worldwide per UTC day (difficulty 6, five
  shots, target relocates after each hit). Streaks; Wordle-style share text
  that embeds a *verifiable* replay code.
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

- **Campaign stars → duel arsenal.** True, and the strongest link: the Heavy,
  Splitter and Retro shells need 12/30/50 campaign stars *and* stardust before
  they can be carried into a duel.
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

Cosmetics never touch gameplay, so PVP stays fair by construction — and note
that this is why the streak pays in stardust rather than in anything a duel
can feel.

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
   and is not decidable from anything — it would have to be believed. Cut.
2. **Only what this arena can pay.** The generator proves, per seat, which
   declarations have at least one winning shot that honours them, and the game
   offers only those. Measurement found levels where no route passes two
   worlds; without the proof the game would have been selling a bet it could
   not settle.

## Economy (see docs/MONETIZATION.md for pricing)

Stardust flows in from every mode; flows out to projectile unlocks, aim-trace
upgrades, and cosmetic trails. IAP: two stardust packs and a cosmetics
bundle, priced at pocket change. No ads in v1 — the Data-Safety story
("collects nothing, works offline") is itself a conversion feature.

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
4. **Sound design** — synthesized, seed-derived ambient per system.
5. **Ghost racing** — overlay a friend's daily replay as a translucent ghost.
