"""Draws the tentacles round the vacuum chest's GUI, frame by frame, as pixel art (needs numpy and Pillow).

Run from the goldenquarry folder:  python3 tools/gen_tentacles.py
After reference/vacuum/tentacles.jpg: the back purple and glossy, with dark spots; the underside
pink with round suckers (a light rim round a dark hole) that get smaller towards the tip; the tip
curled round towards the underside; a dark outline. Each tentacle waves (the waves run in to the
chest), is drawn in and reaches out again, and its suckers and spots move with its skin.

Writes textures/gui/vacuum_chest_tentacles.png: one row of FRAMES sprites (SIZE x SIZE) per
tentacle, the tentacle's root at (SIZE/2 - REACH*dx, SIZE/2 - REACH*dy) of its sprite; and
textures/gui/vacuum_chest_stars.png: the twinkling stars that are pulled in beside them.
The tentacles' roots, directions and lengths are the ones in client/Tentacles.java.
"""
import math

import numpy as np
from PIL import Image

OUT = "src/main/resources/assets/goldenquarry/textures/gui"
SIZE, FRAMES, REACH = 96, 16, 40
WIDTH = 216                   # the GUI's width (VacuumChestMenu.WIDTH)
R = WIDTH - 1
# root x, root y (GUI pixels), direction out, length, phase, which way the tip curls (+1 or -1)
TENTACLES = [
    (6, 14, -0.75, -0.66, 54, 0.0, 1), (34, 13, -0.2, -0.98, 40, 1.7, -1), (4, 48, -0.98, -0.2, 40, 3.1, 1),
    (4, 104, -0.97, 0.25, 46, 5.0, -1), (9, 152, -0.8, 0.6, 52, 3.6, 1), (18, 160, -0.4, 0.92, 40, 0.4, -1),
    (R - 6, 14, 0.75, -0.66, 54, 2.3, -1), (R - 34, 13, 0.2, -0.98, 40, 4.2, 1), (R - 4, 48, 0.98, -0.2, 40, 0.9, -1),
    (R - 4, 104, 0.97, 0.25, 46, 1.2, 1), (R - 9, 152, 0.8, 0.6, 52, 2.8, -1), (R - 18, 160, 0.4, 0.92, 40, 5.6, 1)]

# colours read off the reference (its main clusters)
OUTLINE = (20, 2, 40)
BACK = [(46, 9, 85), (77, 25, 128), (108, 42, 173), (135, 62, 206)]     # edge .. middle of the back
GLOSS, SHINE = (178, 121, 212), (241, 193, 239)
SPOT = (46, 9, 85)
PINK, PINK_EDGE, RIM, HOLE, HOLE_DEEP = (218, 72, 168), (180, 40, 128), (252, 168, 228), (144, 36, 108), (82, 7, 62)


def frame(root, direction, length, phase, curl, t):
    """One frame (t from 0 to 1) of one tentacle: an RGBA array SIZE x SIZE."""
    rx, ry = root
    th0 = math.atan2(direction[1], direction[0])
    reach = length * (0.7 + 0.3 * (0.5 + 0.5 * math.sin(2 * math.pi * t + phase)))
    pulled = length - reach                         # how far its skin has gone in
    ds = 0.25
    s_values = np.arange(-6.0, reach + ds, ds)
    pts, normals, widths, sig = [], [], [], []
    x, y = rx - 6.0 * math.cos(th0), ry - 6.0 * math.sin(th0)
    for s in s_values:
        f = min(1.0, max(0.0, s) / reach)
        # a gentle S along it (its waves run in), and the tip curled round towards the underside
        bend = 0.45 * math.sin(2 * math.pi * (0.8 * f) + 2 * math.pi * 2 * t + phase) * (0.2 + 0.8 * f)
        hook = curl * (2.1 + 0.35 * math.sin(2 * math.pi * t + phase)) * f ** 3
        th = th0 + bend + hook
        pts.append((x, y))
        normals.append((-math.sin(th), math.cos(th)))
        widths.append(2.2 + 10.5 * (1.0 - f) ** 0.75)
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
    u = np.abs(lateral) / (w / 2.0)
    sg = sig[j]
    for k in np.nonzero(inside)[0]:
        if lateral[k] > 0:                             # the pink underside
            c = PINK_EDGE if u[k] > 0.74 else PINK
            # suckers: in a row along the middle of the underside, closer and smaller to the tip;
            # a light rim with a lit edge round a dark hole
            spacing = max(3.0, w[k] * 0.55)
            n = round(sg[k] / spacing)
            s_c = n * spacing
            jc = int(np.clip(np.searchsorted(sig, s_c), 0, len(pts) - 1))
            wc = widths[jc]
            centre = pts[jc] + normals[jc] * curl * (wc / 4.0 + 0.3)
            rho = min(2.4, max(0.8, wc * 0.22))
            off = q[k] - centre
            dist = math.hypot(*off)
            if s_c > -2 and dist <= rho:
                if rho >= 1.4:
                    if dist <= 0.75:
                        c = HOLE_DEEP
                    elif dist <= rho - 0.9:
                        c = HOLE
                    else:
                        c = SHINE if off[0] + off[1] < -rho * 0.9 else RIM
                else:
                    c = HOLE
        else:                                          # the purple back
            c = BACK[0] if u[k] > 0.82 else BACK[1] if u[k] > 0.6 else BACK[2] if u[k] > 0.12 else BACK[3]
            if 0.22 <= u[k] <= 0.48 and w[k] > 2.5:      # the gloss along it
                c = GLOSS
                if u[k] <= 0.4 and (sg[k] % 9.0) < 2.2:
                    c = SHINE
            # dark spots on the back, fixed on its skin
            n = round((sg[k] - 2.0) / 5.5)
            s_c = n * 5.5 + 2.0
            if (n * 7 + int(phase * 10)) % 3 != 0 and s_c > 0:
                jc = int(np.clip(np.searchsorted(sig, s_c), 0, len(pts) - 1))
                wc = widths[jc]
                centre = pts[jc] - normals[jc] * curl * (wc * 0.33)
                if wc > 3.0 and math.hypot(*(q[k] - centre)) <= max(0.7, wc * 0.14):
                    c = SPOT
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
    return img


def stars():
    """Eight frames of a twinkling four-pointed star, 9x9: white core, pink and violet points."""
    sheet = Image.new("RGBA", (9 * 8, 9))
    core, inner, outer = (255, 255, 255), (241, 193, 239), (178, 121, 212)
    for f in range(8):
        size = [1, 2, 3, 4, 4, 3, 2, 1][f]
        cx = cy = 4
        pts = {(cx, cy): core}
        for r in range(1, size + 1):
            c = inner if r <= size // 2 + 1 else outer
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                pts[(cx + dx * r, cy + dy * r)] = c
        if size >= 3:
            for dx, dy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
                pts[(cx + dx, cy + dy)] = outer
        for (x, y), c in pts.items():
            sheet.putpixel((f * 9 + x, y), c + (255,))
    sheet.save(f"{OUT}/vacuum_chest_stars.png")


def main():
    sheet = Image.new("RGBA", (SIZE * FRAMES, SIZE * len(TENTACLES)))
    for i, (x, y, dx, dy, length, phase, curl) in enumerate(TENTACLES):
        root = (SIZE / 2 - REACH * dx, SIZE / 2 - REACH * dy)
        for f in range(FRAMES):
            img = frame(root, (dx, dy), length, phase, curl, f / FRAMES)
            sheet.paste(Image.fromarray(img, "RGBA"), (f * SIZE, i * SIZE))
    sheet.save(f"{OUT}/vacuum_chest_tentacles.png")
    stars()


if __name__ == "__main__":
    main()
