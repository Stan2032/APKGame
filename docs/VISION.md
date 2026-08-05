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

### 3.2 The Gambit — betting on yourself *(makes the name true)*

Before a shot, optionally **declare** a constraint:
- *Blind* — no aim preview this shot.
- *The long way* — must not cross the straight line to the target.
- *Two-body* — must be deflected by at least two bodies.
- *Featherweight* — power below 40%.

Declaring multiplies the shot's score (and, in campaign, its star value).
Failing the declaration wastes the shot entirely.

Why: self-imposed difficulty is the engine of long-term engagement (chess
odds, roguelike ascensions, speedrun categories) and costs nothing to
produce. It converts mastery into *stakes*, which is what "gambit" means.
Verification is free — the simulation already knows what happened.

### 3.3 Consequence — misses reshape the field

A shot that dies leaves **debris**: a small permanent body at its death
point. Miss twice and you have complicated your own board.

Why: every shot now matters even when it fails, tension escalates within a
match instead of resetting, and in duels the arena visibly degrades into
something neither player designed — a shared story.

### 3.4 Resonance — the system has a rhythm

Pulsars already breathe on fixed periods. Make that a *timing* mechanic:
fire in phase and the assist amplifies; fire out of phase and it fights you.
Multiple pulsars with different periods create polyrhythm, and the audio
sonifies each pulse so the field can be learned **by ear**.

Why: adds a timing skill orthogonal to aiming, and gives the game a sonic
identity — "the level has a beat" is something no competitor has.

### 3.5 Objectives beyond destruction

Procedurally selected win conditions per system:
- **Breach** (current) — destroy the core.
- **Capture** — achieve a stable orbit for N ticks without collision.
- **Thread** — pass through gates in order.
- **Courier** — strike a moving comet, not a fixed station.
- **Survivor** — get the shot home again after a flyby.

Why: this is the multi-faceted variety the project always intended;
procgen supplies layouts, objectives supply *reasons*, and the combination
multiplies rather than adds.

### 3.6 Expedition — the compulsive spine *(replaces the sector grind)*

A **run** is a chain of systems. You carry hull, fuel, and anchors between
them. Between systems you choose your route (safe/rich/unknown) and take a
mutator (*binary star ahead*, *anchors doubled*, *debris persists across
systems*). Death ends the run; a meta-currency persists and unlocks
starting options.

Why: "one more run" is the strongest known retention loop, and it makes
generated content feel like a journey instead of a list. The current
12-levels-per-sector campaign is a checklist and should be **replaced** by
this, not supplemented.

---

## 4. What we kill or demote

Being honest about subtraction:
- **Sector/level checklist campaign** → becomes Expedition. Levels as a grid
  of numbered tiles is the most generic possible framing.
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
| **B** | Gambits (declare/verify/score) | Cheap once anchors exist; makes the name true; adds stakes immediately. |
| **C** | Objectives (Capture, Thread, Courier) | Multiplies existing procgen without new art. |
| **D** | Expedition run structure + mutators | Needs B and C to have something to string together. |
| **E** | Resonance + audio sonification | Best after Expedition, so it lands as depth rather than noise. |
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
