"""The Pure Daisy's GUI: the dawn garden (textures/gui/pure_daisy.png, pure_daisy_widgets.png, pure_daisy_gloss.png).

A garden at dawn in a frame of pale birch: the sky warming over a line of far trees along the top, the sun
rising in the right-hand corner, a lawn with little flowers in it; in the middle a square bed of nine tiles, the
middle one soil where the Pure Daisy grows (the screen draws it), the eight round it mossy stones the blocks it
purifies are set in (as round the daisy in the world); a big dewdrop of glass on the left (the screen fills it
with mana), a woven basket for what the daisy has made on the right, stepping stones between.

The screen (machine/daisy/client/PureDaisyScreen.java) draws the moving parts on layouts.DAISY's numbers.
"""
import math

import art as A
import theme as T
from layouts import DAISY, W, MACHINE_H, drop_half
from pix import hexc

BIRCH = [hexc(h) for h in ("FBF6E6", "EDE3C8", "D4C8A6", "A8996F", "6E6244", "3A3424")]
GRASS = [hexc(h) for h in ("C8F59A", "9BE36A", "6FC24A", "4E9E38", "37782C", "245420")]
SOIL = [hexc(h) for h in ("6A4A2E", "4E3420", "382416", "22160C")]
STONE = [hexc(h) for h in ("E8E8E2", "C9CAC2", "A6A79E", "7E8078", "56584F")]
FLOWERS = [hexc(h) for h in ("FFFFFF", "FFE066", "FFB3DE", "B9A3FF", "8FD8FF")]
G = [hexc(h) for h in ("FFF9D2", "FFE47D", "F4BA2E", "BA7C16", "6E4308")]
MANA = [hexc(h) for h in ("F2FFFF", "A6F6FF", "55D9F7", "2A9FE2", "1B64B8", "0B2F5C")]
WICKER = [hexc(h) for h in ("F2D59A", "D9B26E", "B88A48", "8C6230", "5A3C1A")]

THEME = T.Theme(
    outline=BIRCH[5], metal=(G[0], G[1], G[2], G[3], G[4]), groove=hexc("1E3A16"),
    stone=(BIRCH[0], BIRCH[1], BIRCH[3]), stone_noise=0.1, floor_shadow=hexc("243318"),
    slot_rim=(BIRCH[0], BIRCH[2], BIRCH[4]), slot_inside=(hexc("2E3A22"), hexc("161C10")),
    inv_bg=lambda x, y: A.tint(A.lerp(BIRCH[1], BIRCH[2], (y - 150) / 98.0), (A.fbm(x, y, 7, 191) - 0.5) * 0.1),
    plaque_face=(BIRCH[0], BIRCH[2]), plaque_text_hint=hexc("2E5A1E"), button_face=(BIRCH[1], BIRCH[2], BIRCH[3]),
    crystal=(hexc("FFFFFF"), hexc("E6FFF2"), hexc("B6F59A"), hexc("6FC24A"), hexc("37782C")), salt=13)

CX, CY, CELL = DAISY.CX, DAISY.CY, DAISY.CELL
HORIZON = 30                                     # where the far trees meet the lawn


# ---------------------------------------------------------------- sky, trees, lawn

def sky(x, y):
    t = y / float(HORIZON)
    c = A.ramp([(0.0, hexc("9CC8F0")), (0.5, hexc("F6C6CE")), (1.0, hexc("FFE0B0"))], t)
    # the sun's warmth in the right-hand corner
    d = math.hypot(x - (W - 20), y - 4)
    return A.lerp(c, hexc("FFF4D0"), max(0.0, 1.0 - d / 70.0) * 0.6)


def treeline(x):
    """How high the far trees reach over the horizon at x."""
    return 5 + 4 * A.noise(x, 0, 6.0, 201) + 3 * A.noise(x, 0, 2.5, 203)


def lawn(x, y):
    """Grass, lit by the low sun from the right, little flowers in it, mown stripes."""
    t = (y - HORIZON) / float(MACHINE_H - HORIZON)
    c = A.ramp([(0.0, GRASS[2]), (0.5, GRASS[3]), (1.0, GRASS[4])], t)
    if (x // 12 + y // 40) % 2 == 0:
        c = A.light(c, 0.05)                                    # the mower's stripes
    n = A.fbm(x, y, 4.0, 207, 2)
    c = A.tint(c, (n - 0.5) * 0.22)
    if A.rnd2(x, y, 205) < 0.06:
        c = A.light(c, 0.12)                                    # blades catching the light
    if A.rnd2(x, y, 209) < 0.005:
        c = FLOWERS[int(A.rnd2(x, y, 211) * len(FLOWERS))]
    return c


def background(x, y):
    top = HORIZON - treeline(x)
    if y < top:
        return sky(x, y)
    if y < HORIZON:
        c = A.lerp(hexc("6E8F7A"), hexc("4E7A5A"), (y - top) / max(1.0, HORIZON - top))
        if A.rnd2(x, y, 213) < 0.2:
            c = A.light(c, 0.12)
        return A.lerp(c, hexc("F6C6CE"), 0.25)                 # far away: in the dawn's haze
    c = lawn(x, y)
    vx = (x - W / 2.0) / (W / 2.0)
    vy = (y - MACHINE_H / 2.0) / (MACHINE_H / 2.0)
    return A.dark(c, 0.25 * min(1.0, (vx * vx * 0.7 + max(0.0, vy) ** 2) * 0.7))


def sun(cv):
    """The sun just risen in the top right corner, behind the trees."""
    cx, cy, r = W - 26, 18, 9
    f = A.circle(cx, cy, r)
    for x, y, d in A.bounds(f, cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1):
        if y >= HORIZON - treeline(x):
            continue
        cv.set(x, y, A.lerp(hexc("FFFBE8"), hexc("FFD27A"), min(1.0, (r - d) / r) * 0.2 + (y - cy + r) / (2.0 * r) * 0.5))
    A.glow(cv, f, (cx - r, cy - r, cx + r, cy + r), hexc("FFE9B0"), reach=10.0, alpha=70)


# ---------------------------------------------------------------- the bed

def bed(cv):
    """Nine tiles: soil in the middle, mossy stones round it, a border of birch logs."""
    x1, y1 = CX - 3 * CELL // 2, CY - 3 * CELL // 2
    x2, y2 = x1 + 3 * CELL, y1 + 3 * CELL
    border = A.box(x1 - 4, y1 - 4, x2 + 4, y2 + 4, 4)
    A.shadow(cv, border, (x1 - 4, y1 - 4, x2 + 4, y2 + 4), dx=2, dy=3, alpha=110, soft=2.0)
    for x, y, depth in A.bounds(border, x1 - 4, y1 - 4, x2 + 4, y2 + 4):
        k = A.facing(border, x + 0.5, y + 0.5)
        if depth <= 1.0:
            c = BIRCH[5]
        elif depth <= 4.0:
            c = BIRCH[0] if k > 0.3 else (BIRCH[3] if k < -0.3 else BIRCH[1])
            if A.rnd2(x, y, 215) < 0.12:
                c = BIRCH[5]                                    # the birch bark's dark marks
        else:
            c = soil(x, y)
        cv.set(x, y, c)
    for j in range(3):
        for i in range(3):
            tx, ty = x1 + i * CELL, y1 + j * CELL
            if (i, j) == (1, 1):
                daisy_bed(cv, tx, ty)
            else:
                tile(cv, tx + 2, ty + 2, tx + CELL - 2, ty + CELL - 2)


def soil(x, y):
    n = A.fbm(x, y, 2.5, 217, 2)
    c = SOIL[1] if n > 0.55 else (SOIL[2] if n > 0.3 else SOIL[3])
    if A.rnd2(x, y, 219) < 0.05:
        c = SOIL[0]
    return c


def tile(cv, x1, y1, x2, y2):
    """A mossy stone the block sits on, a little raised."""
    f = A.box(x1, y1, x2, y2, 3)
    for x, y, depth in A.bounds(f, x1, y1, x2, y2):
        k = A.facing(f, x + 0.5, y + 0.5)
        if depth <= 1.0:
            c = STONE[4]
        else:
            n = A.fbm(x, y, 3.0, 221, 2)
            c = A.lerp(STONE[1], STONE[2], n)
            if depth <= 2.5:
                c = STONE[0] if k > 0.3 else (STONE[3] if k < -0.3 else c)
            moss = A.fbm(x + 30, y, 4.0, 223, 2)
            if moss > 0.62 and depth > 1.5:
                c = A.lerp(c, GRASS[3], 0.7)
        cv.set(x, y, c)


def daisy_bed(cv, x1, y1):
    """The middle tile: dark soil and a ring of tiny white petals fallen round the daisy."""
    cx, cy = x1 + CELL / 2.0, y1 + CELL / 2.0
    for y in range(y1 + 2, y1 + CELL - 2):
        for x in range(x1 + 2, x1 + CELL - 2):
            c = soil(x, y)
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if 9.0 < d < 11.0 and A.rnd2(x, y, 225) < 0.35:
                c = FLOWERS[0]
            elif d < 9.0 and A.rnd2(x, y, 227) < 0.15:
                c = GRASS[3]
            cv.set(x, y, c)


# ---------------------------------------------------------------- the dewdrop, the basket, the stones

def dewdrop(cv):
    """A dewdrop of glass: clear, its rim catching the light, a white glint (the screen fills it with mana)."""
    cx, cy, r, tip = DAISY.DROP
    top, bottom = cy - tip, cy + r
    # its shadow on the grass
    sh = A.ellipse(cx + 3, bottom + 1, r - 2, 3.5)
    A.shadow(cv, sh, (cx - r, bottom - 4, cx + r + 6, bottom + 6), dx=0, dy=0, alpha=120, soft=2.5)
    for y in range(top - 1, bottom + 1):
        h = drop_half(y)
        if h <= 0.0:
            continue
        for x in range(int(math.floor(cx - h)) - 1, int(math.ceil(cx + h)) + 1):
            dx = x + 0.5 - cx
            if abs(dx) > h:
                continue
            edge = h - abs(dx)
            vertical = min(drop_half(y - 1), drop_half(y + 1))
            if edge <= 1.0 or vertical <= abs(dx):
                c = hexc("1C4A5A")
            elif edge <= 2.0:
                c = hexc("D8F6FF") if dx < 0 else hexc("6FB8D0")      # the glass's thick rim
            else:
                t = (y - top) / float(bottom - top)
                c = A.lerp(hexc("2A5A6A"), hexc("163844"), t)
                c = A.lerp(c, lawn(x, y), 0.25)                        # the grass seen through it
            cv.set(x, y, c)
    # a white glint high on its left
    for (gx, gy) in ((cx - 7, cy - 8), (cx - 8, cy - 6), (cx - 8, cy - 5), (cx - 6, cy - 10)):
        cv.set(gx, gy, (255, 255, 255))


def basket(cv):
    """A woven basket round the outputs, its handle arching over them."""
    x1, y1, x2, y2 = DAISY.BASKET
    # the handle
    hx, hy, hr = (x1 + x2) / 2.0, y1 + 4, (x2 - x1) / 2.0 - 3
    for x, y, d in A.bounds(A.shell(A.circle(hx, hy, hr), 1.6), x1, y1 - hr - 3, x2, y1 + 6):
        if y + 0.5 > hy:
            continue
        cv.set(x, y, WICKER[1] if (x + y) % 3 else WICKER[3])
    body = A.box(x1, y1, x2, y2, 5)
    A.shadow(cv, body, (x1, y1, x2, y2), dx=2, dy=3, alpha=120, soft=2.0)
    inner = A.box(x1 + 5, y1 + 5, x2 - 5, y2 - 5, 2)
    for x, y, depth in A.bounds(body, x1, y1, x2, y2):
        k = A.facing(body, x + 0.5, y + 0.5)
        if depth <= 1.0:
            c = WICKER[4]
        elif inner(x + 0.5, y + 0.5) > 0:
            # the weave: bands crossing over and under the stakes
            band = (y - y1) // 3
            over = ((x - x1) // 4 + band) % 2 == 0
            c = WICKER[1] if over else WICKER[2]
            if (y - y1) % 3 == 0:
                c = WICKER[3]
            c = A.tint(c, 0.15 * k)
        else:
            c = hexc("3A2A14")
        cv.set(x, y, c)
    for (x, y) in DAISY.OUTPUTS:
        T.slot(cv, x, y, THEME, rim=(WICKER[0], WICKER[2], WICKER[4]), inside=(hexc("3A2A16"), hexc("20160A")))


def stepping_stones(cv):
    """A few round stones across the lawn from the bed to the basket."""
    for (x, y, r) in ((176, 92, 4.5), (186, 100, 3.8), (176, 108, 3.2)):
        f = A.ellipse(x, y, r * 1.3, r)
        A.draw(cv, f, (x - 7, y - 5, x + 7, y + 5), lambda px, py, d: A.lerp(STONE[1], STONE[2], A.noise(px, py, 2.0, 229)),
               outline=STONE[4], bevel=1.2, hi=0.4, lo=0.35)


def gem_socket(cv, gx, gy):
    f = A.circle(gx + 0.5, gy + 0.5, 4.8)
    for x, y, d in A.bounds(f, gx - 6, gy - 6, gx + 7, gy + 7):
        k = A.facing(f, x + 0.5, y + 0.5)
        cv.set(x, y, BIRCH[5] if d <= 1.0 else ((G[1] if k > 0.2 else (G[3] if k < -0.3 else G[2])) if d <= 2.0 else hexc("10180A")))


# ---------------------------------------------------------------- the panel, the sheet, the gloss

def panel():
    cv = T.new_panel()
    T.inventory(cv, THEME)
    T.panel(cv, 0, 0, W, MACHINE_H, THEME, background)
    sun(cv)
    stepping_stones(cv)
    bed(cv)
    for (x, y) in DAISY.INPUTS:
        T.slot(cv, x, y, THEME, rim=(STONE[0], STONE[2], STONE[4]), inside=(hexc("2A2A22"), hexc("141410")))
    basket(cv)
    dewdrop(cv)
    gem_socket(cv, *DAISY.GEM)
    px, py = DAISY.POOL
    sock = A.box(px - 1, py - 1, px + 17, py + 17, 3)
    A.draw(cv, sock, (px - 1, py - 1, px + 17, py + 17), GRASS[4], outline=BIRCH[5], bevel=1.5, hi=0.3, lo=0.4, sunk=True)
    for (x, y) in DAISY.LIGHTS:
        T.corner_gem(cv, x, y, THEME)
    return cv


SHEET_REGIONS = T.SHARED_REGIONS + DAISY.SHEET

BUTTERFLY = [["#.....#",
              "##...##",
              "###.###",
              ".##.##.",
              "##.#.##",
              "#..#..#",
              "...#..."],
             [".......",
              "...#...",
              "..###..",
              "..###..",
              "..###..",
              "...#...",
              "......."]]


def sheet():
    cv = T.new_sheet()
    T.shared_pieces(cv, THEME)
    bx, by = DAISY.BUTTERFLY_UV
    for k, rows in enumerate(BUTTERFLY):
        A.glyph(cv, rows, bx + k * DAISY.BUTTERFLY_SIZE, by, (255, 255, 255))
    px, py = DAISY.PETAL_UV
    for (dx, dy, a) in ((1, 0, 200), (0, 1, 200), (1, 1, 255), (2, 1, 200), (1, 2, 160)):
        cv.set(px + dx, py + dy, (255, 255, 255), a)
    pool_lamp(cv, False, *T.POOL_UV)
    pool_lamp(cv, True, T.POOL_UV[0] + T.POOL_SIZE, T.POOL_UV[1])
    return cv


def pool_lamp(cv, lit, x0, y0):
    """The pool lamp: a little stone birdbath, mana in it when a pool is beside the machine."""
    rim = A.ellipse(x0 + 8, y0 + 9.5, 7.2, 4.8)
    for x, y, d in A.bounds(rim, x0, y0, x0 + 16, y0 + 16):
        k = A.facing(rim, x + 0.5, y + 0.5)
        if d <= 1.0:
            c = STONE[4]
        elif d <= 2.6:
            c = STONE[0] if k > 0.3 else (STONE[3] if k < -0.3 else STONE[1])
        else:
            c = (MANA[1] if y < y0 + 9 else MANA[3]) if lit else (hexc("3A4A3A") if y < y0 + 9 else hexc("263426"))
        cv.set(x, y, c)
    if lit:
        cv.set(x0 + 6, y0 + 8, (255, 255, 255))
        cv.set(x0 + 7, y0 + 8, MANA[0])


def gloss(panel_cv):
    """Where a shine sweeps over the panel: its gold, and the dewdrop's glass."""
    out = T.new_panel()
    cx, cy, r, tip = DAISY.DROP
    for y in range(panel_cv.h):
        for x in range(panel_cv.w):
            p = panel_cv.px[x, y]
            if p[3] == 0:
                continue
            r_, g, b = p[:3]
            gold = r_ > 170 and g > 110 and b < 130 and r_ - b > 90
            mx, my = x - 12, y - 12
            glass = drop_half(my) > abs(mx + 0.5 - cx)
            if gold or glass:
                out.px[x, y] = (255, 255, 255, min(255, int((90 if gold else 50) + 150 * A.lum((r_, g, b)))))
    return out
