"""Builds the Golden Quarry block textures straight from the reference photo (reference/block.png).

Run from the goldenquarry folder:  python3 tools/extract_block.py [--preview DIR]
The chest's two visible faces, the cage above them, the dark stand and the drill are unwarped from
the photo into 64x64 textures (4 pixels per model unit), so the block carries the photo's pixels
one to one. Where the photo's drill hangs in front of the chest and cage, the pixels behind it are
filled in; the drill itself is a separate model that spins (tools/gen_model.py, QuarryAreaRenderer).
"""
import os
import sys

from png_io import read_png, write_png

REF = "reference/block.png"
OUT = "src/main/resources/assets/goldenquarry/textures/block"
W, H, PX = read_png(REF)
R = 4   # texture pixels per model unit

# areas of the photo as (top-left, top-right, bottom-left, bottom-right) corners
BODY_A = ((403, 121.8), (476, 125.3), (403, 179.1), (476, 186.0))     # left face of the chest (16 x 9)
BODY_B = ((476, 125.3), (537.5, 121.3), (476, 186.0), (537.5, 179.5))  # right face
CAGE_A = ((403, 85.0), (476, 82.0), (403, 121.8), (476, 125.3))        # the cage above them (16 x 6)
CAGE_B = ((476, 82.0), (537.5, 85.0), (476, 125.3), (537.5, 121.3))
BASE_A = ((403, 179.1), (476, 186.0), (403, 290.0), (476, 297.0))      # the dark stand (16 x 16)
HOUSING_A = ((438, 101.0), (476, 103.0), (438, 113.0), (476, 115.5))   # drill motor, left side (8 x 2)
HOUSING_B = ((476, 103.0), (505, 101.0), (476, 115.5), (505, 113.0))   # right side


def bil(x, y):
    x0, y0 = int(x), int(y)
    fx, fy = x - x0, y - y0
    c = [0.0, 0.0, 0.0]
    for dx, dy, wt in ((0, 0, (1 - fx) * (1 - fy)), (1, 0, fx * (1 - fy)), (0, 1, (1 - fx) * fy), (1, 1, fx * fy)):
        p = PX[y0 + dy][x0 + dx]
        for i in range(3):
            c[i] += p[i] * wt
    return tuple(int(round(v)) for v in c)


def unwarp(quad, w, h):
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = quad
    rows = []
    for j in range(h):
        v = (j + 0.5) / h
        row = []
        for i in range(w):
            t = (i + 0.5) / w
            tx, ty = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            bx, by = x2 + (x3 - x2) * t, y2 + (y3 - y2) * t
            row.append(bil(tx + (bx - tx) * v, ty + (by - ty) * v))
        rows.append(row)
    return rows


def is_sky(c):
    """The pale evening sky seen through the open cage, and the glare on it: transparent."""
    return c[2] > 118 and c[0] > 222 and c[1] > 196


def lum(c):
    return 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]


def is_drill(c):
    """Greys and pinks of the drill: much less yellow than the gold, tan and rust of the chest."""
    return c[0] - c[2] < 80 and lum(c) > 95 and not is_sky(c)


def canvas():
    return [[(0, 0, 0, 0)] * 64 for _ in range(64)]


def put(img, rows, x, y):
    for j, row in enumerate(rows):
        for i, c in enumerate(row):
            if c is not None:
                img[y + j][x + i] = tuple(c[:3]) + (255,)


def save(name, img):
    write_png(f"{OUT}/{name}.png", 64, 64, img)


def chest_side(quad, patch=None):
    face = unwarp(quad, 16 * R, 9 * R)
    if patch:
        patch(face)
    img = canvas()
    put(img, [face[0]] * (7 * R), 0, 0)          # rows the model never shows
    put(img, face, 0, 7 * R)                      # the chest is 9 units high: rows 28..63
    return img


def patch_body_a(face):
    """The photo's drill shaft and its white tip hang in front of the left face's upper middle
    (units 8..11 of the top three rows): fill in the plain gold from units 3..5 beside it."""
    for j in range(0, 3 * R):
        for i in range(8 * R, 12 * R):
            u, k = divmod(i, R)
            face[j][i] = face[j][(3 + (u - 8) % 3) * R + k]


def cage_side(quad, shift=0):
    """The cage as the photo shows it: the ring (top two units, rounded down at the ends), the posts
    and the knobs at their feet. Only the sky between them is transparent; the photo's drill (which
    hangs in rows 2..5 of the middle) is left out too, it is a model of its own. shift moves the
    sampled columns right, so the far post lands on the sheet's end like on the other face."""
    g = unwarp(quad, 16 * R, 6 * R)
    out = [[None] * (16 * R) for _ in range(6 * R)]
    for j in range(6 * R):
        for i in range(16 * R):
            src = i - shift * R
            if not 0 <= src < 16 * R:
                continue
            c = g[j][src]
            u, v = i // R, j // R
            if is_sky(c):
                continue
            if v <= 1:
                out[j][i] = c                          # ring
            elif u <= 2 or u >= 13:
                if v == 5 or not is_drill(c):
                    out[j][i] = c                      # posts and knobs at the ends
    img = canvas()
    put(img, out, 0, 10 * R)                           # the cage is 6 units high: rows 40..63
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    imgs = {
        "quarry_side_a": chest_side(BODY_A, patch_body_a),
        "quarry_side_b": chest_side(BODY_B),
        "quarry_cage_a": cage_side(CAGE_A),
        "quarry_cage_b": cage_side(CAGE_B, 1),
    }
    # the stand: the photo's left face, evened out (it is a plain dark block)
    base = unwarp(BASE_A, 16 * R, 16 * R)
    avg = [sum(c[q] for row in base for c in row) / (64 * 64) for q in range(3)]
    img = canvas()
    put(img, [[tuple(int(avg[q] + (c[q] - avg[q]) * 0.6) for q in range(3)) for c in row] for row in base], 0, 0)
    imgs["quarry_base"] = img
    # the drill, in big pixels like the photo's (one pixel per model unit, 16x16 texture): motor
    # sides as the photo's two visible sides show them, lid, funnel and bit in its colours
    pal = {"D": (60, 30, 14), "d": (91, 55, 28), "G": (149, 116, 101), "g": (200, 168, 152),
           "W": (237, 223, 206), "T": (159, 130, 84)}
    rows = {
        0: "GWWgGDDDDDdGWgGd",     # motor sides A (x 0..7) and B (x 8..15): light blocks on top,
        1: "gGGGdDDDDDdgGGGd",     # dark where the two sides meet, as in the photo
        2: "dDDDDDDDd.......",     # lid edge
        3: "gGGGGd..........",     # funnel
        4: "GdgD............",     # bit
    }
    drill = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y, row in rows.items():
        for x, ch in enumerate(row):
            if ch in pal:
                drill[y][x] = pal[ch] + (255,)
    for y in range(6, 14):                          # top and bottom of the motor: dark, grey rivets
        for x in range(8):
            corner = x in (1, 6) and y in (7, 12)
            drill[y][x] = (pal["G"] if corner else pal["D"] if (x + y) % 3 else pal["d"]) + (255,)
    write_png(f"{OUT}/quarry_drill.png", 16, 16, drill)
    for name, im in imgs.items():
        save(name, im)

    if "--preview" in sys.argv:
        out = sys.argv[-1]
        S = 4
        Wp, Hp = 2 * 64 * S + 10, 60 * S
        pv = [[(40, 40, 40, 255)] * Wp for _ in range(Hp)]
        for n, (cage, side) in enumerate([(imgs["quarry_cage_a"], imgs["quarry_side_a"]), (imgs["quarry_cage_b"], imgs["quarry_side_b"])]):
            rows = cage[40:64] + side[28:64]
            for j, row in enumerate(rows):
                for i, c in enumerate(row):
                    col = c if c[3] else ((252, 236, 160, 255) if ((i // 4) + (j // 4)) % 2 else (236, 220, 146, 255))
                    for dy in range(S):
                        for dx in range(S):
                            pv[j * S + dy][n * (64 * S + 10) + i * S + dx] = col
        write_png(f"{out}/extracted_faces.png", Wp, Hp, pv)


if __name__ == "__main__":
    main()
