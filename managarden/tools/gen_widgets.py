"""The GUI's widget sheet (textures/gui/greenhouse_widgets.png, 256x128) and the opening bloom
(textures/gui/greenhouse_bloom.png, 8 frames of 64x64).

Sheet layout (the screen reads it at these places):
  (0,0)    buttons 16x16: normal, hover, (32) pressed, (48) off
  (0,16)   icons 12x12: redstone ignore, high, low, output on, output off, output bound
  (64,0)   the heart's glass shine, 38x38
  (0,40)   the mod's badge, 88x13
  (0,56)   plate: left cap 9x15, (9,56) right cap 9x15, (18,56) middle column 1x15
  (0,80)   growth bar fill 152x6 (growing); (0,88) the same, paused
"""
import math
import os

from pix import Canvas, ASSETS, mix, shade, hexc, rnd
from sprites import PAL, BLOSSOM_S

OUT = hexc("1A0703")
LW0, LW1, LW2, LW3, LW4 = hexc("A2512A"), hexc("7A3314"), hexc("5E240B"), hexc("4A1A08"), hexc("310B04")
LR0, LR1, LR2, LR3, LR4 = hexc("FBF8EE"), hexc("E2DCCB"), hexc("C9C2B1"), hexc("A89F8B"), hexc("7A715F")
M0, M1, M2, M3, M4, M5 = hexc("F2FFFF"), hexc("A6F6FF"), hexc("55D9F7"), hexc("2A9FE2"), hexc("1B64B8"), hexc("123C7C")
L0, L1, L2, L3, L4, L5 = hexc("E4FAA8"), hexc("A8E563"), hexc("6DC043"), hexc("45922F"), hexc("2A6428"), hexc("173D1C")
P0, P1, P2, P3, P4 = hexc("FFF0F8"), hexc("FFC4E2"), hexc("EE8FC2"), hexc("C45A97"), hexc("6E2350")
RED0, RED1, RED2 = hexc("FF5A4A"), hexc("C8281C"), hexc("6A0E08")


def button(cv, x, y, state):
    """A leaf-green bevelled square; hover is brighter, pressed sunk, off grey-green."""
    face = {"normal": L2, "hover": L1, "pressed": L3, "off": hexc("5E7A5A")}[state]
    hi = {"normal": L1, "hover": L0, "pressed": L2, "off": hexc("8BA386")}[state]
    lo = {"normal": L4, "hover": L3, "pressed": L4, "off": hexc("3E5039")}[state]
    cv.outline(x, y, x + 16, y + 16, L5)
    cv.rect(x + 1, y + 1, x + 15, y + 15, face)
    if state == "pressed":
        hi, lo = lo, hi
    cv.hline(x + 1, x + 15, y + 1, hi)
    cv.vline(x + 1, y + 1, y + 15, hi)
    cv.hline(x + 1, x + 15, y + 14, lo)
    cv.vline(x + 14, y + 1, y + 15, lo)
    cv.set(x + 2, y + 2, shade(hi, 0.4))
    # rounded corners
    for (cx, cy) in ((x, y), (x + 15, y), (x, y + 15), (x + 15, y + 15)):
        cv.clear(cx, cy)


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
            "K": hexc("7A7A7A"), "c": M4, "C": M2, "p": LR3, "g": hexc("3E5039"), "G": hexc("7C8C78"), "y": LW0}


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


BADGE_TEXT = "MANA GARDEN"


def badge(cv, x0, y0):
    """The mod's badge: a pink rounded plate with a heart and MANA GARDEN in the plate letters."""
    w, h = 88, 13
    for y in range(h):
        for x in range(w):
            edge_x = min(x, w - 1 - x)
            edge_y = min(y, h - 1 - y)
            if edge_x + edge_y < 2:
                continue
            if edge_x == 0 or edge_y == 0 or edge_x + edge_y == 2:
                c = P4
            elif y == 1 or (edge_x == 1 and y < h // 2):
                c = P1
            elif y == h - 2:
                c = P3
            else:
                c = mix(P2, P3, (y - 2) / (h - 4) * 0.5)
            cv.set(x0 + x, y0 + y, c)
    heart = [".XX.XX.", "XWXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."]
    for j, row in enumerate(heart):
        for i, ch in enumerate(row):
            if ch == "X":
                cv.set(x0 + 5 + i, y0 + 3 + j, hexc("FFFFFF"))
            elif ch == "W":
                cv.set(x0 + 5 + i, y0 + 3 + j, P1)
    draw_plate_text(cv, BADGE_TEXT, x0 + 15, y0 + 4, hexc("FFFFFF"), shadow=P4)


GLYPHS = {
    "M": ["X...X", "XX.XX", "X.X.X", "X...X", "X...X"], "A": [".XX.", "X..X", "XXXX", "X..X", "X..X"],
    "N": ["X..X", "XX.X", "X.XX", "X..X", "X..X"], "G": [".XXX", "X...", "X.XX", "X..X", ".XXX"],
    "R": ["XXX.", "X..X", "XXX.", "X.X.", "X..X"], "D": ["XXX.", "X..X", "X..X", "X..X", "XXX."],
    "E": ["XXXX", "X...", "XXX.", "X...", "XXXX"], " ": ["..", "..", "..", "..", ".."],
}


def draw_plate_text(cv, text, x, y, col, shadow=None):
    for ch in text:
        g = GLYPHS[ch]
        for j, row in enumerate(g):
            for i, c in enumerate(row):
                if c == "X":
                    if shadow is not None:
                        cv.set(x + i, y + j + 1, shadow)
                    cv.set(x + i, y + j, col)
        x += len(g[0]) + 1


def plate(cv, x0, y0):
    """The name plate's caps and middle: livingrock with a livingwood edge and a leaf on each cap."""
    h = 15
    # middle column
    col = [OUT, LW1, LR0, LR1, LR1, LR1, LR1, LR1, LR1, LR1, LR2, LR2, LR3, LW3, OUT]
    for y in range(h):
        cv.set(x0 + 18, y0 + y, col[y])
    # left cap (9 wide): rounded end with a leaf and a pearl
    for cap, flip in ((0, False), (9, True)):
        for y in range(h):
            for x in range(9):
                xx = 8 - x if flip else x
                # distance from the rounded end
                dx = 8 - xx
                c = None
                inside = dx <= 7 - (1 if y in (0, h - 1) else 0) - (1 if y in (1, h - 2) and dx == 7 else 0)
                if not inside:
                    continue
                if y == 0 or y == h - 1 or dx == 7 or (dx == 6 and y in (1, h - 2)):
                    c = OUT
                elif y == 1 or (dx == 6 and y < 8):
                    c = LW1
                elif y == h - 2 or dx == 6:
                    c = LW3
                elif dx >= 4:
                    c = LW2 if y > 3 else LW1
                else:
                    c = col[y]
                cv.set(x0 + cap + x, y0 + y, c)
        px = x0 + cap + (3 if not flip else 5)
        for (dx, dy, c) in ((0, 6, M4), (1, 6, M3), (0, 7, M2), (1, 7, M1), (0, 5, OUT), (1, 5, OUT), (0, 8, OUT), (1, 8, OUT)):
            cv.set(px + dx - (1 if flip else 0), y0 + dy, c)


def growth_fill(cv, x0, y0, paused):
    """The growth bar's fill: a green gradient with little leaves along it."""
    w, h = 152, 6
    for y in range(h):
        base = [L0, L1, L1, L2, L3, L4][y] if not paused else [hexc("B8C4A8"), hexc("98A888"), hexc("98A888"), hexc("7C8C6C"), hexc("5E6E50"), hexc("465238")][y]
        for x in range(w):
            c = base
            if not paused and y in (2, 3) and (x % 12) in (4, 5):
                c = shade(base, 0.25)
            if (x % 12) == 0 and y > 0:
                c = shade(base, -0.12)
            cv.set(x0 + x, y0 + y, c)


def widgets():
    cv = Canvas(256, 128)
    for i, s in enumerate(("normal", "hover", "pressed", "off")):
        button(cv, i * 16, 0, s)
    for i, key in enumerate(("ignore", "high", "low", "out_on", "out_off", "out_bound")):
        cv.sprite(ICONS[key], i * 12, 16, ICON_PAL)
    glass_shine(cv, 64, 0)
    badge(cv, 0, 40)
    plate(cv, 0, 56)
    growth_fill(cv, 0, 80, False)
    growth_fill(cv, 0, 88, True)
    cv.save(os.path.join(ASSETS, "textures", "gui", "greenhouse_widgets.png"))
    return cv


def bloom():
    """The opening: a bud that swells and blooms into an eight-petal mana flower (8 frames of 64x64)."""
    frames = 8
    cv = Canvas(64 * frames, 64)
    for f in range(frames):
        t = f / (frames - 1)
        cx, cy = 64 * f + 32, 32
        open_ = t
        n = 8
        length = 8 + 20 * open_
        width = 4 + 6 * open_
        for layer in (0, 1):
            for k in range(n):
                a = k / n * math.tau + (math.pi / n if layer else 0) - math.pi / 2
                ln = length * (0.8 if layer else 1.0)
                spread = open_ * (1.0 if layer == 0 else 0.85)
                # petal: an ellipse from the middle outwards, tilted by how open it is
                for s in range(int(ln * 3)):
                    d = s / 3
                    tt = d / ln
                    hw = width * math.sin(math.pi * min(1.0, tt * 1.05)) * (0.6 + 0.4 * spread)
                    px, py = cx + math.cos(a) * d * (0.35 + 0.65 * spread), cy + math.sin(a) * d * (0.35 + 0.65 * spread)
                    for q in range(-int(hw * 2), int(hw * 2) + 1):
                        o = q / 2
                        x, y = px - math.sin(a) * o, py + math.cos(a) * o
                        edge = abs(o) > hw - 1.0 or tt > 0.93
                        base = mix(P1, P0, tt) if layer == 0 else mix(M1, M0, tt)
                        if not layer and tt < 0.35:
                            base = mix(M2, base, tt / 0.35)
                        c = (P3 if layer == 0 else M3) if edge else base
                        cv.set(int(math.floor(x)), int(math.floor(y)), c)
        # the heart: a glowing mana pearl
        r = 3 + 3 * open_
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= r:
                    cv.set(x, y, M0 if d < r * 0.4 else (M1 if d < r * 0.75 else M2))
    cv.save(os.path.join(ASSETS, "textures", "gui", "greenhouse_bloom.png"))
    return cv


if __name__ == "__main__":
    widgets()
    bloom()
