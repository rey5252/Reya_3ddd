"""Elven Portal GUI panel texture, in the style of Mana Garden's: a deep navy panel with rounded corners
and a bright green edge, wound with curly leaf vines and hung with little gold lights. In the middle stands
the elven moon gate, as the block is: a ring of cream stone rimmed in gold with eight runes carved round
it, vines winding round it, a gem in its top, on a two-step pedestal, with the portal inside it (the screen
draws the swirl); the input slots are on its left, the output slots on its right, the mana bar under it.

    python3 tools/gen_gui.py            writes textures/gui/elven_portal.png

Coordinates are the menu's (PortalMenu): the panel is W x H, the texture has a margin M round it for the
vines that stick out of the frame. The moving parts (the swirl, the gem, the lit runes, the floating
crystals, the lit arrows, the mana, the gold lights' twinkle) are drawn over it by client/PortalScreen.java;
tools/check_layout.py keeps the two in step.
"""
import math
import os
import sys

from pix import Canvas, ASSETS, mix, shade, hexc, rnd, rnd2, rrect
from style import *
import vines as V

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "lib"))
import wood as WD  # noqa: E402

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
GATE = (120, 56)                     # the middle of the moon gate (the ring round the portal)
GATE_RO, GATE_RI = 34, 23            # the ring's outer radius, and the portal's inside it
GATE_BOX = (84, 20, 156, 100)        # x1, y1, x2, y2 round the whole gate and its pedestal (for its tooltip)
SWIRL = (97, 33, 46, 46)             # x, y, w, h of the portal's square: the screen draws the swirl there
GEM = (120, 28)                      # the middle of the gem in the ring's top, which the screen lights
# the eight runes carved round the ring (their 5x5 glyphs' top-left corners): the screen lights them
RUNES = [(144, 65), (129, 80), (107, 80), (92, 65), (92, 43), (107, 28), (129, 28), (144, 43)]
RUNE_R = 28.0                        # the runes' distance from the middle
CRYSTALS = [(94, 85), (142, 85)]     # the natura crystals' sprites (5x8) at rest on the lower step: the screen floats them
ARROWS = [(74, 53), (155, 53)]       # the arrows' top-left corners (12 x 9); the screen lights them on a trade
ARROW_W, ARROW_H = 12, 9
BAR = (64, 108, 176, 114)            # the mana bar's inside
BUTTONS = [(18, 102)]                # the redstone button (16 x 16)
POOL = (206, 102)                    # the pool light (16 x 16): lit while a mana pool is beside the portal

# the lights the screen makes twinkle (their middle pixel): the mana crystals on the panels' corners (the
# close button takes the top right one's place), then the gold lights on the gate's vines (from GOLD_LIGHTS on)
LIGHTS = [(1, 1), (1, 122), (238, 122), (29, 212), (210, 212), (85, 72), (153, 39)]
GOLD_LIGHTS = 5

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


# ---------------------------------------------------------------- behind the gate

PATTERN = hexc("2A4487")


def ring(cv, cx, cy, r, col):
    steps = int(r * 8)
    for k in range(steps):
        a = k / steps * math.tau
        cv.set(int(math.floor(cx + math.cos(a) * r)), int(math.floor(cy + math.sin(a) * r)), col)


CIRCLE_GLYPHS = [
    [".#.", "###", ".#."], ["#.#", ".#.", "#.#"], ["##.", "#.#", ".##"], [".##", "#..", ".##"],
    ["#.#", "###", "#.#"], ["###", ".#.", "#.#"], [".#.", "#.#", "###"], ["#..", "###", "..#"]]


def runes(cv):
    """A faint rune circle behind the gate, like the one behind Mana Garden's heart."""
    cx, cy = GATE
    for r, col in ((47.5, PATTERN), (45.5, mix(PATTERN, N3, 0.55))):
        ring(cv, cx, cy, r, col)
    for k in range(10):
        a = (k + 0.5) / 10 * math.tau
        x, y = int(round(cx + math.cos(a) * 51.5)), int(round(cy + math.sin(a) * 51.5))
        if y > GATE_BOX[3] - 8 or y < 8 or not (INPUT[0] + 56 < x < OUTPUT[0] - 3):
            continue                 # not over the slots or the frame
        for j, row in enumerate(CIRCLE_GLYPHS[k % len(CIRCLE_GLYPHS)]):
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


# ---------------------------------------------------------------- the moon gate

# the gate's runes, 5x5 (the widget sheet has them lit, in the same order)
GATE_GLYPHS = [
    ["..#..", ".###.", "#.#.#", "..#..", "..#.."],
    ["#.#.#", "#.#.#", ".###.", "..#..", "..#.."],
    [".###.", "#...#", "#.#.#", "#...#", ".###."],
    ["#...#", "##.##", "#.#.#", "#...#", "#...#"],
    ["..#..", ".#.#.", "#.#.#", ".#.#.", "..#.."],
    ["#.#..", "#.#..", "#.###", "#....", "#####"],
    [".#.#.", "#####", ".#.#.", "#####", ".#.#."],
    ["..#..", "..#..", "#####", ".#.#.", "#...#"]]
GATE_OUT = hexc("3A2408")


def gate_ring(cv, cx, cy, ro, ri, glyphs=None, spots=(), carve=None):
    """The moon gate's ring round (cx, cy): a dark outline, a gold rim (2 px), a band of cream stone, a thin
    gold inner rim and a dark inner edge, all lit from the top-left; runes carved in the stone at `spots`."""
    for y in range(int(cy - ro) - 1, int(cy + ro) + 2):
        for x in range(int(cx - ro) - 1, int(cx + ro) + 2):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(px, py)
            if d > ro or d <= ri:
                continue
            lit = (px * LIGHT_DIR[0] + py * LIGHT_DIR[1]) / max(d, 0.001)
            hi, lo = lit > 0.35, lit < -0.35
            if d > ro - 1:
                c = GATE_OUT
            elif d > ro - 3:
                c = Y1 if hi else (Y3 if lo else Y2)
            elif d > ri + 2:
                t = (d - (ri + 2)) / float(ro - 3 - (ri + 2))
                base = LR0 if hi else (LR2 if lo else LR1)
                c = mix(base, LR3, 0.25 * (1.0 - t)) if not hi else base
                if rnd2(x, y, 23) < 0.08:
                    c = mix(c, LR3, 0.4)
            elif d > ri + 1:
                c = Y2 if lo else Y3                    # the inner rim faces the other way
            else:
                c = GATE_OUT
            cv.set(x, y, c)
    if glyphs:
        for (gx, gy), g in zip(spots, glyphs):
            for j, row in enumerate(g):
                for i, ch in enumerate(row):
                    if ch == "#":
                        cv.set(gx + i, gy + j, carve or LR3)


def gate_void(cv, cx, cy, r):
    """The inside of the ring while the portal is shut: a dark green deep with a few faint stars."""
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d > r:
                continue
            c = mix(VOID0, VOID1, min(1.0, d / r))
            if rnd2(x, y, 51) < 0.02:
                c = mix(c, G1, 0.35)
            cv.set(x, y, c)


def gate_gem_socket(cv, gx, gy):
    """A gold setting round the pixel (gx, gy) with a dark hole (the screen sets the 7x7 gem in it)."""
    for dy in range(-5, 6):
        for dx in range(-5, 6):
            d = math.hypot(dx, dy)
            if d <= 4.6:
                cv.set(gx + dx, gy + dy, (Y1 if dx + dy < 0 else Y3) if d > 3.6 else hexc("0A1A12"))
    for (dx, dy) in ((-5, 0), (5, 0), (0, -5)):
        cv.set(gx + dx, gy + dy, GATE_OUT)


def pedestal(cv, cx, bottom, upper_w, lower_w, step_h=5):
    """Two steps of cream stone under the gate, the upper one banded in gold."""
    y2 = bottom
    for (w, y1, gold) in ((lower_w, bottom - step_h, False), (upper_w, bottom - 2 * step_h, True)):
        x1, x2 = cx - w // 2, cx + w // 2
        for y in range(y1, y2):
            for x in range(x1, x2):
                if x in (x1, x2 - 1) or y in (y1, y2 - 1):
                    cv.set(x, y, GATE_OUT)
                    continue
                if gold:
                    c = Y1 if y == y1 + 1 else (Y3 if y == y2 - 2 else Y2)
                    if (x - x1) % 6 == 3 and y1 + 1 < y < y2 - 2:
                        c = Y1
                else:
                    c = LR0 if y == y1 + 1 else (LR3 if y == y2 - 2 else LR1)
                    if (x - x1) % 12 == 0 and y > y1 + 1:
                        c = LR3
                    elif rnd2(x, y, 7) < 0.1:
                        c = LR2
                cv.set(x, y, c)
        y2 = y1


def arc_arm(cx, cy, radius, a0, length, seed, sway=0.12, period=26.0):
    """A vine along the outside of the ring, clockwise from the angle a0 for `length` px: blades swaying
    along it, tendrils curling outwards and a curl at its end."""
    phase = 0.13 * seed
    x, y = cx + math.cos(a0) * radius, cy + math.sin(a0) * radius
    path = V.walk(x, y, a0 + math.pi / 2, length + 26, V.both(lambda s: 1.0 / radius, V.wave(sway, period, phase)))
    specs = []
    for i in range(int(length // 13)):
        s = 7 + i * 13 + 2 * ((seed + i) % 3)
        if s > length - 4:
            break
        specs.append(V.hook_spec(path, s, -1, 7 + ((seed + 2 * i) % 3), 4.0 + 0.4 * ((seed + i) % 2), TONES, r0=1.0))
    specs += V.chain_specs(path, 0, length - 2, TONES, length=12, pitch=7.5, r=1.8, seed=seed)
    ex, ey, eth = V.at(path, length - 3)
    specs.append(V.curl_spec(ex, ey, eth, 16, -4.6, 1.5, 0.6, TONES, power=1.3))
    return specs


# the vines round the gate: (start angle, length) clockwise along a circle just outside the ring
GATE_VINES = [(math.radians(108), 27, 5), (math.radians(288), 27, 6)]


def gate_vine_specs():
    cx, cy = GATE
    specs = []
    for (a0, length, seed) in GATE_VINES:
        specs += arc_arm(cx, cy, GATE_RO + 1.5, a0, length, seed)
    return specs


def gate(cv):
    cx, cy = GATE
    gate_void(cv, cx, cy, GATE_RI + 0.5)
    V.paint_specs(cv, gate_vine_specs(), VOUT)
    gate_ring(cv, cx, cy, GATE_RO, GATE_RI, GATE_GLYPHS, RUNES)
    pedestal(cv, cx, GATE_BOX[3] - 2, 44, 58)
    gate_gem_socket(cv, *GEM)


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
                    cv.set(ax + i, ay + j, mix(WD.WOOD[4], WD.LR[2], 0.35))


def mana_bar_track(cv):
    x1, y1, x2, y2 = BAR
    WD.bar_track(cv, x1, y1, x2, y2)
    for x in range(x1 + 14, x2, 14):
        cv.vline(x, y1 + 1, y2, hexc("15283A"))
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
    """The mana crystals on the panels' corners and the gold lights on the gate's vines."""
    for (x, y) in LIGHTS[:GOLD_LIGHTS]:
        WD.crystal(cv, x, y)
    for (x, y) in LIGHTS[GOLD_LIGHTS:]:
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
    WD.panel(cv, 0, 0, W, MACHINE_H)
    cx, cy = GATE
    WD.inlay_ring(cv, cx, cy, 46.5)
    WD.carved_runes(cv, cx, cy, 51.5, 10, skip=lambda x, y: y > GATE_BOX[3] - 6 or y < 9 or not (INPUT[0] + 56 < x < OUTPUT[0] - 3))
    for i in range(9):
        WD.slot(cv, INPUT[0] + i % 3 * 18, INPUT[1] + i // 3 * 18, WD.SLOT_IN)
        WD.slot(cv, OUTPUT[0] + i % 3 * 18, OUTPUT[1] + i // 3 * 18, WD.SLOT_OUT)
    arrows(cv)
    gate(cv)
    mana_bar_track(cv)


def inventory(cv):
    """The player's inventory on a livingwood panel hanging under the machine panel."""
    WD.panel(cv, INV_X1, MACHINE_H - 14, INV_X2, H, inner=False)
    for r in range(3):
        for c in range(9):
            WD.slot(cv, INV_SLOT_X + c * 18, INV_Y + r * 18, WD.SLOT_INV)
    for c in range(9):
        WD.slot(cv, INV_SLOT_X + c * 18, HOTBAR_Y, WD.SLOT_INV)


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
