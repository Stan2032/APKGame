# AI_LOOK_AVOIDANCE.md — keeping this game from looking/reading AI-generated

Generated products have a recognizable smell. Players and reviewers discount it instantly. These are binding rules for ALL player-facing surfaces (UI copy, store listing, icon, screenshots, update notes) and for future agent sessions writing any of them.

## Visual rules
1. **Palette is bespoke and dark**: #070B18 space-blue base, cyan/amber/violet accents, planet palettes hand-indexed (Theme.kt BodyPalettes). BANNED: white cards on lavender gradients, purple→pink hero gradients, glassmorphism blur stacks, gradient-mesh blobs — the default "AI app" look.
2. **The game world IS the art.** Screens lean on the live canvas (starfield, bodies, trails), not stock illustration. Never add: 3D clip-art rockets, DALL-E-style splash art, isometric mascots.
3. **Shape language**: M3 defaults + 8–12dp radii. BANNED: rounded-3xl-everything, floating pill nav bars, card-in-card-in-card nesting.
4. **Type does the branding**: system font + weight/letter-spacing (see title treatment: GRAVITY/GAMBIT black-weight, 8sp tracking). BANNED: decorative display fonts, gradient text.
5. **Emoji policy**: functional glyphs in menus only (🚀 ⚔️ ✦ ★), max one per line, never in body copy, never emoji-walls.
6. **Motion**: physics-driven only (trails, explosions from the sim, twinkle). BANNED: confetti bursts, lottie celebration blobs, bouncing badges.
7. **Icon**: geometric orbit motif on flat #0B1021 (current adaptive icon). BANNED: gradient spheres with lens flares, AI-render planets.

## Copy voice (the bigger tell)
Voice = a physics nerd who respects you: precise, dry, quietly confident, occasionally playful ("the physics noticed", "No peeking", "friendly fire is on. Be careful.").
1. BANNED WORDS/SHAPES: Unleash, Elevate, Seamless, Supercharge, Dive into, Embark, journey, "Experience the thrill", "Take X to the next level", "your ultimate companion", triple exclamation, rhetorical-question hooks ("Ready to dominate?").
2. **Say the mechanism, not the adjective.** Not "amazing realistic physics" → "every shot obeys real N-body gravity". Not "fair play guaranteed" → "your phone re-simulates the replay; forged scores fail the math".
3. Sentence caps on hype: one superlative per screen, zero per store paragraph unless measured ("machine-proven solvable" is allowed because it is literal).
4. Numbers concrete, never rounded-marketing ("52-character duel codes", "0.1° aim nudges", "5 shots"). And **concrete is not the same as correct** — this rule's own example said "~230-character codes" for months, which was wrong at both ends. A specific wrong number reads more credibly than a vague one, which makes it worse. Measure, then quote.
5. No fake social proof ever: no invented reviews, ratings, player counts, "millions of players".
6. Update notes: changelog facts, not "We've been listening! 🎉".
7. British-vs-US spelling: US, consistent.

## Accessibility (quality floor, not an afterthought)
1. **Never fixed `height()` on anything containing text** — use `heightIn(min=)`. Users scale fonts past 200%; fixed heights clip labels silently. Guarded by AccessibilityTest.
2. **Rows of chips/buttons wrap** (`FlowRow`), because four chips that fit at 1.0x do not fit at 2.0x.
3. **Canvas gets a live `contentDescription`** describing match state; decorative canvases (ambient backdrop, rules) get `clearAndSetSemantics {}` so they never pollute the accessibility tree.
4. **Symbolic glyphs need spoken equivalents**: "★★☆" reads as noise; the level cell carries "Level 3, 2 of 3 stars" via merged semantics.
5. Material3 enforces 48dp touch targets by default — do not fight it with smaller fixed sizes.

## Structural tells to avoid in product + repo
1. No placeholder anything reaching a build: "Feature 1", lorem, "Coming soon!" walls (empty states must earn their place: billing/ads sections self-hide instead of stubbing).
2. No dead settings (every toggle works or doesn't exist).
3. No generic template screens (About/FAQ/Terms boilerplate padded with filler).
4. Docs: no "In conclusion", no "It's important to note", no bullet-lists-of-three-adjectives.
5. Naming: no "Pro/Ultra/Max/AI" suffixes; features get game-world names (targeting computer, Retro burn, stardust — already set; keep register consistent).

## The pointing test (run BEFORE the litmus test, on every noun)
Words are pointers, not things. For each load-bearing word ask "why this word?"
repeatedly until you reach the reality it denotes — then check WHOSE reality
that is. Insider referents (our architecture, our docs, our pride) are invalid
on context-free surfaces; the referent must be something the *reader* has
experienced or can see in the adjacent screenshot.
- Case study: feature-graphic tagline "PVP BY CODE" → why "code"? → MatchRecord
  → base64 string → an *implementation* referent; a store-page stranger has no
  path from "code" to that. The player-experienced reality: you send your move
  to a friend as a message. Fix: "DUELS BY TEXT". Likewise "NO SERVERS" (our
  hosting reality) → "NO ACCOUNTS" (their signup reality).
- Corollary: shorthand ("challenge code") is allowed only on context-rich
  surfaces AFTER the surrounding sentence has established its referent (as
  DuelScreen does); never cold on banners, titles, or short descriptions.

## Litmus test before shipping any surface
Read it aloud. If it could be pasted into any other mobile game unchanged, rewrite it until it could only belong to THIS game (mentions gravity, seeds, codes, stardust, or a real mechanic by name).
