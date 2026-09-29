"""Makes the core generators' textures move (run by tools/build.py after it has written them).

Each face becomes a strip of FRAMES frames (with a .png.mcmeta): waves of light run over the rings
into the middle, the middle glows and dims, and the coloured marks round the rim go slowly round.
The dark pixels stay dark, so the picture stays the original's. Before that, what the photo of the
originals gave the rim from the grass and earth round them (green or brown pixels) is taken from
the rim's other side.
"""
import colorsys
import json

import numpy as np
from PIL import Image

T = "src/main/resources/assets/quantumsolar/textures/block"
NAMES = ["ice_heart", "sun_heart", "ruby", "jade", "topaz", "amethyst"]
FRAMES, FRAME_TICKS = 60, 2
WAVES = 3            # waves over the rings in one round of the rim


def stray(c):
    h, s, v = colorsys.rgb_to_hsv(*(c / 255.0))
    green = 0.15 < h < 0.4 and s > 0.3
    earth = h < 0.12 and s > 0.25 and v < 0.45 and not (c[0] > 1.6 * c[2] and c[0] > 90)
    return green or earth


def clean_rim(a):
    out = a.copy()
    for y in range(16):
        for x in range(16):
            if min(x, y, 15 - x, 15 - y) > 1 or not stray(a[y, x].astype(float)):
                continue
            for mx, my in ((15 - x, y), (x, 15 - y), (15 - x, 15 - y), (y, x)):
                if not stray(a[my, mx].astype(float)):
                    out[y, x] = a[my, mx]
                    break
    # the ring inside the rim is plain dark in the originals: a pixel there unlike its mirror
    # images, which agree with each other, came from round the block too
    for y in range(1, 15):
        for x in range(1, 15):
            if max(abs(x - 7.5), abs(y - 7.5)) != 6.5:
                continue
            p, m1, m2 = (out[y, x].astype(float), out[y, 15 - x].astype(float), out[15 - y, x].astype(float))
            if np.abs(m1 - m2).sum() < 40 and np.abs(p - m1).sum() > 50:
                out[y, x] = out[y, 15 - x]
    return out


RIM = [(x, 0) for x in range(16)] + [(15, y) for y in range(1, 16)] + \
      [(x, 15) for x in range(14, -1, -1)] + [(0, y) for y in range(14, 0, -1)]


def frame(a, f):
    out = a.astype(float).copy()
    # the rim's marks go round: each rim pixel takes the one f steps behind it
    n = len(RIM)
    step = f * n // FRAMES
    for i, (x, y) in enumerate(RIM):
        sx, sy = RIM[(i - step) % n]
        out[y, x] = a[sy, sx]
    # waves of light into the middle, on the lit pixels only
    for y in range(1, 15):
        for x in range(1, 15):
            c = a[y, x].astype(float)
            if c.max() < 70:
                continue
            d = max(abs(x - 7.5), abs(y - 7.5))
            phase = f / FRAMES * WAVES + d / 7.0
            k = 1.0 + 0.22 * np.cos(2 * np.pi * phase)
            if d <= 1.5:
                k = 1.0 + 0.35 * np.cos(2 * np.pi * f / FRAMES * WAVES)
            out[y, x] = c * k if k <= 1.0 else c + (255 - c) * (k - 1.0) * 0.9
    return out.round().clip(0, 255).astype(np.uint8)


def beat(f):
    """A heartbeat over the round: two quick throbs, then rest (twice a round)."""
    t = (f / FRAMES * 2) % 1.0
    return max(np.exp(-((t - 0.08) / 0.05) ** 2), 0.7 * np.exp(-((t - 0.26) / 0.05) ** 2))


def heart_frame(a, f, top):
    """The hearts: the rim's marks go round as on the others; on the front the heart throbs, on the
    top a band of light runs along the diagonal stripes."""
    out = frame_rim(a, f).astype(float)
    for y in range(1, 15):
        for x in range(1, 15):
            c = a[y, x].astype(float)
            if c.max() < 70:
                continue
            if top:
                k = 0.3 * np.cos(2 * np.pi * (f / FRAMES * WAVES - (x + y) / 16.0))
            else:
                k = 0.35 * beat(f) - 0.08
            out[y, x] = c * (1 + k) if k <= 0 else c + (255 - c) * k
    return out.round().clip(0, 255).astype(np.uint8)


def frame_rim(a, f):
    out = a.copy()
    n = len(RIM)
    step = f * n // FRAMES
    for i, (x, y) in enumerate(RIM):
        sx, sy = RIM[(i - step) % n]
        out[y, x] = a[sy, sx]
    return out


def main():
    for name in NAMES:
        for part in ("top", "side"):
            path = f"{T}/core_generator_{name}_{part}.png"
            a = np.asarray(Image.open(path).convert("RGB"))
            if a.shape[0] != 16:
                a = a[:16]
            if "heart" in name:
                a = clean_rim(a)
                strip = np.concatenate([heart_frame(a, f, part == "top") for f in range(FRAMES)], axis=0)
            else:
                a = clean_rim(a)
                strip = np.concatenate([frame(a, f) for f in range(FRAMES)], axis=0)
            Image.fromarray(strip).save(path)
            with open(path + ".mcmeta", "w") as fh:
                json.dump({"animation": {"frametime": FRAME_TICKS}}, fh)


if __name__ == "__main__":
    main()
