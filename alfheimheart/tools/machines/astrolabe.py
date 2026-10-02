"""The Terrestrial Plate's GUI: the celestial astrolabe (textures/gui/terra_plate.png, terra_plate_widgets.png,
terra_plate_gloss.png).

The night sky in a frame of silver and deep blue: an astrolabe fills it, the plate its heart (a lapis octagon in
silver with a pale sun on it and a dark core, where the light gathers), a hexagram round it whose points are the
six inputs, set as lenses in the silver limb (the sky's triangle in mana blue, the earth's in green: terrasteel
is both), the deep inside the limb full of stars; the mana gauge an arc of the astrolabe's on its left, marked
in quarters; on the right, past a channel the made terrasteel goes along, a tower of three outputs under a
crescent moon that holds the status gem.

The screen (machine/plate/client/TerraPlateScreen.java) draws the moving parts on layouts.PLATE's numbers.
"""
import math

import art as A
import theme as T
from layouts import PLATE, W, MACHINE_H
from pix import hexc

N = [hexc(h) for h in ("03050D", "070B1D", "0C1430", "13204A", "1C3166", "284A8A", "3A6AB4")]
SV = [hexc(h) for h in ("F4F8FF", "D2DCEE", "A6B2CC", "74809E", "474F6A")]
G = [hexc(h) for h in ("FFF9D2", "FFE47D", "F4BA2E", "BA7C16", "6E4308")]
MANA = [hexc(h) for h in ("F2FFFF", "A6F6FF", "55D9F7", "2A9FE2", "1B64B8", "0B2F5C")]
TG = [hexc(h) for h in ("E4FFE8", "8DF5A0", "3FD866", "20A44A", "116B30")]
LAPIS = [hexc(h) for h in ("4A7CE0", "3361C6", "254CA8", "1B3A88", "122966")]
TEAL = [hexc(h) for h in ("D8FFF6", "8FF0DE", "4FC9C4", "2C8F98")]

THEME = T.Theme(
    outline=N[0], metal=(SV[0], SV[1], SV[2], SV[3], SV[4]), groove=hexc("010208"),
    stone=(N[5], N[4], N[2]), stone_noise=0.3, floor_shadow=hexc("010309"),
    slot_rim=(SV[2], N[5], N[1]), slot_inside=(hexc("0E1838"), hexc("060B1C")),
    inv_bg=lambda x, y: A.tint(A.lerp(N[3], N[2], (y - 150) / 98.0), (A.fbm(x, y, 9, 97) - 0.5) * 0.2),
    plaque_face=(N[4], N[2]), plaque_text_hint=SV[0], button_face=(N[5], N[4], N[2]),
    crystal=(hexc("FFFFFF"), hexc("DDEBFF"), hexc("9FC2FF"), hexc("5A7FC8"), hexc("2A3F7A")), salt=7)

CX, CY = PLATE.CX, PLATE.CY


# ---------------------------------------------------------------- the sky

def star(x, y, salt):
    """A star at the pixel, or None: few, of a few brightnesses, some bluish, some warm."""
    r = A.rnd2(x, y, salt)
    if r > 0.022:
        return None
    k = A.rnd2(x, y, salt + 1)
    c = (255, 255, 255) if k < 0.5 else (hexc("BFD8FF") if k < 0.8 else hexc("FFE9C8"))
    return c, 0.35 + 0.65 * A.rnd2(x, y, salt + 2)


def sky(x, y):
    """The night: deep blue darkening upwards, a faint band of the galaxy across it, a haze of nebula, stars."""
    t = y / float(MACHINE_H)
    c = A.ramp([(0.0, N[1]), (0.55, N[2]), (1.0, N[3])], t)
    band = math.exp(-(((x * 0.45 - y) - 20) / 34.0) ** 2)                 # the galaxy, slanting down to the right
    n = A.fbm(x, y, 18.0, 113, 3)
    c = A.lerp(c, hexc("2C3F86"), 0.35 * band * n)
    neb = A.fbm(x + 40, y, 26.0, 117, 3)
    c = A.lerp(c, hexc("3B1F6E"), 0.22 * max(0.0, neb - 0.45) * 2.0)
    c = A.lerp(c, hexc("124E5E"), 0.18 * max(0.0, A.fbm(x, y + 60, 22.0, 119, 2) - 0.55) * 2.0)
    s = star(x, y, 121 + int(band * 3))
    if s is None and band > 0.5 and A.rnd2(x, y, 125) < 0.03 * band:
        s = ((220, 230, 255), 0.3)
    if s is not None:
        c = A.lerp(c, s[0], s[1])
    # the vignette
    dx = (x - W / 2.0) / (W / 2.0)
    dy = (y - MACHINE_H / 2.0) / (MACHINE_H / 2.0)
    return A.dark(c, 0.4 * min(1.0, (dx * dx * 0.6 + dy * dy) * 0.5))


BIG_STARS = PLATE.STARS
CONSTELLATIONS = [[(170, 128), (178, 136), (188, 132), (184, 142)], [(10, 40), (20, 52), (34, 46)]]


def stars(cv):
    """A few bright stars with crosses, and two faint constellations."""
    for line in CONSTELLATIONS:
        for (x1, y1), (x2, y2) in zip(line, line[1:]):
            A.thick_line(cv, x1 + 0.5, y1 + 0.5, x2 + 0.5, y2 + 0.5, 0.3, hexc("6C86C8"), 60)
        for (x, y) in line:
            cv.set(x, y, hexc("DDE8FF"))
    for (x, y) in BIG_STARS:
        cv.set(x, y, (255, 255, 255))
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            cv.set(x + dx, y + dy, hexc("BFD8FF"), 150)
        for (dx, dy) in ((2, 0), (-2, 0), (0, 2), (0, -2)):
            cv.set(x + dx, y + dy, hexc("7FA8FF"), 70)


# ---------------------------------------------------------------- the astrolabe

def silver_ring(cv, r1, r2, ticks=0, a_from=None, a_to=None):
    """A ring of silver from r1 to r2, bevelled (lit on the top-left of its outer edge), ticks engraved across it."""
    outer = A.circle(CX, CY, r2)
    inner = A.circle(CX, CY, r1)
    f = A.subtract(outer, inner)
    for x, y, d in A.bounds(f, CX - r2 - 1, CY - r2 - 1, CX + r2 + 1, CY + r2 + 1):
        px, py = x + 0.5, y + 0.5
        deg = math.degrees(math.atan2(py - CY, px - CX)) % 360
        if a_from is not None and not (a_from <= deg <= a_to):
            continue
        dist = math.hypot(px - CX, py - CY)
        if dist > r2 - 0.8 or dist < r1 + 0.8:
            c = N[0]
        else:
            k = A.facing(outer, px, py)
            t = (dist - r1) / (r2 - r1)
            c = SV[1] if (k > 0.3 and t > 0.5) or (k < -0.3 and t < 0.5) else (SV[3] if (k < -0.3 and t > 0.5) or (k > 0.3 and t < 0.5) else SV[2])
            if ticks and (deg % ticks) < 360.0 / (2 * math.pi * dist) * 0.9:
                c = SV[4]
        cv.set(x, y, c)


def deep(cv):
    """Inside the limb: the astrolabe's own sky, darker, its stars thicker, a dotted ring through the hexagram."""
    r = PLATE.LIMB_R1
    f = A.circle(CX, CY, r)
    for x, y, d in A.bounds(f, CX - r - 1, CY - r - 1, CX + r + 1, CY + r + 1):
        dist = math.hypot(x + 0.5 - CX, y + 0.5 - CY)
        t = dist / r
        c = A.ramp([(0.0, hexc("16285E")), (0.5, hexc("0C1638")), (1.0, hexc("050916"))], t)
        c = A.tint(c, (A.fbm(x, y, 9.0, 131, 2) - 0.5) * 0.25)
        s = star(x, y, 133)
        if s is not None:
            c = A.lerp(c, s[0], s[1])
        if abs(dist - 35.0) < 0.5 and int(math.degrees(math.atan2(y - CY, x - CX)) // 4) % 2 == 0:
            c = A.lerp(c, SV[2], 0.4)
        cv.set(x, y, c)


def hexagram(cv):
    """The two triangles through the sockets: the sky's in mana blue, the earth's in green, softly glowing."""
    pts = [(x + 8.0, y + 8.0) for (x, y) in PLATE.INPUTS]
    sky_tri = [pts[i] for i in PLATE.SKY]
    earth_tri = [pts[i] for i in range(6) if i not in PLATE.SKY]
    for tri, (glow_c, core_c) in ((earth_tri, (TG[3], TG[1])), (sky_tri, (MANA[3], MANA[1]))):
        for (x1, y1), (x2, y2) in zip(tri, tri[1:] + tri[:1]):
            A.thick_line(cv, x1, y1, x2, y2, 2.2, glow_c, 50)
            A.thick_line(cv, x1, y1, x2, y2, 0.7, core_c, 210)


def lens(cv, x, y, sky_side):
    """A socket: a round silver bezel mounted on the limb round the slot, a gem of its triangle's colour on its
    outer side."""
    cx, cy = x + 8.0, y + 8.0
    bezel = A.circle(cx, cy, 16.0)
    A.shadow(cv, bezel, (cx - 17, cy - 17, cx + 17, cy + 17), dx=1, dy=2, alpha=160, soft=1.5)
    hole = A.circle(cx, cy, 12.9)
    for px, py, d in A.bounds(bezel, cx - 17, cy - 17, cx + 17, cy + 17):
        k = A.facing(bezel, px + 0.5, py + 0.5)
        di = hole(px + 0.5, py + 0.5)
        if d <= 0.9:
            c = N[0]
        elif di > 0.9:
            c = SV[0] if k > 0.6 else (SV[1] if k > 0.15 else (SV[3] if k < -0.35 else SV[2]))
        elif di > 0:
            c = N[0]
        else:
            c = A.lerp(hexc("0B1636"), hexc("050A18"), (py - cy + 12) / 24.0)
        cv.set(px, py, c)
    T.slot(cv, x, y, THEME)
    ang = math.atan2(cy - CY, cx - CX)
    gx, gy = cx + math.cos(ang) * 14.4, cy + math.sin(ang) * 14.4
    col = MANA if sky_side else TG
    g = A.circle(gx, gy, 2.1)
    for px, py, d in A.bounds(g, gx - 3, gy - 3, gx + 3, gy + 3):
        k = A.facing(g, px + 0.5, py + 0.5)
        cv.set(px, py, N[0] if d < 0.6 else (col[0] if k > 0.3 else (col[3] if k < -0.3 else col[2])))


def sun_pixel(px, py):
    """The pale sun on the plate (as the screen lights it): its ring, its eight rays, their tips."""
    d = math.hypot(px, py)
    if abs(d - PLATE.SUN_RING_R) < 0.6:
        return TEAL[2] if py < 0 else TEAL[3]
    ang = math.atan2(py, px)
    k = round(ang / (math.pi / 4.0))
    off = abs(ang - k * math.pi / 4.0) * d
    if PLATE.RAY_FROM < d < PLATE.RAY_TO and off < 0.6:
        return TEAL[1] if d < PLATE.SUN_RING_R else TEAL[2]
    if PLATE.RAY_TO <= d < PLATE.TIP_TO and off < 1.1:
        return TEAL[0]
    return None


def plate(cv):
    """The plate: a lapis octagon in a silver rim with a thin gold line inside it, the sun on it, the dark core."""
    r = PLATE.PLATE_R
    f = A.polygon(CX, CY, r, 8, 22.5)
    A.shadow(cv, f, (CX - r - 2, CY - r - 2, CX + r + 2, CY + r + 2), dx=2, dy=3, alpha=170, soft=2.0)
    for x, y, depth in A.bounds(f, CX - r - 2, CY - r - 2, CX + r + 2, CY + r + 2):
        px, py = x + 0.5 - CX, y + 0.5 - CY
        k = A.facing(f, x + 0.5, y + 0.5)
        d = math.hypot(px, py)
        if depth <= 1.0:
            c = N[0]
        elif depth <= 3.0:
            c = SV[0] if k > 0.6 else (SV[1] if k > 0.15 else (SV[3] if k < -0.3 else SV[2]))
        elif depth <= 4.0:
            c = G[2] if k > 0 else G[3]
        elif d > PLATE.CORE_R + 1:
            t = (py + r) / (2.0 * r)
            c = A.lerp(LAPIS[1], LAPIS[3], t)
            if A.rnd2(x, y, 141) < 0.12:
                c = A.lerp(c, LAPIS[0] if A.rnd2(x, y, 142) < 0.5 else LAPIS[4], 0.45)
            s = sun_pixel(px, py)
            if s is not None:
                c = s
            if depth <= 5.0:
                c = A.dark(c, 0.2)
        elif d > PLATE.CORE_R:
            c = G[2] if py > 0 else G[3]
        else:
            t = d / PLATE.CORE_R
            c = A.ramp([(0.0, hexc("1C3466")), (0.7, hexc("0B1430")), (1.0, hexc("050916"))], t)
            if A.rnd2(x, y, 143) < 0.08:
                c = A.lerp(c, MANA[1], 0.5)
        cv.set(x, y, c)


def gauge_arc(cv):
    """The mana gauge: a channel of the astrolabe's on its left, silver-rimmed, dark inside (the screen fills
    it from the bottom up), marked outside in tens of degrees and its quarters, knobs at its ends."""
    r1, r2 = PLATE.ARC_R1, PLATE.ARC_R2
    a0, a1 = PLATE.ARC_FROM, PLATE.ARC_TO
    outer = A.circle(CX, CY, r2 + 1)
    inner = A.circle(CX, CY, r1 - 1)
    for x, y, d in A.bounds(A.subtract(outer, inner), CX - r2 - 2, CY - r2 - 2, CX + r2 + 2, CY + r2 + 2):
        px, py = x + 0.5, y + 0.5
        deg = math.degrees(math.atan2(py - CY, px - CX)) % 360
        if not (a0 - 1.2 <= deg <= a1 + 1.2):
            continue
        dist = math.hypot(px - CX, py - CY)
        edge = deg < a0 or deg > a1
        if dist < r1 - 0.2 or dist > r2 + 0.2 or edge:
            k = A.facing(outer, px, py)
            c = SV[1] if k > 0.2 else SV[3]
            if dist < r1 - 0.2:
                c = SV[3] if k > 0.2 else SV[1]
        else:
            c = A.lerp(hexc("08102A"), hexc("040816"), (dist - r1) / (r2 - r1))
        cv.set(x, y, c)
    for deg in range(a0, a1 + 1, 10):
        quarter = (deg - a0) % 30 == 0
        for t in ((r2 + 2.0, r2 + 3.6) if quarter else (r2 + 2.0, r2 + 2.8)):
            x = CX + t * math.cos(math.radians(deg))
            y = CY + t * math.sin(math.radians(deg))
            cv.set(int(math.floor(x)), int(math.floor(y)), SV[1] if quarter else SV[3])
    for deg in (a0, a1):
        x = CX + (r1 + r2) / 2.0 * math.cos(math.radians(deg))
        y = CY + (r1 + r2) / 2.0 * math.sin(math.radians(deg))
        knob = A.circle(x, y, 3.6)
        A.draw(cv, knob, (x - 5, y - 5, x + 5, y + 5), SV[2], outline=N[0], bevel=1.6, hi=0.6, lo=0.45)


def channel(cv):
    """The channel from the limb out to the tower that the made terrasteel goes along."""
    x1, y, x2, _ = PLATE.BEAM
    for x in range(x1 - 2, x2 + 1):
        cv.set(x, y - 3, N[0])
        cv.set(x, y - 2, SV[2])
        for yy in (y - 1, y):
            cv.set(x, yy, hexc("050A1C"))
        cv.set(x, y + 1, SV[3])
        cv.set(x, y + 2, N[0])
    for x in range(x1 + 4, x2 - 2, 6):
        cv.set(x, y - 1, hexc("1A2A5A"))


def tower(cv):
    """The moon tower: a silver-framed column of night glass holding the outputs, a spire up to the crescent moon
    whose arms hold the status gem, steps at its foot."""
    x1, y1, x2, y2 = PLATE.TOWER
    body = A.box(x1, y1, x2, y2, 4)
    A.shadow(cv, body, (x1, y1, x2, y2), dx=2, dy=3, alpha=160, soft=2.0)
    inner = A.box(x1 + 4, y1 + 4, x2 - 4, y2 - 4, 2)
    for x, y, depth in A.bounds(body, x1, y1, x2, y2):
        px, py = x + 0.5, y + 0.5
        k = A.facing(body, px, py)
        di = inner(px, py)
        if depth <= 1.0:
            c = N[0]
        elif depth <= 3.0:
            c = SV[0] if k > 0.6 else (SV[1] if k > 0.15 else (SV[3] if k < -0.3 else SV[2]))
        elif di > 0:
            c = N[0]
        else:
            t = (py - y1) / float(y2 - y1)
            c = A.ramp([(0.0, hexc("1A2F6A")), (1.0, hexc("08102A"))], t)
            s = star(x, y, 151)
            if s is not None:
                c = A.lerp(c, s[0], s[1] * 0.8)
        cv.set(x, y, c)
    for (x, y) in PLATE.OUTPUTS:
        T.slot(cv, x, y, THEME, rim=(SV[1], SV[2], SV[4]), inside=(hexc("13235A"), hexc("070D22")))
    # the spire up to the moon
    mx, my, mr = PLATE.MOON
    for y in range(my + mr - 2, y1):
        for x in range(mx - 1, mx + 2):
            cv.set(x, y, N[0] if x != mx else SV[2])
    moon(cv, mx, my, mr)
    # steps at its foot
    step = A.box(x1 - 3, y2 - 2, x2 + 3, y2 + 3, 1)
    A.draw(cv, step, (x1 - 3, y2 - 2, x2 + 3, y2 + 3), N[4], outline=N[0], bevel=1.2, hi=0.4, lo=0.4)


def moon(cv, mx, my, r):
    """A crescent of silver and pale gold, opening to the right, round the gem's gold setting."""
    disc = A.circle(mx + 0.5, my + 0.5, r)
    bite = A.circle(mx + 0.5 + r * 0.55, my + 0.5 - r * 0.1, r * 0.82)
    crescent = A.subtract(disc, bite)
    A.glow(cv, disc, (mx - r, my - r, mx + r + 1, my + r + 1), hexc("9FC2FF"), reach=4.0, alpha=45)
    for x, y, d in A.bounds(crescent, mx - r - 1, my - r - 1, mx + r + 2, my + r + 2):
        k = A.facing(disc, x + 0.5, y + 0.5)
        if d <= 0.9:
            c = N[0]
        else:
            c = A.lerp(hexc("FFF4D8"), SV[2], (0.5 - 0.5 * k))
            if A.rnd2(x, y, 155) < 0.1:
                c = A.dark(c, 0.12)                                  # its seas
        cv.set(x, y, c)
    setting = A.circle(mx + 0.5, my + 0.5, 4.8)
    for x, y, d in A.bounds(setting, mx - 6, my - 6, mx + 7, my + 7):
        k = A.facing(setting, x + 0.5, y + 0.5)
        cv.set(x, y, N[0] if d <= 1.0 else (G[1] if k > 0.2 else G[3]) if d <= 2.0 else hexc("0A0D1A"))


def pool_socket(cv):
    px, py = PLATE.POOL
    sock = A.box(px - 1, py - 1, px + 17, py + 17, 3)
    A.draw(cv, sock, (px - 1, py - 1, px + 17, py + 17), N[2], outline=N[0], bevel=1.5, hi=0.3, lo=0.4, sunk=True)


# ---------------------------------------------------------------- the panel, the sheet, the gloss

def panel():
    cv = T.new_panel()
    T.inventory(cv, THEME)
    T.panel(cv, 0, 0, W, MACHINE_H, THEME, sky)
    stars(cv)
    A.glow(cv, A.circle(CX, CY, PLATE.LIMB_R2 + 1), (CX - 72, CY - 72, CX + 72, CY + 72), hexc("3A6AD0"), reach=14.0, alpha=55)
    gauge_arc(cv)
    deep(cv)
    hexagram(cv)
    silver_ring(cv, PLATE.LIMB_R1, PLATE.LIMB_R2, ticks=10)
    channel(cv)
    for i, (x, y) in enumerate(PLATE.INPUTS):
        lens(cv, x, y, i in PLATE.SKY)
    plate(cv)
    tower(cv)
    pool_socket(cv)
    for (x, y) in PLATE.LIGHTS:
        T.corner_gem(cv, x, y, THEME)
    return cv


SHEET_REGIONS = T.SHARED_REGIONS + PLATE.SHEET


def star_sprite(cv):
    """A four-pointed star, white (the screen tints it): its twinkles."""
    x0, y0 = PLATE.STAR_UV
    s = PLATE.STAR_SIZE
    c = s // 2
    for y in range(s):
        for x in range(s):
            dx, dy = abs(x - c), abs(y - c)
            a = 0.0
            if dx == 0 or dy == 0:
                a = max(0.0, 1.0 - (dx + dy) / (c + 0.5)) ** 1.2
            elif dx == 1 and dy == 1:
                a = 0.35
            if dx == 0 and dy == 0:
                a = 1.0
            if a > 0.02:
                cv.set(x0 + x, y0 + y, (255, 255, 255), int(255 * a))


def pool_lamp(cv, lit, x0, y0):
    """The pool lamp: a little round pool of night water, the moon's light in it when a pool is beside the machine."""
    rim = A.ellipse(x0 + 8, y0 + 9.5, 7.2, 4.8)
    for x, y, d in A.bounds(rim, x0, y0, x0 + 16, y0 + 16):
        k = A.facing(rim, x + 0.5, y + 0.5)
        if d <= 1.0:
            c = N[0]
        elif d <= 2.6:
            c = SV[0] if k > 0.3 else (SV[3] if k < -0.3 else SV[1])
        else:
            c = (MANA[1] if y < y0 + 9 else MANA[3]) if lit else (hexc("1A2550") if y < y0 + 9 else hexc("101838"))
        cv.set(x, y, c)
    if lit:
        cv.set(x0 + 6, y0 + 8, (255, 255, 255))
        cv.set(x0 + 7, y0 + 8, MANA[0])


def sheet():
    cv = T.new_sheet()
    T.shared_pieces(cv, THEME)
    star_sprite(cv)
    pool_lamp(cv, False, *T.POOL_UV)
    pool_lamp(cv, True, T.POOL_UV[0] + T.POOL_SIZE, T.POOL_UV[1])
    return cv


def gloss(panel_cv):
    """Where a shine sweeps over the panel: its silver and gold."""
    out = T.new_panel()
    for y in range(panel_cv.h):
        for x in range(panel_cv.w):
            p = panel_cv.px[x, y]
            if p[3] == 0:
                continue
            r, g, b = p[:3]
            silver = r > 150 and g > 160 and b > 180 and max(r, g, b) - min(r, g, b) < 70
            gold = r > 170 and g > 110 and b < 130 and r - b > 90
            if silver or gold:
                out.px[x, y] = (255, 255, 255, min(255, int(90 + 165 * A.lum((r, g, b)))))
    return out
