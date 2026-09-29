"""Mana Greenhouse GUI: panel texture and widget sheet, in mana (cyan) and plant (green) colours on
Botania's livingwood and livingrock.

    python3 tools/gen_gui.py            writes textures/gui/greenhouse.png and greenhouse_widgets.png
    python3 tools/gen_gui.py --preview  also writes build/preview/gui.png (3x, with sample items)

Coordinates are the menu's (GreenhouseMenu): the panel is W x H, the texture has a margin M round
it for the vines and flowers that stick out of the frame.
"""
import math
import os
import sys

from pix import Canvas, ASSETS, mix, shade, hexc, rnd, rnd2
from sprites import PAL, LEAF_DIAG, LEAF_DIAG_S, LEAF_BIG, LEAF_RIGHT, LEAF_TINY, BLOSSOM, BLOSSOM_S, DAISY, BUD, \
    flip_h, flip_v, rot90
from shapes import gem, PEARL, MANA

M = 10
W, H = 256, 236
MACHINE_H = 146
FRAME = 9
INV_X1, INV_X2 = 36, 220

# ---------------------------------------------------------------- palette
OUT = hexc("1A0703")
# livingwood (Botania's red-brown planks)
LW0, LW1, LW2, LW3, LW4 = hexc("A2512A"), hexc("7A3314"), hexc("5E240B"), hexc("4A1A08"), hexc("310B04")
# livingrock (warm pale stone)
LR0, LR1, LR2, LR3, LR4 = hexc("FBF8EE"), hexc("E2DCCB"), hexc("C9C2B1"), hexc("A89F8B"), hexc("7A715F")
# mana
M0, M1, M2, M3, M4, M5 = hexc("F2FFFF"), hexc("A6F6FF"), hexc("55D9F7"), hexc("2A9FE2"), hexc("1B64B8"), hexc("123C7C")
# leaves
L0, L1, L2, L3, L4, L5 = hexc("E4FAA8"), hexc("A8E563"), hexc("6DC043"), hexc("45922F"), hexc("2A6428"), hexc("173D1C")
# panel (deep greenhouse teal)
P0, P1, P2, P3 = hexc("235A5B"), hexc("1D4C4F"), hexc("173F43"), hexc("113236")
PATTERN = hexc("2C6C69")
SOIL0, SOIL1, SOIL2 = hexc("5A3E28"), hexc("3F2B1C"), hexc("2A1C12")
Y0, Y1, Y2, Y3 = hexc("FFF6B0"), hexc("F4C842"), hexc("C98B1A"), hexc("7A4E0C")
# player inventory
SLOT_IN, SLOT_HI, SLOT_LO = hexc("8B8B8B"), hexc("FFFFFF"), hexc("373737")

HEART = (128, 60)
ORB_R = 19
FLOWERS = [(120, 15), (146, 26), (157, 52), (146, 78), (120, 89), (94, 78), (83, 52), (94, 26)]
UPGRADES = [(19, 20), (19, 42), (19, 64), (19, 86)]
CHARGE = (221, 20)
GAUGE = (223, 42, 235, 104)          # x1, y1, x2, y2 of the charge gauge's inside
BAR = (52, 115, 204, 121)            # growth bar inside
BUTTONS = [(18, 111), (222, 111)]    # redstone, output (16x16 incl. outline)
INV_Y, HOTBAR_Y, INV_SLOT_X = 154, 212, 48


# ---------------------------------------------------------------- panel inside

def panel_bg(cv):
    """Machine panel inside: a soft vertical gradient with faint speckles, a rune circle behind the
    ring of flowers and a shadow under the frame."""
    x1, y1, x2, y2 = FRAME, FRAME, W - FRAME, MACHINE_H - FRAME
    for y in range(y1, y2):
        t = (y - y1) / (y2 - y1 - 1)
        base = mix(P0, P2, t)
        for x in range(x1, x2):
            n = rnd2(x, y, 3)
            c = base
            if n < 0.045:
                c = shade(base, 0.05)
            elif n > 0.965:
                c = shade(base, -0.06)
            cv.set(x, y, c)
    # shadow of the frame on the panel (top and left darker, 2 px)
    for i, a in enumerate((110, 50)):
        cv.hline(x1, x2, y1 + i, (4, 16, 18), a)
        cv.vline(x1 + i, y1 + i + 1, y2, (4, 16, 18), a)
    cx, cy = HEART
    for r, col in ((48.5, PATTERN), (46.5, mix(PATTERN, P1, 0.55)), (55.5, mix(PATTERN, P1, 0.4))):
        ring(cv, cx, cy, r, col)
    for k in range(64):
        if k % 2 == 0:
            a = k / 64 * math.tau
            cv.set(int(math.floor(cx + math.cos(a) * 52)), int(math.floor(cy + math.sin(a) * 52)), PATTERN)
    for k in range(8):
        a = (k + 0.5) / 8 * math.tau
        rune(cv, int(round(cx + math.cos(a) * 52)), int(round(cy + math.sin(a) * 52)), k)


def ring(cv, cx, cy, r, col):
    steps = int(r * 8)
    for k in range(steps):
        a = k / steps * math.tau
        cv.set(int(math.floor(cx + math.cos(a) * r)), int(math.floor(cy + math.sin(a) * r)), col)


RUNES = [
    [".#.", "###", ".#."], ["#.#", ".#.", "#.#"], ["##.", "#.#", ".##"], [".##", "#..", ".##"],
    ["#.#", "###", "#.#"], ["###", ".#.", "#.#"], [".#.", "#.#", "###"], ["#..", "###", "..#"]]


def rune(cv, x, y, k):
    for j, row in enumerate(RUNES[k % len(RUNES)]):
        for i, ch in enumerate(row):
            if ch == "#":
                cv.set(x - 1 + i, y - 1 + j, mix(PATTERN, M2, 0.3))


# ---------------------------------------------------------------- slots

def slot_frame(cv, x, y, ramp, inside):
    """An 18x18 slot sunk into the panel (x, y = the item's corner): outline, a rim lit from the
    bottom-right (sunk), a darker inside with a shadow under the top-left rim."""
    hi, mid, lo = ramp
    x1, y1 = x - 1, y - 1
    cv.outline(x1 - 1, y1 - 1, x1 + 19, y1 + 19, OUT)
    cv.rect(x1, y1, x1 + 18, y1 + 18, mid)
    cv.hline(x1, x1 + 18, y1, lo)
    cv.vline(x1, y1, y1 + 18, lo)
    cv.hline(x1, x1 + 18, y1 + 17, hi)
    cv.vline(x1 + 17, y1, y1 + 18, hi)
    cv.set(x1, y1 + 17, mid)
    cv.set(x1 + 17, y1, mid)
    cv.rect(x, y, x + 16, y + 16, inside)
    cv.hline(x, x + 16, y, shade(inside, -0.4))
    cv.vline(x, y, y + 16, shade(inside, -0.4))


def flower_slot(cv, x, y):
    """A little livingwood planter: soil at the bottom with blades of grass, the flower stands in it."""
    slot_frame(cv, x, y, (LW0, LW2, LW4), P3)
    for yy in range(y + 1, y + 16):
        t = (yy - y) / 15
        cv.hline(x + 1, x + 16, yy, mix(hexc("17393E"), hexc("0E282C"), t))
    for xx in range(x + 1, x + 16):
        top = y + 12 + (1 if rnd(xx * 3 + y, 5) < 0.35 else 0)
        for yy in range(top, y + 16):
            n = rnd2(xx, yy, 9)
            cv.set(xx, yy, SOIL1 if n < 0.6 else (SOIL0 if n < 0.85 else SOIL2))
        cv.set(xx, top, SOIL0)
    for gx, h in ((x + 2, 2), (x + 3, 3), (x + 13, 3), (x + 14, 2)):
        for k in range(h):
            cv.set(gx, y + 12 - k - 1 + (1 if h == 2 else 0), L3 if k == 0 else (L2 if k < h - 1 else L1))


def upgrade_slot(cv, x, y):
    """Upgrade slot: a livingrock rim with a faint tablet in it."""
    slot_frame(cv, x, y, (LR0, LR2, LR4), hexc("183E42"))
    ghost = mix(hexc("183E42"), LR1, 0.16)
    cv.outline(x + 4, y + 2, x + 12, y + 14, ghost)
    for yy in (y + 5, y + 8, y + 11):
        cv.hline(x + 6, x + 10, yy, ghost)


def charge_slot(cv, x, y):
    """Charge slot: a mana-blue rim with a faint mana tablet in it."""
    inside = hexc("143848")
    slot_frame(cv, x, y, (M1, M3, M5), inside)
    ghost = mix(inside, M1, 0.2)
    cv.outline(x + 3, y + 3, x + 13, y + 13, ghost)
    for (cx, cy) in ((x + 3, y + 3), (x + 12, y + 3), (x + 3, y + 12), (x + 12, y + 12)):
        cv.set(cx, cy, inside)
    for d in range(3):
        cv.set(x + 7 - d, y + 5 + d, ghost)
        cv.set(x + 8 + d, y + 5 + d, ghost)
        cv.set(x + 7 - d, y + 10 - d, ghost)
        cv.set(x + 8 + d, y + 10 - d, ghost)


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
                cv.hline(x - 2, x + 3, y, L5)
                cv.hline(x - 1, x + 2, y, L3)
                cv.set(x - 1, y, L2)
            else:
                cv.vline(x, y - 2, y + 3, L5)
                cv.vline(x, y - 1, y + 2, L3)
                cv.set(x, y - 1, L2)
        else:
            cv.rect(x - 1, y - 1, x + 2, y + 2, L5)
    if diag:
        for (x, y) in pts:
            cv.set(x, y, L3)
            cv.set(x + (1 if dx * dy < 0 else -1), y, L3)
            cv.set(x, y - 1, L2)
    for (x, y) in pts:
        cv.set(x, y, M4)


def orb(cv):
    """The mana heart: a glass sphere in a livingwood ring (the mana inside is drawn by the screen)."""
    cx, cy = HEART
    r = ORB_R
    for y in range(cy - r - 3, cy + r + 3):
        for x in range(cx - r - 3, cx + r + 3):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(px, py)
            if d <= r - 2:
                t = (y - (cy - r)) / (2 * r)
                cv.set(x, y, mix(hexc("14404A"), hexc("0A232B"), t))
            elif d <= r + 2:
                light = (-px - py) / (d * 1.4142 + 1e-6)
                if d > r + 1:
                    cv.set(x, y, OUT)
                elif d > r:
                    cv.set(x, y, mix(LW3, LW0, (light + 1) / 2))
                elif d > r - 1:
                    cv.set(x, y, mix(LW4, LW1, (light + 1) / 2))
                else:
                    cv.set(x, y, mix(LR3, LR0, (light + 1) / 2))
    # a few leaves on the ring
    for (sx, sy, rows) in ((cx - 22, cy - 25, LEAF_DIAG_S), (cx + 15, cy + 18, flip_h(flip_v(LEAF_DIAG_S)))):
        pass


def glass_shine(cv):
    """Glass highlights drawn over the mana: a curved streak and a glint (in the widget sheet)."""


# ---------------------------------------------------------------- bars

def growth_bar_track(cv):
    x1, y1, x2, y2 = BAR
    cv.outline(x1 - 2, y1 - 2, x2 + 2, y2 + 2, OUT)
    cv.hline(x1 - 1, x2 + 1, y1 - 1, LW3)
    cv.vline(x1 - 1, y1 - 1, y2 + 1, LW3)
    cv.hline(x1 - 1, x2 + 1, y2, LW0)
    cv.vline(x2, y1 - 1, y2 + 1, LW0)
    cv.set(x1 - 1, y2, LW2)
    cv.set(x2, y1 - 1, LW2)
    cv.rect(x1, y1, x2, y2, hexc("0A1F22"))
    cv.hline(x1, x2, y1, hexc("051214"))
    for x in range(x1 + 19, x2, 19):
        cv.vline(x, y1 + 1, y2, hexc("12302F"))
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
    cv.hline(x1 - 1, x2 + 1, y1 - 1, M5)
    cv.vline(x1 - 1, y1 - 1, y2 + 1, M5)
    cv.hline(x1 - 1, x2 + 1, y2, M2)
    cv.vline(x2, y1 - 1, y2 + 1, M2)
    cv.set(x1 - 1, y2, M4)
    cv.set(x2, y1 - 1, M4)
    cv.rect(x1, y1, x2, y2, hexc("081C24"))
    for y in range(y1 + 6, y2 - 2, 8):
        cv.hline(x1, x1 + 3, y, hexc("1D4C5A"))
        cv.hline(x2 - 3, x2, y, hexc("1D4C5A"))


# ---------------------------------------------------------------- frame

TOP_RAMP = [OUT, LW0, LW1, LW2, LW2, LW3, LR0, LR1, M2]
BOTTOM_RAMP = [OUT, LW3, LW2, LW2, LW3, LW4, LR2, LR1, M3]


def frame(cv, x1, y1, x2, y2, open_bottom=None):
    """The frame: livingwood planks outside, a livingrock lip, a mana vein inside; mitred corners,
    lit from the top-left. open_bottom = (xa, xb) leaves the bottom edge's inner rows out there (where
    the inventory panel joins)."""
    for y in range(y1, y2):
        for x in range(x1, x2):
            dt, dl, db, dr = y - y1, x - x1, y2 - 1 - y, x2 - 1 - x
            d = min(dt, dl, db, dr)
            if d >= FRAME:
                continue
            if d == dt or d == dl:
                ramp, along, edge = TOP_RAMP, (x if d == dt else y), ("h" if d == dt else "v")
                lit = True
            else:
                ramp, along, edge = BOTTOM_RAMP, (x if d == db else y), ("h" if d == db else "v")
                lit = False
            c = ramp[d]
            # planks: grain streaks and a seam every 23 pixels in the wood rows
            if 1 <= d <= 5:
                seam = (along + (7 if edge == "v" else 0)) % 23
                if seam == 0 and 1 <= d <= 4:
                    c = LW4 if d > 1 else LW3
                elif seam == 1 and 1 <= d <= 4:
                    c = shade(c, 0.12)
                elif 2 <= d <= 4 and rnd(along * 5 + d, 40 + (1 if lit else 2)) < 0.16:
                    c = shade(c, -0.18)
            cv.set(x, y, c)


def frame_corner(cv, cx, cy):
    """A livingrock rosette with a mana pearl, pinned on a frame corner, leaves peeking out."""
    r = 6.2
    pix = set()
    for yy in range(int(cy - r) - 1, int(cy + r) + 2):
        for xx in range(int(cx - r) - 1, int(cx + r) + 2):
            if (xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2 <= r * r:
                pix.add((xx, yy))
    for (x, y) in pix:
        d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
        edge = any((x + a, y + b) not in pix for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            cv.set(x, y, OUT)
        else:
            light = (-(x + 0.5 - cx) - (y + 0.5 - cy)) / (d * 1.4142 + 1e-6) if d > 0 else 0
            cv.set(x, y, mix(LR3, LR0, (light + 1) / 2) if d > 3.6 else LR2)
    # petal notches: four little grooves make it a flower of stone
    for a in range(4):
        ang = math.pi / 4 + a * math.pi / 2
        cv.set(int(math.floor(cx + math.cos(ang) * 4.6)), int(math.floor(cy + math.sin(ang) * 4.6)), LR4)
    gem(cv, cx, cy, 2.9, PEARL)


def vine(cv, pts, leaves=(), flowers=()):
    """A stem through the points (2 px: dark under, green over), with sprites hung on it."""
    for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
        n = max(abs(x2 - x1), abs(y2 - y1), 1)
        for i in range(n + 1):
            x = x1 + round((x2 - x1) * i / n)
            y = y1 + round((y2 - y1) * i / n)
            cv.set(x, y + 1, L5)
            cv.set(x + 1, y + 1, L5)
            cv.set(x, y, L3)
            cv.set(x, y - 1, L5) if cv.get(x, y - 1)[:3] in (OUT,) else None
    for (x, y, rows) in leaves:
        cv.sprite(rows, x, y, PAL)
    for (x, y, rows) in flowers:
        cv.sprite(rows, x, y, PAL)


def wave(x1, x2, y, amp=1.5, period=17.0, phase=0.0):
    return [(x, y + round(math.sin((x + phase) / period * math.tau) * amp)) for x in range(x1, x2 + 1, 2)]


def decorations(cv):
    # vines along the top edge, left and right of where the name plate sits
    for (xa, xb, ph) in ((14, 88, 0.0), (168, 242, 8.0)):
        pts = wave(xa, xb, 1, 1.6, 19.0, ph)
        vine(cv, pts)
    top_leaves = [
        (16, -6, LEAF_DIAG), (30, 2, flip_v(LEAF_DIAG_S)), (44, -7, LEAF_BIG), (60, 1, flip_v(LEAF_DIAG)),
        (74, -6, LEAF_DIAG_S), (175, -6, flip_h(LEAF_DIAG_S)), (190, 1, flip_h(flip_v(LEAF_DIAG))),
        (203, -7, flip_h(LEAF_BIG)), (220, 2, flip_h(flip_v(LEAF_DIAG_S))), (232, -6, flip_h(LEAF_DIAG))]
    for (x, y, rows) in top_leaves:
        cv.sprite(rows, x, y, PAL)
    for (x, y, rows) in ((37, -4, BLOSSOM), (84, -3, BLOSSOM_S), (212, -4, BLOSSOM), (166, -3, DAISY[1:6] and BLOSSOM_S)):
        cv.sprite(rows, x, y, PAL)
    # vines hanging down the sides from the top corners
    for side in (0, 1):
        x = 1 if side == 0 else W - 3
        pts = [(x + (round(math.sin(y / 9.0) * 1.2)), y) for y in range(10, 62, 2)]
        vine(cv, pts)
        for (yy, rows) in ((16, LEAF_DIAG_S), (30, flip_v(LEAF_DIAG)), (44, LEAF_TINY), (56, flip_v(LEAF_DIAG_S))):
            if side == 0:
                cv.sprite(flip_h(rows) if yy in (16, 44) else rows, x - 7, yy, PAL)
            else:
                cv.sprite(rows if yy in (16, 44) else flip_h(rows), x + 3, yy, PAL)
        cv.sprite(BUD, x - 1, 62, PAL)
    # the bottom corners: a tuft of leaves and a daisy
    for (x, y, rows) in ((6, MACHINE_H - 4, LEAF_DIAG), (-3, MACHINE_H - 10, flip_h(LEAF_DIAG_S)),
                         (W - 13, MACHINE_H - 4, flip_h(LEAF_DIAG)), (W - 3, MACHINE_H - 10, LEAF_DIAG_S)):
        cv.sprite(rows, x, y, PAL)
    cv.sprite(DAISY, 12, MACHINE_H - 3, PAL)
    cv.sprite(DAISY, W - 19, MACHINE_H - 3, PAL)
    for (x, y) in ((4, 4), (W - 5, 4), (4, MACHINE_H - 5), (W - 5, MACHINE_H - 5)):
        frame_corner(cv, x + 0.5, y + 0.5)


def machine(cv):
    panel_bg(cv)
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
    frame(cv, 0, 0, W, MACHINE_H)


def inventory(cv):
    """The player's inventory on a pale livingrock panel hanging under the machine panel."""
    x1, x2, y1, y2 = INV_X1, INV_X2, MACHINE_H - FRAME, H
    ramp_l = [OUT, LR0, LR1, LR2, LR2, LR3]
    ramp_r = [OUT, LR3, LR2, LR2, LR2, LR4]
    for y in range(y1, y2):
        for x in range(x1, x2):
            dl, dr, db = x - x1, x2 - 1 - x, y2 - 1 - y
            d = min(dl, dr, db)
            if d >= 6:
                c = LR2 if rnd2(x, y, 7) > 0.08 else LR1
            elif d == dl:
                c = ramp_l[d]
            else:
                c = ramp_r[d]
            cv.set(x, y, c)
    for r in range(3):
        for c in range(9):
            vanilla_slot(cv, INV_SLOT_X + c * 18, INV_Y + r * 18)
    for c in range(9):
        vanilla_slot(cv, INV_SLOT_X + c * 18, HOTBAR_Y)
    # the joins under the machine panel: little livingwood brackets
    for bx in (x1 - 4, x2 - 2):
        cv.rect(bx, MACHINE_H, bx + 6, MACHINE_H + 3, OUT)
        cv.hline(bx + 1, bx + 5, MACHINE_H, LW1)
        cv.hline(bx + 1, bx + 5, MACHINE_H + 1, LW3)


def vanilla_slot(cv, x, y):
    cv.rect(x - 1, y - 1, x + 17, y + 17, SLOT_IN)
    cv.hline(x - 1, x + 17, y - 1, SLOT_LO)
    cv.vline(x - 1, y - 1, y + 17, SLOT_LO)
    cv.hline(x - 1, x + 17, y + 16, SLOT_HI)
    cv.vline(x + 16, y - 1, y + 17, SLOT_HI)
    cv.set(x - 1, y + 16, SLOT_IN)
    cv.set(x + 16, y - 1, SLOT_IN)


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
