"""The little galaxies the tentacles catch (besides stars), read off reference/vacuum/galaxies.jpg
pixel for pixel (needs numpy and Pillow). Run from the goldenquarry folder:
python3 tools/extract_galaxies.py

The reference is a sheet of 8 x 8 pixel-art galaxies drawn 8.5 screen pixels a pixel; its grid is
found from where the picture changes most (every 8.5 pixels from 8.25), each pixel read as the
median of its middle; the cells are 15 pixels with a line of the frame between them (every 16).
The sheet's dark ground becomes see-through (its near shades half so).
Writes textures/gui/vacuum_chest_galaxies.png: 8 x 8 sprites of 15 x 15.
"""
import numpy as np
from PIL import Image

REF = "reference/vacuum/galaxies.jpg"
OUT = "src/main/resources/assets/goldenquarry/textures/gui/vacuum_chest_galaxies.png"
PIXEL, ORIGIN = 8.5, 8.25
CELL, FIRST = 15, 6


def main():
    im = np.asarray(Image.open(REF).convert("RGB")).astype(float)
    n = int((im.shape[1] - ORIGIN) / PIXEL)
    art = np.zeros((n, n, 3), dtype=int)
    for j in range(n):
        for i in range(n):
            x0, y0 = int(ORIGIN + i * PIXEL + 2.5), int(ORIGIN + j * PIXEL + 2.5)
            art[j, i] = np.median(im[y0:y0 + 4, x0:x0 + 4].reshape(-1, 3), axis=0)
    ground = np.median(art[8:18, 8:10].reshape(-1, 3), axis=0)
    sheet = np.zeros((8 * CELL, 8 * CELL, 4), dtype=np.uint8)
    for j in range(8):
        for i in range(8):
            c = art[FIRST + 16 * j:FIRST + 16 * j + CELL, FIRST + 16 * i:FIRST + 16 * i + CELL]
            d = np.abs(c - ground).sum(axis=2)
            a = np.where(d < 18, 0, np.where(d < 40, 150, 255))
            sheet[j * CELL:(j + 1) * CELL, i * CELL:(i + 1) * CELL, :3] = c
            sheet[j * CELL:(j + 1) * CELL, i * CELL:(i + 1) * CELL, 3] = a
    Image.fromarray(sheet, "RGBA").save(OUT)


if __name__ == "__main__":
    main()
