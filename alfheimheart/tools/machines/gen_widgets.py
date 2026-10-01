"""The widget sheet every machine's GUI shares (textures/gui/machine_widgets.png, 256x128), in the living-wood
style.

    python3 tools/machines/gen_widgets.py

Sheet layout (machine/client/MachineScreen.java and the machines' screens read it at these places):
  (0,0)    buttons 16x16: normal, hover, (32) pressed, (48) off
  (0,16)   icons 12x12: redstone ignore, high, low
  (104,0)  the close button 16x16: normal, hover (120), pressed (136)
  (104,16) a soft halo 9x9 (white, fading out, a hole in its middle): the screens tint it
  (160,0)  the lit arrow 12x9, and (172,0) the same brighter (as it flashes)
  (160,16) the status gem 7x7: working, idle, no mana or full, stopped by redstone ((167,16), (174,16), (181,16))
  (192,0)  the pool light 16x16: no pool beside the machine, (208,0) a pool beside it
  (0,56)   the title plaque: left end 16x20, (16,56) right end 16x20, (32,56) and (40,56) tiles 8x20
  (0,80)   the mana bar's fill 112x6
  (0,96)   the Runic Altar's eight runes lit, 5x5 each (in gen_gui.ALTAR_GLYPHS' order)
  (48,96)  a big soft halo 15x15 (white, fading out): flashes and glowing orbs
"""
import math
import os
import sys

from pix import Canvas, ASSETS, mix, shade, hexc

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "lib"))
import wood as WD  # noqa: E402

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
SCROLL_CAP = 16
SCROLL_TILE_U = 32
SCROLL_TILE_W = 8
FILL_V = 80
FILL_W, FILL_H = 112, 6
RUNE_UV = (0, 96)
RUNE_SIZE = 5
HALO_UV = (48, 96)
HALO_SIZE = 15
# every piece of the sheet as (name, x, y, w, h): check_layout.py makes sure none overlaps another
SHEET_REGIONS = [
    ("buttons", 0, 0, 64, 16), ("icons", 0, 16, 36, 12), ("close", 104, 0, 48, 16), ("glow", 104, 16, 9, 9),
    ("arrows", 160, 0, 24, 9), ("gems", 160, 16, 28, 7), ("pool", 192, 0, 32, 16), ("plaque", 0, 56, 48, 20),
    ("fill", 0, 80, 112, 6), ("runes", 0, 96, 40, 5), ("halo", 48, 96, 15, 15)]

GREEN = [hexc(h) for h in ("F4FFE8", "DDF9A6", "A4EC5C", "62CC42", "37993C", "1E6534")]
MANA = WD.MANA
GOLD = WD.GOLD
RED = WD.RED
LR = WD.LR


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
ICON_PAL = {"r": RED[2], "R": RED[1], "w": hexc("FFFFFF"), "o": hexc("2A1206"), "b": WD.WOOD[3], "k": hexc("4A4A4A"),
            "K": hexc("7A7A7A")}

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
    """The lit arrow: the panel's double chevron in mana light (bright: as it flashes)."""
    for j, row in enumerate(ARROW):
        for i, ch in enumerate(row):
            if ch == "#":
                edge = j in (0, len(ARROW) - 1)
                cv.set(x0 + i, y0 + j, (MANA[0] if bright else MANA[1]) if not edge else (MANA[1] if bright else MANA[2]))


GEM_COLORS = [
    (GREEN[0], GREEN[2], GREEN[3], GREEN[5]),                             # working: green
    (GOLD[0], GOLD[1], GOLD[2], GOLD[3]),                                 # idle: gold
    (hexc("FFE4DC"), RED[0], RED[2], RED[4]),                             # no mana, or the outputs are full: red
    (hexc("C8C8D8"), hexc("8A8AA0"), hexc("5A5A70"), hexc("30303F")),     # stopped by redstone: grey
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
    """A little mana pool: livingrock round bright mana (lit: a pool is beside the machine), or round dark."""
    pal = {"o": hexc("2C2820"), "L": LR[1], "l": LR[3],
           "C": MANA[1] if lit else hexc("2A3550"), "c": MANA[3] if lit else hexc("1C2440"),
           "s": hexc("FFFFFF") if lit else None}
    for j, row in enumerate(POOL):
        for i, ch in enumerate(row):
            if ch == "." or pal.get(ch) is None:
                continue
            cv.set(x0 + i, y0 + j, pal[ch])
    if lit:
        for (dx, dy) in ((6, 4), (8, 4), (7, 3), (7, 5)):
            cv.set(x0 + dx, y0 + dy, MANA[1], 160)


def mana_fill(cv, x0, y0):
    """The mana bar's fill: Botania's mana blue, light at the top, a bright dash every 14 pixels."""
    rows = [MANA[1], MANA[2], MANA[2], MANA[3], MANA[3], MANA[4]]
    for y in range(FILL_H):
        for x in range(FILL_W):
            c = rows[y]
            if y in (1, 2) and x % 14 in (5, 6):
                c = shade(c, 0.3)
            if x % 14 == 0 and y > 0:
                c = shade(c, -0.15)
            cv.set(x0 + x, y0 + y, c)


def lit_runes(cv):
    """The Runic Altar's runes, glowing: pale mana strokes with a white heart where they cross."""
    from gen_gui import ALTAR_GLYPHS
    for k, g in enumerate(ALTAR_GLYPHS):
        x0 = RUNE_UV[0] + k * RUNE_SIZE
        for j, row in enumerate(g):
            for i, ch in enumerate(row):
                if ch != "#":
                    continue
                n = sum(1 for (di, dj) in ((1, 0), (-1, 0), (0, 1), (0, -1))
                        if 0 <= j + dj < 5 and 0 <= i + di < 5 and g[j + dj][i + di] == "#")
                cv.set(x0 + i, RUNE_UV[1] + j, MANA[0] if n >= 3 else MANA[1])


def halo(cv, x0, y0):
    """A big soft round halo, white fading out to nothing (15x15): the screens tint it for flashes and orbs."""
    c = (HALO_SIZE - 1) / 2.0
    for y in range(HALO_SIZE):
        for x in range(HALO_SIZE):
            d = math.hypot(x - c, y - c)
            a = max(0.0, 1.0 - d / (c + 0.5)) ** 1.6
            if a > 0.02:
                cv.set(x0 + x, y0 + y, (255, 255, 255), int(a * 235))


def widgets():
    cv = Canvas(SHEET_W, SHEET_H)
    for i, s in enumerate(("normal", "hover", "pressed", "off")):
        WD.button(cv, BUTTON_UV[0] + i * 16, BUTTON_UV[1], s)
    for i, key in enumerate(("ignore", "high", "low")):
        cv.sprite(ICONS[key], i * 12, ICON_V, ICON_PAL)
    for i, s in enumerate(("normal", "hover", "pressed")):
        WD.close_button(cv, CLOSE_UV[0] + i * CLOSE_SIZE, CLOSE_UV[1], s)
    WD.glow(cv, *GLOW_UV)
    arrow(cv, ARROW_UV[0], ARROW_UV[1], False)
    arrow(cv, ARROW_UV[0] + ARROW_W, ARROW_UV[1], True)
    for i, colors in enumerate(GEM_COLORS):
        gem(cv, GEM_UV[0] + i * GEM_SIZE, GEM_UV[1], colors)
    pool(cv, POOL_UV[0], POOL_UV[1], False)
    pool(cv, POOL_UV[0] + POOL_SIZE, POOL_UV[1], True)
    WD.plaque_cap(cv, 0, SCROLL_V, False)
    WD.plaque_cap(cv, SCROLL_CAP, SCROLL_V, True)
    WD.plaque_tile(cv, SCROLL_TILE_U, SCROLL_V, 0)
    WD.plaque_tile(cv, SCROLL_TILE_U + SCROLL_TILE_W, SCROLL_V, 7)
    mana_fill(cv, 0, FILL_V)
    lit_runes(cv)
    halo(cv, *HALO_UV)
    cv.save(os.path.join(ASSETS, "textures", "gui", "machine_widgets.png"))
    return cv


if __name__ == "__main__":
    widgets()
