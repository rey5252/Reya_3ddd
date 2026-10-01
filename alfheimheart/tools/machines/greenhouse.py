"""The Petal Farm's GUI: the greenhouse (textures/gui/petal_farm.png, petal_farm_widgets.png, petal_farm_gloss.png).

Inside a greenhouse in a frame of white-painted iron: the sky through its panes (the screen colours it with the
time of day and runs the sun across it as a cycle goes by), climbing roses on its glazing bars; the inputs six
clay pots on two shelves of pale wood, the bone meal in a burlap sack under them; a watering can of tin on the
left (the screen fills it with mana), a shelf of glass jars for the petals on the right; a tiled floor.

The screen (machine/farm/client/PetalFarmScreen.java) draws the moving parts on layouts.FARM's numbers.
"""
import math

import art as A
import theme as T
from layouts import FARM, W, MACHINE_H
from pix import hexc

IRON = [hexc(h) for h in ("FFFFFF", "EEF2F2", "D2DADA", "A6B0B0", "6E7878", "343C3C")]
SKY = [hexc(h) for h in ("D8F0FF", "B4E0FF", "8CCBF5", "6AB4E8")]
GLASS_TINT = hexc("E8FFF4")
WOOD = [hexc(h) for h in ("F0D8A8", "D8B880", "B8925C", "8A6A40", "5A4224")]
CLAY = [hexc(h) for h in ("F4A878", "DA8458", "B86640", "8C4A2C", "5A2C18")]
LEAF = [hexc(h) for h in ("B8F08A", "7CCB52", "4E9A38", "2F6A2A", "1C4420")]
ROSE = [hexc(h) for h in ("FFD0E0", "FF8AB0", "E04878", "A02450")]
TIN = [hexc(h) for h in ("F4F8FA", "C8D2D8", "9AA6AE", "6A767E", "3A444A")]
TILE = [hexc(h) for h in ("E8D8C0", "D4BE9E", "B89C78", "8A7052")]
G = [hexc(h) for h in ("FFF9D2", "FFE47D", "F4BA2E", "BA7C16", "6E4308")]
MANA = [hexc(h) for h in ("F2FFFF", "A6F6FF", "55D9F7", "2A9FE2", "1B64B8", "0B2F5C")]

THEME = T.Theme(
    outline=IRON[5], metal=(G[0], G[1], G[2], G[3], G[4]), groove=hexc("1C3A2A"),
    stone=(IRON[0], IRON[1], IRON[3]), stone_noise=0.06, floor_shadow=hexc("2A3A3A"),
    slot_rim=(IRON[0], IRON[2], IRON[4]), slot_inside=(hexc("3A2A1E"), hexc("1E1610")),
    inv_bg=lambda x, y: floor(x, y),
    plaque_face=(IRON[0], IRON[2]), plaque_text_hint=hexc("2E6A3A"), button_face=(IRON[1], IRON[2], IRON[3]),
    crystal=(hexc("FFFFFF"), ROSE[0], ROSE[1], ROSE[2], ROSE[3]), salt=19)

CX = FARM.CX
FLOOR_Y = 118                                    # where the glazing meets the tiled floor


def floor(x, y):
    """Terracotta-and-cream tiles in a chequer, a little worn."""
    tx, ty = (x + 3) // 10, (y + 1) // 10
    c = TILE[0] if (tx + ty) % 2 else TILE[1]
    if (x + 3) % 10 == 0 or (y + 1) % 10 == 0:
        c = TILE[3]
    c = A.tint(c, (A.fbm(x, y, 5.0, 301, 2) - 0.5) * 0.12)
    return c


def glazing(x, y):
    """The greenhouse's glass: the sky through it, pale and bright, its panes between white glazing bars."""
    t = y / float(FLOOR_Y)
    c = A.ramp([(0.0, SKY[3]), (0.5, SKY[2]), (1.0, SKY[0])], t)
    c = A.lerp(c, GLASS_TINT, 0.18)
    # a far hedge and lawn outside, low down
    if y > FLOOR_Y - 26:
        hedge = FLOOR_Y - 26 + 6 * A.noise(x, 0, 7.0, 303)
        if y > hedge:
            c = A.lerp(c, A.lerp(LEAF[2], LEAF[3], A.noise(x, y, 3.0, 305)), 0.55)
    # reflections: soft diagonal streaks across the panes
    s = ((x + y * 0.6) % 46)
    if 3 < s < 7:
        c = A.light(c, 0.18)
    elif 9 < s < 10:
        c = A.light(c, 0.1)
    # the glazing bars
    bx, by = (x - 6) % 32, (y - 4) % 38
    if bx in (0, 1) or by in (0, 1):
        c = IRON[1] if (bx == 0 or by == 0) else IRON[3]
    return c


def background(x, y):
    if y >= FLOOR_Y:
        return floor(x, y)
    if y == FLOOR_Y - 1:
        return IRON[3]
    return glazing(x, y)


def roses(cv):
    """Climbing roses up the glazing bars at the sides: stems, leaves, flowers."""
    for x0 in (6, 70, 182, 246):
        for y in range(10, FLOOR_Y - 2):
            x = x0 + int(round(1.5 * math.sin(y * 0.25 + x0)))
            cv.set(x, y, LEAF[3])
            if y % 6 == 0:
                for (dx, dy, c) in ((1, 0, LEAF[1]), (2, 0, LEAF[2]), (1, -1, LEAF[0])):
                    cv.set(x + dx, y + dy, c)
            if y % 6 == 3:
                for (dx, dy, c) in ((-1, 0, LEAF[1]), (-2, 0, LEAF[2]), (-1, -1, LEAF[0])):
                    cv.set(x + dx, y + dy, c)
            if y % 17 == 5:
                for (dx, dy, c) in ((0, -1, ROSE[1]), (-1, 0, ROSE[1]), (1, 0, ROSE[2]), (0, 1, ROSE[2]), (0, 0, ROSE[0])):
                    cv.set(x + dx, y + dy, c)


def shelf(cv, x1, y, x2):
    """A shelf of pale wood on iron brackets."""
    board = A.box(x1, y, x2, y + 5, 1)
    A.shadow(cv, board, (x1, y, x2, y + 5), dx=1, dy=2, alpha=110, soft=1.5)
    A.draw(cv, board, (x1, y, x2, y + 5), lambda px, py, d: A.tint(WOOD[1], (A.noise(px * 0.3, py * 3, 2.0, 307) - 0.5) * 0.2),
           outline=WOOD[4], bevel=1.2, hi=0.45, lo=0.35)
    for bx in (x1 + 6, x2 - 8):
        for k in range(6):
            cv.set(bx + k // 2, y + 5 + k, IRON[4])
            cv.set(bx + 1 + k // 2, y + 5 + k, IRON[2])


def pot(cv, x, y):
    """A clay pot round the slot (x, y its item corner): a rolled rim, its body tapering under it."""
    cx = x + 8.0
    rim = A.box(x - 4, y - 3, x + 20, y + 2, 2)
    body_top, body_bottom = y + 2, y + 24
    for yy in range(body_top, body_bottom):
        t = (yy - body_top) / float(body_bottom - body_top)
        half = 11.0 - 3.0 * t
        for xx in range(int(cx - half) - 1, int(cx + half) + 2):
            dx = xx + 0.5 - cx
            if abs(dx) > half:
                continue
            u = (dx + half) / (2 * half)
            c = A.ramp([(0.0, CLAY[0]), (0.35, CLAY[1]), (0.8, CLAY[2]), (1.0, CLAY[3])], u)
            if abs(dx) > half - 1.0 or yy == body_bottom - 1:
                c = CLAY[4]
            cv.set(xx, yy, c)
    A.draw(cv, rim, (x - 4, y - 3, x + 20, y + 2), CLAY[1], outline=CLAY[4], bevel=1.2, hi=0.45, lo=0.35)
    T.slot(cv, x, y, THEME, rim=(CLAY[0], CLAY[2], CLAY[4]), inside=(hexc("4A3020"), hexc("26180E")))


def sack(cv, x, y):
    """A burlap sack of bone meal round the slot, tied at the top."""
    body = A.union(A.box(x - 5, y - 2, x + 21, y + 19, 6), A.circle(x + 8, y - 3, 4))
    area = (x - 6, y - 8, x + 22, y + 20)
    A.shadow(cv, body, area, dx=1, dy=2, alpha=110, soft=1.5)
    A.draw(cv, body, area, lambda px, py, d: A.lerp(hexc("C8B088"), hexc("A8905E"), 0.5 + 0.5 * math.sin(px * 1.3) * math.sin(py * 1.3)),
           outline=hexc("4A3A20"), bevel=1.5, hi=0.35, lo=0.35)
    for k in range(-3, 4):
        cv.set(x + 8 + k, y - 4, hexc("6A5230"))
    T.slot(cv, x, y, THEME, rim=(hexc("E0CCA4"), hexc("A8905E"), hexc("4A3A20")), inside=(hexc("4A3A26"), hexc("261C10")))


def can(cv):
    """A tin watering can: its body (the screen fills it with mana), a handle arching over it, its spout out to the
    right with a rose on it."""
    x1, y1, x2, y2, r = FARM.CAN
    body = A.box(x1, y1, x2, y2, r)
    A.shadow(cv, body, (x1, y1, x2, y2), dx=2, dy=3, alpha=120, soft=2.0)
    # the spout, behind the body
    A.thick_line(cv, x2 - 2, y2 - 12, x2 + 14, y1 - 6, 2.4, TIN[4])
    A.thick_line(cv, x2 - 2, y2 - 12, x2 + 14, y1 - 6, 1.4, TIN[2])
    rose = A.circle(x2 + 15, y1 - 7, 3.2)
    A.draw(cv, rose, (x2 + 10, y1 - 12, x2 + 20, y1 - 2), TIN[1], outline=TIN[4], bevel=1.0, hi=0.5, lo=0.4)
    # the handle
    for x, y, d in A.bounds(A.shell(A.ellipse((x1 + x2) / 2.0, y1, (x2 - x1) / 2.0 - 3, 12.0), 1.3), x1, y1 - 14, x2, y1 + 1):
        if y + 0.5 < y1:
            cv.set(x, y, TIN[2] if y < y1 - 6 else TIN[3])
    for x, y, depth in A.bounds(body, x1, y1, x2, y2):
        k = A.facing(body, x + 0.5, y + 0.5)
        if depth <= 1.0:
            c = TIN[4]
        elif depth <= 3.0:
            u = (x - x1) / float(x2 - x1)
            c = A.ramp([(0.0, TIN[0]), (0.4, TIN[1]), (1.0, TIN[3])], u)
        else:
            c = A.lerp(hexc("2A3A44"), hexc("161E24"), (y - y1) / float(y2 - y1))  # the inside, seen through (the mana fills it)
        cv.set(x, y, c)
    # rivets along its seams
    for yy in range(y1 + 4, y2 - 2, 6):
        cv.set(x1 + 2, yy, TIN[0])
        cv.set(x2 - 3, yy, TIN[3])


def jars(cv):
    """A shelf of glass jars with cork lids for the petals: one round each output."""
    x1, y1, x2, y2 = FARM.JARS
    back = A.box(x1, y1, x2, y2, 3)
    A.shadow(cv, back, (x1, y1, x2, y2), dx=2, dy=3, alpha=110, soft=2.0)
    A.draw(cv, back, (x1, y1, x2, y2), lambda px, py, d: A.tint(WOOD[2], (A.noise(px * 0.4, py * 2, 2.0, 309) - 0.5) * 0.2),
           outline=WOOD[4], bevel=1.5, hi=0.4, lo=0.4)
    for (x, y) in FARM.OUTPUTS:
        jar = A.box(x - 3, y - 2, x + 19, y + 19, 4)
        for px, py, d in A.bounds(jar, x - 3, y - 2, x + 19, y + 19):
            k = A.facing(jar, px + 0.5, py + 0.5)
            if d <= 1.0:
                cv.set(px, py, hexc("5A7A70"))
            elif d <= 2.0:
                cv.set(px, py, hexc("E8FFF8") if k > 0 else hexc("9CC4B8"))
        lid = A.box(x - 1, y - 5, x + 17, y - 1, 1)
        A.draw(cv, lid, (x - 1, y - 5, x + 17, y - 1), hexc("C89A64"), outline=hexc("5A3A1A"), bevel=1.0, hi=0.4, lo=0.35)
        T.slot(cv, x, y, THEME, rim=(hexc("E8FFF8"), hexc("B8DCD0"), hexc("6A8A80")), inside=(hexc("2E4440"), hexc("16221E")))
    for (sy) in (y1 + 26, y1 + 48, y1 + 70):
        for x in range(x1 + 2, x2 - 2):
            cv.set(x, sy, WOOD[3])


def gem_socket(cv, gx, gy):
    f = A.circle(gx + 0.5, gy + 0.5, 4.8)
    for x, y, d in A.bounds(f, gx - 6, gy - 6, gx + 7, gy + 7):
        k = A.facing(f, x + 0.5, y + 0.5)
        cv.set(x, y, IRON[5] if d <= 1.0 else ((G[1] if k > 0.2 else (G[3] if k < -0.3 else G[2])) if d <= 2.0 else hexc("1A140C")))


# ---------------------------------------------------------------- the panel, the sheet, the gloss

def panel():
    cv = T.new_panel()
    T.inventory(cv, THEME)
    T.panel(cv, 0, 0, W, MACHINE_H, THEME, background)
    roses(cv)
    for (x1, y, x2) in FARM.SHELVES:
        shelf(cv, x1, y, x2)
    for (x, y) in FARM.INPUTS:
        pot(cv, x, y)
    sack(cv, *FARM.FERTILIZER)
    can(cv)
    jars(cv)
    gem_socket(cv, *FARM.GEM)
    px, py = FARM.POOL
    sock = A.box(px - 1, py - 1, px + 17, py + 17, 3)
    A.draw(cv, sock, (px - 1, py - 1, px + 17, py + 17), TILE[2], outline=IRON[5], bevel=1.5, hi=0.3, lo=0.4, sunk=True)
    for (x, y) in FARM.LIGHTS:
        T.corner_gem(cv, x, y, THEME)
    return cv


SHEET_REGIONS = T.SHARED_REGIONS + FARM.SHEET


def sheet():
    cv = T.new_sheet()
    T.shared_pieces(cv, THEME)
    px, py = FARM.PETAL_UV
    for (dx, dy, a) in ((1, 0, 230), (2, 0, 255), (0, 1, 200), (1, 1, 255), (2, 1, 255), (3, 1, 200), (1, 2, 180), (2, 2, 220)):
        cv.set(px + dx, py + dy, (255, 255, 255), a)
    pool_lamp(cv, False, *T.POOL_UV)
    pool_lamp(cv, True, T.POOL_UV[0] + T.POOL_SIZE, T.POOL_UV[1])
    return cv


def pool_lamp(cv, lit, x0, y0):
    """The pool lamp: a little tin basin, mana in it when a pool is beside the machine."""
    rim = A.ellipse(x0 + 8, y0 + 9.5, 7.2, 4.8)
    for x, y, d in A.bounds(rim, x0, y0, x0 + 16, y0 + 16):
        k = A.facing(rim, x + 0.5, y + 0.5)
        if d <= 1.0:
            c = TIN[4]
        elif d <= 2.6:
            c = TIN[0] if k > 0.3 else (TIN[3] if k < -0.3 else TIN[1])
        else:
            c = (MANA[1] if y < y0 + 9 else MANA[3]) if lit else (hexc("3A4248") if y < y0 + 9 else hexc("262C30"))
        cv.set(x, y, c)
    if lit:
        cv.set(x0 + 6, y0 + 8, (255, 255, 255))
        cv.set(x0 + 7, y0 + 8, MANA[0])


def gloss(panel_cv):
    """Where a shine sweeps over the panel: its gold, the can's tin and the jars' glass."""
    out = T.new_panel()
    for y in range(panel_cv.h):
        for x in range(panel_cv.w):
            p = panel_cv.px[x, y]
            if p[3] == 0:
                continue
            r, g, b = p[:3]
            gold = r > 170 and g > 110 and b < 130 and r - b > 90
            pale = r > 225 and g > 235 and b > 230
            if gold or pale:
                out.px[x, y] = (255, 255, 255, min(255, int(60 + 150 * A.lum((r, g, b)))))
    return out
