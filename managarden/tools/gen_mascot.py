"""The garden keeper: a little anime girl in a dress of leaves and mana, for the greenhouse GUI.

    python3 tools/gen_mascot.py [--preview]

Writes textures/gui/mascot.png (256x128):
  (0,0)    body frame 0, 64x112: without her free arm, eyes open, smiling
  (64,0)   body frame 1: the same with her head a pixel up (breathing)
  (128,0)  eyes, 22x10 each, one under the other: open, half, shut, happy, starry
  (152,0)  mouths, 6x4 each: smile, open, happy
  (160,0)  her free arm, 28x40 each: resting on the GUI's frame, waving (two frames)
The screen (client/Mascot.java) puts them together at the places noted there.

She is drawn in parts from little pixel maps and shapes, back to front.
"""
import math
import os
import sys

from PIL import Image

from pix import Canvas, ASSETS, hexc, upscale, ROOT

W, H = 64, 112

PAL = {
    # outlines
    "O": hexc("1C3F3C"),   # hair outline (deep teal)
    "K": hexc("2B1A2E"),   # dark line (lashes)
    "X": hexc("C07A68"),   # skin outline
    # hair: mint green to cyan tips
    "H": hexc("EFFFEE"), "h": hexc("B9F4C4"), "g": hexc("84DDAA"), "G": hexc("55BD93"), "D": hexc("348C77"),
    "c": hexc("A4F4FF"), "C": hexc("56D2F0"), "q": hexc("2E9CC4"),
    # skin
    "S": hexc("FFF1E8"), "s": hexc("FFDFCF"), "x": hexc("F6BFA8"), "z": hexc("E39F88"), "b": hexc("FFA2B6"),
    # eyes
    "k": hexc("6A4460"), "w": hexc("FFFFFF"), "e": hexc("EAF4FF"),
    "i": hexc("86F2FF"), "I": hexc("33B4EA"), "J": hexc("1E6CC0"), "p": hexc("0E2F5E"),
    "m": hexc("B8455E"), "n": hexc("FF8FA3"),
    # dress: white with leaves and mana
    "W": hexc("FFFFFF"), "V": hexc("E8F0FA"), "U": hexc("C2D0E4"), "u": hexc("8E9EBD"),
    "L": hexc("A8E563"), "l": hexc("6DC043"), "M": hexc("45922F"), "N": hexc("2A6428"), "Q": hexc("173D1C"),
    "A": hexc("A6F6FF"), "a": hexc("55D9F7"), "B": hexc("2A9FE2"), "Z": hexc("1B64B8"),
    # livingwood (belt, boots, wand)
    "T": hexc("A2512A"), "t": hexc("7A3314"), "r": hexc("5E240B"), "R": hexc("310B04"),
    # flowers
    "P": hexc("FFF0F8"), "F": hexc("FFC4E2"), "f": hexc("EE8FC2"), "E": hexc("C45A97"), "Y": hexc("FFD84A"), "y": hexc("D69A1C"),
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
                if ch == "_":           # explicitly clear
                    self.set(x + i, y + j, ".")
                    continue
                if only_on is not None and self.get(x + i, y + j) not in only_on:
                    continue
                self.set(x + i, y + j, ch)

    def paste(self, other, x, y, keep=None):
        for j in range(other.h):
            for i in range(other.w):
                k = other.get(i, j)
                if k != "." and (keep is None or keep(i, j, k)):
                    self.set(x + i, y + j, k)

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


def spans_mask(spans):
    pix = set()
    for y, (a, b) in spans.items():
        for x in range(a, b + 1):
            pix.add((x, y))
    return pix


def poly_mask(points):
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


def edge_of(pix):
    return {(x, y) for (x, y) in pix if any((x + dx, y + dy) not in pix for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}


def outline_set(g, pix, k):
    for (x, y) in edge_of(pix):
        g.set(x, y, k)


def outline(g, key, skip=()):
    """Every filled pixel next to an empty one (4-neighbours), not of a skipped kind, becomes key."""
    marks = []
    for y in range(g.h):
        for x in range(g.w):
            k = g.get(x, y)
            if k == "." or k in skip:
                continue
            if any(g.get(x + dx, y + dy) == "." for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                marks.append((x, y))
    for (x, y) in marks:
        g.set(x, y, key)


# ---------------------------------------------------------------- head: 40 x 46, face centre at x = 20

def head():
    g = Grid(40, 46)
    hair = {4: (15, 24), 5: (12, 27), 6: (10, 29), 7: (8, 31), 8: (7, 32), 9: (6, 33), 10: (5, 34), 11: (4, 35),
            12: (4, 35), 13: (3, 36), 14: (3, 36), 15: (3, 36), 16: (2, 37)}
    for y in range(17, 34):
        hair[y] = (2, 37)
    hair_pix = spans_mask(hair)
    for y in range(34, 46):
        for x in list(range(2, 9)) + list(range(31, 38)):
            hair_pix.add((x, y))
    for (x, y) in hair_pix:
        g.set(x, y, "g")
    face = {18: (9, 30), 19: (8, 31), 20: (8, 31), 21: (8, 31), 22: (8, 31), 23: (8, 31), 24: (8, 31), 25: (8, 31),
            26: (8, 31), 27: (8, 31), 28: (8, 31), 29: (8, 31), 30: (9, 30), 31: (9, 30), 32: (10, 29), 33: (11, 28),
            34: (12, 27), 35: (13, 26), 36: (15, 24), 37: (17, 22), 38: (18, 21)}
    face_pix = spans_mask(face)
    for (x, y) in face_pix:
        g.set(x, y, "S")
    for y in range(37, 46):
        for x in range(17, 23):
            if g.get(x, y) == ".":
                g.set(x, y, "x" if y < 40 else "s")
    for (x, y) in face_pix:
        if x in (8, 31) or (x in (9, 30) and y > 29):
            g.set(x, y, "s")
    for (x, y) in face_pix:
        if (x, y + 1) not in face_pix and y > 30:
            g.set(x, y, "x")
    # bangs: pointed locks hanging over the forehead; the middle one dips between the eyes
    tips = [(9, 24.5), (14, 23.0), (19.5, 26.0), (25, 23.0), (30, 24.5)]

    def bang_bottom(x):
        return max(18.0, max(ty - 1.35 * abs(x + 0.5 - tx) for tx, ty in tips))

    for x in range(8, 32):
        yb = bang_bottom(x)
        for y in range(16, int(math.floor(yb)) + 1):
            g.set(x, y, "g")
        yb_i = int(math.floor(yb))
        g.set(x, yb_i, "O")
        if g.get(x, yb_i - 1) == "g":
            g.set(x, yb_i - 1, "D")
    crown = (19.5, 4.0)
    for (ex, ey) in [(4, 20), (6, 22)] + [(tx, ty - 2) for tx, ty in tips] + [(33, 22), (35, 20)]:
        n = 50
        for s in range(n + 1):
            t = s / n
            bx = crown[0] + (ex - crown[0]) * t + (ex - crown[0]) * 0.22 * math.sin(t * math.pi)
            by = crown[1] + (ey - crown[1]) * t
            x, y = int(round(bx)), int(round(by))
            if t > 0.2 and g.get(x, y) == "g":
                g.set(x, y, "G")
    for x in range(8, 32):
        yb = int(math.floor(bang_bottom(x)))
        if (x, yb + 1) in face_pix:
            g.set(x, yb + 1, "x")
    # the shine band across the top of the head
    for x in range(7, 33):
        y = 9 + round(((x - 19.5) / 12.5) ** 2 * 3.0)
        if g.get(x, y) == "g":
            g.set(x, y, "h")
        if g.get(x, y - 1) == "g" and x % 4 in (1, 2):
            g.set(x, y - 1, "h")
    for (x, y) in ((12, 9), (13, 9), (26, 9), (27, 9), (19, 8), (20, 8)):
        if g.get(x, y) in ("h", "g"):
            g.set(x, y, "H")
    # side locks: strands, shadow by the face, highlight outside (they go on down over her shoulders)
    for y in range(20, 46):
        for (x1, x2, inner, sign) in ((2, 8, 8, -1), (31, 37, 31, 1)):
            wave = round(math.sin(y / 5.0) * 0.6)
            for x in range(x1, x2 + 1):
                if g.get(x, y) not in ("g", "G", "D", "h"):
                    continue
                k = "g"
                if x == inner:
                    k = "D"
                elif x == inner - sign:
                    k = "G"
                elif x == (5 if sign < 0 else 34) + wave:
                    k = "G"
                elif x == (3 if sign < 0 else 36):
                    k = "h" if y < 34 else "g"
                g.set(x, y, k)
    for y in range(18, 34):
        for x in (7, 32):
            if g.get(x, y) == "g":
                g.set(x, y, "G")
    outline(g, "O", skip=("S", "s", "x", "z", "X"))
    # the locks go on below the grid: no outline across their bottom
    for x in list(range(2, 9)) + list(range(31, 38)):
        if g.get(x, 45) == "O" and g.get(x, 44) not in (".", "O"):
            g.set(x, 45, g.get(x, 44))
    for y in range(g.h):
        for x in range(g.w):
            if g.get(x, y) in ("S", "s", "x") and any(g.get(x + dx, y + dy) == "." for dx, dy in ((1, 0), (-1, 0), (0, 1))):
                g.set(x, y, "X")
    sprout = [
        "..ll...lll.",
        ".lLLl.lLLLl",
        ".lLLlMlLLl.",
        "..llMMll...",
        "....Ml.....",
        "....M......",
    ]
    g.draw(sprout, 15, 0)
    # a flower pinned in her hair, over her left temple (the viewer's right), with two leaves
    g.draw([
        "...LL.....",
        "..lLLl.EE.",
        "..NlMEEFFE",
        "...NEFFPFE",
        "..EEFPYPFE",
        ".EFFPYyYPE",
        ".EFFFPYPFE",
        "..EFFFPFE.",
        "...EEEFE..",
        ".....EE...",
    ], 27, 6)
    return g


# eyes: the viewer's left eye; the right one mirrors its shape but keeps the glint on the left
EYES = {
    "open": [
        "..KKKKKK",
        "KKKkkkkK",
        ".KwwJJJK",
        ".KwwJpJK",
        ".KJJppJK",
        ".KIIppIK",
        ".KIIIIiK",
        ".KiiiwiK",
        "..KiiiK.",
        "...kkk..",
    ],
    "half": [
        "........",
        "........",
        "........",
        "..KKKKKK",
        "KKKkkkkK",
        ".KJJppJK",
        ".KIIpIIK",
        ".KiIIwiK",
        "..KiiiK.",
        "...kkk..",
    ],
    "shut": [
        "........",
        "........",
        "........",
        "........",
        "........",
        "........",
        "KK....KK",
        ".KKKKKK.",
        "........",
        "........",
    ],
    "happy": [
        "........",
        "........",
        "........",
        "...KKK..",
        "..KKkKK.",
        ".KK...KK",
        "KK.....K",
        "........",
        "........",
        "........",
    ],
    "stars": [
        "..KKKKKK",
        "KKKkkkkK",
        ".KJJwJJK",
        ".KJwwwJK",
        ".KIIwIIK",
        ".KwIIIwK",
        ".KIIiIIK",
        ".KiwiiiK",
        "..KiiiK.",
        "...kkk..",
    ],
}
EYE_L = (9, 24)          # left eye's corner on the head grid
EYE_R = (23, 24)


def put_eyes(g, variant, ox=0, oy=0):
    """Draws both eyes on the head grid (at ox, oy), clearing the skin under them first."""
    rows = EYES[variant]
    for (ex, ey), flip in ((EYE_L, False), (EYE_R, True)):
        for j in range(10):
            for i in range(8):
                x, y = ox + ex + i, oy + ey + j
                if g.get(x, y) in ("K", "k", "w", "J", "p", "I", "i"):
                    g.set(x, y, "S")
        sh = rows if not flip else [r[::-1] for r in rows]
        # keep the glint on the left in the mirrored eye
        if flip and variant in ("open", "half"):
            sh = [r.replace("w", "I") for r in sh]
            fixed = []
            for j, r in enumerate(sh):
                r = list(r)
                if variant == "open" and j in (2, 3):
                    r[2] = "w"
                    r[3] = "w"
                if variant == "open" and j == 7:
                    r[5] = "w"
                if variant == "half" and j == 7:
                    r[5] = "w"
                fixed.append("".join(r))
            sh = fixed
        g.draw(sh, ox + ex, oy + ey)


MOUTHS = {
    "smile": ["......", ".m..m.", "..mm..", "......"],
    "open": ["......", "..mm..", ".mnnm.", "..mm.."],
    "happy": ["......", ".mmmm.", ".mnnm.", "..mm.."],
}
MOUTH_AT = (17, 34)      # the mouth rect's corner on the head grid (6 x 4)


def put_mouth(g, variant, ox=0, oy=0):
    for j in range(4):
        for i in range(6):
            x, y = ox + MOUTH_AT[0] + i, oy + MOUTH_AT[1] + j
            if g.get(x, y) in ("m", "n"):
                g.set(x, y, "S")
    g.draw(MOUTHS[variant], ox + MOUTH_AT[0], oy + MOUTH_AT[1])


def put_blush(g, ox=0, oy=0):
    g.draw(["bb.", ".bb"], ox + 9, oy + 33)
    g.draw([".bb", "bb."], ox + 28, oy + 33)


# ---------------------------------------------------------------- body (sprite coordinates, 64 x 112)

HEAD_X, HEAD_Y = 12, 4           # where the 40 x 46 head grid goes; face centre x = 32
SHOULDER_Y = 44
FEET_Y = 108                      # soles' line
DOWN = FEET_Y - 104               # everything below the head moves down this much to stand on the ground


def back_hair(g, dy):
    """Long hair falling behind her back, in the hair's shadow tones, ending in mana-cyan points."""
    pix = poly_mask([(17, 40 + dy), (46, 40 + dy), (49, 58 + dy), (48, 74 + dy), (15, 74 + dy), (14, 58 + dy)])
    for (x, y) in pix:
        k = "G"
        if y > 66 + dy:
            k = "C" if y > 70 + dy else "G"
        if x in (22, 41) or (x + y) % 9 == 0:
            k = "D" if y <= 70 + dy else "q"
        g.set(x, y, k)
    for x0 in (15, 20, 26, 36, 41, 46):
        for d in range(3):
            for w in range(3 - d):
                g.set(x0 - 1 + d + w, 74 + dy + d, "C" if w else "q")
    outline_set(g, {(x, y) for y in range(g.h) for x in range(g.w) if g.get(x, y) in ("G", "D", "C", "q")}, "O")


def front_locks(g, dy):
    """The side locks go on from the head down over her shoulders and end in cyan points."""
    for (x1, x2, inner, sign) in ((14, 20, 20, -1), (43, 49, 43, 1)):
        top = HEAD_Y + 46 + dy
        bottom = 72 + dy
        pix = set()
        for y in range(top - 1, bottom + 1):
            t = (y - top) / (bottom - top)
            shrink = int(round(t * 2.2))
            a, b = x1 + (shrink if sign < 0 else 0), x2 - (0 if sign < 0 else shrink)
            if sign < 0:
                a = x1 + int(round(t * 1.5))
                b = x2 - int(round(t * 1.0))
            else:
                a = x1 + int(round(t * 1.0))
                b = x2 - int(round(t * 1.5))
            for x in range(a, b + 1):
                pix.add((x, y))
        for (x, y) in pix:
            t = (y - top) / (bottom - top)
            k = "g"
            if x == inner or x == inner - sign * 0:
                k = "D"
            if x == inner - sign:
                k = "G"
            if x == (17 if sign < 0 else 46) + round(math.sin(y / 5.0) * 0.6):
                k = "G"
            if t > 0.6:
                k = {"g": "c", "G": "C", "D": "q"}[k]
            g.set(x, y, k)
        # the point
        for d in range(3):
            g.set((x1 + x2) // 2 + (d if sign > 0 else -d), bottom + 1 + d, "C" if d < 2 else "q")
        outline_set(g, pix | {((x1 + x2) // 2 + (d if sign > 0 else -d), bottom + 1 + d) for d in range(3)}, "O")


def legs(g):
    """White stockings and livingwood boots with a leaf at the cuff."""
    for (x1, x2) in ((26, 31), (33, 38)):
        for y in range(80 + DOWN, 98):
            for x in range(x1, x2):
                g.set(x, y, "W" if x < x1 + 2 else ("V" if x < x2 - 1 else "U"))
            g.set(x1 - 1, y, "u")
            g.set(x2, y, "u")
        for y in range(98, FEET_Y):
            for x in range(x1 - 1, x2 + 1):
                g.set(x, y, "t" if y < 100 else ("r" if y < FEET_Y - 1 else "R"))
            g.set(x1 - 1, y, "R")
            g.set(x2, y, "R")
        g.set(x1, 99, "T")
        g.set(x1 + 1, 99, "T")
        g.set(x1 - 2, FEET_Y - 1, "R")
        g.set(x2 + 1, FEET_Y - 1, "R")
        g.draw(["Ll", ".lN"], x2 - 1, 96)
        # a garter of leaves round the top of each stocking, just under the frills
        for x in range(x1 - 1, x2 + 1):
            g.set(x, 86, "N" if x in (x1 - 1, x2) else ("L" if x < x1 + 2 else "l"))
            g.set(x, 87, "N" if x in (x1 - 1, x2) else "M")
        # the stockings' knees catch the light
        g.set(x1 + 1, 91, "W")
        g.set(x1 + 2, 91, "W")


def skirt(g):
    """A skirt of five big pointed leaves over white frills with a mana-blue hem."""
    top_y, hem_y = 57 + DOWN, 80 + DOWN
    frill = poly_mask([(24, top_y), (40, top_y), (49, hem_y), (15, hem_y)])
    for (x, y) in frill:
        g.set(x, y, "W" if y < hem_y - 2 else ("V" if y < hem_y - 1 else "a"))
    for x in range(15, 50):
        g.set(x, hem_y, "B" if x % 4 else "Z")
        if x % 4 in (1, 2):
            g.set(x, hem_y + 1, "B")
    outline_set(g, frill | {(x, hem_y) for x in range(15, 50)} | {(x, hem_y + 1) for x in range(15, 50) if x % 4 in (1, 2)}, "u")
    leaves = [(18.5, 19, 0), (45.5, 19, 4), (24.5, 21, 1), (39.5, 21, 3), (32, 22, 2)]
    for (tip_x, length, i) in leaves:
        base_x = 26 + i * 3.0
        pts = [(base_x - 3.0, top_y), (base_x + 3.0, top_y), (tip_x + 4.5, top_y + length - 7),
               (tip_x + 0.5, top_y + length), (tip_x - 3.5, top_y + length - 7)]
        pix = poly_mask(pts)
        for (x, y) in pix:
            t = (y - top_y) / length
            lit = x < base_x + (tip_x - base_x) * t
            g.set(x, y, "L" if lit else "l")
        outline_set(g, pix, "N")
        for s in range(2, length - 1):
            t = s / length
            x = int(math.floor(base_x + (tip_x + 0.5 - base_x) * t))
            y = top_y + s
            if (x, y) in pix and (x, y) not in edge_of(pix):
                g.set(x, y, "M")
    for x in range(23, 42):
        g.set(x, top_y, "N")


def torso(g):
    """Bodice: white, lit from the left, a collar of two leaves and a mana crystal brooch."""
    d = DOWN
    pix = poly_mask([(23, 43), (41, 43), (42, 46), (39, 56 + d), (25, 56 + d), (22, 46)])
    for (x, y) in pix:
        g.set(x, y, "W" if x < 29 else ("V" if x < 37 else "U"))
    outline_set(g, pix, "u")
    for y in range(48, 55 + d):
        g.set(31 + (y % 2), y, "M")
    for x in range(23, 42):
        g.set(x, 54 + d, "T" if x < 30 else "t")
        g.set(x, 55 + d, "t" if x < 30 else "r")
        g.set(x, 56 + d, "R")
    g.draw([".f.", "fYf", ".f."], 36, 53 + d)
    g.draw(["LLLl.", ".lLLl", "..lMN", "...N."], 25, 42)
    g.draw([".lLLL", "lLLl.", "NMl..", ".N..."], 34, 42)
    g.draw([".a.", "aAB", ".Z."], 30, 45)


def wand_arm(g):
    """Her right arm (viewer's left) holds the Wand of the Forest up beside her."""
    for y in range(36, 86):
        g.set(10, y, "t" if y % 7 else "T")
        g.set(11, y, "r")
    g.set(10, 86, "R")
    g.set(11, 86, "R")
    g.draw(["..cc..", ".cAAc.", "cAWWAc", ".cAAc.", "..tt.."], 8, 30)
    g.draw(["LL", "lN"], 12, 38)
    sleeve = poly_mask([(18, 43), (24, 43), (25, 49), (19, 52), (16, 49)])
    for (x, y) in sleeve:
        g.set(x, y, "W" if x < 21 else "V")
    outline_set(g, sleeve, "u")
    g.draw(["LLlll", "NlMlN"], 16, 51)
    arm = poly_mask([(17, 52), (21, 52), (15, 63), (11, 63)])
    for (x, y) in arm:
        g.set(x, y, "S" if x < 15 else "s")
    outline_set(g, arm, "X")
    g.draw([".XXX.", "XSSsX", "XssxX", ".XXX."], 8, 61)


def ground(g):
    """A tuft of grass and two little flowers under her feet."""
    y0 = FEET_Y - 1
    g.draw([
        "..l....L..l.....M.l...L...l....",
        ".lL.l.lLl.Ll.L.lMlL..lLl.lL.l..",
        "lLLlLlLLLlLLlLlLLLLlLLLLlLLlLl.",
        "NlMlNlMlNlMlNlMlNlMlNlMlNlMlN..",
    ], 16, y0)
    g.draw([".F.", "FYF", ".F."], 17, y0 - 2)
    g.draw([".P.", "PYP", ".P."], 43, y0 - 1)


def free_arm(pose):
    """Her left arm (the viewer's right), 28 x 40, drawn over the body at (36, 34)."""
    g = Grid(28, 40)
    ox, oy = 36, 34
    sleeve = poly_mask([(40 - ox, 43 - oy), (46 - ox, 43 - oy), (48 - ox, 49 - oy), (43 - ox, 52 - oy), (39 - ox, 48 - oy)])
    if pose == "rest":
        # reaching over to rest her hand on the GUI's frame
        arm = poly_mask([(44 - ox, 49 - oy), (48 - ox, 49 - oy), (57 - ox, 59 - oy), (54 - ox, 62 - oy)])
        hand = (52 - ox, 58 - oy, ["..XXX.", ".XSSSX", "XSSSSsX", "XssssX.", ".XXXX.."])
    elif pose == "wave1":
        arm = poly_mask([(44 - ox, 47 - oy), (48 - ox, 48 - oy), (52 - ox, 36 - oy), (48 - ox, 35 - oy)])
        hand = (47 - ox, 29 - oy, [".X.X.X", "XSXSXS", "XSSSSX", "XSSSsX", ".XssX.", "..XX.."])
    else:
        arm = poly_mask([(44 - ox, 47 - oy), (48 - ox, 48 - oy), (54 - ox, 37 - oy), (50 - ox, 35 - oy)])
        hand = (50 - ox, 29 - oy, ["X.X.X.", "SXSXSX", "XSSSSX", "XsSSSX", ".XssX.", "..XX.."])
    for (x, y) in arm:
        g.set(x, y, "S")
    outline_set(g, arm, "X")
    hx, hy, rows = hand
    g.draw(rows, hx, hy)
    for (x, y) in sleeve:
        g.set(x, y, "V" if x < 44 - ox else "U")
    outline_set(g, sleeve, "u")
    g.draw(["lllLL", "NlMlN"], 41 - ox, 51 - oy)
    return g


def body_frame(breath):
    g = Grid(W, H)
    lift = 1 if breath else 0
    back_hair(g, -lift)
    legs(g)
    skirt(g)
    torso(g)
    wand_arm(g)
    front_locks(g, -lift)
    ground(g)
    hd = head()
    put_eyes(hd, "open")
    put_mouth(hd, "smile")
    put_blush(hd)
    for y in range(hd.h):
        for x in range(hd.w):
            k = hd.get(x, y)
            if k == ".":
                continue
            yy = HEAD_Y + y - lift
            # the neck ends in the collar
            if yy >= SHOULDER_Y - 1 and k in ("s", "x", "X", "S"):
                continue
            g.set(HEAD_X + x, yy, k)
    return g


def face_parts():
    """The eye and mouth overlays, cut from the head drawn with each variant (so they carry the hair
    and skin round them too)."""
    eyes, mouths = [], []
    for v in ("open", "half", "shut", "happy", "stars"):
        hd = head()
        put_eyes(hd, v)
        put_mouth(hd, "smile")
        put_blush(hd)
        eyes.append(hd.crop(EYE_L[0], EYE_L[1], EYE_R[0] + 8 - EYE_L[0], 10))
    for v in ("smile", "open", "happy"):
        hd = head()
        put_eyes(hd, "open")
        put_mouth(hd, v)
        put_blush(hd)
        mouths.append(hd.crop(MOUTH_AT[0], MOUTH_AT[1], 6, 4))
    return eyes, mouths


def sheet():
    cv = Canvas(256, 128)
    for f in (0, 1):
        body_frame(f == 1).to_canvas(cv, 64 * f, 0)
    eyes, mouths = face_parts()
    for i, e in enumerate(eyes):
        e.to_canvas(cv, 128, i * 10)
    for i, m in enumerate(mouths):
        m.to_canvas(cv, 152, i * 4)
    for i, pose in enumerate(("rest", "wave1", "wave2")):
        free_arm(pose).to_canvas(cv, 160 + 28 * i, 0)
    return cv


def preview(cv):
    """The frames put together as the screen does, on the panel's colour, 6x."""
    out = os.path.join(ROOT, "build", "preview")
    os.makedirs(out, exist_ok=True)
    img = cv.img
    frames = []
    for (body, eyes, mouth, arm) in ((0, 0, 0, 0), (1, 1, 1, 1), (0, 3, 2, 2), (0, 4, 2, 0), (0, 2, 0, 0)):
        f = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        f.alpha_composite(img.crop((64 * body, 0, 64 * body + 64, 112)), (0, 0))
        lift = 1 if body == 1 else 0
        f.alpha_composite(img.crop((128, eyes * 10, 150, eyes * 10 + 10)), (HEAD_X + EYE_L[0], HEAD_Y + EYE_L[1] - lift))
        f.alpha_composite(img.crop((152, mouth * 4, 158, mouth * 4 + 4)), (HEAD_X + MOUTH_AT[0], HEAD_Y + MOUTH_AT[1] - lift))
        f.alpha_composite(img.crop((160 + 28 * arm, 0, 188 + 28 * arm, 40)), (36, 34))
        frames.append(f)
    bg = Image.new("RGBA", (len(frames) * (W + 6) + 6, H + 12), (29, 76, 79, 255))
    for i, f in enumerate(frames):
        bg.alpha_composite(f, (6 + i * (W + 6), 6))
    upscale(bg, 4).save(os.path.join(out, "mascot.png"))


def main():
    cv = sheet()
    cv.save(os.path.join(ASSETS, "textures", "gui", "mascot.png"))
    if "--preview" in sys.argv:
        preview(cv)


if __name__ == "__main__":
    main()
