#!/usr/bin/env python3
"""Synthesizes the game's sound effects from pure math — no licensed assets.

Outputs 16-bit mono 22050Hz WAVs into app/src/main/res/raw/. Deterministic
(fixed seeds) so re-runs are reproducible; restyle by editing parameters.
Design intent: short, quiet, low-register "physical" sounds — nothing shrill.
"""
import math, os, random, struct, wave

SR = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
MASTER = 0.55  # conservative peak; players' ears > presence


def write(name, samples):
    path = os.path.join(OUT, name)
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = b"".join(
            struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767 * MASTER))
            for s in samples
        )
        w.writeframes(frames)
    print("wrote", os.path.relpath(path))


def envelope(i, attack_s, decay):
    t = i / SR
    attack = min(1.0, t / attack_s) if attack_s > 0 else 1.0
    return attack * math.exp(-decay * t)


def sweep(dur, f0, f1, decay, noise=0.0, seed=1, attack=0.004, shape=math.sin):
    """Sine sweep f0->f1 with optional noise layer."""
    n = int(dur * SR)
    rng = random.Random(seed)
    out, phase = [], 0.0
    for i in range(n):
        t = i / SR
        f = f0 + (f1 - f0) * (t / dur)
        phase += 2 * math.pi * f / SR
        s = shape(phase)
        if noise > 0:
            s += noise * (rng.random() * 2 - 1) * math.exp(-16 * t)
        out.append(s * envelope(i, attack, decay))
    return out


def notes(seq, note_dur, decay=7.0, gap=0.0):
    out = []
    for f in seq:
        n = int(note_dur * SR)
        phase = 0.0
        for i in range(n):
            phase += 2 * math.pi * f / SR
            out.append(math.sin(phase) * envelope(i, 0.006, decay))
        out.extend([0.0] * int(gap * SR))
    return out


def mix(a, b):
    n = max(len(a), len(b))
    return [(a[i] if i < len(a) else 0) + (b[i] if i < len(b) else 0) for i in range(n)]


# Launch: soft "thoomp" — fast downward sweep + a breath of noise.
write("sfx_fire.wav", sweep(0.26, 760, 130, decay=11, noise=0.55, seed=11))

# Station hit: low boom with a sub layer and gentle clip for weight.
boom = mix(
    sweep(0.55, 150, 52, decay=5.5, noise=0.6, seed=22),
    sweep(0.55, 75, 40, decay=4.5, seed=23),
)
write("sfx_hit_station.wav", [max(-0.9, min(0.9, s * 0.9)) for s in boom])

# Planet impact: duller, shorter thud.
write("sfx_hit_planet.wav", sweep(0.32, 210, 80, decay=8.5, noise=0.35, seed=33))

# Retro burn: rising hiss, cut short — a thruster spitting against velocity.
n = int(0.22 * SR)
rng = random.Random(44)
burn = []
for i in range(n):
    t = i / SR
    rise = min(1.0, t / 0.05)
    burn.append((rng.random() * 2 - 1) * rise * math.exp(-7 * t) * 0.8)
write("sfx_brake.wav", burn)

# Style bonus: small two-note chime, understated.
write("sfx_style.wav", notes([880.0, 1318.5], 0.16, decay=9.0))

# Victory: three rising notes.
write("sfx_win.wav", notes([523.25, 659.25, 783.99], 0.19, decay=6.5))

# Defeat: two falling notes, soft.
write("sfx_lose.wav", notes([392.0, 293.66], 0.24, decay=5.5))
