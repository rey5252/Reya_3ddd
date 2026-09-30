"""Mana Greenhouse GUI panel texture: a deep navy panel with rounded corners and a bright green edge,
wound with curly leaf vines and hung with little gold lights, dark slots outlined in sage green.

    python3 tools/gen_gui.py            writes textures/gui/greenhouse.png
    python3 tools/gen_gui.py --preview  also writes build/preview/gui.png (3x, with sample items)

Coordinates are the menu's (GreenhouseMenu): the panel is W x H, the texture has a margin M round
it for the vines that stick out of the frame. The moving parts (mana, bars, glints, the gold lights'
twinkle) are drawn over it by client/GreenhouseScreen.java; tools/check_layout.py keeps the two in step.
"""
import math
import os
import sys

from pix import Canvas, ASSETS, mix, shade, hexc, rnd, rnd2, rrect
from style import *
from sprites import PAL
import vines as V

M = 10
W, H = 256, 236
MACHINE_H = 146
FRAME = 5                            # the edge: outline, two rows of green, the mana vein, a navy bevel
VEIN = 3                             # the frame row the mana runs along (2 px in from the outline)
CORNER_R = 5
INV_X1, INV_X2 = 36, 220

HEART = (128, 60)
ORB_R = 19
FLOWERS = [(120, 15), (146, 26), (157, 52), (146, 78), (120, 89), (94, 78), (83, 52), (94, 26)]
UPGRADES = [(19, 20), (19, 42), (19, 64), (19, 86)]
CHARGE = (221, 20)
GAUGE = (223, 42, 235, 104)          # x1, y1, x2, y2 of the charge gauge's inside
BAR = (52, 115, 204, 121)            # growth bar inside
BUTTONS = [(18, 111), (222, 111)]    # redstone, output (16x16 incl. outline)
INV_Y, HOTBAR_Y, INV_SLOT_X = 154, 212, 48

# the little gold lights on the vines (their middle pixel); the screen makes them twinkle
LIGHTS = [
    (12, -6), (40, -8), (58, -6), (-6, 20), (-7, 52), (8, 149), (-4, 100),
    (243, -8), (218, -8), (198, -6), (262, 22), (262, 60), (248, 149), (262, 104)]

VEIN_COL = hexc("1B4A80")
LIGHT_DIR = (-0.62, -0.78)

TONES = (G1, G2, G3)                 # vine greens: highlight, light, dark


# ---------------------------------------------------------------- rounded panels

def frame_color(ring, light):
    """The edge's colour at a ring (0 = outermost) where the surface faces the light by `light` (-1..1):
    a raised green rim lit from the top-left, then the mana vein, then a navy bevel that is sunk (lit
    from the other side)."""
    hi, lo = light > 0.35, light < -0.35
    if ring == 0:
        return OUT
    if ring == 1:
        return G1 if hi else (G3 if lo else G2)
    if ring == 2:
        return G2 if hi else (G4 if lo else G3)
    if ring == VEIN:
        return VEIN_COL
    return N5 if hi else (N0 if lo else N3)


def panel_shape(cv, x1, y1, x2, y2, fill, r=CORNER_R):
    """A rounded panel with the green edge; `fill(x, y)` gives the colour of its inside."""
    for y in range(y1, y2):
        for x in range(x1, x2):
            depth, nx, ny = rrect(x, y, x1, y1, x2, y2, r)
            if depth <= 0:
                continue
            ring = int(depth)
            if ring < FRAME:
                cv.set(x, y, frame_color(ring, nx * LIGHT_DIR[0] + ny * LIGHT_DIR[1]))
            else:
                cv.set(x, y, fill(x, y))


def machine_fill(x, y):
    t = y / float(MACHINE_H)
    base = mix(N2, N4, t ** 0.9)
    n = rnd2(x, y, 3)
    if n < 0.05:
        return shade(base, 0.05)
    if n > 0.965:
        return shade(base, -0.07)
    if rnd2(x, y, 17) < 0.011:
        return NSTAR
    return base


def inventory_fill(x, y):
    t = (y - MACHINE_H) / float(H - MACHINE_H)
    base = mix(N3, N4, t)
    n = rnd2(x, y, 4)
    if n < 0.05:
        return shade(base, 0.05)
    if n > 0.965:
        return shade(base, -0.07)
    if rnd2(x, y, 19) < 0.010:
        return NSTAR
    return base


# ---------------------------------------------------------------- panel decoration behind the slots

def ring(cv, cx, cy, r, col):
    steps = int(r * 8)
    for k in range(steps):
        a = k / steps * math.tau
        cv.set(int(math.floor(cx + math.cos(a) * r)), int(math.floor(cy + math.sin(a) * r)), col)


RUNES = [
    [".#.", "###", ".#."], ["#.#", ".#.", "#.#"], ["##.", "#.#", ".##"], [".##", "#..", ".##"],
    ["#.#", "###", "#.#"], ["###", ".#.", "#.#"], [".#.", "#.#", "###"], ["#..", "###", "..#"]]
PATTERN = hexc("2A4487")


def rune(cv, x, y, k):
    for j, row in enumerate(RUNES[k % len(RUNES)]):
        for i, ch in enumerate(row):
            if ch == "#":
                cv.set(x - 1 + i, y - 1 + j, mix(PATTERN, M2, 0.3))


def runes(cv):
    """A rune circle behind the ring of flowers."""
    cx, cy = HEART
    for r, col in ((48.5, PATTERN), (46.5, mix(PATTERN, N3, 0.55)), (55.5, mix(PATTERN, N3, 0.4))):
        ring(cv, cx, cy, r, col)
    for k in range(64):
        if k % 2 == 0:
            a = k / 64 * math.tau
            cv.set(int(math.floor(cx + math.cos(a) * 52)), int(math.floor(cy + math.sin(a) * 52)), PATTERN)
    for k in range(8):
        a = (k + 0.5) / 8 * math.tau
        rune(cv, int(round(cx + math.cos(a) * 52)), int(round(cy + math.sin(a) * 52)), k)


# ---------------------------------------------------------------- slots

SAGE = (S1, S2, S3)                  # a slot's rim: lit side, sides, shaded side
MANA_RIM = (M1, M3, M5)


def slot_frame(cv, x, y, rim, inside_top, inside_bottom):
    """An 18x18 slot (x, y = the item's corner): a bevelled rim in green, a dark inside with a shadow
    under the top-left rim and a faint light at the bottom-right, the rim's four corners rounded."""
    hi, mid, lo = rim
    x1, y1 = x - 1, y - 1
    cv.rect(x1, y1, x1 + 18, y1 + 18, mid)
    cv.hline(x1, x1 + 18, y1, hi)
    cv.vline(x1, y1, y1 + 18, hi)
    cv.hline(x1, x1 + 18, y1 + 17, lo)
    cv.vline(x1 + 17, y1, y1 + 18, lo)
    cv.set(x1, y1 + 17, mid)
    cv.set(x1 + 17, y1, mid)
    for yy in range(y, y + 16):
        cv.hline(x, x + 16, yy, mix(inside_top, inside_bottom, (yy - y) / 15.0))
    cv.hline(x, x + 16, y, shade(inside_top, -0.55))
    cv.vline(x, y, y + 16, shade(inside_top, -0.55))
    cv.hline(x + 1, x + 16, y + 15, mix(inside_bottom, hi, 0.12))
    cv.vline(x + 15, y + 1, y + 16, mix(inside_bottom, hi, 0.12))
    for (cx, cy) in ((x1, y1), (x1 + 17, y1), (x1, y1 + 17), (x1 + 17, y1 + 17)):
        cv.set(cx, cy, shade(mid, -0.55))


def flower_slot(cv, x, y):
    """A little planter: soil at the bottom with blades of grass, the flower stands in it."""
    slot_frame(cv, x, y, SAGE, hexc("10193A"), hexc("0B1129"))
    for xx in range(x + 1, x + 16):
        top = y + 12 + (1 if rnd(xx * 3 + y, 5) < 0.35 else 0)
        for yy in range(top, y + 16):
            n = rnd2(xx, yy, 9)
            cv.set(xx, yy, SOIL1 if n < 0.6 else (SOIL0 if n < 0.85 else SOIL2))
        cv.set(xx, top, SOIL0)
    for gx, h in ((x + 2, 2), (x + 3, 3), (x + 13, 3), (x + 14, 2)):
        for k in range(h):
            cv.set(gx, y + 12 - k - 1 + (1 if h == 2 else 0), G3 if k == 0 else (G2 if k < h - 1 else G1))


def upgrade_slot(cv, x, y):
    """Upgrade slot: a faint tablet in it."""
    inside = hexc("10193A")
    slot_frame(cv, x, y, SAGE, inside, hexc("0B1129"))
    ghost = mix(inside, S1, 0.16)
    cv.outline(x + 4, y + 2, x + 12, y + 14, ghost)
    for yy in (y + 5, y + 8, y + 11):
        cv.hline(x + 6, x + 10, yy, ghost)


def charge_slot(cv, x, y):
    """Charge slot: a mana-blue rim with a faint mana tablet in it."""
    inside = hexc("0E2440")
    slot_frame(cv, x, y, MANA_RIM, inside, hexc("091529"))
    ghost = mix(inside, M1, 0.2)
    cv.outline(x + 3, y + 3, x + 13, y + 13, ghost)
    for (cx, cy) in ((x + 3, y + 3), (x + 12, y + 3), (x + 3, y + 12), (x + 12, y + 12)):
        cv.set(cx, cy, inside)
    for d in range(3):
        cv.set(x + 7 - d, y + 5 + d, ghost)
        cv.set(x + 8 + d, y + 5 + d, ghost)
        cv.set(x + 7 - d, y + 10 - d, ghost)
        cv.set(x + 8 + d, y + 10 - d, ghost)


def inventory_slot(cv, x, y):
    slot_frame(cv, x, y, SAGE, hexc("0F1738"), hexc("0A1129"))


# ---------------------------------------------------------------- vines to the heart

def channel(cv, x1, y1, x2, y2):
    """A vine from a flower slot to the heart: dark edges, green body, a mana vein in its middle
    (the screen makes mana run along the vein)."""
    dx, dy = x2 - x1, y2 - y1
    n = max(abs(dx), abs(dy))
    pts = [(x1 + round(dx * i / n), y1 + round(dy * i / n)) for i in range(n + 1)]
    diag = dx != 0 and dy != 0
    for (x, y) in pts:
        if not diag:
            if dx == 0:
                cv.hline(x - 2, x + 3, y, VOUT)
                cv.hline(x - 1, x + 2, y, G3)
                cv.set(x - 1, y, G2)
            else:
                cv.vline(x, y - 2, y + 3, VOUT)
                cv.vline(x, y - 1, y + 2, G3)
                cv.set(x, y - 1, G2)
        else:
            cv.rect(x - 1, y - 1, x + 2, y + 2, VOUT)
    if diag:
        for (x, y) in pts:
            cv.set(x, y, G3)
            cv.set(x + (1 if dx * dy < 0 else -1), y, G3)
            cv.set(x, y - 1, G2)
    for (x, y) in pts:
        cv.set(x, y, M4)


def orb(cv):
    """The mana heart: a glass sphere in a green ring with a thin gold line (the mana inside is drawn by
    the screen)."""
    cx, cy = HEART
    r = ORB_R
    for y in range(cy - r - 3, cy + r + 3):
        for x in range(cx - r - 3, cx + r + 3):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(px, py)
            if d <= r - 2:
                t = (y - (cy - r)) / (2 * r)
                cv.set(x, y, mix(hexc("10305A"), hexc("081A36"), t))
            elif d <= r + 2:
                light = (px * LIGHT_DIR[0] + py * LIGHT_DIR[1]) / (d + 1e-6)     # facing the light: > 0
                k = (light + 1) / 2
                if d > r + 1:
                    cv.set(x, y, OUT)
                elif d > r:
                    cv.set(x, y, mix(G3, G1, k))
                elif d > r - 1:
                    cv.set(x, y, mix(Y3, Y1, k))
                else:
                    cv.set(x, y, mix(N5, N3, k))


# ---------------------------------------------------------------- bars

def growth_bar_track(cv):
    x1, y1, x2, y2 = BAR
    cv.outline(x1 - 2, y1 - 2, x2 + 2, y2 + 2, OUT)
    cv.hline(x1 - 1, x2 + 1, y1 - 1, S1)
    cv.vline(x1 - 1, y1 - 1, y2 + 1, S1)
    cv.hline(x1 - 1, x2 + 1, y2, S3)
    cv.vline(x2, y1 - 1, y2 + 1, S3)
    cv.set(x1 - 1, y2, S2)
    cv.set(x2, y1 - 1, S2)
    cv.rect(x1, y1, x2, y2, hexc("081026"))
    cv.hline(x1, x2, y1, hexc("040817"))
    for x in range(x1 + 19, x2, 19):
        cv.vline(x, y1 + 1, y2, hexc("14204A"))
    sprout(cv, x1 - 13, y1 - 3)


def sprout(cv, x, y):
    rows = ["...oo.oo..",
            "..olmoldo.",
            "..ohlmlo..",
            "...oodo...",
            "....od....",
            "..yyodyy..",
            ".yYYYYYYy."]
    pal = dict(PAL)
    pal['y'] = SOIL1
    pal['Y'] = SOIL0
    cv.sprite(rows, x, y, pal)


def gauge_track(cv):
    x1, y1, x2, y2 = GAUGE
    cv.outline(x1 - 2, y1 - 2, x2 + 2, y2 + 2, OUT)
    cv.hline(x1 - 1, x2 + 1, y1 - 1, M2)
    cv.vline(x1 - 1, y1 - 1, y2 + 1, M2)
    cv.hline(x1 - 1, x2 + 1, y2, M5)
    cv.vline(x2, y1 - 1, y2 + 1, M5)
    cv.set(x1 - 1, y2, M4)
    cv.set(x2, y1 - 1, M4)
    cv.rect(x1, y1, x2, y2, hexc("07152C"))
    for y in range(y1 + 6, y2 - 2, 8):
        cv.hline(x1, x1 + 3, y, hexc("1D4C7A"))
        cv.hline(x2 - 3, x2, y, hexc("1D4C7A"))


# ---------------------------------------------------------------- vines and lights round the frame

def arm(x, y, theta, length, sway, period, phase, hooks, tail, seed, tail_len=24, r=2.1):
    """A vine along an edge: a chain of blades swaying along a walk from (x, y) heading theta, little
    curling tendrils (`hooks` = [(distance, side, length, turn)]) and a curl at its end (`tail` = turn)."""
    path = V.walk(x, y, theta, length + tail_len, V.wave(sway, period, phase))
    specs = []
    for (s, side, ln, turn) in hooks:
        specs.append(V.hook_spec(path, s, side, ln, turn, TONES))
    specs += V.chain_specs(path, 0, length - 2, TONES, r=r, seed=seed)
    ex, ey, eth = V.at(path, length - 3)
    specs.append(V.curl_spec(ex, ey, eth, tail_len, tail, 1.6, 0.6, TONES))
    return specs


def corner_top_left():
    """The top-left corner's vines (the other corners are mirrors of these): an arm along the top edge,
    an arm down the left edge and a big curl outside the corner. Returns (top, left, curl) specs."""
    top = arm(-2, -3.4, 0.0, 52, 0.20, 34, 0.0,
              hooks=[(9, -1, 12, 4.0), (24, -1, 13, 4.5), (39, -1, 12, 4.0)], tail=-6.0, seed=1)
    left = arm(-3.4, -2, math.pi / 2, 50, 0.20, 34, 0.25,
               hooks=[(10, 1, 12, 4.0), (25, 1, 13, 4.5), (40, 1, 12, 4.0)], tail=6.0, seed=2)
    flourish = [V.curl_spec(-1.5, -1.5, -2.4, 20, -7.5, 1.9, 0.6, TONES, power=1.5)]
    return top, left, flourish


def decorations(cv):
    top, left, flourish = corner_top_left()
    cx = W / 2.0 - 0.0
    cy = MACHINE_H / 2.0
    for (fx, fy) in ((False, False), (True, False), (False, True), (True, True)):
        for group in (top, left, flourish):
            V.paint_specs(cv, group, VOUT, fx=fx, fy=fy, cx=cx, cy=cy)
    for (x, y) in LIGHTS:
        light(cv, x, y)


def light(cv, x, y):
    """A little gold light: a bright middle, a warm cross round it, a faint halo."""
    for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1), (-2, 0), (2, 0), (0, -2), (0, 2)):
        cv.set(x + dx, y + dy, Y2, 70)
    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        cv.set(x + dx, y + dy, Y1)
    cv.set(x, y, Y0)


# ---------------------------------------------------------------- the panels

def machine(cv):
    panel_shape(cv, 0, 0, W, MACHINE_H, machine_fill)
    runes(cv)
    cx, cy = HEART
    for (x, y) in FLOWERS:
        sx, sy = x + 8, y + 8
        dx, dy = sx - cx, sy - cy
        d = math.hypot(dx, dy)
        ux, uy = dx / d, dy / d
        start = (round(cx + ux * (ORB_R + 1)), round(cy + uy * (ORB_R + 1)))
        end = (round(sx - ux * 10.5), round(sy - uy * 10.5))
        channel(cv, end[0], end[1], start[0], start[1])
    orb(cv)
    for (x, y) in FLOWERS:
        flower_slot(cv, x, y)
    for (x, y) in UPGRADES:
        upgrade_slot(cv, x, y)
    charge_slot(cv, *CHARGE)
    gauge_track(cv)
    growth_bar_track(cv)


def inventory(cv):
    """The player's inventory on a navy panel hanging under the machine panel."""
    panel_shape(cv, INV_X1, MACHINE_H - 14, INV_X2, H, inventory_fill)
    for r in range(3):
        for c in range(9):
            inventory_slot(cv, INV_SLOT_X + c * 18, INV_Y + r * 18)
    for c in range(9):
        inventory_slot(cv, INV_SLOT_X + c * 18, HOTBAR_Y)


def build():
    cv = Canvas(W + 2 * M, H + 2 * M, M, M)
    inventory(cv)
    machine(cv)
    decorations(cv)
    return cv


def main():
    cv = build()
    cv.save(os.path.join(ASSETS, "textures", "gui", "greenhouse.png"))
    if "--preview" in sys.argv:
        from preview import preview_gui
        preview_gui(cv)


if __name__ == "__main__":
    main()
