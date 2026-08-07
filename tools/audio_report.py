#!/usr/bin/env python3
"""
Measure the shipped SFX, and build a pad+SFX mix to judge the balance by ear.

Two jobs, both aimed at the one question no assertion can answer — does the
audio sound right?

1. **Report** every `res/raw/sfx_*.wav`: duration, peak, RMS, clipped samples,
   trailing silence. These catch the failures that are objective — a silent
   cue, a clipped one, one that outlives the moment it marks, one mixed far
   from its siblings.

2. **Mix** a generated ambience pad with real SFX laid over it at the exact
   volumes `GameScreen` passes to `SoundPool`. This is the actual answer to
   docs/AUDIO.md §7's open question, "is the pad too quiet or too loud under
   the SFX", which cannot be judged from two files played separately.

Usage:
    ./gradlew :core:ambienceWav          # produce the pads first
    python3 tools/audio_report.py        # report + write the balance mix

Nothing here ships; it writes into build/ and prints.
"""

import glob
import math
import os
import struct
import sys
import wave

RAW = "app/src/main/res/raw"
PADS = "core/build/ambience"
OUT = "build/audio"

# The volumes GameScreen actually passes, and roughly when they land in a turn.
# Kept in this file rather than inferred, so a change in GameScreen that is not
# mirrored here shows up as a mix that no longer matches the game.
EVENTS = [
    ("sfx_fire.wav", 1.00, 0.6),
    ("sfx_hit_planet.wav", 0.80, 2.1),
    ("sfx_style.wav", 0.60, 2.6),
    ("sfx_brake.wav", 1.00, 4.0),
    ("sfx_hit_station.wav", 1.00, 5.2),
    ("sfx_win.wav", 0.80, 6.0),
]

# A cue quieter than this much below the loudest one is probably a mistake
# rather than a choice — see the sfx_brake finding in docs/AUDIO.md §4.
QUIET_FLAG_DB = 4.0


def read(path):
    with wave.open(path) as w:
        frames, rate = w.getnframes(), w.getframerate()
        raw = w.readframes(frames)
    return list(struct.unpack("<" + "h" * (len(raw) // 2), raw)), rate


def write(path, samples, rate):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(struct.pack("<" + "h" * len(samples), *samples))


def db(v):
    return 20 * math.log10(v / 32768.0) if v > 0 else float("-inf")


def report():
    rows = []
    for path in sorted(glob.glob(os.path.join(RAW, "sfx_*.wav"))):
        s, rate = read(path)
        if not s:
            print(f"FAIL: {path} is empty")
            return None, 1
        peak = max(abs(v) for v in s)
        rms = math.sqrt(sum(v * v for v in s) / len(s))
        clipped = sum(1 for v in s if abs(v) >= 32700)
        tail = 0
        for v in reversed(s):
            if abs(v) > 200:
                break
            tail += 1
        rows.append((os.path.basename(path), len(s) / rate, rate, peak, rms, clipped, tail / rate))

    print(f"{'file':22s}{'secs':>6}{'rate':>7}{'peak dB':>9}{'rms':>7}{'clip':>6}{'tail s':>8}")
    for name, secs, rate, peak, rms, clipped, tail in rows:
        print(f"{name:22s}{secs:6.2f}{rate:7d}{db(peak):9.1f}{rms:7.0f}{clipped:6d}{tail:8.2f}")

    problems = 0
    loudest = max(r[3] for r in rows)
    for name, _, _, peak, _, clipped, tail in rows:
        if clipped:
            print(f"  ! {name}: {clipped} clipped samples")
            problems += 1
        if tail > 0.08:
            print(f"  ! {name}: {tail:.2f}s of trailing silence — the cue outlives its moment")
            problems += 1
        gap = db(loudest) - db(peak)
        if gap > QUIET_FLAG_DB:
            print(f"  ? {name}: {gap:.1f} dB below the loudest cue — deliberate, or a mixing slip?")
    return rows, problems


def balance_mix():
    pads = sorted(glob.glob(os.path.join(PADS, "*.wav")))
    if not pads:
        print(f"\nno pads in {PADS} — run ./gradlew :core:ambienceWav first")
        return
    # Capture if present: it is the objective whose pad a player sits inside
    # longest, so it is the fairest test of the balance.
    pad_path = next((p for p in pads if "capture" in p), pads[0])
    pad, rate = read(pad_path)

    mix = list(pad)
    for name, vol, at in EVENTS:
        path = os.path.join(RAW, name)
        if not os.path.exists(path):
            print(f"  ! {name} missing; skipped in the mix")
            continue
        s, srate = read(path)
        if srate != rate:
            print(f"  ! {name} is {srate}Hz against a {rate}Hz pad; skipped rather than resampled")
            continue
        off = int(at * rate)
        for i, v in enumerate(s):
            j = off + i
            if j < len(mix):
                mix[j] = max(-32768, min(32767, mix[j] + int(v * vol)))

    out = os.path.join(OUT, "balance-check-pad-plus-sfx.wav")
    write(out, mix, rate)
    peak = max(abs(v) for v in mix)
    print(f"\nbalance mix: {out}  (pad {os.path.basename(pad_path)}, peak {db(peak):.1f} dBFS)")
    if peak >= 32700:
        print("  ! the combined mix clips — the pad and the cues cannot both be this loud")


if __name__ == "__main__":
    rows, problems = report()
    if rows is None:
        sys.exit(1)
    balance_mix()
    print("\nNothing above can tell you whether it sounds good. Listen (docs/AUDIO.md §7).")
    sys.exit(1 if problems else 0)
