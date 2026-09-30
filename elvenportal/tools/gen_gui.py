"""Elven Portal GUI panel texture, in the style of Mana Garden's: a deep navy panel with rounded corners
and a bright green edge, wound with curly leaf vines and hung with little gold lights. In the middle stands
a dreamwood arch, the Elven Gateway Core for its keystone, with the portal inside it (the screen draws the
swirl); the input slots are on its left, the output slots on its right, the mana bar under it.

    python3 tools/gen_gui.py            writes textures/gui/elven_portal.png

Coordinates are the menu's (PortalMenu): the panel is W x H, the texture has a margin M round it for the
vines that stick out of the frame. The moving parts (the swirl, the gem, the lit arrows, the mana, the
gold lights' twinkle) are drawn over it by client/PortalScreen.java; tools/check_layout.py keeps the two in
step.
"""
import math
import os
import sys

from pix import Canvas, ASSETS, mix, shade, hexc, rnd, rnd2, rrect
from style import *
import vines as V

M = 12
W, H = 240, 214
MACHINE_H = 124
FRAME = 5                            # the edge: outline, two rows of green, the mana vein, a navy bevel
VEIN = 3                             # the frame row the mana runs along (2 px in from the outline)
CORNER_R = 5
INV_X1, INV_X2 = 28, 212

INPUT = (18, 30)                     # the first input slot's item corner; three rows of three, 18 apart
OUTPUT = (168, 30)                   # the first output slot's item corner
INV_Y, HOTBAR_Y, INV_SLOT_X = 132, 190, 40
ARCH = (84, 8, 156, 102)             # x1, y1, x2, y2 round the whole arch
SWIRL = (98, 24, 44, 72)             # x, y, w, h of the portal inside the arch: the screen draws the swirl there
GEM = (120, 17)                      # the middle of the keystone's gem, which the screen lights
ARROWS = [(74, 53), (155, 53)]       # the arrows' top-left corners (12 x 9); the screen lights them on a trade
ARROW_W, ARROW_H = 12, 9
BAR = (64, 108, 176, 114)            # the mana bar's inside
BUTTONS = [(18, 102)]                # the redstone button (16 x 16)
POOL = (206, 102)                    # the pool light (16 x 16): lit while a mana pool is beside the portal

# the little gold lights on the vines (their middle pixel); the screen makes them twinkle
LIGHTS = [
    (21, -8), (-6, 21), (-6, 47), (-8, -8), (222, -9), (247, 16), (247, 47), (247, -8),
    (21, 134), (-9, 103), (-10, 76), (-8, 131), (218, 132), (248, 101), (250, 75), (247, 131)]

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
    """The machine panel's navy: a smooth gradient from the lit top to a deeper bottom."""
    t = y / float(MACHINE_H)
    return mix(N2, N4, t ** 0.9)


def inventory_fill(x, y):
    t = (y - MACHINE_H) / float(H - MACHINE_H)
    return mix(N3, N4, t)


# ---------------------------------------------------------------- behind the arch

PATTERN = hexc("2A4487")


def ring(cv, cx, cy, r, col):
    steps = int(r * 8)
    for k in range(steps):
        a = k / steps * math.tau
        cv.set(int(math.floor(cx + math.cos(a) * r)), int(math.floor(cy + math.sin(a) * r)), col)


RUNES = [
    [".#.", "###", ".#."], ["#.#", ".#.", "#.#"], ["##.", "#.#", ".##"], [".##", "#..", ".##"],
    ["#.#", "###", "#.#"], ["###", ".#.", "#.#"], [".#.", "#.#", "###"], ["#..", "###", "..#"]]


def runes(cv):
    """A faint rune circle behind the arch, like the one behind Mana Garden's heart."""
    cx, cy = 120, 57
    for r, col in ((49.5, PATTERN), (47.5, mix(PATTERN, N3, 0.55))):
        ring(cv, cx, cy, r, col)
    for k in range(10):
        a = (k + 0.5) / 10 * math.tau
        x, y = int(round(cx + math.cos(a) * 53.5)), int(round(cy + math.sin(a) * 53.5))
        if ARCH[0] - 3 <= x <= ARCH[2] + 3 and y < ARCH[3]:
            continue
        for j, row in enumerate(RUNES[k % len(RUNES)]):
            for i, ch in enumerate(row):
                if ch == "#":
                    cv.set(x - 1 + i, y - 1 + j, mix(PATTERN, M2, 0.3))


# ---------------------------------------------------------------- slots

SAGE = (S1, S2, S3)                  # an input slot's rim: lit side, sides, shaded side
GOLD_RIM = (Y1, Y2, Y3)              # an output slot's rim: what comes back from the elves
DIM_SAGE = (S2, S3, S4)              # the inventory's slots: calmer than the machine's, so those stand out


def slot_frame(cv, x, y, rim, inside_top, inside_bottom):
    """An 18x18 slot (x, y = the item's corner): a bevelled rim, a dark inside with a shadow under the
    top-left rim and a faint light at the bottom-right, the rim's four corners rounded."""
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


def grid(cv, corner, rim, top, bottom):
    for i in range(9):
        slot_frame(cv, corner[0] + i % 3 * 18, corner[1] + i // 3 * 18, rim, top, bottom)


def inventory_slot(cv, x, y):
    slot_frame(cv, x, y, DIM_SAGE, hexc("14204A"), hexc("0E1838"))


# ---------------------------------------------------------------- the arch

def dreamwood(x, y, vertical, lit, seed):
    """A pixel of dreamwood: pale grey-green, lit by `lit` (-1..1), with grooves along the grain every three
    pixels that wander by a pixel now and then."""
    along, across = (y, x) if vertical else (x, y)
    base = mix(DW3, DW1, (lit + 1) / 2)
    wander = 1 if rnd(along // 5 + seed * 31, across // 3 + 7) < 0.28 else 0
    if (across + wander) % 3 == 0:
        base = mix(base, DW4, 0.5)
    elif rnd2(across, along // 2, 17 + seed) < 0.06:
        base = mix(base, DW0, 0.6)
    return base


def box(cv, x1, y1, x2, y2, fill, outline=DWOUT):
    """A block of the arch: an outline and an inside painted by fill(x, y, lit) with lit = -1 (right/bottom
    edge) .. 1 (left/top edge)."""
    for y in range(y1, y2):
        for x in range(x1, x2):
            if x in (x1, x2 - 1) or y in (y1, y2 - 1):
                cv.set(x, y, outline)
                continue
            fx = (x - x1 - 1) / max(1.0, x2 - x1 - 3.0)
            fy = (y - y1 - 1) / max(1.0, y2 - y1 - 3.0)
            lit = 1.0 if x == x1 + 1 or y == y1 + 1 else (-1.0 if x == x2 - 2 or y == y2 - 2 else 0.3 - 0.6 * (fx + fy) / 2)
            cv.set(x, y, fill(x, y, lit))


def pillar(cv, x1, x2, seed):
    y1, y2 = SWIRL[1] - 2, ARCH[3] - 5
    box(cv, x1, y1, x2, y2, lambda x, y, lit: dreamwood(x, y, True, lit, seed))
    # a spiral of glimmer carved in the middle of the pillar, like on glimmering dreamwood
    cx, cy = (x1 + x2) / 2.0 - 0.5, (y1 + y2) / 2.0
    for k in range(26):
        a = k * 0.55
        r = 0.6 + k * 0.17
        x, y = int(round(cx + math.cos(a) * r)), int(round(cy + math.sin(a) * r * 1.3))
        if x1 + 2 <= x < x2 - 2 and y1 + 2 <= y < y2 - 2:
            cv.set(x, y, PK1 if k % 5 else PK0)


def lintel(cv):
    x1, y1, x2, y2 = ARCH[0], SWIRL[1] - 14, ARCH[2], SWIRL[1]
    box(cv, x1, y1, x2, y2, lambda x, y, lit: dreamwood(x, y, False, lit, 5))
    # a thin groove along it
    for x in range(x1 + 3, x2 - 3):
        if not (GEM[0] - 9 <= x <= GEM[0] + 8):
            cv.set(x, y1 + 8, mix(DW4, DW5, 0.4))


def keystone(cv):
    """The Elven Gateway Core as the keystone: dark livingwood with green glyphs and a gem socket."""
    x1, y1, x2, y2 = GEM[0] - 8, ARCH[1], GEM[0] + 8, SWIRL[1] + 3
    for y in range(y1, y2):
        for x in range(x1, x2):
            if x in (x1, x2 - 1) or y in (y1, y2 - 1):
                cv.set(x, y, CW4)
                continue
            lit = 1 if x == x1 + 1 or y == y1 + 1 else (-1 if x == x2 - 2 or y == y2 - 2 else 0)
            c = CW1 if lit > 0 else (CW3 if lit < 0 else CW2)
            if rnd2(x, y, 41) < 0.15:
                c = mix(c, CW3, 0.5)
            cv.set(x, y, c)
    glyphs = ["G..G.GG.G..G",
              "G.GG....GG.G",
              "............",
              "............",
              "............",
              "............",
              "............",
              "............",
              "............",
              "............",
              "............",
              "............",
              "GG.G....G.GG",
              "G..GG..GG..G",
              "............",
              "..G.GGGG.G..",
              "............"]
    for j, row in enumerate(glyphs):
        for i, ch in enumerate(row):
            if ch == "G":
                cv.set(x1 + 2 + i, y1 + 2 + j, GLYPH1 if (i + j) % 3 else GLYPH0)
    # the gem's socket, round the pixel GEM: a gold ring round a dark hole (the screen sets the 7x7 gem in it)
    gx, gy = GEM
    for dy in range(-5, 6):
        for dx in range(-5, 6):
            d = math.hypot(dx, dy)
            if d <= 4.6:
                cv.set(gx + dx, gy + dy, (Y2 if dx + dy < 0 else Y3) if d > 3.6 else hexc("0A1A12"))


def base(cv):
    """The livingrock step the arch stands on, with a natura crystal at each end."""
    x1, y1, x2, y2 = ARCH[0] - 2, ARCH[3] - 6, ARCH[2] + 2, ARCH[3]
    for y in range(y1, y2):
        for x in range(x1, x2):
            if x in (x1, x2 - 1) or y in (y1, y2 - 1):
                cv.set(x, y, hexc("2C2820"))
                continue
            c = LR0 if y == y1 + 1 else (LR3 if y == y2 - 2 else LR1)
            if (x - x1) % 12 == 0 and y > y1 + 1:
                c = LR3
            elif rnd2(x, y, 7) < 0.12:
                c = LR2
            cv.set(x, y, c)
    for cx in (ARCH[0] + 1, ARCH[2] - 2):
        crystal(cv, cx, y1 - 1)


def crystal(cv, cx, bottom):
    """A little natura crystal: a green diamond with a white glint, standing on the step."""
    rows = ["..o..",
            ".oho.",
            ".olm.",
            "olmmo",
            "olmdo",
            ".omd.",
            ".odd.",
            "..o.."]
    pal = {"o": hexc("0E3B22"), "h": hexc("F4FFE8"), "l": G0, "m": G1, "d": G3}
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != ".":
                cv.set(cx - 2 + i, bottom - len(rows) + 1 + j, pal[ch])


def opening(cv):
    """The portal inside the arch: a dark void with a faint glow in the middle (the swirl goes over it)."""
    x, y, w, h = SWIRL
    for j in range(h):
        for i in range(w):
            u, v = (i + 0.5 - w / 2) / (w / 2), (j + 0.5 - h / 2) / (h / 2)
            r = math.hypot(u, v)
            c = mix(VOID0, VOID1, min(1.0, r))
            if rnd2(i, j, 51) < 0.012:
                c = mix(c, G1, 0.35)
            cv.set(x + i, y + j, c)
    # a shadow under the lintel
    cv.hline(x, x + w, y, VOID1)
    cv.hline(x, x + w, y + 1, mix(VOID1, VOID0, 0.4))


def arch(cv):
    opening(cv)
    pillar(cv, ARCH[0] + 2, SWIRL[0], 1)
    pillar(cv, SWIRL[0] + SWIRL[2], ARCH[2] - 2, 2)
    lintel(cv)
    base(cv)
    keystone(cv)


# ---------------------------------------------------------------- arrows, the mana bar

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
    """Two dim double chevrons, into the portal and out of it; the screen lights them on a trade."""
    for (ax, ay) in ARROWS:
        for j, row in enumerate(ARROW):
            for i, ch in enumerate(row):
                if ch == "#":
                    cv.set(ax + i, ay + j, mix(N2, S2, 0.35))


def mana_bar_track(cv):
    x1, y1, x2, y2 = BAR
    cv.outline(x1 - 2, y1 - 2, x2 + 2, y2 + 2, OUT)
    cv.hline(x1 - 1, x2 + 1, y1 - 1, M5)
    cv.vline(x1 - 1, y1 - 1, y2 + 1, M5)
    cv.hline(x1 - 1, x2 + 1, y2, M2)
    cv.vline(x2, y1 - 1, y2 + 1, M2)
    cv.set(x1 - 1, y2, M4)
    cv.set(x2, y1 - 1, M4)
    cv.rect(x1, y1, x2, y2, hexc("07152C"))
    for x in range(x1 + 14, x2, 14):
        cv.vline(x, y1 + 1, y2, hexc("12305A"))
    # a mana drop before it
    drop = ["..#..",
            ".#c#.",
            "#cCc#",
            "#cCC#",
            "#ccC#",
            ".###."]
    pal = {"#": M5, "c": M3, "C": M1}
    for j, row in enumerate(drop):
        for i, ch in enumerate(row):
            if ch != ".":
                cv.set(x1 - 12 + i, y1 - 2 + j, pal[ch])


# ---------------------------------------------------------------- vines and lights round the frame

def edge_arm(x, y, theta, length, side, seed, sway=0.26, period=30.0):
    """A vine along an edge, from (x, y) heading theta for `length` px: a chain of blades swaying along
    the walk, little tendrils curling out to `side` (+1 clockwise, -1 anticlockwise on the screen: the
    edge's outer side) and a curl at its end."""
    phase = 0.13 * seed
    path = V.walk(x, y, theta + sway * math.sin(math.tau * phase), length + 26, V.wave(sway, period, phase))
    specs = []
    for i in range(int(length // 14)):
        s = 8 + i * 14 + 2 * ((seed + i) % 3)
        if s > length - 4:
            break
        specs.append(V.hook_spec(path, s, side, 8 + ((seed + 2 * i) % 3), 4.3 + 0.4 * ((seed + i) % 2), TONES, r0=1.1))
    specs += V.chain_specs(path, 0, length - 2, TONES, length=14, pitch=8, r=1.9, seed=seed)
    ex, ey, eth = V.at(path, length - 3)
    specs.append(V.curl_spec(ex, ey, eth, 20, side * 5.4, 1.6, 0.6, TONES, power=1.3))
    return specs


def corner_cluster():
    """Leaves and a bud where the two arms meet, pointing out of the corner."""
    return [V.spec(V.blade((-3.0, -3.0), ang, ln, 4.4, bend=bend), TONES)
            for ang, ln, bend in ((-2.95, 8, 0.5), (-1.75, 8, -0.5), (-2.35, 7, 0.0))]


def corner_specs(h_len, v_len, seed):
    """A panel corner's vines, drawn for the top-left corner (the others are mirrors): an arm along the
    top edge, an arm down the left edge, a cluster of leaves on the corner."""
    return (edge_arm(-3, -3.4, 0.0, h_len, -1, seed) + edge_arm(-3.4, -3, math.pi / 2, v_len, 1, seed + 3)
            + corner_cluster())


# the arms' lengths at the corners: the top ones stop short of the title scroll, the bottom ones of the inventory
CORNERS = [(False, False, 44, 50, 1), (True, False, 44, 50, 2), (False, True, 22, 50, 3), (True, True, 22, 50, 4)]


def tendril_tips():
    """Where the vines' tendrils end (the lights hang there): for choosing LIGHTS."""
    cx, cy = W / 2.0, MACHINE_H / 2.0
    tips = []
    for (fx, fy, h_len, v_len, seed) in CORNERS:
        for (samples, tones, thr, min_r) in corner_specs(h_len, v_len, seed):
            if fx or fy:
                samples = V.mirrored(samples, fx, fy, cx, cy)
            x, y, r, th = samples[-1]
            if r < 0.9:
                tips.append((math.floor(x), math.floor(y)))
    return tips


def decorations(cv):
    cx, cy = W / 2.0, MACHINE_H / 2.0
    for (fx, fy, h_len, v_len, seed) in CORNERS:
        V.paint_specs(cv, corner_specs(h_len, v_len, seed), VOUT, fx=fx, fy=fy, cx=cx, cy=cy)
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
    grid(cv, INPUT, SAGE, hexc("10193A"), hexc("0B1129"))
    grid(cv, OUTPUT, GOLD_RIM, hexc("1A1830"), hexc("100E22"))
    arrows(cv)
    arch(cv)
    mana_bar_track(cv)


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
    if "--tips" in sys.argv:
        print(tendril_tips())
        return
    cv = build()
    cv.save(os.path.join(ASSETS, "textures", "gui", "elven_portal.png"))


if __name__ == "__main__":
    main()
