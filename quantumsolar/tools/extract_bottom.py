"""The blocks' bottom, read off reference/bottoms.png pixel for pixel (needs numpy, scipy, Pillow).
Run from the quantumsolar folder:  python3 tools/extract_bottom.py

The photo shows eleven of the originals from below; all have the same bottom (dark, squares one in
another, grey dots at the corners). Each face's corners are where the grey dots between them meet;
the grid is laid on each exactly (tools/extract_hearts.py's fit) and each texel is the median over
the eleven faces' texel middles, so the cursor and any glare on one of them go; then lit back up
by the half the game darkens a bottom by.
Writes textures/block/bottom.png, the bottom of every block.
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from extract_hearts import fit, read  # noqa: E402

XS = [28, 166, 304, 442, 580, 717, 856]
YS = [36, 174, 311]
# (column, row) of the faces the photo shows whole
FACES = [(c, 0) for c in range(6)] + [(0, 1), (1, 1), (2, 1), (4, 1), (5, 1)]


def main():
    img = np.asarray(Image.open("reference/bottoms.png").convert("RGB")).astype(float)
    faces = []
    for c, r in FACES:
        q = [(XS[c], YS[r]), (XS[c + 1], YS[r]), (XS[c + 1], YS[r + 1]), (XS[c], YS[r + 1])]
        faces.append(read(img, fit(img, q, reach=5.0)))
    # the game draws a bottom at half its light: lit back up
    bottom = np.median(np.stack(faces), axis=0) * 2.0
    Image.fromarray(bottom.round().clip(0, 255).astype(np.uint8)).save(
        "src/main/resources/assets/quantumsolar/textures/block/bottom.png")


if __name__ == "__main__":
    main()
