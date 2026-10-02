"""The Petal Apothecary's GUI: the flower alchemist's table (textures/gui/petal_apothecary.png,
petal_apothecary_widgets.png, petal_apothecary_gloss.png).

A table of warm walnut in a frame of dark wood and brass: a sheet of parchment on it with a flower drawn in ink,
petals strewn about; the apothecary's stone bowl in the middle (its rim, the water's dark bed: the screen fills it
with water coloured by the petals), standing on a stem over the seeds' slot; its nine inputs a fan of petal-
shaped brass plates over it like a flower's petals round its heart; a round flask of glass on a brass stand on
the left (the screen fills it with mana); a shelf of walnut for the flowers made on the right.

The screen (machine/apothecary/client/PetalApothecaryScreen.java) draws the moving parts on layouts.APOTHECARY's
numbers.
"""
import math

import art as A
import theme as T
from layouts import APOTHECARY, W, MACHINE_H, flask_half
from pix import hexc

WALNUT = [hexc(h) for h in ("C08A5A", "9C6A40", "7E5230", "5E3A20", "3E2414", "24140A")]
BRASS = [hexc(h) for h in ("FFF2C4", "F2D27A", "D2A848", "9C7428", "5E4412")]
PARCH = [hexc(h) for h in ("FBF0D4", "F0DFB4", "DCC48E", "B89C64", "7A6640")]
STONE = [hexc(h) for h in ("E2DCCB", "C9C2B1", "A89F8B", "7A715F", "4E4636")]
PETALS = [hexc(h) for h in ("FF8AA0", "FFD166", "B9A3FF", "8FD8FF", "9BE36A", "FFFFFF", "FF9A4A")]
MANA = [hexc(h) for h in ("F2FFFF", "A6F6FF", "55D9F7", "2A9FE2", "1B64B8", "0B2F5C")]

THEME = T.Theme(
    outline=WALNUT[5], metal=(BRASS[0], BRASS[1], BRASS[2], BRASS[3], BRASS[4]), groove=hexc("140A04"),
    stone=(WALNUT[2], WALNUT[3], WALNUT[4]), stone_noise=0.25, floor_shadow=hexc("1A0E06"),
    slot_rim=(BRASS[1], BRASS[3], BRASS[4]), slot_inside=(hexc("2E1E12"), hexc("180E08")),
    inv_bg=lambda x, y: planks(x, y, 0.85),
    plaque_face=(PARCH[0], PARCH[2]), plaque_text_hint=hexc("4A2E14"), button_face=(WALNUT[1], WALNUT[2], WALNUT[3]),
    crystal=(hexc("FFFFFF"), hexc("FFE0EA"), hexc("FF9AB8"), hexc("D2557A"), hexc("7A2442")), salt=17)

CX, CY = APOTHECARY.CX, APOTHECARY.CY


def planks(x, y, k=1.0):
    """Walnut planks running across the table: their grain, seams between them."""
    row = (y + 2) // 11
    yy = (y + 2) % 11
    off = (row * 41) % 53
    if yy == 0 or (x + off) % 64 == 0:
        return A.dark(WALNUT[4], 1.0 - k) if k < 1.0 else WALNUT[4]
    base = WALNUT[2] if row % 2 else A.lerp(WALNUT[2], WALNUT[1], 0.35)
    grain = A.noise(x * 0.25, yy * 2.5 + row * 7, 2.0, 231)
    c = A.tint(base, (grain - 0.5) * 0.3)
    if A.noise(x * 0.5 + row * 13, yy, 6.0, 233) > 0.82:
        c = A.dark(c, 0.15)                                         # a knot
    if yy == 1:
        c = A.light(c, 0.12)
    return A.dark(c, 1.0 - k) if k < 1.0 else c


def table(x, y):
    """The table: planks, and a sheet of parchment under the bowl with a flower drawn on it in ink."""
    c = planks(x, y)
    # the parchment, a little askew
    px, py = x - CX, y - 82
    u, v = px * 0.995 + py * 0.1, -px * 0.1 + py * 0.995
    if abs(u) < 66 and abs(v) < 52:
        edge = min(66 - abs(u), 52 - abs(v))
        c = A.lerp(PARCH[1], PARCH[2], A.fbm(x, y, 9.0, 235, 2))
        if edge < 3:
            c = A.lerp(c, PARCH[3], 0.5)                            # its edges, browned
        # the ink drawing: a big flower, its petals round where the bowl stands
        d = math.hypot(u, (v - 6) * 1.1)
        ang = math.atan2(v - 6, u)
        petal = 30 + 9 * math.cos(ang * 6)
        if abs(d - petal) < 0.6 or abs(d - 14) < 0.5:
            c = A.lerp(c, hexc("6A4A2A"), 0.55)
        if abs(v - 44) < 0.5 and abs(u) < 40 and int(u) % 3:
            c = A.lerp(c, hexc("6A4A2A"), 0.35)                     # a line of writing
    vx = (x - W / 2.0) / (W / 2.0)
    vy = (y - MACHINE_H / 2.0) / (MACHINE_H / 2.0)
    return A.dark(c, 0.3 * min(1.0, (vx * vx * 0.7 + vy * vy) * 0.6))


def strewn(cv):
    """Petals strewn over the table."""
    for k in range(26):
        x = int(A.rnd(k, 241) * (W - 30)) + 15
        y = int(A.rnd(k, 243) * (MACHINE_H - 30)) + 15
        if math.hypot(x - CX, (y - 70) * 1.2) < 75 or x > 200 or x < 50:
            continue
        col = PETALS[int(A.rnd(k, 245) * len(PETALS))]
        cv.set(x, y, col)
        cv.set(x + 1, y, A.dark(col, 0.15))
        cv.set(x, y + 1, A.dark(col, 0.3))


# ---------------------------------------------------------------- the bowl

def ellipse_k(x, y, cx, cy, rx, ry):
    return ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2


def bowl(cv):
    """The apothecary: a stone bowl seen from above and in front, petals carved round its body, a brass band, its
    stem down to the seeds' slot."""
    rx, ry = APOTHECARY.RIM_RX, APOTHECARY.RIM_RY
    wrx, wry = APOTHECARY.WATER_RX, APOTHECARY.WATER_RY
    deep = ry + APOTHECARY.BODY
    sx, sy = APOTHECARY.REAGENT
    sh = A.ellipse(CX + 3, CY + deep + 1, rx - 4, 4)
    A.shadow(cv, sh, (CX - rx, CY + deep - 4, CX + rx + 6, CY + deep + 6), dx=0, dy=0, alpha=120, soft=2.5)
    # the stem and the seeds' slot under it
    for y in range(CY + deep - 2, sy - 2):
        for x in range(CX - 5, CX + 5):
            t = (x - (CX - 5)) / 9.0
            c = STONE[4] if x in (CX - 5, CX + 4) else A.ramp([(0.0, STONE[0]), (0.5, STONE[1]), (1.0, STONE[3])], t)
            cv.set(x, y, c)
    T.slot(cv, sx, sy, THEME, rim=(STONE[0], STONE[2], STONE[4]), inside=(hexc("3A2A1A"), hexc("1E140C")))

    def body(x, y):
        return ellipse_k(x, y, CX, CY, rx, deep) <= 1.0 and y + 0.5 >= CY

    for y in range(CY, CY + deep + 1):
        for x in range(CX - rx - 1, CX + rx + 1):
            if not body(x, y):
                continue
            t = (x + 0.5 - (CX - rx)) / (2.0 * rx)
            if not all(body(x + dx, y + dy) for (dx, dy) in ((1, 0), (-1, 0), (0, 1))):
                c = STONE[4]
            else:
                c = A.ramp([(0.0, STONE[0]), (0.3, STONE[1]), (0.75, STONE[2]), (1.0, STONE[3])], t)
                row = y - CY
                if row in (ry, ry + 1):
                    c = BRASS[1] if t < 0.3 else (BRASS[2] if t < 0.75 else BRASS[3])
                    if row == ry + 1:
                        c = A.dark(c, 0.2)
            cv.set(x, y, c)
    # petals carved round the body
    for k in range(7):
        px = -18 + k * 6
        x0, y0 = CX + px, CY + ry + 6 + int(abs(px) / 9)
        if not body(x0, y0 + 2):
            continue
        col = PETALS[k % len(PETALS)]
        for (dx, dy) in ((0, 0), (1, 0), (0, 1), (1, 1), (0, -1), (1, -1), (-1, 0), (2, 0)):
            if body(x0 + dx, y0 + dy):
                cv.set(x0 + dx, y0 + dy, A.lerp(col, STONE[2], 0.55 if dy else 0.35))
    # the rim, and the water's dark bed inside it
    for y in range(CY - ry - 1, CY + ry + 2):
        for x in range(CX - rx - 1, CX + rx + 2):
            if ellipse_k(x, y, CX, CY, rx, ry) > 1.0:
                continue
            ki = ellipse_k(x, y, CX, CY, wrx, wry)
            if any(ellipse_k(x + dx, y + dy, CX, CY, rx, ry) > 1.0 for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                c = STONE[4]
            elif ki > 1.0:
                nx, ny = (x + 0.5 - CX) / rx ** 2, (y + 0.5 - CY) / ry ** 2
                n = math.hypot(nx, ny) or 1.0
                lit = (nx * A.LIGHT[0] + ny * A.LIGHT[1]) / n
                c = STONE[0] if lit > 0.35 else (STONE[2] if lit < -0.35 else STONE[1])
                if ki < 1.4:
                    c = STONE[3] if y + 0.5 < CY else STONE[0]
            else:
                c = A.lerp(hexc("1C4A52"), hexc("0B1E24"), min(1.0, ki))
            cv.set(x, y, c)
    gem_socket(cv, *APOTHECARY.GEM)


def petal_plate(cv, x, y, deg):
    """An input's brass plate: a rounded square round the slot drawn out into a petal's tip, pointing away from
    the bowl, a vein along it."""
    cx, cy = x + 8.0, y + 8.0
    a = math.radians(deg)
    tip = (cx + math.cos(a) * 16.0, cy + math.sin(a) * 16.0)
    f = A.union(A.box(cx - 11, cy - 11, cx + 11, cy + 11, 5), A.segment(cx, cy, tip[0], tip[1], 4.5))
    area = (cx - 22, cy - 22, cx + 22, cy + 22)
    A.shadow(cv, f, area, dx=1, dy=2, alpha=120, soft=1.5)
    A.draw(cv, f, area, lambda px, py, d: A.lerp(BRASS[2], BRASS[3], 0.25 + 0.35 * A.noise(px, py, 3.0, 251)), outline=WALNUT[5],
           bevel=1.6, hi=0.45, lo=0.4)
    A.thick_line(cv, cx + math.cos(a) * 11.5, cy + math.sin(a) * 11.5, tip[0] - math.cos(a) * 2.5, tip[1] - math.sin(a) * 2.5, 0.4,
                 BRASS[4], 220)
    T.slot(cv, x, y, THEME)


# ---------------------------------------------------------------- the flask, the shelf

def flask(cv):
    """A round flask of glass on a little brass stand, a cork in its neck (the screen fills it with mana)."""
    cx, cy, r, neck, nw = APOTHECARY.FLASK
    top = cy - neck
    # the stand: a brass ring under the bulb, three little feet
    for (fx, fy) in ((cx - 9, cy + r - 1), (cx, cy + r + 1), (cx + 9, cy + r - 1)):
        for yy in range(fy, cy + r + 5):
            cv.set(fx, yy, BRASS[3])
            cv.set(fx + 1, yy, BRASS[2])
    ring = A.ellipse(cx + 0.5, cy + r - 2, r - 2, 2.5)
    for x, y, d in A.bounds(A.shell(ring, 0.8), cx - r, cy + r - 6, cx + r + 1, cy + r + 2):
        cv.set(x, y, BRASS[1] if y < cy + r - 2 else BRASS[3])
    for y in range(top - 6, cy + r + 1):
        h = flask_half(y) if y >= top else 0.0
        if y < top:
            # the cork
            for x in range(cx - nw, cx + nw + 1):
                cv.set(x, y, hexc("3A2414") if x in (cx - nw, cx + nw) or y == top - 6 else (hexc("C89A64") if x < cx else hexc("9C7448")))
            continue
        if h <= 0.0:
            continue
        for x in range(int(math.floor(cx + 0.5 - h)) - 1, int(math.ceil(cx + 0.5 + h)) + 1):
            dx = x + 0.5 - (cx + 0.5)
            if abs(dx) > h:
                continue
            edge = h - abs(dx)
            if edge <= 1.0 or (y >= cy + r - 1) or (min(flask_half(y - 1), flask_half(y + 1)) <= abs(dx) and y >= top + 1):
                c = hexc("2A1A10")
            elif edge <= 2.0:
                c = hexc("F4FFFF") if dx < 0 else hexc("8FB8C0")
            else:
                c = A.lerp(hexc("3A5A5C"), hexc("1E3638"), (y - top) / float(cy + r - top))
                c = A.lerp(c, table(x, y), 0.3)
            cv.set(x, y, c)
    for (gx, gy) in ((cx - 6, cy - 6), (cx - 7, cy - 4), (cx - 7, cy - 3), (cx - 2, top + 4), (cx - 2, top + 5)):
        cv.set(gx, gy, (255, 255, 255))


def shelf(cv):
    """A walnut shelf for the flowers made: four compartments, a brass plate on top."""
    x1, y1, x2, y2 = APOTHECARY.SHELF
    body = A.box(x1, y1, x2, y2, 3)
    A.shadow(cv, body, (x1, y1, x2, y2), dx=2, dy=3, alpha=130, soft=2.0)
    A.draw(cv, body, (x1, y1, x2, y2), lambda x, y, d: planks(x * 3, y, 0.9), outline=WALNUT[5], bevel=2.0, hi=0.35, lo=0.4)
    for (x, y) in APOTHECARY.OUTPUTS:
        T.slot(cv, x, y, THEME, rim=(BRASS[1], BRASS[2], BRASS[4]))
    plate = A.box(x1 + 6, y1 + 3, x2 - 6, y1 + 10, 1)
    A.draw(cv, plate, (x1 + 6, y1 + 3, x2 - 6, y1 + 10), BRASS[2], outline=WALNUT[5], bevel=1.2, hi=0.5, lo=0.4)


def gem_socket(cv, gx, gy):
    f = A.circle(gx + 0.5, gy + 0.5, 4.8)
    for x, y, d in A.bounds(f, gx - 6, gy - 6, gx + 7, gy + 7):
        k = A.facing(f, x + 0.5, y + 0.5)
        cv.set(x, y, WALNUT[5] if d <= 1.0 else ((BRASS[1] if k > 0.2 else (BRASS[3] if k < -0.3 else BRASS[2])) if d <= 2.0 else hexc("140A04")))


# ---------------------------------------------------------------- the panel, the sheet, the gloss

def panel():
    cv = T.new_panel()
    T.inventory(cv, THEME)
    T.panel(cv, 0, 0, W, MACHINE_H, THEME, table)
    strewn(cv)
    for (x, y), deg in zip(APOTHECARY.INPUTS, APOTHECARY.FAN_ANGLES):
        petal_plate(cv, x, y, deg)
    bowl(cv)
    flask(cv)
    shelf(cv)
    px, py = APOTHECARY.POOL
    sock = A.box(px - 1, py - 1, px + 17, py + 17, 3)
    A.draw(cv, sock, (px - 1, py - 1, px + 17, py + 17), WALNUT[3], outline=WALNUT[5], bevel=1.5, hi=0.3, lo=0.4, sunk=True)
    for (x, y) in APOTHECARY.LIGHTS:
        T.corner_gem(cv, x, y, THEME)
    return cv


SHEET_REGIONS = T.SHARED_REGIONS + APOTHECARY.SHEET


def sheet():
    cv = T.new_sheet()
    T.shared_pieces(cv, THEME)
    px, py = APOTHECARY.PETAL_UV
    for (dx, dy, a) in ((1, 0, 230), (2, 0, 255), (0, 1, 200), (1, 1, 255), (2, 1, 255), (3, 1, 200), (1, 2, 180), (2, 2, 220)):
        cv.set(px + dx, py + dy, (255, 255, 255), a)
    T.soft_glow(cv, APOTHECARY.STEAM_UV[0], APOTHECARY.STEAM_UV[1], APOTHECARY.STEAM_SIZE, 160, hole=0.0, power=1.4)
    pool_lamp(cv, False, *T.POOL_UV)
    pool_lamp(cv, True, T.POOL_UV[0] + T.POOL_SIZE, T.POOL_UV[1])
    return cv


def pool_lamp(cv, lit, x0, y0):
    """The pool lamp: a little stone dish, mana in it when a pool is beside the machine."""
    rim = A.ellipse(x0 + 8, y0 + 9.5, 7.2, 4.8)
    for x, y, d in A.bounds(rim, x0, y0, x0 + 16, y0 + 16):
        k = A.facing(rim, x + 0.5, y + 0.5)
        if d <= 1.0:
            c = STONE[4]
        elif d <= 2.6:
            c = STONE[0] if k > 0.3 else (STONE[3] if k < -0.3 else STONE[1])
        else:
            c = (MANA[1] if y < y0 + 9 else MANA[3]) if lit else (hexc("3A3228") if y < y0 + 9 else hexc("261E16"))
        cv.set(x, y, c)
    if lit:
        cv.set(x0 + 6, y0 + 8, (255, 255, 255))
        cv.set(x0 + 7, y0 + 8, MANA[0])


def gloss(panel_cv):
    """Where a shine sweeps over the panel: its brass, and the flask's glass."""
    out = T.new_panel()
    cx, cy, r, neck, nw = APOTHECARY.FLASK
    for y in range(panel_cv.h):
        for x in range(panel_cv.w):
            p = panel_cv.px[x, y]
            if p[3] == 0:
                continue
            r_, g, b = p[:3]
            brass = r_ > 170 and g > 120 and b < 140 and r_ - b > 80
            mx, my = x - 12, y - 12
            glass = flask_half(my) > abs(mx + 0.5 - (cx + 0.5)) and my >= cy - neck
            if brass or glass:
                out.px[x, y] = (255, 255, 255, min(255, int((90 if brass else 50) + 150 * A.lum((r_, g, b)))))
    return out
