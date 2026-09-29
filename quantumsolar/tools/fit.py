"""Lays the 16 x 16 texel grid exactly on a face in reference/panels.jpg, the way the fortune upgrades
were read (the grid put over the picture until every texel's edges sit on the picture's): starting
from the face's corners in tools/strips.py, each corner is moved about (a few picture pixels, in
steps of a quarter) while the texels come out more even inside, that is, while the grid's lines lie
more on the picture's edges between texels. Then each texel is the median of the picture over its
middle. Writes the fitted corners to tools/fitted.json.

Run from the quantumsolar folder:  python3 tools/fit.py [face names...]
"""
import json
import os
import sys

import numpy as np
from PIL import Image
from scipy.ndimage import map_coordinates

sys.path.insert(0, os.path.dirname(__file__))
from rectify import REF, homography  # noqa: E402
from strips import STRIPS  # noqa: E402

OUT = "tools/fitted.json"
IMG = np.asarray(Image.open(REF).convert("RGB")).astype(float)
SUB = 5          # samples a side inside each texel
INNER = 0.7      # of the texel's width the samples cover


def sample_points(q, i=0):
    H = homography([(0, 1), (1, 1), (1, 0), (0, 0)], q)
    t = (np.arange(SUB) + 0.5) / SUB * INNER + (1 - INNER) / 2
    u = (np.arange(16)[:, None] + t[None, :]).reshape(-1) / 16.0       # 16*SUB
    uu, vv = np.meshgrid(u, u)                                          # rows: v, cols: u
    P = H @ np.stack([uu.ravel(), vv.ravel(), np.ones(uu.size)])
    return P[0] / P[2], P[1] / P[2]


def texels(q):
    x, y = sample_points(q)
    ch = [map_coordinates(IMG[:, :, c], [y, x], order=1, mode="nearest") for c in range(3)]
    s = np.stack(ch, axis=-1).reshape(16, SUB, 16, SUB, 3)             # v, sv, u, su
    return s


def cost(q):
    s = texels(q)
    within = s.var(axis=(1, 3)).sum()
    # how different the texels are from each other: a grid slid off the face onto a flat area
    # would look even inside too, so evenness is weighed against contrast kept
    means = s.mean(axis=(1, 3))
    spread = means.var(axis=(0, 1)).sum() + 1.0
    return within / spread


def fit(q0, reach=3.0, step=0.25):
    q = [list(map(float, p)) for p in q0]
    best = cost(q)
    for size in (1.0, 0.5, step):
        improved = True
        while improved:
            improved = False
            for k in range(4):
                for dx, dy in ((size, 0), (-size, 0), (0, size), (0, -size)):
                    t = [p[:] for p in q]
                    t[k][0] += dx
                    t[k][1] += dy
                    if abs(t[k][0] - q0[k][0]) > reach or abs(t[k][1] - q0[k][1]) > reach:
                        continue
                    c = cost(t)
                    if c < best - 1e-9:
                        best, q, improved = c, t, True
    return q, best


def read(q):
    """The face at 16 x 16: each texel the median of its middle samples."""
    s = texels(q)
    return np.median(s.reshape(16, SUB, 16, SUB, 3).transpose(0, 2, 1, 3, 4).reshape(16, 16, -1, 3), axis=2
                     ).round().clip(0, 255).astype(np.uint8)


def main(names):
    done = json.load(open(OUT)) if os.path.exists(OUT) else {}
    for name in names:
        q0 = STRIPS[name]["q"]
        q, c = fit(q0)
        done[name] = [[round(v, 2) for v in p] for p in q]
        print(name, round(cost(q0), 1), "->", round(c, 1), done[name])
    json.dump(done, open(OUT, "w"), indent=1)


if __name__ == "__main__":
    main(sys.argv[1:])
