"""The GUI's widget sheet (textures/gui/elven_portal_widgets.png, 256x128) and the portal's swirl for the
GUI (textures/gui/portal_swirl.png, 16 frames of 44x72 side by side).

Sheet layout (the screen, client/PortalScreen.java, reads it at these places):
  (0,0)    buttons 16x16: normal, hover, (32) pressed, (48) off
  (0,16)   icons 12x12: redstone ignore, high, low
  (104,0)  the red close button 16x16: normal, hover (120), pressed (136)
  (104,16) a soft halo 9x9 (white, fading to nothing, a hole in its middle), tinted gold by the screen for the lights' twinkle
  (160,0)  the lit arrow 12x9, and (172,0) the same brighter (its head)
  (160,16) the keystone's gem 7x7: trading, idle, no mana or full, stopped by redstone ((167,16), (174,16), (181,16))
  (192,0)  the pool light 16x16: no pool beside the portal, (208,0) a pool beside it
  (0,56)   the title scroll: left roller 16x20, (16,56) right roller 16x20, (32,56) and (40,56) tiles of paper 8x20
  (0,80)   the mana bar's fill 112x6
  (0,96)   the gate's eight runes lit, 5x5 each (in gen_gui.GATE_GLYPHS' order)
  (48,96)  a natura crystal 5x8: resting (dim), (53,96) afloat (lit)
"""
import math
import os

from pix import Canvas, ASSETS, mix, shade, hexc, rnd2
from style import OUT, N0, N1, N2, N3, N4, N5, G0, G1, G2, G3, G4, S2, Y1, Y2, Y3, P0, P1, P2, P3, P4, P5, \
    R0, R1, R2, R3, R4, M1, M2, M3, M4, LR1, LR3
import swirl as S

SHEET_W, SHEET_H = 256, 128
BUTTON_UV = (0, 0)
ICON_V = 16
CLOSE_UV = (104, 0)
CLOSE_SIZE = 16
GLOW_UV = (104, 16)
GLOW_SIZE = 9
ARROW_UV = (160, 0)
ARROW_W, ARROW_H = 12, 9
GEM_UV = (160, 16)
GEM_SIZE = 7
POOL_UV = (192, 0)
POOL_SIZE = 16
SCROLL_V = 56
SCROLL_H = 20
SCROLL_CAP = 16
SCROLL_TILE_U = 32
SCROLL_TILE_W = 8                    # two tiles side by side, the screen alternates them
SCROLL_PAPER = (3, 16)               # the paper's first and last row on the scroll pieces (the rollers reach further)
FILL_V = 80
FILL_W, FILL_H = 112, 6
RUNE_UV = (0, 96)
RUNE_SIZE = 5
CRYSTAL_UV = (48, 96)
CRYSTAL_W, CRYSTAL_H = 5, 8
# every piece of the sheet as (name, x, y, w, h): tools/check_layout.py makes sure none overlaps another
# (a piece reaching into its neighbour's pixels shows up in the game as stray dots)
SHEET_REGIONS = [
    ("buttons", 0, 0, 64, 16), ("icons", 0, 16, 36, 12), ("close", 104, 0, 48, 16), ("glow", 104, 16, 9, 9),
    ("arrows", 160, 0, 24, 9), ("gems", 160, 16, 28, 7), ("pool", 192, 0, 32, 16), ("scroll", 0, 56, 48, 20),
    ("fill", 0, 80, 112, 6), ("runes", 0, 96, 40, 5), ("crystals", 48, 96, 10, 8)]
# the GUI's swirl: frames of the portal inside the gate (as the panel's SWIRL), side by side
SWIRL_W, SWIRL_H, SWIRL_FRAMES = 46, 46, 16

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
}
ICON_PAL = {"r": RED1, "R": RED0, "w": hexc("FFFFFF"), "o": hexc("2A1206"), "b": LW1, "k": hexc("4A4A4A"),
            "K": hexc("7A7A7A"), "c": M4, "C": M2, "p": S2, "g": hexc("3E5039"), "G": hexc("7C8C78"), "y": LW0}


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
    """A soft round halo, white fading out to nothing (9x9), with a hole where the light's own cross is
    (that stays as painted in the panel): the screen tints it gold and pulses it."""
    for y in range(9):
        for x in range(9):
            d = math.hypot(x - 4, y - 4)
            if d < 1.05:
                continue
            a = max(0.0, 1.0 - (d - 1.0) / 3.0) ** 2.0
            if a > 0.02:
                cv.set(x0 + x, y0 + y, (255, 255, 255), int(a * 125))


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




# ---------------------------------------------------------------- the portal's own pieces

ARROW = ["##....##...",
         ".##....##..",
         "..##....##.",
         "...##....##",
         "....##....#",
         "...##....##",
         "..##....##.",
         ".##....##..",
         "##....##..."]


def arrow(cv, x0, y0, bright):
    """The lit arrow: the panel's double chevron in green light (bright: its head, as it flashes)."""
    for j, row in enumerate(ARROW):
        for i, ch in enumerate(row):
            if ch == "#":
                edge = j in (0, len(ARROW) - 1)
                cv.set(x0 + i, y0 + j, (G0 if bright else G1) if not edge else (G1 if bright else G2))


GEM_COLORS = [
    (hexc("F4FFE8"), G0, G2, G4),                          # trading: green
    (hexc("FFFBE6"), Y1, Y2, Y3),                          # idle: gold
    (hexc("FFE4DC"), R0, R2, R4),                          # no mana, or the outputs are full: red
    (hexc("C8C8D8"), hexc("8A8AA0"), hexc("5A5A70"), hexc("30303F")),   # stopped by redstone: grey
]


def gem(cv, x0, y0, colors):
    """A round gem 7x7, lit from the top-left."""
    glint, light, mid, dark = colors
    for y in range(7):
        for x in range(7):
            d = math.hypot(x + 0.5 - 3.5, y + 0.5 - 3.5)
            if d > 3.5:
                continue
            k = ((x + 0.5 - 3.5) * -0.62 + (y + 0.5 - 3.5) * -0.78) / 3.5
            c = dark if d > 2.8 else (light if k > 0.25 else (mid if k > -0.35 else dark))
            cv.set(x0 + x, y0 + y, c)
    cv.set(x0 + 2, y0 + 2, glint)


POOL = ["................",
        "................",
        "................",
        "................",
        ".......s........",
        "................",
        "..oooooooooooo..",
        ".oLCCCCCCCCCCLo.",
        ".oLccccccccccLo.",
        ".oLLLLLLLLLLLLo.",
        "..oLllllllllLo..",
        "..oLLLLLLLLLLo..",
        "...oLllllllLo...",
        "....oooooooo....",
        "................",
        "................"]


def pool(cv, x0, y0, lit):
    """A little mana pool: livingrock round bright mana (lit: a pool is beside the portal), or round dark."""
    pal = {"o": hexc("2C2820"), "L": LR1, "l": LR3,
           "C": M1 if lit else hexc("2A3550"), "c": M3 if lit else hexc("1C2440"), "s": hexc("FFFFFF") if lit else None}
    for j, row in enumerate(POOL):
        for i, ch in enumerate(row):
            if ch == "." or pal.get(ch) is None:
                continue
            cv.set(x0 + i, y0 + j, pal[ch])
    if lit:
        for (dx, dy) in ((6, 4), (8, 4), (7, 3), (7, 5)):
            cv.set(x0 + dx, y0 + dy, M1, 160)


def mana_fill(cv, x0, y0):
    """The mana bar's fill: Botania's mana blue, light at the top, a bright dash every 14 pixels."""
    rows = [M1, M2, M2, M3, M3, M4]
    for y in range(FILL_H):
        for x in range(FILL_W):
            c = rows[y]
            if y in (1, 2) and x % 14 in (5, 6):
                c = shade(c, 0.3)
            if x % 14 == 0 and y > 0:
                c = shade(c, -0.15)
            cv.set(x0 + x, y0 + y, c)


def lit_runes(cv):
    """The gate's runes, glowing: pale green strokes with a white heart where they cross."""
    from gen_gui import GATE_GLYPHS
    for k, g in enumerate(GATE_GLYPHS):
        x0 = RUNE_UV[0] + k * RUNE_SIZE
        for j, row in enumerate(g):
            for i, ch in enumerate(row):
                if ch != "#":
                    continue
                n = sum(1 for (di, dj) in ((1, 0), (-1, 0), (0, 1), (0, -1))
                        if 0 <= j + dj < 5 and 0 <= i + di < 5 and g[j + dj][i + di] == "#")
                cv.set(x0 + i, RUNE_UV[1] + j, hexc("F4FFE8") if n >= 3 else G0)


CRYSTAL = ["..o..",
           ".oho.",
           "olhmo",
           "olmdo",
           "olmdo",
           "omddo",
           ".odo.",
           "..o.."]


def crystals(cv):
    """A natura crystal, resting (dim) and afloat (lit)."""
    for i, lit in enumerate((False, True)):
        pal = {"o": hexc("0E3B22"), "h": hexc("F4FFE8") if lit else G1, "l": G0 if lit else G2, "m": G1 if lit else G3,
               "d": G2 if lit else G4}
        cv.sprite(CRYSTAL, CRYSTAL_UV[0] + i * CRYSTAL_W, CRYSTAL_UV[1], pal)


def widgets():
    cv = Canvas(SHEET_W, SHEET_H)
    for i, s in enumerate(("normal", "hover", "pressed", "off")):
        button(cv, BUTTON_UV[0] + i * 16, BUTTON_UV[1], s)
    for i, key in enumerate(("ignore", "high", "low")):
        cv.sprite(ICONS[key], i * 12, ICON_V, ICON_PAL)
    for i, s in enumerate(("normal", "hover", "pressed")):
        close_button(cv, CLOSE_UV[0] + i * CLOSE_SIZE, CLOSE_UV[1], s)
    glow(cv, *GLOW_UV)
    arrow(cv, ARROW_UV[0], ARROW_UV[1], False)
    arrow(cv, ARROW_UV[0] + ARROW_W, ARROW_UV[1], True)
    for i, colors in enumerate(GEM_COLORS):
        gem(cv, GEM_UV[0] + i * GEM_SIZE, GEM_UV[1], colors)
    pool(cv, POOL_UV[0], POOL_UV[1], False)
    pool(cv, POOL_UV[0] + POOL_SIZE, POOL_UV[1], True)
    scroll_cap(cv, 0, SCROLL_V, False)
    scroll_cap(cv, SCROLL_CAP, SCROLL_V, True)
    scroll_tile(cv, SCROLL_TILE_U, SCROLL_V, 0)
    scroll_tile(cv, SCROLL_TILE_U + SCROLL_TILE_W, SCROLL_V, 7)
    mana_fill(cv, 0, FILL_V)
    lit_runes(cv)
    crystals(cv)
    cv.save(os.path.join(ASSETS, "textures", "gui", "elven_portal_widgets.png"))
    return cv


def swirl_sheet():
    """The GUI's swirl, frame after frame: a round portal filling the ring of the gate."""
    cv = Canvas(SWIRL_W * SWIRL_FRAMES, SWIRL_H)
    for f in range(SWIRL_FRAMES):
        grid = S.swirl_frame(SWIRL_W, SWIRL_H, f / SWIRL_FRAMES, rx=SWIRL_W / 2.0 - 0.4, ry=SWIRL_H / 2.0 - 0.4, motes=8)
        S.put(cv, grid, f * SWIRL_W, 0)
    cv.save(os.path.join(ASSETS, "textures", "gui", "portal_swirl.png"))
    return cv


if __name__ == "__main__":
    widgets()
    swirl_sheet()
