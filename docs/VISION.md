# VISION.md — what this game must become

Written 2026-08-05, after the owner's challenge: *make it hard to put down;
question everything; create, don't clone.* This document is deliberately
harsh about the current design. It supersedes DESIGN.md's roadmap section
where the two disagree.

---

## 1. Honest diagnosis

Ask the uncomfortable questions.

**What is the game currently about?** Aiming well in gravity. That is a
*puzzle* framing: a level is a lock, the player hunts for the key, and once
found the level is spent. Puzzle games are respected and abandoned.

**Why would someone open it tomorrow?** Campaign progress and a daily streak.
Both are extrinsic scaffolding — the same scaffolding every mobile game
bolts on. Nothing about the *act of playing* pulls them back.

**What is the single most joyful moment it produces?** A shot that curves
absurdly and connects; the involuntary laugh of "how did that work".

**How often does that happen, and who caused it?** Rarely — and usually by
accident. **This is the central failure.** The game's best moment is
something that happens *to* the player rather than something they authored.
Everything below follows from fixing that one thing.

**What is genuinely ours?** Deterministic physics with verifiable replays.
That is real and rare. But today it buys us serverless PVP — a *distribution*
advantage, not a *fun* advantage. It should also buy gameplay.

**Does the name mean anything?** "Gambit" implies a calculated risk, a
sacrifice for advantage. Nothing in the current game is a gambit. The name is
a promise the design does not keep. Below, it does.

---

## 2. The central insight

> The field is fixed and the player is a solver. Flip it: let the player
> **author** the field, then reward audacity.

A player who bends gravity themselves owns the impossible shot. It stops
being luck and becomes signature. Two people given the same system should
produce visibly different, personal solutions — and want to show each other.

Everything below is downstream of that inversion.

---

## 3. The mechanics

Ordered by leverage. Each is deterministic-safe (integer inputs, pure
functions of tick) so replays and serverless PVP survive intact.

### 3.1 Anchors — the player becomes a level designer *(highest leverage)*

Before firing, place 1–3 **anchors**: small point masses, attracting or
repelling, at quantized positions. Your shot bends around your own
construction.

Why this changes everything:
- **Authorship.** The absurd curve was your idea, not the generator's.
- **Unbounded skill ceiling** with zero content cost — the interesting space
  is combinatorial, not authored.
- **Expression.** Same level, wildly different solutions, all valid.
- **In duels it becomes psychological**: your anchors persist into your
  opponent's turn. You are not only shooting at them, you are *rebuilding
  the arena around them*. That is a form of PVP interaction I have not seen
  in an artillery game, and it is only possible because our physics is real.

Cost: `TurnInput` grows (protocol v3); the solver must account for anchor
freedom when measuring difficulty; UI needs a placement mode.

### 3.2 The Gambit — betting on yourself *(makes the name true)* — **SHIPPED 0.9.0**

Before a shot, optionally **declare** a constraint:
- *Featherweight* — power below 40%. ×1.5
- *Two-body* — pass through the pull of two different worlds. ×1.8
- *Stolen speed* — end faster than you launched, by a clear margin. ×1.6
- *The long way* — never cross the line to the target. ×2.2

Declaring multiplies the shot's payout (style in campaign/daily, **damage** in
duels — the only currency a duel reads). Failing the declaration wastes the
shot entirely: the hit does not land.

Two things this section originally got wrong, both corrected by measurement:

- ***Blind* (fire with no aim preview) was cut.** It is the one proposal here
  the physics cannot see. A peer verifying a share code re-runs the simulation
  and derives everything else from the flight; nothing in the inputs records
  whether the sender peeked. A scoring rule taken on trust, in a game built on
  taking nothing on trust, is a hole — and one that pays a multiplier. (D-23)
- **The menu is proved per level, per seat, not fixed.** Three of twelve
  measured levels had *no* winning shot that passes two worlds. Offering that
  bet there would be the game lying to the player. The generator now proves
  what each seat can honour and offers only that. (D-24)

Why: self-imposed difficulty is the engine of long-term engagement (chess
odds, roguelike ascensions, speedrun categories) and costs nothing to
produce. It converts mastery into *stakes*, which is what "gambit" means.
Verification is free — the simulation already knows what happened.

### 3.3 Consequence — misses reshape the field — **measured 0.17.2, rule revised**

A shot that dies leaves **debris**: a small permanent body at its death
point. Miss twice and you have complicated your own board.

Why: every shot now matters even when it fails, tension escalates within a
match instead of resetting, and in duels the arena visibly degrades into
something neither player designed — a shared story.

**What measurement changed** (`DebrisMeasurementTest`, D-39). The paragraph
above is kept because the *intent* survives, but three of its assumptions did
not, and the rule has to be written from the numbers instead:

- **"Miss twice" is far off.** Inside a realistic aim cone, **57–80% of
  misses die on a body**, so a match litters constantly rather than
  occasionally. (The uniform solver grid says the opposite — 86% leave the
  field — because it includes firing away from the target. That sample is not
  the player, and taking it at face value would have killed the mechanic on a
  false negative.)
- **Debris can never appear in open space.** A shot has essentially one way
  to die on the board: hitting a body (18,000 samples → 2,625 body deaths, 1
  own-station death, 0 timeouts). So debris always lands against something
  already solid. It cannot block an empty lane, and no wording of "at its
  death point" changes that.
- **It cannot be conserved matter.** Adding the shell's cross-section to the
  body it struck grows a radius-100 planet to 100.12 and moves the solve rate
  by *nothing* across 16 hits. Debris has to be sized for legibility.

What makes it work is that a lump is **directional** — it sits on the side
the shot came from, so it obstructs the approach you keep taking, which is
the real content of "the arena degrades into something neither player
designed". Growing a radius instead would be isotropic and say nothing.

Sizing, measured against solvability: at radius 12 a level keeps 50–100% of
its winning shots after 30 lumps; at radius 20, 33–67%. **Nothing stranded
the player** in 27 configurations, including litter worse than the 30-turn
cap can produce. Recommended: **radius ~12, mass 0**.

### 3.4 Resonance — the system has a rhythm — **half shipped 0.14.0**

Pulsars already breathe on fixed periods. Make that a *timing* mechanic:
fire in phase and the assist amplifies; fire out of phase and it fights you.
Multiple pulsars with different periods create polyrhythm, and the audio
sonifies each pulse so the field can be learned **by ear**.

Why: adds a timing skill orthogonal to aiming, and gives the game a sonic
identity — "the level has a beat" is something no competitor has.

**Shipped:** the ear half. The generated pad gives a pulsar a voice that
swells at its real gravity period, phase-locked to the shot (docs/AUDIO.md
§2). The field can now be learned by ear before it is a rule.

**Still open:** the *mechanic* — phase currently changes the pull, as it
always has, but nothing rewards firing in phase beyond the physics itself.
Polyrhythm needs multiple pulsars with different periods, which the pad
already supports (each gets its own tremolo) but the generator does not yet
produce. Note the constraint D-33 exposes: a second period only stays
phase-locked across loop repeats if it also divides the loop length, so
periods must be chosen from divisors of 7 seconds rather than freely.

### 3.5 Objectives beyond destruction — **SHIPPED (Capture, Courier, Thread)**

Procedurally selected win conditions per system:
- **Breach** (current) — destroy the core.
- **Capture** ✅ — hold a ring around the marked world for 240 unbroken ticks.
  The build taught the design rather than the reverse. A shot arriving from far
  away is on a **hyperbolic** trajectory: with a single dominant body and
  nothing to dissipate energy it *must* leave again, however well aimed.
  Measured across twelve generated levels, the best dwell any unbraked shot
  achieved was **191 ticks**, and every refined optimum pinned itself against
  the slowest launch the game permits — the search was asking to go slower than
  physics allows. Fire a RETRO shell and burn at the right moment and the same
  levels yield **339–818**. So Capture is an orbital-insertion puzzle, it hands
  the player a RETRO shell whatever their loadout, and it is the first thing in
  the game that gives that shell a reason to exist.
- **Thread** ✅ — fly through the gates *in order*. The order is the objective:
  a path that clips all three in the wrong sequence has threaded nothing.
  Gates are not scattered and hoped over — the generator flies a real
  trajectory first and hangs the gates on it, so a solution provably exists
  before the level is offered, then re-derives one independently, because "we
  built it from a path" and "a player can find a path" are different claims.
- **Courier** ✅ — strike the marked drifting comet, not a fixed station: the
  target moves, so the shot has to be led. Cheap to add because comets already
  existed — but *not* cheap to assume: of 36 sampled levels only 11 carried a
  comet, and **two of those comets could not be hit at all**. Presence is not
  proof, which is why nothing ships without one.
- **Survivor** — get the shot home again after a flyby.

Why: this is the multi-faceted variety the project always intended;
procgen supplies layouts, objectives supply *reasons*, and the combination
multiplies rather than adds.

### 3.6 Expedition — the compulsive spine — **shipped 0.15.0–0.16.0**

A **run** is a chain of systems. You carry hull, fuel, and anchors between
them. Between systems you choose your route (safe/rich/unknown) and take a
mutator (*binary star ahead*, *anchors doubled*, *debris persists across
systems*). Death ends the run; a meta-currency persists and unlocks
starting options.

**What shipped, and where it departs from the paragraph above** (D-34):

- **Fuel only, not hull-and-fuel-and-anchors.** Three resources need three
  sets of rules, three readouts and three failure states to earn their place,
  and a player cannot hold three trend lines in their head between shots.
- **Travel cost is the clock.** The first draft escalated the *shot* cost;
  the test for it failed at once and taught the design — a per-shot cost
  cannot clock a player who fires one shot, so a perfect run reached the leg
  cap with a full tank. Travel cost rises without bound against refunds that
  are bounded, so past a computed depth every leg is a net loss however
  perfectly it is played. Runs end by arithmetic, not by hoping the
  difficulty curve outruns the player.
- **Death does not end the run; the tank does.** A wreck costs fuel and
  advances you. Ending on one bad system would make the opening minute the
  whole game and would punish precisely the player who took the hard route.
- **Mutators are run rules, not generator requests** — for now. A *binary
  star ahead* has to ask the generator for a layout it may not be able to
  produce in band, and the fallback would leave the run claiming a condition
  that never applied: the exact failure D-24 and D-27 exist to prevent.
  Field-shaping mutators are deferred until the generator can *prove* one
  took.
- **The meta-currency is stardust**, the one the game already has. Minting an
  unspendable token before the thing it buys exists would be a number that
  goes up and means nothing.

One thing the spec did not anticipate: Expedition is where **Blind** finally
becomes legal. D-23 cut it from Gambits because no peer can verify that a
sender did not look at their preview — an objection entirely about
verification by a stranger, which does not apply to a solo run that sends
nothing. It ships as the *Dark run* mutator.

Why: "one more run" is the strongest known retention loop, and it makes
generated content feel like a journey instead of a list.

> **Amended 2026-08-06 (D-32b).** This section originally argued Expedition
> should **replace** the 12-levels-per-sector campaign. The owner's decision is
> that it is **additive**: the campaign stays as the teaching and progression
> spine, Expedition becomes a third PVE mode. The replacement would have
> invalidated the stars, sector gating and unlock ladder that the season track
> and the shop both reference, and forced a save migration — for a tidiness
> gain. Two overlapping PVE modes is the accepted cost.

---

## 4. What we kill or demote

Being honest about subtraction:
- ~~**Sector/level checklist campaign** → becomes Expedition.~~ **Reversed by
  D-32b**, and this line survived the amendment to §3.6 by two versions — the
  kind of leftover that makes a doc argue with itself. The campaign stays: it
  is the teaching spine, and the stars, sector gating and unlock ladder it
  produces are referenced by the season track and the shop. Expedition is a
  third PVE mode alongside it (shipped 0.15.0). The original complaint — that
  a grid of numbered tiles is the most generic possible framing — is still
  fair, and is now answered by *having somewhere else to be* rather than by
  deleting the grid.
- **"Style bonuses" as passive trivia** → folded into Gambits, where the
  player *chooses* them in advance instead of being told afterwards.
- **Projectile types as shop unlocks** → become run-time choices inside an
  Expedition, so they are decisions rather than purchases.
- **Preview upgrade tiers** → the preview becomes a Gambit dimension
  (*Blind* pays more), not a paid convenience.

---

## 5. Staged plan

Each stage is independently shippable and playable; none blocks a playtest.

| Stage | Content | Why this order |
|---|---|---|
| **A** | Anchors (engine + UI + solver awareness), protocol v3 | The inversion. Everything else is better once the player authors the field. |
| **B** ✅ | Gambits (declare/verify/score) — shipped 0.9.0 | Cheap once anchors exist; makes the name true; adds stakes immediately. |
| **C** ✅ | Objectives — Capture, Courier and Thread all shipped | Multiplies existing procgen without new art. |
| **D** | Expedition run structure + mutators (**additive**, not a campaign replacement — D-32b). **Shipped 0.15.0 (engine) + 0.16.0 (junction UI).** | Needs B and C to have something to string together. |
| **E** | Resonance + audio sonification — **sonification half shipped 0.14.0**, resonance *mechanic* still open | Taken early against the plan, because the game had no music at all and the pulsar pad answers both problems at once (D-33). The gameplay half still wants Expedition first. |
| **F** | Debris/consequence | Tune last; it interacts with every other system. |

Protocol note: A changes `TurnInput`; C and F change simulation outcomes.
Both are replay-protocol bumps, which is free *now* (nothing has shipped)
and expensive later. Take them early and deliberately — see D-22 and
TUNING.md §Versioning.

## 6. The test this design must pass

Not "is it clever" but: **does a player, unprompted, want to show someone
else what they just did?** Anchors + Gambits are designed so the answer is
yes, because the shot was authored, constrained, and provable. The share
code already carries the proof; the design finally gives it something worth
proving.
