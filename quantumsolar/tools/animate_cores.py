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


GLOW = {"ice_heart": np.array([140, 240, 255]), "sun_heart": np.array([255, 226, 130])}


def heart_frame(a, f, top, name="ice_heart"):
    """The hearts. Round the rim the marks go round as on the others. On the top a band of light
    runs along the diagonal stripes. On the front the heart beats: at each throb it lights up and a
    glow flares out round it into the ground and dies away; after each beat a crystal glint sweeps
    across it corner to corner; and here and there in it a sparkle flashes."""
    out = frame_rim(a, f).astype(float)
    if top:
        for y in range(1, 15):
            for x in range(1, 15):
                c = a[y, x].astype(float)
                if c.max() < 70:
                    continue
                k = 0.3 * np.cos(2 * np.pi * (f / FRAMES * WAVES - (x + y) / 16.0))
                out[y, x] = c * (1 + k) if k <= 0 else c + (255 - c) * k
        return out.round().clip(0, 255).astype(np.uint8)
    heart = np.zeros((16, 16), dtype=bool)
    for y in range(1, 15):
        for x in range(1, 15):
            c = a[y, x].astype(float)
            heart[y, x] = c.max() > 175 or (c.max() - c.min()) > 60
    b = beat(f)
    glow = GLOW[name]
    half = FRAMES // 2
    sweep = (f % half) / half * 30 - 6                   # the glint's place along x + y
    sparks = [(5, 5, 0), (10, 6, 11), (7, 9, 22), (9, 11, 37), (4, 7, 48)]
    for y in range(1, 15):
        for x in range(1, 15):
            c = a[y, x].astype(float)
            if heart[y, x]:
                k = 0.25 * b
                if abs((x + y) - sweep) < 1.0:
                    k = max(k, 0.75)
                elif abs((x + y) - sweep) < 2.0:
                    k = max(k, 0.35)
                out[y, x] = c + (255 - c) * k
            else:
                # the glow round it, strongest next to it
                d = min(abs(x - i) + abs(y - j) for j in range(1, 15) for i in range(1, 15) if heart[j, i]) if heart.any() else 99
                if d <= 2:
                    k = (0.75 if d == 1 else 0.35) * b
                    out[y, x] = c * (1 - k) + glow * k
    for x, y, at in sparks:
        if heart[y, x]:
            age = (f - at) % FRAMES
            if age < 4:
                out[y, x] = (255, 255, 255) if age < 2 else out[y, x] * 0.5 + 127
                for i, j in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                    if age < 2 and heart[j, i]:
                        out[j, i] = out[j, i] * 0.5 + np.array([128, 128, 128])
    return out.round().clip(0, 255).astype(np.uint8)


def frame_rim(a, f):
    out = a.copy()
    n = len(RIM)
    step = f * n // FRAMES
    for i, (x, y) in enumerate(RIM):
        sx, sy = RIM[(i - step) % n]
        out[y, x] = a[sy, sx]
    return out


def lighten(c, k):
    return c * (1 + k) if k <= 0 else c + (255 - c) * k


def panel_frame(a, f, style, centre):
    """The flare and tearful panels: flare tops a band of light running across, flare fronts their
    ornament throbbing out from its middle, tearful tops rings of light spreading from the dark
    star, tearful fronts a glint running down the tears and the eyes glowing."""
    out = a.astype(float).copy()
    cx, cy = centre
    t = f / FRAMES * WAVES
    for y in range(1, 15):
        for x in range(1, 15):
            c = a[y, x].astype(float)
            h, sat, v = colorsys.rgb_to_hsv(*(c / 255.0))
            if style == "flare_top":
                if v < 0.3:
                    continue
                k = 0.28 * np.cos(2 * np.pi * (t - (x - y) / 20.0))
            elif style == "flare_side":
                if sat < 0.3 or v < 0.3:
                    continue
                d = max(abs(x - 7.5), abs(y - 7.5))
                k = 0.3 * np.cos(2 * np.pi * (t + d / 6.0))
            elif style == "tearful_top":
                if v < 0.25:
                    continue
                d = np.hypot(x - cx, y - cy)
                k = 0.3 * np.cos(2 * np.pi * (t - d / 7.0))
            else:                                        # tearful front
                if sat < 0.3 or v < 0.25:
                    continue
                if 0.02 < h < 0.13:                       # the brown boards and orange eyes
                    if c[0] > 180:                         # the eyes glow
                        k = 0.3 * np.cos(2 * np.pi * t * 0.5)
                    else:
                        continue
                else:                                     # the tears: a glint running down
                    k = 0.6 * np.exp(-(((y / 16.0 - (t % 1.0)) % 1.0 - 0.5) / 0.08) ** 2) - 0.1
            out[y, x] = lighten(c, k)
    return out.round().clip(0, 255).astype(np.uint8)


def dark_star(a):
    """Where the tearful tops' dark star is: the middle of their darkest pixels inside the frame."""
    lum = a[2:14, 2:14].astype(float).sum(axis=2)
    ys, xs = np.nonzero(lum <= np.percentile(lum, 6))
    return xs.mean() + 2, ys.mean() + 2


PANELS = {"flare": ["sunset", "aurora", "ember", "glyph", "amber", "blaze"],
          "tearful": ["sky", "meadow", "dusk", "sunrise", "spring", "honey", "orchid", "twilight"]}


def animate_panels():
    for fam, names in PANELS.items():
        for name in names:
            for part in ("top", "side"):
                path = f"{T}/{fam}_panel_{name}_{part}.png"
                a = np.asarray(Image.open(path).convert("RGB"))[:16]
                style = f"{fam}_{part}"
                centre = dark_star(a) if style == "tearful_top" else (7.5, 7.5)
                strip = np.concatenate([panel_frame(a, f, style, centre) for f in range(FRAMES)], axis=0)
                Image.fromarray(strip).save(path)
                with open(path + ".mcmeta", "w") as fh:
                    json.dump({"animation": {"frametime": FRAME_TICKS}}, fh)


def main():
    for name in NAMES:
        for part in ("top", "side"):
            path = f"{T}/core_generator_{name}_{part}.png"
            a = np.asarray(Image.open(path).convert("RGB"))
            if a.shape[0] != 16:
                a = a[:16]
            if "heart" in name:
                a = clean_rim(a)
                strip = np.concatenate([heart_frame(a, f, part == "top", name) for f in range(FRAMES)], axis=0)
            else:
                a = clean_rim(a)
                strip = np.concatenate([frame(a, f) for f in range(FRAMES)], axis=0)
            Image.fromarray(strip).save(path)
            with open(path + ".mcmeta", "w") as fh:
                json.dump({"animation": {"frametime": FRAME_TICKS}}, fh)


if __name__ == "__main__":
    main()
    animate_panels()
