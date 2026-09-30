"""The mod's logo for the Mods list (META-INF/mods.toml logoFile): the elven moon gate with the portal
swirling inside it, standing at the edge of a navy panel in the GUI's style (the green edge, curly vines,
gold lights) that reads ELVEN PORTAL, with an elementium ingot and a dragonstone floating out of the swirl.

    python3 tools/gen_logo.py [--preview]

Forge draws the logo fitted into a box 50 GUI pixels high, so it is exactly 50 high: every pixel of it
lands on whole screen pixels at any GUI scale.
"""
import math
import os
import sys

from PIL import Image

from pix import Canvas, ROOT, mix, shade, hexc, rnd2, upscale
from style import *
import vines as V
import swirl as S
import gen_gui as G

W, H = 200, 50
PANEL = (30, 5, 193, 50)            # x1, y1, x2, y2 of the panel (its bottom edge is the logo's; vines round the rest)
GATE_MIDDLE = (24, 23)              # the moon gate's middle, and its ring's outer radius and the portal's inside it
GATE_R = (21, 13)
GEM = (24, 6)
LIGHTS = [(167, 1), (196, 27), (182, 1), (198, 12)]

# the plate letters (as client/PlateFont.java), 5 rows each
GLYPHS = {
    "A": [".XX.", "X..X", "XXXX", "X..X", "X..X"], "E": ["XXXX", "X...", "XXX.", "X...", "XXXX"],
    "L": ["X...", "X...", "X...", "X...", "XXXX"], "N": ["X..X", "XX.X", "X.XX", "X..X", "X..X"],
    "O": [".XX.", "X..X", "X..X", "X..X", ".XX."], "P": ["XXX.", "X..X", "XXX.", "X...", "X..."],
    "R": ["XXX.", "X..X", "XXX.", "X.X.", "X..X"], "T": ["XXXXX", "..X..", "..X..", "..X..", "..X.."],
    "V": ["X...X", "X...X", ".X.X.", ".X.X.", "..X.."],
}


def text_pixels(text, scale):
    """The set of pixels of the text at a scale, from (0, 0), and its width."""
    pix = set()
    x = 0
    for ch in text:
        g = GLYPHS[ch]
        for j, row in enumerate(g):
            for i, c in enumerate(row):
                if c == "X":
                    for dy in range(scale):
                        for dx in range(scale):
                            pix.add((x + i * scale + dx, j * scale + dy))
        x += (len(g[0]) + 1) * scale
    return pix, x - scale


def draw_title(cv, text, x0, y0, scale, ramp, edge):
    """Big letters: a vertical colour ramp, a dark outline all round and a shadow under it."""
    pix, _ = text_pixels(text, scale)
    h = 5 * scale
    ring = set()
    for (x, y) in pix:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if (x + dx, y + dy) not in pix:
                    ring.add((x + dx, y + dy))
    for (x, y) in ring:
        cv.set(x0 + x + 1, y0 + y + 2, (0, 0, 0), 90)
    for (x, y) in ring:
        cv.set(x0 + x, y0 + y, edge)
    for (x, y) in pix:
        t = y / (h - 1)
        c = ramp[min(len(ramp) - 1, int(t * len(ramp)))]
        if (x, y - 1) not in pix:
            c = shade(c, 0.35)          # a lit top edge on every stroke
        cv.set(x0 + x, y0 + y, c)


# ---------------------------------------------------------------- the panel

def panel(cv):
    x1, y1, x2, y2 = PANEL
    G.panel_shape(cv, x1, y1, x2, y2, lambda x, y: mix(N2, N4, ((y - y1) / float(y2 - y1)) ** 0.9))
    # a faint rune ring round the arch's side of the panel, and a few stars
    for y in range(y1 + 5, y2 - 5):
        for x in range(x1 + 5, x2 - 5):
            r = math.hypot(x - 23, (y - 27) * 1.05)
            if abs(r - 40.5) < 0.55:
                cv.set(x, y, G.PATTERN)
            elif rnd2(x, y, 77) < 0.006:
                cv.set(x, y, mix(N1, Y1, 0.35))


def vines(cv):
    """Vines on the panel's right corners, as on the GUI's (mirrored from the top-left corner's)."""
    x1, y1, x2, y2 = PANEL
    pw, ph = x2 - x1, y2 - y1
    specs = (G.edge_arm(-3, -3.4, 0.0, 34, -1, 2) + G.edge_arm(-3.4, -3, math.pi / 2, 22, 1, 5) + G.corner_cluster())
    V.paint_specs(cv, specs, VOUT, fx=True, cx=pw / 2.0, dx=x1, dy=y1)


# ---------------------------------------------------------------- the gate

def gate(cv):
    """The moon gate, as in the GUI: the portal swirling in a gold-rimmed stone ring, its gem lit, on a
    two-step pedestal, a vine climbing its lower left."""
    cx, cy = GATE_MIDDLE
    ro, ri = GATE_R
    G.gate_void(cv, cx, cy, ri + 0.5)
    n = 2 * ri
    S.put(cv, S.swirl_frame(n, n, 0.35, rx=ri - 0.3, ry=ri - 0.3, motes=5, seed=3), cx - ri, cy - ri)
    V.paint_specs(cv, G.arc_arm(cx, cy, ro + 1.5, math.radians(100), 22, 3), VOUT)
    G.gate_ring(cv, cx, cy, ro, ri)
    G.pedestal(cv, cx, H, 28, 38, step_h=4)
    gx, gy = GEM
    G.gate_gem_socket(cv, gx, gy)
    for dy in range(-3, 4):
        for dx in range(-3, 4):
            d = math.hypot(dx, dy)
            if d > 3.5:
                continue
            k = (dx * -0.62 + dy * -0.78) / 3.5
            cv.set(gx + dx, gy + dy, G4 if d > 2.8 else (G0 if k > 0.25 else (G2 if k > -0.35 else G4)))
    cv.set(gx - 1, gy - 1, hexc("F4FFE8"))


# ---------------------------------------------------------------- what the elves send back

INGOT = ["....oooooo..",
         "...ohhhhlo..",
         "..ohllllmo..",
         ".ohllllmdo..",
         "ooooooodo...",
         "olmmmmmdo...",
         "odddddddo...",
         "oooooooo...."]
GEMSTONE = ["..ooooo..",
            ".ohhllmo.",
            "ohllllmdo",
            "olllmmmdo",
            ".olmmmdo.",
            "..olmdo..",
            "...odo...",
            "....o...."]
SPARK = ["..w..", ".wWw.", "wWWWw", ".wWw.", "..w.."]


def sprite(cv, rows, x0, y0, pal):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in pal:
                cv.set(x0 + i, y0 + j, pal[ch])


def gifts(cv):
    pink = {"o": hexc("4A1030"), "h": hexc("FFE0F3"), "l": hexc("FF9AD8"), "m": hexc("E560B0"), "d": hexc("A0306E")}
    sprite(cv, INGOT, 160, 31, pink)
    sprite(cv, GEMSTONE, 172, 15, {"o": hexc("3A0A2A"), "h": hexc("FFF0F8"), "l": hexc("FF7AC4"), "m": hexc("D43C8E"),
                                   "d": hexc("8A1C5C")})
    for (x, y, big, col) in ((163, 21, True, (Y1, Y0)), (184, 30, False, (Y1, Y0)), (153, 15, False, (PK1, PK0)),
                             (177, 41, True, (PK1, PK0)), (185, 12, False, (G1, G0))):
        sprite(cv, SPARK if big else ["w"], x - (2 if big else 0), y - (2 if big else 0), {"w": col[0], "W": col[1]})


def build():
    cv = Canvas(W, H)
    panel(cv)
    vines(cv)
    draw_title(cv, "ELVEN", 60, 11, 3, [G0, G1, G1, G2, G3], VOUT)
    draw_title(cv, "PORTAL", 60, 28, 3, [Y0, Y1, Y1, Y2, Y3], Y4)
    gifts(cv)
    gate(cv)
    for (x, y) in LIGHTS:
        G.light(cv, x, y)
    return cv


def main():
    cv = build()
    cv.save(os.path.join(ROOT, "src", "main", "resources", "elvenportal_logo.png"))
    if "--preview" in sys.argv:
        out = os.path.join(ROOT, "build", "preview")
        os.makedirs(out, exist_ok=True)
        bg = Image.new("RGBA", (W + 8, H + 8), (40, 40, 40, 255))
        bg.alpha_composite(cv.img, (4, 4))
        upscale(bg, 5).save(os.path.join(out, "logo.png"))


if __name__ == "__main__":
    main()
