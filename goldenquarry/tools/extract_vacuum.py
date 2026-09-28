"""Builds the vacuum chest's textures from its references, pixel for pixel (needs Pillow).

Run from the goldenquarry folder:  python3 tools/extract_vacuum.py
- reference/vacuum/gui.png is the GUI at exactly 3 screen pixels per GUI pixel, on the pixel grid,
  so every GUI pixel is read from the middle of its 3x3 block. What is drawn on top of the
  background there is taken off again: the four stacks in the storage (their slots rebuilt from an
  empty one), the labels, the range number and the title's letters (the screen writes them, in the
  player's language), and the LoliUtility name plate (the screen draws the mod's own). Its slots,
  buttons and title plate are kept, in the chest's own colours (steel, gold, purple).
- The frame is drawn the way LoliUtility frames its machines (reference/vacuum/mobfarm_gui.png, the
  same 3:1 grid): bolt, rod and spiral ornaments and the gold knobs are that GUI's own pixels, in
  the chest's colours. The panel inside is left open: the screen draws under it the turning vortex
  (vacuum_chest_vortex.png, its frames stacked). Under the panel goes the player inventory, drawn
  the vanilla way.
- reference/vacuum/block.png is the block in the game: the front face is unwarped through the
  homography that takes the centres of its steel frame (texels 1 and 14) to their places, each
  texel is the median of its middle, and the face's shading (0.8, north/south) is taken off.
"""
import math
import os

import numpy as np
from PIL import Image

ASSETS = "src/main/resources/assets/goldenquarry/textures"
TOP = 14                         # GUI row of the reference that is the texture's row 0 (the title plate's top)
BG = (146, 107, 68)              # the panel's brown
# The GUI in the vacuum chest's own colours (its block texture): the brown frame becomes its steel,
# the light lining and the slot rims its gold, the brown panel and slots its purple, the red button
# its crystal. Greys, black and white stay.
VACUUM_COLOURS = {
    (132, 77, 51): (161, 166, 200), (96, 54, 44): (105, 105, 140),          # frame: steel
    (255, 229, 151): (231, 208, 138), (255, 201, 107): (231, 182, 98),      # lining, slot rims: gold
    (186, 126, 73): (169, 115, 66), (77, 46, 38): (34, 35, 35),             # buttons
    (146, 107, 68): (61, 25, 112), (147, 103, 63): (61, 25, 112),           # the panel: purple
    (134, 89, 58): (44, 14, 86), (124, 80, 54): (34, 9, 70),                # slots and their spiral
    (188, 0, 31): (120, 15, 248), (224, 11, 46): (168, 51, 255), (163, 0, 27): (90, 2, 228),  # the crystal button
}


def reference_gui():
    """The reference GUI read off its 3:1 grid, cleaned and in the chest's colours: {(x, y): colour}
    with y counted from the title plate's top (TOP), the dimmed world left out."""
    ref = Image.open("reference/vacuum/gui.png").convert("RGB")
    w, h = ref.size[0] // 3, ref.size[1] // 3
    g = [[ref.getpixel((x * 3 + 1, y * 3 + 1)) for x in range(w)] for y in range(h)]

    def fill(x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                g[y][x] = c

    # the storage's first four slots hold stacks: copy the empty fifth slot over them
    for c in range(4):
        for j in range(18):
            for i in range(18):
                g[41 + j][23 + 18 * c + i] = g[41 + j][23 + 18 * 5 + i]
    fill(8, 97, 199, 107, BG)                 # the labels "Item filter" and "Range"
    fill(165, 110, 176, 124, BG)              # the range number
    # the title's letters: the plate's light middle rows, between the dotted ends
    for y in range(16, 23):
        for x in range(57, 150):
            g[y][x] = (198, 198, 198)
    out = {}
    for y in range(TOP, h):
        for x in range(w):
            c = g[y][x]
            if not (c[0] == c[1] == c[2] and 14 <= c[0] <= 18):
                out[(x, y - TOP)] = VACUUM_COLOURS.get(c, c)
    return out


# The frame, redrawn the way LoliUtility's machine GUIs are framed (reference/vacuum/mobfarm_gui.png,
# also on a 3:1 grid): a thin steel double line between black ones, gold knobs on the corners,
# square gold spirals in the panel's corners held by rods from steel bolts on the sides, and the
# bottom corners stepping down to the inventory. Everything moves SHIFT to the right, so the bolts
# and knobs have room outside the frame.
SHIFT = 4
WIDTH = 208 + 2 * SHIFT
FRAME_TOP, FRAME_BOTTOM = 12, 148        # the frame's outer rows
INV_X, INV_Y = 16 + SHIFT, 157           # the inventory panel's top left corner
# the bottom corners step in towards the inventory: (first row, inset) of each step
STEPS = [(149, 4), (153, 8), (157, 12)]
STEPS_END = 164
BLACK, STEEL_LIGHT, STEEL, STEEL_DARK = (0, 0, 0), (221, 225, 231), (161, 166, 200), (105, 105, 140)
PURPLE_LINE, PURPLE_DARK, GOLD = (96, 52, 148), (34, 9, 70), (231, 182, 98)
# the frame's bands by their distance from the outside (1 = the outer black line)
BANDS = {1: BLACK, 2: STEEL, 3: STEEL_DARK, 4: BLACK, 5: PURPLE_LINE, 6: PURPLE_DARK, 7: PURPLE_DARK, 8: GOLD}
# the panel: purple shades from the deep middle to the edges (the vortex pulling things in)
SHADES = [(24, 5, 48), (33, 10, 66), (44, 16, 86), (55, 22, 104), (66, 29, 122), (78, 37, 140)]
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]
# the ornament on the frame's top left, from the mob farm's (rows 35..48, columns 62..79 of its GUI):
# the bolt, the rod through the frame and the spiral in its box; "." is left as it is
ORNAMENT = ["KKKKK.............",
            "KSSSK.............",
            "KTTTK.............",
            "KUUUK..KKKKKKKKKKK",
            "KSSSKKKKKKBBBBBBBK",
            "KTTTKLMMMMMMMMMMDK",
            "KUUUKKKKKKOOOOOPDK",
            "KSSSK...BOqqqqOqDK",
            "KTTTK...BOqOOqOqDK",
            "KKKKK...BOrOrrOrDK",
            "........BOuOOOOuDK",
            "........BOxxxxxxDK",
            "........BDDDDDDDDK",
            "........KKKKKKKKKK"]
ORNAMENT_COLOURS = {"K": BLACK, "S": STEEL_LIGHT, "T": STEEL, "U": STEEL_DARK, "L": (250, 240, 200),
                    "M": (245, 230, 180), "B": STEEL, "D": STEEL_DARK, "O": (20, 8, 34),
                    "P": (240, 220, 165), "q": (238, 210, 150), "r": (231, 182, 98), "u": (205, 150, 80),
                    "x": (180, 120, 64)}
# the spiral's colours by its row, light at the top: M P q q r u x
SPIRAL_ROWS = [(245, 230, 180), (240, 220, 165), (238, 210, 150), (238, 210, 150), (231, 182, 98),
               (205, 150, 80), (180, 120, 64)]
KNOB = ["KKKKKKK", "KbbbbbK", "KbcccdK", "KbcccdK", "KbcccdK", "KbddddK", "KKKKKKK"]
KNOB_COLOURS = {"K": BLACK, "b": (240, 215, 140), "c": GOLD, "d": (169, 115, 66)}


def inside(x, y):
    """The frame's shape: the panel, and under it the steps down to the inventory."""
    if SHIFT <= x < WIDTH - SHIFT and FRAME_TOP <= y <= FRAME_BOTTOM:
        return True
    for i, (y0, inset) in enumerate(STEPS):
        y1 = STEPS[i + 1][0] - 1 if i + 1 < len(STEPS) else STEPS_END
        if y0 <= y <= y1 and SHIFT + inset <= x < WIDTH - SHIFT - inset:
            return True
    return False


def distances():
    """Chebyshev distance of each pixel of the shape from the outside (1 on the rim)."""
    d = {}
    todo = [(x, y) for y in range(256) for x in range(WIDTH) if inside(x, y)]
    ring = [p for p in todo if any(not inside(p[0] + i, p[1] + j) for i in (-1, 0, 1) for j in (-1, 0, 1))]
    level = 1
    left = set(todo)
    while ring:
        for p in ring:
            d[p] = level
            left.discard(p)
        nxt = set()
        for (x, y) in ring:
            for i in (-1, 0, 1):
                for j in (-1, 0, 1):
                    q = (x + i, y + j)
                    if q in left:
                        nxt.add(q)
        ring = list(nxt)
        level += 1
    return d


VORTEX_X, VORTEX_Y, VORTEX_W, VORTEX_H, VORTEX_FRAMES = SHIFT + 8, FRAME_TOP + 8, 192, INV_Y - FRAME_TOP - 8, 12


def vortex(x, y, turn=0.0):
    """The panel's purple: deeper towards the middle, and three lighter arms winding into it, so all
    of it looks pulled in (dithered, so the steps between the shades stay soft)."""
    cx, cy = WIDTH / 2.0 - 0.5, 80.0
    u, v = (x - cx) / 100.0, (y - cy) / 64.0
    square = (u ** 4 + v ** 4) ** 0.25
    round_ = math.hypot(u, v)
    a = math.atan2(v, u)
    phase = (math.log(round_ + 0.04) * 2.6 + 3 * a / (2 * math.pi) + turn) % 1.0
    level = 0.2 + 4.6 * min(1.0, square) ** 0.85
    level += (1.0 if phase < 0.10 else 0.5 if phase < 0.2 else 0.0) * min(1.0, round_ * 2.5)
    level += (BAYER[y % 4][x % 4] + 0.5) / 16.0 - 0.5
    return SHADES[max(0, min(len(SHADES) - 1, int(round(level))))]


def put_pattern(tex, pattern, colours, x0, y0, flip_x=False, flip_y=False):
    rows = pattern[::-1] if flip_y else pattern
    for j, row in enumerate(rows):
        if flip_x:
            row = row[::-1]
        for i, ch in enumerate(row):
            if ch != ".":
                tex.putpixel((x0 + i, y0 + j), colours[ch] + (255,))


def ornament(tex, x0, y0, flip_x=False, flip_y=False):
    """Bolt, rod and spiral, flipped for the other corners: the spiral stays light at the top, the
    box's rim light on its top and left and dark on its bottom and right."""
    h, w = len(ORNAMENT), len(ORNAMENT[0])
    for tj, row in enumerate(ORNAMENT):
        for ti, ch in enumerate(row):
            if ch == ".":
                continue
            fi = w - 1 - ti if flip_x else ti
            fj = h - 1 - tj if flip_y else tj
            c = ORNAMENT_COLOURS[ch]
            if ch in "MPqrux" and 9 <= ti <= 15:          # the spiral (the rod keeps its colour)
                k = tj - 5
                c = SPIRAL_ROWS[6 - k if flip_y else k]
            elif ch in "BD":                               # the box's rim
                left = (ti == 16) if flip_x else (ti == 8)
                top = (tj == 12) if flip_y else (tj == 4)
                c = STEEL if left or top else STEEL_DARK
            tex.putpixel((x0 + fi, y0 + fj), c + (255,))


def gui():
    ref = reference_gui()
    tex = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    # the title plate, as the reference has it
    for (x, y), c in ref.items():
        if y <= 11 and 51 <= x <= 158:
            tex.putpixel((x + SHIFT, y), c + (255,))
    # the frame's bands; inside them the reference's slots and buttons, and where the reference has
    # its plain brown the panel stays open: the screen draws the turning vortex under it
    d = distances()
    for (x, y), k in d.items():
        if k in BANDS:
            c = BANDS[k]
        else:
            c = ref.get((x - SHIFT, y))
            if c is None or c == VACUUM_COLOURS[BG] or not (8 <= x - SHIFT <= 199 and 21 <= y <= 140):
                continue
        tex.putpixel((x, y), c + (255,))
    # the vortex, in frames: its arms wind in a twelfth of a turn of their pattern each frame
    frames = Image.new("RGBA", (VORTEX_W, VORTEX_H * VORTEX_FRAMES))
    for f in range(VORTEX_FRAMES):
        for j in range(VORTEX_H):
            for i in range(VORTEX_W):
                frames.putpixel((i, f * VORTEX_H + j), vortex(VORTEX_X + i, VORTEX_Y + j, f / VORTEX_FRAMES) + (255,))
    frames.save(f"{ASSETS}/gui/vacuum_chest_vortex.png")
    # the ornaments: top ones under the frame's top, bottom ones over its bottom, mirrored on the right
    top_y, bottom_y = FRAME_TOP + 3, FRAME_BOTTOM - 3 - len(ORNAMENT) + 1
    ornament(tex, 0, top_y)
    ornament(tex, WIDTH - len(ORNAMENT[0]), top_y, flip_x=True)
    ornament(tex, 0, bottom_y, flip_y=True)
    ornament(tex, WIDTH - len(ORNAMENT[0]), bottom_y, flip_x=True, flip_y=True)
    # gold knobs on the frame's top corners and on the steps' outer corners
    for cx, cy in ((SHIFT, FRAME_TOP), (WIDTH - SHIFT - 1, FRAME_TOP),
                   (SHIFT + STEPS[-1][1], STEPS_END), (WIDTH - SHIFT - 1 - STEPS[-1][1], STEPS_END)):
        put_pattern(tex, KNOB, KNOB_COLOURS, cx - 3, cy - 3)
    inventory(tex, INV_X, INV_Y)
    tex.save(f"{ASSETS}/gui/vacuum_chest.png")


def inventory(tex, x0, y0, w=176, h=88):
    """The vanilla inventory panel: black rim with cut corners, white light top left, grey shade
    bottom right, the 27 slots and the hotbar."""
    K, W, C, S = (0, 0, 0), (255, 255, 255), (198, 198, 198), (85, 85, 85)
    for y in range(h):
        for x in range(w):
            cx, cy = min(x, w - 1 - x), min(y, h - 1 - y)      # distance from the nearest corner
            if (cx, cy) in ((0, 0), (1, 0), (0, 1)):
                continue                                       # the cut corners
            if cx == 0 or cy == 0 or (cx, cy) == (1, 1):
                c = K
            else:
                light = x <= 2 or y <= 2
                shade = x >= w - 3 or y >= h - 3
                c = C if light == shade else W if light else S
            tex.putpixel((x0 + x, y0 + y), c + (255,))
    for r in range(4):
        for col in range(9):
            slot(tex, x0 + 7 + 18 * col, y0 + 6 + 18 * r + (4 if r == 3 else 0))


def slot(tex, x0, y0):
    for y in range(18):
        for x in range(18):
            if (x, y) in ((17, 0), (0, 17)):
                c = (139, 139, 139)
            elif x == 0 or y == 0:
                c = (55, 55, 55)
            elif x == 17 or y == 17:
                c = (255, 255, 255)
            else:
                c = (139, 139, 139)
            tex.putpixel((x0 + x, y0 + y), c + (255,))


def block():
    im = np.asarray(Image.open("reference/vacuum/block.png").convert("RGB")).astype(int)

    def steel(c):
        r, g, b = c
        return b > r + 12 and b > g + 8 and (r + g + b) / 3 > 80

    def centre(vals):
        idx = [i for i, v in enumerate(vals) if v]
        return (idx[0] + idx[-1]) / 2

    top = [(x, 95 + centre([steel(im[y, x]) for y in range(95, 140)])) for x in range(110, 300, 10)]
    bot = [(x, 395 + centre([steel(im[y, x]) for y in range(395, 450)])) for x in range(110, 300, 10)]
    left = [(50 + centre([steel(im[y, x]) for x in range(50, 90)]), y) for y in range(170, 390, 10)]
    right = [(305 + centre([steel(im[y, x]) for x in range(305, 350)]), y) for y in range(160, 400, 10)]

    def line(ps, horizontal):
        ps = np.array(ps, float)
        return np.polyfit(ps[:, 0], ps[:, 1], 1) if horizontal else np.polyfit(ps[:, 1], ps[:, 0], 1)

    T, B, L, R = line(top, True), line(bot, True), line(left, False), line(right, False)

    def meet(hl, vl):
        a1, b1 = hl
        a2, b2 = vl
        y = (a1 * b2 + b1) / (1 - a1 * a2)
        return a2 * y + b2, y

    corners = [meet(T, L), meet(T, R), meet(B, L), meet(B, R)]
    grid = [(1.5, 1.5), (14.5, 1.5), (1.5, 14.5), (14.5, 14.5)]
    rows, rhs = [], []
    for (u, v), (x, y) in zip(grid, corners):
        rows.append([u, v, 1, 0, 0, 0, -x * u, -x * v])
        rhs.append(x)
        rows.append([0, 0, 0, u, v, 1, -y * u, -y * v])
        rhs.append(y)
    hm = np.append(np.linalg.solve(np.array(rows, float), np.array(rhs, float)), 1.0).reshape(3, 3)
    tex = Image.new("RGBA", (16, 16))
    for j in range(16):
        for i in range(16):
            vals = []
            for a in np.linspace(0.3, 0.7, 5):
                for b in np.linspace(0.3, 0.7, 5):
                    p = hm @ np.array([i + a, j + b, 1.0])
                    vals.append(im[int(round(p[1] / p[2])), int(round(p[0] / p[2]))])
            c = np.median(np.array(vals), axis=0) / 0.8
            tex.putpixel((i, j), tuple(int(min(255, round(v))) for v in c) + (255,))
    tex.save(f"{ASSETS}/block/vacuum_chest.png")


if __name__ == "__main__":
    os.makedirs(f"{ASSETS}/gui", exist_ok=True)
    gui()
    block()
