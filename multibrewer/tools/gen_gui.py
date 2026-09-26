"""Generates the Multi Brewer GUI texture as pixel art, in the same style as the mob farm.

Run from the mobfarm folder:  python3 tools/gen_gui.py
Writes src/main/resources/assets/mobfarm/textures/gui/mob_farm_<tier>.png (268x206, same
coordinates as the screen) and, with --preview, a 3x preview image.
"""
import math, struct, sys, zlib

M = 4                       # margin around the screen area (caps and clips stick out)
SW, SH = 256, 236           # screen area, same coordinates as the menu
W, H = SW + 2 * M, SH + 2 * M

TIERS = {
    # bg, bg light, bg dark, trim, trim light, trim dark
    "wooden":    ((107, 63, 31), (128, 78, 40), (70, 40, 17), (201, 145, 63), (236, 190, 110), (118, 78, 30)),
    "stone":     ((78, 82, 88), (96, 100, 107), (50, 53, 58), (185, 190, 198), (232, 235, 240), (104, 109, 116)),
    "iron":      ((60, 74, 94), (76, 92, 114), (38, 47, 61), (200, 210, 222), (244, 247, 251), (106, 118, 132)),
    "golden":    ((117, 45, 42), (140, 62, 56), (70, 26, 31), (227, 179, 65), (255, 226, 140), (136, 96, 18)),
    "diamond":   ((28, 85, 99), (38, 104, 118), (17, 56, 66), (227, 179, 65), (255, 226, 140), (136, 96, 18)),
    "netherite": ((58, 35, 64), (72, 46, 80), (35, 20, 41), (224, 160, 128), (255, 210, 186), (136, 88, 70)),
}
BLACK = (10, 9, 12)
BAND = (44, 46, 58)
BAND_HI = (78, 82, 98)
BAND_LO = (30, 31, 40)
ORANGE, ORANGE_HI, ORANGE_LO = (240, 150, 40), (255, 206, 110), (150, 76, 16)
INV_BG, INV_HI, INV_LO = (198, 198, 198), (255, 255, 255), (85, 85, 85)
CLIP, CLIP_HI, CLIP_LO = (140, 145, 157), (204, 208, 218), (86, 90, 102)
STONE_OUT, STONE, STONE_HI, STONE_LO, STONE_ENG = (38, 42, 40), (152, 166, 154), (196, 208, 197), (110, 122, 112), (128, 142, 130)
TRACK_OUT, TRACK_RIM, TRACK = (12, 10, 12), (126, 131, 141), (38, 24, 26)


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [[(0, 0, 0, 0)] * w for _ in range(h)]

    def set(self, x, y, c, a=255):
        x += M
        y += M
        if 0 <= x < self.w and 0 <= y < self.h:
            if a >= 255 or self.px[y][x][3] == 0:
                self.px[y][x] = tuple(c[:3]) + (a,)
            else:
                old = self.px[y][x]
                t = a / 255.0
                self.px[y][x] = tuple(int(old[i] * (1 - t) + c[i] * t) for i in range(3)) + (255,)

    def rect(self, x1, y1, x2, y2, c, a=255):
        for y in range(y1, y2):
            for x in range(x1, x2):
                self.set(x, y, c, a)

    def outline(self, x1, y1, x2, y2, c):
        self.rect(x1, y1, x2, y1 + 1, c)
        self.rect(x1, y2 - 1, x2, y2, c)
        self.rect(x1, y1, x1 + 1, y2, c)
        self.rect(x2 - 1, y1, x2, y2, c)

    def save(self, path, scale=1, bg=None):
        rows = []
        for y in range(self.h * scale):
            row = b"\x00"
            for x in range(self.w * scale):
                p = self.px[y // scale][x // scale]
                if bg is not None and p[3] < 255:
                    t = p[3] / 255.0
                    p = tuple(int(p[i] * t + bg[i] * (1 - t)) for i in range(3)) + (255,)
                row += bytes(p)
            rows.append(row)
        raw = b"".join(rows)
        ch = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
        with open(path, "wb") as f:
            f.write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", self.w * scale, self.h * scale, 8, 6, 0, 0, 0))
                    + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b""))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def rnd(i, salt):
    h = (i * 0x9E3779B1 + salt * 0x85EBCA6B) & 0xFFFFFFFF
    h ^= h >> 15
    h = (h * 0x2C1B3C6D) & 0xFFFFFFFF
    h ^= h >> 12
    return h


# ------------------------------------------------------------------ pieces

GREY_HI = (92, 93, 90)
GREY_LO = (62, 63, 61)


def corner_cap(cv, cx, cy, pal=None):
    """Orange square cap sticking out of a corner."""
    cv.rect(cx - 3, cy - 3, cx + 4, cy + 4, BLACK)
    cv.rect(cx - 2, cy - 2, cx + 3, cy + 3, ORANGE)
    cv.rect(cx - 2, cy - 2, cx + 3, cy - 1, ORANGE_HI)
    cv.rect(cx - 2, cy - 2, cx - 1, cy + 3, ORANGE_HI)
    cv.rect(cx + 2, cy - 1, cx + 3, cy + 3, ORANGE_LO)
    cv.rect(cx - 1, cy + 2, cx + 3, cy + 3, ORANGE_LO)


KEY = ["#######",
       "......#",
       ".####.#",
       ".#..#.#",
       ".#.##.#",
       ".#....#",
       ".######"]


def key_spiral(cv, x, y, pal, flip_x=False, flip_y=False, handle_to=None):
    """Gold square spiral like the reference: black outline, grey metal ring, dark inside, a spiral
    shaded bright yellow at the top to orange at the bottom (11x11 incl. outline). The spiral's first
    row runs out of the tile as a handle to x = handle_to, ending in a bright dot."""
    tr, trl = pal[3], pal[4]
    shades = [mix(trl, (255, 255, 230), 0.45), mix(trl, (255, 255, 230), 0.2), trl, trl,
              tr, mix(tr, ORANGE, 0.45), mix(tr, ORANGE, 0.6)]
    cv.rect(x - 1, y - 1, x + 10, y + 10, BLACK)
    cv.rect(x, y, x + 9, y + 9, GREY_LO)
    cv.rect(x, y, x + 9, y + 1, GREY_HI)
    cv.rect(x, y, x + 1, y + 9, GREY_HI)
    cv.rect(x + 1, y + 1, x + 8, y + 8, (42, 30, 16))
    fx = (lambda c: x + 7 - c) if flip_x else (lambda c: x + 1 + c)
    fy = (lambda r: y + 7 - r) if flip_y else (lambda r: y + 1 + r)
    for r in range(7):
        for c in range(7):
            if KEY[r][c] == "#":
                cv.set(fx(c), fy(r), shades[6 - r if flip_y else r])
    if handle_to is not None:
        row = fy(0)
        start = fx(0)
        step = 1 if handle_to > start else -1
        for xx in range(start, handle_to + step, step):
            cv.set(xx, row - 1, BLACK)
            cv.set(xx, row + 1, BLACK)
            cv.set(xx, row, shades[0 if not flip_y else 2])
        cv.set(handle_to, row, (255, 250, 220))
        cv.set(handle_to + step, row, BLACK)


def clip(cv, x, y):
    """Grey metal clip: three stacked plates (light over dark) held on the outside of the frame."""
    cv.rect(x - 1, y - 1, x + 4, y + 9, BLACK)
    for k in range(3):
        yy = y + k * 3
        cv.rect(x, yy, x + 3, yy + 1, CLIP_HI)
        cv.rect(x, yy + 1, x + 3, yy + 2, CLIP)
        if k < 2:
            cv.rect(x, yy + 2, x + 3, yy + 3, CLIP_LO)


def vanilla_slot(cv, x, y):
    cv.rect(x - 1, y - 1, x + 17, y + 17, (139, 139, 139))
    cv.rect(x - 1, y - 1, x + 17, y, (55, 55, 55))
    cv.rect(x - 1, y - 1, x, y + 17, (55, 55, 55))
    cv.rect(x - 1, y + 16, x + 17, y + 17, (255, 255, 255))
    cv.rect(x + 16, y - 1, x + 17, y + 17, (255, 255, 255))


def inventory_panel(cv, x1, y1, x2, y2):
    """Plain light-grey panel like vanilla, with the usual bevel and rounded corners."""
    cv.rect(x1 + 1, y1, x2 - 1, y2, BLACK)
    cv.rect(x1, y1 + 1, x2, y2 - 1, BLACK)
    cv.rect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, INV_BG)
    cv.rect(x1 + 1, y1 + 1, x2 - 2, y1 + 3, INV_HI)
    cv.rect(x1 + 1, y1 + 1, x1 + 3, y2 - 2, INV_HI)
    cv.rect(x1 + 3, y2 - 3, x2 - 1, y2 - 1, INV_LO)
    cv.rect(x2 - 3, y1 + 3, x2 - 1, y2 - 1, INV_LO)


def stone_slot(cv, x, y, shade_col=None, shadow=False, plain=False):
    """18x18 pale sage tile like the reference: dark edge, inner shadow top/left, a faint engraved
    gear ring with a small square in the middle and dots in the corners (item area x..x+16)."""
    face = (142, 152, 133)
    eng = (127, 137, 118)
    light = (170, 176, 158)
    inner = (88, 94, 84)
    if shadow and shade_col is not None:
        cv.rect(x + 1, y + 17, x + 18, y + 18, mix(shade_col, BLACK, 0.3))
        cv.rect(x + 17, y + 1, x + 18, y + 18, mix(shade_col, BLACK, 0.3))
    cv.rect(x - 1, y - 1, x + 17, y + 17, (54, 26, 30))
    cv.rect(x, y, x + 16, y + 16, face)
    cv.rect(x, y, x + 16, y + 1, inner)
    cv.rect(x, y, x + 1, y + 16, inner)
    cv.rect(x + 15, y + 1, x + 16, y + 16, light)
    cv.rect(x + 1, y + 15, x + 16, y + 16, light)
    if plain:
        return
    # gear ring: ring of radius ~4-6 with small teeth
    c0 = 7.5
    for yy in range(1, 15):
        for xx in range(1, 15):
            dx, dy = xx - c0, yy - c0
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            tooth = math.cos(ang * 8) > 0.35
            if 4.2 <= d <= (6.4 if tooth else 5.6):
                cv.set(x + xx, y + yy, eng)
    # centre square
    for k in range(6, 10):
        for (xx, yy) in ((k, 6), (k, 9), (6, k), (9, k)):
            cv.set(x + xx, y + yy, eng)
    for (xx, yy) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        cv.set(x + xx, y + yy, eng)


def cap_notch(cv, cx, cy, mirror=False):
    """Orange L-shaped cap at the end of a leg: a square with one top corner cut away."""
    def notch(x, y):
        return y < cy and (x > cx if not mirror else x < cx)
    pts = [(x, y) for x in range(cx - 2, cx + 3) for y in range(cy - 2, cy + 3) if not notch(x, y)]
    ps = set(pts)
    for (x, y) in pts:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if (x + dx, y + dy) not in ps:
                    cv.set(x + dx, y + dy, BLACK)
    for (x, y) in pts:
        c = ORANGE
        if (x, y - 1) not in ps or (x - 1, y) not in ps:
            c = ORANGE_HI
        elif (x, y + 1) not in ps or (x + 1, y) not in ps:
            c = ORANGE_LO
        cv.set(x, y, c)


def gold_brackets(cv, x1, y1, x2, y2, pal, arm=5):
    """Gold L-brackets on the four corners of a box (outside it)."""
    tr, trl, trd = pal[3], pal[4], pal[5]
    for (cx, cy, dx, dy) in ((x1, y1, 1, 1), (x2 - 1, y1, -1, 1), (x1, y2 - 1, 1, -1), (x2 - 1, y2 - 1, -1, -1)):
        for i in range(-1, arm + 1):
            for t in range(-1, 3):
                cv.set(cx - 2 * dx + i * dx, cy - 2 * dy + t * dy, BLACK)
                cv.set(cx - 2 * dx + t * dx, cy - 2 * dy + i * dy, BLACK)
        for i in range(arm):
            cv.set(cx - 2 * dx + i * dx, cy - 2 * dy, trl)
            cv.set(cx - 2 * dx + i * dx, cy - 1 * dy, tr)
            cv.set(cx - 2 * dx, cy - 2 * dy + i * dy, trl)
            cv.set(cx - 1 * dx, cy - 2 * dy + i * dy, tr)
        cv.set(cx - 2 * dx + (arm - 1) * dx, cy - 1 * dy, trd)
        cv.set(cx - 1 * dx, cy - 2 * dy + (arm - 1) * dy, trd)


def horn(cv, ax, ay, pal, mirror=False):
    """Wide curled gold horn anchored at (ax, ay): the tail rises out from the anchor and ends
    in a spiral curling back over itself (drawn pointing left; mirror=True points right)."""
    tr, trl, trd = pal[3], pal[4], pal[5]
    pts = []
    # tail: from the anchor, out to the left and up
    for k in range(80):
        t = k / 79.0
        pts.append((-t * 16.0, -t * t * 3.0))
    # spiral at the outer end, curling up and back in
    sx, sy = -16.0, -3.0
    cx0, cy0 = sx + 1.0, sy - 3.6
    for k in range(140):
        t = k / 139.0
        ang = math.pi * 0.5 + t * 1.75 * math.pi      # start below the centre, go round anticlockwise
        r = 3.6 * (1.0 - t * 0.6)
        pts.append((cx0 + r * 1.25 * math.cos(ang), cy0 + r * math.sin(ang)))
    stroke = set()
    for (x, y) in pts:
        for dx in (0, 1):
            for dy in (0, 1):
                stroke.add((int(math.floor(x)) + dx, int(math.floor(y)) + dy))
    def put(x, y, c):
        cv.set(ax - x if mirror else ax + x, ay + y, c)
    for (x, y) in stroke:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if (x + dx, y + dy) not in stroke:
                    put(x + dx, y + dy, (40, 24, 14))
    for (x, y) in stroke:
        c = tr
        if (x, y - 1) not in stroke:
            c = trl
        elif (x, y + 1) not in stroke:
            c = trd
        put(x, y, c)
    # a little sparkle beside each horn
    put(-22, -2, trl)


def sparkle(cv, x, y, pal, big=False):
    tr, trl = pal[3], pal[4]
    if big:
        cv.set(x, y - 1, tr, 200); cv.set(x, y + 1, tr, 200)
        cv.set(x - 1, y, tr, 200); cv.set(x + 1, y, tr, 200)
        cv.set(x, y, trl)
    else:
        cv.set(x, y, tr, 190)


def gem_icon(cv, cx, cy, bright, deep):
    shape = ["..#..", ".###.", "#####", "#####", "#####", ".###.", "..#.."]
    for r, row in enumerate(shape):
        for c, ch in enumerate(row):
            if ch == "#":
                col = bright if r < 4 else deep
                cv.set(cx - 2 + c, cy - 3 + r, col)
    for r, row in enumerate(shape):
        for c, ch in enumerate(row):
            if ch == "#":
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    rr, cc = r + dy, c + dx
                    if not (0 <= rr < 7 and 0 <= cc < 5) or shape[rr][cc] != "#":
                        cv.set(cx - 2 + cc, cy - 3 + rr, BLACK)
    cv.set(cx - 1, cy - 1, (255, 255, 255))
    cv.set(cx - 1, cy, (255, 255, 255), 150)


def bar_track(cv, x1, y, x2, h):
    cv.rect(x1 - 2, y - 1, x2 + 2, y + h + 1, TRACK_OUT)
    cv.rect(x1 - 1, y - 2, x2 + 1, y + h + 2, TRACK_OUT)
    cv.rect(x1 - 1, y - 1, x2 + 1, y + h + 1, TRACK_RIM)
    cv.rect(x1, y, x2, y + h, TRACK)
    cv.rect(x1, y, x2, y + 1, (24, 14, 16))


# ------------------------------------------------------------------ whole screen

TOP_H = 136                       # ornate panel height; the inventory panel hangs below
IX1, IX2, IY1 = 39, 217, 146      # inventory panel
SLOTS = {"in1": (36, 108), "in2": (64, 108), "in3": (92, 108), "out": (104, 64), "fuel": (20, 14),
         "dur": (20, 52), "pow": (20, 76), "up1": (196, 108), "up2": (218, 108), "ing": (64, 12)}
PREVIEW = (130, 14, 237, 97)
GAUGE = (17, 37, 44, 44)
GLASS, GLASS_HI, GLASS_IN = (150, 196, 210), (225, 245, 250), (22, 20, 34)
# alchemy colours: violet panel, gold trim, a violet-teal brew
PAL = ((70, 36, 92), (92, 52, 118), (40, 18, 56), (227, 179, 65), (255, 226, 140), (136, 96, 18))
BREW, BREW_HI, BREW_LO = (150, 90, 220), (210, 170, 255), (70, 40, 130)
TUBES = [[(72, 29), (72, 33)],
         [(72, 91), (72, 96)], [(72, 96), (44, 96), (44, 106)], [(72, 96), (72, 106)], [(72, 96), (100, 96), (100, 106)],
         [(92, 72), (103, 72)]]
POOL_Y = 132


MEDAL_COLORS = [(220, 60, 70), (70, 130, 235), (80, 200, 90), (170, 80, 220)]
BOTTLE = ["..ccc..",
          "..ggg..",
          ".gLLLg.",
          "gLLLLLg",
          "gLwLLLg",
          "gLLLLLg",
          ".gDDDg."]


def potion_medallion(cv, x, y, pal, color, flip_x=False, handle_to=None):
    """Frame medallion (11x11 incl. outline): grey metal ring, dark inside, a little potion bottle.
    A gold line runs out of it across the frame, ending in a bright dot."""
    tr, trl = pal[3], pal[4]
    cv.rect(x - 1, y - 1, x + 10, y + 10, BLACK)
    cv.rect(x, y, x + 9, y + 9, GREY_LO)
    cv.rect(x, y, x + 9, y + 1, GREY_HI)
    cv.rect(x, y, x + 1, y + 9, GREY_HI)
    cv.rect(x + 1, y + 1, x + 8, y + 8, (26, 16, 36))
    cols = {"c": (150, 100, 56), "g": (190, 226, 236), "L": color, "w": (255, 255, 255),
            "D": tuple(int(v * 0.6) for v in color)}
    for r, row in enumerate(BOTTLE):
        for c, ch in enumerate(row):
            if ch in cols:
                cv.set(x + 1 + (6 - c if flip_x else c), y + 1 + r, cols[ch])
    if handle_to is not None:
        row = y + 4
        start = x - 1 if handle_to < x else x + 9
        step = 1 if handle_to > start else -1
        for xx in range(start, handle_to + step, step):
            cv.set(xx, row - 1, BLACK)
            cv.set(xx, row + 1, BLACK)
            cv.set(xx, row, trl)
        cv.set(handle_to, row, (255, 250, 220))
        cv.set(handle_to + step, row, BLACK)


def sprite(cv, x, y, rows, cols):
    for r, row in enumerate(rows):
        for c, ch in enumerate(row):
            if ch in cols:
                cv.set(x + c, y + r, cols[ch])


def flask_inside(x, y):
    """Neck 68..76 from y 33, round bulb centred on (72, 72). The screen uses the same shape."""
    if 68 <= x <= 76 and 33 <= y <= 56:
        return True
    return (x - 72) ** 2 + (y - 72) ** 2 <= 380


def glass_shape(cv, inside):
    """Glass wall one pixel around a set, black line around that; lighter on the top left."""
    wall = set()
    for (x, y) in inside:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy)
                if q not in inside:
                    wall.add(q)
    for (x, y) in wall:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy)
                if q not in inside and q not in wall:
                    cv.set(q[0], q[1], BLACK)
    for (x, y) in wall:
        lit = (x + 1, y) in inside or (x, y + 1) in inside
        cv.set(x, y, GLASS_HI if lit else GLASS)
    for (x, y) in inside:
        cv.set(x, y, GLASS_IN)


def channel(points):
    out = set()
    for (a, b) in zip(points, points[1:]):
        (x1, y1), (x2, y2) = a, b
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                out.add((x, y))
    return out


def mx(x):
    """Mirror an x coordinate across the middle of the screen."""
    return SW - 1 - x


def recess(cv, x1, y1, x2, y2, fill, pal):
    """Sunken plate: black edge, dark inside with a shadow on top and a light line at the bottom."""
    bg, bgl, bgd = pal[0], pal[1], pal[2]
    pink = mix(bg, (236, 190, 170), 0.16)
    cv.rect(x1, y1, x2, y2, BLACK)
    cv.rect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill)
    cv.rect(x1 + 1, y1 + 1, x2 - 1, y1 + 2, mix(fill, BLACK, 0.45))
    cv.rect(x1 + 1, y2 - 2, x2 - 1, y2 - 1, mix(fill, pink, 0.35))


def build(pal=PAL):
    cv = Canvas(W, H)
    bg, bgl, bgd, tr, trl, trd = pal
    pink = mix(bg, (236, 190, 170), 0.16)
    accent = mix(trl, (240, 150, 90), 0.5)
    bx1, bx2, top_h = 0, SW, TOP_H
    ix1, ix2, iy1, iy2 = IX1, IX2, IY1, SH

    inside = set()
    def area(x1, y1, x2, y2):
        for y in range(y1, y2):
            for x in range(x1, x2):
                inside.add((x, y))
                inside.add((mx(x), y))
    area(bx1 + 4, 4, SW // 2, top_h - 4)
    area(bx1 + 11, top_h - 4, SW // 2, top_h)
    area(ix1 - 2, top_h, SW // 2, iy1)
    area(ix1 - 2, iy1, ix1, iy1 + 5)
    area(ix1 - 6, iy1 + 2, ix1, iy1 + 5)
    for (x, y) in inside:
        edge = any((x + dx, y + dy) not in inside for dx in (-1, 0, 1) for dy in (-1, 0, 1))
        face = all((x + dx, y + dy) in inside for dx in range(-3, 4) for dy in range(-3, 4))
        cv.set(x, y, bg if face else pink if edge else bgd)

    cv.rect(bx1 + 7, 7, bx2 - 7, 8, accent)
    cv.rect(bx1 + 7, 8, bx2 - 7, 9, mix(bg, BLACK, 0.25))
    for x in (bx1 + 7, bx2 - 8):
        cv.rect(x, 7, x + 1, top_h - 10, accent)

    grey = {}
    def pipe(points):
        for (a, b) in zip(points, points[1:]):
            (x1, y1), (x2, y2) = a, b
            if y1 == y2:
                for x in range(min(x1, x2), max(x1, x2) + 2):
                    grey[(x, y1)] = 1
                    grey[(x, y1 + 1)] = 1
            else:
                for y in range(min(y1, y2), max(y1, y2) + 2):
                    grey[(x1, y)] = 1
                    grey[(x1 + 1, y)] = 1
    pipe([(SW // 2, 1), (bx1 + 1, 1), (bx1 + 1, top_h - 3), (bx1 + 8, top_h - 3), (bx1 + 8, top_h),
          (ix1 - 5, top_h), (ix1 - 5, iy1 - 1), (ix1 - 12, iy1 - 1), (ix1 - 12, iy1 + 3)])
    for (x, y) in list(grey):
        grey[(mx(x), y)] = 1
    near_in = lambda q: any((q[0] + dx, q[1] + dy) in inside for dx in (-1, 0, 1) for dy in (-1, 0, 1))
    shade = {}
    for (x, y) in grey:
        outer = inner = False
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in grey:
                if near_in(q):
                    inner = True
                else:
                    outer = True
        if outer != inner:
            shade[(x, y)] = GREY_HI if outer else GREY_LO
        else:
            shade[(x, y)] = GREY_HI if ((x - 1, y) not in grey or (x, y - 1) not in grey) else GREY_LO
    for (x, y) in shade:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy)
                if q not in shade:
                    cv.set(q[0], q[1], BLACK)
    for (x, y), c in shade.items():
        cv.set(x, y, c)
    for side in (False, True):
        f = (lambda x: mx(x)) if side else (lambda x: x)
        for x in range(ix1 - 9, ix1):
            cv.set(f(x), iy1 + 5, BLACK)
            cv.set(f(x), iy1 + 6, GREY_HI)
            cv.set(f(x), iy1 + 7, GREY_LO)
            cv.set(f(x), iy1 + 8, BLACK)

    # ornaments down both side edges
    def plus(x, y):
        for (dx, dy) in ((0, -1), (0, 1), (-1, 0), (1, 0)):
            cv.set(x + dx, y + dy, accent)
        cv.set(x, y, mix(bg, BLACK, 0.2))
    def dash(x, y):
        cv.set(x, y, mix(bg, accent, 0.55))
        cv.set(x, y + 1, mix(bg, accent, 0.55))
    def ell(x, y, fx):
        c = mix(bg, pink, 0.9)
        for k in range(3):
            cv.set(x + k * fx, y, c)
        cv.set(x, y + 1, c)
        cv.set(x, y + 2, c)
    def dot(x, y):
        cv.set(x, y, mix(bg, accent, 0.35))
    kinds = (plus, dash, ell, dot, dash, plus, dot, ell)
    i = 0
    for y in range(22, top_h - 20, 6):
        i += 1
        if rnd(i, 7) % 3 == 0:
            continue
        x = bx1 + 9 + rnd(i, 8) % 2
        k = kinds[rnd(i, 9) % len(kinds)]
        for side in (False, True):
            xx = mx(x) if side else x
            if k is ell:
                ell(xx, y, -1 if side else 1)
            else:
                k(xx, y)

    # corner caps, clips, key spirals, leg caps
    for side in (False, True):
        f = (lambda x: mx(x)) if side else (lambda x: x)
        corner_cap(cv, f(bx1), 0)
        clip(cv, f(bx1 - 3) - (2 if side else 0), 5)
        clip(cv, f(bx1 - 3) - (2 if side else 0), top_h - 12)
        for (y, fy) in ((8, False), (top_h - 17, True)):
            potion_medallion(cv, f(bx1 + 4) - (8 if side else 0), y, pal,
                             MEDAL_COLORS[(1 if side else 0) + (2 if fy else 0)], flip_x=side, handle_to=f(bx1 + 1))
        cap_notch(cv, f(ix1 - 11), iy1 + 5, side)

    # player inventory
    inventory_panel(cv, ix1, iy1, ix2, iy2)
    for r in range(3):
        for c in range(9):
            vanilla_slot(cv, ix1 + 8 + c * 18, iy1 + 8 + r * 18)
    for c in range(9):
        vanilla_slot(cv, ix1 + 8 + c * 18, iy1 + 66)

    content = [(2, 2, 16, 22), (2, 104, 16, 131), (14, 8, 50, 98), (58, 6, 86, 32), (48, 26, 96, 106), (98, 58, 126, 88),
               (30, 90, 114, 131), (126, 8, 241, 131), (238, 2, 256, 22), (238, 104, 256, 131)]

    # little details: bottles on a shelf, herbs, stars and gold runes round the flask
    shelf_y = 50
    for x in range(98, 127):
        cv.set(x, shelf_y, trd); cv.set(x, shelf_y + 1, mix(bgd, BLACK, 0.3))
    for (x, col) in ((101, (220, 60, 70)), (109, (80, 200, 90)), (117, (70, 130, 235))):
        sprite(cv, x, shelf_y - 7, BOTTLE, {"c": (150, 100, 56), "g": (190, 226, 236), "L": col, "w": (255, 255, 255),
                                           "D": tuple(int(v * 0.6) for v in col)})
    herb = ["..g..", ".gGg.", "g.G.g", "..G..", ".bbb."]
    sprite(cv, 104, 30, herb, {"g": (90, 180, 80), "G": (60, 130, 50), "b": (120, 80, 50)})
    mushroom = [".rrr.", "rwrwr", "..s..", "..s.."]
    sprite(cv, 116, 32, mushroom, {"r": (200, 50, 60), "w": (250, 240, 230), "s": (230, 220, 200)})
    for (x, y) in ((100, 18), (122, 22), (111, 14), (47, 20), (54, 26)):
        cv.set(x, y, trl)
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            cv.set(x + dx, y + dy, mix(bg, trl, 0.5))
    runes = [["#.#", ".#.", "#.#"], ["###", "#..", "###"], [".#.", "###", ".#."], ["#..", "###", "..#"]]
    for (rune, (rx, ry)) in zip(runes, ((47, 58), (92, 50), (46, 84), (93, 86))):
        sprite(cv, rx, ry, rune, {"#": mix(bg, trl, 0.55)})
    content += [(96, 12, 128, 54)]

    # background: soft bubbles floating in the free parts of the panel
    placed = []
    for i in range(700):
        x = 10 + rnd(i, 11) % (SW - 20)
        y = 10 + rnd(i, 12) % 118
        if any(c[0] - 3 <= x <= c[2] + 3 and c[1] - 3 <= y <= c[3] + 3 for c in content):
            continue
        if any(abs(x - px) + abs(y - py) < 9 for (px, py) in placed):
            continue
        placed.append((x, y))
        r = 1 + rnd(i, 13) % 2
        ring = mix(bg, BREW_HI, 0.35)
        for dx in range(-r, r + 1):
            for dy in range(-r, r + 1):
                if abs(dx) + abs(dy) == r + (1 if r == 2 else 0) or (r == 1 and abs(dx) + abs(dy) == 1):
                    cv.set(x + dx, y + dy, ring)
        cv.set(x - r + 1, y - r + (0 if r == 1 else 1), mix(bg, (255, 255, 255), 0.5))

    # drips of brew hanging from the gold line along the top
    for (x, n) in ((47, 3), (53, 5), (91, 4), (99, 2), (108, 5), (117, 3), (124, 4), (44, 2), (86, 3)):
        for y in range(8, 8 + n):
            cv.set(x, y, BREW)
        cv.set(x - 1, 8 + n, BREW_LO); cv.set(x, 8 + n, BREW); cv.set(x + 1, 8 + n, BREW_LO)
        cv.set(x - 1, 9 + n, BREW_LO); cv.set(x, 9 + n, BREW_HI); cv.set(x + 1, 9 + n, BREW_LO)
        cv.set(x, 10 + n, BREW_LO)

    # a pool of brew on the strip above the inventory (the screen animates its surface)
    for x in range(ix1 - 1, ix2 + 1):
        surf = POOL_Y + round(math.sin(x / 5.0) * 1.2)
        for y in range(surf, iy1):
            if (x, y) in inside and all((x + dx, y + dy) in inside for dx in (-2, 0, 2) for dy in (-2, 0)):
                cv.set(x, y, BREW_HI if y == surf else mix(BREW, BREW_LO, min(1.0, (y - surf) / 10.0)))

    # glass: flask, a tube from the ingredient into its neck, a manifold down to the three bottles,
    # a spout across to the mixed result
    glass = {(x, y) for x in range(40, 104) for y in range(26, 110) if flask_inside(x, y)}
    for t in TUBES:
        glass |= channel(t)
    glass_shape(cv, glass)
    for x in range(66, 79):
        cv.set(x, 40, trd); cv.set(x, 41, tr); cv.set(x, 42, trl)
    for x in range(64, 81):
        cv.set(x, 93, BLACK)
    for x in range(66, 79):
        cv.set(x, 92, tr)

    # fuel: sunken gauge under the blaze powder slot with a flame mark
    x1, y1, x2, y2 = GAUGE
    recess(cv, x1, y1, x2, y2, mix(bgd, BLACK, 0.45), pal)
    flame = ["..#..", ".#o#.", ".#oo#", "#oyo#", "#oyo#", ".###."]
    for r, row in enumerate(flame):
        for c, ch in enumerate(row):
            col = {"#": BLACK, "o": (240, 130, 30), "y": (255, 220, 90)}.get(ch)
            if col:
                cv.set(x2 + 2 + c, y1 - 1 + r, col)

    # slots
    for key in ("in1", "in2", "in3", "out", "fuel", "dur", "pow", "ing"):
        sx, sy = SLOTS[key]
        stone_slot(cv, sx, sy, bgd, shadow=True, plain=True)
        gold_brackets(cv, sx - 2, sy - 2, sx + 18, sy + 18, pal, arm=4)
    recess(cv, 192, 104, 239, 128, mix(bgd, BLACK, 0.35), pal)
    for key in ("up1", "up2"):
        sx, sy = SLOTS[key]
        stone_slot(cv, sx, sy, plain=True)

    # effect list of the potion being made
    px1, py1, px2, py2 = PREVIEW
    recess(cv, px1, py1, px2, py2, mix(bgd, BLACK, 0.4), pal)
    gold_brackets(cv, px1 - 1, py1 - 1, px2 + 1, py2 + 1, pal, arm=6)
    return cv


if __name__ == "__main__":
    import os
    out = "src/main/resources/assets/multibrewer/textures/gui"
    os.makedirs(out, exist_ok=True)
    cv = build()
    cv.save(f"{out}/brewer.png")
    if "--preview" in sys.argv:
        cv.save(f"{sys.argv[-1]}/preview_brewer.png", scale=3, bg=(40, 60, 40))
