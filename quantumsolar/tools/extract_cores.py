"""The core generators' textures, read off reference/cores.png pixel for pixel (needs numpy, scipy
and Pillow). Run from the quantumsolar folder:  python3 tools/extract_cores.py

reference/cores.png is four of the original blocks seen from straight above, side by side (amethyst,
topaz, jade, ruby), lit full, so their top faces show the texture's own colours. The row of four is
found against the grass round it; each block is a quarter of it; then, as for the other faces
(tools/fit.py), each block's square is moved and sized a little until the 16 x 16 grid's lines lie
on the picture's edges between texels, and each texel is the median over its middle.
Writes textures/block/core_generator_<name>_top.png and _side.png (the same texture on all sides,
as the originals have).
"""
import colorsys

import numpy as np
from PIL import Image
from scipy.ndimage import map_coordinates

REF = "reference/cores.png"
OUT = "src/main/resources/assets/quantumsolar/textures/block"
NAMES = ["amethyst", "topaz", "jade", "ruby"]
SUB, INNER = 5, 0.6


def grass(c):
    h, s, v = colorsys.rgb_to_hsv(*(c / 255.0))
    return 0.15 < h < 0.35 and s > 0.3


def row_box(img):
    """The row of blocks: the rows and columns where most pixels are not grass."""
    a = img.astype(float)
    mask = np.zeros(a.shape[:2], dtype=bool)
    for y in range(a.shape[0]):
        for x in range(a.shape[1]):
            mask[y, x] = not grass(a[y, x])
    cols = np.nonzero(mask.mean(axis=0) > 0.6)[0]
    rows = np.nonzero(mask.mean(axis=1) > 0.6)[0]
    return cols.min(), rows.min(), cols.max() + 1, rows.max() + 1


def texels(img, x0, y0, size):
    t = (np.arange(SUB) + 0.5) / SUB * INNER + (1 - INNER) / 2
    u = (np.arange(16)[:, None] + t[None, :]).reshape(-1) / 16.0 * size
    xx, yy = np.meshgrid(x0 + u - 0.5, y0 + u - 0.5)
    ch = [map_coordinates(img[:, :, c], [yy.ravel(), xx.ravel()], order=1, mode="nearest") for c in range(3)]
    return np.stack(ch, axis=-1).reshape(16, SUB, 16, SUB, 3)


def cost(img, x0, y0, size):
    s = texels(img, x0, y0, size)
    return s.var(axis=(1, 3)).sum()


def fit(img, x0, y0, size):
    best = (cost(img, x0, y0, size), x0, y0, size)
    for step in (1.0, 0.5, 0.25):
        improved = True
        while improved:
            improved = False
            _, bx, by, bs = best
            for dx, dy, ds in ((step, 0, 0), (-step, 0, 0), (0, step, 0), (0, -step, 0), (0, 0, step), (0, 0, -step)):
                c = cost(img, bx + dx, by + dy, bs + ds)
                if c < best[0] - 1e-6:
                    best = (c, bx + dx, by + dy, bs + ds)
                    improved = True
    return best[1:]


def main():
    img = np.asarray(Image.open(REF).convert("RGB")).astype(float)
    x0, y0, x1, y1 = row_box(img)
    size = (x1 - x0) / 4.0
    print("row", x0, y0, x1, y1, "block", size)
    for k, name in enumerate(NAMES):
        bx, by, bs = fit(img, x0 + k * size, y0 + (y1 - y0 - size) / 2, size)
        s = texels(img, bx, by, bs)
        face = np.median(s.transpose(0, 2, 1, 3, 4).reshape(16, 16, -1, 3), axis=2).round().clip(0, 255).astype(np.uint8)
        print(name, round(bx, 2), round(by, 2), round(bs, 2))
        for part in ("top", "side"):
            Image.fromarray(face).save(f"{OUT}/core_generator_{name}_{part}.png")


if __name__ == "__main__":
    main()
