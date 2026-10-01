"""The machines' GUI panels in the living-wood style, all on the same layout (layout.py): livingwood planks
framed with livingrock, mana crystals on the corners, the input slots on the left (livingrock rims), the
output slots on the right (gold rims), a special slot (mana-blue rim) under the heart where a machine has one,
the arrows, the mana bar, the player's inventory on a panel hanging under it. Each machine's heart is drawn
in the middle:

  rune_altar.png    the altar's round top seen from above: a gold-rimmed livingrock disc with eight runes
                    carved round a dark hollow (the rune takes shape there), a keystone gem, a ring inlaid
                    in the wood round it (the ingredients circle along it), its stem down to the reagent slot
  terra_plate.png   the plate: a livingrock octagon with a lapis field and a pale sun pattern, a dark core
                    (the light gathers there), three sockets on a ring round it (the ingredients hover over
                    them), a gem on its rim
  mana_infuser.png  a little mana pool seen from a little above: a livingrock rim round the mana (the screen
                    draws it), its front wall with a gem set in it, its stand down to the catalyst slot; a
                    carved ring over it where the item being infused floats
  pure_daisy.png    a round meadow: the daisy's bed in a gold ring in the middle, eight livingrock stones round
                    it (the blocks being purified sit on them), a gem on its top edge
  petal_apothecary.png  a livingrock bowl with petals in relief round it, its water's dark bed (the screen draws
                    the water), its stem down to the seeds slot; a carved ring over it where the flower rises
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
                    LIGHTS, ALTAR, PLATE, POOL_, DAISY, BOWL, FARM)

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "lib"))
import wood as WD  # noqa: E402

WOOD, LR, MANA, GOLD = WD.WOOD, WD.LR, WD.MANA, WD.GOLD
OUTLINE = WOOD[6]
LIGHT_DIR = (-0.62, -0.78)
LAPIS = [hexc(h) for h in ("4A7CE0", "3361C6", "254CA8", "1B3A88", "122966")]
TEAL = [hexc(h) for h in ("D8FFF6", "8FF0DE", "4FC9C4", "2C8F98")]
HOLLOW = (hexc("12345E"), hexc("060E1C"))
GRASS = [hexc(h) for h in ("9BE36A", "6FC24A", "4E9E38", "37782C", "245420")]
SOIL = [hexc(h) for h in ("5A3B22", "43291A", "2E1C12", "1C110B")]
PETALS = [hexc(h) for h in ("FFFFFF", "FFB3DE", "FFE066", "B9A3FF", "8FD8FF", "FF8A80")]


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


# ---------------------------------------------------------------- the Runic Altar

# the altar's runes, 5x5: water, fire, earth, air, mana, spring, summer, winter (the sheet has them lit, in order)
ALTAR_GLYPHS = [
    ["..#..", ".###.", "##.##", "#...#", ".###."],
    ["..#..", ".#.#.", ".#..#", "#.#.#", ".###."],
    [".....", "..#..", ".#.#.", "#...#", "#####"],
    ["###..", "...#.", ".###.", "#....", ".####"],
    ["..#..", ".#.#.", "#.#.#", ".#.#.", "..#.."],
    ["#...#", ".#.#.", "..#..", "..#..", ".###."],
    ["#.#.#", ".###.", "##.##", ".###.", "#.#.#"],
    ["#.#.#", ".###.", "#####", ".###.", "#.#.#"]]


def altar_disc(cv):
    """The altar's top: a dark outline, a gold rim, a band of livingrock with the runes carved in it, a thin
    gold inner rim and the dark hollow, all lit from the top-left (the hollow's rim the other way)."""
    cx, cy = ALTAR.CENTER
    r, rr = ALTAR.R, ALTAR.RECESS_R
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
            elif d > rr + 1.5:
                t = (d - (rr + 1.5)) / float(r - 3 - (rr + 1.5))
                c = LR[0] if hi else (LR[2] if lo else LR[1])
                c = mix(c, LR[3], 0.25 * (1.0 - t))
                if rnd2(x, y, 23) < 0.08:
                    c = mix(c, LR[3], 0.4)
            elif d > rr + 0.5:
                c = GOLD[2] if lo else GOLD[3]
            elif d > rr - 0.5:
                c = OUTLINE
            else:
                c = hollow_color(d, rr, x, y, 51)
            cv.set(x, y, c)
    for (gx, gy), g in zip(ALTAR.RUNES, ALTAR_GLYPHS):
        for j, row in enumerate(g):
            for i, ch in enumerate(row):
                if ch == "#":
                    cv.set(gx + i, gy + j, mix(LR[3], LR[4], 0.35))


STONE = ["...o...",
         "..oLo..",
         ".oLlmo.",
         "oLlgmDo",
         ".omDDo.",
         "..oDo..",
         "...o..."]


def stone(cv, x, y):
    """A little livingrock diamond (7x7 round the pixel (x, y)) with a gold heart: the altar's stones down to
    the reagent slot, which the screen lights as the livingrock is used."""
    cv.sprite(STONE, x - 3, y - 3, {"o": OUTLINE, "L": LR[0], "l": LR[1], "m": LR[2], "D": LR[3], "g": GOLD[1]})


def altar(cv):
    cx, cy = ALTAR.CENTER
    WD.inlay_ring(cv, cx, cy, ALTAR.ORBIT_R)
    WD.carved_runes(cv, cx, cy, ALTAR.ORBIT_R + 6, 12,
                    skip=lambda x, y: y > cy + 8 or y < 9 or x < INPUT[0] + 58 or x > OUTPUT[0] - 4)
    sx, sy = ALTAR.SLOT
    for (gx, gy) in ALTAR.STONES:
        stone(cv, gx, gy)
    altar_disc(cv)
    gem_socket(cv, *ALTAR.GEM)
    special_slot(cv, sx, sy)


# ---------------------------------------------------------------- the Terrestrial Plate

OCT_NORMALS = [(math.cos(math.radians(a)), math.sin(math.radians(a))) for a in range(0, 360, 45)]


def oct_depth(px, py, r):
    """How far inside the octagon of inradius r the point is, and the outward normal of its nearest edge."""
    best, normal = None, (0.0, 0.0)
    for (nx, ny) in OCT_NORMALS:
        d = r - (px * nx + py * ny)
        if best is None or d < best:
            best, normal = d, (nx, ny)
    return best, normal


def plate_field(px, py, x, y):
    """The plate's lapis field with its pale sun: a ring and eight rays round the core."""
    d = math.hypot(px, py)
    t = (py + PLATE.R) / (2.0 * PLATE.R)
    c = mix(LAPIS[1], LAPIS[3], t)
    if rnd2(x, y, 31) < 0.12:
        c = mix(c, LAPIS[0] if rnd2(x, y, 32) < 0.5 else LAPIS[4], 0.45)
    if abs(d - 12.5) < 0.6:
        return TEAL[2] if py < 0 else TEAL[3]
    ang = math.atan2(py, px)
    k = round(ang / (math.pi / 4.0))
    off = abs(ang - k * math.pi / 4.0) * d
    if 8.0 < d < 17.5 and off < 0.6:
        return TEAL[1] if d < 12.5 else TEAL[2]
    if 17.5 <= d < 18.6 and off < 1.1:
        return TEAL[0]
    return c


def plate(cv):
    cx, cy = PLATE.CENTER
    WD.inlay_ring(cv, cx, cy, PLATE.SOCKET_R)
    r = PLATE.R
    for y in range(cy - r - 2, cy + r + 3):
        for x in range(cx - r - 2, cx + r + 3):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            depth, (nx, ny) = oct_depth(px, py, r)
            if depth <= 0:
                continue
            lit = nx * LIGHT_DIR[0] + ny * LIGHT_DIR[1]
            hi, lo = lit > 0.3, lit < -0.3
            d = math.hypot(px, py)
            if depth < 1:
                c = OUTLINE
            elif depth < 3:
                c = LR[0] if hi else (LR[3] if lo else LR[1])
            elif depth < 4:
                c = GOLD[3] if hi else GOLD[2]
            elif d > PLATE.CORE_R + 1:
                c = plate_field(px, py, x, y)
            elif d > PLATE.CORE_R:
                c = GOLD[2] if py > 0 else GOLD[3]
            else:
                c = hollow_color(d, PLATE.CORE_R, x, y, 61)
            cv.set(x, y, c)
    for (sx, sy) in PLATE.SOCKETS:
        socket(cv, sx, sy, PLATE.SOCKET_SIZE)
    gem_socket(cv, *PLATE.GEM)


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


# ---------------------------------------------------------------- the Mana Infuser

def ellipse_k(px, py, rx, ry):
    return (px / rx) ** 2 + (py / ry) ** 2


def pool(cv):
    cx, cy = POOL_.CENTER
    rx, ry = POOL_.RX, POOL_.RY
    srx, sry = POOL_.SURFACE_RX, POOL_.SURFACE_RY
    wall = POOL_.WALL_H
    ix, iy = POOL_.ITEM
    WD.inlay_ring(cv, ix, iy, POOL_.ITEM_RING_R)
    WD.carved_runes(cv, cx, cy - 4, 38, 14,
                    skip=lambda x, y: y > cy - 12 or y < 9 or x < INPUT[0] + 58 or x > OUTPUT[0] - 4
                    or math.hypot(x - ix, y - iy) < POOL_.ITEM_RING_R + 4)
    sx, sy = POOL_.SLOT
    stem(cv, cx, cy + ry + wall - 1, sy - 1, 12, gold_rows=(3, 4))
    for x in range(cx - 8, cx + 8):
        cv.set(x, sy - 2, OUTLINE if x in (cx - 8, cx + 7) else (LR[1] if x < cx + 3 else LR[3]))
    # the front wall: livingrock bricks, lit on the left, under the rim's front half
    for x in range(cx - rx, cx + rx):
        px = x + 0.5 - cx
        if abs(px) >= rx:
            continue
        top = cy + ry * math.sqrt(max(0.0, 1.0 - (px / rx) ** 2))
        y0 = int(math.floor(top))
        t = (px + rx) / (2.0 * rx)                  # 0 at the left, 1 at the right
        for j in range(0, wall + 1):
            y = y0 + j
            if j == wall or abs(px) > rx - 1.0:
                c = OUTLINE
            else:
                c = LR[0] if t < 0.12 else (LR[1] if t < 0.5 else (LR[2] if t < 0.82 else LR[3]))
                row = j
                if row in (1, 2):
                    c = GOLD[1] if t < 0.3 else (GOLD[2] if t < 0.75 else GOLD[3])
                elif row == 3 or row == 8:
                    c = mix(c, WOOD[5], 0.45)
                elif (row < 8 and (x + 3) % 9 == 0) or (row > 8 and (x + 7) % 9 == 0):
                    c = mix(c, WOOD[5], 0.4)
            cv.set(x, y, c)
    # the rim: livingrock round the mana, its far inner wall in shadow, its near lip lit
    def outer(x, y):
        return ellipse_k(x + 0.5 - cx, y + 0.5 - cy, rx, ry) <= 1.0

    for y in range(cy - ry - 1, cy + ry + 2):
        for x in range(cx - rx - 1, cx + rx + 2):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            if not outer(x, y):
                continue
            ki = ellipse_k(px, py, srx, sry)
            if not all(outer(x + dx, y + dy) for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                c = OUTLINE                         # the rim's outer edge
            elif ki > 1.0:
                nx, ny = px / rx ** 2, py / ry ** 2
                n = math.hypot(nx, ny) or 1.0
                lit = (nx * LIGHT_DIR[0] + ny * LIGHT_DIR[1]) / n
                c = LR[0] if lit > 0.35 else (LR[2] if lit < -0.35 else LR[1])
                if ki < 1.35:
                    c = LR[3] if py < 0 else LR[0]  # the inner edge: the far wall in shadow, the near lip lit
                if rnd2(x, y, 81) < 0.07:
                    c = mix(c, LR[3], 0.35)
            else:
                # the empty basin under the mana (the screen draws the mana over it)
                c = mix(hexc("10263F"), hexc("070F1C"), min(1.0, ki))
            cv.set(x, y, c)
    gem_socket(cv, *POOL_.GEM)
    special_slot(cv, sx, sy)


# ---------------------------------------------------------------- the Pure Daisy

def meadow(cv, cx, cy, r):
    """A round patch of grass, lit from the top-left, with a few tiny flowers in it and a dark edge."""
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(px, py)
            if d > r:
                continue
            if d > r - 1:
                cv.set(x, y, mix(GRASS[4], WOOD[6], 0.5))
                continue
            t = (py + r) / (2.0 * r)
            c = mix(GRASS[1], GRASS[3], t * 0.8 + 0.1 * (px / r))
            n = WD.noise(x, y, 3, 91)
            if n < 0.3:
                c = mix(c, GRASS[3], 0.45)
            elif n > 0.78:
                c = mix(c, GRASS[0], 0.35)
            if rnd2(x, y, 92) < 0.012:
                c = PETALS[int(rnd2(x, y, 93) * len(PETALS))]
            cv.set(x, y, c)


def stone_cell(cv, sx, sy, r):
    """A stone round the daisy (a block being purified): a round livingrock rim, moss on its top, a dark earthy
    hollow the screen draws the block in."""
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
                if py < -r + 3.5 and rnd2(x, y, 94) < 0.45:
                    c = GRASS[1] if rnd2(x, y, 95) < 0.5 else GRASS[2]
            elif d > r - 3.2:
                c = OUTLINE
            else:
                c = mix(hexc("2C2416"), hexc("17110A"), d / (r - 3.2))
            cv.set(x, y, c)


def daisy_bed(cv, cx, cy, r):
    """The daisy's own bed in the middle: a gold ring round soft grass (the screen draws the daisy on it)."""
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(px, py)
            if d > r:
                continue
            lit = (px * LIGHT_DIR[0] + py * LIGHT_DIR[1]) / max(d, 0.001)
            if d > r - 1:
                c = OUTLINE
            elif d > r - 2.5:
                c = GOLD[1] if lit > 0.3 else (GOLD[3] if lit < -0.3 else GOLD[2])
            else:
                c = mix(GRASS[2], GRASS[4], d / r)
                if rnd2(x, y, 96) < 0.15:
                    c = mix(c, GRASS[1], 0.5)
            cv.set(x, y, c)


def daisy(cv):
    cx, cy = DAISY.CENTER
    meadow(cv, cx, cy, DAISY.MEADOW_R + 0.5)
    WD.inlay_ring(cv, cx, cy, DAISY.RING_R, col=mix(GRASS[3], GRASS[4], 0.4))
    for (sx, sy) in DAISY.CELLS:
        stone_cell(cv, sx, sy, DAISY.STONE_R)
    daisy_bed(cv, cx, cy, DAISY.BED_R)
    gem_socket(cv, *DAISY.GEM)


# ---------------------------------------------------------------- the Petal Apothecary

def bowl(cv):
    cx, cy = BOWL.CENTER
    rx, ry = BOWL.RX, BOWL.RY
    wrx, wry = BOWL.WATER_RX, BOWL.WATER_RY
    deep = ry + BOWL.BODY_H
    ix, iy = BOWL.ITEM
    WD.inlay_ring(cv, ix, iy, BOWL.ITEM_RING_R)
    WD.carved_runes(cv, cx, cy - 4, 38, 14,
                    skip=lambda x, y: y > cy - 12 or y < 9 or x < INPUT[0] + 58 or x > OUTPUT[0] - 4
                    or math.hypot(x - ix, y - iy) < BOWL.ITEM_RING_R + 4)
    sx, sy = BOWL.SLOT
    stem(cv, cx, cy + deep - 2, sy - 1, 8, gold_rows=(4,))
    for x in range(cx - 7, cx + 7):
        cv.set(x, sy - 2, OUTLINE if x in (cx - 7, cx + 6) else (LR[1] if x < cx + 3 else LR[3]))

    def body(x, y):
        return ellipse_k(x + 0.5 - cx, y + 0.5 - cy, rx, deep) <= 1.0 and y + 0.5 >= cy

    # the bowl's body: livingrock, round with the light from the left, a gold band and a ring of petals
    for y in range(cy, cy + deep + 1):
        for x in range(cx - rx - 1, cx + rx + 1):
            if not body(x, y):
                continue
            px = x + 0.5 - cx
            t = (px + rx) / (2.0 * rx)
            if not all(body(x + dx, y + dy) for (dx, dy) in ((1, 0), (-1, 0), (0, 1))):
                c = OUTLINE
            else:
                c = LR[0] if t < 0.14 else (LR[1] if t < 0.52 else (LR[2] if t < 0.82 else LR[3]))
                row = y - cy
                if row in (ry - 1, ry):
                    c = GOLD[1] if t < 0.3 else (GOLD[2] if t < 0.75 else GOLD[3])
            cv.set(x, y, c)
    # petals in relief round the body, between the band and the foot
    for k in range(7):
        px = -18 + k * 6
        x0, y0 = cx + px, cy + ry + 5 + int(abs(px) / 9)
        if not body(x0, y0 + 2):
            continue
        col = PETALS[1 + k % 4]
        for (dx, dy) in ((0, 0), (1, 0), (0, 1), (1, 1), (0, -1), (1, -1), (-1, 0), (2, 0)):
            if body(x0 + dx, y0 + dy):
                cv.set(x0 + dx, y0 + dy, mix(col, LR[2], 0.45 if dy else 0.2))

    def outer(x, y):
        return ellipse_k(x + 0.5 - cx, y + 0.5 - cy, rx, ry) <= 1.0

    # the rim, and the water's dark bed inside it
    for y in range(cy - ry - 1, cy + ry + 2):
        for x in range(cx - rx - 1, cx + rx + 2):
            if not outer(x, y):
                continue
            px, py = x + 0.5 - cx, y + 0.5 - cy
            ki = ellipse_k(px, py, wrx, wry)
            if not all(outer(x + dx, y + dy) for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                c = OUTLINE
            elif ki > 1.0:
                nx, ny = px / rx ** 2, py / ry ** 2
                n = math.hypot(nx, ny) or 1.0
                lit = (nx * LIGHT_DIR[0] + ny * LIGHT_DIR[1]) / n
                c = LR[0] if lit > 0.35 else (LR[2] if lit < -0.35 else LR[1])
                if ki < 1.4:
                    c = LR[3] if py < 0 else LR[0]
            else:
                c = mix(hexc("1C4A52"), hexc("0B1E24"), min(1.0, ki))
            cv.set(x, y, c)
    gem_socket(cv, *BOWL.GEM)
    special_slot(cv, sx, sy)


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


# ---------------------------------------------------------------- build

MACHINES = {"rune_altar": altar, "terra_plate": plate, "mana_infuser": pool, "pure_daisy": daisy, "petal_apothecary": bowl,
            "petal_farm": farm}


def build(name):
    cv = Canvas(W + 2 * M, H + 2 * M, M, M)
    base(cv, MACHINES[name])
    return cv


def main():
    for name in MACHINES:
        build(name).save(os.path.join(ASSETS, "textures", "gui", name + ".png"))


if __name__ == "__main__":
    main()
