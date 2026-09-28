"""Draws the tentacles round the vacuum chest's GUI, frame by frame, as pixel art (needs numpy and Pillow).

Run from the goldenquarry folder:  python3 tools/gen_tentacles.py
After reference/vacuum/tentacles.jpg: the back purple and glossy, with dark spots; the underside
pink with round suckers (a light rim, lit on its upper side, round a dark hole, set off by a darker
ring) that get smaller towards the tip; the tip curled round towards the underside; a dark
outline. No two alike: three colourings (violet, magenta, indigo), and each its own thickness,
suckers, sway and curl. Each tentacle sways slowly (the waves run in to the chest), is drawn in
and reaches out again, and its suckers and spots move with its skin. Some catch stars: the star
sits in the curl of the tip while it is drawn in.

Writes textures/gui/vacuum_chest_tentacles.png: one row of FRAMES sprites (SIZE x SIZE) per
tentacle, the tentacle's root at (SIZE/2 - REACH*dx, SIZE/2 - REACH*dy) of its sprite;
textures/gui/vacuum_chest_stars.png: three kinds of twinkling star in three colours (rows), eight
frames each (11x11); and the tentacles' phases and the catchers' grip points (where the caught
star sits, in each frame) into client/Tentacles.java, between its "generated" marks.
"""
import math
import re

import numpy as np
from PIL import Image

OUT = "src/main/resources/assets/goldenquarry/textures/gui"
JAVA = "src/main/java/com/reya/goldenquarry/client/Tentacles.java"
SIZE, FRAMES, REACH = 96, 32, 40
WIDTH = 216                   # the GUI's width (VacuumChestMenu.WIDTH)
R = WIDTH - 1
# root x, root y (GUI pixels), direction out, length, phase, which way the tip curls (+1 or -1),
# colouring, thickness, sucker size, sway, catches stars
TENTACLES = [
    (6, 14, -0.75, -0.66, 54, 0.0, 1, 0, 1.0, 1.15, 0.45, True),
    (34, 13, -0.2, -0.98, 40, 1.7, -1, 1, 0.8, 1.0, 0.55, False),
    (4, 48, -0.98, -0.2, 40, 3.1, 1, 2, 0.85, 1.1, 0.35, False),
    (4, 104, -0.97, 0.25, 46, 5.0, -1, 0, 0.95, 1.2, 0.5, True),
    (9, 152, -0.8, 0.6, 52, 3.6, 1, 2, 1.05, 1.1, 0.4, False),
    (18, 160, -0.4, 0.92, 40, 0.4, -1, 1, 0.8, 1.0, 0.55, False),
    (R - 6, 14, 0.75, -0.66, 54, 2.3, -1, 1, 1.0, 1.1, 0.4, False),
    (R - 34, 13, 0.2, -0.98, 40, 4.2, 1, 0, 0.85, 1.15, 0.5, True),
    (R - 4, 48, 0.98, -0.2, 40, 0.9, -1, 2, 0.8, 1.0, 0.45, False),
    (R - 4, 104, 0.97, 0.25, 46, 1.2, 1, 1, 0.95, 1.2, 0.35, False),
    (R - 9, 152, 0.8, 0.6, 52, 2.8, -1, 0, 1.05, 1.15, 0.5, True),
    (R - 18, 160, 0.4, 0.92, 40, 5.6, 1, 2, 0.8, 1.0, 0.45, False)]

OUTLINE = (20, 2, 40)
# the colourings: back (edge .. middle), gloss, shine, spot; underside, its edge, sucker rim (lit,
# shaded), hole, deep hole. The first is read off the reference (its main clusters).
PALETTES = [
    dict(back=[(46, 9, 85), (77, 25, 128), (108, 42, 173), (135, 62, 206)], gloss=(178, 121, 212), shine=(241, 193, 239),
         spot=(46, 9, 85), pink=(218, 72, 168), edge=(170, 34, 122), rim=(255, 176, 232), rim_shade=(232, 112, 194),
         hole=(144, 36, 108), deep=(74, 5, 56)),
    dict(back=[(64, 8, 72), (104, 20, 112), (146, 36, 150), (178, 62, 180)], gloss=(214, 120, 214), shine=(250, 200, 245),
         spot=(60, 8, 70), pink=(236, 88, 156), edge=(186, 44, 116), rim=(255, 186, 222), rim_shade=(240, 124, 180),
         hole=(150, 30, 96), deep=(80, 6, 50)),
    dict(back=[(28, 12, 78), (50, 26, 122), (76, 44, 168), (104, 68, 204)], gloss=(150, 130, 230), shine=(220, 210, 255),
         spot=(28, 12, 78), pink=(200, 84, 196), edge=(150, 48, 156), rim=(244, 178, 244), rim_shade=(214, 118, 214),
         hole=(120, 34, 120), deep=(62, 8, 68)),
]


def frame(root, direction, length, phase, curl, pal, thick, sucker, sway, t):
    """One frame (t from 0 to 1) of a tentacle's loop: it sways (the waves run in), is drawn in and
    reaches out again, and once a loop snaps at the air as if grabbing at something."""
    snap = math.exp(-((((t - 0.62 + phase / 17.0) % 1.0) - 0.5) / 0.035) ** 2)
    reach = length * (0.7 + 0.3 * (0.5 + 0.5 * math.sin(2 * math.pi * t + phase)) + 0.1 * snap)
    return draw(root, direction, length, reach, curl, pal, thick, sucker,
                lambda f: sway * math.sin(2 * math.pi * (0.8 * f) + 2 * math.pi * 2 * t + phase) * (0.2 + 0.8 * f),
                2.1 + 0.35 * math.sin(2 * math.pi * t + phase) + 0.9 * snap, phase)


def draw(root, direction, length, reach, curl, pal, thick, sucker, bend, hook_amount, seed=0.0):
    """A tentacle reaching `reach` out of its root, swayed by bend(f) along it (f 0 at the root, 1 at
    the tip) and its tip curled hook_amount (radians, towards the underside): an RGBA array
    SIZE x SIZE, and the grip point (where a caught star sits in the curl of its tip)."""
    if reach < 1.0:
        return np.zeros((SIZE, SIZE, 4), np.uint8), (int(root[0]), int(root[1]))
    rx, ry = root
    th0 = math.atan2(direction[1], direction[0])
    pulled = length - reach                         # how far its skin has gone in
    ds = 0.25
    s_values = np.arange(-6.0, reach + ds, ds)
    pts, normals, widths, sig = [], [], [], []
    x, y = rx - 6.0 * math.cos(th0), ry - 6.0 * math.sin(th0)
    for s in s_values:
        f = min(1.0, max(0.0, s) / reach)
        th = th0 + bend(f) + curl * hook_amount * f ** 3
        pts.append((x, y))
        normals.append((-math.sin(th), math.cos(th)))
        widths.append((2.2 + 10.5 * (1.0 - f) ** 0.75) * thick)
        sig.append(s + pulled)
        x += math.cos(th) * ds
        y += math.sin(th) * ds
    pts, normals, widths, sig = map(np.array, (pts, normals, widths, sig))
    yy, xx = np.mgrid[0:SIZE, 0:SIZE]
    q = np.stack([xx + 0.5, yy + 0.5], -1).reshape(-1, 2)
    d2 = ((q[:, None, :] - pts[None, :, :]) ** 2).sum(-1)
    j = d2.argmin(1)
    rel = q - pts[j]
    lateral = (rel * normals[j]).sum(-1) * curl       # > 0 on the underside (inside the curl)
    w = widths[j]
    inside = np.abs(lateral) <= w / 2.0 + 0.15
    # nothing beyond its two ends (the nearest point there is an end, and the band would run on)
    tangent = np.stack([normals[j][:, 1], -normals[j][:, 0]], -1)
    along = (rel * tangent).sum(-1)
    inside &= ~((j == len(pts) - 1) & (along > 0.3))
    inside &= ~((j == 0) & (along < -0.3))
    img = np.zeros((SIZE * SIZE, 4), np.uint8)
    # the suckers' places: as far apart as they are wide, fixed on the skin (they go in with it)
    rhos = np.clip(widths * 0.25 * sucker, 0.9, 3.0)
    spacing = np.maximum(3.2, 2 * rhos + 1.0)
    count = np.cumsum(ds / spacing) + pulled / spacing[0]
    suckers = [(pts[i] + normals[i] * curl * (widths[i] / 4.0 + 0.3), rhos[i]) for i in range(1, len(pts))
               if math.floor(count[i]) != math.floor(count[i - 1]) and s_values[i] > 1.0]
    u = np.abs(lateral) / (w / 2.0)
    sg = sig[j]
    back = pal["back"]
    for k in np.nonzero(inside)[0]:
        if lateral[k] > 0:                             # the pink underside
            c = pal["edge"] if u[k] > 0.76 else pal["pink"]
            # suckers: in a row along the middle of the underside, smaller and closer to the tip
            best = None
            for centre, rho in suckers:
                off = q[k] - centre
                dist = math.hypot(*off)
                if dist <= rho + 0.7 and (best is None or dist - rho < best[0] - best[1]):
                    best = (dist, rho, off)
            if best is not None:
                dist, rho, off = best
                lit = (off[0] + off[1]) < 0
                if dist > rho:
                    c = pal["edge"]                     # the darker ring round a sucker
                elif rho >= 1.4:
                    if dist <= 0.8:
                        c = pal["deep"]
                    elif dist <= rho - 1.0:
                        c = pal["hole"]
                    elif off[0] + off[1] < -rho * 1.0:
                        c = pal["shine"]
                    else:
                        c = pal["rim"] if lit else pal["rim_shade"]
                else:
                    c = pal["hole"]
        else:                                          # the purple back
            c = back[0] if u[k] > 0.82 else back[1] if u[k] > 0.6 else back[2] if u[k] > 0.12 else back[3]
            if 0.22 <= u[k] <= 0.48 and w[k] > 2.5:      # the gloss along it
                c = pal["gloss"]
                if u[k] <= 0.4 and (sg[k] % 9.0) < 2.2:
                    c = pal["shine"]
            # dark spots on the back, fixed on its skin
            n = round((sg[k] - 2.0) / 5.5)
            s_c = n * 5.5 + 2.0
            if (n * 7 + int(seed * 10)) % 3 != 0 and s_c > 0:
                jc = int(np.clip(np.searchsorted(sig, s_c), 0, len(pts) - 1))
                wc = widths[jc]
                centre = pts[jc] - normals[jc] * curl * (wc * 0.33)
                if wc > 3.0 and math.hypot(*(q[k] - centre)) <= max(0.7, wc * 0.14):
                    c = pal["spot"]
        img[k] = c + (255,)
    # the dark outline round it
    inside2 = inside.reshape(SIZE, SIZE)
    padded = np.pad(inside2, 1)
    grown = np.zeros_like(inside2)
    for oy in (0, 1, 2):
        for ox in (0, 1, 2):
            grown |= padded[oy:oy + SIZE, ox:ox + SIZE]
    edge = grown & ~inside2
    img = img.reshape(SIZE, SIZE, 4)
    img[edge] = OUTLINE + (255,)
    # the grip: in the hollow of the curl near the tip
    jg = int(np.clip(np.searchsorted(s_values, reach * 0.86), 0, len(pts) - 1))
    grip = pts[jg] + normals[jg] * curl * (widths[jg] / 2.0 + 3.5)
    return img, (int(round(grip[0])), int(round(grip[1])))


# The catchers' act, in parts: (part, frames, ms each). A star drifts in to the tentacle's tip
# ("wait"), it lunges and curls round it ("lunge"), drags it into the chest ("drag"), stays in
# there ("hidden"), comes out again holding it ("emerge"), the star struggles and the tentacle
# thrashes ("struggle"), the star breaks free as the tip whips open ("escape"), and the tentacle
# snatches after it and misses ("search").
ACT = [("wait", 12, 200), ("lunge", 6, 55), ("drag", 8, 110), ("hidden", 1, 1400), ("emerge", 6, 110),
       ("struggle", 8, 90), ("escape", 3, 60), ("search", 4, 160)]
# what the star is doing, for the screen: 0 none, 1 free, 2 held, 3 struggling, 4 breaking free, 5 fleeing
STAR_STATE = {"wait": 1, "lunge": 1, "drag": 2, "hidden": 0, "emerge": 3, "struggle": 3, "escape": 4, "search": 5}


def ease_in(x):
    return x * x


def ease_out(x):
    return 1 - (1 - x) * (1 - x)


def act(root, direction, length, phase, curl, pal, thick, sucker, sway):
    def draw_(*args):
        return draw(*args, seed=phase)

    """The catcher's frames, and where its star is in each (sprite pixels) and what it is doing."""
    def swaying(u):
        return lambda f: sway * math.sin(2 * math.pi * (0.8 * f) + 2 * math.pi * u + phase) * (0.2 + 0.8 * f)

    frames = []                                    # (image, grip, part, index in part, frames in part)
    for part, n, _ in ACT:
        for i in range(n):
            if part == "wait":
                u = i / n
                img, grip = draw_(root, direction, length, length * (0.84 + 0.04 * math.sin(2 * math.pi * u + phase)),
                                 curl, pal, thick, sucker, swaying(u), 2.0 + 0.2 * math.sin(2 * math.pi * u))
            elif part == "lunge":
                v = (i + 1) / n
                base = swaying(1.0)
                img, grip = draw_(root, direction, length, length * (0.84 + 0.26 * ease_out(v)), curl, pal, thick, sucker,
                                 lambda f, v=v, base=base: base(f) + 0.14 * curl * v * f, 2.0 + 1.0 * v)
            elif part == "drag":
                w = (i + 1) / n
                base = swaying(1.0)
                img, grip = draw_(root, direction, length, length * 1.1 * (1 - ease_in(w)), curl, pal, thick, sucker,
                                 lambda f, w=w, base=base: (base(f) + 0.14 * curl * f) * (1 - w), 3.0)
            elif part == "hidden":
                img, grip = draw_(root, direction, length, 0.0, curl, pal, thick, sucker, lambda f: 0.0, 3.0)
            elif part == "emerge":
                e = (i + 1) / n
                img, grip = draw_(root, direction, length, length * 0.8 * ease_out(e), curl, pal, thick, sucker,
                                 lambda f, e=e: 0.12 * math.sin(6 * f + e * 5) * f, 3.0)
            elif part == "struggle":
                img, grip = draw_(root, direction, length, length * (0.8 + 0.05 * math.sin(i * 2.3)), curl, pal, thick, sucker,
                                 lambda f, i=i: 0.28 * math.sin(i * 1.7 + f * 6.0) * f, 3.0 + 0.35 * math.sin(i * 2.9))
            elif part == "escape":
                x = (i + 1) / n
                img, grip = draw_(root, direction, length, length * (0.8 + 0.12 * x), curl, pal, thick, sucker,
                                 lambda f, x=x: -0.3 * curl * x * f, 3.0 - 2.4 * x)
            else:                                   # search
                y = (i + 1) / n
                img, grip = draw_(root, direction, length, length * (0.92 + 0.12 * math.sin(math.pi * y)), curl, pal, thick,
                                 sucker, lambda f, y=y: 0.25 * curl * math.sin(math.pi * y) * f, 0.6 + 1.4 * y)
            frames.append((img, grip, part, i, n))
    # the star: it drifts in to where the lunge's curl closes, is held in the curl, and breaks
    # free outwards
    catch = next(g for img, g, part, i, n in frames if part == "lunge" and i == n - 1)
    out = np.array(direction) + 0.35 * curl * np.array((-direction[1], direction[0]))
    out /= np.linalg.norm(out)
    far = np.array(catch) + np.array(direction) * 22 + np.array((-direction[1], direction[0])) * 7 * curl
    free_from = next(g for img, g, part, i, n in frames if part == "escape" and i == 0)
    stars = []
    for img, grip, part, i, n in frames:
        if part == "wait":
            p = far + (np.array(catch) - far) * ((i + 1) / n) ** 1.4
        elif part == "lunge":
            p = np.array(catch, float)
        elif part in ("drag", "emerge", "struggle"):
            p = np.array(grip, float)
        elif part == "escape":
            p = np.array(free_from, float) + out * 10 * ((i + 1) / n)
        elif part == "search":
            p = np.array(free_from, float) + out * (10 + 20 * ((i + 1) / n))
        else:
            p = np.array(grip, float)
        stars.append((int(round(p[0])), int(round(p[1])), STAR_STATE[part]))
    return [f[0] for f in frames], stars


STAR_COLOURS = [((255, 255, 255), (255, 205, 240), (214, 120, 220)),     # pink
                ((255, 255, 240), (255, 236, 170), (231, 182, 98)),      # gold
                ((255, 255, 255), (200, 240, 255), (120, 180, 255))]     # ice


def stars():
    """Three kinds of star, each in the three colours (one row each), eight twinkle frames of 11x11:
    a little sparkle, a four-pointed star, an eight-pointed one."""
    sheet = Image.new("RGBA", (11 * 8, 11 * 9))
    for kind in range(3):
        for col, (core, inner, outer) in enumerate(STAR_COLOURS):
            row = kind * 3 + col
            for f in range(8):
                size = [[0, 1, 1, 2, 2, 1, 1, 0], [1, 2, 3, 4, 4, 3, 2, 1], [2, 3, 4, 5, 5, 4, 3, 2]][kind][f]
                cx = cy = 5
                pts = {(cx, cy): core}
                for r in range(1, size + 1):
                    c = inner if r <= (size + 1) // 2 else outer
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        pts[(cx + dx * r, cy + dy * r)] = c
                if kind == 1 and size >= 3:
                    for dx, dy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
                        pts[(cx + dx, cy + dy)] = outer
                if kind == 2:
                    for r in range(1, size // 2 + 1):
                        for dx, dy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
                            pts[(cx + dx * r, cy + dy * r)] = inner if r == 1 else outer
                for (x, y), c in pts.items():
                    sheet.putpixel((f * 11 + x, row * 11 + y), c + (255,))
    sheet.save(f"{OUT}/vacuum_chest_stars.png")


def main():
    catchers = [i for i, t in enumerate(TENTACLES) if t[11]]
    others = [i for i, t in enumerate(TENTACLES) if not t[11]]
    sheet = Image.new("RGBA", (SIZE * FRAMES, SIZE * len(others)))
    for row, i in enumerate(others):
        x, y, dx, dy, length, phase, curl, pal, thick, sucker, sway, _ = TENTACLES[i]
        root = (SIZE / 2 - REACH * dx, SIZE / 2 - REACH * dy)
        for f in range(FRAMES):
            img, _ = frame(root, (dx, dy), length, phase, curl, PALETTES[pal], thick, sucker, sway, f / FRAMES)
            sheet.paste(Image.fromarray(img, "RGBA"), (f * SIZE, row * SIZE))
    sheet.save(f"{OUT}/vacuum_chest_tentacles.png", optimize=True)
    act_frames = sum(n for _, n, _ in ACT)
    acts = Image.new("RGBA", (SIZE * act_frames, SIZE * len(catchers)))
    star_rows = []
    for row, i in enumerate(catchers):
        x, y, dx, dy, length, phase, curl, pal, thick, sucker, sway, _ = TENTACLES[i]
        root = (SIZE / 2 - REACH * dx, SIZE / 2 - REACH * dy)
        imgs, stars_ = act(root, (dx, dy), length, phase, curl, PALETTES[pal], thick, sucker, sway)
        for f, img in enumerate(imgs):
            acts.paste(Image.fromarray(img, "RGBA"), (f * SIZE, row * SIZE))
        star_rows.append(stars_)
    acts.save(f"{OUT}/vacuum_chest_catchers.png", optimize=True)
    stars()
    # which sheet and row each tentacle is in, the act's timing, and the stars, into Tentacles.java
    rows = [catchers.index(i) if i in catchers else others.index(i) for i in range(len(TENTACLES))]
    lines = ["    // <generated by tools/gen_tentacles.py>",
             "    /** Whether each tentacle catches stars (it is in the catchers' sheet then), and its row in its sheet. */",
             "    private static final boolean[] CATCHER = {" + ", ".join("true" if t[11] else "false" for t in TENTACLES) + "};",
             "    private static final int[] ROW = {" + ", ".join(map(str, rows)) + "};",
             "    private static final int OTHERS = " + str(len(others)) + ", CATCHERS = " + str(len(catchers)) + ";",
             "    /** The catchers' act: how long each frame lasts, and what the star is doing in it. */",
             "    private static final int[] ACT_MS = {" + ", ".join(str(ms) for _, n, ms in ACT for _ in range(n)) + "};",
             "    private static final int[] ACT_STATE = {" + ", ".join(str(st) for _, _, st in star_rows[0]) + "};",
             "    /** Where each catcher's star is in each frame (sprite pixels). */",
             "    private static final int[][][] ACT_STAR = {"]
    for row in star_rows:
        lines.append("            {" + ", ".join(f"{{{a}, {b}}}" for a, b, _ in row) + "},")
    lines += ["    };", "    // </generated>"]
    src = open(JAVA, encoding="utf-8").read()
    src = re.sub(r"    // <generated by tools/gen_tentacles.py>.*?    // </generated>", "\n".join(lines), src, flags=re.S)
    open(JAVA, "w", encoding="utf-8").write(src)


if __name__ == "__main__":
    main()
