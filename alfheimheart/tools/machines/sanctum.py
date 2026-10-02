"""The Runic Altar's GUI: the rune sanctum (textures/gui/rune_altar.png, rune_altar_widgets.png, rune_altar_gloss.png).

A ritual circle on a floor of dark slate flagstones, gold set into it: the altar's octagonal top in the middle
(livingrock, a gold rim, a dark hollow the rune takes shape in), a gold ring round it, nine sockets on a ring
round that (the eight inputs, each a pedestal under one of the eight elements' runes, their colours a rainbow
round the ring; the livingrock socket the ninth, at the bottom), channels from each socket in to the altar
(mana runs along them while it works), the circle's double gold rim with its marks. On the left a crystal
column the mana fills (a pool lamp at its foot); on the right an arched reliquary the runes made go into.

The screen (machine/altar/client/RuneAltarScreen.java) draws the moving parts on layouts.ALTAR's numbers.
"""
import math

import art as A
import theme as T
from layouts import ALTAR, W, MACHINE_H, FRAME, altar_spokes
from pix import hexc

S = [hexc(h) for h in ("0A0B15", "121427", "1A1E38", "232849", "2E345B", "3C446F", "525B8C", "727CAE")]
G = [hexc(h) for h in ("FFF9D2", "FFE47D", "F4BA2E", "BA7C16", "6E4308")]
LIVINGROCK = [hexc(h) for h in ("FBF8EE", "E2DCCB", "C9C2B1", "A89F8B", "7A715F")]
MANA = [hexc(h) for h in ("F2FFFF", "A6F6FF", "55D9F7", "2A9FE2", "1B64B8", "0B2F5C")]
VELVET = [hexc(h) for h in ("3A2266", "2A184D", "1D1038", "130A26")]

THEME = T.Theme(
    outline=S[0], metal=(G[0], G[1], G[2], G[3], G[4]), groove=hexc("07080F"),
    stone=(S[5], S[4], S[2]), stone_noise=0.35, floor_shadow=hexc("05060C"),
    slot_rim=(S[6], S[4], S[1]), slot_inside=(hexc("161A30"), hexc("0C0E1C")),
    inv_bg=lambda x, y: A.tint(A.lerp(S[3], S[2], (y - 150) / 98.0), (A.fbm(x, y, 9, 77) - 0.5) * 0.22),
    plaque_face=(S[4], S[2]), plaque_text_hint=G[1], button_face=(S[5], S[4], S[2]), crystal=MANA[:5], salt=3)

# the eight runes over the inputs (layouts.ALTAR.RUNES' order) and the mana rune of the livingrock socket, 9x9
GLYPHS = {
    "fire": ["....#....",
             "...##....",
             "...#.#...",
             "..#..#.#.",
             "..#...##.",
             ".#.....#.",
             ".#..#..#.",
             "..#.#.#..",
             "...###..."],
    "autumn": [".....###.",
               "...##..#.",
               "..#...#..",
               ".#...#.#.",
               ".#..#..#.",
               ".#.#..#..",
               "..##.#...",
               ".####....",
               "#........"],
    "summer": ["....#....",
               ".#..#..#.",
               "..#####..",
               "..#...#..",
               "###...###",
               "..#...#..",
               "..#####..",
               ".#..#..#.",
               "....#...."],
    "earth": [".........",
              "....#....",
              "...#.#...",
              "..#...#..",
              "..#.#.#..",
              ".#.#.#.#.",
              ".#.....#.",
              "#########",
              "........."],
    "winter": ["....#....",
               ".#..#..#.",
               "..#.#.#..",
               "...###...",
               "#########",
               "...###...",
               "..#.#.#..",
               ".#..#..#.",
               "....#...."],
    "water": ["....#....",
              "....#....",
              "...###...",
              "..##.##..",
              ".##...##.",
              ".#.....#.",
              ".#.....#.",
              "..#...#..",
              "...###..."],
    "air": ["..#####..",
            ".#.....#.",
            "#..###..#",
            "#.#...#.#",
            "#.#.#.#.#",
            "#.#..#..#",
            "#..#....#",
            ".#..####.",
            "..#......"],
    "spring": [".........",
               "..##.##..",
               ".#..#..#.",
               ".#..#..#.",
               "..#.#.#..",
               "....#....",
               "....#....",
               "..#####..",
               "........."],
    "mana": ["....#....",
             "...#.#...",
             "..#...#..",
             ".#..#..#.",
             "#..###..#",
             ".#..#..#.",
             "..#...#..",
             "...#.#...",
             "....#...."],
}

CX, CY = ALTAR.CX, ALTAR.CY


def rgb(i):
    return (i >> 16 & 0xFF, i >> 8 & 0xFF, i & 0xFF)


# ---------------------------------------------------------------- the floor

def flagstone(x, y):
    """The sanctum's floor outside the circle: slate flagstones in staggered rows, each its own shade, their
    edges bevelled, dark mortar between; darker towards the panel's edges."""
    row_h = 13
    row = (y + 3) // row_h
    yy = (y + 3) % row_h
    off = (row * 23) % 37
    w = 26 + int(A.rnd2(row, 0, 5) * 10)
    col = (x + off) // w
    xx = (x + off) % w
    if yy == 0 or xx == 0:
        c = S[0]
    else:
        base = A.lerp(S[2], S[3], A.rnd2(row, col, 7))
        n = A.fbm(x, y, 7.0, 11, 2)
        c = A.tint(base, (n - 0.5) * 0.28)
        if A.rnd2(x, y, 13) < 0.03:
            c = A.light(c, 0.12)
        if yy == 1 or xx == 1:
            c = A.light(c, 0.16)                                   # the stone's lit edge
        elif yy == row_h - 1 or xx == w - 1:
            c = A.dark(c, 0.25)
        crack = A.vein(x, y, 16.0, 19 + row, 0.02)
        if crack > 0.6:
            c = A.dark(c, 0.35 * crack)
    # the vignette
    dx = (x - W / 2.0) / (W / 2.0)
    dy = (y - MACHINE_H / 2.0) / (MACHINE_H / 2.0)
    v = min(1.0, (dx * dx * 0.7 + dy * dy) * 0.55)
    return A.dark(c, 0.45 * v)


def ritual_floor(x, y, d):
    """Inside the circle: polished dark stone, lighter towards the middle, faint rings polished into it."""
    t = d / float(ALTAR.OUTER_R)
    c = A.ramp([(0.0, hexc("323A70")), (0.45, hexc("222851")), (1.0, hexc("141832"))], t)
    n = A.fbm(x, y, 10.0, 41, 3)
    c = A.tint(c, (n - 0.5) * 0.18)
    if abs((d % 6.0) - 3.0) < 0.35 and d > ALTAR.INNER_R + 3:
        c = A.light(c, 0.04)
    if A.rnd2(x, y, 43) < 0.012:
        c = A.lerp(c, MANA[2], 0.35)                               # a fleck of mana in the stone
    return c


def background(x, y):
    d = math.hypot(x + 0.5 - CX, y + 0.5 - CY)
    if d < ALTAR.OUTER_R - 3:
        return ritual_floor(x, y, d)
    return flagstone(x, y)


# ---------------------------------------------------------------- the circle

def gold_ring(cv, r, w, bevel=True):
    """A ring of gold, its half-width w, lit on the top-left of its outer edge, the bottom-right of its inner."""
    f = A.shell(A.circle(CX, CY, r), w)
    outer = A.circle(CX, CY, r)
    for x, y, d in A.bounds(f, CX - r - w - 2, CY - r - w - 2, CX + r + w + 2, CY + r + w + 2):
        px, py = x + 0.5, y + 0.5
        k = A.facing(outer, px, py)
        out = outer(px, py) > 0
        if not bevel:
            c = G[2]
        elif d < 0.6:
            c = G[4] if (k < 0) == out else G[3]
        else:
            kk = k if out else -k
            c = G[1] if kk > 0.35 else (G[3] if kk < -0.35 else G[2])
        cv.set(x, y, c)


def outer_rim(cv):
    """The circle's double rim: an outer gold ring, an inner thin one, marks between, studs at the sockets."""
    r_out, r_in = ALTAR.OUTER_R, ALTAR.OUTER_R - 4
    gold_ring(cv, r_out, 1.1)
    gold_ring(cv, r_in, 0.6, bevel=False)
    for k in range(48):
        deg = k * 7.5
        if any(abs(((deg - a + 180) % 360) - 180) < 9 for a in ALTAR.INPUT_ANGLES + [ALTAR.REAGENT_ANGLE]):
            continue
        long = k % 2 == 0
        for t in (1.2, 2.2) if long else (1.8,):
            x, y = CX + (r_in + t) * math.cos(math.radians(deg)), CY + (r_in + t) * math.sin(math.radians(deg))
            cv.set(int(math.floor(x)), int(math.floor(y)), G[3] if long else A.lerp(G[3], S[3], 0.4))
    for deg in ALTAR.INPUT_ANGLES + [ALTAR.REAGENT_ANGLE]:
        x, y = CX + (r_in + 2) * math.cos(math.radians(deg)), CY + (r_in + 2) * math.sin(math.radians(deg))
        stud = A.polygon(x, y, 2.2, 4, 45)
        A.draw(cv, stud, (x - 4, y - 4, x + 4, y + 4), G[2], outline=G[4], bevel=1.0, hi=0.6, lo=0.4)


def spokes(cv):
    """The channels in to the altar: a dark groove, its far side lit, the socket's colour faint along it."""
    colours = [rgb(c) for c in ALTAR.RUNE_COLOURS] + [rgb(ALTAR.REAGENT_COLOUR)]
    for (x1, y1, x2, y2), col in zip(altar_spokes(), colours):
        groove = A.segment(x1, y1, x2, y2, 1.6)
        for x, y, d in A.bounds(groove, min(x1, x2) - 3, min(y1, y2) - 3, max(x1, x2) + 4, max(y1, y2) + 4):
            k = A.facing(groove, x + 0.5, y + 0.5)
            if d < 0.7:
                c = S[5] if k < -0.3 else S[1]                       # sunk: its far (lower right) side lit
            else:
                c = A.lerp(hexc("06070E"), col, 0.28)
            cv.set(x, y, c)


def octagon_altar(cv):
    """The altar's top: livingrock in a gold rim, its eight little runes, a gold ring round the dark hollow."""
    r = ALTAR.ALTAR_R
    top = A.polygon(CX, CY, r, 8, 22.5)
    A.shadow(cv, top, (CX - r - 2, CY - r - 2, CX + r + 2, CY + r + 2), dx=2, dy=3, alpha=150, soft=2.0)
    hollow_r = ALTAR.HOLLOW_R
    for x, y, depth in A.bounds(top, CX - r - 2, CY - r - 2, CX + r + 2, CY + r + 2):
        px, py = x + 0.5, y + 0.5
        k = A.facing(top, px, py)
        d = math.hypot(px - CX, py - CY)
        if depth <= 1.0:
            c = S[0]
        elif depth <= 3.0:                                         # the gold rim
            c = G[0] if k > 0.6 else (G[1] if k > 0.2 else (G[3] if k < -0.3 else G[2]))
            if depth <= 2.0 and k < -0.3:
                c = G[4]
        elif depth <= 4.0:
            c = S[1]
        elif d > hollow_r + 2.5:                                   # livingrock, lit across from the top-left
            t = ((px - CX) * A.LIGHT[0] + (py - CY) * A.LIGHT[1]) / r
            c = A.ramp([(-1.0, LIVINGROCK[3]), (0.0, LIVINGROCK[2]), (1.0, LIVINGROCK[0])], t)
            c = A.tint(c, (A.noise(x, y, 2.0, 61) - 0.5) * 0.14)
            if depth <= 5.0:
                c = A.dark(c, 0.18)                                # the rim's shadow on it
        elif d > hollow_r + 1.0:                                   # the hollow's gold ring
            kk = -A.facing(A.circle(CX, CY, hollow_r + 1.0), px, py)
            c = G[1] if kk > 0.3 else (G[3] if kk < -0.3 else G[2])
        elif d > hollow_r:
            c = S[0]
        else:                                                      # the hollow: the deep, faint stars
            t = d / hollow_r
            c = A.ramp([(0.0, hexc("1C3466")), (0.6, hexc("0E1834")), (1.0, hexc("060A16"))], t)
            if A.rnd2(x, y, 63) < 0.07:
                c = A.lerp(c, MANA[1], 0.5)
        cv.set(x, y, c)
    marks = [["#.#", ".#.", "#.#"], [".#.", "###", ".#."], ["#..", ".#.", "..#"], ["###", "#.#", "###"]]
    for k in range(8):
        a = math.radians(k * 45 + 22.5 - 90)
        x, y = CX + 16.0 * math.cos(a), CY + 16.0 * math.sin(a)
        A.glyph(cv, marks[k % 4], int(round(x - 1.5)), int(round(y - 1.5)), None, 255, carve=(LIVINGROCK[4], LIVINGROCK[0]))


def pedestal(cv, x, y, colour, mark, reagent=False):
    """A socket: a raised slate pedestal (gold-trimmed; livingrock for the reagent) round a slot sunk into it, its
    rune faint in the slot's hollow, a little gem of its colour on its outer side."""
    x1, y1, x2, y2 = x - 4, y - 4, x + 20, y + 20
    body = A.box(x1, y1, x2, y2, 4)
    A.shadow(cv, body, (x1, y1, x2, y2), dx=1, dy=2, alpha=150, soft=1.5)
    trim = (LIVINGROCK[0], LIVINGROCK[1], LIVINGROCK[3]) if reagent else (G[1], G[2], G[3])
    for px, py, depth in A.bounds(body, x1, y1, x2, y2):
        k = A.facing(body, px + 0.5, py + 0.5)
        if depth <= 1.0:
            c = S[0]
        elif depth <= 2.0:
            c = trim[0] if k > 0.3 else (trim[2] if k < -0.3 else trim[1])
        else:
            base = LIVINGROCK[2] if reagent else S[4]
            c = A.tint(base, 0.2 * k)
            c = A.tint(c, (A.noise(px, py, 2.0, 71) - 0.5) * 0.12)
        cv.set(px, py, c)
    rim = (LIVINGROCK[3], LIVINGROCK[4], LIVINGROCK[1]) if reagent else (S[6], S[3], S[0])
    T.slot(cv, x, y, THEME, rim=rim, inside=(hexc("161A30"), hexc("0B0D1A")), mark=mark, mark_colour=colour, mark_alpha=70)


def sockets(cv):
    cols = [rgb(c) for c in ALTAR.RUNE_COLOURS]
    for (x, y), name, col in zip(ALTAR.INPUTS, ALTAR.RUNES, cols):
        pedestal(cv, x, y, col, GLYPHS[name])
    rx, ry = ALTAR.REAGENT
    pedestal(cv, rx, ry, rgb(ALTAR.REAGENT_COLOUR), GLYPHS["mana"], reagent=True)


# ---------------------------------------------------------------- the crystal column

def column(cv):
    """The mana's crystal column: a glass tube (the screen fills it) with marks up its side, a crystal point on
    top in a gold collar, a gold foot over the pool lamp's socket."""
    x1, y1, x2, y2 = ALTAR.GAUGE
    tube = A.box(x1 - 2, y1 - 2, x2 + 2, y2 + 2, 2)
    A.shadow(cv, tube, (x1 - 2, y1 - 2, x2 + 2, y2 + 2), dx=2, dy=2, alpha=140, soft=1.5)
    for x, y, d in A.bounds(tube, x1 - 2, y1 - 2, x2 + 2, y2 + 2):
        k = A.facing(tube, x + 0.5, y + 0.5)
        if d <= 1.0:
            c = S[0]
        elif d <= 2.0:                                             # the glass's thick edge
            c = hexc("BFEFFF") if k > 0.3 else (hexc("3A6C8C") if k < -0.3 else hexc("6FA8C8"))
        else:                                                      # empty glass: dark, a cold sheen down its left
            t = (y - y1) / float(y2 - y1)
            c = A.lerp(hexc("0C1730"), hexc("070C1A"), t)
            if x == x1 + 1:
                c = A.lerp(c, hexc("8FD8FF"), 0.18)
            if x == x2 - 2:
                c = A.lerp(c, hexc("8FD8FF"), 0.08)
        cv.set(x, y, c)
    # marks every quarter up the right side
    for q in range(1, 4):
        y = int(round(y2 - (y2 - y1) * q / 4.0))
        for x in range(x2 + 2, x2 + 5 if q == 2 else x2 + 4):
            cv.set(x, y, G[2])
            cv.set(x, y + 1, G[4])
    # the crystal point: a faceted gem standing in a gold collar
    cx = (x1 + x2) / 2.0
    tip = [(cx, y1 - 17), (cx + 6.5, y1 - 8), (cx + 4.5, y1 - 3), (cx - 4.5, y1 - 3), (cx - 6.5, y1 - 8)]
    crystal_polygon(cv, tip)
    collar = A.box(x1 - 4, y1 - 4, x2 + 4, y1, 1)
    A.draw(cv, collar, (x1 - 4, y1 - 4, x2 + 4, y1), G[2], outline=S[0], bevel=1.5, hi=0.55, lo=0.45)
    # the foot
    foot = A.box(x1 - 5, y2 + 1, x2 + 5, y2 + 7, 1)
    A.draw(cv, foot, (x1 - 5, y2 + 1, x2 + 5, y2 + 7), G[2], outline=S[0], bevel=1.5, hi=0.55, lo=0.45)
    for x in range(x1 - 3, x2 + 3):
        if (x - x1) % 3 == 1:
            cv.set(x, y2 + 4, G[3])
    # the pool lamp's socket under it
    px, py = ALTAR.POOL
    sock = A.box(px - 1, py - 1, px + 17, py + 17, 3)
    A.draw(cv, sock, (px - 1, py - 1, px + 17, py + 17), S[1], outline=S[0], bevel=1.5, hi=0.3, lo=0.4, sunk=True)


def crystal_polygon(cv, pts):
    """A mana crystal: facets from its top point, lit on the left."""
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    top = pts[0]

    def inside(px, py):
        n = len(pts)
        sign = None
        for i in range(n):
            ax, ay = pts[i]
            bx, by = pts[(i + 1) % n]
            cr = (bx - ax) * (py - ay) - (by - ay) * (px - ax)
            if abs(cr) < 1e-9:
                continue
            if sign is None:
                sign = cr > 0
            elif (cr > 0) != sign:
                return False
        return True

    for y in range(int(min(ys)) - 1, int(max(ys)) + 2):
        for x in range(int(min(xs)) - 1, int(max(xs)) + 2):
            px, py = x + 0.5, y + 0.5
            if not inside(px, py):
                continue
            edge = not all(inside(px + dx, py + dy) for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                c = MANA[5]
            else:
                facet = px - top[0]
                c = MANA[0] if facet < -2.5 else (MANA[1] if facet < 0 else (MANA[2] if facet < 2.5 else MANA[3]))
                if py > top[1] + 9:
                    c = A.dark(c, 0.15)
            cv.set(x, y, c)


# ---------------------------------------------------------------- the reliquary

def reliquary(cv):
    """An arched niche of gold and slate round the outputs, dark velvet inside it with a faint pattern, a rune
    seal in its head, a step under it."""
    x1, y1, x2, y2 = ALTAR.NICHE
    outer = A.arch(x1, y1, x2, y2)
    A.shadow(cv, outer, (x1, y1, x2, y2), dx=2, dy=3, alpha=150, soft=2.0)
    inner = A.arch(x1 + 5, y1 + 5, x2 - 5, y2 - 4)
    for x, y, depth in A.bounds(outer, x1, y1, x2, y2):
        px, py = x + 0.5, y + 0.5
        k = A.facing(outer, px, py)
        di = inner(px, py)
        if depth <= 1.0:
            c = S[0]
        elif depth <= 3.0:
            c = G[0] if k > 0.6 else (G[1] if k > 0.15 else (G[3] if k < -0.3 else G[2]))
        elif di > 1.0:
            c = A.tint(S[4], 0.18 * k)
            c = A.tint(c, (A.noise(x, y, 2.0, 81) - 0.5) * 0.15)
        elif di > 0.0:
            kk = -A.facing(inner, px, py)
            c = G[1] if kk > 0.3 else (G[3] if kk < -0.3 else G[2])
        else:
            t = (py - y1) / float(y2 - y1)
            c = A.ramp([(0.0, VELVET[1]), (0.6, VELVET[2]), (1.0, VELVET[3])], t)
            u, v = (x - x1) % 8, (y - y1) % 8
            if abs(u - 4) + abs(v - 4) == 3:
                c = A.light(c, 0.07)                               # the velvet's damask
            if di > -2.0:
                c = A.dark(c, 0.35)
        cv.set(x, y, c)
    # the seal in the arch's head: a gold ring with the mana rune
    sx, sy = (x1 + x2) / 2.0, y1 + 17.0
    seal = A.circle(sx, sy, 7.5)
    A.draw(cv, seal, (sx - 9, sy - 9, sx + 9, sy + 9), lambda x, y, d: A.lerp(VELVET[0], VELVET[2], (y - sy + 7) / 14.0),
           outline=S[0], bevel=0)
    gold_ring_at(cv, sx, sy, 6.5, 0.9)
    A.glyph(cv, GLYPHS["mana"], int(round(sx - 4.5)), int(round(sy - 4.5)), G[2])
    for (x, y) in ALTAR.OUTPUTS:
        T.slot(cv, x, y, THEME, rim=(G[3], G[2], G[1]), inside=(hexc("221440"), hexc("110A22")))
    # the step under it
    step = A.box(x1 + 2, y2 - 1, x2 - 2, y2 + 4, 1)
    A.draw(cv, step, (x1 + 2, y2 - 1, x2 - 2, y2 + 4), S[4], outline=S[0], bevel=1.2, hi=0.4, lo=0.4)


def gold_ring_at(cv, cx, cy, r, w):
    f = A.shell(A.circle(cx, cy, r), w)
    outer = A.circle(cx, cy, r)
    for x, y, d in A.bounds(f, cx - r - 2, cy - r - 2, cx + r + 2, cy + r + 2):
        k = A.facing(outer, x + 0.5, y + 0.5)
        cv.set(x, y, G[1] if k > 0.3 else (G[3] if k < -0.3 else G[2]))


# ---------------------------------------------------------------- the panel, the sheet, the gloss

def panel():
    cv = T.new_panel()
    T.inventory(cv, THEME)
    T.panel(cv, 0, 0, W, MACHINE_H, THEME, background)
    # light from the circle, the column and the reliquary spilling over the flagstones
    floor = A.circle(CX, CY, ALTAR.OUTER_R + 1.5)
    A.glow(cv, floor, (CX - 70, CY - 70, CX + 70, CY + 70), hexc("4458B8"), reach=12.0, alpha=60)
    A.glow(cv, floor, (CX - 70, CY - 70, CX + 70, CY + 70), hexc("05060C"), reach=3.0, alpha=110)
    x1, y1, x2, y2 = ALTAR.GAUGE
    A.glow(cv, A.box(x1 - 2, y1 - 18, x2 + 2, y2 + 7, 3), (x1 - 2, y1 - 18, x2 + 2, y2 + 7), hexc("2A9FE2"), reach=9.0, alpha=55)
    nx1, ny1, nx2, ny2 = ALTAR.NICHE
    A.glow(cv, A.arch(nx1, ny1, nx2, ny2), ALTAR.NICHE, hexc("7A4AD0"), reach=9.0, alpha=50)
    outer_rim(cv)
    spokes(cv)
    gold_ring(cv, ALTAR.INNER_R, 1.0)
    sockets(cv)
    octagon_altar(cv)
    gem_socket(cv, *ALTAR.GEM)
    column(cv)
    reliquary(cv)
    for (x, y) in ALTAR.LIGHTS:
        T.corner_gem(cv, x, y, THEME)
    return cv


def gem_socket(cv, gx, gy):
    """A gold setting round the pixel (gx, gy) with a dark hole: the screen sets the 7x7 status gem in it."""
    f = A.circle(gx + 0.5, gy + 0.5, 4.8)
    for x, y, d in A.bounds(f, gx - 6, gy - 6, gx + 7, gy + 7):
        k = A.facing(f, x + 0.5, y + 0.5)
        if d <= 1.0:
            c = S[0]
        elif d <= 2.0:
            c = G[1] if k > 0.2 else (G[3] if k < -0.3 else G[2])
        else:
            c = hexc("0A0D1A")
        cv.set(x, y, c)


SHEET_REGIONS = T.SHARED_REGIONS + ALTAR.SHEET


def glyph_sheet(cv):
    """The runes lit: white strokes, a faint one-pixel halo (the screen tints them each its colour)."""
    gx, gy = ALTAR.GLYPH_UV
    s = ALTAR.GLYPH_SIZE
    for k, name in enumerate(ALTAR.RUNES):
        lit_glyph(cv, GLYPHS[name], gx + k * s, gy)
    lit_glyph(cv, GLYPHS["mana"], *ALTAR.MANA_GLYPH_UV)


def lit_glyph(cv, rows, x0, y0):
    on = {(i, j) for j, row in enumerate(rows) for i, ch in enumerate(row) if ch == "#"}
    for (i, j) in on:
        n = sum(1 for (di, dj) in ((1, 0), (-1, 0), (0, 1), (0, -1)) if (i + di, j + dj) in on)
        cv.set(x0 + i, y0 + j, (255, 255, 255), 255 if n >= 2 else 200)


def socket_halo(cv):
    """A soft glow round a socket's frame (26x26 round an 18x18 hole): the screen tints it, adding light."""
    x0, y0 = ALTAR.HALO_SOCKET_UV
    s = ALTAR.HALO_SOCKET_SIZE
    f = A.box(4, 4, s - 4, s - 4, 3)
    for y in range(s):
        for x in range(s):
            d = f(x + 0.5, y + 0.5)
            if d < -1.5:
                continue
            a = (1.0 - min(1.0, max(0.0, d) / 4.0)) ** 1.8
            if d < 0:
                a *= max(0.0, 1.0 + d / 1.5)
            if a > 0.02:
                cv.set(x0 + x, y0 + y, (255, 255, 255), int(a * 220))


def column_fill(cv):
    """The column's mana: brightest down the middle of the glass, a lighter vein spiralling up it."""
    x0, y0 = ALTAR.FILL_UV
    w, h = ALTAR.GAUGE[2] - ALTAR.GAUGE[0], ALTAR.GAUGE[3] - ALTAR.GAUGE[1]
    for y in range(h):
        for x in range(w):
            t = abs(x + 0.5 - w / 2.0) / (w / 2.0)
            c = A.ramp([(0.0, MANA[1]), (0.45, MANA[2]), (0.8, MANA[3]), (1.0, MANA[4])], t)
            c = A.lerp(c, MANA[4], 0.25 * y / h)
            s = math.sin(y * 0.35 + x * 0.9)
            if s > 0.92:
                c = A.light(c, 0.35)
            if x == 1:
                c = A.light(c, 0.3)
            cv.set(x0 + x, y0 + y, c)


def pool_lamp(cv, lit, x0, y0):
    """The lamp under the column: a little pool, its mana lit when a pool is beside the machine."""
    rim = A.ellipse(x0 + 8, y0 + 9.5, 7.2, 4.8)
    for x, y, d in A.bounds(rim, x0, y0, x0 + 16, y0 + 16):
        k = A.facing(rim, x + 0.5, y + 0.5)
        if d <= 1.0:
            c = S[0]
        elif d <= 2.6:
            c = LIVINGROCK[0] if k > 0.3 else (LIVINGROCK[3] if k < -0.3 else LIVINGROCK[1])
        else:
            c = (MANA[1] if y < y0 + 9 else MANA[3]) if lit else (hexc("2A3550") if y < y0 + 9 else hexc("1C2440"))
        cv.set(x, y, c)
    if lit:
        cv.set(x0 + 6, y0 + 8, (255, 255, 255))
        cv.set(x0 + 7, y0 + 8, MANA[0])


def sheet():
    cv = T.new_sheet()
    T.shared_pieces(cv, THEME)
    glyph_sheet(cv)
    socket_halo(cv)
    column_fill(cv)
    pool_lamp(cv, False, *T.POOL_UV)
    pool_lamp(cv, True, T.POOL_UV[0] + T.POOL_SIZE, T.POOL_UV[1])
    return cv


def gloss(panel_cv):
    """Where a shine sweeps over the panel: its gold, brighter where the gold is lit."""
    out = T.new_panel()
    for y in range(panel_cv.h):
        for x in range(panel_cv.w):
            p = panel_cv.px[x, y]
            if p[3] == 0:
                continue
            r, g, b = p[:3]
            goldish = r > 150 and g > 90 and b < 140 and r - b > 90
            if goldish:
                a = int(120 + 135 * A.lum((r, g, b)))
                out.px[x, y] = (255, 255, 255, min(255, a))
    return out
