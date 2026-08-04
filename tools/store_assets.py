#!/usr/bin/env python3
"""Generates Play Store listing graphics from the app's icon geometry.

Outputs (git-tracked in store/):
  store/icon-512.png          — Play Store icon (512x512, matches adaptive icon)
  store/feature-1024x500.png  — feature graphic per docs/STORE_LISTING.md

Flat vector style on the app palette; supersampled 4x for clean edges.
Deterministic (fixed seed) so re-runs are reproducible.
"""
from PIL import Image, ImageDraw, ImageFont
import math, random, os

SS = 4  # supersampling factor
BG_ICON = (11, 16, 33)      # #0B1021
BG_FEAT = (7, 11, 24)       # #070B18
CYAN = (77, 208, 225)       # #4DD0E1
PURPLE = (124, 77, 255)     # #7C4DFF
YELLOW = (255, 213, 79)     # #FFD54F
ORANGE = (255, 143, 0)      # #FF8F00
WHITE = (255, 255, 255)
TEXT_DIM = (142, 154, 198)

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
OUT = os.path.join(os.path.dirname(__file__), "..", "store")


def radial_circle(draw, cx, cy, r, inner, outer, steps=64):
    """Cheap radial gradient: concentric circles interpolating inner->outer."""
    for i in range(steps, 0, -1):
        f = i / steps
        col = tuple(int(inner[c] + (outer[c] - inner[c]) * f) for c in range(3))
        draw.ellipse([cx - r * f, cy - r * f, cx + r * f, cy + r * f], fill=col)


def glow(img, cx, cy, r, color, peak_alpha):
    """Soft additive glow layer."""
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    steps = 48
    for i in range(steps, 0, -1):
        f = i / steps
        a = int(peak_alpha * (1 - f) ** 2)
        d.ellipse([cx - r * f, cy - r * f, cx + r * f, cy + r * f], fill=color + (a,))
    img.alpha_composite(overlay)


def draw_star(img, draw, cx, cy, r):
    glow(img, cx, cy, r * 2.8, YELLOW, 110)
    radial_circle(draw, cx, cy, r, WHITE, ORANGE)


def draw_planet(draw, cx, cy, r, light, dark):
    # Offset-light gradient approximation: dark base + shifted lighter discs.
    steps = 40
    for i in range(steps, 0, -1):
        f = i / steps
        col = tuple(int(light[c] + (dark[c] - light[c]) * f) for c in range(3))
        off = r * 0.35 * (1 - f)
        draw.ellipse([cx - r * f - off, cy - r * f - off,
                      cx + r * f - off, cy + r * f - off], fill=col)


def icon():
    S = 512 * SS
    img = Image.new("RGBA", (S, S), BG_ICON + (255,))
    d = ImageDraw.Draw(img)
    k = S / 108.0  # adaptive-icon viewport -> pixels
    cx = cy = 54 * k

    draw_star(img, d, cx, cy, 10 * k)
    # Orbit ring.
    rr = 26 * k
    d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], outline=CYAN, width=int(2.5 * k))
    # Planet on the ring (right).
    draw_planet(d, 80 * k, 54 * k, 5.5 * k, (180, 150, 255), PURPLE)
    # Comet: tail then head (upper left, matching the vector).
    d.polygon([(36 * k, 28 * k), (24 * k, 21 * k), (33 * k, 34 * k)],
              fill=CYAN + (102,))
    d.ellipse([(37 - 3.2) * k, (31 - 3.2) * k, (37 + 3.2) * k, (31 + 3.2) * k], fill=WHITE)

    img = img.resize((512, 512), Image.LANCZOS).convert("RGB")
    img.save(os.path.join(OUT, "icon-512.png"))


def trail_arc(img, draw, cx, cy, r_orbit, t0, t1, n=90):
    """Slingshot trail: dots along a partial orbit, fading in size/alpha."""
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    for i in range(n):
        f = i / (n - 1)
        ang = t0 + (t1 - t0) * f
        # Spiral out slightly so it reads as a flyby, not a closed orbit.
        r = r_orbit * (1.0 + 0.55 * (1 - f))
        x = cx + r * math.cos(ang)
        y = cy + r * math.sin(ang)
        a = int(30 + 200 * f)
        rad = (2.0 + 4.5 * f) * SS
        d.ellipse([x - rad, y - rad, x + rad, y + rad], fill=CYAN + (a,))
    img.alpha_composite(overlay)
    return x, y  # head position


def spaced_text(draw, xy, text, font, fill, tracking):
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, font=font, fill=fill)
        x += draw.textlength(ch, font=font) + tracking
    return x


def feature():
    W, H = 1024 * SS, 500 * SS
    img = Image.new("RGBA", (W, H), BG_FEAT + (255,))
    d = ImageDraw.Draw(img)

    rng = random.Random(2032)
    for _ in range(140):
        x, y = rng.uniform(0, W), rng.uniform(0, H)
        r = rng.uniform(0.6, 2.2) * SS
        a = rng.randint(40, 150)
        d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, a))

    # Planet lower-right with a slingshot trail whipping around it.
    pcx, pcy, pr = W * 0.78, H * 0.62, 120 * SS
    glow(img, pcx, pcy, pr * 1.6, PURPLE, 60)
    d = ImageDraw.Draw(img)
    draw_planet(d, pcx, pcy, pr, (180, 150, 255), (58, 30, 140))
    hx, hy = trail_arc(img, d, pcx, pcy, pr * 1.45, math.pi * 0.75, math.pi * 2.05)
    d = ImageDraw.Draw(img)
    # Projectile head at trail end.
    glow(img, hx, hy, 26 * SS, CYAN, 160)
    d = ImageDraw.Draw(img)
    hr = 7 * SS
    d.ellipse([hx - hr, hy - hr, hx + hr, hy + hr], fill=WHITE)

    # Small star upper-left for depth.
    draw_star(img, d, W * 0.12, H * 0.18, 26 * SS)

    # Title, app treatment: GRAVITY white / GAMBIT cyan, wide tracking.
    f_big = ImageFont.truetype(FONT, 78 * SS)
    f_sub = ImageFont.truetype(FONT, 24 * SS)
    tracking = 16 * SS
    tx, ty = W * 0.065, H * 0.30
    spaced_text(d, (tx, ty), "GRAVITY", f_big, WHITE, tracking)
    spaced_text(d, (tx, ty + 92 * SS), "GAMBIT", f_big, CYAN, tracking)
    spaced_text(d, (tx + 2 * SS, ty + 196 * SS),
                "REAL GRAVITY · PVP BY CODE · NO SERVERS", f_sub, TEXT_DIM, 6 * SS)

    img = img.resize((1024, 500), Image.LANCZOS).convert("RGB")
    img.save(os.path.join(OUT, "feature-1024x500.png"))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    icon()
    feature()
    print("wrote store/icon-512.png, store/feature-1024x500.png")
