"""Clean pixel-art faces for Quantum Solar's blocks, drawn after reference/panels.jpg.

Every face of every block has the same frame (so the edges of a block's faces meet): a dark outline,
a steel ring lit from the top left, and the light grey corner posts the picture shows at every
block's corners. Inside it (12 x 12, pixels 2..13) is the face's own picture, centred: what the
picture shows on that kind of face, in the block's colours.
"""
import colorsys
import math

import numpy as np

OUTLINE = (14, 14, 18)
STEEL_LIGHT, STEEL, STEEL_DARK = (132, 134, 146), (96, 98, 110), (58, 60, 70)
POST = {"w": (242, 243, 247), "l": (204, 206, 214), "d": (132, 134, 144)}
POST_SHAPE = ["wwl", "wll", "lld"]
INNER = (26, 26, 32)
INNER2 = (30, 30, 37)


def blank():
    return np.zeros((16, 16, 3), dtype=np.uint8)


def frame(a):
    """The frame every face has; the inside is left as it is."""
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            a[y, x] = OUTLINE
    for i in range(1, 15):
        a[1, i] = STEEL_LIGHT
        a[i, 1] = STEEL_LIGHT
        a[14, i] = STEEL_DARK
        a[i, 14] = STEEL_DARK
    a[1, 14] = STEEL
    a[14, 1] = STEEL
    for cx, cy, fx, fy in ((0, 0, 1, 1), (13, 0, -1, 1), (0, 13, 1, -1), (13, 13, -1, -1)):
        for j in range(3):
            for i in range(3):
                # the post is lit from the top left whichever corner it is at
                ch = POST_SHAPE[j][i]
                a[cy + j, cx + i] = POST[ch]
        a[cy + (2 if fy > 0 else 0), cx + (2 if fx > 0 else 0)] = POST["d"]
    return a


def fill_inner(a, c=None):
    for y in range(2, 14):
        for x in range(2, 14):
            a[y, x] = c if c is not None else (INNER if (x + 2 * y) % 7 else INNER2)
    return a


def mix(c, d, t):
    return tuple(int(round(p * (1 - t) + q * t)) for p, q in zip(c, d))


def shade(c, k):
    return tuple(int(max(0, min(255, round(v * k)))) for v in c)


def light(c, t=0.45):
    return mix(c, (255, 255, 255), t)


def rainbow(t):
    r, g, b = colorsys.hsv_to_rgb(t % 1.0, 0.65, 1.0)
    return (int(r * 255), int(g * 255), int(b * 255))


def put_pattern(a, rows, colours, x0=2, y0=2):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != ".":
                a[y0 + j, x0 + i] = colours[ch]


# ---- sides ----

# a lightning bolt, centred in the 12 x 12 inside: L light edge, C its colour, D its shade, k glow
BOLT = ["............",
        "......kLLk..",
        ".....kLCk...",
        "....kLCk....",
        "...kLCCCCk..",
        "...kkkCCk...",
        "....kCDk....",
        "...kCDk.....",
        "..kCDk......",
        "..kDk.......",
        "..kk........",
        "............"]


def bolt(a, colour):
    put_pattern(a, BOLT, {"L": light(colour, 0.55), "C": colour, "D": shade(colour, 0.7),
                          "k": mix(INNER, colour, 0.22)})
    return a


def side_bolt(colour):
    """The grid and woven panels' sides: a sunk dark panel, the bolt in the middle."""
    a = frame(fill_inner(blank()))
    # the panel's sunk rim
    for i in range(3, 13):
        a[3, i] = (18, 18, 22)
        a[i, 3] = (18, 18, 22)
        a[12, i] = (44, 44, 52)
        a[i, 12] = (44, 44, 52)
    return bolt(a, colour)


def side_ring(c1, c2, c3):
    """The flare panels' sides: a ring in their colours round a small gem, as the picture shows."""
    a = frame(fill_inner(blank()))
    cx = cy = 7.5
    for y in range(2, 14):
        for x in range(2, 14):
            d = math.hypot(x - cx, y - cy)
            ang = math.atan2(y - cy, x - cx)
            if 3.2 <= d <= 5.3:
                outer = d > 4.3
                c = c1 if outer else c2
                # light from the top left along the ring
                if math.cos(ang + 2.4) > 0.55:
                    c = light(c, 0.35)
                elif math.cos(ang + 2.4) < -0.6:
                    c = shade(c, 0.72)
                a[y, x] = c
            elif d < 1.6:
                a[y, x] = light(c3, 0.3) if (x, y) == (7, 7) else c3
    return a


FACE = ["............",
        "..DDD..DDD..",
        ".DGGGD.DGGGD",
        ".DGoGD.DGoGD",
        "..DDD..DDD..",
        "............",
        "...T....T...",
        "...T....T...",
        "...t....t...",
        "....MMMM....",
        "...M....M...",
        "............"]


def side_face(tear):
    """The tearful panels' fronts: brown, two dark hooded eyes glowing orange, a tear under each in
    the panel's colour, and a sad mouth."""
    a = frame(blank())
    for y in range(2, 14):
        for x in range(2, 14):
            a[y, x] = (134, 88, 46) if (y + (x // 4)) % 3 else (120, 78, 40)
    for x in range(2, 14):
        a[2, x] = (96, 62, 32)
        a[13, x] = (96, 62, 32)
    put_pattern(a, FACE, {"D": (34, 32, 36), "G": (58, 56, 62), "o": (255, 150, 40), "T": tear,
                          "t": shade(tear, 0.7), "M": (60, 40, 24)}, 2, 2)
    return a


def chains(a):
    """The spiral, serpent and meteor generators' sides: grey chains standing at both edges."""
    for x0 in (2, 12):
        for y in range(2, 14):
            k = (y - 2) % 4
            for i in range(2):
                x = x0 + i
                if k == 0:
                    a[y, x] = (200, 202, 210)
                elif k == 3:
                    a[y, x] = (110, 112, 122)
                else:
                    a[y, x] = (160, 162, 172) if i == (0 if x0 == 2 else 1) else (18, 18, 22)
    return a


def side_chain_bolt(colour):
    a = chains(frame(fill_inner(blank())))
    return bolt(a, colour)


def side_chain_slashes(colours):
    a = chains(frame(fill_inner(blank())))
    for k, (x, y0) in enumerate(((5, 4), (8, 3), (10, 5))):
        c = colours[k % len(colours)]
        for i in range(6):
            xx = x - i // 3
            a[y0 + i, xx] = light(c, 0.35) if i == 0 else shade(c, 1.0 - i * 0.07)
    return a


HEART = ["............",
         "..HHH..HHH..",
         ".HWWcHHccCH.",
         ".HWccccccCH.",
         ".HcccccccCH.",
         ".HccccccCCH.",
         "..HccccCCH..",
         "...HccCCH...",
         "....HCCH....",
         ".....HH.....",
         "............",
         "............"]


def side_heart(heart, back):
    a = frame(fill_inner(blank(), back))
    for y in range(2, 14):
        for x in range(2, 14):
            if (x + y) % 4 == 0:
                a[y, x] = shade(back, 0.88)
    put_pattern(a, HEART, {"H": shade(heart, 0.45), "W": (255, 255, 255), "c": heart, "C": shade(heart, 0.78)}, 2, 3)
    return a


def side_squares(c):
    """The core generators' fronts: squares one in another, the corners of the outer one set apart
    as brackets, a bright square in the middle."""
    a = frame(fill_inner(blank(), (16, 16, 20)))
    dim, lit = shade(c, 0.75), light(c, 0.3)
    for y in range(2, 14):
        for x in range(2, 14):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d == 5.5 and (abs(x - 7.5) > 2.5 and abs(y - 7.5) > 2.5):
                a[y, x] = c                    # the brackets at the corners
            elif d == 5.5 and (abs(x - 7.5) == 5.5) != (abs(y - 7.5) == 5.5):
                pass
            elif d == 3.5:
                a[y, x] = dim if (x + y) % 2 else c
            elif d <= 1.5:
                a[y, x] = lit if d == 0.5 else c
    for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        a[y, x] = (240, 240, 245)
    return a


# ---- tops ----

def top_grid(cell):
    """The grid panels' tops: three by three cells, each lit from the top, a white glint in its
    bottom left corner, dark lines between."""
    a = frame(blank())
    for y in range(2, 14):
        for x in range(2, 14):
            a[y, x] = (10, 10, 14)
    for cy in (3, 7, 11):
        for cx in (3, 7, 11):
            for j in range(3):
                for i in range(3):
                    a[cy + j, cx + i] = light(cell, 0.2) if j == 0 else cell if j == 1 else shade(cell, 0.8)
            a[cy + 2, cx] = (250, 250, 255)
    return a


def top_woven(c):
    """The woven panels' tops: a basket weave, bars laid across and along by turns, each tile two
    bars, lit on their upper or left strip, a shadow on the tile's far edge."""
    a = frame(blank())
    lit, mid, gap = light(c, 0.35), c, shade(c, 0.5)
    for ty in range(3):
        for tx in range(3):
            across = (tx + ty) % 2 == 0
            for j in range(4):
                for i in range(4):
                    x, y = 2 + tx * 4 + i, 2 + ty * 4 + j
                    along, cross = (j, i) if across else (i, j)
                    if cross == 3:
                        a[y, x] = gap                      # the shadow where the tile tucks under
                    else:
                        a[y, x] = lit if along % 2 == 0 else mid
    return a


def top_serpent(colours, prism=False):
    """The serpent generators' tops: one line winding across and back, bright on dark."""
    a = frame(fill_inner(blank(), (14, 14, 18)))
    path = []
    rows = [3, 5, 7, 9, 11, 13]
    for k, y in enumerate(rows[:-1]):
        xs = range(3, 13) if k % 2 == 0 else range(12, 2, -1)
        for x in xs:
            path.append((x, y))
        end = 12 if k % 2 == 0 else 3
        path.append((end, y + 1))
    for n, (x, y) in enumerate(path):
        t = n / max(1, len(path) - 1)
        c = rainbow(t * 0.85) if prism else mix(colours[0], colours[1], abs(math.sin(t * math.pi * 2)))
        if y < 14:
            a[y, x] = c
    return a


def top_spiral(colours, prism=False):
    """The spiral generators' tops: a square spiral winding into the middle, one pixel wide."""
    a = frame(fill_inner(blank(), (14, 14, 18)))
    x0, y0, x1, y1 = 2, 2, 13, 13
    path = []
    while x0 <= x1 and y0 <= y1:
        path += [(x, y0) for x in range(x0, x1 + 1)]
        path += [(x1, y) for y in range(y0 + 1, y1 + 1)]
        if y1 > y0:
            path += [(x, y1) for x in range(x1 - 1, x0 - 1, -1)]
        if x1 > x0:
            path += [(x0, y) for y in range(y1 - 1, y0 + 1, -1)]
        x0, y0, x1, y1 = x0 + 2, y0 + 2, x1 - 2, y1 - 2
        # step in so the next turn leaves a dark gap
        if path:
            path.append((x0 - 1, y0))
    for n, (x, y) in enumerate(path):
        if not (2 <= x <= 13 and 2 <= y <= 13):
            continue
        t = n / max(1, len(path) - 1)
        a[y, x] = rainbow(t) if prism else mix(colours[0], colours[1], t)
    return a


METEOR_DASHES = [(5, 3, 0), (10, 3, 1), (13, 6, 2), (7, 7, 1), (12, 9, 0), (4, 9, 2), (9, 11, 0)]


def top_meteor(colours):
    a = frame(fill_inner(blank()))
    for x, y, k in METEOR_DASHES:
        c = colours[k % len(colours)]
        for i in range(3):
            if 2 <= x - i <= 13 and y + i <= 13:
                a[y + i, x - i] = light(c, 0.45) if i == 0 else shade(c, 1.0 - i * 0.18)
    return a


def top_core(c, marks):
    """The core generators' tops (the picture shows only their edge: dark with purple, ice and white
    marks): the marks round the rim, a small square of the core's colour in the middle."""
    a = frame(fill_inner(blank(), (16, 16, 20)))
    ring = [(x, 2) for x in range(2, 14)] + [(13, y) for y in range(3, 14)] + \
           [(x, 13) for x in range(12, 1, -1)] + [(2, y) for y in range(12, 2, -1)]
    for n, (x, y) in enumerate(ring):
        a[y, x] = marks[(n // 2) % len(marks)]
    for y in range(5, 11):
        for x in range(5, 11):
            d = max(abs(x - 7.5), abs(y - 7.5))
            a[y, x] = c if d == 2.5 else (16, 16, 20) if d == 1.5 else light(c, 0.3)
    return a


def top_picture(face, colours):
    """A face the picture shows clearly but whose pattern is not a simple figure (the flare tops):
    its middle, each pixel put to the nearest of its own few colours so no blur is left, in the
    frame."""
    a = frame(blank())
    pal = np.array(colours, dtype=float)
    src = face.astype(float)
    for y in range(2, 14):
        for x in range(2, 14):
            p = src[y, x]
            a[y, x] = tuple(int(v) for v in pal[np.argmin(((pal - p) ** 2).sum(axis=1))])
    return a
