"""The flare and tearful panels' textures, read off photos of the originals pixel for pixel
(needs numpy, scipy and Pillow). Run from the quantumsolar folder:  python3 tools/extract_originals.py

- reference/panels_tops.png: the tops from above, the six flare panels in the first row, the
  tearful ones in the next two (six and three).
- reference/flare_sides.png: the flare panels' fronts side by side (the last first).
- reference/tearful_sides.png: six of the tearful panels' fronts side by side.
Each block's corners are where the grey corner posts between the blocks meet (read off the photos);
the 16 x 16 grid is laid on each face exactly from there (tools/extract_hearts.py's fit) and each
texel is the median of the photo over its middle. Fronts are lit back up by as much as the game
darkens a front (by the ratio of the grey posts on the front to those on the tops).
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from extract_hearts import fit, read  # noqa: E402

OUT = "src/main/resources/assets/quantumsolar/textures/block"

# the tops photo: the corners where the blocks meet, row by row (x, y)
TOPS_ROWS = [
    [(42, 57.5), (214, 55.5), (386, 53.5), (558, 49.5), (730, 46.5), (902, 45.5), (1074, 40.5)],
    [(34.5, 226), (211, 224), (386, 222), (562, 218), (736, 216), (912, 214), (1088, 210)],
    [(28.5, 402), (208, 398), (386, 396), (566, 393), (744, 391.5), (922, 388.5), (1101, 385)],
    [(20.5, 583.5), (204, 581.5), (386, 579), (569.5, 576)],
]
FLARE_X = [7, 195, 390, 585, 780, 975, 1162]
FLARE_TOP = [4, 5, 6, 7, 8, 9, 10]
FLARE_BOTTOM = [211, 209, 208, 207, 206, 205, 204]
TEAR_X = [23, 225, 437, 650, 865, 1085, 1311]
TEAR_TOP = [20, 19, 18, 17, 16, 15, 14]
TEAR_BOTTOM = [234, 235, 236, 237, 238, 239, 240]

FLARE = ["sunset", "aurora", "ember", "glyph", "amber", "blaze"]
# the tearful tops in the photo's order (row two, then row three), and which front each has
TEARFUL = ["sky", "meadow", "dusk", "sunrise", "spring", "honey", "orchid", "twilight"]
TEARFUL_FRONT = {"sky": 0, "meadow": 1, "dusk": 2, "sunrise": 3, "spring": 4, "honey": 5, "orchid": 2, "twilight": 4}


def load(name):
    return np.asarray(Image.open(f"reference/{name}.png").convert("RGB")).astype(float)


def face(img, q):
    return read(img, fit(img, q))


def save(a, name):
    Image.fromarray(np.asarray(a).round().clip(0, 255).astype(np.uint8)).save(f"{OUT}/{name}.png")


def posts(a):
    """How bright a face's grey corner posts are (the brightest grey there)."""
    corners = np.concatenate([a[0:2, 0:2].reshape(-1, 3), a[0:2, 14:16].reshape(-1, 3),
                              a[14:16, 0:2].reshape(-1, 3), a[14:16, 14:16].reshape(-1, 3)])
    return np.percentile(corners.max(axis=1), 90)


def main():
    tops = load("panels_tops")
    top_faces = []
    for r in range(3):
        row, below = TOPS_ROWS[r], TOPS_ROWS[r + 1]
        for i in range(len(row) - 1):
            if i + 1 >= len(below):
                break
            top_faces.append(face(tops, [row[i], row[i + 1], below[i + 1], below[i]]))
    flare_tops, tear_tops = top_faces[:6], top_faces[6:]
    lit = np.mean([posts(f) for f in flare_tops])

    sides = load("flare_sides")
    flare_fronts = [face(sides, [(FLARE_X[i], FLARE_TOP[i]), (FLARE_X[i + 1], FLARE_TOP[i + 1]),
                                 (FLARE_X[i + 1], FLARE_BOTTOM[i + 1]), (FLARE_X[i], FLARE_BOTTOM[i])]) for i in range(6)]
    flare_fronts = flare_fronts[::-1]                     # the photo has the last first
    # the photo's two outer blocks touch the sky at their outer edge: those pixels are taken from
    # the pixel next in
    for f in flare_fronts:
        for y in range(16):
            for x, m in ((0, 1), (15, 14), (1, 2), (14, 13)):
                r, g, b = f[y, x]
                if b > 120 and g > 110 and b > r + 25:
                    f[y, x] = f[y, m]
        for x in range(16):
            for y, m in ((0, 1), (15, 14)):
                r, g, b = f[y, x]
                if b > 120 and g > 110 and b > r + 25:
                    f[y, x] = f[m, x]
    k = lit / np.mean([posts(f) for f in flare_fronts])
    for name, t, s in zip(FLARE, flare_tops, flare_fronts):
        save(t, f"flare_panel_{name}_top")
        save(s * k, f"flare_panel_{name}_side")

    sides = load("tearful_sides")
    tear_fronts = [face(sides, [(TEAR_X[i], TEAR_TOP[i]), (TEAR_X[i + 1], TEAR_TOP[i + 1]),
                                (TEAR_X[i + 1], TEAR_BOTTOM[i + 1]), (TEAR_X[i], TEAR_BOTTOM[i])]) for i in range(6)]
    k = lit / np.mean([posts(f) for f in tear_fronts])
    for name, t in zip(TEARFUL, tear_tops):
        save(t, f"tearful_panel_{name}_top")
        save(tear_fronts[TEARFUL_FRONT[name]] * k, f"tearful_panel_{name}_side")


if __name__ == "__main__":
    main()
