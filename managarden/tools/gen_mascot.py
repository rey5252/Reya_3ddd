"""The garden keeper: a little elf girl with silver twin tails, green eyes and a dress of white, gold
and leaves, who holds the Heart of the Greenhouse in her arms; she stands beside the greenhouse GUI.

    python3 tools/gen_mascot.py [--preview]

Writes textures/gui/mascot.png (256x256):
  (0,0)     body frame 0, 96x136: everything but her arms and the heart, eyes open, smiling
  (96,0)    body frame 1: the same with her head and shoulders a pixel up (breathing)
  (192,0)   eyes, 40x18 each, one under the other: open, half, shut, happy, starry
  (232,0)   mouths, 10x6 each: smile, open, happy
  (0,136)   her arms with the heart, 80x52 each, three to a row: holding it, lifting it a little,
            waving (two frames)
The screen (client/Mascot.java) puts them together at the places noted there.

She is drawn in parts from pixel maps and shapes, back to front, on a grid of palette keys.
"""
import math
import os
import sys

from PIL import Image

from pix import Canvas, ASSETS, hexc, upscale, ROOT

W, H = 96, 136
SHEET_W, SHEET_H = 256, 256
# where the parts go on the body, and where they sit on the sheet (kept in step with client/Mascot.java)
EYES_BOX = (28, 36, 40, 18)          # x, y, w, h on the body
EYES_UV = (192, 0)
MOUTH_BOX = (43, 55, 10, 6)
MOUTH_UV = (232, 0)
ARMS_BOX = (8, 44, 80, 52)
ARMS_UV = (0, 136)                   # poses left to right, three to a row

PAL = {
    # hair: silver with icy mana-blue shadows, mana-cyan tips
    "O": hexc("56628F"), "D": hexc("97A7D0"), "G": hexc("BFCDEA"), "g": hexc("E3EBF8"), "h": hexc("F5F9FF"),
    "H": hexc("FFFFFF"), "c": hexc("A6F6FF"), "C": hexc("5CD6F2"), "q": hexc("2A9FE2"),
    # skin
    "S": hexc("FFF4EC"), "s": hexc("FFE4D6"), "x": hexc("F5C6B3"), "X": hexc("C98A76"), "e": hexc("F4A99C"),
    # eyes (green), lashes, brows, blush, mouth
    "K": hexc("2A1B3D"), "k": hexc("6B4E6E"), "j": hexc("D8A294"), "W": hexc("FFFFFF"), "w": hexc("DDE6F2"),
    "1": hexc("12422A"), "2": hexc("1F6B3A"), "3": hexc("35A04F"), "4": hexc("6FD568"), "5": hexc("B9F59C"),
    "p": hexc("0B2616"), "b": hexc("FFC2CC"), "B": hexc("FF8FA6"), "m": hexc("A8445A"), "n": hexc("FF9CAF"),
    # dress: white, gold, leaves
    "V": hexc("FFFFFF"), "v": hexc("EEF1F8"), "U": hexc("D2D8EA"), "u": hexc("8E97B8"),
    "Y": hexc("FFE688"), "y": hexc("F4C842"), "T": hexc("C98B1A"), "t": hexc("7A4E0C"),
    "L": hexc("A8E563"), "l": hexc("6DC043"), "M": hexc("45922F"), "N": hexc("2A6428"), "Q": hexc("173D1C"),
    # mana (the heart, gems, hem)
    "A": hexc("E6FDFF"), "a": hexc("A6F6FF"), "Z": hexc("55D9F7"), "z": hexc("2A9FE2"), "7": hexc("1B64B8"),
    "8": hexc("0B2F5C"),
    # flowers, boots
    "P": hexc("FFF0F8"), "F": hexc("FFC4E2"), "f": hexc("EE8FC2"), "E": hexc("C45A97"),
    "r": hexc("8A3C18"), "R": hexc("4A1A08"), "o": hexc("B45E30"),
}


class Grid:
    """A grid of palette keys, so parts can be drawn, masked and outlined by name."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.c = [["." for _ in range(w)] for _ in range(h)]

    def get(self, x, y):
        return self.c[y][x] if 0 <= x < self.w and 0 <= y < self.h else "."

    def set(self, x, y, k):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.c[y][x] = k

    def draw(self, rows, x, y, flip=False, only_on=None):
        for j, row in enumerate(rows):
            if flip:
                row = row[::-1]
            for i, ch in enumerate(row):
                if ch in ". ":
                    continue
                if ch == "_":
                    self.set(x + i, y + j, ".")
                    continue
                if only_on is not None and self.get(x + i, y + j) not in only_on:
                    continue
                self.set(x + i, y + j, ch)

    def fill(self, pix, k):
        for (x, y) in pix:
            self.set(x, y, k)

    def to_canvas(self, cv, ox=0, oy=0):
        for y in range(self.h):
            for x in range(self.w):
                k = self.c[y][x]
                if k != ".":
                    cv.set(ox + x, oy + y, PAL[k])

    def crop(self, x, y, w, h):
        g = Grid(w, h)
        for j in range(h):
            for i in range(w):
                g.set(i, j, self.get(x + i, y + j))
        return g

    def copy(self):
        g = Grid(self.w, self.h)
        g.c = [row[:] for row in self.c]
        return g


def mirror(x):
    return W - 1 - x


def poly(points):
    """Pixels whose centres are inside the polygon."""
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    pix = set()
    for y in range(int(min(ys)) - 1, int(max(ys)) + 2):
        for x in range(int(min(xs)) - 1, int(max(xs)) + 2):
            px, py = x + 0.5, y + 0.5
            inside = False
            n = len(points)
            for i in range(n):
                x1, y1 = points[i]
                x2, y2 = points[(i + 1) % n]
                if (y1 > py) != (y2 > py):
                    xi = x1 + (py - y1) * (x2 - x1) / (y2 - y1)
                    if px < xi:
                        inside = not inside
            if inside:
                pix.add((x, y))
    return pix


def ellipse(cx, cy, rx, ry):
    pix = set()
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0:
                pix.add((x, y))
    return pix


def mirrored(pix):
    return {(mirror(x), y) for (x, y) in pix}


def edge(pix):
    return {(x, y) for (x, y) in pix if any((x + dx, y + dy) not in pix for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}


def outline(g, pix, k):
    for p in edge(pix):
        g.set(p[0], p[1], k)


def ring_of(pix):
    """The pixels just outside a shape (4-neighbours)."""
    out = set()
    for (x, y) in pix:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in pix:
                out.add(q)
    return out


# ---------------------------------------------------------------- head

FACE_TOP, CHIN = 28, 63


def face_halfwidth(y):
    """Half the face's width on a row: a wide round chibi face with a small soft chin."""
    table = {53: 17.8, 54: 17.4, 55: 16.9, 56: 16.2, 57: 15.3, 58: 14.2, 59: 12.8, 60: 11.0, 61: 8.8, 62: 6.0}
    if y <= 52:
        return 18.0
    return table.get(y, 0.0)


def face_pixels():
    pix = set()
    for y in range(FACE_TOP, CHIN):
        hw = face_halfwidth(y)
        for x in range(W):
            if abs(x + 0.5 - W / 2) <= hw:
                pix.add((x, y))
    return pix


def twin_tail(side):
    """One of her twin tails, tied high on the side of the head: puffy near the tie, falling in a soft
    wave behind her to below her waist and curling in at the tip."""
    pts = [(21, 13), (15, 9), (9, 10), (4.5, 15), (2, 23), (1.5, 33), (3, 43), (5, 52), (5.5, 61), (4.5, 70),
           (4.5, 79), (6.5, 88), (10, 97), (14, 104), (18, 108.5), (16.5, 101), (16, 94), (17, 86), (18.5, 77),
           (19.5, 68), (20.5, 58), (21.5, 47), (22.5, 36), (23, 26), (23, 18)]
    pix = poly(pts)
    return pix if side == 0 else mirrored(pix)


def ear(side):
    """A long elf ear pointing out and a little up."""
    pts = [(31, 44.5), (22, 42), (14, 38.5), (11, 38), (13.5, 41), (18, 45.5), (23, 50), (28, 53.5), (31, 54)]
    pix = poly(pts)
    return pix if side == 0 else mirrored(pix)


def draw_twin_tails(g):
    for side in (0, 1):
        pix = twin_tail(side)
        rows = {}
        for (x, y) in pix:
            rows.setdefault(y, []).append(x)
        for (x, y) in pix:
            xs = rows[y]
            lo, hi = min(xs), max(xs)
            # t: 0 on the outer edge, 1 on the edge by her body
            t = (x - lo) / max(1, hi - lo) if side == 0 else (hi - x) / max(1, hi - lo)
            if y > 99:
                k = "C" if t > 0.55 or y > 104 else "c"
            elif y > 92:
                k = "c" if t < 0.5 else "G"
            elif t > 0.86:
                k = "D"
            elif t > 0.62:
                k = "G"
            elif t < 0.22 and y < 84:
                k = "h"
            else:
                k = "g"
            g.set(x, y, k)
        # two strands winding down the lock, and a shine where it puffs out below the tie
        for y in range(16, 96):
            xs = rows.get(y)
            if not xs:
                continue
            lo, hi = min(xs), max(xs)
            for (f, k) in ((0.40, "G"), (0.58, "D" if y > 30 else "G")):
                pos = f + 0.06 * math.sin(y / 7.0)
                x = int(round(lo + (hi - lo) * (pos if side == 0 else 1 - pos)))
                if (x, y) in pix and g.get(x, y) not in ("c", "C"):
                    g.set(x, y, k)
        for y in range(14, 34):
            xs = rows.get(y)
            if xs:
                lo, hi = min(xs), max(xs)
                x = lo + 2 if side == 0 else hi - 2
                g.set(x, y, "H" if 18 <= y <= 26 else "h")
        outline(g, pix, "O")


def draw_back_hair(g, lift=0):
    dome = ellipse(48, 34 - lift, 28.5, 28.5)
    sides = poly([(18, 34 - lift), (29, 34 - lift), (30, 60 - lift), (27, 72 - lift), (22, 70 - lift), (18, 58 - lift)])
    sides |= mirrored(sides)
    pix = dome | sides
    for (x, y) in pix:
        g.set(x, y, "G" if y > 44 - lift else "g")
    outline(g, pix, "O")
    return pix


def draw_ears(g, lift=0):
    for side in (0, 1):
        pix = {(x, y - lift) for (x, y) in ear(side)}
        for (x, y) in pix:
            g.set(x, y, "S")
        # the inside of the ear: a pink hollow along its length, shade on the lower edge
        for (x, y) in pix:
            xx = x if side == 0 else mirror(x)
            yy = y + lift
            if 15 <= xx <= 28 and abs(yy - (39.5 + (xx - 13) * 0.42)) < 1.0:
                g.set(x, y, "e")
            elif (x, y + 1) not in pix:
                g.set(x, y, "x")
        outline(g, pix, "X")
        # an earring: a gold cap and a mana crystal drop under the lobe
        ex = 25 if side == 0 else mirror(25) - 2
        drop = ["yY.", ".Z.", "aZz", "Zz7", ".7."]
        g.draw(drop, ex, 53 - lift)
        g.set(ex, 53 - lift, "T" if side else "y")


def draw_face(g, lift=0):
    pix = {(x, y - lift) for (x, y) in face_pixels()}
    for (x, y) in pix:
        g.set(x, y, "S")
    # soft shade down the sides of the face and under the bangs
    for (x, y) in pix:
        yy = y + lift
        hw = face_halfwidth(yy)
        d = min(abs(x + 0.5 - (W / 2 - hw)), abs(x + 0.5 - (W / 2 + hw)))
        if d < 1.6 and yy > 38:
            g.set(x, y, "s")
    for (x, y) in edge(pix):
        if y + lift >= 50:
            g.set(x, y, "X")
    # the neck in shadow under the chin
    for y in range(CHIN - 1, CHIN + 5):
        for x in range(43, 53):
            if g.get(x, y - lift) == ".":
                g.set(x, y - lift, "x" if y < CHIN + 2 else "s")
    return pix


def bang_line(x):
    """How far down the bangs reach over the forehead: parted a little in the middle, sweeping down
    to each side, pointed locks."""
    dx = abs(x + 0.5 - W / 2)
    base = 31.0 + min(8.5, dx * 0.95) if dx < 12 else 39.5 + (dx - 12) * 0.55
    # locks: little points along the edge
    wiggle = 1.2 * max(0.0, math.cos((dx - 2.0) / 3.2 * math.pi)) if dx > 3 else 0.0
    return base + wiggle


def draw_bangs(g, lift=0):
    pix = set()
    for x in range(26, 70):
        yb = bang_line(x)
        for y in range(8, int(math.floor(yb)) + 1):
            pix.add((x, y - lift))
    # side locks framing the face, falling to the jaw in points
    for side in (0, 1):
        lock = poly([(27, 36), (32, 38), (33.5, 50), (33, 60), (31.5, 66), (30, 60), (28, 50)])
        lock = {(x, y - lift) for (x, y) in lock}
        pix |= lock if side == 0 else mirrored(lock)
    top = ellipse(48, 34 - lift, 28.5, 28.5)
    pix = {p for p in pix if p in top or p[1] + lift >= 34}
    for (x, y) in pix:
        g.set(x, y, "g")
    # strands from the crown to the lock points, darker; shine across the top
    crown = (47.5, 6 - lift)
    for tx in (28, 33, 38, 43, 52, 57, 62, 67):
        ty = bang_line(tx) - lift
        n = 60
        for s in range(n + 1):
            t = s / n
            bx = crown[0] + (tx - crown[0]) * t + (tx - crown[0]) * 0.18 * math.sin(t * math.pi)
            by = crown[1] + (ty - crown[1]) * t
            x, y = int(round(bx)), int(round(by))
            if t > 0.35 and (x, y) in pix:
                g.set(x, y, "G")
    for x in range(20, 76):
        y = 15 - lift + round(((x - 47.5) / 26.0) ** 2 * 5.0)
        for (xx, yy, k) in ((x, y, "h"), (x, y + 1, "h" if x % 3 else "g")):
            if g.get(xx, yy) in ("g", "G"):
                g.set(xx, yy, k)
    for (x, y) in ((33, 17), (34, 17), (61, 17), (62, 17), (47, 14), (48, 14)):
        g.set(x, y - lift, "H")
    # the edge of the bangs over the face: dark tips, a shade line just above
    for x in range(26, 70):
        yb = int(math.floor(bang_line(x))) - lift
        if (x, yb) in pix:
            g.set(x, yb, "O")
            if g.get(x, yb - 1) in ("g", "h"):
                g.set(x, yb - 1, "D")
        if g.get(x, yb + 1) == "S":
            g.set(x, yb + 1, "s")
    for side in (0, 1):
        lock = poly([(27, 36), (32, 38), (33.5, 50), (33, 60), (31.5, 66), (30, 60), (28, 50)])
        lock = {(x, y - lift) for (x, y) in lock}
        lock = lock if side == 0 else mirrored(lock)
        for (x, y) in lock:
            g.set(x, y, "G" if (x + y) % 4 else "D")
        outline(g, lock, "O")


def draw_ties_and_flower(g, lift=0):
    """Gold rings with a leaf where the twin tails are tied, a pink flower and a sprout on top."""
    for side in (0, 1):
        tie = ["..yY..", ".yYYy.", "TyyyyT", ".TttT."]
        x = 18 if side == 0 else mirror(18) - 5
        g.draw(tie, x, 14 - lift)
        leaf = ["..LL", ".LlN", "LlN.", "N..."]
        g.draw(leaf if side == 0 else [r[::-1] for r in leaf], (14 if side == 0 else mirror(14) - 3), 11 - lift)
    g.draw([
        "..EE.EE..",
        ".EFFEFFE.",
        "EFPFFFPFE",
        "EFFfYfFFE",
        ".EfYyYfE.",
        "EFFfYfFFE",
        "EFPFFFPFE",
        ".EFFEFFE.",
        "..EE.EE..",
    ], 61, 17 - lift)
    g.draw([
        ".LL...LL.",
        "LlLL.LLlL",
        ".NLlMlLN.",
        "...NMN...",
        "....M....",
        "....M....",
    ], 43, 0 - lift)


def head(g, lift=0):
    back = draw_back_hair(g, lift)
    draw_ears(g, lift)
    draw_face(g, lift)
    draw_bangs(g, lift)
    # the hair's silhouette gets its outline back where the bangs covered it
    for (x, y) in back:
        if g.get(x, y) in ("g", "G", "h", "H", "D") and any(g.get(x + dx, y + dy) == "." for dx, dy in ((1, 0), (-1, 0), (0, -1))):
            g.set(x, y, "O")
    draw_ties_and_flower(g, lift)


# ---------------------------------------------------------------- face parts

EYE_W, EYE_H = 14, 13


def eye_rows(variant):
    """The viewer's left eye, EYE_W x EYE_H: a thick upper lash flicked out at the outer corner, a big
    iris dark at the top and bright green at the bottom, the pupil, two glints; closed and happy eyes
    are lash curves."""
    g = [["." for _ in range(EYE_W)] for _ in range(EYE_H)]

    def put(x, y, k):
        if 0 <= x < EYE_W and 0 <= y < EYE_H:
            g[y][x] = k

    if variant in ("open", "half", "stars"):
        top = 2 if variant != "half" else 6
        # the opening and the iris
        for y in range(top + 1, 12):
            for x in range(1, 13):
                inside = ((x + 0.5 - 7.0) / 5.7) ** 2 + ((y + 0.5 - 7.0) / 4.9) ** 2 <= 1.0
                if not inside:
                    continue
                iris = ((x + 0.5 - 7.3) / 3.9) ** 2 + ((y + 0.5 - 7.3) / 4.4) ** 2 <= 1.0
                if iris:
                    t = (y - 3) / 8.0
                    k = "1" if t < 0.2 else "2" if t < 0.42 else "3" if t < 0.62 else "4" if t < 0.84 else "5"
                    put(x, y, k)
                else:
                    put(x, y, "w" if y <= top + 1 else "W")
        if variant != "stars":
            for y in range(top + 1, 12):
                for x in range(5, 11):
                    if ((x + 0.5 - 7.6) / 1.4) ** 2 + ((y + 0.5 - 6.2) / 2.3) ** 2 <= 1.0 and g[y][x] in "12345":
                        put(x, y, "p")
            for (x, y) in ((5, 4), (6, 4), (5, 5), (6, 5), (9, 9), (4, 8)):
                if y > top and g[y][x] in "12345p":
                    put(x, y, "W")
        else:
            for (x, y) in ((7, 4), (6, 5), (7, 5), (8, 5), (7, 6), (5, 9), (4, 8), (9, 8), (10, 9), (9, 10)):
                if g[y][x] in "12345":
                    put(x, y, "W")
        # the upper lash: thick, flicked out at the outer (left) corner
        for x in range(1, 13):
            put(x, top, "K")
        for x in (2, 3, 11, 12):
            put(x, top + 1, "K")
        put(0, top - 1, "K")
        put(1, top - 1, "K")
        put(12, top + 1, "k")
        if variant != "half":
            for x in range(4, 10):
                put(x, 0, "j")
        # the lower lid: a soft line under the iris
        for x in range(4, 11):
            if g[12][x] == ".":
                put(x, 12, "j")
        put(3, 11, "k")
        put(2, 10, "k")
    elif variant == "shut":
        for x in range(2, 12):
            put(x, 8 + (1 if 4 <= x <= 9 else 0), "K")
        put(1, 7, "K")
        put(0, 6, "K")
        put(12, 8, "k")
    else:  # happy: upward curves
        curve = [(1, 9), (2, 8), (3, 7), (4, 6), (5, 6), (6, 5), (7, 5), (8, 5), (9, 6), (10, 6), (11, 7), (12, 8)]
        for (x, y) in curve:
            put(x, y, "K")
            if 3 <= x <= 10:
                put(x, y + 1, "k" if x in (3, 10) else ".")
    return ["".join(r) for r in g]


def mirror_eye(rows):
    """The right eye: the left one mirrored."""
    return [r[::-1] for r in rows]


EYE_L = (30, 36)                     # corner of the left eye's map on the body
EYE_R = (W - EYE_L[0] - EYE_W, 36)


def put_eyes(g, variant, lift=0):
    # clear what an earlier variant left, back to skin
    for (ex, ey) in (EYE_L, EYE_R):
        for j in range(EYE_H):
            for i in range(EYE_W):
                if g.get(ex + i, ey + j - lift) in "KkjWw12345pH":
                    g.set(ex + i, ey + j - lift, "S")
    rows = eye_rows(variant)
    g.draw(rows, EYE_L[0], EYE_L[1] - lift)
    g.draw(mirror_eye(rows), EYE_R[0], EYE_R[1] - lift)
    # eyebrows: short light strokes above, lifted when happy
    by = 34 - lift - (1 if variant in ("happy", "stars") else 0)
    for (x, y) in ((33, by + 1), (34, by), (35, by), (36, by), (37, by), (38, by)):
        if g.get(x, y) in ("S", "s"):
            g.set(x, y, "j")
        if g.get(mirror(x), y) in ("S", "s"):
            g.set(mirror(x), y, "j")


MOUTHS = {
    "smile": [".........", "..m...m..", "...mmm...", "........."],
    "open": [".........", "...mmm...", "..mnnnm..", "...mmm..."],
    "happy": [".........", "..mmmmm..", "..mnnnm..", "...mmm..."],
}


def put_mouth(g, variant, lift=0):
    mx, my = 44, 56
    for j in range(4):
        for i in range(9):
            if g.get(mx + i, my + j - lift) in ("m", "n"):
                g.set(mx + i, my + j - lift, "S")
    g.draw(MOUTHS[variant], mx, my - lift)


def put_blush_and_nose(g, lift=0):
    blush = [".bbbbbb.", "bBbbBbbB", ".bBbbBb."]
    g.draw(blush, 31, 51 - lift, only_on=("S", "s"))
    g.draw(blush[::-1] if False else [r[::-1] for r in blush], mirror(31) - 7, 51 - lift, only_on=("S", "s"))
    g.set(47, 53 - lift, "x")
    g.set(48, 52 - lift, "S")


# ---------------------------------------------------------------- body

WAIST = 93
HEM = 113
FEET = 133


def draw_collar(g, lift=0):
    """A white sailor-ish collar edged in gold with two leaves, a mana gem at the throat."""
    pix = poly([(36, 64), (59, 64), (62, 69), (54, 72), (48, 69), (41, 72), (33, 69)])
    pix = {(x, y - lift) for (x, y) in pix}
    for (x, y) in pix:
        g.set(x, y, "V" if x < 48 else "v")
    outline(g, pix, "u")
    for (x, y) in edge(pix):
        if y + lift >= 68:
            g.set(x, y, "y")
    g.draw(["LLL.", ".lLL", "..Nl"], 40, 65 - lift)
    g.draw([".LLL", "LLl.", "lN.."], 52, 65 - lift)
    g.draw([".y.", "yZy", "aZz", ".7."], 46, 65 - lift)


def draw_torso(g, lift=0):
    pix = poly([(35, 65), (60, 65), (65, 72), (62, 84), (59, WAIST + 1), (37, WAIST + 1), (34, 84), (31, 72)])
    pix = {(x, y - lift) for (x, y) in pix}
    for (x, y) in pix:
        g.set(x, y, "V" if x < 44 else "v" if x < 55 else "U")
    outline(g, pix, "u")
    # gold piping down the front with little buttons, a leaf embroidery at each side
    for y in range(70, WAIST):
        g.set(47, y - lift, "y" if y % 4 else "T")
    for (x, y) in ((38, 80), (57, 80)):
        g.draw([".L.", "LlL", ".N."], x - 1, y - lift)
    return pix


def draw_skirt(g):
    """A white skirt with a gold hem and a mana-blue scalloped trim, big leaves hanging over it."""
    pix = poly([(36, WAIST), (59, WAIST), (70, HEM - 1), (72, HEM + 1), (23, HEM + 1), (25, HEM - 1)])
    for (x, y) in pix:
        t = (x - 23) / 49.0
        g.set(x, y, "V" if t < 0.4 else "v" if t < 0.75 else "U")
    for x in range(24, 72):
        if (x, HEM - 2) in pix:
            g.set(x, HEM - 2, "Y")
            g.set(x, HEM - 1, "y")
        if (x, HEM) in pix:
            g.set(x, HEM, "T")
        if (x, HEM + 1) in pix:
            g.set(x, HEM + 1, "Z")
    outline(g, pix, "u")
    for x in range(23, 73):
        if (x, HEM + 1) in pix or (x, HEM) in pix:
            g.set(x, HEM + 1, "Z" if x % 4 else "z")
            g.set(x, HEM + 2, "z" if x % 4 in (1, 2) else ".")
            if x % 4 in (1, 2):
                g.set(x, HEM + 3, "7" if x % 4 == 1 else ".")
    # the leaves: five, from the waist, the middle one longest
    for (bx, tip_x, length) in ((39, 30, 15), (43, 38, 18), (48, 48, 19), (53, 58, 18), (57, 66, 15)):
        top = WAIST
        pts = [(bx - 3.2, top), (bx + 3.2, top), (tip_x + 4.2, top + length - 7), (tip_x + 0.5, top + length),
               (tip_x - 3.2, top + length - 7)]
        leaf = poly(pts)
        for (x, y) in leaf:
            lit = x < bx + (tip_x - bx) * ((y - top) / length) + 0.5
            g.set(x, y, "L" if lit else "l")
        outline(g, leaf, "N")
        for sgt in range(2, length - 1):
            t = sgt / length
            x = int(math.floor(bx + (tip_x + 0.5 - bx) * t))
            y = top + sgt
            if (x, y) in leaf and (x, y) not in edge(leaf):
                g.set(x, y, "M")
    # the gold sash at the waist, a pink flower on it
    for x in range(35, 61):
        g.set(x, WAIST - 1, "y")
        g.set(x, WAIST, "T")
    g.draw([".f.", "fYf", ".f."], 55, WAIST - 2)


def draw_legs(g):
    """White stockings with leaf garters, livingwood boots with gold cuffs."""
    for (x1, x2) in ((39, 45), (51, 57)):
        for y in range(HEM + 2, FEET - 6):
            for x in range(x1, x2):
                g.set(x, y, "V" if x < x1 + 2 else "v" if x < x2 - 1 else "U")
            g.set(x1 - 1, y, "u")
            g.set(x2, y, "u")
        for x in range(x1 - 1, x2 + 1):
            g.set(x, HEM + 4, "N" if x in (x1 - 1, x2) else "L" if x < x1 + 2 else "l")
            g.set(x, HEM + 5, "N" if x in (x1 - 1, x2) else "M")
        g.set(x1 + 1, HEM + 9, "V")
        g.set(x1 + 2, HEM + 9, "V")
        # boots
        for y in range(FEET - 6, FEET):
            for x in range(x1 - 1, x2 + 1):
                g.set(x, y, "o" if x < x1 + 2 and y < FEET - 2 else "r")
            g.set(x1 - 1, y, "R")
            g.set(x2, y, "R")
        for x in range(x1 - 1, x2 + 1):
            g.set(x, FEET - 6, "Y" if x < x1 + 3 else "y")
            g.set(x, FEET - 1, "R")
        g.set(x1 - 2, FEET - 1, "R")
        g.set(x2 + 1, FEET - 1, "R")
        g.set(x1, FEET - 3, "Y")


def draw_ground(g):
    """A tuft of grass and two tiny flowers under her feet."""
    y0 = FEET - 1
    g.draw([
        "..l....L..l.....M.l...L...l....l..",
        ".lL.l.lLl.Ll.L.lMlL..lLl.lL.l.lLl.",
        "lLLlLlLLLlLLlLlLLLLlLLLLlLLlLlLLLl",
        "NlMlNlMlNlMlNlMlNlMlNlMlNlMlNlMlN.",
    ], 31, y0)
    g.draw([".F.", "FYF", ".F."], 31, y0 - 2)
    g.draw([".P.", "PYP", ".P."], 62, y0 - 1)


def body_frame(breath):
    g = Grid(W, H)
    lift = 1 if breath else 0
    draw_twin_tails(g)
    draw_legs(g)
    draw_skirt(g)
    draw_torso(g, lift)
    draw_collar(g, lift)
    draw_ground(g)
    hd = Grid(W, H)
    head(hd, lift)
    put_eyes(hd, "open", lift)
    put_blush_and_nose(hd, lift)
    put_mouth(hd, "smile", lift)
    for y in range(H):
        for x in range(W):
            k = hd.get(x, y)
            if k == ".":
                continue
            # the neck goes under the collar
            if y + lift >= 64 and k in ("s", "x", "X", "S") and g.get(x, y) != ".":
                continue
            g.set(x, y, k)
    return g


# ---------------------------------------------------------------- arms and the heart

def heart_pixels(cx, cy, w, h):
    """A plump heart shape: two round lobes and a soft point."""
    pix = set()
    r = w / 4.0 + 0.4
    for y in range(int(cy - h / 2) - 1, int(cy + h / 2) + 2):
        for x in range(int(cx - w / 2) - 1, int(cx + w / 2) + 2):
            px, py = x + 0.5, y + 0.5
            lobe = min(math.hypot(px - (cx - w / 4.0), py - (cy - h / 2 + r)), math.hypot(px - (cx + w / 4.0), py - (cy - h / 2 + r))) <= r
            # the lower part: a triangle-ish with round sides down to the point
            yl = (py - (cy - h / 2 + r)) / (h - r)
            lower = 0.0 <= yl <= 1.0 and abs(px - cx) <= (w / 2.0) * (1.0 - yl ** 1.35)
            if lobe or lower:
                pix.add((x, y))
    return pix


def draw_heart(g, cx, cy, w=30, h=26):
    """The Heart of the Greenhouse: glossy mana, bright at the top-left, deep blue at the bottom-right,
    a big shine on the left lobe and a sprout in the dip."""
    pix = heart_pixels(cx, cy, w, h)
    for (x, y) in pix:
        t = ((x - (cx - w / 2)) / w) * 0.45 + ((y - (cy - h / 2)) / h) * 0.75
        k = "a" if t < 0.35 else "Z" if t < 0.62 else "z" if t < 0.86 else "7"
        g.set(x, y, k)
    # rim light down the right side, the shine on the left lobe
    for (x, y) in pix:
        if (x + 1, y) not in pix and (x, y - 1) in pix and y > cy - h / 2 + 5:
            g.set(x - 1, y, "z")
    lx, ly = int(cx - w / 4.0) - 2, int(cy - h / 2) + 3
    g.draw(["..AAA.", ".AAAAA", "AAAa..", "AAa...", "Aa....", "a....."], lx - 1, ly)
    g.set(int(cx + w / 4.0) + 2, int(cy - h / 2) + 4, "A")
    g.set(int(cx + w / 4.0) + 3, int(cy - h / 2) + 5, "a")
    outline(g, pix, "8")
    # the sprout in the dip
    g.draw([".LL.LL", "LlL.lL", ".NlMN.", "..MN..", "..M..."], int(cx) - 3, int(cy - h / 2) - 3)
    return pix


def hand(g, x, y, flip=False):
    """A small hand holding the side of the heart: fingers curled over its edge, one line between each."""
    rows = [
        "..XXX.",
        ".XSSSX",
        "XSSxSX",
        "XSSSSX",
        "XSxSSX",
        ".XSSSX",
        "..XXX.",
    ]
    g.draw(rows, x, y, flip=flip)


def sleeve(g, cx, cy, lift=0, flip=False):
    """A puffy white sleeve with a gold cuff."""
    pix = ellipse(cx, cy - lift, 6.2, 5.2)
    for (x, y) in pix:
        g.set(x, y, "V" if (x < cx) != flip else "v")
    outline(g, pix, "u")
    for (x, y) in pix:
        if (x, y + 1) not in pix:
            g.set(x, y, "y")
            if (x, y - 1) in pix:
                g.set(x, y - 1, "Y")
    return pix


def arm(g, pts, width=4):
    """An arm (skin) along the points, outlined."""
    pix = set()
    for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
        n = max(abs(x2 - x1), abs(y2 - y1), 1)
        for i in range(n + 1):
            x = x1 + (x2 - x1) * i / n
            y = y1 + (y2 - y1) * i / n
            pix |= ellipse(x, y, width / 2.0, width / 2.0)
    for (x, y) in pix:
        g.set(x, y, "S")
    outline(g, pix, "X")
    return pix


def arms(pose):
    """Her arms and the heart, drawn over the body (in body coordinates; cut to ARMS_BOX)."""
    g = Grid(W, H)
    if pose == "hold":
        sleeve(g, 31, 72)
        sleeve(g, 64, 72, flip=True)
        arm(g, [(31, 76), (30, 83), (36, 88)])
        arm(g, [(64, 76), (65, 83), (59, 88)])
        draw_heart(g, 48, 81)
        hand(g, 31, 83)
        hand(g, 59, 83, flip=True)
    elif pose == "lift":
        sleeve(g, 31, 72)
        sleeve(g, 64, 72, flip=True)
        arm(g, [(31, 76), (31, 80), (35, 85)])
        arm(g, [(64, 76), (64, 80), (60, 85)])
        draw_heart(g, 48, 79)
        hand(g, 31, 81)
        hand(g, 59, 81, flip=True)
    else:  # wave: her left hand keeps the heart, her right one waves beside her face (two frames)
        tilt = 1 if pose == "wave2" else 0
        sleeve(g, 31, 72)
        sleeve(g, 64, 71, flip=True)
        arm(g, [(31, 76), (30, 83), (36, 88)])
        arm(g, [(67, 69), (71 + tilt, 62), (73 + 2 * tilt, 56)])
        draw_heart(g, 47, 81)
        hand(g, 30, 83)
        palm = [
            "..X.X.",
            ".XSXSX",
            "XXSXSX",
            "XSSSSX",
            "XSSSsX",
            ".XSsX.",
            "..XX..",
        ] if not tilt else [
            "...X.X",
            "..XSXSX",
            ".XXSXSX",
            ".XSSSSX",
            "XSSSsX.",
            ".XSsX..",
            "..XX...",
        ]
        g.draw(palm, 70 + 2 * tilt, 48)
    return g


# ---------------------------------------------------------------- the sheet

EYE_VARIANTS = ("open", "half", "shut", "happy", "stars")
MOUTH_VARIANTS = ("smile", "open", "happy")
ARM_POSES = ("hold", "lift", "wave", "wave2")


def face_parts():
    """The eye and mouth overlays, cut from the body drawn with each variant (so they carry the skin,
    hair and blush round them too)."""
    eyes, mouths = [], []
    for v in EYE_VARIANTS:
        g = body_frame(False)
        put_eyes(g, v)
        ex, ey, ew, eh = EYES_BOX
        eyes.append(g.crop(ex, ey, ew, eh))
    for v in MOUTH_VARIANTS:
        g = body_frame(False)
        put_mouth(g, v)
        mx, my, mw, mh = MOUTH_BOX
        mouths.append(g.crop(mx, my, mw, mh))
    return eyes, mouths


def sheet():
    cv = Canvas(SHEET_W, SHEET_H)
    for f in (0, 1):
        body_frame(f == 1).to_canvas(cv, W * f, 0)
    eyes, mouths = face_parts()
    for i, e in enumerate(eyes):
        e.to_canvas(cv, EYES_UV[0], EYES_UV[1] + i * EYES_BOX[3])
    for i, m in enumerate(mouths):
        m.to_canvas(cv, MOUTH_UV[0], MOUTH_UV[1] + i * MOUTH_BOX[3])
    ax, ay, aw, ah = ARMS_BOX
    for i, pose in enumerate(ARM_POSES):
        arms(pose).crop(ax, ay, aw, ah).to_canvas(cv, ARMS_UV[0] + i % 3 * aw, ARMS_UV[1] + i // 3 * ah)
    return cv


def compose(img, body=0, eyes=0, mouth=0, pose=0, lift=None):
    """Puts a frame together from the sheet the way client/Mascot.java does."""
    lift = body if lift is None else lift
    f = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    f.alpha_composite(img.crop((W * body, 0, W * body + W, H)), (0, 0))
    ex, ey, ew, eh = EYES_BOX
    f.alpha_composite(img.crop((EYES_UV[0], EYES_UV[1] + eyes * eh, EYES_UV[0] + ew, EYES_UV[1] + eyes * eh + eh)), (ex, ey - lift))
    mx, my, mw, mh = MOUTH_BOX
    f.alpha_composite(img.crop((MOUTH_UV[0], MOUTH_UV[1] + mouth * mh, MOUTH_UV[0] + mw, MOUTH_UV[1] + mouth * mh + mh)), (mx, my - lift))
    ax, ay, aw, ah = ARMS_BOX
    u, v = ARMS_UV[0] + pose % 3 * aw, ARMS_UV[1] + pose // 3 * ah
    f.alpha_composite(img.crop((u, v, u + aw, v + ah)), (ax, ay - lift))
    return f


def preview(cv):
    out = os.path.join(ROOT, "build", "preview")
    os.makedirs(out, exist_ok=True)
    frames = [compose(cv.img, 0, 0, 0, 0), compose(cv.img, 1, 1, 1, 0), compose(cv.img, 0, 3, 2, 1),
              compose(cv.img, 0, 4, 2, 2), compose(cv.img, 0, 3, 1, 3), compose(cv.img, 0, 2, 0, 0)]
    bg = Image.new("RGBA", (len(frames) * (W + 6) + 6, H + 12), (29, 76, 79, 255))
    for i, f in enumerate(frames):
        bg.alpha_composite(f, (6 + i * (W + 6), 6))
    upscale(bg, 4).save(os.path.join(out, "mascot.png"))
    upscale(bg.crop((0, 0, W + 12, H + 12)), 7).save(os.path.join(out, "mascot_big.png"))


def save_preview(grids, name, scale=6):
    out = os.path.join(ROOT, "build", "preview")
    os.makedirs(out, exist_ok=True)
    gw = sum(gr.w for gr in grids) + 6 * (len(grids) + 1)
    gh = max(gr.h for gr in grids) + 12
    bg = Image.new("RGBA", (gw, gh), (29, 76, 79, 255))
    x = 6
    for gr in grids:
        cv = Canvas(gr.w, gr.h)
        gr.to_canvas(cv)
        bg.alpha_composite(cv.img, (x, 6))
        x += gr.w + 6
    upscale(bg, scale).save(os.path.join(out, name))


def main():
    cv = sheet()
    cv.save(os.path.join(ASSETS, "textures", "gui", "mascot.png"))
    if "--preview" in sys.argv:
        preview(cv)


if __name__ == "__main__":
    main()
