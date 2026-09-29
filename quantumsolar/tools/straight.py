"""The picture's faces put straight (python3 tools/build.py uses it).

The picture shows the blocks at a slant, so their faces come out of it a little crooked. The faces
whose design is a figure are drawn here again texel by texel as the picture has them, only straight
and centred: the sides with the bolt (a dark plate, two square rings round the bolt, half a socket at
each edge that meets the next block's half), the generators' sides (a chain standing at each edge,
the bolt between), the flare panels' rings (the picture's own ring, made even left to right and set
in the middle), the tearful faces, the grids, the serpent lines and the square spirals. Their
colours are the picture's.
"""
import colorsys

import numpy as np

K, D, M, R = (20, 20, 24), (34, 34, 40), (46, 46, 54), (62, 62, 72)
SOCKET = {"W": (206, 208, 216), "G": (156, 158, 168), "g": (104, 106, 116), "h": (12, 12, 15)}


def mix(c, d, t):
    return tuple(int(round(p * (1 - t) + q * t)) for p, q in zip(c, d))


def shade(c, k):
    return tuple(int(max(0, min(255, round(v * k)))) for v in c)


def light(c, t):
    return mix(c, (255, 255, 255), t)


def grid(rows, colours):
    a = np.zeros((16, 16, 3), dtype=np.uint8)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            a[y, x] = colours[ch]
    return a


# the plate the bolt sits on: dark, a ring at the rim and one round the bolt, as the picture shows
PLATE = ["KKKKKKKKKKKKKKKK",
         "KDDDDDDDDDDDDDDK",
         "KDRRRRRRRRRRRRDK",
         "KDRDDDDDDDDDDRDK",
         "KDRDMMMMMMMMDRDK",
         "GGGDMDDDDDDMDGGG",
         "hhGDMDDDDDDMDGhh",
         "hhGDMDDDDDDMDGhh",
         "hhGDMDDDDDDMDGhh",
         "hhGDMDDDDDDMDGhh",
         "gggDMDDDDDDMDggg",
         "KDRDMMMMMMMMDRDK",
         "KDRDDDDDDDDDDRDK",
         "KDRRRRRRRRRRRRDK",
         "KDDDDDDDDDDDDDDK",
         "KKKKKKKKKKKKKKKK"]
# the bolt, as the picture draws it: L lit, C its colour, S in shade (centred on the plate)
BOLT = ["................",
        "................",
        "................",
        "................",
        "................",
        "........LC......",
        ".......LC.......",
        "......LCCC......",
        "........CS......",
        ".......CS.......",
        "......CS........",
        "................",
        "................",
        "................",
        "................",
        "................"]


def put(a, rows, colours):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                a[y, x] = colours[ch]
    return a


def bolt_colours(c):
    return {"L": light(c, 0.45), "C": c, "S": shade(c, 0.72)}


def side_bolt(c):
    colours = {"K": K, "D": D, "M": M, "R": R, **SOCKET}
    return put(grid(PLATE, colours), BOLT, bolt_colours(c))


CHAIN = ["WGg", "GhG", "GhG", "ggg"]


def side_chain_bolt(c):
    """The generators' sides: a chain standing at each edge, the plate and its bolt between."""
    colours = {"K": K, "D": D, "M": M, "R": R, **SOCKET}
    a = grid(PLATE, colours)
    for y in range(16):
        link = CHAIN[y % 4]
        for i in range(3):
            a[y, i] = SOCKET[link[i]]
            a[y, 15 - i] = SOCKET[link[i]]
    return put(a, BOLT, bolt_colours(c))


def even_ornament(face, rim_like):
    """A picture's ornament, pixel for pixel, set in the middle of the plate (by where its coloured
    pixels lie), the plate straight round it with its sockets."""
    colours = {"K": K, "D": D, "M": D, "R": R, **SOCKET}
    a = grid(PLATE, colours)
    for y in range(3, 13):
        for x in range(3, 13):
            a[y, x] = D
    sat = np.zeros((16, 16))
    for y in range(16):
        for x in range(16):
            h, s, v = colorsys.rgb_to_hsv(*(face[y, x] / 255.0))
            inside = 3 <= x <= 12 and 2 <= y <= 13         # the sockets and rims are not the ornament
            sat[y, x] = s * v + v if inside and ((s > 0.28 and v > 0.28) or v > 0.36) else 0
    ys, xs = np.nonzero(sat[2:14, 2:14] > 0)
    # the ornament's middle, by its bulk (a few strays at its edge do not move it)
    if len(xs) > 6:
        lo, hi = np.percentile(xs, [8, 92]), np.percentile(ys, [8, 92])
        xs, ys = np.array([lo[0], lo[1]]).round().astype(int), np.array([hi[0], hi[1]]).round().astype(int)
    if len(xs) == 0:
        return a
    cx, cy = (xs.min() + xs.max()) / 2 + 2, (ys.min() + ys.max()) / 2 + 2
    dx, dy = int(round(7.5 - cx)), int(round(7.5 - cy))
    moved = np.zeros_like(face)
    msat = np.zeros((16, 16))
    for y in range(16):
        for x in range(16):
            sx, sy = x - dx, y - dy
            if 0 <= sx < 16 and 0 <= sy < 16:
                moved[y, x] = face[sy, sx]
                msat[y, x] = sat[sy, sx]
    for y in range(2, 14):
        for x in range(3, 13):
            if msat[y, x] > 0:
                a[y, x] = moved[y, x]
    return a


FACE = ["BBBBBBBBBBBBBBBB",
        "bbbbbbbbbbbbbbbb",
        "BBBBBBBBBBBBBBBB",
        "BBBHHBBBBBBHHBBB",
        "BBHHHHBBBBHHHHBB",
        "BBHOOHBBBBHOOHBB",
        "BBBHHBBBBBBHHBBB",
        "bbbbbbbbbbbbTbbb",
        "BBBBBBBBBBBBTBBB",
        "BBBBBBBBBBBBtBBB",
        "BBBBBBBBBBBBtBBB",
        "BBBKKKKKKKKKKBBB",
        "BBKKbbbbbbbbKKBB",
        "BBBBBBBBBBBBBBBB",
        "bbbbbbbbbbbbbbbb",
        "BBBBBBBBBBBBBBBB"]


def side_face(tear, brown=(138, 92, 50)):
    """The tearful panels' fronts as the picture has them: brown boards, two dark hoods with
    glowing orange eyes, a long tear under the right one, the dark mouth below."""
    colours = {"B": brown, "b": shade(brown, 0.82), "H": (34, 32, 36), "O": (255, 150, 40),
               "T": light(tear, 0.2), "t": tear, "K": (40, 30, 24)}
    return grid(FACE, colours)


def top_grid(cell, rim):
    """The grids: a light rim, a dark line inside it, three by three cells (the middle ones four
    wide, so it is even), each lit from the top with a white glint at its bottom left."""
    a = np.zeros((16, 16, 3), dtype=np.uint8)
    spans = [(2, 4), (6, 9), (11, 13)]
    for y in range(16):
        for x in range(16):
            a[y, x] = rim if x in (0, 15) or y in (0, 15) else (10, 10, 14)
    for y0, y1 in spans:
        for x0, x1 in spans:
            for y in range(y0, y1 + 1):
                for x in range(x0, x1 + 1):
                    a[y, x] = light(cell, 0.18) if y == y0 else shade(cell, 0.8) if y == y1 else cell
            a[y1, x0] = (250, 250, 255)
    return a


def top_serpent(c1, c2, rainbow=False):
    """The serpent tops: one line two pixels thick winding across and back four times, dark between,
    its colour running from one end to the other as the picture's does."""
    a = np.zeros((16, 16, 3), dtype=np.uint8)
    a[:] = (14, 14, 18)
    path = []
    rows = [1, 5, 9, 13]
    for k, y in enumerate(rows):
        xs = list(range(1, 15)) if k % 2 == 0 else list(range(14, 0, -1))
        path += [(x, y) for x in xs]
        if k < len(rows) - 1:
            end = 14 if k % 2 == 0 else 1
            path += [(end, y + i) for i in range(2, 4)]
    n = len(path)
    for i, (x, y) in enumerate(path):
        t = i / (n - 1)
        if rainbow:
            r, g, b = colorsys.hsv_to_rgb(t * 0.85, 0.6, 1.0)
            c = (int(r * 255), int(g * 255), int(b * 255))
        else:
            c = mix(c1, c2, (np.sin(t * np.pi * 3) + 1) / 2)
        a[y, x] = light(c, 0.15)
        a[y + 1, x] = shade(c, 0.8)
    return a


def top_spiral(c1, c2, rainbow=False):
    """The spiral tops: a square spiral one pixel wide with one pixel of dark between its turns,
    from the rim into the middle, its colour running along it."""
    a = np.zeros((16, 16, 3), dtype=np.uint8)
    a[:] = (14, 14, 18)
    path = []
    x0, y0, x1, y1 = 1, 1, 14, 14
    first = True
    while x0 <= x1 and y0 <= y1:
        top = [(x, y0) for x in range(x0 if first else x0 - 1, x1 + 1)]
        right = [(x1, y) for y in range(y0 + 1, y1 + 1)]
        bottom = [(x, y1) for x in range(x1 - 1, x0 - 1, -1)]
        left = [(x0, y) for y in range(y1 - 1, y0 + 1, -1)]
        path += top + right + bottom + left
        first = False
        x0, y0, x1, y1 = x0 + 2, y0 + 2, x1 - 2, y1 - 2
    n = len(path)
    for i, (x, y) in enumerate(path):
        t = i / (n - 1)
        if rainbow:
            r, g, b = colorsys.hsv_to_rgb(t * 0.9, 0.6, 1.0)
            c = (int(r * 255), int(g * 255), int(b * 255))
        else:
            c = mix(c1, c2, t)
        a[y, x] = c
    # the rim, dark, lit on its top and left as the picture's is
    for i in range(16):
        a[0, i] = (40, 40, 46)
        a[i, 0] = (40, 40, 46)
        a[15, i] = (8, 8, 10)
        a[i, 15] = (8, 8, 10)
    return a
