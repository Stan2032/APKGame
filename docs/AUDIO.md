# AUDIO.md — how this game makes sound

Two systems, deliberately unlike each other.

| | SFX | Ambience |
|---|---|---|
| What | 7 one-shots: fire, hit station, hit planet, brake, style, win, lose | one continuous pad per system |
| Made | offline by `tools/gen_sfx.py`, shipped as `res/raw/*.wav` | at runtime, from the system itself |
| Played by | `SoundPool` (`audio/SoundManager.kt`) | `AudioTrack` MODE_STATIC (`audio/AmbienceTrack.kt`) |
| Rules live in | the Python script | `:core` `Ambience.kt` |
| Setting | `Profile.soundEnabled` | `Profile.musicEnabled` |
| APK cost | ~90 KB | **0 bytes** |

Two settings rather than one, because wanting a hit to land audibly and
wanting a drone underneath it are different wants. Collapsing them into a
single "Sound" switch forces anyone who only objects to the second into
silence.

---

## 1. Why the ambience is generated

There is no finite set of levels here. Every system comes out of a seed, so
there is nothing to record music *for*. The two honest options were one
background loop shared by everything — which says nothing about where you are
— or a loop derived from the place itself.

So the pad is built from the arrangement of mass on the board. Each body that
matters contributes one partial:

| Property of the body | Property of its voice |
|---|---|
| mass | pitch (heavier sits lower) and level |
| position | phase offset within the loop |
| kind | tremolo rate and, for a repulsor, a deliberately sour interval |
| the system's **objective** | which set of intervals the whole chord is drawn from |

A system with one huge star and two pebbles does not sound like three even
worlds, because it is not three even worlds.

### The objective is audible

One interval set per objective, so the brief has a sound before it has words:

| Objective | Intervals | Character |
|---|---|---|
| Breach | 1/1 3/2 2/1 3/1 9/4 | bare fifths and octaves — open, hard, nothing to resolve |
| Capture | 1/1 6/5 3/2 9/5 12/5 | minor third and minor seventh — held, unresolved, which is what four seconds of orbit feels like |
| Courier | 1/1 9/8 3/2 27/16 4/1 | a suspended second that refuses to settle, like the moving target it names |
| Thread | 1/1 5/4 3/2 15/8 5/2 | major and extended — the only objective with a correct *order*, and the only one that sounds resolved |

---

## 2. The pulsar lock — the one load-bearing sound

`Tuning.PULSAR_PERIOD` is 210 ticks, and at `DT = 1/60` that is exactly 3.5
seconds of real gravity oscillation. The loop is **7 seconds**: the smallest
whole number of seconds containing a whole number of pulsar periods (two).

That choice is what makes the sonification survive repetition. Because the
loop is an integer multiple of the pulsar's period, looping forever never
drifts out of lock — the phase relationship survives every seam, for a
25-second flight or a twenty-minute session.

`AmbienceTrack.resyncPulsarPhase()` seeks the track to sample 0 when the shot
fires, and the pulsar's tremolo is built to start at its peak, so **sample
zero is maximum pull**. From then on the swell you hear is the pull the shot
is actually flying through.

**The honest scope of the claim.** The tremolo runs at the pulsar's rate
always. It only tells you the *phase* if playback was restarted at the shot's
first tick, because a free-running loop has no idea when the player fired.
`Ambience.pulsarLocked(spec)` is the guard: true only when the system has a
pulsar, and the app skips the seek entirely when it is false — there is
nothing to learn from locking a system with no pulse, and a seek costs a
pause/play pair an ear can sometimes catch.

This is the first delivery against VISION Stage E.

---

## 3. Why the loop has no seam

A rendered loop clicks at its join unless every component completes a **whole
number of cycles** inside it. So the stored unit is not frequency but
`Voice.cycles` — integer cycles per loop. Frequency is derived (`Voice.hz`),
never stored. Seamlessness is therefore a property of the type rather than a
tuning someone can nudge out of true, and the same holds for every tremolo
(`Voice.lfoCycles`).

`AmbienceTest.theSeamIsContinuousInTheRenderedSamples` checks this the way an
ear would: the step from the last sample to the first must be no larger than
the largest step *inside* the loop.

### And why it is still exactly in tune

Those two requirements normally fight. Snapping frequencies to a loop grid
detunes the intervals; keeping the intervals pure puts the seam back.

They stop fighting if the root is chosen from a divisible grid. Every entry in
`ROOT_CYCLES` is a multiple of 80 (320, 400, 480, 560 cycles → 45.7, 57.1,
68.6, 80.0 Hz), and every denominator in the ratio table divides 80. So
`root × num / den` is always a whole cycle count: exact just intonation *and*
an exact seam, with no rounding anywhere.

Detuning is then a deliberate act rather than an artefact. `DETUNE_CYCLES = 1`
shifts a voice by one cycle per loop — 1/7 Hz — so two voices beat once every
seven seconds, which is exactly one loop, so the beat loops seamlessly too.
Without it a sum of pure tones is dead air.

---

## 4. Cost

| | |
|---|---|
| APK bytes | 0 — nothing is shipped, it is computed |
| Render | ~40 ms desktop for 7 voices × 154,350 samples; roughly 150 ms on a phone, off the main thread, once per system |
| RAM, transient | one float sine table + one float mix buffer, ~1.5 MB, freed after render |
| RAM, resident | the 301 KB PCM loop while a match is on screen |
| CPU while playing | **none**. `MODE_STATIC` hands the whole loop to the audio HAL once and it repeats there |

`MODE_STATIC` is only available *because* the loop is seamless. A loop with a
seam would have needed a crossfading streamer and an application thread to run
it, for the whole match. The mathematics above pays for itself in battery.

The sine table is what keeps the render cheap and exact at once: each voice
advances a whole number of table steps per sample, so the index arithmetic is
integer modular and lands on real table entries forever. No interpolation, no
drift, one transcendental call per sample of table instead of one per sample
per voice.

22.05 kHz is used because nothing in the pad reaches 1.4 kHz — nearly three
octaves below Nyquist. Halving the rate halves the buffer, and a device mixing
at 48 kHz resamples a signal with no high-frequency content to alias.

---

## 5. Listening to it without a device

    ./gradlew :core:ambienceWav

Writes one `.wav` per objective plus a guaranteed-pulsar system to
`core/build/ambience/`, and prints each voice with the body that produced it.

This exists because sound is the one generated thing here that measurement
cannot sign off. A loop can pass every assertion in `AmbienceTest` and still
be unpleasant to sit inside for ten minutes. Until 0.14.0 the seven SFX had
never been heard by anyone, only computed, and that gap existed purely because
auditioning them seemed to need a device. It does not need a device. It needs
a file.

---

## 6. What ambience must never do

It is not part of the determinism contract and must not become part of it. It
is never hashed, never simulated against, never crosses the wire
(docs/DETERMINISM.md). Two devices playing the same duel will hear the same
pad because they generate the same system, but nothing checks that and nothing
should — the day a verification depends on an audio buffer is the day audio
tuning becomes a protocol break.

It also never gates information the player cannot get another way. The pulsar
swell is a *second* channel for something the board already shows visually,
for the same reason the star glyphs carry text labels: a player with the sound
off must lose atmosphere, never ability.

---

## 7. Open questions for the first playtest

- Does a seven-second loop reveal itself as a loop over a five-minute match?
  The seam is provably continuous, but repetition is a different complaint
  from clicking.
- Is the pulsar swell legible as *information*, or does it read as texture?
- Are the four objective colourings distinguishable by ear, or only on paper?
- Is `TARGET_PEAK = 0.30` the right level under the SFX?
- Do the high voices (a light body two octaves up can reach ~480 Hz) sit
  badly on a phone speaker, where there is no bass to balance them?
