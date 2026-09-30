"""The garden keeper's portrait: the face of a little elf mage with white twin tails, calm green eyes
and red drop earrings, a white capelet trimmed with gold at her shoulders, in a small livingwood frame
like the greenhouse GUI's. It hangs beside the GUI.

    python3 tools/gen_mascot.py [--preview]

Writes textures/gui/mascot.png (256x128):
  (0,0)     the portrait, 88x74: frame, background and her face, eyes open, smiling
  (88,0)    the same with her head a pixel up (breathing)
  (176,0)   eyes, 40x18 each, one under the other: open, half, shut, happy, starry
  (216,0)   mouths, 10x6 each: smile, open, happy
The screen (client/Mascot.java) puts them together at the places noted there.

She is drawn in parts from pixel maps and shapes, back to front, on a grid of palette keys (in the
coordinates of a 96x136 figure, of which the portrait shows the head and shoulders).
"""
import math
import os
import sys

from PIL import Image

from pix import Canvas, ASSETS, hexc, upscale, ROOT, rrect
import style as st
import vines as V

W, H = 96, 136                       # the figure's grid, which the portrait crops
PW, PH = 88, 74                      # the portrait
FRAME = 4                            # its frame's thickness
CROP = (8, 2)                        # the figure's point that shows at the portrait's inside corner
SHEET_W, SHEET_H = 256, 128
# where the parts go on the portrait, and where they sit on the sheet (kept in step with client/Mascot.java)
EYES_BOX = (24, 38, 40, 18)          # x, y, w, h on the portrait
EYES_UV = (176, 0)
MOUTH_BOX = (39, 57, 10, 6)
MOUTH_UV = (216, 0)

PAL = {
    # hair: white with lavender-grey shadows
    "O": hexc("6F6A86"), "D": hexc("A9A4C0"), "G": hexc("CDC9DD"), "g": hexc("EBE9F3"), "h": hexc("F8F7FC"),
    "H": hexc("FFFFFF"),
    # skin
    "S": hexc("FFF3EA"), "s": hexc("FFE3D4"), "x": hexc("F4C4B0"), "X": hexc("C98A76"), "e": hexc("F2A596"),
    # eyes (green), lashes, lids, blush, mouth
    "K": hexc("2A2233"), "k": hexc("6A5566"), "j": hexc("D6A396"), "W": hexc("FFFFFF"), "w": hexc("DDE3EE"),
    "1": hexc("0F3B35"), "2": hexc("1C6B55"), "3": hexc("2E9C66"), "4": hexc("64CF7C"), "5": hexc("B3F2A0"),
    "p": hexc("0A241E"), "b": hexc("FFC6CE"), "B": hexc("FF9AAE"), "m": hexc("A04A55"), "n": hexc("FF9CAF"),
    # the coat: white, beige gold, black, brown boots
    "V": hexc("FFFFFF"), "v": hexc("EEEEF4"), "U": hexc("D5D6E4"), "u": hexc("8C8CA6"),
    "Y": hexc("F5E2A6"), "y": hexc("DDBF78"), "T": hexc("B8924A"), "t": hexc("7A5A28"),
    "N": hexc("2E2B38"), "M": hexc("4A465E"), "Q": hexc("17151D"),
    "r": hexc("8E5F3D"), "o": hexc("B07C52"), "R": hexc("5E3A22"), "E": hexc("3A2214"),
    # red: the earrings, the orb, the staff
    "A": hexc("FFB0A8"), "a": hexc("E23A48"), "Z": hexc("B3172B"), "z": hexc("7E0F20"), "7": hexc("4E0914"),
    "F": hexc("A8453A"), "f": hexc("7C2A24"),
    # grass and little flowers under her feet
    "L": hexc("A8E563"), "l": hexc("6DC043"), "P": hexc("45922F"), "q": hexc("173D1C"),
    "c": hexc("FFF0F8"), "C": hexc("FFC4E2"), "i": hexc("FFD84A"),
    # the frame and background: the GUI panel's edge in miniature (dark outline, greens, sunk navy bevel),
    # its navy inside
    "-": st.OUT, "'": st.G0, ":": st.G1, ";": st.G2, ",": st.G3, '"': st.G4, "`": st.VOUT,
    "~": st.N0, "@": st.N1, "#": st.N2, "$": st.N3, "|": st.N4, "\\": st.N5,
    "I": st.Y0, "J": st.Y1, "d": st.Y2, "/": st.Y3,
    # the Heart of the Greenhouse (mana)
    "8": hexc("0B2F5C"), "9": hexc("1B64B8"), "0": hexc("2A9FE2"), "6": hexc("55D9F7"), "!": hexc("A6F6FF"),
    "?": hexc("E6FDFF"),
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


def lifted(pix, lift):
    return {(x, y - lift) for (x, y) in pix}


def edge(pix):
    return {(x, y) for (x, y) in pix if any((x + dx, y + dy) not in pix for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}


def outline(g, pix, k):
    for p in edge(pix):
        g.set(p[0], p[1], k)


def thick_line(pts, width):
    """The pixels along a polyline, a round brush of the width."""
    pix = set()
    for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
        n = max(abs(x2 - x1), abs(y2 - y1), 1) * 2
        for i in range(int(n) + 1):
            x = x1 + (x2 - x1) * i / n
            y = y1 + (y2 - y1) * i / n
            pix |= ellipse(x, y, width / 2.0, width / 2.0)
    return pix


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
    """One of her twin tails, tied at the side of the head above the ear, falling long behind her
    in a soft wave to her knees, a little flared out at the end."""
    pts = [(22, 18), (16, 16), (11, 19), (7, 26), (5, 36), (4.5, 48), (5, 60), (4, 72), (3, 84), (3.5, 96),
           (5.5, 106), (9, 114), (12, 110), (13, 100), (14.5, 90), (16.5, 80), (18.5, 70), (20, 60), (21, 48),
           (22, 38), (23, 28)]
    pix = poly(pts)
    return pix if side == 0 else mirrored(pix)


def ear(side):
    """A long elf ear pointing out and a little up."""
    pts = [(31, 44.5), (22, 42), (14, 38.5), (11, 38), (13.5, 41), (18, 45.5), (23, 50), (28, 53.5), (31, 54)]
    pix = poly(pts)
    return pix if side == 0 else mirrored(pix)


def draw_twin_tails(g, lift=0):
    for side in (0, 1):
        pix = lifted(twin_tail(side), lift)
        rows = {}
        for (x, y) in pix:
            rows.setdefault(y, []).append(x)
        for (x, y) in pix:
            xs = rows[y]
            lo, hi = min(xs), max(xs)
            t = (x - lo) / max(1, hi - lo) if side == 0 else (hi - x) / max(1, hi - lo)   # 0 outside, 1 by her
            k = "D" if t > 0.84 else "G" if t > 0.6 else "h" if t < 0.2 and y < 90 else "g"
            g.set(x, y, k)
        for y in rows:
            xs = rows[y]
            lo, hi = min(xs), max(xs)
            for (f, k) in ((0.38, "G"), (0.62, "D")):
                pos = f + 0.07 * math.sin((y + lift) / 8.0)
                x = int(round(lo + (hi - lo) * (pos if side == 0 else 1 - pos)))
                if (x, y) in pix and (y + lift) > 22:
                    g.set(x, y, k)
        for y in range(18, 36):
            xs = rows.get(y - lift)
            if xs:
                lo, hi = min(xs), max(xs)
                g.set(lo + 2 if side == 0 else hi - 2, y - lift, "H" if 22 <= y <= 30 else "h")
        outline(g, pix, "O")


def draw_back_hair(g, lift=0):
    dome = lifted(ellipse(48, 34, 28.5, 28.5), lift)
    sides = lifted(poly([(19, 34), (30, 34), (31, 62), (28, 72), (23, 70), (19, 58)]), lift)
    sides |= mirrored(sides)
    pix = dome | sides
    for (x, y) in pix:
        g.set(x, y, "G" if y + lift > 44 else "g")
    outline(g, pix, "O")
    return pix


def draw_ears(g, lift=0):
    for side in (0, 1):
        pix = lifted(ear(side), lift)
        for (x, y) in pix:
            g.set(x, y, "S")
        for (x, y) in pix:
            xx = x if side == 0 else mirror(x)
            yy = y + lift
            if 15 <= xx <= 28 and abs(yy - (39.5 + (xx - 13) * 0.42)) < 1.0:
                g.set(x, y, "e")
            elif (x, y + 1) not in pix:
                g.set(x, y, "x")
        outline(g, pix, "X")
        # a red drop earring on a gold hook under the lobe
        ex = 24 if side == 0 else mirror(24) - 2
        g.draw(["yY.", ".y.", "aZz", "aZz", "Zz7", ".7."], ex, 52 - lift)
        g.set(ex, 54 - lift, "A")


def draw_face(g, lift=0):
    pix = lifted(face_pixels(), lift)
    for (x, y) in pix:
        g.set(x, y, "S")
    for (x, y) in pix:
        yy = y + lift
        hw = face_halfwidth(yy)
        d = min(abs(x + 0.5 - (W / 2 - hw)), abs(x + 0.5 - (W / 2 + hw)))
        if d < 1.6 and yy > 38:
            g.set(x, y, "s")
    for (x, y) in edge(pix):
        if y + lift >= 50:
            g.set(x, y, "X")
    for y in range(CHIN - 1, CHIN + 5):
        for x in range(43, 53):
            if g.get(x, y - lift) == ".":
                g.set(x, y - lift, "x" if y < CHIN + 2 else "s")
    return pix


def bang_line(x):
    """How far down the bangs reach: a lock falling between the eyes from the middle parting, a level
    edge with small points over the eyes (her brows under it), sloping down only at the sides."""
    dx = abs(x + 0.5 - W / 2)
    if dx < 1.5:
        return 40.5                                   # the lock between her eyes
    if dx < 5.0:
        return 31.0 + (dx - 1.5) * 1.3                # the parting
    if dx < 13.0:
        return 35.5 + 1.3 * max(0.0, math.cos((dx - 9.0) / 2.0 * math.pi)) ** 2
    return 36.0 + (dx - 13.0) * 0.7


def draw_bangs(g, lift=0):
    pix = set()
    for x in range(26, 70):
        yb = bang_line(x)
        for y in range(8, int(math.floor(yb)) + 1):
            pix.add((x, y - lift))
    locks = set()
    for side in (0, 1):
        lock = lifted(poly([(27, 36), (32, 38), (33.5, 50), (33, 60), (31.5, 67), (30, 60), (28, 50)]), lift)
        locks |= lock if side == 0 else mirrored(lock)
    top = lifted(ellipse(48, 34, 28.5, 28.5), lift)
    pix = {p for p in pix if p in top or p[1] + lift >= 34}
    for (x, y) in pix:
        g.set(x, y, "g")
    crown = (47.5, 6 - lift)
    for tx in (28, 33, 39, 45, 51, 57, 63, 67):
        ty = bang_line(tx) - lift
        n = 60
        for s in range(n + 1):
            t = s / n
            bx = crown[0] + (tx - crown[0]) * t + (tx - crown[0]) * 0.18 * math.sin(t * math.pi)
            by = crown[1] + (ty - crown[1]) * t
            x, y = int(round(bx)), int(round(by))
            if t > 0.16 and (x, y) in pix:
                g.set(x, y, "G")
    # the lock between her eyes, and the part above it
    for y in range(26, 41):
        if (47, y - lift) in pix:
            g.set(47, y - lift, "G")
        if (48, y - lift) in pix:
            g.set(48, y - lift, "h" if y < 38 else "G")
    for x in range(20, 76):
        y = 15 - lift + round(((x - 47.5) / 26.0) ** 2 * 5.0)
        for (xx, yy, k) in ((x, y, "h"), (x, y + 1, "h" if x % 3 else "g")):
            if g.get(xx, yy) in ("g", "G"):
                g.set(xx, yy, k)
    for (x, y) in ((33, 17), (34, 17), (61, 17), (62, 17), (46, 14), (47, 14)):
        g.set(x, y - lift, "H")
    for x in range(26, 70):
        yb = int(math.floor(bang_line(x))) - lift
        if (x, yb) in pix:
            g.set(x, yb, "O")
            if g.get(x, yb - 1) in ("g", "h"):
                g.set(x, yb - 1, "D")
        if g.get(x, yb + 1) == "S":
            g.set(x, yb + 1, "s")
    for (x, y) in locks:
        g.set(x, y, "G" if (x + y) % 4 else "D")
    outline(g, locks, "O")


def draw_ties(g, lift=0):
    """Dark ties with a gold bead where the twin tails are gathered."""
    for side in (0, 1):
        x = 17 if side == 0 else mirror(17) - 5
        g.draw([".QNNQ.", "QNMMNQ", "QNyYNQ", ".QNNQ."], x, 16 - lift)


def head(g, lift=0):
    back = draw_back_hair(g, lift)
    draw_ears(g, lift)
    draw_face(g, lift)
    draw_bangs(g, lift)
    for (x, y) in back:
        if g.get(x, y) in ("g", "G", "h", "H", "D") and any(g.get(x + dx, y + dy) == "." for dx, dy in ((1, 0), (-1, 0), (0, -1))):
            g.set(x, y, "O")
    draw_ties(g, lift)


# ---------------------------------------------------------------- face parts

EYE_W, EYE_H = 14, 13


def eye_rows(variant):
    """The viewer's left eye, EYE_W x EYE_H: calm, the upper lid low over a big green iris (dark teal at
    the top, bright green at the bottom), a thick lash flicked out at the outer corner, two glints;
    closed and happy eyes are lash curves."""
    g = [["." for _ in range(EYE_W)] for _ in range(EYE_H)]

    def put(x, y, k):
        if 0 <= x < EYE_W and 0 <= y < EYE_H:
            g[y][x] = k

    if variant in ("open", "half", "stars"):
        top = 3 if variant != "half" else 6
        for y in range(top + 1, 12):
            for x in range(1, 13):
                inside = ((x + 0.5 - 7.0) / 5.7) ** 2 + ((y + 0.5 - 7.4) / 4.6) ** 2 <= 1.0
                if not inside:
                    continue
                iris = ((x + 0.5 - 7.3) / 3.9) ** 2 + ((y + 0.5 - 7.6) / 4.2) ** 2 <= 1.0
                if iris:
                    t = (y - 4) / 7.0
                    k = "1" if t < 0.18 else "2" if t < 0.4 else "3" if t < 0.62 else "4" if t < 0.84 else "5"
                    put(x, y, k)
                else:
                    put(x, y, "w" if y <= top + 1 else "W")
        if variant != "stars":
            for y in range(top + 1, 12):
                for x in range(5, 11):
                    if ((x + 0.5 - 7.6) / 1.4) ** 2 + ((y + 0.5 - 6.9) / 2.2) ** 2 <= 1.0 and g[y][x] in "12345":
                        put(x, y, "p")
            for (x, y) in ((5, 5), (6, 5), (5, 6), (9, 9), (4, 8)):
                if y > top and g[y][x] in "12345p":
                    put(x, y, "W")
        else:
            for (x, y) in ((7, 5), (6, 6), (7, 6), (8, 6), (7, 7), (5, 9), (4, 8), (9, 8), (10, 9), (9, 10)):
                if g[y][x] in "12345":
                    put(x, y, "W")
        # the upper lid: a thick lash across, flicked out at the outer (left) corner
        for x in range(1, 13):
            put(x, top, "K")
        for x in (2, 3, 11, 12):
            put(x, top + 1, "K")
        put(0, top - 1, "K")
        put(1, top - 1, "K")
        put(12, top + 1, "k")
        if variant != "half":
            for x in range(4, 10):
                put(x, top - 2, "j")
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


EYE_L = (30, 36)
EYE_R = (W - EYE_L[0] - EYE_W, 36)


def put_eyes(g, variant, lift=0):
    for (ex, ey) in (EYE_L, EYE_R):
        for j in range(EYE_H):
            for i in range(EYE_W):
                if g.get(ex + i, ey + j - lift) in "KkjWw12345p":
                    g.set(ex + i, ey + j - lift, "S")
    rows = eye_rows(variant)
    g.draw(rows, EYE_L[0], EYE_L[1] - lift)
    g.draw([r[::-1] for r in rows], EYE_R[0], EYE_R[1] - lift)
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
    g.draw([r[::-1] for r in blush], mirror(31) - 7, 51 - lift, only_on=("S", "s"))
    g.set(47, 53 - lift, "x")


# ---------------------------------------------------------------- shoulders

def draw_capelet(g, lift=0):
    """A white capelet over her shoulders, edged with gold, a dark high collar with a gold clasp and a
    red gem (the portrait shows its top)."""
    cape = lifted(poly([(35, 62), (60, 62), (66, 68), (68, 77), (59, 80), (49, 78), (46, 78), (36, 80), (27, 77),
                        (29, 68)]), lift)
    for (x, y) in cape:
        g.set(x, y, "V" if x < 44 else "v" if x < 58 else "U")
    for (x, y) in edge(cape):
        g.set(x, y, "T" if (x, y + 1) not in cape else "y")
    for y in range(66, 79):
        if (47, y - lift) in cape:
            g.set(47, y - lift, "Y")
            g.set(48, y - lift, "y")
    g.draw([
        "..QNNNNNQ..",
        ".QNMNNNMNQ.",
        "..QyNNNyQ..",
        "...yYaYy...",
        "....tZt....",
    ], 43, 62 - lift)


def figure(lift=0):
    """Her head and shoulders on the figure's grid."""
    g = Grid(W, H)
    draw_twin_tails(g, lift)
    hd = Grid(W, H)
    head(hd, lift)
    put_eyes(hd, "open", lift)
    put_blush_and_nose(hd, lift)
    put_mouth(hd, "smile", lift)
    for y in range(H):
        for x in range(W):
            k = hd.get(x, y)
            if k != ".":
                g.set(x, y, k)
    draw_capelet(g, lift)
    return g


def figure_image(eyes="open", mouth="smile"):
    """Her head and shoulders as an image (the figure's grid), for the logo."""
    g = figure()
    put_eyes(g, eyes)
    put_mouth(g, mouth)
    cv = Canvas(W, H)
    g.to_canvas(cv)
    return cv.img


# ---------------------------------------------------------------- the portrait

def inside(x, y):
    """Whether a portrait pixel is inside the frame."""
    return FRAME <= x < PW - FRAME and FRAME <= y < PH - FRAME


LIGHT_DIR = (-0.62, -0.78)
CORNER_R = 3


def frame(g):
    """The frame: the GUI panel's edge in miniature, with rounded corners and light from the top-left:
    a dark outline, two rows of green, a navy bevel that is sunk (lit from the other side)."""
    for y in range(PH):
        for x in range(PW):
            if inside(x, y):
                continue
            depth, nx, ny = rrect(x, y, 0, 0, PW, PH, CORNER_R)
            if depth <= 0:
                continue
            ring = int(depth)
            light = nx * LIGHT_DIR[0] + ny * LIGHT_DIR[1]
            hi, lo = light > 0.35, light < -0.35
            if ring == 0:
                k = "-"
            elif ring == 1:
                k = ":" if hi else ("," if lo else ";")
            elif ring == 2:
                k = ";" if hi else ('"' if lo else ",")
            else:
                k = "\\" if hi else ("~" if lo else "$")
            g.set(x, y, k)


def background(g):
    """The inside: the GUI panel's navy, darker at the bottom, a faint ring of runes behind her head."""
    for y in range(PH):
        for x in range(PW):
            if inside(x, y):
                t = y / PH
                c = "#" if t < 0.4 else ("$" if t < 0.75 else "|")
                r = math.hypot(x + 0.5 - PW / 2, (y + 0.5 - 34) * 1.05)
                if abs(r - 31.5) < 0.55 or (abs(r - 34.5) < 0.5 and (x + y) % 2 == 0):
                    c = "@"
                g.set(x, y, c)


def light_at(cv, x, y):
    """A little gold light, as on the GUI's vines."""
    for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1), (-2, 0), (2, 0), (0, -2), (0, 2)):
        cv.set(x + dx, y + dy, st.Y2, 70)
    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        cv.set(x + dx, y + dy, st.Y1)
    cv.set(x, y, st.Y0)


# the lights and the mana gem on the frame (the screen twinkles them; kept in step with client/Mascot.java)
LIGHT_SPOTS = [(81, 4), (7, 69)]
GEM_SPOT = (44, 71)


def decorate(cv, ox, oy):
    """Vines curl over the frame's top-left and bottom-right corners, gold lights sit in the other two,
    and a mana gem hangs on the bottom edge in a little gold clasp. (ox, oy) is the portrait's place on the sheet."""
    tones = (st.G1, st.G2, st.G3)
    top = V.walk(3.0, 2.2, 0.05, 26, V.wave(0.16, 24))
    specs = V.chain_specs(top, 0, 20, tones, length=11, pitch=6.5, r=1.6, seed=5)
    ex, ey, eth = V.at(top, 19)
    specs.append(V.curl_spec(ex, ey, eth, 20, 6.5, 1.4, 0.55, tones))
    left = V.walk(2.2, 3.0, math.pi / 2 - 0.05, 20, V.wave(0.16, 20, 0.25))
    specs.append(V.hook_spec(left, 8, 1, 10, 4.0, tones, r0=0.95, r1=0.5))
    specs += V.chain_specs(left, 0, 14, tones, length=10, pitch=6.5, r=1.5, seed=6)
    V.paint_specs(cv, specs, st.VOUT, dx=ox, dy=oy)
    V.paint_specs(cv, specs, st.VOUT, fx=True, fy=True, cx=PW / 2.0, cy=PH / 2.0, dx=ox, dy=oy)
    for (x, y) in LIGHT_SPOTS:
        light_at(cv, ox + x, oy + y)
    gx, gy = ox + GEM_SPOT[0], oy + GEM_SPOT[1]
    for (dx, dy, c) in ((-2, -1, st.Y3), (-1, -2, st.Y2), (0, -2, st.Y1), (1, -2, st.Y2), (2, -1, st.Y3),
                        (-2, 0, st.Y3), (2, 0, st.Y3), (-1, 0, st.M4), (0, 0, st.M2), (1, 0, st.M4),
                        (-1, -1, st.M3), (0, -1, st.M1), (1, -1, st.M3), (-1, 1, st.Y3), (0, 1, st.Y2), (1, 1, st.Y3)):
        cv.set(gx + dx, gy + dy, c)


def portrait(lift=0, eyes="open", mouth="smile"):
    g = Grid(PW, PH)
    background(g)
    fig = figure(lift)
    if eyes != "open":
        put_eyes(fig, eyes, lift)
    if mouth != "smile":
        put_mouth(fig, mouth, lift)
    ox, oy = CROP
    for y in range(PH):
        for x in range(PW):
            if not inside(x, y):
                continue
            k = fig.get(x - FRAME + ox, y - FRAME + oy)
            if k != ".":
                g.set(x, y, k)
    frame(g)
    return g


# ---------------------------------------------------------------- the sheet

EYE_VARIANTS = ("open", "half", "shut", "happy", "stars")
MOUTH_VARIANTS = ("smile", "open", "happy")


def sheet():
    cv = Canvas(SHEET_W, SHEET_H)
    for f in (0, 1):
        portrait(lift=f).to_canvas(cv, PW * f, 0)
        decorate(cv, PW * f, 0)
    ex, ey, ew, eh = EYES_BOX
    for i, v in enumerate(EYE_VARIANTS):
        portrait(eyes=v).crop(ex, ey, ew, eh).to_canvas(cv, EYES_UV[0], EYES_UV[1] + i * eh)
    mx, my, mw, mh = MOUTH_BOX
    for i, v in enumerate(MOUTH_VARIANTS):
        portrait(mouth=v).crop(mx, my, mw, mh).to_canvas(cv, MOUTH_UV[0], MOUTH_UV[1] + i * mh)
    return cv


def compose(img, frame_no=0, eyes=0, mouth=0):
    """Puts a portrait together from the sheet the way client/Mascot.java does."""
    lift = frame_no
    f = Image.new("RGBA", (PW, PH), (0, 0, 0, 0))
    f.alpha_composite(img.crop((PW * frame_no, 0, PW * frame_no + PW, PH)), (0, 0))
    ex, ey, ew, eh = EYES_BOX
    f.alpha_composite(img.crop((EYES_UV[0], EYES_UV[1] + eyes * eh, EYES_UV[0] + ew, EYES_UV[1] + eyes * eh + eh)), (ex, ey - lift))
    mx, my, mw, mh = MOUTH_BOX
    f.alpha_composite(img.crop((MOUTH_UV[0], MOUTH_UV[1] + mouth * mh, MOUTH_UV[0] + mw, MOUTH_UV[1] + mouth * mh + mh)), (mx, my - lift))
    return f


def preview(cv):
    out = os.path.join(ROOT, "build", "preview")
    os.makedirs(out, exist_ok=True)
    frames = [compose(cv.img, 0, 0, 0), compose(cv.img, 1, 1, 1), compose(cv.img, 0, 3, 2), compose(cv.img, 0, 4, 2),
              compose(cv.img, 0, 2, 0)]
    bg = Image.new("RGBA", (len(frames) * (PW + 6) + 6, PH + 12), (40, 40, 40, 255))
    for i, f in enumerate(frames):
        bg.alpha_composite(f, (6 + i * (PW + 6), 6))
    upscale(bg, 4).save(os.path.join(out, "mascot.png"))
    upscale(bg.crop((0, 0, PW + 12, PH + 12)), 8).save(os.path.join(out, "mascot_big.png"))


def main():
    cv = sheet()
    cv.save(os.path.join(ASSETS, "textures", "gui", "mascot.png"))
    if "--preview" in sys.argv:
        preview(cv)


if __name__ == "__main__":
    main()
