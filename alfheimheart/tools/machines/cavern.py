"""The Orechid Mine's GUI: the gem cavern (textures/gui/orechid_mine.png, orechid_mine_widgets.png, orechid_mine_gloss.png).

A cavern in a frame of dark timber and iron: rough rock lit by glowing crystals, veins of ore in it; in the middle
a ring of sockets cut in the rock where the ores the block may become shine (the screen sets them there), round
a window of livingrock and gold where the block being turned sits; the orechid on a mossy stone under it; the
inputs in a minecart on rails at the bottom left, a lantern hanging on a chain on the left (the screen fills it
with mana light), a treasure chest for the ores on the right.

The screen (machine/orechid/client/OrechidMineScreen.java) draws the moving parts on layouts.MINE's numbers.
"""
import math

import art as A
import theme as T
from layouts import MINE, W, MACHINE_H, lantern_half
from pix import hexc

ROCK = [hexc(h) for h in ("8A8478", "6E685E", "565048", "403A34", "2A2622", "161412")]
TIMBER = [hexc(h) for h in ("A87A4A", "86602E", "684820", "4A3216", "2A1C0C")]
IRON = [hexc(h) for h in ("D8DCE0", "A8AEB4", "7A8088", "4E545A", "2A2E32")]
G = [hexc(h) for h in ("FFF9D2", "FFE47D", "F4BA2E", "BA7C16", "6E4308")]
LIVINGROCK = [hexc(h) for h in ("FBF8EE", "E2DCCB", "C9C2B1", "A89F8B", "7A715F")]
ORES = [hexc(h) for h in ("E8B37F", "D8D8D8", "FCEE4B", "5DECF5", "17DD62", "345EC3", "FF2A2A")]
CRYSTAL = [hexc(h) for h in ("F2E4FF", "C8A0FF", "9A6AE8", "6A40B0", "3A2070")]
MOSS = [hexc(h) for h in ("9BE36A", "6FC24A", "4E9E38", "2F6A2A")]
MANA = [hexc(h) for h in ("F2FFFF", "A6F6FF", "55D9F7", "2A9FE2", "1B64B8", "0B2F5C")]

THEME = T.Theme(
    outline=hexc("0E0C0A"), metal=(IRON[0], IRON[1], IRON[2], IRON[3], IRON[4]), groove=hexc("08070A"),
    stone=(TIMBER[1], TIMBER[2], TIMBER[3]), stone_noise=0.3, floor_shadow=hexc("080706"),
    slot_rim=(ROCK[0], ROCK[2], ROCK[4]), slot_inside=(hexc("1E1A16"), hexc("0E0C0A")),
    inv_bg=lambda x, y: A.dark(rock(x, y + 200), 0.15),
    plaque_face=(TIMBER[1], TIMBER[3]), plaque_text_hint=hexc("FFE9B0"), button_face=(TIMBER[1], TIMBER[2], TIMBER[3]),
    crystal=(CRYSTAL[0], CRYSTAL[1], CRYSTAL[2], CRYSTAL[3], CRYSTAL[4]), salt=23)

CX, CY = MINE.CX, MINE.CY


def rock(x, y):
    """Rough cave rock: lumps lit from above, cracks, flecks and veins of ore."""
    n = A.fbm(x, y, 9.0, 311, 3)
    lumps = A.fbm(x, y, 4.0, 313, 2)
    c = A.ramp([(0.0, ROCK[4]), (0.4, ROCK[3]), (0.75, ROCK[2]), (1.0, ROCK[1])], n * 0.7 + lumps * 0.3)
    up = A.fbm(x, y - 1, 4.0, 313, 2) - lumps                       # the lumps' tops catch the light
    if up < -0.04:
        c = A.light(c, 0.1)
    elif up > 0.05:
        c = A.dark(c, 0.15)
    if A.vein(x, y, 20.0, 317, 0.02) > 0.5:
        c = A.dark(c, 0.4)                                           # cracks
    v = A.vein(x + 100, y, 26.0, 319, 0.025) * max(0.0, A.noise(x, y, 40.0, 333) - 0.55) * 2.2
    if v > 0.25:
        c = A.lerp(c, ORES[int(A.noise(x, y, 30.0, 321) * len(ORES)) % len(ORES)], 0.4 * min(1.0, v))   # a vein of ore, here and there
    if A.rnd2(x, y, 323) < 0.004:
        c = ORES[int(A.rnd2(x, y, 325) * len(ORES))]
    return c


def background(x, y):
    c = rock(x, y)
    vx = (x - W / 2.0) / (W / 2.0)
    vy = (y - MACHINE_H / 2.0) / (MACHINE_H / 2.0)
    # the cavern is darker at its edges, a little lighter where the crystals are
    c = A.dark(c, 0.45 * min(1.0, (vx * vx * 0.8 + vy * vy) * 0.7))
    for (cx, cy) in MINE.CRYSTALS:
        d = math.hypot(x - cx, y - cy)
        if d < 26:
            c = A.lerp(c, CRYSTAL[2], 0.25 * (1 - d / 26.0) ** 2)
    return c


def crystal(cv, cx, cy, size):
    """A cluster of violet crystals growing out of the rock."""
    for k, (dx, h, w) in enumerate(((-3, size, 2.5), (1, size * 1.4, 3.0), (5, size * 0.9, 2.2))):
        bx, by = cx + dx, cy
        tip = (bx + (k - 1) * 1.5, by - h)
        pts = [tip, (bx + w, by - h * 0.55), (bx + w, by), (bx - w, by), (bx - w, by - h * 0.55)]
        poly_fill(cv, pts, lambda px, py, u: CRYSTAL[1] if u < 0.4 else (CRYSTAL[2] if u < 0.75 else CRYSTAL[3]), CRYSTAL[4])
        cv.set(int(tip[0]), int(tip[1]) + 1, CRYSTAL[0])


def poly_fill(cv, pts, colour, outline):
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]

    def inside(px, py):
        sign = None
        for i in range(len(pts)):
            ax, ay = pts[i]
            bx, by = pts[(i + 1) % len(pts)]
            cr = (bx - ax) * (py - ay) - (by - ay) * (px - ax)
            if abs(cr) < 1e-9:
                continue
            if sign is None:
                sign = cr > 0
            elif (cr > 0) != sign:
                return False
        return True

    x0, x1 = min(xs), max(xs)
    for y in range(int(min(ys)) - 1, int(max(ys)) + 2):
        for x in range(int(x0) - 1, int(x1) + 2):
            px, py = x + 0.5, y + 0.5
            if not inside(px, py):
                continue
            edge = not all(inside(px + dx, py + dy) for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            cv.set(x, y, outline if edge else colour(px, py, (px - x0) / max(1.0, x1 - x0)))


# ---------------------------------------------------------------- the ring and the window

def socket(cv, sx, sy):
    """A socket cut in the rock for an ore: a dark hollow in an iron ring."""
    f = A.circle(sx + 0.5, sy + 0.5, 7.0)
    A.shadow(cv, f, (sx - 8, sy - 8, sx + 9, sy + 9), dx=1, dy=1, alpha=120, soft=1.2)
    hole = A.circle(sx + 0.5, sy + 0.5, 5.0)
    for x, y, d in A.bounds(f, sx - 8, sy - 8, sx + 9, sy + 9):
        k = A.facing(f, x + 0.5, y + 0.5)
        if d <= 1.0:
            c = THEME.outline
        elif hole(x + 0.5, y + 0.5) > 0:
            c = IRON[1] if k > 0.3 else (IRON[3] if k < -0.3 else IRON[2])
        else:
            c = A.lerp(hexc("1A1612"), hexc("0A0806"), math.hypot(x + 0.5 - sx - 0.5, y + 0.5 - sy - 0.5) / 5.0)
        cv.set(x, y, c)


def window(cv):
    """The window the block being turned sits in: livingrock in a gold rim, rough rock inside."""
    r, rr = 17, 13
    f = A.circle(CX, CY, r)
    A.shadow(cv, f, (CX - r, CY - r, CX + r, CY + r), dx=2, dy=3, alpha=150, soft=2.0)
    for x, y, depth in A.bounds(f, CX - r - 1, CY - r - 1, CX + r + 1, CY + r + 1):
        px, py = x + 0.5, y + 0.5
        k = A.facing(f, px, py)
        d = math.hypot(px - CX, py - CY)
        if depth <= 1.0:
            c = THEME.outline
        elif depth <= 3.0:
            c = G[1] if k > 0.3 else (G[3] if k < -0.3 else G[2])
        elif d > rr + 0.5:
            c = LIVINGROCK[0] if k > 0.3 else (LIVINGROCK[2] if k < -0.3 else LIVINGROCK[1])
        elif d > rr - 0.5:
            c = THEME.outline
        else:
            n = A.noise(x, y, 2.5, 327)
            c = ROCK[1] if n > 0.6 else (ROCK[2] if n > 0.3 else ROCK[3])
            c = A.dark(c, max(0.0, (d - rr + 4) / 4.0) * 0.5)
        cv.set(x, y, c)


def orechid_stone(cv):
    """The mossy stone the orechid grows on, under the window."""
    ox, oy = MINE.ORECHID
    f = A.ellipse(ox, oy + 6, 13, 6)
    A.shadow(cv, f, (ox - 14, oy, ox + 14, oy + 13), dx=1, dy=2, alpha=130, soft=1.5)
    for x, y, depth in A.bounds(f, ox - 14, oy, ox + 14, oy + 13):
        k = A.facing(f, x + 0.5, y + 0.5)
        if depth <= 1.0:
            c = THEME.outline
        else:
            c = ROCK[1] if k > 0.2 else (ROCK[3] if k < -0.3 else ROCK[2])
            if y < oy + 5 and A.rnd2(x, y, 329) < 0.6:
                c = MOSS[1] if A.rnd2(x, y, 331) < 0.5 else MOSS[2]
        cv.set(x, y, c)


# ---------------------------------------------------------------- the cart, the lantern, the chest

def rails(cv):
    y = MINE.CART[3] + 2
    for x in range(8, 92):
        cv.set(x, y, IRON[1])
        cv.set(x, y + 1, IRON[3])
        cv.set(x, y + 5, IRON[2])
        if x % 6 == 0:
            for yy in range(y - 1, y + 7):
                cv.set(x, yy, TIMBER[2] if yy < y + 6 else TIMBER[4])
                cv.set(x + 1, yy, TIMBER[3])


def cart(cv):
    """A minecart of iron and timber holding the inputs."""
    x1, y1, x2, y2 = MINE.CART
    body = A.box(x1, y1, x2, y2, 3)
    A.shadow(cv, body, (x1, y1, x2, y2), dx=2, dy=2, alpha=140, soft=1.5)
    for x, y, depth in A.bounds(body, x1, y1, x2, y2):
        k = A.facing(body, x + 0.5, y + 0.5)
        if depth <= 1.0:
            c = THEME.outline
        elif depth <= 3.0:
            c = IRON[1] if k > 0.3 else (IRON[3] if k < -0.3 else IRON[2])
        else:
            c = TIMBER[2] if ((x - x1) // 5) % 2 else TIMBER[1]
            if (y - y1) % 9 == 0:
                c = IRON[3]
        cv.set(x, y, c)
    for (wx) in (x1 + 9, x2 - 10):
        wheel = A.circle(wx, y2, 4.0)
        A.draw(cv, wheel, (wx - 5, y2 - 5, wx + 5, y2 + 5), IRON[2], outline=THEME.outline, bevel=1.2, hi=0.5, lo=0.4)
        cv.set(int(wx), int(y2), IRON[0])
    for (x, y) in MINE.INPUTS:
        T.slot(cv, x, y, THEME)


def lantern(cv):
    """A lantern hanging on a chain: an iron cap and base round a glass body (the screen fills it with mana light)."""
    cx, cy, hw, hh = MINE.LANTERN
    top, bottom = cy - hh, cy + hh
    for y in range(8, top - 4):
        cv.set(cx, y, IRON[2] if y % 3 else IRON[4])
        cv.set(cx + 1 if y % 3 == 1 else cx - 1, y, IRON[3])
    cap = A.union(A.box(cx - hw + 1, top - 5, cx + hw, top + 1, 2), A.circle(cx + 0.5, top - 6, 2.5))
    A.draw(cv, cap, (cx - hw, top - 10, cx + hw + 1, top + 2), IRON[2], outline=THEME.outline, bevel=1.5, hi=0.5, lo=0.4)
    base = A.box(cx - hw + 1, bottom - 1, cx + hw, bottom + 4, 2)
    A.draw(cv, base, (cx - hw, bottom - 2, cx + hw + 1, bottom + 5), IRON[2], outline=THEME.outline, bevel=1.5, hi=0.5, lo=0.4)
    for y in range(top, bottom):
        h = lantern_half(y)
        for x in range(int(math.floor(cx - h)), int(math.ceil(cx + h)) + 1):
            dx = x + 0.5 - cx
            if abs(dx) > h:
                continue
            edge = h - abs(dx)
            if edge <= 1.0:
                c = THEME.outline
            elif edge <= 2.0:
                c = hexc("E8F0F4") if dx < 0 else hexc("8A9AA4")
            else:
                c = A.lerp(hexc("2A2E36"), hexc("16181E"), (y - top) / float(bottom - top))
            if abs(dx) < 0.6 and edge > 2.0:
                c = IRON[3]                                        # the frame's bar down its middle
            cv.set(x, y, c)


def chest(cv):
    """A treasure chest of timber bound in gold for the ores: its lid open behind, the outputs inside."""
    x1, y1, x2, y2 = MINE.CHEST
    lid = A.box(x1 + 2, y1, x2 - 2, y1 + 16, 6)
    A.draw(cv, lid, (x1 + 2, y1, x2 - 2, y1 + 16), lambda x, y, d: TIMBER[1] if ((x - x1) // 6) % 2 else TIMBER[2], outline=THEME.outline,
           bevel=1.5, hi=0.35, lo=0.4)
    for x in range(x1 + 4, x2 - 4):
        cv.set(x, y1 + 12, G[2])
        cv.set(x, y1 + 13, G[3])
    body = A.box(x1, y1 + 14, x2, y2, 3)
    A.shadow(cv, body, (x1, y1 + 14, x2, y2), dx=2, dy=3, alpha=150, soft=2.0)
    for x, y, depth in A.bounds(body, x1, y1 + 14, x2, y2):
        k = A.facing(body, x + 0.5, y + 0.5)
        if depth <= 1.0:
            c = THEME.outline
        elif depth <= 3.0:
            c = G[1] if k > 0.3 else (G[3] if k < -0.3 else G[2])
        else:
            c = A.lerp(hexc("2A1C10"), hexc("140C06"), (y - y1) / float(y2 - y1))
        cv.set(x, y, c)
    for (x, y) in MINE.OUTPUTS:
        T.slot(cv, x, y, THEME, rim=(G[1], G[2], G[4]), inside=(hexc("2A1C10"), hexc("120A04")))
    # the lock
    lx, ly = (x1 + x2) // 2, y2 - 3
    lock = A.box(lx - 3, ly - 4, lx + 4, ly + 2, 1)
    A.draw(cv, lock, (lx - 3, ly - 4, lx + 4, ly + 2), G[2], outline=THEME.outline, bevel=1.0, hi=0.5, lo=0.4)


def gem_socket(cv, gx, gy):
    f = A.circle(gx + 0.5, gy + 0.5, 4.8)
    for x, y, d in A.bounds(f, gx - 6, gy - 6, gx + 7, gy + 7):
        k = A.facing(f, x + 0.5, y + 0.5)
        cv.set(x, y, THEME.outline if d <= 1.0 else ((G[1] if k > 0.2 else (G[3] if k < -0.3 else G[2])) if d <= 2.0 else hexc("0A0806")))


# ---------------------------------------------------------------- the panel, the sheet, the gloss

def panel():
    cv = T.new_panel()
    T.inventory(cv, THEME)
    T.panel(cv, 0, 0, W, MACHINE_H, THEME, background)
    for i, (x, y) in enumerate(MINE.CRYSTALS):
        crystal(cv, x, y, 7 + (i % 3) * 2)
    A.ring_line(cv, CX, CY, MINE.ORE_R, IRON[3], w=0.6, alpha=160)
    for (ox, oy) in MINE.ORES:
        socket(cv, ox, oy)
    window(cv)
    orechid_stone(cv)
    rails(cv)
    cart(cv)
    lantern(cv)
    chest(cv)
    gem_socket(cv, *MINE.GEM)
    px, py = MINE.POOL
    sock = A.box(px - 1, py - 1, px + 17, py + 17, 3)
    A.draw(cv, sock, (px - 1, py - 1, px + 17, py + 17), ROCK[3], outline=THEME.outline, bevel=1.5, hi=0.3, lo=0.4, sunk=True)
    for (x, y) in MINE.LIGHTS:
        T.corner_gem(cv, x, y, THEME)
    return cv


SHEET_REGIONS = T.SHARED_REGIONS + MINE.SHEET


def sheet():
    cv = T.new_sheet()
    T.shared_pieces(cv, THEME)
    pool_lamp(cv, False, *T.POOL_UV)
    pool_lamp(cv, True, T.POOL_UV[0] + T.POOL_SIZE, T.POOL_UV[1])
    return cv


def pool_lamp(cv, lit, x0, y0):
    """The pool lamp: a little pool in the rock, mana in it when a pool is beside the machine."""
    rim = A.ellipse(x0 + 8, y0 + 9.5, 7.2, 4.8)
    for x, y, d in A.bounds(rim, x0, y0, x0 + 16, y0 + 16):
        k = A.facing(rim, x + 0.5, y + 0.5)
        if d <= 1.0:
            c = THEME.outline
        elif d <= 2.6:
            c = ROCK[0] if k > 0.3 else (ROCK[3] if k < -0.3 else ROCK[1])
        else:
            c = (MANA[1] if y < y0 + 9 else MANA[3]) if lit else (hexc("22242A") if y < y0 + 9 else hexc("16181C"))
        cv.set(x, y, c)
    if lit:
        cv.set(x0 + 6, y0 + 8, (255, 255, 255))
        cv.set(x0 + 7, y0 + 8, MANA[0])


def gloss(panel_cv):
    """Where a shine sweeps over the panel: its gold, its iron and the crystals."""
    out = T.new_panel()
    for y in range(panel_cv.h):
        for x in range(panel_cv.w):
            p = panel_cv.px[x, y]
            if p[3] == 0:
                continue
            r, g, b = p[:3]
            gold = r > 170 and g > 110 and b < 130 and r - b > 90
            crystal_ = b > 180 and r > 130 and g < 190 and b - g > 40
            iron = r > 150 and abs(r - g) < 12 and abs(g - b) < 14
            if gold or crystal_ or iron:
                out.px[x, y] = (255, 255, 255, min(255, int(60 + 160 * A.lum((r, g, b)))))
    return out
