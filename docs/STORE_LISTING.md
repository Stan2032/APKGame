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

## Full description (draft)
> Every shot obeys real N-body gravity. Curve around planets, skim a star for a graze bonus, or fire away from the target and let three gravity wells bring it home.
>
> **Campaign** — endless generated sectors. Every level is machine-proven solvable before you see it: the generator simulates hundreds of shots and ships only fields with a real solution. Fewer shots, more stars.
>
> **Daily Challenge** — one seed for the whole world each day. Five shots, moving target, share a result code that mathematically proves your score.
>
> **Duels** — hot-seat on one phone, or send a challenge code (~230 characters) through any messenger. Your rival's phone re-simulates your shots and verifies them — forged scores fail the math. No servers. No accounts. No waiting for matchmaking.
>
> Four projectile types, 0.1° aim precision, style bonuses for showing off, cosmetic trails your rival sees in the replay.
>
> Private by construction: no internet permission, no data collection, works in airplane mode. Optional purchases are cosmetics and convenience only — nothing you can buy wins a duel.

Keyword notes: gravity, slingshot, artillery, orbit, pvp, duel, offline game, physics puzzle, turn based. Never stuff; Play penalizes.

## Screenshots (phone, min 4; capture from real gameplay, portrait, no device frames, no fake-quote overlays)
1. Mid-flight slingshot around a planet, trail glowing (the money shot — retake until beautiful).
2. Aim mode: preview dots bending through a binary-star field + angle/power readout visible.
3. Duel HANDOFF "Shot locked in 🔒" code card (the differentiator — show the code).
4. Campaign sector map with mixed star ratings.
5. Daily summary with score + "Share result (with proof)".
6. (opt) Repulsor anomaly field mid-explosion.
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
