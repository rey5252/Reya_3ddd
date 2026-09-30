"""The GUI's widget sheet (textures/gui/greenhouse_widgets.png, 256x128) and the opening bloom
(textures/gui/greenhouse_bloom.png, 8 frames of 64x64).

Sheet layout (the screen, client/GreenhouseScreen.java, reads it at these places):
  (0,0)    buttons 16x16: normal, hover, (32) pressed, (48) off
  (0,16)   icons 12x12: redstone ignore, high, low, output on, output off, output bound
  (64,0)   the heart's glass shine, 38x38
  (104,0)  the red close button 16x16: normal, hover (120), pressed (136)
  (104,16) a soft glow 9x9 (white, fading to nothing), tinted gold by the screen for the lights' twinkle
  (0,56)   the title scroll: left roller 16x20, (16,56) right roller 16x20, (32,56) and (40,56) tiles of paper 8x20
  (0,80)   growth bar fill 152x6 (growing); (0,88) the same, paused
"""
import math
import os

from pix import Canvas, ASSETS, mix, shade, hexc, rnd, rnd2
from style import OUT, VOUT, N0, N1, N2, N3, N4, N5, G0, G1, G2, G3, G4, G5, S0, S1, S2, S3, S4, Y0, Y1, Y2, Y3, Y4, \
    P0, P1, P2, P3, P4, P5, R0, R1, R2, R3, R4, M0, M1, M2, M3, M4, M5
from sprites import PAL

SHEET_W, SHEET_H = 256, 128
BUTTON_UV = (0, 0)
ICON_V = 16
SHINE_UV = (64, 0)
CLOSE_UV = (104, 0)
CLOSE_SIZE = 16
GLOW_UV = (104, 16)
GLOW_SIZE = 9
SCROLL_V = 56
SCROLL_H = 20
SCROLL_CAP = 16
SCROLL_TILE_U = 32
SCROLL_TILE_W = 8                    # two tiles side by side, the screen alternates them
SCROLL_PAPER = (3, 16)               # the paper's first and last row on the scroll pieces (the rollers reach further)
FILL_V = 80

# Botania's blues for the mana icons, the pinks of the bloom
LW0, LW1 = hexc("A2512A"), hexc("7A3314")
PK0, PK1, PK2, PK3, PK4 = hexc("FFF0F8"), hexc("FFC4E2"), hexc("EE8FC2"), hexc("C45A97"), hexc("6E2350")
RED0, RED1, RED2 = hexc("FF5A4A"), hexc("C8281C"), hexc("6A0E08")


# ---------------------------------------------------------------- buttons

def button(cv, x, y, state):
    """A navy square with a bevelled green rim; hover is brighter, pressed sunk, off grey-green."""
    face_top = {"normal": N1, "hover": N0, "pressed": N4, "off": hexc("1A2238")}[state]
    face_bot = {"normal": N3, "hover": N2, "pressed": N5, "off": hexc("121828")}[state]
    hi = {"normal": G1, "hover": G0, "pressed": G3, "off": hexc("7E9078")}[state]
    mid = {"normal": G2, "hover": G1, "pressed": G3, "off": hexc("5C6E58")}[state]
    lo = {"normal": G3, "hover": G2, "pressed": G1, "off": hexc("3E4C3C")}[state]
    if state == "pressed":
        hi, lo = lo, hi
    cv.rect(x, y, x + 16, y + 16, mid)
    cv.hline(x, x + 16, y, hi)
    cv.vline(x, y, y + 16, hi)
    cv.hline(x, x + 16, y + 15, lo)
    cv.vline(x + 15, y, y + 16, lo)
    cv.set(x, y + 15, mid)
    cv.set(x + 15, y, mid)
    for yy in range(y + 1, y + 15):
        cv.hline(x + 1, x + 15, yy, mix(face_top, face_bot, (yy - y - 1) / 13.0))
    cv.hline(x + 1, x + 15, y + 1, shade(face_top, -0.5))
    cv.vline(x + 1, y + 1, y + 15, shade(face_top, -0.5))
    cv.hline(x + 2, x + 15, y + 14, mix(face_bot, hi, 0.14))
    cv.vline(x + 14, y + 2, y + 15, mix(face_bot, hi, 0.14))
    # rounded corners
    for (cx, cy) in ((x, y), (x + 15, y), (x, y + 15), (x + 15, y + 15)):
        cv.clear(cx, cy)
    for (cx, cy) in ((x + 1, y), (x, y + 1), (x + 14, y), (x + 15, y + 1), (x, y + 14), (x + 1, y + 15), (x + 14, y + 15), (x + 15, y + 14)):
        cv.set(cx, cy, shade(mid, -0.5))


ICONS = {
    # redstone dust always glowing: works whatever
    "ignore": ["............",
               "....rr......",
               "...rRRr.rr..",
               "..rRwRrrRRr.",
               "...rRRRRRr..",
               "..rrRRrRr...",
               ".rRRr..rRr..",
               ".rRRRr.rRRr.",
               "..rrRRrrRr..",
               "....rRRr....",
               ".....rr.....",
               "............"],
    # a lit redstone torch: works with a signal
    "high": ["....o.......",
             "...oRo......",
             "..oRwRo.....",
             "...oRo......",
             "...obo......",
             "...obo......",
             "...obo......",
             "...obo......",
             "...obo......",
             "...ooo......",
             "............",
             "............"],
    # an unlit torch: works without a signal
    "low": ["....o.......",
            "...oko......",
            "..okKko.....",
            "...oko......",
            "...obo......",
            "...obo......",
            "...obo......",
            "...obo......",
            "...obo......",
            "...ooo......",
            "............",
            "............"],
    # mana flowing out into a pool
    "out_on": ["............",
               ".....cc.....",
               "....cCCc....",
               "....cCCc....",
               "..c.cCCc.c..",
               "...cCCCCc...",
               "....cCCc....",
               ".....cc.....",
               ".pppppppppp.",
               ".pCCCCCCCCp.",
               "..pppppppp..",
               "............"],
    "out_off": ["............",
                ".....gg.....",
                "....gGGg....",
                "....gGGg....",
                "..g.gGGg.g..",
                "...gGGGGg...",
                "....gGGg....",
                ".....gg.....",
                ".pppppppppp.",
                ".p........p.",
                "..pppppppp..",
                "............"],
    # bound: the flow with a little wand
    "out_bound": ["..........w.",
                  ".....cc..wy.",
                  "....cCCcwy..",
                  "....cCCy....",
                  "..c.cCyc.c..",
                  "...cCyCCc...",
                  "....yCCc....",
                  "...y.cc.....",
                  ".pppppppppp.",
                  ".pCCCCCCCCp.",
                  "..pppppppp..",
                  "............"],
}
ICON_PAL = {"r": RED1, "R": RED0, "w": hexc("FFFFFF"), "o": hexc("2A1206"), "b": LW1, "k": hexc("4A4A4A"),
            "K": hexc("7A7A7A"), "c": M4, "C": M2, "p": S2, "g": hexc("3E5039"), "G": hexc("7C8C78"), "y": LW0}


def glass_shine(cv, x0, y0):
    """The heart's glass: a bright curved streak at the top-left and a small glint (38x38)."""
    r = 17.0
    cx, cy = x0 + 19, y0 + 19
    for k in range(40):
        a = math.radians(196 + k * 1.9)
        for rr, al in ((r - 3.0, 150), (r - 4.0, 70)):
            x, y = cx + math.cos(a) * rr, cy + math.sin(a) * rr
            cv.set(int(math.floor(x)), int(math.floor(y)), (230, 255, 255), al)
    for k in range(10):
        a = math.radians(20 + k * 3)
        x, y = cx + math.cos(a) * (r - 3), cy + math.sin(a) * (r - 3)
        cv.set(int(math.floor(x)), int(math.floor(y)), (200, 240, 255), 60)
    cv.set(cx - 8, cy - 10, (255, 255, 255), 230)
    cv.set(cx - 9, cy - 9, (255, 255, 255), 150)
    cv.set(cx - 7, cy - 10, (255, 255, 255), 110)


# ---------------------------------------------------------------- the close button

CROSS = ["XX....XX",
         "XXX..XXX",
         ".XXXXXX.",
         "..XXXX..",
         "..XXXX..",
         ".XXXXXX.",
         "XXX..XXX",
         "XX....XX"]


def close_button(cv, x0, y0, state):
    """A round red button with a white X (16x16): hover is brighter with a gold rim, pressed darker with
    the X pushed a pixel in."""
    r = 7.5
    cx, cy = x0 + 8, y0 + 8
    face = {"normal": (R0, R1, R2), "hover": (hexc("FFC8B8"), R0, R1), "pressed": (R1, R2, R3)}[state]
    for y in range(y0, y0 + 16):
        for x in range(x0, x0 + 16):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(px, py)
            if d > r:
                continue
            if d > r - 1:
                cv.set(x, y, OUT if state != "hover" else Y3)
                continue
            k = (px * -0.62 + py * -0.78) / (d + 1e-6)
            if d > r - 2:
                # the rim, lit from the top-left
                cv.set(x, y, face[0] if k > 0.2 else (face[2] if k < -0.3 else face[1]))
                continue
            k = (px * -0.62 + py * -0.78) / 6.0
            cv.set(x, y, mix(face[1], face[0], max(0.0, k) * 1.4) if k > 0 else mix(face[1], face[2], -k * 1.3))
    # the white X with a dark red shadow
    off = 1 if state == "pressed" else 0
    for shadow in (True, False):
        col = R4 if shadow else hexc("FFFFFF")
        d = off + (1 if shadow else 0)
        for j, row in enumerate(CROSS):
            for i, ch in enumerate(row):
                if ch == "X":
                    cv.set(x0 + 4 + i + d - 1, y0 + 4 + j + d - 1, col)
    if state == "hover":
        cv.set(x0 + 3, y0 + 3, hexc("FFFFFF"))


def glow(cv, x0, y0):
    """A soft round glow, white fading out to nothing (9x9): the screen tints it gold and pulses it."""
    c = 4.0
    for y in range(9):
        for x in range(9):
            d = math.hypot(x + 0.5 - c - 0.5, y + 0.5 - c - 0.5)
            a = max(0.0, 1.0 - d / 4.4) ** 1.6
            if a > 0.02:
                cv.set(x0 + x, y0 + y, (255, 255, 255), int(a * 255))


# ---------------------------------------------------------------- the title scroll

ROLLER = [P5, P1, P2, P3, P4, P4, P5]          # a roller's columns, lit from the left


def paper_shade(j, h):
    """The paper's colour on its row j of h+1 (0 and h are the outline)."""
    if j == 0 or j == h:
        return P5
    if j == 1:
        return P0
    if j == h - 1:
        return P4
    if j == h - 2:
        return P3
    return mix(P1, P2, (j - 2) / float(h - 5))


def scroll_cap(cv, x0, y0, right):
    """A roller (7 wide, the full 20 high, round at both ends) with the paper curling off it (9 wide).
    The left piece has the roller at its left, the right piece at its right; the light is from the left
    on both."""
    rx = x0 + (9 if right else 0)
    for i in range(7):
        for y in range(20):
            inset = 2 if y in (0, 19) else (1 if y in (1, 18) else 0)
            if i < inset or i > 6 - inset:
                continue
            c = ROLLER[i]
            if y in (0, 19) or (y in (1, 18) and i in (inset, 6 - inset)):
                c = P5
            elif y in (1, 18):
                c = P1 if i < 3 else P3
            cv.set(rx + i, y0 + y, c)
    top, bottom = SCROLL_PAPER
    h = bottom - top
    for k in range(9):
        x = x0 + (k if right else 7 + k)
        depth = (8 - k) if right else k            # 0 is next to the roller
        shadow = (0.6, 0.38, 0.22, 0.12, 0.05, 0.0, 0.0, 0.0, 0.0)[depth]
        for j in range(h + 1):
            c = paper_shade(j, h)
            if 1 < j < h:
                c = mix(c, P4, shadow)
            cv.set(x, y0 + top + j, c)


def scroll_tile(cv, x0, y0, salt):
    """8 columns of paper with a few fibres, tiled across the scroll by the screen (two variants)."""
    top, bottom = SCROLL_PAPER
    for i in range(SCROLL_TILE_W):
        for j in range(bottom - top + 1):
            cv.set(x0 + i, y0 + top + j, paper_shade(j, bottom - top))
        for j in range(3, bottom - top - 3):
            if rnd2(i, j, 41 + salt) < 0.09:
                cv.set(x0 + i, y0 + top + j, mix(P1, P3, 0.45 if rnd2(i, j, 43 + salt) < 0.5 else 0.25))


# ---------------------------------------------------------------- bar fill

def growth_fill(cv, x0, y0, paused):
    """The growth bar's fill: a bright green gradient with little bright dashes along it."""
    w, h = 152, 6
    for y in range(h):
        base = [G0, G1, G1, G2, G3, G4][y] if not paused else [hexc("B8C4A8"), hexc("98A888"), hexc("98A888"), hexc("7C8C6C"), hexc("5E6E50"), hexc("465238")][y]
        for x in range(w):
            c = base
            if not paused and y in (2, 3) and (x % 12) in (4, 5):
                c = shade(base, 0.25)
            if (x % 12) == 0 and y > 0:
                c = shade(base, -0.12)
            cv.set(x0 + x, y0 + y, c)


def widgets():
    cv = Canvas(SHEET_W, SHEET_H)
    for i, s in enumerate(("normal", "hover", "pressed", "off")):
        button(cv, BUTTON_UV[0] + i * 16, BUTTON_UV[1], s)
    for i, key in enumerate(("ignore", "high", "low", "out_on", "out_off", "out_bound")):
        cv.sprite(ICONS[key], i * 12, ICON_V, ICON_PAL)
    glass_shine(cv, *SHINE_UV)
    for i, s in enumerate(("normal", "hover", "pressed")):
        close_button(cv, CLOSE_UV[0] + i * CLOSE_SIZE, CLOSE_UV[1], s)
    glow(cv, *GLOW_UV)
    scroll_cap(cv, 0, SCROLL_V, False)
    scroll_cap(cv, SCROLL_CAP, SCROLL_V, True)
    scroll_tile(cv, SCROLL_TILE_U, SCROLL_V, 0)
    scroll_tile(cv, SCROLL_TILE_U + SCROLL_TILE_W, SCROLL_V, 7)
    growth_fill(cv, 0, FILL_V, False)
    growth_fill(cv, 0, FILL_V + 8, True)
    cv.save(os.path.join(ASSETS, "textures", "gui", "greenhouse_widgets.png"))
    return cv


def petal_pixels(cx, cy, angle, length, width, start=0.0):
    """A teardrop petal from near the middle outwards: widest at 60% of its length, pointed tip."""
    pix = set()
    ca, sa = math.cos(angle), math.sin(angle)
    steps = int(length * 3) + 2
    for i in range(steps + 1):
        t = i / steps
        d = start + length * t
        hw = width * (math.sin(math.pi * min(1.0, t / 0.62) * 0.5) if t < 0.62 else math.cos((t - 0.62) / 0.38 * math.pi / 2) ** 0.8)
        px, py = cx + ca * d, cy + sa * d
        n = int(hw * 3) + 1
        for j in range(-n, n + 1):
            o = hw * j / max(1, n)
            pix.add((int(math.floor(px - sa * o)), int(math.floor(py + ca * o))))
    return pix


def bloom():
    """The opening: a green bud that swells and blooms into a pink mana lotus (8 frames of 64x64), seen
    from above; the heart glows mana-blue as it opens."""
    from shapes import shade_shape, PETAL_PINK, PETAL_WHITE, LEAF
    frames = 8
    cv = Canvas(64 * frames, 64)
    for f in range(frames):
        t = f / (frames - 1)
        ease = 1 - (1 - t) ** 2
        cx, cy = 64 * f + 32, 32
        # sepals: green leaves under everything, opening first
        n_sep = 8
        sep_len = 6 + 22 * min(1.0, ease * 1.2)
        for k in range(n_sep):
            a = -math.pi / 2 + (k + 0.5) * math.tau / n_sep + 0.15 * (1 - t)
            shade_shape(cv, petal_pixels(cx, cy, a, sep_len, 1.6 + 1.8 * ease), LEAF)
        # outer petals: pink, 8 of them
        if t > 0.05:
            n = 8
            ln = 4 + 20 * ease
            wd = 2.0 + 4.6 * ease
            for k in range(n):
                a = -math.pi / 2 + k * math.tau / n + 0.35 * (1 - ease)
                shade_shape(cv, petal_pixels(cx, cy, a, ln, wd, start=1.0), PETAL_PINK)
        # inner petals: white, between the outer ones, a little shorter
        if t > 0.25:
            n = 8
            e2 = (t - 0.25) / 0.75
            e2 = 1 - (1 - e2) ** 2
            ln = 3 + 14 * e2
            wd = 1.6 + 3.8 * e2
            for k in range(n):
                a = -math.pi / 2 + (k + 0.5) * math.tau / n + 0.3 * (1 - e2)
                shade_shape(cv, petal_pixels(cx, cy, a, ln, wd, start=1.0), PETAL_WHITE, soft=True)
        # the heart: a bud tip at first, then a glowing mana pearl ringed with golden stamens
        r = 2.0 + 3.5 * ease
        glow_px = set()
        for y in range(int(cy - r) - 2, int(cy + r) + 3):
            for x in range(int(cx - r) - 2, int(cx + r) + 3):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= r:
                    glow_px.add((x, y))
                    c = M0 if d < r * 0.35 else (M1 if d < r * 0.7 else M2)
                    if t < 0.2:
                        c = PK1 if d < r * 0.6 else PK2
                    cv.set(x, y, c)
        if t >= 0.4:
            for k in range(10):
                a = k / 10 * math.tau + 0.3
                x, y = cx + math.cos(a) * (r + 1.2), cy + math.sin(a) * (r + 1.2)
                cv.set(int(math.floor(x)), int(math.floor(y)), hexc("FFD84A") if k % 2 == 0 else hexc("FFF4A0"))
        cv.set(cx - 2, cy - 2, (255, 255, 255))
    cv.save(os.path.join(ASSETS, "textures", "gui", "greenhouse_bloom.png"))
    return cv


if __name__ == "__main__":
    widgets()
    bloom()
