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
> **Campaign** — endless generated sectors. Every level is machine-proven solvable before you see it, and a level beatable by simply pointing at the target is thrown away. Some systems ask for something other than destruction: hold orbit around a marked world for four seconds, strike a comet that will not stay still, or thread three gates in the right order.
>
> **Every system has its own sound.** Nothing here is a recording. The drone under each level is built from the worlds on the board — heavier bodies sing lower — and where a pulsar beats, you can hear its gravity rise and fall before you fire through it.
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
- **The audio paragraph is the one line here that no human has yet checked.**
  Every clause in it is true of the code — the pad is synthesized per system
  from the bodies, heavier bodies do get lower voices, and a pulsar's tremolo
  does run at its real gravity period (docs/AUDIO.md, `AmbienceTest`). What is
  *unverified* is whether any of it is audible as described on a phone
  speaker. "You can hear its gravity rise and fall" is a promise made to a
  stranger's ears, so **do not submit this paragraph until someone has
  listened** (TODO PRESENTATION). If the pulsar reads as wobble rather than as
  a pulse, cut that clause and keep the first two sentences, which stand on
  their own.

## Screenshots (phone, min 4; capture from real gameplay, portrait, no device frames, no fake-quote overlays)

**Go straight to these levels — do not grind for the moments.** The
coordinates below are computed by `ScreenshotSeedsTest`, which searches real
campaign levels for each shot and prints the aim where one is needed. Levels
are given as a player navigates them (sector, level), not as raw seeds.
Regenerate any time with
`./gradlew :core:test --tests "*ScreenshotSeedsTest*"` — and **re-run it after
any generator change**, because these coordinates are only true for the
generation the code produces today.

> ⚠️ **Read this before planning the shoot: the headline table is a LATE-GAME
> shoot.** Campaign content is gated, and the first published catalogue ignored
> that — naming a coordinate is not the same as making it reachable.
> - A **level** needs ≥1★ on the level before it, so levels must be cleared in
>   order (any rating will do).
> - A **sector** needs **20★** in the sector before it (of 36 available).
>
> So *sector 4, level 11* costs **80★ across sectors 0–3**, plus clearing ten
> levels of sector 4 — roughly 30–40 levels of play before the money shot can
> be taken. That is fine if you are playing anyway (owner items 3, 8 and 9 all
> want hands on the game), but it is not a five-minute job.
>
> **If you want to shoot immediately, use the early table below.** Two of the
> shots are available on the very first level of a fresh install.

| # | Shot | Go to | Aim |
|---|---|---|---|
| 1 | Mid-flight slingshot around a world, trail glowing (**the money shot** — retake until beautiful) | **sector 4, level 11** | angle `3060`, power `300` → gain 230, passes 25u from a surface, 279 ticks |
| 2 | Aim mode: floating stick mid-pull, preview dots bending through a heavy two-keeper field | **sector 4, level 4** | — (4 bodies of radius ≥45: a r=75 star and three large planets) |
| 3 | Anchors placed, preview visibly curving through them (*the mechanic nothing else has*) | **sector 3, level 6** | — (highest measured anchor leverage in range, 0.20) |
| 4 | The gambit dialog open, showing a declaration and its payout | **sector 0, level 1** | — (all four declarations offered: Featherweight, Two-body, Stolen speed, Long way) |
| 5 | A Capture system: the ring around the marked world, part-filled mid-orbit | **sector 1, level 2** | angle `2360`, power `1000`, brake at tick `40` → dwell 334 (needs 240) |
| 6 | **Debris**: a board wearing the history of your own misses (new in 0.18.0) | **sector 0, level 1** | — (reaches 3 wrecks within the 4-shot campaign limit) |
| 7 | Duel HANDOFF "Shot locked in 🔒" code card (the differentiator — show the code) | any duel | not seed-dependent |
| 8 | Season ladder with both lanes visible and a tier or two collected | Season screen | not seed-dependent |
| 9 | Campaign sector map with mixed star ratings | Campaign screen | not seed-dependent |

### Reachable on a fresh install (no grinding)

| Shot | Go to | Aim | Unlock cost |
|---|---|---|---|
| The gambit dialog, all four declarations offered | **sector 0, level 1** | — | **none — first level** |
| **Debris**: a board wearing your own misses | **sector 0, level 1** | — | **none — first level** |
| Slingshot (smaller than the headline one: gain 132 vs 230) | **sector 0, level 11** | angle `2500`, power `500` → passes 31u from a surface | clear sector 0 levels 1–10, any rating |

Everything else — the best slingshot, the Capture ring, the double-keeper
field, the high-anchor-leverage board — lives deeper and needs the star cost
above. Shots 7–9 (duel handoff, season ladder, sector map) are not gated at
all, though the season ladder looks better with a tier or two collected.

> ⚠️ **Shot 2 used to say "binary-star field".** There is no such thing in this
> game — the generator emits at most one star, and its double-keeper path adds
> a second large *planet*. Measured across 720 systems the star count is never
> above 1. Anyone hunting for a binary would have hunted forever (D-41).

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
