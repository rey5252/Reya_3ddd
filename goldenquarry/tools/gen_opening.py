"""Draws the vacuum chest GUI's opening and its cracks as pixel art (needs numpy and Pillow).

Run from the goldenquarry folder:  python3 tools/gen_opening.py
- textures/gui/vacuum_chest_galaxy.png: the galaxy the GUI opens out of (after the reference's
  spiral galaxy: a white-gold core, two arms wound round it, blue and ice outside, gold inside, dark
  dust lanes between, stars scattered along them), GALAXY x GALAXY, FRAMES frames in a 4x4 grid,
  turning half a turn in all (its two arms make it look the same after half a turn), dithered.
- textures/gui/vacuum_chest_cracks.png: the frame cracking where a catcher drags its star into
  the chest: a hole broken out at the root, rimmed with ender light, and cracks running from it
  over the frame and the panel, branching; one row per catcher, CRACK_FRAMES frames of them
  growing. The sprite's middle is on the tentacle's root; only what is on the GUI is drawn.
- textures/gui/vacuum_chest_glint.png: a light sweeping over the frame's steel and gold (frames
  stacked, the whole GUI each).
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from gen_tentacles import TENTACLES  # noqa: E402

OUT = "src/main/resources/assets/goldenquarry/textures/gui"
GALAXY, FRAMES = 128, 16
CRACK, CRACK_FRAMES = 72, 10
GLINT_FRAMES = 24
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0 - 0.47

# the galaxy's colours, dark to light, the arms' blue and the core's gold
BLUES = [(8, 10, 40), (14, 26, 86), (24, 52, 150), (42, 96, 206), (86, 156, 240), (160, 214, 255), (230, 246, 255)]
GOLDS = [(60, 30, 20), (120, 64, 26), (186, 116, 46), (231, 170, 80), (250, 214, 140), (255, 240, 200), (255, 255, 240)]


def rng(seed):
    return np.random.default_rng(seed)


def galaxy_frame(turn, stars):
    n = GALAXY
    ys, xs = np.mgrid[0:n, 0:n]
    u, v = (xs - n / 2 + 0.5) / (n / 2), (ys - n / 2 + 0.5) / (n / 2)
    # a little tilted, as seen from above at an angle
    v = v / 0.82
    r = np.hypot(u, v) + 1e-6
    th = np.arctan2(v, u) - turn
    # two logarithmic arms, and a fainter pair between them
    wind = 3.1
    arm = np.zeros_like(r)
    for k, (w, strength) in enumerate(((0.42, 1.0), (0.3, 0.45))):
        off = k * math.pi / 2
        d = np.angle(np.exp(1j * (2 * (th - wind * np.log(r) - off)))) / 2   # nearest of the pair
        arm += strength * np.exp(-(d / (w * (0.6 + r))) ** 2)
    # clumps along the arms (fixed to the galaxy, so they turn with it)
    clump = 0.75 + 0.25 * np.sin(9 * th + 14 * r) * np.sin(5 * th - 23 * r)
    disc = np.clip(1.0 - r, 0, 1) ** 1.3
    core = np.exp(-(r / 0.16) ** 2)
    halo = np.exp(-(r / 0.45) ** 2) * 0.35
    light = arm * clump * disc * 0.95 + core * 1.25 + halo
    # dust lanes: dark just inside each arm
    lane = np.zeros_like(r)
    d2 = np.angle(np.exp(1j * (2 * (th - wind * np.log(r) + 0.55)))) / 2
    lane += np.exp(-(d2 / 0.16) ** 2) * (r > 0.12) * (r < 0.85)
    light = light * (1 - 0.55 * lane)
    # gold inside, blue outside, mixed in between
    goldness = np.clip(1.25 - r * 2.4, 0, 1)
    img = np.zeros((n, n, 4), dtype=np.uint8)
    for y in range(n):
        for x in range(n):
            lv = light[y, x] + BAYER[y % 4, x % 4] * 0.16
            if lv < 0.13 or r[y, x] > 0.97:     # no faint dots out in the square's corners
                continue
            pal = GOLDS if goldness[y, x] + BAYER[(y + 2) % 4, (x + 1) % 4] * 0.5 > 0.5 else BLUES
            i = min(len(pal) - 1, int(lv * (len(pal) - 0.2)))
            a = 255 if lv > 0.2 else 170
            img[y, x] = pal[i] + (a,)
    # stars: white and pale ones along the arms, turning with the galaxy, twinkling
    for (sr, sa, kind, ph) in stars:
        a = sa + turn
        x, y = n / 2 - 0.5 + math.cos(a) * sr * n / 2, n / 2 - 0.5 + math.sin(a) * sr * n / 2 * 0.82
        xi, yi = int(round(x)), int(round(y))
        tw = 0.5 + 0.5 * math.sin(ph + turn * 8)
        col = [(255, 255, 255), (200, 230, 255), (255, 236, 180)][kind]
        if not (1 <= xi < n - 1 and 1 <= yi < n - 1):
            continue
        img[yi, xi] = col + (255,)
        if tw > 0.55:
            arm_col = tuple(int(c * 0.75) for c in col) + (200,)
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                img[yi + dy, xi + dx] = arm_col
            if tw > 0.9 and 2 <= xi < n - 2 and 2 <= yi < n - 2:
                for dx, dy in ((2, 0), (-2, 0), (0, 2), (0, -2)):
                    img[yi + dy, xi + dx] = arm_col[:3] + (120,)
    return img


REFERENCE = "reference/vacuum/galaxy.jpg"
CORE, RADIUS = (373, 323), 312          # the galaxy's bright core in the reference, and how far it reaches
SIGNATURE = (70, 548, 165, 592)         # the painter's signature, left out
COLOURS = 32
HAZE = 32


OPEN_FRAMES = 24                        # the galaxy opening, in a 6 x 4 grid
TWIST = 5.5                             # how tightly its arms are wound at first (radians per e-fold)
WIND = -2.6                             # the reference's own arms: angle per e-fold of radius


def galaxy(twist_sign=1.0):
    """The galaxy opening as drawn over the reference: at first its arms wound tight round the bright
    core, then unwinding outwards along their own spiral to the reference as it is; the arms reach
    out ahead of the dark lanes between them. Each frame's pixels are the reference's (each sprite
    pixel the mean of the reference's pixels it covers), the black sky taken off (alpha from how
    bright it is), faded out towards the rim, all frames in the same COLOURS of the reference."""
    ref = Image.open(REFERENCE).convert("RGB")
    a = np.asarray(ref).copy()
    x0, y0, x1, y1 = SIGNATURE
    a[y0:y1, x0:x1] = 0
    side = 2 * RADIUS
    big = np.zeros((side, side, 3), dtype=np.uint8)
    big_img = Image.new("RGB", (side, side))
    big_img.paste(Image.fromarray(a), (RADIUS - CORE[0], RADIUS - CORE[1]))
    # the reference at 4x the sprite, to sample twisted and then average down
    n4 = GALAXY * 4
    src = np.asarray(big_img.resize((n4, n4), Image.BOX)).astype(float)
    ys, xs = np.mgrid[0:n4, 0:n4]
    u, v = (xs - n4 / 2 + 0.5) / (n4 / 2), (ys - n4 / 2 + 0.5) / (n4 / 2)
    r = np.hypot(u, v) + 1e-6
    th = np.arctan2(v, u)
    frames = []
    for f in range(OPEN_FRAMES):
        p = f / (OPEN_FRAMES - 1)
        ease = 1 - (1 - p) ** 2
        # wind the arms up: each radius turned by more the further out, less as it opens
        twist = twist_sign * TWIST * (1 - ease) * np.log(np.clip(r, 0.03, 1.0))
        sa = th - twist
        sx = (np.cos(sa) * r * (n4 / 2) + n4 / 2 - 0.5).round().astype(int).clip(0, n4 - 1)
        sy = (np.sin(sa) * r * (n4 / 2) + n4 / 2 - 0.5).round().astype(int).clip(0, n4 - 1)
        img = src[sy, sx]
        # how far it has opened: the arms (bright) out ahead of the lanes, along the spiral
        arm = img.max(axis=2) / 255.0
        edge = 0.08 + ease * 1.1 * (0.7 + 0.3 * arm) + 0.08 * np.cos(2 * (th - twist - WIND * np.log(r)))
        reveal = np.clip((edge - r) / 0.12, 0, 1)
        img = img * reveal[..., None]
        small = img.reshape(GALAXY, 4, GALAXY, 4, 3).mean(axis=(1, 3))
        light = np.clip(small - HAZE, 0, None)
        rr = r.reshape(GALAXY, 4, GALAXY, 4).mean(axis=(1, 3))
        fade = np.clip((1.0 - rr) / 0.18, 0, 1)
        alpha = np.clip((light.max(axis=2) - 12) / 110.0, 0, 1) * fade
        colour = np.clip(light * 1.12 / np.maximum(np.minimum(alpha[..., None] * 1.6, 1.0), 0.45), 0, 255)
        frames.append((colour, alpha))
    strip = np.concatenate([c for c, al in frames], axis=1).astype(np.uint8)
    pal_img = Image.fromarray(strip).quantize(COLOURS, method=Image.Quantize.MEDIANCUT)
    sheet = np.zeros((GALAXY * 4, GALAXY * 6, 4), dtype=np.uint8)
    for f, (colour, alpha) in enumerate(frames):
        q = np.asarray(Image.fromarray(colour.astype(np.uint8)).quantize(palette=pal_img, dither=Image.Dither.NONE).convert("RGB"))
        al = np.select([alpha < 0.1, alpha < 0.3, alpha < 0.55], [0, 110, 190], 255).astype(np.uint8)
        sheet[(f // 6) * GALAXY:(f // 6 + 1) * GALAXY, (f % 6) * GALAXY:(f % 6 + 1) * GALAXY] = np.dstack([q, al])
    Image.fromarray(sheet, "RGBA").save(f"{OUT}/vacuum_chest_galaxy.png")


GUI_TEX = f"{OUT}/vacuum_chest.png"
PANEL = (12, 20, 204, 157)          # the open panel (the vortex under it): x0, y0, x1, y1 exclusive
GLINT_H = 170                        # the glint runs over the frame, down to the steps


def on_gui(tex):
    """Where the GUI is: its drawn pixels and the open panel."""
    a = tex[:, :, 3] > 0
    x0, y0, x1, y1 = PANEL
    a[y0:y1, x0:x1] = True
    return a


def crack_paths(g, dx, dy):
    """The cracks from the root: jagged branching lines, each pixel with how far along it is."""
    pix = {}
    todo = []
    base = math.atan2(-dy, -dx)                 # into the chest
    for k in range(6):
        todo.append((0.0, 0.0, base + (k - 2.5) * 0.55 + g.normal(0, 0.15), 0.0, g.uniform(20, 32), 0))
    while todo:
        x, y, a, dist, length, depth = todo.pop()
        run = 0
        while dist < length:
            x += math.cos(a)
            y += math.sin(a)
            dist += 1
            run += 1
            if run >= g.integers(2, 4):          # a jag every few pixels
                a += g.normal(0, 0.55)
                run = 0
            p = (int(round(x)), int(round(y)))
            if p not in pix or pix[p] > dist:
                pix[p] = dist
            if depth < 2 and dist > 3 and g.random() < 0.12:
                todo.append((x, y, a + g.choice([-1, 1]) * g.uniform(0.5, 1.1), dist, dist + (length - dist) * 0.6, depth + 1))
    return pix


def cracks():
    tex = np.array(Image.open(GUI_TEX).convert("RGBA"))
    mask = on_gui(tex)
    catchers = [t for t in TENTACLES if t[11]]
    sheet = np.zeros((CRACK * len(catchers), CRACK * CRACK_FRAMES, 4), dtype=np.uint8)
    c = CRACK // 2
    for row, t in enumerate(catchers):
        rx, ry, dx, dy = int(t[0]), int(t[1]), t[2], t[3]
        g = rng(100 + row)
        paths = crack_paths(g, dx, dy)
        longest = max(paths.values())
        hole = {}
        for j in range(-7, 8):
            for i in range(-7, 8):
                a = math.atan2(j, i)
                hole[(i, j)] = math.hypot(i, j) / (1.0 + 0.35 * math.sin(3 * a + row) + 0.2 * math.sin(5 * a))
        chips = [(int(g.integers(-11, 12)), int(g.integers(-11, 12))) for _ in range(14)]
        for f in range(CRACK_FRAMES):
            grow = (f + 1) / CRACK_FRAMES
            out = np.zeros((CRACK, CRACK, 4), dtype=np.uint8)

            def put(p, col, a=255, over=True):
                x, y = p[0] + c, p[1] + c
                gx, gy = rx + p[0], ry + p[1]
                if not (0 <= x < CRACK and 0 <= y < CRACK and 0 <= gx < tex.shape[1] and 0 <= gy < tex.shape[0]):
                    return
                if not mask[gy, gx] or (not over and out[y, x, 3]):
                    return
                out[y, x] = col + (a,)

            reach = longest * grow
            shown = [(p, d) for p, d in paths.items() if d <= reach]
            # round each crack: ender light glowing out of it below, the broken edge lit above
            for (x, y), d in shown:
                for (i, j) in ((0, 1), (1, 0), (1, 1)):
                    put((x + i, y + j), (170, 90, 255), 150, over=False)
                for (i, j) in ((0, -1), (-1, 0)):
                    put((x + i, y + j), (236, 226, 250), 170, over=False)
            for (x, y), d in shown:
                near = d / max(1.0, reach)
                if d < 5 * grow + 1:
                    col = (230, 170, 255) if (x + y) % 2 else (180, 90, 255)    # bright where it broke
                elif near < 0.7:
                    col = (8, 0, 16)
                else:
                    col = (40, 14, 66)                                          # the thin end, fainter
                put((x, y), col)
                if d < 9 * grow and near < 0.5:                                 # wider near the hole
                    put((x + 1, y), (8, 0, 16))
            # chips knocked loose round the hole: little light shards with a dark underside
            for i, (x, y) in enumerate(chips[:int(len(chips) * grow)]):
                if math.hypot(x, y) < 3:
                    continue
                put((x, y), (200, 196, 222), 230)
                put((x + 1, y), (120, 110, 150), 230)
                put((x, y + 1), (40, 20, 60), 200)
            # the hole, broken out and rimmed with ender light
            rad = 1.5 + 3.5 * min(1.0, grow * 1.6)
            for p, h in hole.items():
                if h <= rad - 1.2:
                    put(p, (6, 0, 14))
                elif h <= rad - 0.4:
                    put(p, (120, 40, 210))
                elif h <= rad + 0.3:
                    put(p, (226, 180, 255), 230)
            sheet[row * CRACK:(row + 1) * CRACK, f * CRACK:(f + 1) * CRACK] = out
    Image.fromarray(sheet, "RGBA").save(f"{OUT}/vacuum_chest_cracks.png")


def glint():
    """A soft band of light running down over the frame from the top left, one frame each."""
    tex = np.array(Image.open(GUI_TEX).convert("RGBA"))[:GLINT_H]
    h, w = tex.shape[:2]
    shiny = tex[:, :, 3] > 0
    x0, y0, x1, y1 = PANEL
    inner = np.zeros_like(shiny)
    inner[y0 + 1:y1 - 1, x0 + 1:x1 - 1] = True
    shiny &= ~inner
    ys, xs = np.mgrid[0:h, 0:w]
    s = xs + ys * 0.7
    sheet = np.zeros((h * GLINT_FRAMES, w, 4), dtype=np.uint8)
    for f in range(GLINT_FRAMES):
        centre = -20 + (w + h * 0.7 + 40) * f / (GLINT_FRAMES - 1)
        k = np.exp(-((s - centre) / 5.0) ** 2) + 0.5 * np.exp(-((s - centre + 11) / 2.0) ** 2)
        a = np.where(shiny, np.clip(k * 190, 0, 255), 0).astype(np.uint8)
        a[a < 20] = 0
        frame = np.zeros((h, w, 4), dtype=np.uint8)
        frame[:, :, :3] = (255, 250, 230)
        frame[:, :, 3] = a
        sheet[f * h:(f + 1) * h] = frame
    Image.fromarray(sheet, "RGBA").save(f"{OUT}/vacuum_chest_glint.png")


if __name__ == "__main__":
    galaxy()
    cracks()
    glint()
