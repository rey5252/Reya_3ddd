"""Builds the vacuum chest's textures from its references, pixel for pixel (needs Pillow).

Run from the goldenquarry folder:  python3 tools/extract_vacuum.py
- reference/vacuum/gui.png is the GUI at exactly 3 screen pixels per GUI pixel, on the pixel grid,
  so every GUI pixel is read from the middle of its 3x3 block. What is drawn on top of the
  background there is taken off again: the four stacks in the storage (their slots rebuilt from an
  empty one), the labels, the range number and the title's letters (the screen writes them, in the
  player's language), and the LoliUtility name plate (the screen draws the mod's own). Under the
  panel goes the player inventory, drawn the vanilla way.
- reference/vacuum/block.png is the block in the game: the front face is unwarped through the
  homography that takes the centres of its steel frame (texels 1 and 14) to their places, each
  texel is the median of its middle, and the face's shading (0.8, north/south) is taken off.
"""
import os

import numpy as np
from PIL import Image

ASSETS = "src/main/resources/assets/goldenquarry/textures"
TOP = 14                         # GUI row of the reference that is the texture's row 0 (the title plate's top)
BG = (146, 107, 68)              # the panel's brown
PANEL_Y = 146                    # the inventory panel's top row in the texture (the frame's last black row)


def gui():
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
    for y in range(97, 108):                  # "Range" reaches into the frame: the frame as the row above
        for x in range(200, 208):
            g[y][x] = g[96][x]
    fill(165, 110, 176, 124, BG)              # the range number
    # the title's letters: the plate's light middle rows, between the dotted ends
    for y in range(16, 23):
        for x in range(57, 150):
            g[y][x] = (198, 198, 198)

    tex = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    for y in range(TOP, h):
        for x in range(w):
            c = g[y][x]
            if c[0] == c[1] == c[2] and 14 <= c[0] <= 18:      # the dimmed world behind the GUI
                continue
            tex.putpixel((x, y - TOP), c + (255,))
    inventory(tex, 16, PANEL_Y)
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
