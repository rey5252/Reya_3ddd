"""The mod's logo for the Mods list (META-INF/mods.toml logoFile): the elven moon gate with the portal swirling
in it, standing at the edge of a livingwood panel with mana crystals on its corners, that reads HEART OF
ALFHEIM in livingrock letters.

    python3 tools/portal/gen_logo.py [--preview]

Forge draws the logo fitted into a box 50 GUI pixels high, so it is exactly 50 high: every pixel of it
lands on whole screen pixels at any GUI scale.
"""
import math
import os
import sys

from PIL import Image

from pix import Canvas, ROOT, mix, shade, hexc, upscale
from style import *
import vines as V
import swirl as S
import gen_gui as G

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "lib"))
import wood as WD  # noqa: E402

W, H = 200, 50
PANEL = (30, 5, 196, 50)
GATE_MIDDLE = (24, 23)
GATE_R = (21, 13)
GEM = (24, 6)

GLYPHS = {
    "A": [".XX.", "X..X", "XXXX", "X..X", "X..X"], "E": ["XXXX", "X...", "XXX.", "X...", "XXXX"],
    "F": ["XXXX", "X...", "XXX.", "X...", "X..."], "H": ["X..X", "X..X", "XXXX", "X..X", "X..X"],
    "I": ["XXX", ".X.", ".X.", ".X.", "XXX"], "L": ["X...", "X...", "X...", "X...", "XXXX"],
    "M": ["X...X", "XX.XX", "X.X.X", "X...X", "X...X"], "O": [".XX.", "X..X", "X..X", "X..X", ".XX."],
    "R": ["XXX.", "X..X", "XXX.", "X.X.", "X..X"], "T": ["XXXXX", "..X..", "..X..", "..X..", "..X.."],
    " ": ["..", "..", "..", "..", ".."],
}


def text_pixels(text, scale):
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
            c = shade(c, 0.3)
        cv.set(x0 + x, y0 + y, c)


def gate(cv):
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


def build():
    cv = Canvas(W, H)
    x1, y1, x2, y2 = PANEL
    WD.panel(cv, x1, y1, x2, y2)
    draw_title(cv, "HEART OF", 60, 10, 3, [WD.LR[0], WD.LR[0], WD.LR[1], WD.LR[1], WD.LR[2]], WD.WOOD[6])
    draw_title(cv, "ALFHEIM", 60, 28, 3, [WD.MANA[0], WD.MANA[1], WD.MANA[1], WD.MANA[2], WD.MANA[3]], WD.MANA[5])
    for (x, y) in ((x2 - 2, y1 + 1), (x2 - 2, y2 - 3)):
        WD.crystal(cv, x, y)
    gate(cv)
    return cv


def main():
    cv = build()
    cv.save(os.path.join(ROOT, "src", "main", "resources", "alfheimheart_logo.png"))
    if "--preview" in sys.argv:
        out = os.path.join(ROOT, "build", "preview")
        os.makedirs(out, exist_ok=True)
        bg = Image.new("RGBA", (W + 8, H + 8), (40, 40, 40, 255))
        bg.alpha_composite(cv.img, (4, 4))
        upscale(bg, 5).save(os.path.join(out, "logo.png"))


if __name__ == "__main__":
    main()
