"""The first-generation machines' GUI panels in the living-wood style, all on the same layout (layout.py): livingwood planks
framed with livingrock, mana crystals on the corners, the input slots on the left (livingrock rims), the
output slots on the right (gold rims), a special slot (mana-blue rim) under the heart where a machine has one,
the arrows, the mana bar, the player's inventory on a panel hanging under it. Each machine's heart is drawn
in the middle (the machines with looks of their own are gen_v2.py's):

  petal_farm.png    a livingwood planter of soil on short legs over the bone meal slot, a dotted arc over it
                    that the sun runs along

    python3 tools/machines/gen_gui.py

The moving parts (the forming rune, the lit runes, the orbiting items, the light, the mana, the gems, the
lit arrows, the twinkle) are drawn over the panels by the machines' screens; check_layout.py keeps them in step.
"""
import math
import os
import sys

from pix import Canvas, ASSETS, mix, shade, hexc, rnd2
from layout import (M, W, H, MACHINE_H, INV_X1, INV_X2, INPUT, OUTPUT, INV_Y, HOTBAR_Y, INV_SLOT_X, ARROWS, BAR,
                    LIGHTS, FARM, MINE, FIELD)

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "lib"))
import wood as WD  # noqa: E402

WOOD, LR, MANA, GOLD = WD.WOOD, WD.LR, WD.MANA, WD.GOLD
OUTLINE = WOOD[6]
LIGHT_DIR = (-0.62, -0.78)
HOLLOW = (hexc("12345E"), hexc("060E1C"))
GRASS = [hexc(h) for h in ("9BE36A", "6FC24A", "4E9E38", "37782C", "245420")]
SOIL = [hexc(h) for h in ("5A3B22", "43291A", "2E1C12", "1C110B")]
PETALS = [hexc(h) for h in ("FFFFFF", "FFB3DE", "FFE066", "B9A3FF", "8FD8FF", "FF8A80")]
ROCK = [hexc(h) for h in ("A9A9A9", "8E8E8E", "767676", "5E5E5E", "434343")]
ORES = [hexc(h) for h in ("E8B37F", "D8D8D8", "FCEE4B", "5DECF5", "17DD62", "345EC3", "FF2A2A")]


# ---------------------------------------------------------------- shared

ARROW = ["##....##...",
         ".##....##..",
         "..##....##.",
         "...##....##",
         "....##....#",
         "...##....##",
         "..##....##.",
         ".##....##..",
         "##....##..."]


def arrows(cv):
    """Two dim double chevrons, into the heart and out of it; the screen lights them."""
    for (ax, ay) in ARROWS:
        for j, row in enumerate(ARROW):
            for i, ch in enumerate(row):
                if ch == "#":
                    cv.set(ax + i, ay + j, mix(WOOD[4], LR[2], 0.35))


def mana_bar_track(cv):
    x1, y1, x2, y2 = BAR
    WD.bar_track(cv, x1, y1, x2, y2)
    for x in range(x1 + 14, x2, 14):
        cv.vline(x, y1 + 1, y2, hexc("15283A"))
    drop = ["..#..",
            ".#c#.",
            "#cCc#",
            "#cCC#",
            "#ccC#",
            ".###."]
    pal = {"#": MANA[5], "c": MANA[3], "C": MANA[1]}
    for j, row in enumerate(drop):
        for i, ch in enumerate(row):
            if ch != ".":
                cv.set(x1 - 12 + i, y1 - 2 + j, pal[ch])


def grids(cv):
    for i in range(9):
        WD.slot(cv, INPUT[0] + i % 3 * 18, INPUT[1] + i // 3 * 18, WD.SLOT_IN)
        WD.slot(cv, OUTPUT[0] + i % 3 * 18, OUTPUT[1] + i // 3 * 18, WD.SLOT_OUT)


def inventory(cv):
    """The player's inventory on a livingwood panel hanging under the machine panel."""
    WD.panel(cv, INV_X1, MACHINE_H - 14, INV_X2, H, inner=False)
    for r in range(3):
        for c in range(9):
            WD.slot(cv, INV_SLOT_X + c * 18, INV_Y + r * 18, WD.SLOT_INV)
    for c in range(9):
        WD.slot(cv, INV_SLOT_X + c * 18, HOTBAR_Y, WD.SLOT_INV)


def decorations(cv):
    for (x, y) in LIGHTS:
        WD.crystal(cv, x, y)


def gem_socket(cv, gx, gy):
    """A gold setting round the pixel (gx, gy) with a dark hole (the screen sets the 7x7 gem in it)."""
    for dy in range(-5, 6):
        for dx in range(-5, 6):
            d = math.hypot(dx, dy)
            if d <= 4.6:
                cv.set(gx + dx, gy + dy, (GOLD[1] if dx + dy < 0 else GOLD[3]) if d > 3.6 else hexc("0A1A12"))
    for (dx, dy) in ((-5, 0), (5, 0), (0, -5), (0, 5)):
        cv.set(gx + dx, gy + dy, OUTLINE)


def hollow_color(d, r, x, y, salt):
    """The dark deep of a hollow (d from its middle, r its radius) with a few faint stars."""
    c = mix(HOLLOW[0], HOLLOW[1], min(1.0, d / r) ** 0.8)
    if rnd2(x, y, salt) < 0.05:
        c = mix(c, MANA[2], 0.4)
    return c


def stem(cv, cx, y1, y2, w, gold_rows=()):
    """A livingrock column (x = cx - w/2 .. cx + w/2) from y1 down to y2, rounded by its light, gold bands across."""
    x1, x2 = cx - w // 2, cx + w // 2
    for y in range(y1, y2):
        for x in range(x1, x2):
            if x in (x1, x2 - 1):
                c = OUTLINE
            else:
                t = (x - x1 - 1) / float(max(1, x2 - x1 - 3))
                c = LR[0] if t < 0.18 else (LR[1] if t < 0.6 else (LR[2] if t < 0.85 else LR[3]))
                if (y - y1) in gold_rows:
                    c = GOLD[1] if t < 0.3 else (GOLD[2] if t < 0.75 else GOLD[3])
            cv.set(x, y, c)


def special_slot(cv, x, y):
    WD.slot(cv, x, y, WD.SLOT_MANA)


def base(cv, heart):
    """A machine's panel: the inventory panel, the machine panel, its heart, the slots, arrows, bar, crystals."""
    inventory(cv)
    WD.panel(cv, 0, 0, W, MACHINE_H)
    heart(cv)
    grids(cv)
    arrows(cv)
    mana_bar_track(cv)
    decorations(cv)


# ---------------------------------------------------------------- runes and sockets

# the first Runic Altar's runes, 5x5: water, fire, earth, air, mana, spring, summer, winter (the shared sheet has
# them lit, in order)
ALTAR_GLYPHS = [
    ["..#..", ".###.", "##.##", "#...#", ".###."],
    ["..#..", ".#.#.", ".#..#", "#.#.#", ".###."],
    [".....", "..#..", ".#.#.", "#...#", "#####"],
    ["###..", "...#.", ".###.", "#....", ".####"],
    ["..#..", ".#.#.", "#.#.#", ".#.#.", "..#.."],
    ["#...#", ".#.#.", "..#..", "..#..", ".###."],
    ["#.#.#", ".###.", "##.##", ".###.", "#.#.#"],
    ["#.#.#", ".###.", "#####", ".###.", "#.#.#"]]


def socket(cv, sx, sy, r):
    """A little round livingrock socket with a dark hollow (an ingredient hovers over it)."""
    for y in range(int(sy - r) - 1, int(sy + r) + 2):
        for x in range(int(sx - r) - 1, int(sx + r) + 2):
            px, py = x + 0.5 - sx - 0.5, y + 0.5 - sy - 0.5
            d = math.hypot(px, py)
            if d > r:
                continue
            lit = (px * LIGHT_DIR[0] + py * LIGHT_DIR[1]) / max(d, 0.001)
            if d > r - 1:
                c = OUTLINE
            elif d > r - 2.5:
                c = LR[0] if lit > 0.3 else (LR[3] if lit < -0.3 else LR[1])
            elif d > r - 3.2:
                c = OUTLINE
            else:
                c = hollow_color(d, r - 3.2, x, y, 71)
            cv.set(x, y, c)


def ellipse_k(px, py, rx, ry):
    return (px / rx) ** 2 + (py / ry) ** 2


# ---------------------------------------------------------------- the Petal Farm

def farm(cv):
    x1, soil, x2, bottom = FARM.BED
    top = FARM.SOIL_TOP
    sx, sy = FARM.SUN
    # the sun's path: a dotted arc carved over the bed
    steps = int(FARM.SUN_R * math.pi * 1.2)
    for k in range(steps + 1):
        if k % 3:
            continue
        a = math.pi + math.pi * k / steps
        x, y = int(round(sx + math.cos(a) * FARM.SUN_R - 0.5)), int(round(sy + math.sin(a) * FARM.SUN_R - 0.5))
        cv.set(x, y, mix(LR[2], WOOD[2], 0.4))
        cv.set(x, y + 1, mix(WOOD[5], WOOD[3], 0.3))
    # legs, then the soil seen over the planter's front, then its front of planks
    for lx in (x1 + 3, x2 - 7):
        for y in range(bottom, bottom + 8):
            for x in range(lx, lx + 4):
                c = OUTLINE if x in (lx, lx + 3) or y == bottom + 7 else (WOOD[2] if x == lx + 1 else WOOD[4])
                cv.set(x, y, c)
    for y in range(top, soil):
        for x in range(x1 + 1, x2 - 1):
            t = (y - top) / float(max(1, soil - top - 1))
            c = mix(SOIL[2], SOIL[0], t)
            if rnd2(x, y, 97) < 0.18:
                c = mix(c, SOIL[3], 0.6)
            if y == top and rnd2(x, y, 98) < 0.5:
                c = GRASS[2]
            cv.set(x, y, c)
    for x in range(x1, x2):
        cv.set(x, top - 1, OUTLINE)
    for y in range(top - 1, bottom + 1):
        cv.set(x1, y, OUTLINE)
        cv.set(x2 - 1, y, OUTLINE)
    for y in range(soil, bottom + 1):
        for x in range(x1, x2):
            if x in (x1, x2 - 1) or y == bottom:
                c = OUTLINE
            elif y in (soil, soil + 1):
                c = LR[0] if y == soil else LR[2]
            elif y in (bottom - 5, bottom - 4):
                c = GOLD[1] if y == bottom - 5 else GOLD[3]
            elif x in (x1 + 1, x1 + 2, x2 - 3, x2 - 2):
                c = WOOD[1] if x in (x1 + 1, x2 - 3) else WOOD[4]
            else:
                c = WD.planks(x, y * 2, True)
            cv.set(x, y, c)
    gem_socket(cv, *FARM.GEM)
    special_slot(cv, *FARM.SLOT)


# ---------------------------------------------------------------- the Orechid Mine

def rock_window(cv):
    """The window into the rock: a dark outline, a gold rim, a band of livingrock, then rough stone with flecks
    of ore in it (the screen draws the block being turned over it, and its cracks)."""
    cx, cy = MINE.CENTER
    r, rr = MINE.R, MINE.ROCK_R
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(px, py)
            if d > r:
                continue
            lit = (px * LIGHT_DIR[0] + py * LIGHT_DIR[1]) / max(d, 0.001)
            hi, lo = lit > 0.35, lit < -0.35
            if d > r - 1:
                c = OUTLINE
            elif d > r - 3:
                c = GOLD[1] if hi else (GOLD[3] if lo else GOLD[2])
            elif d > rr + 0.5:
                c = LR[0] if hi else (LR[2] if lo else LR[1])
            elif d > rr - 0.5:
                c = OUTLINE
            else:
                n = WD.noise(x, y, 2.5, 101)
                c = ROCK[1] if n > 0.6 else (ROCK[2] if n > 0.3 else ROCK[3])
                if rnd2(x, y, 102) < 0.05:
                    c = ORES[int(rnd2(x, y, 103) * len(ORES))]
                c = mix(c, ROCK[4], max(0.0, (d - rr + 4) / 4.0) * 0.5)
            cv.set(x, y, c)


def mine(cv):
    cx, cy = MINE.CENTER
    WD.inlay_ring(cv, cx, cy, MINE.ORE_R)
    WD.carved_runes(cv, cx, cy, MINE.ORE_R + 7, 16,
                    skip=lambda x, y: y > cy + 30 or y < 9 or x < INPUT[0] + 58 or x > OUTPUT[0] - 4
                    or min(math.hypot(x - ox, y - oy) for (ox, oy) in MINE.ORES) < 10)
    for (ox, oy) in MINE.ORES:
        socket(cv, ox, oy, MINE.ORE_SOCKET_R)
    rock_window(cv)
    gem_socket(cv, *MINE.GEM)


# ---------------------------------------------------------------- the Crop Field

def field(cv):
    x1, y1, x2, y2 = FIELD.BOX
    # the frame of livingwood, its livingrock rim, the tilled soil inside in furrows
    for y in range(y1, y2):
        for x in range(x1, x2):
            edge = min(x - x1, x2 - 1 - x, y - y1, y2 - 1 - y)
            if edge == 0:
                c = OUTLINE
            elif edge in (1, 2):
                lit = x - x1 < 3 or y - y1 < 3
                c = WOOD[1] if lit else WOOD[4]
                if edge == 2:
                    c = LR[1] if lit else LR[3]
            elif edge == 3:
                c = OUTLINE
            else:
                # furrows: a ridge of soil, lit on top, before each row of crops
                row_y = min(FIELD.ROWS, key=lambda r: abs(r - y))
                k = y - row_y
                if k == 0:
                    c = SOIL[0]
                elif k in (-1, 1):
                    c = SOIL[1]
                elif k > 0:
                    c = mix(SOIL[2], SOIL[3], min(1.0, (k - 1) / 5.0))
                else:
                    c = mix(SOIL[1], SOIL[2], min(1.0, (-k - 1) / 5.0))
                if rnd2(x, y, 104) < 0.08:
                    c = mix(c, SOIL[3], 0.5)
            cv.set(x, y, c)
    gem_socket(cv, *FIELD.GEM)
    # little legs at its corners down towards the slot
    for lx in (x1 + 4, x2 - 8):
        for y in range(y2, y2 + 5):
            for x in range(lx, lx + 4):
                cv.set(x, y, OUTLINE if x in (lx, lx + 3) or y == y2 + 4 else (WOOD[2] if x == lx + 1 else WOOD[4]))
    special_slot(cv, *FIELD.SLOT)


# ---------------------------------------------------------------- build

MACHINES = {"petal_farm": farm, "orechid_mine": mine, "crop_field": field}


def build(name):
    cv = Canvas(W + 2 * M, H + 2 * M, M, M)
    base(cv, MACHINES[name])
    return cv


def main():
    for name in MACHINES:
        build(name).save(os.path.join(ASSETS, "textures", "gui", name + ".png"))


if __name__ == "__main__":
    main()
