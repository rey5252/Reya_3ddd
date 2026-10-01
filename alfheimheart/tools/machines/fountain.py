"""The Mana Infuser's GUI: the crystal fountain (textures/gui/mana_infuser.png, mana_infuser_widgets.png,
mana_infuser_gloss.png).

A courtyard of pale marble tiles with a mosaic ring of sea glass and gold round a marble fountain: its basin seen
from above and in front (a carved front wall with a gold band and the status gem, the rim, the far inside wall
and a mosaic floor through its opening: the screen fills it with mana water to the machine's level), standing
on a stem over the catalyst's foundation stone; the inputs in an arched alcove trimmed with sea glass on the
left, the outputs in a gilded one on the right, ivy in the corners.

The screen (machine/infuser/client/ManaInfuserScreen.java) draws the moving parts on layouts.INFUSER's numbers.
"""
import math

import art as A
import theme as T
from layouts import INFUSER, W, MACHINE_H
from pix import hexc

MB = [hexc(h) for h in ("FFFFFF", "F1F3F7", "DDE2EA", "C4CBD8", "A3ABBC", "7D8599", "565D70", "2E3342")]
SEA = [hexc(h) for h in ("E6FFFA", "A8F5E6", "5FDCCB", "2FB3A6", "1A7D78", "0E4A4A")]
G = [hexc(h) for h in ("FFF9D2", "FFE47D", "F4BA2E", "BA7C16", "6E4308")]
MANA = [hexc(h) for h in ("F2FFFF", "A6F6FF", "55D9F7", "2A9FE2", "1B64B8", "0B2F5C")]
LEAF = [hexc(h) for h in ("B8F08A", "7CCB52", "4E9A38", "2F6A2A", "1C4420")]

THEME = T.Theme(
    outline=MB[7], metal=(G[0], G[1], G[2], G[3], G[4]), groove=SEA[5],
    stone=(MB[0], MB[2], MB[4]), stone_noise=0.12, floor_shadow=MB[6],
    slot_rim=(MB[0], MB[3], MB[5]), slot_inside=(hexc("21404A"), hexc("102429")),
    inv_bg=lambda x, y: marble(x, y, 0.92),
    plaque_face=(MB[1], MB[3]), plaque_text_hint=SEA[5], button_face=(MB[1], MB[2], MB[4]),
    crystal=(hexc("FFFFFF"), SEA[1], SEA[2], SEA[3], SEA[4]), salt=11)

CX, CY = INFUSER.CX, INFUSER.CY


def marble(x, y, light_k=1.0):
    """Pale marble: cool white, grey veins wandering through it."""
    n = A.fbm(x, y, 14.0, 161, 3)
    c = A.lerp(MB[1], MB[2], n)
    v = A.vein(x, y, 22.0, 163, 0.03)
    if v > 0:
        c = A.lerp(c, MB[4], 0.55 * v)
    v2 = A.vein(x + 50, y, 12.0, 167, 0.02)
    if v2 > 0:
        c = A.lerp(c, MB[3], 0.35 * v2)
    return A.dark(c, 1.0 - light_k) if light_k < 1.0 else c


# ---------------------------------------------------------------- the courtyard

def courtyard(x, y):
    """Marble tiles with thin grout, a mosaic ring of sea glass and gold round the fountain, a soft vignette."""
    tile = 22
    tx, ty = (x + 5) % tile, (y + 9) % tile
    c = marble(x + (y + 9) // tile * 37, y + (x + 5) // tile * 53)
    if tx == 0 or ty == 0:
        c = A.lerp(c, hexc("8FB6B6"), 0.55)                         # the grout, a little teal
    elif tx == 1 or ty == 1:
        c = A.light(c, 0.35)
    # the mosaic ring
    dx, dy = (x + 0.5 - CX) / 1.0, (y + 0.5 - (CY + 26)) / 0.55
    d = math.hypot(dx, dy)
    if 62 < d < 70:
        cell = (int(math.atan2(dy, dx) * 26) + int(d)) % 5
        tess = [SEA[2], SEA[3], G[2], SEA[1], MB[0]][cell]
        c = A.lerp(c, tess, 0.75)
        if (x + y) % 3 == 0:
            c = A.dark(c, 0.12)
    elif 60.5 < d <= 62 or 70 <= d < 71.5:
        c = A.lerp(c, G[3], 0.5)
    vx = (x - W / 2.0) / (W / 2.0)
    vy = (y - MACHINE_H / 2.0) / (MACHINE_H / 2.0)
    return A.dark(c, 0.22 * min(1.0, (vx * vx * 0.7 + vy * vy) * 0.6))


IVY = [((8, 8), 1, 1), ((247, 8), -1, 1), ((8, 147), 1, -1), ((247, 147), -1, -1)]


def ivy(cv):
    """Ivy creeping in from the corners: a stem and little leaves."""
    for (x0, y0), sx, sy in IVY:
        for k in range(16):
            t = k / 15.0
            x = x0 + sx * int(round(18 * t + 3 * math.sin(t * 5)))
            y = y0 + sy * int(round(6 * t * t + 2 * math.sin(t * 7)))
            cv.set(x, y, LEAF[3])
            if k % 4 == 1:
                leaf(cv, x, y + sy * 2, sx)
            if k % 4 == 3:
                leaf(cv, x - sx, y - sy * 2, -sx)


def leaf(cv, x, y, s):
    pts = [(0, 0, LEAF[1]), (s, 0, LEAF[0]), (0, 1, LEAF[2]), (s, 1, LEAF[1]), (2 * s, 0, LEAF[2]), (0, -1, LEAF[2])]
    for dx, dy, c in pts:
        cv.set(x + dx, y + dy, c)


# ---------------------------------------------------------------- the fountain

def rim_k(x, y, rx, ry, cy=CY):
    return ((x + 0.5 - CX) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2


def fountain(cv):
    rx, ry = INFUSER.RIM_RX, INFUSER.RIM_RY
    orx, ory = INFUSER.OPEN_RX, INFUSER.OPEN_RY
    depth = INFUSER.DEPTH
    # its shadow on the floor
    sh = A.ellipse(CX + 3, CY + depth + 4, rx + 2, ry + 3)
    A.shadow(cv, sh, (CX - rx - 4, CY + depth - ry, CX + rx + 6, CY + depth + ry + 8), dx=0, dy=0, alpha=110, soft=4.0)
    stem(cv)
    # the front wall: a cylinder's face from the rim's lower edge down `depth`, carved panels, a gold band
    for x in range(CX - rx, CX + rx):
        px = x + 0.5 - CX
        if abs(px) >= rx:
            continue
        lower = CY + ry * math.sqrt(max(0.0, 1.0 - (px / rx) ** 2))
        y_top = int(math.floor(lower))
        t = (px + rx) / (2.0 * rx)                                  # 0 on the left, 1 on the right
        for j in range(0, depth + 1):
            y = y_top + j
            if j == depth or abs(px) > rx - 1.0:
                c = MB[7]
            else:
                c = A.ramp([(0.0, MB[0]), (0.25, MB[1]), (0.6, MB[3]), (0.9, MB[5]), (1.0, MB[6])], t)
                c = A.tint(c, (A.noise(x, y, 2.0, 171) - 0.5) * 0.06)
                if j in (3, 4):                                     # the gold band under the rim
                    c = G[1] if t < 0.3 else (G[2] if t < 0.75 else G[3])
                    if j == 4:
                        c = A.dark(c, 0.15)
                elif j == 5:
                    c = A.dark(c, 0.3)
                elif j == depth - 3:
                    c = A.light(c, 0.2) if t < 0.5 else A.dark(c, 0.15)   # the plinth's lip
                elif j > depth - 3:
                    c = A.dark(c, 0.12)
                else:
                    # carved arched panels round the wall
                    panel_w = 11
                    u = (x - (CX - rx) + 2) % panel_w
                    v = j - 7
                    if 0 <= v < depth - 12 and u in (1, panel_w - 2):
                        c = A.dark(c, 0.25) if u == panel_w - 2 else A.light(c, 0.25)
                    elif v == -1 and 1 <= u <= panel_w - 2:
                        c = A.dark(c, 0.2)
            cv.set(x, y, c)
    # the rim's top and the inside through its opening
    for y in range(CY - ry - 1, CY + ry + 2):
        for x in range(CX - rx - 1, CX + rx + 2):
            k = rim_k(x, y, rx, ry)
            if k > 1.0:
                continue
            ki = rim_k(x, y, orx, ory)
            edge = any(rim_k(x + dx, y + dy, rx, ry) > 1.0 for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                c = MB[7]
            elif ki > 1.0:
                # the rim's flat top: lit towards the back, its inner lip darker
                nx, ny = (x + 0.5 - CX) / rx ** 2, (y + 0.5 - CY) / ry ** 2
                n = math.hypot(nx, ny) or 1.0
                lit = (nx * A.LIGHT[0] + ny * A.LIGHT[1]) / n
                c = MB[0] if lit > 0.3 else (MB[3] if lit < -0.4 else MB[1])
                if ki < 1.25:
                    c = MB[5] if y + 0.5 < CY else MB[1]
                if 1.55 < ki < 1.75:
                    c = A.lerp(c, SEA[2], 0.6)                         # a line of sea glass round the rim
            elif any(rim_k(x + dx, y + dy, orx, ory) > 1.0 for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                c = MB[6]
            else:
                c = inside(x, y)
            cv.set(x, y, c)
    gem_socket(cv, *INFUSER.GEM)


def inside(x, y):
    """Through the opening: the far inside wall (lit at its top, shadowed lower), the mosaic floor deep down."""
    floor_y = CY + INFUSER.WATER_EMPTY
    if y + 0.5 < floor_y - INFUSER.OPEN_RY * 0.6:
        t = (y + 0.5 - (CY - INFUSER.OPEN_RY)) / float(floor_y - INFUSER.OPEN_RY * 0.6 - (CY - INFUSER.OPEN_RY))
        c = A.ramp([(0.0, MB[3]), (0.6, MB[5]), (1.0, MB[6])], t)
        if (y - CY) % 6 == 0:
            c = A.dark(c, 0.1)                                         # courses of stone
        return c
    # the floor: little tiles of sea glass, dim
    u, v = x // 3, y // 2
    tess = [SEA[3], SEA[4], hexc("1F5C66"), SEA[3]][(u * 7 + v * 13) % 4]
    c = A.dark(tess, 0.35)
    if x % 3 == 0 or y % 2 == 0:
        c = A.dark(c, 0.3)
    return c


def stem(cv):
    """The fountain's stem down to the catalyst's foundation stone, and the stone itself round its slot."""
    sx, sy = INFUSER.CATALYST
    top = CY + INFUSER.RIM_RY + INFUSER.DEPTH - 1
    for y in range(top, sy - 3):
        for x in range(CX - 7, CX + 7):
            t = (x - (CX - 7)) / 13.0
            c = MB[7] if x in (CX - 7, CX + 6) else A.ramp([(0.0, MB[0]), (0.5, MB[2]), (1.0, MB[5])], t)
            if (y - top) % 4 == 3:
                c = A.dark(c, 0.12)
            cv.set(x, y, c)
    stone = A.box(sx - 7, sy - 5, sx + 23, sy + 21, 5)
    A.shadow(cv, stone, (sx - 7, sy - 5, sx + 23, sy + 21), dx=1, dy=2, alpha=100, soft=1.5)
    A.draw(cv, stone, (sx - 7, sy - 5, sx + 23, sy + 21), lambda x, y, d: marble(x, y, 0.9), outline=MB[7], bevel=2.0, hi=0.45, lo=0.4)
    for (gx, gy) in ((sx - 3, sy + 8), (sx + 18, sy + 8)):
        cv.set(gx, gy, SEA[2])
        cv.set(gx, gy + 1, SEA[4])
    T.slot(cv, sx, sy, THEME, rim=(SEA[1], SEA[3], SEA[5]), inside=(hexc("1D4650"), hexc("0E2329")))


def alcove(cv, box, trim):
    """An arched alcove of marble trimmed with sea glass or gold round a 2x3 block of slots."""
    x1, y1, x2, y2 = box
    outer = A.arch(x1, y1, x2, y2)
    A.shadow(cv, outer, box, dx=2, dy=3, alpha=90, soft=2.0)
    inner = A.arch(x1 + 5, y1 + 5, x2 - 5, y2 - 3)
    lit, mid, shade = trim
    for x, y, depth in A.bounds(outer, x1, y1, x2, y2):
        px, py = x + 0.5, y + 0.5
        k = A.facing(outer, px, py)
        di = inner(px, py)
        if depth <= 1.0:
            c = MB[7]
        elif depth <= 3.0:
            c = lit if k > 0.15 else (shade if k < -0.3 else mid)
        elif di > 1.0:
            c = A.tint(marble(x, y), 0.15 * k)
        elif di > 0.0:
            c = MB[6]
        else:
            t = (py - y1) / float(y2 - y1)
            c = A.ramp([(0.0, hexc("2A5560")), (1.0, hexc("143038"))], t)
            if (x + y) % 6 == 0 and (x - y) % 6 == 0:
                c = A.light(c, 0.08)
            if di > -2.0:
                c = A.dark(c, 0.3)
        cv.set(x, y, c)
    # a shell carved in the arch's head
    sx, sy = (x1 + x2) / 2.0, y1 + 12.0
    for k in range(5):
        a = math.radians(200 + k * 35)
        A.thick_line(cv, sx, sy + 3, sx + 6 * math.cos(a), sy + 3 + 6 * math.sin(a), 0.5, lit, 200)
    cv.set(int(sx), int(sy + 3), mid)


def gem_socket(cv, gx, gy):
    f = A.circle(gx + 0.5, gy + 0.5, 4.8)
    for x, y, d in A.bounds(f, gx - 6, gy - 6, gx + 7, gy + 7):
        k = A.facing(f, x + 0.5, y + 0.5)
        cv.set(x, y, MB[7] if d <= 1.0 else ((G[1] if k > 0.2 else (G[3] if k < -0.3 else G[2])) if d <= 2.0 else hexc("0A1A1E")))


# ---------------------------------------------------------------- the panel, the sheet, the gloss

def panel():
    cv = T.new_panel()
    T.inventory(cv, THEME)
    T.panel(cv, 0, 0, W, MACHINE_H, THEME, courtyard)
    ivy(cv)
    alcove(cv, INFUSER.ALCOVES[0], (SEA[1], SEA[3], SEA[4]))
    alcove(cv, INFUSER.ALCOVES[1], (G[1], G[2], G[3]))
    for (x, y) in INFUSER.INPUTS:
        T.slot(cv, x, y, THEME, rim=(SEA[1], SEA[3], SEA[5]))
    for (x, y) in INFUSER.OUTPUTS:
        T.slot(cv, x, y, THEME, rim=(G[1], G[2], G[4]))
    fountain(cv)
    px, py = INFUSER.POOL
    sock = A.box(px - 1, py - 1, px + 17, py + 17, 3)
    A.draw(cv, sock, (px - 1, py - 1, px + 17, py + 17), MB[3], outline=MB[7], bevel=1.5, hi=0.3, lo=0.4, sunk=True)
    for (x, y) in INFUSER.LIGHTS:
        T.corner_gem(cv, x, y, THEME)
    return cv


SHEET_REGIONS = T.SHARED_REGIONS + INFUSER.SHEET

RING_GLYPHS = [["#.#", ".#.", "#.#"], [".#.", "###", ".#."], ["##.", "#.#", ".##"], ["#..", "###", "..#"]]


def catalyst_ring(cv):
    """A circle of little runes round the catalyst, white (the screen tints it its catalyst's colour)."""
    x0, y0 = INFUSER.CATALYST_RING_UV
    s = INFUSER.CATALYST_RING_SIZE
    c = s / 2.0
    for y in range(s):
        for x in range(s):
            d = math.hypot(x + 0.5 - c, y + 0.5 - c)
            if abs(d - (c - 1.5)) < 0.6:
                cv.set(x0 + x, y0 + y, (255, 255, 255), 200)
    for k in range(8):
        a = k * math.pi / 4.0 + math.pi / 8.0
        gx, gy = c + (c - 5.0) * math.cos(a), c + (c - 5.0) * math.sin(a)
        A.glyph(cv, RING_GLYPHS[k % 4], x0 + int(round(gx - 1.5)), y0 + int(round(gy - 1.5)), (255, 255, 255), 230)


def pool_lamp(cv, lit, x0, y0):
    """The pool lamp: a little marble basin, mana in it when a pool is beside the machine."""
    rim = A.ellipse(x0 + 8, y0 + 9.5, 7.2, 4.8)
    for x, y, d in A.bounds(rim, x0, y0, x0 + 16, y0 + 16):
        k = A.facing(rim, x + 0.5, y + 0.5)
        if d <= 1.0:
            c = MB[7]
        elif d <= 2.6:
            c = MB[0] if k > 0.3 else (MB[4] if k < -0.3 else MB[2])
        else:
            c = (MANA[1] if y < y0 + 9 else MANA[3]) if lit else (hexc("3A4A52") if y < y0 + 9 else hexc("26343A"))
        cv.set(x, y, c)
    if lit:
        cv.set(x0 + 6, y0 + 8, (255, 255, 255))
        cv.set(x0 + 7, y0 + 8, MANA[0])


def sheet():
    cv = T.new_sheet()
    T.shared_pieces(cv, THEME)
    catalyst_ring(cv)
    pool_lamp(cv, False, *T.POOL_UV)
    pool_lamp(cv, True, T.POOL_UV[0] + T.POOL_SIZE, T.POOL_UV[1])
    return cv


def gloss(panel_cv):
    """Where a shine sweeps over the panel: its gold and sea glass."""
    out = T.new_panel()
    for y in range(panel_cv.h):
        for x in range(panel_cv.w):
            p = panel_cv.px[x, y]
            if p[3] == 0:
                continue
            r, g, b = p[:3]
            gold = r > 170 and g > 110 and b < 130 and r - b > 90
            sea = g > 150 and b > 140 and r < 140
            if gold or sea:
                out.px[x, y] = (255, 255, 255, min(255, int(90 + 165 * A.lum((r, g, b)))))
    return out
