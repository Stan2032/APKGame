# STORE_LISTING.md — Play listing draft (voice-checked against AI_LOOK_AVOIDANCE.md)

## Identity
- Title (≤30): `Gravity Gambit`
- Developer name: Stan2032 (or personal choice at Console signup)
- Category: Game → Arcade · Tags: physics, PvP, offline, puzzle
- Content rating expectation: PEGI 3 / Everyone (abstract sci-fi, no realistic violence, no gambling; IAP present → declared)

## Short description (≤80 chars) — options, pick by A/B later
(All phrases must pass AI_LOOK_AVOIDANCE.md §pointing test: reader-world referents only.)
1. `Real-gravity artillery. Duel friends by text message — no accounts, no logins.` (79)
2. `Slingshot shots through real gravity. Send duels to friends in any chat app.` (77)
3. `N-body artillery duels. Every level provably beatable. Duels fit in a text.` (75)
4. `Real gravity, real duels. PVP is skill only — everyone gets every shell.` (72)

## Full description (draft)

> Every shot obeys real N-body gravity. Curve around planets, skim a star for a graze bonus, or fire away from the target and let three gravity wells bring it home.
>
> **Place your own gravity.** Before firing, drop up to three anchors that pull or push, and bend the shot along a path you designed. You stop solving the field and start building it.
>
> **Call your shot.** Declare before you fire — under 40% power, through the pull of two different worlds, or the long way round without ever crossing the line to the target. Keep the promise and the shot pays up to 2.2×. Break it and the shot is wasted. The game only ever offers a call it has proved this level can pay.
>
> **Campaign** — endless generated sectors. Every level is machine-proven solvable before you see it, and a level beatable by simply pointing at the target is thrown away. Some systems ask for something other than destruction: hold orbit around a marked world for four seconds, or strike a comet that will not stay still.
>
> **Daily Challenge** — one seed for the whole world each day. Five shots, moving target, share a result code that mathematically proves your score.
>
> **Duels** — hot-seat on one phone, or send a challenge code through any messenger. Fifty-two characters opens a duel. Your rival's phone re-simulates your shots and verifies them, so a forged score fails the maths. No servers. No accounts. No waiting for matchmaking.
>
> **PVP is skill only.** A duel issues every shell and the longest aim trace to *both* players, always. Nothing anyone has bought, unlocked or ground for can decide a match.
>
> **A season worth returning to.** Fifty tiers over four weeks, filled by playing anything — campaign, the daily, or a duel. The optional pass adds a second reward at every tier and pays out everything you have already reached the moment you buy it.
>
> Private by construction: no accounts, no data collection, no analytics — and the whole game works in airplane mode.

Keyword notes: gravity, slingshot, artillery, orbit, pvp, duel, offline game, physics puzzle, turn based. Never stuff; Play penalizes.

**Accuracy notes (re-audited 2026-08-06 — every number here is measured):**
- "Fifty-two characters" is the real length of an opening duel code. The draft
  previously said "~230", which was wrong at both ends: a full 30-turn duel
  with every anchor spent reaches ~1039 (`ShareCodeSizeTest`). Quote the
  opening figure, since that is the one a player actually sends first.
- "PVP is skill only" became true in 0.12.0 (D-29). Before that the preview
  upgrade and the Breaker's damage both reached duels, so the old line
  "nothing you can buy wins a duel" was **false when written**. It is now
  enforced by `Economy.duelLoadout()` and held by a test.
- "up to 2.2×" is the largest gambit payout; "four seconds" is
  `Tuning.CAPTURE_TICKS` at 60Hz. Re-check both against TUNING.md before
  submitting, and never round a claim in the game's favour.

## Screenshots (phone, min 4; capture from real gameplay, portrait, no device frames, no fake-quote overlays)
1. Mid-flight slingshot around a planet, trail glowing (the money shot — retake until beautiful).
2. Aim mode: the floating stick mid-pull, preview dots bending through a binary-star field.
3. Anchors placed and the preview visibly curving through them (this is the mechanic nothing else has).
4. The gambit dialog open, showing a declaration and its payout.
5. A Capture system: the ring around the marked world, part-filled mid-orbit.
6. Duel HANDOFF "Shot locked in 🔒" code card (the differentiator — show the code).
7. Season ladder with both lanes visible and a tier or two collected.
8. Campaign sector map with mixed star ratings.
Caption style: mechanism statements ("Real N-body gravity", "PVP that fits in a text"), no exclamation marks.

## Feature graphic (1024×500)
Dark #070B18 field; title set in the in-app type treatment (GRAVITY heavy-white / GAMBIT cyan, wide tracking); one planet with a projectile trail arcing a full slingshot around it; no renders, no faces, flat vector shapes consistent with the icon.

**Generated drafts live in `store/`** (`icon-512.png`, `feature-1024x500.png`),
reproducible via `python3 tools/store_assets.py`. Treat as v1 drafts — restyle
by editing the script, not by hand, so they stay regenerable.

## What's-new template (per AI_LOOK_AVOIDANCE §copy)
```
0.x.y
- <mechanism-level change, one line each>
- <e.g. "Retro brake weakened 0.45→0.55; graze window widened to 18u">
```
