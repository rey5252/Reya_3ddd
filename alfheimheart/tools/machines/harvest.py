"""The Crop Field's GUI: the golden field (textures/gui/crop_field.png, crop_field_widgets.png, crop_field_gloss.png).

A field of wheat at sunset in a frame of weathered fence wood: the sky going from gold to violet over far hills,
the sun low on them, ripe wheat everywhere under it; the inputs six plots in two furrows of tilled soil with
sprouts in their ridges, the bone meal in a burlap sack under them; a rain gauge of glass in a wooden stand on
the left (the screen fills it with mana and rains into it from a little cloud), a crate for the harvest on the
right, a scarecrow keeping watch.

The screen (machine/field/client/CropFieldScreen.java) draws the moving parts on layouts.FIELD's numbers.
"""
import math

import art as A
import theme as T
from layouts import FIELD, W, MACHINE_H
from pix import hexc

FENCE = [hexc(h) for h in ("D8C4A0", "B8A07A", "8E7656", "64503A", "3A2E20")]
WHEAT = [hexc(h) for h in ("FFF0A8", "F4D46A", "E0B444", "B88A2C", "7A5A1A")]
SOIL = [hexc(h) for h in ("6A4A2E", "4E3420", "382416", "22160C")]
STAVE = [hexc(h) for h in ("B88A58", "966C40", "74522E", "52381E", "301E0E")]
IRON = [hexc(h) for h in ("C8CCD0", "9AA0A6", "6E747A", "42484E")]
G = [hexc(h) for h in ("FFF9D2", "FFE47D", "F4BA2E", "BA7C16", "6E4308")]
MANA = [hexc(h) for h in ("F2FFFF", "A6F6FF", "55D9F7", "2A9FE2", "1B64B8", "0B2F5C")]

THEME = T.Theme(
    outline=FENCE[4], metal=(G[0], G[1], G[2], G[3], G[4]), groove=hexc("2A1A0A"),
    stone=(FENCE[0], FENCE[1], FENCE[3]), stone_noise=0.25, floor_shadow=hexc("3A2410"),
    slot_rim=(FENCE[0], FENCE[2], FENCE[4]), slot_inside=(hexc("3A2A1A"), hexc("1E140C")),
    inv_bg=lambda x, y: A.tint(A.lerp(FENCE[1], FENCE[2], (y - 150) / 98.0), (A.noise(x * 0.3, y * 2, 2.0, 351) - 0.5) * 0.18),
    plaque_face=(FENCE[0], FENCE[2]), plaque_text_hint=hexc("4A2E10"), button_face=(FENCE[1], FENCE[2], FENCE[3]),
    crystal=(hexc("FFFFFF"), WHEAT[0], WHEAT[1], WHEAT[2], WHEAT[3]), salt=29)

HORIZON = 34


def hills(x):
    return 6 + 5 * A.noise(x, 0, 30.0, 353) + 2 * A.noise(x, 0, 8.0, 355)


def sky(x, y):
    t = y / float(HORIZON)
    c = A.ramp([(0.0, hexc("6A4A9A")), (0.45, hexc("E87A6A")), (1.0, hexc("FFC870"))], t)
    d = math.hypot(x - 64, y - (HORIZON - 4))
    return A.lerp(c, hexc("FFF0C0"), max(0.0, 1.0 - d / 60.0) * 0.55)


def wheat(x, y):
    """Ripe wheat: stalks leaning in the wind, heads catching the low sun, darker towards the bottom."""
    t = (y - HORIZON) / float(MACHINE_H - HORIZON)
    lean = int(y * 0.35)
    stalk = (x + lean) % 3
    c = A.ramp([(0.0, WHEAT[1]), (0.6, WHEAT[2]), (1.0, WHEAT[3])], t)
    if stalk == 0:
        c = A.dark(c, 0.15)
    head = A.noise(x + lean, y, 3.0, 357)
    if head > 0.7:
        c = A.light(c, 0.2)
    if A.rnd2(x, y, 359) < 0.02:
        c = WHEAT[0]
    return c


def background(x, y):
    top = HORIZON - hills(x)
    if y < top:
        return sky(x, y)
    if y < HORIZON:
        c = A.lerp(hexc("8A6A9A"), hexc("A87A6A"), (y - top) / max(1.0, HORIZON - top))
        return A.lerp(c, hexc("FFC870"), 0.15)
    c = wheat(x, y)
    vx = (x - W / 2.0) / (W / 2.0)
    vy = (y - MACHINE_H / 2.0) / (MACHINE_H / 2.0)
    return A.dark(c, 0.3 * min(1.0, (vx * vx * 0.7 + max(0.0, vy) ** 2) * 0.7))


def sun(cv):
    cx, cy, r = 64, HORIZON - 5, 8
    f = A.circle(cx, cy, r)
    A.glow(cv, f, (cx - r, cy - r, cx + r, cy + r), hexc("FFE0A0"), reach=12.0, alpha=80)
    for x, y, d in A.bounds(f, cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1):
        if y >= HORIZON - hills(x):
            continue
        cv.set(x, y, A.lerp(hexc("FFF8E0"), hexc("FFB860"), (y - cy + r) / (2.0 * r)))


def furrow(cv, x1, y1, x2, y2):
    """A strip of tilled soil: ridges lit along their tops, shadowed behind, sprouts along them."""
    f = A.box(x1, y1, x2, y2, 5)
    A.shadow(cv, f, (x1, y1, x2, y2), dx=1, dy=2, alpha=120, soft=1.5)
    for x, y, depth in A.bounds(f, x1, y1, x2, y2):
        if depth <= 1.0:
            cv.set(x, y, SOIL[3])
            continue
        k = (y - y1) % 7
        c = A.tint(SOIL[1], (A.fbm(x, y, 2.5, 363, 2) - 0.5) * 0.3)
        if k == 2:
            c = A.light(c, 0.22)                                    # a ridge's top
        elif k == 3:
            c = A.light(c, 0.08)
        elif k in (5, 6):
            c = A.dark(c, 0.25)                                     # the furrow between
        if k == 1 and A.rnd2(x, y, 365) < 0.18:
            c = hexc("6FC24A") if A.rnd2(x, y, 367) < 0.5 else hexc("4E9E38")   # a sprout
        cv.set(x, y, c)


def plot(cv, x, y):
    T.slot(cv, x, y, THEME, rim=(hexc("8A6A48"), SOIL[1], SOIL[3]), inside=(hexc("3A2A1A"), hexc("1E140C")))


def sack(cv, x, y):
    body = A.union(A.box(x - 5, y - 2, x + 21, y + 19, 6), A.circle(x + 8, y - 3, 4))
    area = (x - 6, y - 8, x + 22, y + 20)
    A.shadow(cv, body, area, dx=1, dy=2, alpha=110, soft=1.5)
    A.draw(cv, body, area, lambda px, py, d: A.lerp(hexc("C8B088"), hexc("A8905E"), 0.5 + 0.5 * math.sin(px * 1.3) * math.sin(py * 1.3)),
           outline=hexc("4A3A20"), bevel=1.5, hi=0.35, lo=0.35)
    for k in range(-3, 4):
        cv.set(x + 8 + k, y - 4, hexc("6A5230"))
    T.slot(cv, x, y, THEME, rim=(hexc("E0CCA4"), hexc("A8905E"), hexc("4A3A20")), inside=(hexc("4A3A26"), hexc("261C10")))


def rain_gauge(cv):
    """A rain gauge: a tall glass cylinder marked up its side, a funnel on top, in a wooden stand (the screen fills
    it with mana)."""
    cx, cy, hw, hh = FIELD.TUBE
    top, bottom = cy - hh, cy + hh
    # the stand: two posts and a foot
    for px in (cx - hw - 4, cx + hw + 3):
        for y in range(top + 6, bottom + 6):
            cv.set(px, y, STAVE[3])
            cv.set(px + 1, y, STAVE[1] if px < cx else STAVE[2])
    foot = A.box(cx - hw - 7, bottom + 3, cx + hw + 7, bottom + 8, 2)
    A.draw(cv, foot, (cx - hw - 7, bottom + 3, cx + hw + 7, bottom + 8), STAVE[1], outline=STAVE[4], bevel=1.2, hi=0.4, lo=0.4)
    for y in range(top, bottom + 1):
        for x in range(cx - hw, cx + hw):
            edge = min(x - (cx - hw), cx + hw - 1 - x)
            if edge == 0 or y == bottom:
                c = hexc("2A3A40")
            elif edge == 1:
                c = hexc("E8FAFF") if x < cx else hexc("90B4C0")
            else:
                c = A.lerp(hexc("24343C"), hexc("141E24"), (y - top) / float(bottom - top))
                c = A.lerp(c, wheat(x, y), 0.25)
            cv.set(x, y, c)
    # marks up its side, longer every fifth
    for k, y in enumerate(range(bottom - 3, top + 2, -5)):
        for x in range(cx + hw - 4, cx + hw - 1 if k % 2 else cx + hw - 6 + 5):
            cv.set(x if k % 2 else x - 2, y, hexc("F4FFFF"))
    # the funnel
    for j in range(6):
        w = hw + 4 - j
        for x in range(cx - w, cx + w):
            cv.set(x, top - 6 + j, hexc("2A3A40") if x in (cx - w, cx + w - 1) or j == 0 else (hexc("C8E8F0") if x < cx else hexc("8AB0BC")))


def crate(cv):
    """A crate of planks for the harvest: three rows of slots, the planks' ends nailed."""
    x1, y1, x2, y2 = FIELD.CRATE
    body = A.box(x1, y1, x2, y2, 2)
    A.shadow(cv, body, (x1, y1, x2, y2), dx=2, dy=3, alpha=140, soft=2.0)
    for x, y, depth in A.bounds(body, x1, y1, x2, y2):
        k = A.facing(body, x + 0.5, y + 0.5)
        if depth <= 1.0:
            c = FENCE[4]
        elif depth <= 4.0:
            c = A.tint(FENCE[1], 0.2 * k)
            if (x - x1) % 12 == 0 or (y - y1) % 12 == 0:
                c = FENCE[3]
        else:
            c = hexc("2A1C10")
        cv.set(x, y, c)
    for (nx, ny) in ((x1 + 2, y1 + 2), (x2 - 3, y1 + 2), (x1 + 2, y2 - 3), (x2 - 3, y2 - 3)):
        cv.set(nx, ny, IRON[1])
    for (x, y) in FIELD.OUTPUTS:
        T.slot(cv, x, y, THEME, rim=(FENCE[0], FENCE[2], FENCE[4]), inside=(hexc("3A2814"), hexc("1E140A")))


def scarecrow(cv):
    """A scarecrow in a straw hat on a post, arms out, keeping watch over the field."""
    sx, sy = FIELD.SCARECROW
    for y in range(sy + 6, sy + 38):
        cv.set(sx, y, FENCE[3])
        cv.set(sx + 1, y, FENCE[2])
    for x in range(sx - 10, sx + 12):
        cv.set(x, sy + 13, FENCE[3])
        cv.set(x, sy + 14, FENCE[2])
    shirt = A.box(sx - 5, sy + 11, sx + 7, sy + 24, 2)
    A.draw(cv, shirt, (sx - 5, sy + 11, sx + 7, sy + 24), lambda x, y, d: hexc("8A4A6A") if (x // 2 + y // 2) % 2 else hexc("6A3450"),
           outline=hexc("2A1420"), bevel=1.0, hi=0.3, lo=0.3)
    head = A.circle(sx + 1, sy + 7, 3.6)
    A.draw(cv, head, (sx - 4, sy + 2, sx + 6, sy + 12), hexc("E8D4A0"), outline=hexc("5A4A2A"), bevel=1.0, hi=0.3, lo=0.3)
    cv.set(sx, sy + 6, hexc("2A1A0A"))
    cv.set(sx + 2, sy + 6, hexc("2A1A0A"))
    for x in range(sx - 5, sx + 8):
        cv.set(x, sy + 3, WHEAT[2])
        cv.set(x, sy + 4, WHEAT[3])
    for x in range(sx - 2, sx + 5):
        cv.set(x, sy + 1, WHEAT[1])
        cv.set(x, sy + 2, WHEAT[2])
    for (hx, hy) in ((sx - 11, sy + 15), (sx + 12, sy + 15), (sx - 4, sy + 25), (sx + 6, sy + 25)):
        cv.set(hx, hy, WHEAT[1])
        cv.set(hx, hy + 1, WHEAT[2])


def gem_socket(cv, gx, gy):
    f = A.circle(gx + 0.5, gy + 0.5, 4.8)
    for x, y, d in A.bounds(f, gx - 6, gy - 6, gx + 7, gy + 7):
        k = A.facing(f, x + 0.5, y + 0.5)
        cv.set(x, y, FENCE[4] if d <= 1.0 else ((G[1] if k > 0.2 else (G[3] if k < -0.3 else G[2])) if d <= 2.0 else hexc("1A120A")))


# ---------------------------------------------------------------- the panel, the sheet, the gloss

def panel():
    cv = T.new_panel()
    T.inventory(cv, THEME)
    T.panel(cv, 0, 0, W, MACHINE_H, THEME, background)
    sun(cv)
    scarecrow(cv)
    for (x1, y1, x2, y2) in FIELD.FURROWS:
        furrow(cv, x1, y1, x2, y2)
    for (x, y) in FIELD.INPUTS:
        plot(cv, x, y)
    sack(cv, *FIELD.FERTILIZER)
    rain_gauge(cv)
    crate(cv)
    gem_socket(cv, *FIELD.GEM)
    px, py = FIELD.POOL
    sock = A.box(px - 1, py - 1, px + 17, py + 17, 3)
    A.draw(cv, sock, (px - 1, py - 1, px + 17, py + 17), SOIL[1], outline=FENCE[4], bevel=1.5, hi=0.3, lo=0.4, sunk=True)
    for (x, y) in FIELD.LIGHTS:
        T.corner_gem(cv, x, y, THEME)
    return cv


SHEET_REGIONS = T.SHARED_REGIONS + FIELD.SHEET


def sheet():
    cv = T.new_sheet()
    T.shared_pieces(cv, THEME)
    pool_lamp(cv, False, *T.POOL_UV)
    pool_lamp(cv, True, T.POOL_UV[0] + T.POOL_SIZE, T.POOL_UV[1])
    return cv


def pool_lamp(cv, lit, x0, y0):
    """The pool lamp: a little wooden tub, mana in it when a pool is beside the machine."""
    rim = A.ellipse(x0 + 8, y0 + 9.5, 7.2, 4.8)
    for x, y, d in A.bounds(rim, x0, y0, x0 + 16, y0 + 16):
        k = A.facing(rim, x + 0.5, y + 0.5)
        if d <= 1.0:
            c = STAVE[4]
        elif d <= 2.6:
            c = STAVE[0] if k > 0.3 else (STAVE[3] if k < -0.3 else STAVE[1])
        else:
            c = (MANA[1] if y < y0 + 9 else MANA[3]) if lit else (hexc("2A3238") if y < y0 + 9 else hexc("1A2026"))
        cv.set(x, y, c)
    if lit:
        cv.set(x0 + 6, y0 + 8, (255, 255, 255))
        cv.set(x0 + 7, y0 + 8, MANA[0])


def gloss(panel_cv):
    """Where a shine sweeps over the panel: its gold, and the rain gauge's glass."""
    out = T.new_panel()
    for y in range(panel_cv.h):
        for x in range(panel_cv.w):
            p = panel_cv.px[x, y]
            if p[3] == 0:
                continue
            r, g, b = p[:3]
            gold = r > 170 and g > 110 and b < 130 and r - b > 90 and (y < 30 or y > 160 or x < 20 or x > 260)
            mx, my = x - 12, y - 12
            cx, cy, hw, hh = FIELD.TUBE
            glass = abs(mx + 0.5 - cx) < hw and abs(my + 0.5 - cy) < hh
            if gold or glass:
                out.px[x, y] = (255, 255, 255, min(255, int(70 + 160 * A.lum((r, g, b)))))
    return out
